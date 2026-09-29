import copy
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools.learning_content.signs import CONTENT, canonical_bytes, parse_index, validate, validate_cards, validate_guide, full_audit


class SignInventoryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = json.loads((CONTENT / "catalog.json").read_text(encoding="utf-8"))
        cls.sources = json.loads((CONTENT / "sources.json").read_text(encoding="utf-8"))
        cls.cards = json.loads((CONTENT / "curated.json").read_text(encoding="utf-8"))
        cls.guide = json.loads((CONTENT / "guide.json").read_text(encoding="utf-8"))

    def test_committed_inventory_and_audit(self):
        audit = full_audit(self.data, self.sources)
        self.assertEqual(canonical_bytes(audit), (CONTENT / "audit.json").read_bytes())
        self.assertEqual(408, audit["total"])
        self.assertEqual(408, audit["csTitles"])
        self.assertEqual(292, audit["indexedFamilies"])
        self.assertEqual(408, audit["atomicLegalEntries"])
        self.assertEqual(44, audit["warningLegalCodesVerified"])
        self.assertEqual(49, audit["warningGraphicExecutionsIndexed"])
        self.assertEqual(8, audit["priorityLegalCodesVerified"])
        self.assertEqual(13, audit["priorityGraphicExecutionsIndexed"])
        self.assertEqual(34, audit["mandatoryLegalCodesVerified"])
        self.assertEqual(51, audit["mandatoryGraphicExecutionsIndexed"])
        self.assertEqual(22, audit["zoneLegalCodesVerified"])
        self.assertEqual(50, audit["zoneGraphicExecutionsIndexed"])
        self.assertEqual(47, audit["trafficLegalCodesVerified"])
        self.assertEqual(47, audit["trafficGraphicExecutionsIndexed"])
        self.assertEqual(73, audit["directionLegalCodesVerified"])
        self.assertEqual(73, audit["directionGraphicExecutionsIndexed"])
        self.assertEqual(28, audit["otherInfoLegalCodesVerified"])
        self.assertEqual(33, audit["panelLegalCodesVerified"])
        self.assertEqual(40, audit["markingLegalCodesVerified"])
        self.assertEqual(39, audit["signalAtomicAspectsIndexed"])
        self.assertEqual(2, audit["graphicVersionReviewRequired"])
        self.assertIsNone(audit["canonicalVariants"])
        self.assertEqual(40, audit["categories"]["road_marking"])
        self.assertEqual(39, audit["categories"]["light_signal"])
        self.assertEqual(101, audit["bundledImages"])
        self.assertEqual(98, audit["graphicsPresent"])
        self.assertEqual(408, audit["ruTitles"])
        self.assertEqual(408, audit["ukTitles"])
        self.assertEqual(310, len(audit["graphicsMissingCodes"]))
        self.assertEqual(308, audit["graphicLicenseReviewRequired"])
        self.assertEqual(["IZ 7a", "IZ 7b"], audit["graphicVersionReviewRequiredCodes"])
        self.assertFalse(audit["productionReady"])
        self.assertEqual([], audit["unreviewedCodes"])

    def test_duplicate_code_rejected(self):
        data = copy.deepcopy(self.data)
        data["signs"].append(copy.deepcopy(data["signs"][0]))
        data["inventoryCount"] += 1
        with self.assertRaisesRegex(ValueError, "Duplicate sign code"):
            validate(data, self.sources)

    def test_empty_czech_title_and_invalid_category_rejected(self):
        for key, value, error in (("titleCs", "", "Empty Czech"), ("category", "unknown", "Invalid category")):
            with self.subTest(key=key):
                data = copy.deepcopy(self.data)
                data["signs"][0][key] = value
                with self.assertRaisesRegex(ValueError, error):
                    validate(data, self.sources)

    def test_unreviewed_translation_cannot_replace_official_czech(self):
        data = copy.deepcopy(self.data)
        data["signs"][0]["titleRu"] = "Предупреждение"
        with self.assertRaisesRegex(ValueError, "Unreviewed explanation"):
            validate(data, self.sources)

    def test_source_and_image_integrity(self):
        data = copy.deepcopy(self.data)
        data["signs"][0]["sourceIds"] = ["absent"]
        with self.assertRaisesRegex(ValueError, "Missing source"):
            validate(data, self.sources)
        data = copy.deepcopy(self.data)
        data["signs"][0]["graphic"]["path"] = "../escape.png"
        with self.assertRaisesRegex(ValueError, "Unsafe or missing image"):
            validate(data, self.sources)

    def test_local_graphic_hash_mime_orphan_and_explicit_sharing(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            graphics = root / "graphics"
            graphics.mkdir()
            image = graphics / "A-10.png"
            image.write_bytes(b"\x89PNG\r\n\x1a\nexample bytes")
            sign = copy.deepcopy(self.data["signs"][0])
            sign["graphic"] = {
                "sourceId": "decree-294-2015", "status": "VERIFIED",
                "path": "graphics/A-10.png", "mime": "image/png",
                "sha256": hashlib.sha256(image.read_bytes()).hexdigest(),
            }
            data = {"signs": [sign], "inventoryCount": 1}
            with patch("tools.learning_content.signs.CONTENT", root):
                self.assertEqual(1, validate(data, self.sources)["bundledImages"])
                corrupted = copy.deepcopy(data)
                corrupted["signs"][0]["graphic"]["sha256"] = "0" * 64
                with self.assertRaisesRegex(ValueError, "Image hash mismatch"):
                    validate(corrupted, self.sources)
                corrupted = copy.deepcopy(data)
                corrupted["signs"][0]["graphic"]["mime"] = "image/webp"
                with self.assertRaisesRegex(ValueError, "Image MIME mismatch"):
                    validate(corrupted, self.sources)
                extra = graphics / "unreviewed.png"
                extra.write_bytes(image.read_bytes())
                with self.assertRaisesRegex(ValueError, "Orphan sign graphics"):
                    validate(data, self.sources)
                extra.unlink()
                shared = copy.deepcopy(sign)
                shared["code"] = "A 11"
                shared["sourceProvision"] = sign["sourceProvision"].replace("A 10", "A 11")
                with self.assertRaisesRegex(ValueError, "Unexplained duplicate image hash"):
                    validate({"signs": [sign, shared], "inventoryCount": 2}, self.sources)
                sign["graphic"]["shared"] = True
                shared["graphic"]["shared"] = True
                self.assertEqual(2, validate({"signs": [sign, shared], "inventoryCount": 2}, self.sources)["total"])

    def test_prohibition_batch_has_distinct_annex_three_sources(self):
        by_code = {sign["code"]: sign for sign in self.data["signs"]}
        paths = set()
        for number, file_id in zip(range(14, 19), range(840305, 840314, 2)):
            code = f"B {number}"
            graphic = by_code[code]["graphic"]
            self.assertEqual("VERIFIED", graphic["status"])
            self.assertEqual("esbirka-294-2015-2025-07-01-zip", graphic["sourceId"])
            self.assertEqual(str(file_id), graphic["sourceFileId"])
            self.assertIn("_pril_3_frag_1075596033_IZ.tiff", graphic["sourceArchivePath"])
            self.assertEqual("image/webp", graphic["mime"])
            self.assertNotIn(graphic["path"], paths)
            paths.add(graphic["path"])
        self.assertEqual(5, len(paths))

    def test_index_wrap_and_variant_kept_without_fabrication(self):
        text = "\f" * 10 + "6.1 B 3      Zákaz vozidel                     07/2019\n" + "\f" * 8
        signs = parse_index(text)
        self.assertEqual(["B 3"], [s["code"] for s in signs])
        self.assertEqual("Zákaz vozidel", signs[0]["titleCs"])

    def test_deterministic_json(self):
        self.assertEqual(canonical_bytes(self.data), (CONTENT / "catalog.json").read_bytes())
        self.assertEqual(canonical_bytes(self.cards), (CONTENT / "curated.json").read_bytes())
        self.assertEqual(canonical_bytes(self.guide), (CONTENT / "guide.json").read_bytes())

    def test_curated_cards_have_provenance_and_all_languages(self):
        validate_cards(self.data, self.cards, self.sources)
        self.assertEqual(408, len(self.cards["cards"]))
        first_five = {s["code"] for s in self.data["signs"]}
        reviewed = {c["code"] for c in self.cards["cards"] if c["code"] in first_five}
        self.assertEqual(first_five, reviewed)

    def test_legal_codes_and_graphic_executions_are_distinct(self):
        signs = {s["code"]: s for s in self.data["signs"]}
        self.assertEqual({"A 31a", "A 31b", "A 31c"}, set(signs) & {"A 31a", "A 31b", "A 31c"})
        self.assertEqual({240, 160, 80}, {
            int(signs[c]["titleCs"].split("(")[1].split()[0]) for c in ("A 31a", "A 31b", "A 31c")
        })
        self.assertEqual(["A 6b-1", "A 6b-2"], signs["A 6b"]["graphicVariantCodes"])
        self.assertNotIn("A 6b-1", signs)
        self.assertEqual(["P 4-1", "P 4-2", "P 4-3"], signs["P 4"]["graphicVariantCodes"])
        self.assertNotIn("P 4-1", signs)
        self.assertEqual(["C 5a", "C 5b"], [code for code in ("C 5a", "C 5b") if code in signs])
        self.assertEqual("C 5", signs["C 5a"]["familyCode"])
        self.assertEqual(["IZ 10a-1", "IZ 10a-2"], signs["IZ 10a"]["graphicVariantCodes"])
        self.assertEqual("VERSION_REVIEW_REQUIRED", signs["IZ 7a"]["graphic"]["status"])
        self.assertEqual(13, len(signs["B 20a"]["graphicVariantCodes"]))
        self.assertEqual("Zákaz vjezdu vozidel, jejichž šířka přesahuje vyznačenou mez",
                         signs["B 15"]["titleCs"])
        self.assertEqual("Jednosměrný provoz s povoleným provozem cyklistů v protisměru",
                         signs["IP 4c"]["titleCs"])
        self.assertEqual({f"IP 11{suffix}" for suffix in "abcdefg"},
                         set(signs) & {f"IP 11{suffix}" for suffix in "abcdefg"})
        self.assertTrue(all(signs[code]["sourceProvision"].endswith(code)
                            for code in signs if code.startswith("IP ")))
        self.assertEqual({f"IS 22{suffix}" for suffix in "abcdef"},
                         set(signs) & {f"IS 22{suffix}" for suffix in "abcdef"})
        self.assertNotIn("IS 6c", signs)
        self.assertTrue(all(signs[code]["sourceProvision"].endswith(code)
                            for code in signs if code.startswith("IS ")))
        self.assertEqual("Označník zastávky", signs["IJ 4b"]["titleCs"])
        self.assertEqual("Návěst před odbočením na odpočívku", signs["IJ 18b"]["titleCs"])
        self.assertEqual({"E 2a", "E 2b", "E 2c", "E 2d"},
                         set(signs) & {"E 2a", "E 2b", "E 2c", "E 2d"})
        self.assertEqual("Vzdálenost", signs["E 3b"]["titleCs"])
        self.assertTrue(signs["V 10f"]["titleCs"].endswith("osobu těžce pohybově postiženou"))
        self.assertTrue(all(signs[code]["sourceProvision"].endswith(code)
                            for code in signs if code.startswith("V ")))
        self.assertNotIn("S 1", signs)
        self.assertEqual({"S 1a", "S 1b", "S 1c"},
                         set(signs) & {"S 1a", "S 1b", "S 1c"})
        self.assertEqual("S 1", signs["S 1a"]["familyCode"])
        self.assertTrue(all(signs[code]["sourceProvision"].endswith(code)
                            for code in signs if code.startswith("S ")))

    def test_card_cannot_replace_original_title_or_omit_translation(self):
        cards = copy.deepcopy(self.cards)
        cards["cards"][0]["titleCs"] = "Altered"
        with self.assertRaisesRegex(ValueError, "official Czech title"):
            validate_cards(self.data, cards, self.sources)
        cards = copy.deepcopy(self.cards)
        cards["cards"][0]["titleUk"] = ""
        with self.assertRaisesRegex(ValueError, "Incomplete CS/RU/UK"):
            validate_cards(self.data, cards, self.sources)

    def test_question_links_require_existing_official_id(self):
        cards = copy.deepcopy(self.cards)
        linked = next(c for c in cards["cards"] if c["code"] == "B 20a")
        linked["questionLinks"][0]["officialId"] = "not-in-bank"
        with self.assertRaisesRegex(ValueError, "lacks bank evidence"):
            validate_cards(self.data, cards, self.sources, {"RP000001"})
        cards = copy.deepcopy(self.cards)
        linked = next(c for c in cards["cards"] if c["code"] == "B 20a")
        linked["questionLinks"][0]["evidence"]["questionTextCs"] = "Jen nejvyšší rychlost."
        with self.assertRaisesRegex(ValueError, "not explicitly verified"):
            validate_cards(self.data, cards, self.sources)
        cards = copy.deepcopy(self.cards)
        linked = next(c for c in cards["cards"] if c["code"] == "B 20a")
        linked["questionLinks"] *= 2
        with self.assertRaisesRegex(ValueError, "lacks bank evidence"):
            validate_cards(self.data, cards, self.sources)

    def test_question_reference_inventory_is_hash_bound(self):
        refs = json.loads((CONTENT / "official_question_refs.json").read_text(encoding="utf-8"))
        self.assertEqual(1136, refs["officialIdsCount"])
        self.assertEqual(1, full_audit(self.data, self.sources)["linkedQuestionsVerified"])
        self.assertEqual(1, full_audit(self.data, self.sources)["linkedRelationshipsVerified"])

    def test_source_backed_guide_blocks(self):
        validate_guide(self.guide, self.sources)
        self.assertEqual(4, len(self.guide["blocks"]))
        corrupted = copy.deepcopy(self.guide)
        corrupted["blocks"][0]["provision"] = ""
        with self.assertRaisesRegex(ValueError, "Incomplete guide block"):
            validate_guide(corrupted, self.sources)

    def test_legal_appendix_inventory_has_provisions(self):
        legal = [s for s in self.data["signs"] if s["category"] in {"road_marking", "light_signal"}]
        self.assertEqual(79, len(legal))
        self.assertEqual({"V", "S"}, {s["code"].split()[0] for s in legal})
        self.assertTrue(all(s["sourceProvision"].startswith("Příloha č.") for s in legal))


if __name__ == "__main__":
    unittest.main()
