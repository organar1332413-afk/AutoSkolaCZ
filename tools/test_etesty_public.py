import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools.etesty_public.diff_snapshots import compare
from tools.etesty_public.fetch import Fetcher
from tools.etesty_public.parser import AREAS, ParseError, parse_bulletin, parse_list, parse_sample_test
from tools.etesty_public.pipeline import atomic_json, build, encode, media_signature_matches, sha, validate


FIXTURES = Path(__file__).parent / "etesty_public" / "fixtures"


class PublicAdapterTests(unittest.TestCase):
    def test_bulletin_and_pagination(self):
        bulletin = parse_bulletin((FIXTURES / "bulletin.html").read_bytes())
        self.assertEqual(bulletin["publicationDate"], "2026-04-02")
        self.assertEqual(set(AREAS) | {99}, set(bulletin["areas"]))
        questions, pagination = parse_list((FIXTURES / "list-1.html").read_bytes(), 99, "https://etesty.md.gov.cz/ro/Bulletin/List?id=99")
        self.assertEqual((len(questions), pagination["pages"]), (5, 228))
        self.assertEqual(questions[0]["officialId"], "RP1401114")
        self.assertEqual(questions[0]["internalSourceId"], 180)
        self.assertEqual(questions[0]["textCs"], "Nemotorové vozidlo je: ")
        self.assertEqual([x["correct"] for x in questions[0]["answers"]], [True, False, False])
        self.assertIsNone(questions[0]["category"])

    def test_real_sample_positive_observations_are_only_positive(self):
        questions = parse_sample_test((FIXTURES / "sample-b.html").read_bytes(), "B")
        self.assertEqual(len(questions), 25)
        self.assertTrue(all(q["questionCode"] for q in questions))
        with self.assertRaises(ParseError):
            parse_sample_test((FIXTURES / "sample-b.html").read_bytes(), "C")

    def test_exact_entities_two_answers_answer_media_and_video(self):
        html = b'''<input id="pageSize" value="1000"><input id="pageNumber" value="1"><span>z 1</span>
        <div class="QuestionPanel"><div class="QuestionCode">[RP123]</div><div class="QuestionImagePanel"><div>A &amp; B  </div><video src="/binary_content_storage/V.mp4"></video></div>
        <div class="AnswersPanel"><div id="answer-container-2"><div><span class="answer-checkbox">A</span><span class="answer-text" data-isCorrect="True">Ano &lt;test&gt; </span><img src="/binary_content_storage/A.png"></div><div><span class="answer-checkbox">B</span><span class="answer-text" data-isCorrect="False">Ne</span></div></div></div></div>'''
        q, _ = parse_list(html, 55, "https://etesty.md.gov.cz/ro/Bulletin/List?id=55")
        self.assertEqual(q[0]["textCs"], "A & B  ")
        self.assertEqual(q[0]["answers"][0]["textCs"], "Ano <test> ")
        self.assertEqual(q[0]["points"], 4)
        self.assertEqual(q[0]["pointsProvenance"], "derivedFromOfficialBlueprint")
        self.assertEqual({m["kind"] for m in q[0]["media"]}, {"video", "image"})
        self.assertEqual(q[0]["media"][1]["answerCode"], "A")

    def test_image_only_answers_have_empty_official_text_and_single_media(self):
        html = b'''<input id="pageSize" value="1"><input id="pageNumber" value="1"><span>z 1</span>
        <div class="QuestionPanel"><span class="QuestionCode">[RP1601008]</span>
        <div class="QuestionImagePanel"><div>Which sign?</div></div><div class="AnswersPanel"><div id="answer-container-1408">
        <div><span class="answer-checkbox">A</span><div class="answer-image" data-isCorrect="False"><img src="/binary_content_storage/A_W_1.jpg"></div><dialog><img src="/binary_content_storage/A_W_1.jpg"></dialog></div>
        <div><span class="answer-checkbox">B</span><div class="answer-image" data-isCorrect="True"><img src="/binary_content_storage/A_W_2.jpg"></div></div></div></div></div>'''
        q, _ = parse_list(html, 54, "https://etesty.md.gov.cz/ro/Bulletin/List?id=54")
        self.assertEqual([a["textCs"] for a in q[0]["answers"]], ["", ""])
        self.assertEqual([a["correct"] for a in q[0]["answers"]], [False, True])
        self.assertEqual(len(q[0]["media"]), 2)

    def test_cache_resume_hash_and_origin_restriction(self):
        with tempfile.TemporaryDirectory() as directory:
            fetcher = Fetcher(directory)
            with self.assertRaises(ValueError):
                fetcher.get("https://example.com/other")
            url = "https://etesty.md.gov.cz/ro/Bulletin"
            key = sha(url.encode())
            file = Path(directory) / key[:2] / key
            file.parent.mkdir()
            file.write_bytes(b"recorded")
            self.assertEqual(fetcher.get(url), b"recorded")
            self.assertEqual(fetcher.cached, 1)

    def test_retry_then_persist_exact_response_hash(self):
        class Response:
            status = 200
            def __enter__(self): return self
            def __exit__(self, *args): return False
            def read(self): return b"exact source bytes"
        with tempfile.TemporaryDirectory() as directory, \
             patch("tools.etesty_public.fetch.urlopen", side_effect=[OSError("transient"), Response()]) as request, \
             patch("tools.etesty_public.fetch.time.sleep"):
            fetcher = Fetcher(directory, delay=0, retries=2)
            record, body = fetcher.record("https://etesty.md.gov.cz/ro/Bulletin")
            self.assertEqual(request.call_count, 2)
            self.assertEqual(record["sha256"], sha(body))
            self.assertEqual(fetcher.get(record["sourceUrl"]), b"exact source bytes")
            self.assertEqual(fetcher.cached, 1)

    def test_media_signature_rejects_html_error_body(self):
        self.assertFalse(media_signature_matches(b"<html>Error</html>", "video/mp4"))
        self.assertTrue(media_signature_matches(b"GIF89a\x01\x00", "image/gif"))
        self.assertTrue(media_signature_matches(b"\x00\x00\x00\x18ftypisom", "video/mp4"))

    def test_diff_tracks_correctness_separately(self):
        q = {"officialId": "RP1", "textCs": "Hi", "answers": [{"code": "A", "textCs": "A", "correct": True}, {"code": "B", "textCs": "B", "correct": False}], "points": 2,
             "category": "rules", "media": [], "eligibilityEvidence": []}
        changed = json.loads(json.dumps(q))
        changed["answers"][0]["correct"] = False
        changed["answers"][1]["correct"] = True
        self.assertEqual(compare({"questions": [q]}, {"questions": [changed]}),
                         [{"officialId": "RP1", "change": "CORRECT ANSWER CHANGED"}])

    def test_build_deterministic_and_never_ready_from_observation(self):
        with tempfile.TemporaryDirectory() as directory:
            q = {"officialId": "RP1", "internalSourceId": 4, "category": "rules", "textCs": "Text ", "points": 2,
                 "pointsProvenance": "derivedFromOfficialBlueprint", "answers": [{"code": "A", "textCs": "Ano", "correct": True}, {"code": "B", "textCs": "Ne", "correct": False}],
                 "media": [], "sourceRefs": ["https://etesty.md.gov.cz/ro/Bulletin/List?id=52"],
                 "eligibilityEvidence": [{"licenceGroup": "B", "source": "https://etesty.md.gov.cz/co/DLTest/SampleTest/B", "evidenceType": "OFFICIAL_GENERATOR_OBSERVED"}]}
            snapshot = {"snapshot": {"source": "https://etesty.md.gov.cz/ro/Bulletin", "publicationDate": "2026-04-02", "databaseVersion": "public-etesty-2026-04-02", "retrievedAt": "2026-09-25T00:00:00Z", "totalDiscovered": 1, "rawPages": []}, "questions": [q], "observations": [], "mediaInventory": [], "quarantine": []}
            build(snapshot, directory)
            first = (Path(directory) / "package-v2.json").read_bytes()
            build(snapshot, directory)
            self.assertEqual(first, (Path(directory) / "package-v2.json").read_bytes())
            manifest = json.loads(first)["manifest"]
            self.assertTrue(all(not g["eligibilityComplete"] for g in manifest["groupReadiness"]))
            self.assertEqual(json.loads(first)["questions"][0]["textCs"], "Text ")

    def test_missing_media_rejected_and_shared_media_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "media/shared.png"
            file.parent.mkdir()
            file.write_bytes(b"image bytes")
            ref = {"path": "media/shared.png", "sha256": sha(b"image bytes"), "mimeType": "image/png"}
            question = {"officialId": "RP1", "category": "signs", "textCs": "Značka", "points": 1,
                        "answers": [{"code": "A", "textCs": "", "correct": True, "underlyingSourceTextCs": ".",
                                     "underlyingSourceTextRef": "https://etesty.md.gov.cz/api/v1/PublicWeb/Question/1"},
                                    {"code": "B", "textCs": "Ne", "correct": False}],
                        "media": [{**ref, "answerCode": "A"}], "sourceRefs": ["https://etesty.md.gov.cz/ro/Bulletin/List?id=54"],
                        "eligibilityEvidence": []}
            snap = {"snapshot": {"source": "https://etesty.md.gov.cz/ro/Bulletin", "publicationDate": "2026-04-02", "databaseVersion": "public-etesty-2026-04-02", "retrievedAt": "2026-09-25T00:00:00Z", "totalDiscovered": 2, "rawPages": []},
                    "questions": [question, {**question, "officialId": "RP2"}], "observations": [],
                    "mediaInventory": [{**ref, "size": len(b"image bytes"), "sourceUrl": "https://etesty.md.gov.cz/binary_content_storage/shared.png"}], "quarantine": []}
            self.assertEqual(validate(snap, directory), [])
            build(snap, directory)
            built = json.loads((Path(directory) / "package-v2.json").read_text())
            self.assertEqual(built["questions"][0]["answers"][0], {"code": "A", "textCs": "", "correct": True})
            file.unlink()
            self.assertEqual(len(validate(snap, directory)), 2)
            with self.assertRaises(ValueError):
                build(snap, directory)


if __name__ == "__main__":
    unittest.main()
