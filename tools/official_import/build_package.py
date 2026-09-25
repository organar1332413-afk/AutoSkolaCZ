"""Compile reviewed source files into Autoškola CZ content format v2.

This is deliberately not a parser for an undocumented Ministry export or web API.
The reviewed input schema and trust boundary are documented in docs/IMPORT.md.
"""

import argparse
import hashlib
import json
import re
import shutil
from datetime import date, datetime
from pathlib import Path


GROUPS = ("A", "B", "BE", "C", "CE", "D", "DE")
BLUEPRINT = "etesty-2026-09-v1"
SECTIONS = {"rules": (10, 2), "safe_driving": (4, 2), "signs": (3, 1),
            "situations": (3, 4), "vehicle": (2, 1), "related": (2, 2),
            "first_aid": (1, 1)}
IMAGE_MIME = {"image/png", "image/jpeg", "image/webp"}
OFFICIAL = "https://etesty.md.gov.cz/"
ID = re.compile(r"[A-Za-z0-9_-]+\Z")
MEDIA_PATH = re.compile(r"media/[A-Za-z0-9_-]+\.[A-Za-z0-9]+\Z")


class IntakeError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise IntakeError(message)


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def read_json(path):
    def distinct_keys(pairs):
        obj = {}
        for key, value in pairs:
            require(key not in obj, f"Duplicate JSON key: {key}")
            obj[key] = value
        return obj
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=distinct_keys)


def official_url(value):
    return isinstance(value, str) and value.startswith(OFFICIAL)


def group(value):
    require(value in GROUPS, f"Unsupported licence group: {value}")
    return value


def verify_review(source_dir):
    review = read_json(source_dir / "review.json")
    require(set(review) == {"sourceSha256", "exportSha256", "questionsSha256",
                            "eligibilitySha256", "coverageSha256", "media", "reviewedAt", "reviewedBy"},
            "Unexpected review manifest fields")
    require(isinstance(review["reviewedAt"], str) and review["reviewedAt"].strip()
            and isinstance(review["reviewedBy"], str) and review["reviewedBy"].strip(),
            "Review attribution is required")
    for name, key in (("source.json", "sourceSha256"), ("questions.json", "questionsSha256"),
                      ("eligibility.json", "eligibilitySha256"), ("coverage.json", "coverageSha256")):
        require(re.fullmatch(r"[a-f0-9]{64}", review[key]) is not None
                and sha256((source_dir / name).read_bytes()) == review[key],
                f"Reviewed input changed: {name}")
    require(isinstance(review["media"], list), "Invalid reviewed media inventory")
    paths = []
    for item in review["media"]:
        require(set(item) == {"path", "sha256", "mimeType"}
                and isinstance(item["path"], str) and MEDIA_PATH.fullmatch(item["path"])
                and isinstance(item["sha256"], str) and re.fullmatch(r"[a-f0-9]{64}", item["sha256"])
                and isinstance(item["mimeType"], str), "Invalid reviewed media")
        paths.append(item["path"])
    require(paths == sorted(set(paths)), "Reviewed media inventory must have unique sorted paths")
    return review


def safe_media(source_dir, item, answer_codes):
    require(set(item) == {"path", "mimeType"} or set(item) == {"path", "mimeType", "answerCode"},
            "Unexpected media fields")
    path = item["path"]
    require(isinstance(path, str) and MEDIA_PATH.fullmatch(path), f"Unsafe media path: {path}")
    require(item["answerCode"] in answer_codes if "answerCode" in item else True,
            "Media answerCode does not exist")
    require(re.fullmatch(r"(image|video)/[a-z0-9.+-]+", item["mimeType"]) is not None,
            "Unsupported media MIME syntax")
    file = source_dir / path
    require(file.is_file() and not file.is_symlink() and file.resolve().is_relative_to(source_dir.resolve()),
            f"Missing or unsafe media: {path}")
    require(file.stat().st_size <= 50 * 1024 * 1024, f"Media exceeds 50 MiB: {path}")
    return {**item, "sha256": sha256(file.read_bytes())}


