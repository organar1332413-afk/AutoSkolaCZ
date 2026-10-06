"""Regression guards for the three confirmed mistakes and the overlooked P 4."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class SignsContentRevisionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        audit = json.loads((ROOT / 'docs/signs-audit/signs-content-audit.json').read_text())
        cls.rows = {row['sign_code']: row for row in audit['records']}

    def text(self, code, language):
        blocks = self.rows[code]['displayed_blocks']
        return ' '.join(b[language] for b in [blocks['meaning'], blocks['action'],
                                              blocks['memory'], *blocks['additional']] if b)

    def test_p4_requires_us_to_yield_and_keeps_conditional_stop(self):
        expected = {'cs': ('Přednost musíte dát vy.', 'pokud'),
                    'ru': ('Уступить дорогу должны вы.', 'если'),
                    'ua': ('Дати дорогу повинні ви.', 'якщо')}
        for lang, (memory, condition) in expected.items():
            with self.subTest(lang=lang):
                self.assertEqual(memory, self.rows['P 4']['displayed_blocks']['memory'][lang])
                self.assertIn(condition, self.text('P 4', lang))
        self.assertNotIn('přednost ano', self.text('P 4', 'cs'))

    def test_a12a_no_unconditional_pedestrian_right(self):
        self.assertNotIn('Chodci nejsou vázáni', self.text('A 12a', 'cs'))
        for lang in ('cs', 'ru', 'ua'):
            self.assertIn('50', self.text('A 12a', lang))
        self.assertIn('Výstraha sama nedává', self.text('A 12a', 'cs'))
        self.assertIn('не даёт', self.text('A 12a', 'ru'))
        self.assertIn('не дає', self.text('A 12a', 'ua'))

    def test_b22a_keeps_both_tractor_conditions_and_binding_lane(self):
        for lang in ('cs', 'ru', 'ua'):
            text = self.text('B 22a', lang)
            self.assertIn('3500', text)
            self.assertIn('80', text)
        self.assertNotIn('обычно', self.text('B 22a', 'ru'))
        self.assertNotIn('зазвичай', self.text('B 22a', 'ua'))
        self.assertIn('objíždění', self.text('B 22a', 'cs'))
        self.assertIn('объезда', self.text('B 22a', 'ru'))
        self.assertIn('об’їзду', self.text('B 22a', 'ua'))

    def test_is6d_cannot_be_overhead_in_any_language(self):
        self.assertIn('nesmí umístit nad vozovku', self.text('IS 6d', 'cs'))
        self.assertIn('нельзя размещать над проезжей частью', self.text('IS 6d', 'ru'))
        self.assertIn('не можна розміщувати над проїзною частиною', self.text('IS 6d', 'ua'))

    def test_revised_cards_have_short_memory_and_no_exact_visible_duplicates(self):
        for code, row in self.rows.items():
            if not row.get('revision'):
                continue
            blocks = row['displayed_blocks']
            self.assertIsNotNone(blocks['memory'], code)
            self.assertLessEqual(len(blocks['memory']['cs'].split()), 12, code)
            for lang in ('cs', 'ru', 'ua'):
                with self.subTest(code=code, lang=lang):
                    texts = [b[lang] for b in [blocks['meaning'], blocks['action'],
                                               blocks['memory'], *blocks['additional']] if b]
                    self.assertEqual(len(texts), len(set(texts)))


if __name__ == '__main__':
    unittest.main()
