"""Regression checks for delivery gates; these are not Android test results."""
from pathlib import Path
import tempfile
import unittest
from collect_stage2_verification import collect, lint_results, test_results as read_tests


class VerificationGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.addCleanup(self.temp.cleanup)

    def suite(self, **overrides):
        attributes = dict(name="fixture", tests="52", failures="0", errors="0", skipped="0")
        attributes.update(overrides)
        text = " ".join(f'{k}="{v}"' for k, v in attributes.items())
        (self.root / "TEST-fixture.xml").write_text(f"<testsuite {text}/>")

    def test_accepts_complete_passing_suite(self):
        self.suite()
        self.assertEqual(read_tests(self.root, 52)["tests"], 52)

    def test_rejects_missing_or_incomplete_tests(self):
        with self.assertRaises(ValueError):
            read_tests(self.root, 52)
        self.suite(tests="51")
        with self.assertRaises(ValueError):
            read_tests(self.root, 52)

    def test_rejects_failures_errors_and_skips(self):
        for key in ("failures", "errors", "skipped"):
            with self.subTest(key=key):
                self.suite(**{key: "1"})
                with self.assertRaises(ValueError):
                    read_tests(self.root, 52)

    def test_lint_warnings_retained_and_errors_rejected(self):
        file = self.root / "lint.xml"
        file.write_text('<issues><issue id="UseTomlInstead" severity="Warning" message="fixture"/></issues>')
        self.assertEqual(lint_results(file)["warnings"], 1)
        for severity in ("Error", "Fatal"):
            file.write_text(f'<issues><issue id="fixture" severity="{severity}"/></issues>')
            with self.assertRaises(ValueError):
                lint_results(file)

    def test_missing_lint_is_not_success(self):
        with self.assertRaises(ValueError):
            lint_results(self.root / "missing.xml")

    def test_does_not_substitute_archived_results(self):
        archive = self.root / "docs/verification-stage2"
        archive.mkdir(parents=True)
        (archive / "TEST-old.xml").write_text('<testsuite name="old" tests="52" failures="0" errors="0" skipped="0"/>')
        with self.assertRaises(ValueError):
            collect(self.root)


if __name__ == "__main__":
    unittest.main()
