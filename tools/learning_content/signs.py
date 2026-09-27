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
    "V": "road_marking", "S": "light_signal",
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


def add_legal_appendices(records: list[dict]) -> list[dict]:
    appendix = json.loads((CONTENT / "legal_appendix_inventory.json").read_text(encoding="utf-8"))
    if appendix["effectiveFrom"] != "2025-07-01" or appendix["sourceId"] != "decree-294-2015":
        raise ValueError("Legal appendix provenance changed")
    seen = {item["code"] for item in records}
    for item in appendix["items"]:
        code = item["code"]
        if code in seen or code.split()[0] not in {"V", "S"}:
            raise ValueError(f"Duplicate or unsupported appendix code: {code}")
        seen.add(code)
        records.append({
            "code": code, "titleCs": item["titleCs"], "category": item["category"],
            "sourceProvision": item["sourceProvision"], "printedScopes": [], "variantTitles": [],
            "sourceIds": [appendix["sourceId"]], "sourceIndexPage": None,
            "graphic": {"sourceId": appendix["sourceId"], "status": "LICENSE_REVIEW_REQUIRED"},
            "reviewStatus": "LEGAL_INDEX_ONLY", "meaningCs": None, "explanationCs": None,
            "titleRu": None, "titleUk": None, "explanationRu": None, "explanationUk": None,
            "driverActions": [], "exceptions": [], "commonMistake": None,
            "confusedWith": [], "questionOfficialIds": [],
        })
    return sorted(records, key=lambda r: (PREFIXES_INDEX(r["code"]), r["code"]))


def reconcile_warning_annex(records: list[dict]) -> list[dict]:
    """Distinguish legal A 31a–c signs from VL sheets of one legal code.

    The 2019 VL index prints A 31a až c on one line. Annex 1 of the
    consolidated decree defines three separate codes. Conversely, the
    numbered VL sheets of A 6b/13/14/32a/32b are graphic executions of
    one legal code, not new legal sign codes.
    """
    by_code = {sign["code"]: sign for sign in records}
    if "A 31a" not in by_code or "A 31b" in by_code:
        raise ValueError("Unexpected A 31 legal/graphic inventory")
    for code, distance in (("A 31a", 240), ("A 31b", 160), ("A 31c", 80)):
        sign = dict(by_code["A 31a"]) if code != "A 31a" else by_code[code]
        sign["code"] = code
        sign["titleCs"] = f"Návěstní deska ({distance} m)"
        sign["sourceIds"] = ["decree-294-2015", "md-vl-6-1-2019"]
        sign["sourceProvision"] = f"Příloha č. 1 k vyhlášce č. 294/2015 Sb., {code}"
        sign["familyCode"] = "A 31"
        sign["graphicVariantCodes"] = [code]
        sign["reviewStatus"] = "LEGAL_INDEX_ONLY"
        by_code[code] = sign
    for code in ("A 6b", "A 13", "A 14", "A 32a", "A 32b"):
        sign = by_code[code]
        sign["graphicVariantCodes"] = [f"{code}-1", f"{code}-2"]
        sign["familyCode"] = code
    by_code["A 32a"]["titleCs"] = "Výstražný kříž pro železniční přejezd jednokolejný"
    by_code["A 32b"]["titleCs"] = "Výstražný kříž pro železniční přejezd vícekolejný"
    for sign in by_code.values():
        if sign["category"] == "warning":
            sign.setdefault("familyCode", sign["code"] if not sign["code"].startswith("A 31") else "A 31")
            sign.setdefault("graphicVariantCodes", [sign["code"]])
            sign.setdefault("sourceProvision", f"Příloha č. 1 k vyhlášce č. 294/2015 Sb., {sign['code']}")
    return sorted(by_code.values(), key=lambda r: (PREFIXES_INDEX(r["code"]), r["code"]))


