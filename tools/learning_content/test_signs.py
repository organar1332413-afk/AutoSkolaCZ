import copy
import json
import tempfile
import unittest
from pathlib import Path

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
        self.assertEqual(350, audit["total"])
        self.assertEqual(350, audit["csTitles"])
        self.assertEqual(347, audit["indexedFamilies"])
        self.assertEqual(44, audit["warningLegalCodesVerified"])
        self.assertEqual(49, audit["warningGraphicExecutionsIndexed"])
        self.assertEqual(8, audit["priorityLegalCodesVerified"])
        self.assertEqual(13, audit["priorityGraphicExecutionsIndexed"])
        self.assertEqual(34, audit["mandatoryLegalCodesVerified"])
        self.assertEqual(51, audit["mandatoryGraphicExecutionsIndexed"])
        self.assertIsNone(audit["canonicalVariants"])
        self.assertEqual(40, audit["categories"]["road_marking"])
        self.assertEqual(29, audit["categories"]["light_signal"])
        self.assertEqual(0, audit["bundledImages"])

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
        self.assertEqual(130, len(self.cards["cards"]))
        first_four = {s["code"] for s in self.data["signs"]
                      if s["category"] in {"warning", "priority", "prohibition", "mandatory"}}
        reviewed = {c["code"] for c in self.cards["cards"] if c["code"] in first_four}
        self.assertEqual(first_four, reviewed)

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
        self.assertEqual(13, len(signs["B 20a"]["graphicVariantCodes"]))
        self.assertEqual("Zákaz vjezdu vozidel, jejichž šířka přesahuje vyznačenou mez",
                         signs["B 15"]["titleCs"])

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
        self.assertEqual(69, len(legal))
        self.assertEqual({"V", "S"}, {s["code"].split()[0] for s in legal})
        self.assertTrue(all(s["sourceProvision"].startswith("Příloha č.") for s in legal))


if __name__ == "__main__":
    unittest.main()
