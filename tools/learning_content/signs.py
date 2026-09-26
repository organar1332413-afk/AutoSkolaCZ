"""Compile the Ministry-approved VL 6.1 index into a reviewable sign inventory.

VL 6.1 is a graphical specification, not a consolidated legal definition of
sign meanings. The generated inventory deliberately does not invent meanings,
translations, image licenses, or question associations.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTENT = ROOT / "content" / "learning" / "signs"
VL_2019_SHA256 = "f0deebfe11495ce1fa17fb28773e38b2e4efef93ec768cd15e7e378f33aa251d"
VL_2025_SHA256 = "9f5a8d99b3362e82cce497b3a5046ffa9435684b3028b3aafec899082424029d"
PREFIXES = {
    "A": "warning", "P": "priority", "B": "prohibition",
    "C": "mandatory", "IZ": "information_zone", "IP": "information_traffic",
    "IS": "information_direction", "IJ": "information_other", "E": "additional_panel",
}
INDEX_ROW = re.compile(
    r"^\s*6\.1\s+(A|P|B|C|IZ|IP|IS|IJ|E)\s*([0-9]+[a-z]?)"
    r"(.*?)\s+07/2019\s*$", re.M
)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def canonical_bytes(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")


def parse_index(text: str) -> list[dict]:
    """Parse the printed index (PDF pages 11–17), preserving official names.

    The index groups some graphic variants on one line. A row is an indexed
    sign family; its printed variant range is retained and not silently
    expanded into fabricated individual names.
    """
    pages = text.split("\f")
    if len(pages) < 18:
        raise ValueError("VL 6.1 index pages missing")
    out: dict[str, dict] = {}
    for page_index in range(10, 17):
        page = pages[page_index]
        for match in INDEX_ROW.finditer(page):
            prefix, number, tail = match.groups()
            code = f"{prefix} {number}"
            tail = tail.strip()
            # Printed numeric variants follow an en dash. Letter ranges are
            # preserved as the printed scope pending individual sheet review.
            title = re.sub(r"^(?:[–-]\s*\d+\s*(?:až|a)\s*\d+\s*)", "", tail).strip()
            title = re.sub(r"^(?:(?:až|a|,)\s*(?:\d+[a-z]?|[a-z])\s*|[–-]\s*\d+\s*(?:a|až)\s*\d+\s*)", "", title).strip()
            title = re.sub(r"^[–-]\s*\d+\s*(?:a|až)\s*\d+\s*", "", title).strip()
            if not title:
                preceding = page[:match.start()].splitlines()
                if preceding:
                    title = preceding[-1].strip()
                # These four rows wrap around the code column in the official
                # printed index. Rejoin the following continuation line.
                if code in {"B 3", "B 13", "B 14", "B 17", "B 19"}:
                    following = next((line.strip() for line in page[match.end():].splitlines() if line.strip()), "")
                    if following and not following.startswith("6.1 "):
                        title += " " + following
            if not title:
                raise ValueError(f"Missing Czech title for {code}")
            record = out.get(code)
            if record is not None:
                if record["titleCs"] != title:
                    record["variantTitles"].append(title)
                record["printedScopes"].append(tail)
                continue
            out[code] = {
                "code": code, "titleCs": title, "category": PREFIXES[prefix],
                "printedScopes": [tail], "variantTitles": [], "sourceIds": ["md-vl-6-1-2019"],
                "sourceIndexPage": page_index + 1,
                "graphic": {"sourceId": "md-vl-6-1-2019", "status": "LICENSE_REVIEW_REQUIRED"},
                "reviewStatus": "INDEX_ONLY", "meaningCs": None,
                "explanationCs": None, "titleRu": None, "titleUk": None,
                "explanationRu": None, "explanationUk": None,
                "driverActions": [], "exceptions": [], "commonMistake": None,
                "confusedWith": [], "questionOfficialIds": [],
            }
    return [out[key] for key in sorted(out, key=lambda code: (PREFIXES_INDEX(code), code))]


def PREFIXES_INDEX(code: str) -> int:
    return list(PREFIXES).index(code.split()[0])


def apply_2025_revision(records: list[dict]) -> list[dict]:
    by_code = {r["code"]: r for r in records}
    # The March 2025 amendment replaces selected graphic sheets, including
    # IZ 5a/b and IP 32; it does not repeal those legal signs.
    revised = {"P 2", "P 3", "P 8", "IZ 5a", "IZ 5b", "IP 1", "IP 9", "IP 28a", "IP 32", "E 4", "E 13"}
    additions = {
        "IZ 10a": ("Sdílená zóna", "information_zone"),
        "IZ 10b": ("Konec sdílené zóny", "information_zone"),
        "IP 13f": ("Parkoviště P+D", "information_traffic"),
        "IJ 15": ("Servisní místo pro sanitaci hygienických zařízení obytných vozidel", "information_other"),
    }
    for code in revised & by_code.keys():
        by_code[code]["graphic"]["sourceId"] = "md-vl-6-1-2025-change-1"
        by_code[code]["sourceIds"].append("md-vl-6-1-2025-change-1")
    for code, (title, category) in additions.items():
        if code in by_code:
            raise ValueError(f"Unexpected duplicate amendment sign: {code}")
        by_code[code] = {
            "code": code, "titleCs": title, "category": category,
            "printedScopes": [], "variantTitles": [], "sourceIds": ["md-vl-6-1-2025-change-1"],
            "sourceIndexPage": 5,
            "graphic": {"sourceId": "md-vl-6-1-2025-change-1", "status": "LICENSE_REVIEW_REQUIRED"},
            "reviewStatus": "INDEX_ONLY", "meaningCs": None,
            "explanationCs": None, "titleRu": None, "titleUk": None,
            "explanationRu": None, "explanationUk": None,
            "driverActions": [], "exceptions": [], "commonMistake": None,
            "confusedWith": [], "questionOfficialIds": [],
        }
    return sorted(by_code.values(), key=lambda r: (PREFIXES_INDEX(r["code"]), r["code"]))


def validate(data: dict, sources: dict) -> dict:
    codes: set[str] = set()
    categories = Counter()
    for sign in data["signs"]:
        code = sign["code"]
        if code in codes:
            raise ValueError(f"Duplicate sign code: {code}")
        codes.add(code)
        if not sign["titleCs"].strip():
            raise ValueError(f"Empty Czech title: {code}")
        if sign["category"] not in PREFIXES.values():
            raise ValueError(f"Invalid category: {code}")
        for source_id in sign["sourceIds"] + [sign["graphic"]["sourceId"]]:
            if source_id not in sources:
                raise ValueError(f"Missing source {source_id} for {code}")
        if sign["graphic"]["status"] not in {"LICENSE_REVIEW_REQUIRED", "VERIFIED"}:
            raise ValueError(f"Invalid image status: {code}")
        if sign["graphic"].get("path"):
            path = Path(sign["graphic"]["path"])
            if path.is_absolute() or ".." in path.parts or not (CONTENT / path).is_file():
                raise ValueError(f"Unsafe or missing image: {code}")
        if sign["reviewStatus"] == "INDEX_ONLY" and any(sign.get(key) for key in ("meaningCs", "explanationCs", "titleRu", "titleUk")):
            raise ValueError(f"Unreviewed explanation: {code}")
        categories[sign["category"]] += 1
    if len(data["signs"]) != data["inventoryCount"]:
        raise ValueError("Inventory count mismatch")
    return {"total": len(codes), "categories": dict(sorted(categories.items())),
            "csTitles": sum(bool(s["titleCs"]) for s in data["signs"]),
            "ruTitles": sum(bool(s["titleRu"]) for s in data["signs"]),
            "ukTitles": sum(bool(s["titleUk"]) for s in data["signs"]),
            "bundledImages": sum(bool(s["graphic"].get("path")) for s in data["signs"]),
            "licenseReviewRequired": sum(s["graphic"]["status"] == "LICENSE_REVIEW_REQUIRED" for s in data["signs"])}


def validate_cards(data: dict, cards: dict, sources: dict, official_ids: set[str] | None = None) -> None:
    inventory = {sign["code"] for sign in data["signs"]}
    seen: set[str] = set()
    for card in cards["cards"]:
        code = card["code"]
        if code not in inventory or code in seen:
            raise ValueError(f"Missing or duplicate catalog code in teaching cards: {code}")
        seen.add(code)
        if card["reviewStatus"] != "MINISTRY_GUIDANCE_VERIFIED":
            raise ValueError(f"Unreviewed teaching card: {code}")
        if not all(card.get(k, "").strip() for k in ("meaningCs", "simpleCs", "ru", "uk")):
            raise ValueError(f"Incomplete CS/RU/UK teaching card: {code}")
        if not card.get("sourceIds") or any(source_id not in sources for source_id in card["sourceIds"]):
            raise ValueError(f"Missing card source: {code}")
        for official_id in card.get("questionOfficialIds", []):
            if official_ids is None or official_id not in official_ids:
                raise ValueError(f"Question link lacks bank evidence: {code} / {official_id}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--vl2019", type=Path, help="Official VL 6.1 2019 PDF")
    parser.add_argument("--vl2025", type=Path, help="Official VL 6.1 change 1 PDF")
    parser.add_argument("--check", action="store_true", help="Validate committed catalog and audit")
    args = parser.parse_args()
    sources = json.loads((CONTENT / "sources.json").read_text(encoding="utf-8"))
    if args.check:
        data = json.loads((CONTENT / "catalog.json").read_text(encoding="utf-8"))
        audit = validate(data, sources)
        validate_cards(data, json.loads((CONTENT / "curated.json").read_text(encoding="utf-8")), sources)
        if canonical_bytes(audit) != (CONTENT / "audit.json").read_bytes():
            raise ValueError("Audit drift")
        print(audit)
        return
    if not args.vl2019 or not args.vl2025:
        parser.error("Both official PDFs are required for regeneration")
    for path, expected in ((args.vl2019, VL_2019_SHA256), (args.vl2025, VL_2025_SHA256)):
        if sha256(path) != expected:
            raise ValueError(f"Unexpected official PDF SHA-256: {path}")
    import subprocess
    text = subprocess.check_output(["pdftotext", "-layout", str(args.vl2019), "-"], text=True)
    records = apply_2025_revision(parse_index(text))
    data = {"schemaVersion": 1, "scope": "ministry-graphic-index-inventory",
            "verifiedAt": "2026-09-26", "inventoryCount": len(records), "signs": records}
    audit = validate(data, sources)
    CONTENT.mkdir(parents=True, exist_ok=True)
    (CONTENT / "catalog.json").write_bytes(canonical_bytes(data))
    (CONTENT / "audit.json").write_bytes(canonical_bytes(audit))
    print(audit)


if __name__ == "__main__":
    main()