def compile_package(source_dir):
    source_dir = Path(source_dir)
    review = verify_review(source_dir)
    meta = read_json(source_dir / "source.json")
    questions = read_json(source_dir / "questions.json")
    mapping_path = source_dir / "eligibility.json"
    mapping_bytes = mapping_path.read_bytes()
    mappings = read_json(mapping_path)
    coverage = read_json(source_dir / "coverage.json")
    require(set(meta) == {"databaseVersion", "publicationDate", "retrievedAt", "source",
                          "exportFile", "exportSha256"}, "Unexpected source metadata fields")
    require(isinstance(meta["databaseVersion"], str) and meta["databaseVersion"].strip()
            and official_url(meta["source"]), "Invalid source metadata")
    require(isinstance(meta["publicationDate"], str)
            and re.fullmatch(r"\d{4}-\d{2}-\d{2}", meta["publicationDate"]), "Invalid publication date")
    date.fromisoformat(meta["publicationDate"])
    require(isinstance(meta["retrievedAt"], str)
            and re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})",
                             meta["retrievedAt"]), "Invalid retrieval timestamp")
    require(datetime.fromisoformat(meta["retrievedAt"].replace("Z", "+00:00")).tzinfo is not None,
            "retrievedAt needs a timezone")
    export_name = meta["exportFile"]
    require(isinstance(export_name, str) and ID.fullmatch(export_name.rsplit(".", 1)[0])
            and Path(export_name).name == export_name, "Unsafe export filename")
    export_file = source_dir / export_name
    require(export_file.is_file() and not export_file.is_symlink()
            and re.fullmatch(r"[a-f0-9]{64}", meta["exportSha256"])
            and sha256(export_file.read_bytes()) == meta["exportSha256"] == review["exportSha256"],
            "Reviewed export hash mismatch")
    require(isinstance(questions, list) and questions, "Questions must be a nonempty array")
    require(isinstance(mappings, list) and isinstance(coverage, list), "Invalid evidence arrays")

    indexed = {}
    media_paths = {}
    for raw in questions:
        require(set(raw) == {"officialId", "category", "textCs", "points", "answers", "media", "source"},
                "Unexpected question fields")
        qid = raw["officialId"]
        require(isinstance(qid, str) and ID.fullmatch(qid) and qid not in indexed,
                f"Duplicate or invalid official ID: {qid}")
        require(raw["category"] in SECTIONS and isinstance(raw["textCs"], str)
                and raw["textCs"].strip() and type(raw["points"]) is int
                and raw["points"] in (1, 2, 4) and official_url(raw["source"]),
                f"Invalid question: {qid}")
        answers = raw["answers"]
        require(isinstance(answers, list) and len(answers) in (2, 3)
                and [a.get("code") for a in answers] == list("ABC")[:len(answers)]
                and all(set(a) == {"code", "textCs", "correct"}
                        and isinstance(a["textCs"], str) and a["textCs"].strip()
                        and type(a["correct"]) is bool for a in answers)
                and sum(a["correct"] for a in answers) == 1, f"Invalid answers: {qid}")
        require(isinstance(raw["media"], list), f"Invalid media: {qid}")
        media = [safe_media(source_dir, m, {a["code"] for a in answers}) for m in raw["media"]]
        for item in media:
            prior = media_paths.get(item["path"])
            require(prior is None or prior == (item["sha256"], item["mimeType"]),
                    f"Conflicting media reference: {item['path']}")
            media_paths[item["path"]] = (item["sha256"], item["mimeType"])
        indexed[qid] = {**raw, "media": media, "eligibility": [], "translations": []}

    mapping_pairs = set()
    for row in mappings:
        require(set(row) == {"officialId", "licenceGroup", "source", "evidenceRef"},
                "Unexpected eligibility fields")
        qid, code = row["officialId"], group(row["licenceGroup"])
        require(qid in indexed and official_url(row["source"])
                and isinstance(row["evidenceRef"], str) and row["evidenceRef"].strip(),
                f"Unverifiable eligibility: {qid}/{code}")
        require((qid, code) not in mapping_pairs, f"Duplicate eligibility: {qid}/{code}")
        mapping_pairs.add((qid, code))
        indexed[qid]["eligibility"].append({"licenceGroup": code, "source": row["source"]})

    attestations = {}
    for row in coverage:
        require(set(row) == {"licenceGroup", "blueprintVersion", "eligibilityComplete",
                             "mappingSha256", "exportSha256", "officialIds", "source", "evidenceRef"},
                "Unexpected coverage fields")
        code = group(row["licenceGroup"])
        require(code not in attestations and type(row["eligibilityComplete"]) is bool
                and row["blueprintVersion"] == BLUEPRINT and official_url(row["source"])
                and isinstance(row["evidenceRef"], str) and row["evidenceRef"].strip(),
                f"Invalid coverage attestation: {code}")
        ids = row["officialIds"]
        require(isinstance(ids, list) and ids == sorted(set(ids))
                and all(isinstance(i, str) for i in ids), f"Invalid coverage inventory: {code}")
        require(row["mappingSha256"] == sha256(mapping_bytes)
                and row["exportSha256"] == meta["exportSha256"],
                f"Coverage evidence hash mismatch: {code}")
        actual = sorted(qid for qid, c in mapping_pairs if c == code)
        require(ids == actual, f"Coverage inventory mismatch: {code}")
        attestations[code] = row

    result = []
    for code in GROUPS:
        claim = attestations.get(code)
        eligible = [q for q in indexed.values() if any(e["licenceGroup"] == code for e in q["eligibility"])]
        content_complete = all(sum(q["category"] == category and q["points"] == points
                                   for q in eligible) >= count
                               for category, (count, points) in SECTIONS.items())
        media_complete = all(m["mimeType"] in IMAGE_MIME for q in eligible for m in q["media"])
        result.append({"licenceGroup": code, "blueprintVersion": BLUEPRINT,
                       "eligibilityComplete": bool(claim and claim["eligibilityComplete"]),
                       "contentComplete": content_complete,
                       "mediaComplete": media_complete,
                       "source": claim["source"] if claim else meta["source"]})

    for q in indexed.values():
        q["eligibility"].sort(key=lambda e: GROUPS.index(e["licenceGroup"]))
    actual_media = [{"path": path, "sha256": media_paths[path][0], "mimeType": media_paths[path][1]}
                    for path in sorted(media_paths)]
    require(review["media"] == actual_media, "Referenced media differs from reviewed inventory")
    package = {"manifest": {"formatVersion": 2, "databaseVersion": meta["databaseVersion"],
                            "publicationDate": meta["publicationDate"], "source": meta["source"],
                            "retrievedAt": meta["retrievedAt"], "sample": False,
                            "groupReadiness": result},
               "questions": [indexed[k] for k in sorted(indexed)]}
    report = {"exportSha256": meta["exportSha256"], "mappingSha256": sha256(mapping_bytes),
              "reviewSha256": sha256((source_dir / "review.json").read_bytes()),
              "reviewedAt": review["reviewedAt"], "reviewedBy": review["reviewedBy"],
              "questionsSha256": sha256((source_dir / "questions.json").read_bytes()),
              "coverageSha256": sha256((source_dir / "coverage.json").read_bytes()),
              "questionCount": len(indexed), "eligibilityCount": len(mapping_pairs),
              "eligibilityEvidence": [{"officialId": row["officialId"], "licenceGroup": row["licenceGroup"],
                                       "evidenceRef": row["evidenceRef"]} for row in mappings],
              "coverageEvidence": [{"licenceGroup": code, "evidenceRef": row["evidenceRef"],
                                    "officialIdCount": len(row["officialIds"]),
                                    "eligibilityComplete": row["eligibilityComplete"]}
                                   for code, row in sorted(attestations.items())],
              "readiness": result, "mediaPaths": sorted(media_paths),
              "warning": "Hashes bind reviewed inputs; source authenticity and group coverage require human verification."}
    return package, report


def write_output(source_dir, output_dir):
    source_dir, output_dir = Path(source_dir), Path(output_dir)
    require(not output_dir.exists() or not any(output_dir.iterdir()), "Output directory must be empty")
    require(not output_dir.resolve().is_relative_to(source_dir.resolve()), "Output cannot be inside input")
    package, report = compile_package(source_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    encoded = (json.dumps(package, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    require(len(encoded) <= 20 * 1024 * 1024, "Package exceeds Android importer 20 MiB limit")
    (output_dir / "package-v2.json").write_bytes(encoded)
    (output_dir / "audit.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for path in report["mediaPaths"]:
        destination = output_dir / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source_dir / path, destination)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="Reviewed source directory")
    parser.add_argument("output", type=Path, help="Empty output directory")
    args = parser.parse_args()
    try:
        write_output(args.input, args.output)
    except (IntakeError, ValueError, OSError, KeyError, TypeError) as exc:
        parser.exit(1, f"Intake rejected: {exc}\n")


if __name__ == "__main__":
    main()
