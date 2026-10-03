"""Coverage/provenance guard for translations of existing sign teaching text."""
import hashlib
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1] / "content/learning/signs"

class SignDetailTranslationsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original = (ROOT / "curated.json").read_bytes()
        cls.cards = json.loads(cls.original)["cards"]
        cls.companion = json.loads((ROOT / "detail-translations.json").read_text())
        cls.rows = cls.companion["texts"]

    def test_companion_is_bound_to_unchanged_source_and_is_not_claimed_official(self):
        self.assertEqual("curated.json", self.companion["sourceFile"])
        self.assertEqual(hashlib.sha256(self.original).hexdigest(), self.companion["sourceSha256"])
        self.assertEqual("TRANSLATION_DRAFT", self.companion["reviewStatus"])
        self.assertEqual(408, len(self.cards))

    def test_every_existing_action_memory_and_extra_phrase_has_both_helpers(self):
        expected = {card[field] for card in self.cards
                    for field in ("driverActionsCs", "memoryCs", "mistakeCs", "simpleCs")}
        self.assertEqual(expected, {row["cs"] for row in self.rows})
        self.assertEqual(len(expected), len(self.rows), "Duplicate Czech lookup keys")
        for row in self.rows:
            for locale in ("ru", "uk"):
                with self.subTest(cs=row["cs"], locale=locale):
                    self.assertTrue(row[locale].strip())
                    self.assertRegex(row[locale], r"[А-Яа-яІіЇїЄє]", "Cyrillic helper required")
                    self.assertEqual(re.findall(r"\d+", row["cs"]), re.findall(r"\d+", row[locale]),
                                     "Do not invent/change numeric conditions or sign references")

    def test_existing_title_and_meaning_translations_remain_available_for_all_cards(self):
        for card in self.cards:
            for field in ("titleRu", "titleUk", "ru", "uk"):
                self.assertTrue(card[field].strip(), (card["code"], field))

if __name__ == "__main__":
    unittest.main()
