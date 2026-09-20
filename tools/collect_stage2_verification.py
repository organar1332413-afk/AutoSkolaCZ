#!/usr/bin/env python3
"""Collect current Gradle outputs; never substitute archived evidence for a new run."""
from pathlib import Path
import json
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
TEST_OUTPUTS = {
    "domain": ("core/domain/build/test-results/test", 37),
    "data": ("core/data/build/test-results/testDebugUnitTest", 10),
    "app": ("app/build/test-results/testDebugUnitTest", 5),
}


def test_results(directory: Path, minimum: int) -> dict:
    files = sorted(directory.glob("TEST-*.xml"))
    if not files:
        raise ValueError(f"Missing current test reports: {directory}")
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    suites = []
    for file in files:
        suite = ET.parse(file).getroot()
        if suite.tag != "testsuite":
            raise ValueError(f"Unexpected test report: {file}")
        for key in totals:
            totals[key] += int(suite.attrib[key])
        suites.append(suite.attrib["name"])
    if totals["tests"] < minimum or any(totals[k] for k in ("failures", "errors", "skipped")):
        raise ValueError(f"Incomplete or failing tests: {directory}: {totals}")
    return dict(totals, suites=suites)


def lint_results(file: Path) -> dict:
    if not file.is_file():
        raise ValueError(f"Missing current Lint report: {file}")
    report = ET.parse(file).getroot()
    if report.tag != "issues":
        raise ValueError(f"Unexpected Lint report: {file}")
    issues = [dict(id=i.attrib["id"], severity=i.attrib["severity"],
                   message=i.attrib.get("message", "")) for i in report.findall("issue")]
    errors = [i for i in issues if i["severity"].lower() in ("error", "fatal")]
    if errors:
        raise ValueError(f"Lint errors in {file}: {errors}")
    return {"errors": 0, "warnings": sum(i["severity"].lower() == "warning" for i in issues),
            "issues": issues}


def collect(root: Path) -> dict:
    tests = {name: test_results(root / path, minimum)
             for name, (path, minimum) in TEST_OUTPUTS.items()}
    lint = {variant: lint_results(root / f"app/build/reports/lint-results-{variant}.xml")
            for variant in ("debug", "release")}
    return {"tests": tests, "lint": lint,
            "tests_total": sum(group["tests"] for group in tests.values()),
            "instrumentation": "Not evaluated here; see separate device test job",
            "physical_device_ui_and_tts": "Not evaluated"}


if __name__ == "__main__":
    try:
        result = collect(ROOT)
        result["commit"] = subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
        result["source"] = "Current build directories; not docs/verification-stage2"
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except (ValueError, ET.ParseError, KeyError) as error:
        raise SystemExit(str(error))
