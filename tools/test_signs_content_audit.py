"""Coverage and failure-mode checks for the production content audit guard."""
import copy
import csv
import unittest

from validate_signs_content_audit import (
    AUDIT_DIR, ROOT, AuditValidationError, load_bundle, validate, validate_core,
    validate_csv, validate_summary,
)


class SignsContentAuditTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.bundle = load_bundle()

    def changed_audit(self, mutate):
        bundle = dict(self.bundle)
        bundle['audit'] = copy.deepcopy(bundle['audit'])
        mutate(bundle['audit'])
        return bundle

    def assert_rejected(self, mutate, expected):
        with self.assertRaisesRegex(AuditValidationError, expected):
            validate_core(**self.changed_audit(mutate))

    def test_complete_checked_in_audit_and_exports(self):
        validate()

    def test_reject_missing_record(self):
        self.assert_rejected(lambda a: a['records'].pop(), 'Exactly 408')

    def test_reject_duplicate_code(self):
        self.assert_rejected(lambda a: a['records'][-1].update(sign_code=a['records'][0]['sign_code']),
                             'Duplicate audit')

    def test_reject_unknown_code(self):
        self.assert_rejected(lambda a: a['records'][-1].update(sign_code='B 999'), 'Missing/unknown')

    def test_reject_missing_confidence_or_verdict(self):
        for field in ('confidence', 'overall', 'legal', 'pedagogy'):
            with self.subTest(field=field):
                self.assert_rejected(lambda a: a['records'][0].pop(field), f'missing {field}')

    def test_reject_inconsistent_verdict(self):
        self.assert_rejected(lambda a: a['records'][0].update(legal='LEGAL_WRONG'), 'inconsistent LEGAL')

    def test_reject_risk_wrong_without_reason(self):
        for verdict in ('RISK', 'WRONG'):
            with self.subTest(verdict=verdict):
                self.assert_rejected(lambda a: next(r for r in a['records'] if r['overall'] == verdict)
                                     .update(reasons=[]), 'requires a reason')

    def test_reject_each_required_proposal_and_language(self):
        for verdict in ('IMPROVE', 'RISK', 'WRONG'):
            with self.subTest(verdict=verdict):
                self.assert_rejected(lambda a: next(r for r in a['records'] if r['overall'] == verdict)
                                     .update(proposed_correction=[]), 'proposed correction required')
        for lang in ('cs', 'ru', 'ua'):
            with self.subTest(lang=lang):
                self.assert_rejected(lambda a: next(r for r in a['records'] if r['overall'] == 'RISK')
                                     ['proposed_correction'][0].update({lang: ' '}), f'corrected {lang}')

    def test_reject_changed_production(self):
        bundle = dict(self.bundle)
        bundle['current_hashes'] = dict(bundle['current_hashes'])
        bundle['current_hashes']['content/learning/signs/curated.json'] = '0' * 64
        with self.assertRaisesRegex(AuditValidationError, 'Production files differ'):
            validate_core(**bundle)

    def test_reject_missing_review_dimension_or_fabricated_citation(self):
        self.assert_rejected(lambda a: a['records'][0]['review_checks'].pop('ua_semantics'), 'review dimensions')
        self.assert_rejected(lambda a: a['records'][0]['official'].update(citation_url='https://example.com'),
                             'wrong official citation')

    def test_reject_csv_confidence_drift(self):
        with (ROOT / AUDIT_DIR / 'signs-content-audit.csv').open(encoding='utf-8', newline='') as f:
            reader = csv.DictReader(f)
            rows, columns = list(reader), reader.fieldnames
        rows[0]['confidence'] = 'LOW'
        with self.assertRaisesRegex(AuditValidationError, 'CSV differs'):
            validate_csv(self.bundle['audit']['records'], rows, columns)

    def test_reject_summary_missing_risk_and_wrong_count(self):
        summary = (ROOT / AUDIT_DIR / 'signs-content-audit-summary.md').read_text(encoding='utf-8')
        code = next(r['sign_code'] for r in self.bundle['audit']['records'] if r['overall'] == 'RISK')
        changed = summary.replace(f'| {code} |', '| B 999 |', 1)
        with self.assertRaisesRegex(AuditValidationError, 'RISK list differs'):
            validate_summary(self.bundle['audit'], changed)
        changed = summary.replace('| HIGH | 389 |', '| HIGH | 388 |')
        with self.assertRaisesRegex(AuditValidationError, 'Summary count differs'):
            validate_summary(self.bundle['audit'], changed)


if __name__ == '__main__':
    unittest.main()
