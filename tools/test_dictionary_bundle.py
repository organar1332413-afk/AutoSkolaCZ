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


class FullSignVocabularyTest(unittest.TestCase):
    def test_full_production_corpus_has_no_missing_data(self):
        from sign_vocabulary import audit
        words=json.loads(ASSET.read_text())
        metrics,missing,forms=audit(words)
        self.assertEqual(408,metrics['sign_cards_scanned'])
        self.assertEqual(1802,metrics['tappable_text_fields_scanned'])
        self.assertEqual(11313,metrics['token_occurrences'])
        self.assertEqual(2275,metrics['unique_normalized_forms'])
        failures=[]
        for kind,tokens in missing.items():
            for token in tokens:
                for row in forms[token]:failures.append(f"{token}: {kind}: {row['code']}/{row['field']}: {row['cs']}")
        self.assertFalse(failures,'\n'.join(failures))
        for key in ['duplicate_lemmas','duplicate_forms','conflicting_mappings']:self.assertEqual(0,metrics[key],key)

    def test_authored_source_reproduces_packaged_asset(self):
        from build_sign_dictionary import build
        self.assertEqual(json.loads(ASSET.read_text()),build())

    def test_every_entry_is_normalized_finite_and_has_short_context(self):
        import re,unicodedata
        from sign_vocabulary import normalize,tokens,corpus
        _,corpus_forms=corpus()
        words=json.loads(ASSET.read_text())
        for word in words:
            with self.subTest(lemma=word['lemma']):
                self.assertEqual(normalize(word['lemma']),word['lemma'])
                self.assertEqual(len(word['forms']),len(set(word['forms'])))
                self.assertTrue(any(f in corpus_forms for f in word['forms']))
                self.assertTrue(word['exampleCs'].strip())
                for form in word['forms']:
                    self.assertEqual(normalize(form),form)
                    self.assertEqual(form,unicodedata.normalize('NFC',form))
                    self.assertNotRegex(form,r'[\d_\ufffd]|Ã|Ä|Å')
                    self.assertEqual([form],list(tokens(form)),f'Technical token: {form}')
                    self.assertTrue(form in corpus_forms or form==word['lemma'],f'Unobserved surface form: {form}')
                for text in word['translations']:
                    for field in ['translation','meaning']:
                        self.assertTrue(text[field].strip(),field)
                        self.assertRegex(text[field],r'[А-Яа-яІіЇїЄєҐґ]')
                        self.assertNotRegex(text[field],r'\ufffd|TODO|TBD|Перевод пока недоступен|Переклад поки недоступний|n/a')
                    self.assertNotEqual(text['translation'],text['meaning'])
                    self.assertLessEqual(len(text['translation']),90)
                    self.assertLessEqual(len(text['meaning']),145)
                    self.assertNotIn('\n',text['meaning'])

    def test_tokenizer_excludes_identifiers_but_keeps_czech_prepositions(self):
        from sign_vocabulary import tokens,normalize
        self.assertEqual(['chodci','tvar','bus'],list(tokens('A 12a · Chodci, (Tvar). V 2b; https://example.cz/road 408 ID_abc km m BUS.')))
        self.assertEqual(['v','s','a','řidič'],list(tokens('v 2 s 3 a řidič')))
        self.assertEqual('tvar',normalize('(TVAR,)'))

if __name__ == "__main__":
    unittest.main()
