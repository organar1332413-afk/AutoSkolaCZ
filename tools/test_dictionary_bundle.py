"""Validate the real local dictionary, separate from sign paragraph translations."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "app/src/main/assets/content/dictionary-v1.json"

class DictionaryBundleTest(unittest.TestCase):
    def test_single_common_asset_serves_all_variants(self):
        self.assertEqual([ASSET], list((ROOT / "app/src").rglob("dictionary-v1.json")))

    def test_dictionary_is_nonempty_and_has_both_languages(self):
        words = json.loads(ASSET.read_text())
        self.assertTrue(words)
        self.assertEqual(len(words), len({word["id"] for word in words}))
        for word in words:
            with self.subTest(word=word["id"]):
                self.assertTrue(word["lemma"].strip())
                self.assertEqual({"ru", "uk"}, {text["locale"] for text in word["translations"]})
                self.assertTrue(all(text["translation"].strip() for text in word["translations"]))

    def test_forms_are_real_nonempty_entries(self):
        for word in json.loads(ASSET.read_text()):
            with self.subTest(word=word["id"]):
                self.assertIn(word["lemma"], word["forms"])
                self.assertEqual(len(word["forms"]), len(set(word["forms"])))
                self.assertTrue(all(form.strip() for form in word["forms"]))

if __name__ == "__main__":
    unittest.main()
