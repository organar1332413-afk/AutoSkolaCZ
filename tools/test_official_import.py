import copy
import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from official_import.build_package import BLUEPRINT, GROUPS, SECTIONS, IntakeError, compile_package, write_output


def digest(data):
    return hashlib.sha256(data).hexdigest()


class OfficialIntakeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "source"
        self.root.mkdir()
        (self.root / "export.bin").write_bytes(b"synthetic reviewed export")
        self.meta = {"databaseVersion": "fixture-1", "publicationDate": "2026-04-02",
                     "retrievedAt": "2026-09-25T00:00:00Z", "source": "https://etesty.md.gov.cz/",
                     "exportFile": "export.bin", "exportSha256": digest(b"synthetic reviewed export")}
        self.questions = []
        self.mappings = []
        self.coverage = []

    def question(self, qid="1", category="rules", points=2, media=None):
        self.questions.append({"officialId": qid, "category": category,
                               "textCs": "  Původní český text?  ", "points": points,
                               "answers": [{"code": "A", "textCs": "Ano", "correct": True},
                                           {"code": "B", "textCs": "Ne", "correct": False}],
                               "media": media or [], "source": "https://etesty.md.gov.cz/ro/Bulletin/List?id=99"})

    def mapping(self, qid, code):
        self.mappings.append({"officialId": qid, "licenceGroup": code,
                              "source": "https://etesty.md.gov.cz/", "evidenceRef": f"export.bin:{qid}:{code}"})

    def save(self):
        for name, value in (("source", self.meta), ("questions", self.questions),
                            ("eligibility", self.mappings), ("coverage", self.coverage)):
            (self.root / f"{name}.json").write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")
        inventory = {}
        for question in self.questions:
            for media in question["media"]:
                path = media["path"]
                file = self.root / path
                if file.is_file():
                    inventory[path] = {"path": path, "sha256": digest(file.read_bytes()),
                                       "mimeType": media["mimeType"]}
        reviewed = {"sourceSha256": digest((self.root / "source.json").read_bytes()),
                    "exportSha256": self.meta["exportSha256"],
                    "questionsSha256": digest((self.root / "questions.json").read_bytes()),
                    "eligibilitySha256": digest((self.root / "eligibility.json").read_bytes()),
                    "coverageSha256": digest((self.root / "coverage.json").read_bytes()),
                    "media": [inventory[path] for path in sorted(inventory)],
                    "reviewedAt": "2026-09-25T00:00:00Z", "reviewedBy": "fixture reviewer"}
        (self.root / "review.json").write_text(json.dumps(reviewed, ensure_ascii=False), encoding="utf-8")

    def attest(self, code="B", complete=True):
        self.save()
        self.coverage.append({"licenceGroup": code, "blueprintVersion": BLUEPRINT,
                              "eligibilityComplete": complete,
                              "mappingSha256": digest((self.root / "eligibility.json").read_bytes()),
                              "exportSha256": self.meta["exportSha256"],
                              "officialIds": sorted(m["officialId"] for m in self.mappings if m["licenceGroup"] == code),
                              "source": "https://etesty.md.gov.cz/", "evidenceRef": "export.bin:coverage:B"})
        self.save()

    def full_pool(self, code="B"):
        index = 0
        for section, (count, points) in SECTIONS.items():
            for _ in range(count):
                index += 1
                self.question(str(index), section, points)
                self.mapping(str(index), code)

    def assert_rejected(self):
        self.save()
        with self.assertRaises((IntakeError, ValueError, TypeError)):
            compile_package(self.root)

    def test_unmapped_question_is_unknown_for_all_groups(self):
        self.question()
        self.save()
        pack, audit = compile_package(self.root)
        self.assertEqual([], pack["questions"][0]["eligibility"])
        self.assertFalse(any(r["eligibilityComplete"] for r in audit["readiness"]))
        self.assertEqual("  Původní český text?  ", pack["questions"][0]["textCs"])

    def test_complete_attested_pool_only_enables_selected_group(self):
        self.full_pool("C")
        self.attest("C")
        pack, audit = compile_package(self.root)
        self.assertEqual(GROUPS, tuple(r["licenceGroup"] for r in audit["readiness"]))
        self.assertEqual("C", pack["questions"][0]["eligibility"][0]["licenceGroup"])
        self.assertTrue(all(audit["readiness"][3][k] for k in
                            ("eligibilityComplete", "contentComplete", "mediaComplete")))
        self.assertFalse(audit["readiness"][1]["eligibilityComplete"])
        output = Path(self.temp.name) / "out"
        write_output(self.root, output)
        self.assertEqual(pack, json.loads((output / "package-v2.json").read_text()))
        first = (output / "package-v2.json").read_bytes()
        second = Path(self.temp.name) / "second"
        write_output(self.root, second)
        self.assertEqual(first, (second / "package-v2.json").read_bytes())

    def test_missing_section_and_wrong_points_keep_content_incomplete(self):
        self.full_pool()
        self.questions.pop()
        self.mappings.pop()
        self.attest()
        self.assertFalse(compile_package(self.root)[1]["readiness"][1]["contentComplete"])
        self.questions[-1]["points"] = 4
        self.save()
        self.assertFalse(compile_package(self.root)[1]["readiness"][1]["contentComplete"])

    def test_incomplete_attestation_blocks_even_complete_pool(self):
        self.full_pool()
        self.attest(complete=False)
        readiness = compile_package(self.root)[1]["readiness"][1]
        self.assertFalse(readiness["eligibilityComplete"])
        self.assertTrue(readiness["contentComplete"])

    def test_video_keeps_media_incomplete_and_copies_original(self):
        self.full_pool()
        (self.root / "media").mkdir()
        (self.root / "media" / "clip.mp4").write_bytes(b"original video")
        self.questions[0]["media"] = [{"path": "media/clip.mp4", "mimeType": "video/mp4"}]
        self.attest()
        pack, report = compile_package(self.root)
        self.assertFalse(report["readiness"][1]["mediaComplete"])
        self.assertEqual(digest(b"original video"), pack["questions"][0]["media"][0]["sha256"])
        output = Path(self.temp.name) / "out"
        write_output(self.root, output)
        self.assertEqual(b"original video", (output / "media" / "clip.mp4").read_bytes())

    def test_shared_image_is_preserved_for_multiple_questions(self):
        (self.root / "media").mkdir()
        (self.root / "media" / "sign.png").write_bytes(b"synthetic image")
        for qid in ("1", "2"):
            self.question(qid, media=[{"path": "media/sign.png", "mimeType": "image/png"}])
        self.save()
        pack, audit = compile_package(self.root)
        self.assertEqual(["media/sign.png"], audit["mediaPaths"])
        self.assertEqual(pack["questions"][0]["media"], pack["questions"][1]["media"])

    def test_review_rejects_any_modified_input_or_media(self):
        (self.root / "media").mkdir()
        (self.root / "media" / "sign.png").write_bytes(b"reviewed image")
        self.question(media=[{"path": "media/sign.png", "mimeType": "image/png"}])
        self.mapping("1", "B")
        self.attest(complete=False)
        originals = {path: (self.root / path).read_bytes() for path in
                     ("source.json", "export.bin", "questions.json", "eligibility.json",
                      "coverage.json", "media/sign.png")}
        for path, original in originals.items():
            (self.root / path).write_bytes(original + b" ")
            with self.subTest(path=path), self.assertRaises((IntakeError, ValueError)):
                compile_package(self.root)
            (self.root / path).write_bytes(original)
        self.assertEqual(1, compile_package(self.root)[1]["questionCount"])

    def test_review_rejects_unreviewed_referenced_media_and_inventory_conflict(self):
        (self.root / "media").mkdir()
        (self.root / "media" / "first.png").write_bytes(b"one")
        (self.root / "media" / "extra.png").write_bytes(b"two")
        self.question(media=[{"path": "media/first.png", "mimeType": "image/png"}])
        self.save()
        review = json.loads((self.root / "review.json").read_text())
        self.questions[0]["media"].append({"path": "media/extra.png", "mimeType": "image/png"})
        self.save()
        revised = json.loads((self.root / "review.json").read_text())
        review["questionsSha256"] = revised["questionsSha256"]
        (self.root / "review.json").write_text(json.dumps(review))
        with self.assertRaises(IntakeError):
            compile_package(self.root)
        self.questions[0]["media"].pop()
        self.save()
        review = json.loads((self.root / "review.json").read_text())
        review["media"].append({"path": "media/extra.png", "sha256": digest(b"two"), "mimeType": "image/png"})
        (self.root / "review.json").write_text(json.dumps(review))
        with self.assertRaises(IntakeError):
            compile_package(self.root)

    def test_unsupported_group_and_duplicate_mapping_rejected(self):
        self.question()
        self.mapping("1", "T")
        self.assert_rejected()
        self.mappings[0]["licenceGroup"] = "B"
        self.mappings.append(copy.deepcopy(self.mappings[0]))
        self.assert_rejected()

    def test_coverage_hash_and_inventory_must_match_mapping(self):
        self.question()
        self.mapping("1", "B")
        self.attest()
        self.coverage[0]["officialIds"] = []
        self.assert_rejected()
        self.coverage[0]["officialIds"] = ["1"]
        self.coverage[0]["mappingSha256"] = "0" * 64
        self.assert_rejected()

    def test_export_hash_and_missing_media_rejected(self):
        self.question()
        self.meta["exportSha256"] = "0" * 64
        self.assert_rejected()
        self.meta["exportSha256"] = digest(b"synthetic reviewed export")
        self.questions[0]["media"] = [{"path": "media/missing.png", "mimeType": "image/png"}]
        self.assert_rejected()

    def test_answers_paths_and_duplicate_ids_rejected(self):
        self.question()
        self.questions[0]["answers"][1]["correct"] = True
        self.assert_rejected()
        self.questions[0]["answers"][1]["correct"] = False
        self.question()
        self.assert_rejected()
        self.questions.pop()
        self.questions[0]["media"] = [{"path": "../secret.png", "mimeType": "image/png"}]
        self.assert_rejected()


if __name__ == "__main__":
    unittest.main()