def reconcile_priority_annex(records: list[dict]) -> list[dict]:
    """Annex 2 has eight legal codes; VL numbered sheets are graphic variants."""
    priority = {s["code"]: s for s in records if s["category"] == "priority"}
    if set(priority) != {f"P {n}" for n in range(1, 9)}:
        raise ValueError("Unexpected Annex 2 priority inventory")
    for code, count in (("P 4", 3), ("P 5", 2), ("P 6", 3)):
        priority[code]["graphicVariantCodes"] = [f"{code}-{n}" for n in range(1, count + 1)]
    for sign in priority.values():
        sign["familyCode"] = sign["code"]
        sign.setdefault("graphicVariantCodes", [sign["code"]])
        sign["sourceIds"] = list(dict.fromkeys(["decree-294-2015"] + sign["sourceIds"]))
        sign["sourceProvision"] = f"Příloha č. 2 k vyhlášce č. 294/2015 Sb., {sign['code']}"
    return records


def reconcile_prohibition_annex(records: list[dict]) -> list[dict]:
    """Reconcile Annex 3 legal codes, titles and printed VL sheet variants."""
    prohibited = {s["code"]: s for s in records if s["category"] == "prohibition"}
    expected = ({f"B {n}" for n in range(1, 20)} |
                {f"B {n}{suffix}" for n in (20, 21, 22, 23, 24) for suffix in "ab"} |
                {f"B {n}" for n in range(25, 35)} | {"B 30a"})
    if set(prohibited) != expected:
        raise ValueError(f"Annex 3 disagreement: {sorted(set(prohibited) ^ expected)}")
    corrected_titles = {
        "B 15": "Zákaz vjezdu vozidel, jejichž šířka přesahuje vyznačenou mez",
        "B 16": "Zákaz vjezdu vozidel, jejichž výška přesahuje vyznačenou mez",
        "B 18": "Zákaz vjezdu vozidel přepravujících nebezpečný náklad",
        "B 19": "Zákaz vjezdu vozidel přepravujících náklad, který může způsobit ohrožení životního prostředí",
    }
    for code, title in corrected_titles.items():
        prohibited[code]["titleCs"] = title
    sheet_counts = {"B 1": 3, "B 2": 3, "B 4": 6, "B 13": 5,
                    "B 20a": 13, "B 20b": 13, "B 27": 2, "B 32": 2, "B 34": 2}
    for sign in prohibited.values():
        code = sign["code"]
        sign["familyCode"] = code
        sign["graphicVariantCodes"] = (
            [f"{code}-{n}" for n in range(1, sheet_counts[code] + 1)]
            if code in sheet_counts else [code])
        sign["sourceIds"] = list(dict.fromkeys(["decree-294-2015"] + sign["sourceIds"]))
        sign["sourceProvision"] = f"Příloha č. 3 k vyhlášce č. 294/2015 Sb., {code}"
    return records


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
        if sign["reviewStatus"] in {"INDEX_ONLY", "LEGAL_INDEX_ONLY"} and any(sign.get(key) for key in ("meaningCs", "explanationCs", "titleRu", "titleUk")):
            raise ValueError(f"Unreviewed explanation: {code}")
        if sign["reviewStatus"] == "LEGAL_INDEX_ONLY" and not sign.get("sourceProvision"):
            raise ValueError(f"Missing legal appendix: {code}")
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
    if official_ids is None:
        refs = json.loads((CONTENT / "official_question_refs.json").read_text(encoding="utf-8"))
        ordered = refs["officialIds"]
        if (ordered != sorted(set(ordered)) or len(ordered) != refs["officialIdsCount"] or
            sha256_bytes(canonical_bytes(ordered)) != refs["officialIdsSha256"]):
            raise ValueError("Official question reference inventory drift")
        official_ids = set(ordered)
    inventory = {sign["code"]: sign for sign in data["signs"]}
    seen: set[str] = set()
    for card in cards["cards"]:
        code = card["code"]
        if code not in inventory or code in seen:
            raise ValueError(f"Missing or duplicate catalog code in teaching cards: {code}")
        seen.add(code)
        if card["reviewStatus"] not in {"MINISTRY_GUIDANCE_VERIFIED", "LEGAL_ANNEX_VERIFIED"}:
            raise ValueError(f"Unreviewed teaching card: {code}")
        if card["reviewStatus"] == "LEGAL_ANNEX_VERIFIED" and not card.get("sourceProvision", "").strip():
            raise ValueError(f"Missing legal provision: {code}")
        if card.get("titleCs") != inventory[code]["titleCs"]:
            raise ValueError(f"Teaching card changes official Czech title: {code}")
        if not all(card.get(k, "").strip() for k in
            ("titleRu", "titleUk", "meaningCs", "simpleCs", "ru", "uk", "mistakeCs",
             "memoryCs", "driverActionsCs", "sourceProvision", "checkedAt")):
            raise ValueError(f"Incomplete CS/RU/UK teaching card: {code}")
        if not re.fullmatch(r"20\d\d-\d\d-\d\d", card["checkedAt"]):
            raise ValueError(f"Invalid review date: {code}")
        if card["sourceProvision"].split(",")[-1].strip() != code:
            raise ValueError(f"Unmatched legal provision: {code}")
        if "decree-294-2015" not in card.get("sourceIds", []):
            raise ValueError(f"Legal source absent from card: {code}")
        if any(other not in inventory for other in card.get("confusedWith", [])):
            raise ValueError(f"Unknown compared sign: {code}")
        if not card.get("sourceIds") or any(source_id not in sources for source_id in card["sourceIds"]):
            raise ValueError(f"Missing card source: {code}")
        if card.get("questionOfficialIds"):
            raise ValueError(f"Legacy question links lack review evidence: {code}")
        linked: set[str] = set()
        for link in card.get("questionLinks", []):
            official_id = link["officialId"]
            if official_id in linked or official_id not in official_ids:
                raise ValueError(f"Question link lacks bank evidence: {code} / {official_id}")
            linked.add(official_id)
            if link["signId"] != code or link["reviewStatus"] not in {"VERIFIED", "REVIEW_REQUIRED", "REJECTED"}:
                raise ValueError(f"Invalid question relationship: {code} / {official_id}")
            evidence = link.get("evidence", {})
            if link["reviewStatus"] == "VERIFIED":
                if (link["evidenceType"] != "EXPLICIT_CODE_IN_OFFICIAL_QUESTION_TEXT" or
                    re.search(rf"(?<!\w){re.escape(code)}(?!\w)", evidence.get("questionTextCs", "")) is None or
                    not re.fullmatch(r"[a-f0-9]{64}", evidence.get("rawResponseSha256", "")) or
                    not evidence.get("sourceUrl", "").startswith("https://etesty.md.gov.cz/")):
                    raise ValueError(f"Question link not explicitly verified: {code} / {official_id}")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def validate_guide(guide: dict, sources: dict) -> None:
    if guide["reviewStatus"] != "LEGAL_TEXT_CHECKED" or guide["topic"] != "11":
        raise ValueError("Unreviewed signs guide")
    if not guide["sourceIds"] or any(source_id not in sources for source_id in guide["sourceIds"]):
        raise ValueError("Guide source missing")
    seen: set[str] = set()
    for block in guide["blocks"]:
        if block["id"] in seen:
            raise ValueError("Duplicate guide block")
        seen.add(block["id"])
        if not all(block.get(key, "").strip() for key in ("provision", "ruleSummaryCs", "simpleCs", "ru", "uk")):
            raise ValueError(f"Incomplete guide block: {block['id']}")
        if block["officialTextCs"] is not None and not block["officialTextCs"].strip():
            raise ValueError(f"Empty official legal excerpt: {block['id']}")


def full_audit(data: dict, sources: dict) -> dict:
    audit = validate(data, sources)
    cards = json.loads((CONTENT / "curated.json").read_text(encoding="utf-8"))
    validate_cards(data, cards, sources)
    audit["teachingCardsCsRuUk"] = len(cards["cards"])
    audit["unreviewedTeachingCards"] = audit["total"] - len(cards["cards"])
    # These are partial index counts until *every* legal annex is reconciled.
    audit["indexedFamilies"] = len({s.get("familyCode", s["code"]) for s in data["signs"]})
    audit["indexedRows"] = len(data["signs"])
    audit["warningLegalCodesVerified"] = sum(s["category"] == "warning" for s in data["signs"])
    audit["warningGraphicExecutionsIndexed"] = sum(
        len(s["graphicVariantCodes"]) for s in data["signs"] if s["category"] == "warning")
    audit["priorityLegalCodesVerified"] = sum(s["category"] == "priority" for s in data["signs"])
    audit["priorityGraphicExecutionsIndexed"] = sum(
        len(s["graphicVariantCodes"]) for s in data["signs"] if s["category"] == "priority")
    audit["prohibitionLegalCodesVerified"] = sum(s["category"] == "prohibition" for s in data["signs"])
    audit["prohibitionGraphicExecutionsIndexed"] = sum(
        len(s["graphicVariantCodes"]) for s in data["signs"] if s["category"] == "prohibition")
    audit["canonicalFamilies"] = None
    audit["canonicalVariants"] = None
    audit["totalCards"] = len(cards["cards"])
    audit["completeCardsCs"] = sum(all(card.get(k) for k in ("titleCs", "meaningCs", "simpleCs")) for card in cards["cards"])
    audit["completeCardsRu"] = sum(all(card.get(k) for k in ("titleRu", "ru")) for card in cards["cards"])
    audit["completeCardsUk"] = sum(all(card.get(k) for k in ("titleUk", "uk")) for card in cards["cards"])
    audit["completeCardsAllLanguages"] = sum(all(card.get(k) for k in
        ("titleRu", "titleUk", "meaningCs", "simpleCs", "ru", "uk", "sourceProvision", "checkedAt"))
        for card in cards["cards"])
    audit["graphicsRequired"] = audit["total"]
    audit["graphicsPresent"] = audit["bundledImages"]
    audit["graphicsMissing"] = audit["total"] - audit["bundledImages"]
    audit["legalVerified"] = len(cards["cards"])
    audit["unreviewed"] = audit["unreviewedTeachingCards"]
    audit["linkedQuestionsVerified"] = len({link["officialId"] for c in cards["cards"]
        for link in c.get("questionLinks", []) if link["reviewStatus"] == "VERIFIED"})
    audit["linkedRelationshipsVerified"] = sum(link["reviewStatus"] == "VERIFIED" for c in cards["cards"]
        for link in c.get("questionLinks", []))
    audit["linkedQuestionsReviewRequired"] = sum(link["reviewStatus"] == "REVIEW_REQUIRED" for c in cards["cards"]
        for link in c.get("questionLinks", []))
    return audit


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--vl2019", type=Path, help="Official VL 6.1 2019 PDF")
    parser.add_argument("--vl2025", type=Path, help="Official VL 6.1 change 1 PDF")
    parser.add_argument("--check", action="store_true", help="Validate committed catalog and audit")
    args = parser.parse_args()
    sources = json.loads((CONTENT / "sources.json").read_text(encoding="utf-8"))
    if args.check:
        data = json.loads((CONTENT / "catalog.json").read_text(encoding="utf-8"))
        audit = full_audit(data, sources)
        validate_guide(json.loads((CONTENT / "guide.json").read_text(encoding="utf-8")), sources)
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
    records = reconcile_prohibition_annex(reconcile_priority_annex(reconcile_warning_annex(
        add_legal_appendices(apply_2025_revision(parse_index(text))))))
    data = {"schemaVersion": 1, "scope": "ministry-graphic-index-inventory",
            "verifiedAt": "2026-09-26", "inventoryCount": len(records), "signs": records}
    audit = full_audit(data, sources)
    CONTENT.mkdir(parents=True, exist_ok=True)
    (CONTENT / "catalog.json").write_bytes(canonical_bytes(data))
    (CONTENT / "audit.json").write_bytes(canonical_bytes(audit))
    print(audit)


if __name__ == "__main__":
    main()
