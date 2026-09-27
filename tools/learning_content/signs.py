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


def image_mime(path: Path) -> str | None:
    with path.open("rb") as image:
        start = image.read(4096)
    if start.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"
    if start.startswith(b"\xff\xd8\xff"):
        return "image/jpeg"
    if start[:4] == b"RIFF" and start[8:12] == b"WEBP":
        return "image/webp"
    if re.search(rb"<svg(?:\s|>)", start):
        return "image/svg+xml"
    return None


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


def reconcile_mandatory_annex(records: list[dict]) -> list[dict]:
    """Split the two legal snow-chain codes grouped on one 2019 VL index row."""
    by_code = {s["code"]: s for s in records}
    if "C 5a" not in by_code or "C 5b" in by_code:
        raise ValueError("Unexpected C 5a/5b grouping")
    original = by_code["C 5a"]
    by_code["C 5b"] = {
        **original, "code": "C 5b", "titleCs": "Sněhové řetězy - konec",
        "printedScopes": ["a 5b       Sněhové řetězy"],
        "sourceIds": ["decree-294-2015", "md-vl-6-1-2019"],
        "reviewStatus": "LEGAL_INDEX_ONLY",
    }
    by_code["C 6b"]["titleCs"] = "Konec nejnižší dovolené rychlosti"
    by_code["C 13b"]["titleCs"] = "Rozsviť světla - konec"
    by_code["C 15b"]["titleCs"] = "Zimní výbava - konec"
    mandatory = {code: sign for code, sign in by_code.items() if sign["category"] == "mandatory"}
    if len(mandatory) != 34:
        raise ValueError("Annex 4 legal code count mismatch")
    sheet_counts = {"C 6a": 9, "C 6b": 9, "C 14a": 2}
    for sign in mandatory.values():
        code = sign["code"]
        sign["familyCode"] = "C 5" if code in {"C 5a", "C 5b"} else code
        sign["graphicVariantCodes"] = ([f"{code}-{n}" for n in range(1, sheet_counts[code] + 1)]
                                       if code in sheet_counts else [code])
        sign["sourceIds"] = list(dict.fromkeys(["decree-294-2015"] + sign["sourceIds"]))
        sign["sourceProvision"] = f"Příloha č. 4 k vyhlášce č. 294/2015 Sb., {code}"
    return sorted(by_code.values(), key=lambda r: (PREFIXES_INDEX(r["code"]), r["code"]))


def reconcile_zone_annex(records: list[dict]) -> list[dict]:
    """Apply Annex 5(1) titles and expose graphic sheet variants."""
    zones = {s["code"]: s for s in records if s["category"] == "information_zone"}
    if len(zones) != 22:
        raise ValueError("Annex 5(1) zone code count mismatch")
    corrected = {
        "IZ 2a": "Silnice pro motorová vozidla",
        "IZ 2b": "Konec silnice pro motorová vozidla",
        "IZ 7a": "Nízkoemisní zóna",
        "IZ 7b": "Konec nízkoemisní zóny",
    }
    for code, title in corrected.items():
        zones[code]["titleCs"] = title
    sheet_counts = {
        "IZ 4a": 2, "IZ 4b": 2, "IZ 6a": 4, "IZ 6b": 4,
        "IZ 7a": 2, "IZ 7b": 2, "IZ 8a": 8, "IZ 8b": 4,
        "IZ 9a": 4, "IZ 9b": 4, "IZ 10a": 2, "IZ 10b": 2,
    }
    for sign in zones.values():
        code = sign["code"]
        sign["familyCode"] = code
        sign["graphicVariantCodes"] = (
            [f"{code}-{n}" for n in range(1, sheet_counts[code] + 1)]
            if code in sheet_counts else [code])
        sign["sourceIds"] = list(dict.fromkeys(["decree-294-2015"] + sign["sourceIds"]))
        sign["sourceProvision"] = f"Příloha č. 5, bod 1 k vyhlášce č. 294/2015 Sb., {code}"
        if code in {"IZ 7a", "IZ 7b"}:
            # 2025 decree changed the sign's representation; the old VL
            # sheet must not be presented as the current legal illustration.
            sign["graphic"]["sourceId"] = "decree-294-2015"
            sign["graphic"]["status"] = "VERSION_REVIEW_REQUIRED"
    return records


def reconcile_traffic_annex(records: list[dict]) -> list[dict]:
    """Annex 5(2) defines separate legal codes hidden by the old VL index."""
    by_code = {sign["code"]: sign for sign in records}
    additions = {
        "IP 1": "Okruh",
        "IP 4b": "Jednosměrný provoz",
        "IP 4c": "Jednosměrný provoz s povoleným provozem cyklistů v protisměru",
        "IP 11c": "Parkoviště podélné stání",
        "IP 11d": "Parkoviště stání na chodníku kolmé nebo šikmé",
        "IP 11e": "Parkoviště stání na chodníku podélné",
        "IP 11f": "Parkoviště částečné stání na chodníku kolmé nebo šikmé",
        "IP 11g": "Parkoviště částečné stání na chodníku podélné",
        "IP 23b": "Objíždění tramvaje (jízda podél tramvaje vlevo)",
    }
    for code, title in additions.items():
        if code in by_code:
            raise ValueError(f"Unexpected existing legal sign: {code}")
        template = by_code["IP 11b"] if code.startswith("IP 11") else by_code.get("IP 4a")
        sign = dict(template)
        sign.update(code=code, titleCs=title, printedScopes=[], variantTitles=[],
                    sourceIndexPage=None, reviewStatus="LEGAL_INDEX_ONLY",
                    sourceIds=["decree-294-2015"], familyCode=code,
                    graphicVariantCodes=[code],
                    graphic={"sourceId": "decree-294-2015", "status": "LICENSE_REVIEW_REQUIRED"})
        by_code[code] = sign
    corrected = {
        "IP 11b": "Parkoviště kolmé nebo šikmé stání",
        "IP 22": "Změna organizace dopravy",
        "IP 23c": "Sjíždění vozidel veřejné hromadné dopravy osob z tramvajového pásu",
        "IP 28a": "Zpoplatnění provozu",
        "IP 28b": "Nejvyšší dovolené rychlosti",
    }
    for code, title in corrected.items():
        by_code[code]["titleCs"] = title
    expected = set(additions) | {sign["code"] for sign in records if sign["category"] == "information_traffic"}
    traffic = {code: sign for code, sign in by_code.items() if sign["category"] == "information_traffic"}
    if set(traffic) != expected or len(traffic) != 47:
        raise ValueError("Annex 5(2) legal-code reconciliation failed")
    for code, sign in traffic.items():
        sign["familyCode"] = code
        sign["graphicVariantCodes"] = [code]
        sign["sourceIds"] = list(dict.fromkeys(["decree-294-2015"] + sign["sourceIds"]))
        sign["sourceProvision"] = f"Příloha č. 5, bod 2 k vyhlášce č. 294/2015 Sb., {code}"
        if code == "IP 4c":
            sign["sourceIds"].append("decree-205-2025")
    return sorted(by_code.values(), key=lambda sign: (PREFIXES_INDEX(sign["code"]), sign["code"]))


def reconcile_direction_annex(records: list[dict]) -> list[dict]:
    """Expand legal IS codes combined into one printed VL index row."""
    groups = {
        "1abc": "Směrová tabule pro příjezd k dálnici (přímo, vlevo nebo vpravo)",
        "1def": "Směrová tabule před nájezdem na dálnici (přímo, vlevo nebo vpravo)",
        "2abc": "Směrová tabule pro příjezd k silnici pro motorová vozidla (přímo, vlevo nebo vpravo)",
        "2def": "Směrová tabule před nájezdem na silnici pro motorová vozidla (přímo, vlevo nebo vpravo)",
        "3abc": "Směrová tabule s cílem (přímo, vlevo nebo vpravo)",
        "4abc": "Směrová tabule s místním cílem (přímo, vlevo nebo vpravo)",
        "5": "Směrová tabule s jiným cílem", "6a": "Označení křižovatky",
        "6bd": "Návěst před křižovatkou", "6e": "Směrová návěst pro směr přímo",
        "6fg": "Směrová návěst před odbočením",
        "7a": "Směrová návěst pro odbočení", "7b": "Směrová tabule pro výjezd",
        "8a": "Dálková návěst s šipkou", "8b": "Dálková návěst se vzdálenostmi",
        "9a": "Návěst před úrovňovou křižovatkou",
        "9b": "Návěst před okružní křižovatkou",
        "9cde": "Návěst před křižovatkou s omezením",
        "10ab": "Návěst změny směru jízdy",
        "10c": "Návěst změny směru jízdy před překážkou",
        "10d": "Návěst změny směru jízdy s omezením",
        "11a": "Návěst před objížďkou",
        "11bcd": "Směrová tabule pro vyznačení objížďky",
        "12abc": "Směrová tabule pro náhradní trasu dálnice (přímo, vlevo nebo vpravo)",
        "12d": "Náhradní trasa", "13": "Blízká návěst",
        "14": "Hranice územního celku", "15ab": "Jiný název",
        "16a": "Číslo dálnice", "16b": "Číslo silnice",
        "17": "Číslo silnice pro mezinárodní provoz",
        "18ab": "Kilometrovník",
        "19abc": "Směrová tabule pro cyklisty (přímo, vlevo nebo vpravo)",
        "20": "Návěst pro cyklisty",
        "21abc": "Směrová tabulka pro cyklisty (přímo, vlevo nebo vpravo)",
        "21d": "Konec cyklistické trasy",
        "22abcdef": "Označení názvu ulice nebo jiného veřejného prostranství",
        "23": "Návěst pro kulturní nebo turistický cíl",
        "24a": "Kulturní nebo turistický cíl",
        "24b": "Směrová tabule pro kulturní nebo turistický cíl",
        "24c": "Komunální cíl",
    }
    legal_titles: dict[str, str] = {}
    for group, title in groups.items():
        match = re.fullmatch(r"(\d+)([a-z]+)?", group)
        assert match is not None
        number, suffixes = match.groups()
        for suffix in suffixes if suffixes else ("",):
            code = f"IS {number}{suffix}"
            if code in legal_titles:
                raise ValueError(f"Duplicate Annex 5(3) code: {code}")
            legal_titles[code] = title
    if len(legal_titles) != 73:
        raise ValueError("Annex 5(3) code count changed")
    by_code = {sign["code"]: sign for sign in records}
    indexed = {code for code, sign in by_code.items() if sign["category"] == "information_direction"}
    if indexed - legal_titles.keys():
        raise ValueError(f"VL direction code absent in legal annex: {indexed - legal_titles.keys()}")
    template = by_code["IS 1a"]
    for code, title in legal_titles.items():
        if code not in by_code:
            sign = dict(template)
            sign.update(code=code, printedScopes=[], variantTitles=[],
                        sourceIndexPage=None, reviewStatus="LEGAL_INDEX_ONLY",
                        sourceIds=["decree-294-2015"],
                        graphic={"sourceId": "decree-294-2015", "status": "LICENSE_REVIEW_REQUIRED"})
            by_code[code] = sign
        sign = by_code[code]
        sign["titleCs"] = title
        sign["familyCode"] = "IS " + re.match(r"\d+", code.split()[1]).group()
        sign["graphicVariantCodes"] = [code]
        sign["sourceIds"] = list(dict.fromkeys(["decree-294-2015"] + sign["sourceIds"]))
        sign["sourceProvision"] = f"Příloha č. 5, bod 3 k vyhlášce č. 294/2015 Sb., {code}"
    return sorted(by_code.values(), key=lambda sign: (PREFIXES_INDEX(sign["code"]), sign["code"]))


def reconcile_other_info_annex(records: list[dict]) -> list[dict]:
    """Check every Annex 5(4) IJ code and its current legal title."""
    titles = {
        "1": "Policie", "2": "Nemocnice", "3": "První pomoc",
        "4a": "Označník zastávky", "4b": "Označník zastávky",
        "4c": "Zastávka autobusu", "4d": "Zastávka tramvaje",
        "4e": "Zastávka trolejbusu", "5": "Informace", "6": "Telefon",
        "7": "Čerpací stanice", "8": "Opravna", "9": "Stanice technické kontroly",
        "10": "Hotel nebo motel", "11a": "Restaurace", "11b": "Občerstvení",
        "12": "WC", "13": "Místo pro odpočinek",
        "14a": "Tábořiště pro stany", "14b": "Tábořiště pro obytné přívěsy",
        "14c": "Tábořiště pro stany a pro obytné přívěsy",
        "15": "Servisní místo pro sanitaci hygienických zařízení obytných vozidel",
        "16": "Silniční kaple", "17a": "Truckpark",
        "17b": "Návěst před truckparkem",
        "18a": "Návěst před odpočívkou",
        "18b": "Návěst před odbočením na odpočívku",
        "18c": "Návěst pro odbočení na odpočívku",
    }
    by_code = {sign["code"]: sign for sign in records}
    if "IJ 4b" in by_code or len(titles) != 28:
        raise ValueError("Annex 5(4) grouped-stop inventory changed")
    sign = dict(by_code["IJ 4a"])
    sign.update(code="IJ 4b", titleCs=titles["4b"], printedScopes=[],
                variantTitles=[], sourceIndexPage=None, reviewStatus="LEGAL_INDEX_ONLY",
                sourceIds=["decree-294-2015"],
                graphic={"sourceId": "decree-294-2015", "status": "LICENSE_REVIEW_REQUIRED"})
    by_code["IJ 4b"] = sign
    actual = {code for code, sign in by_code.items() if sign["category"] == "information_other"}
    expected = {f"IJ {suffix}" for suffix in titles}
    if actual != expected:
        raise ValueError(f"Annex 5(4) mismatch: {actual ^ expected}")
    for suffix, title in titles.items():
        code = f"IJ {suffix}"
        sign = by_code[code]
        sign["titleCs"] = title
        sign["familyCode"] = "IJ 4" if suffix.startswith("4") else code
        sign["graphicVariantCodes"] = [code]
        sign["sourceIds"] = list(dict.fromkeys(["decree-294-2015"] + sign["sourceIds"]))
        sign["sourceProvision"] = f"Příloha č. 5, bod 4 k vyhlášce č. 294/2015 Sb., {code}"
    return sorted(by_code.values(), key=lambda sign: (PREFIXES_INDEX(sign["code"]), sign["code"]))


def reconcile_panel_annex(records: list[dict]) -> list[dict]:
    """Split legal E codes that VL prints under a common graphical heading."""
    titles = {
        "1": "Počet", "2a": "Tvar křižovatky", "2b": "Tvar křižovatky",
        "2c": "Tvar křižovatky", "2d": "Tvar dvou křižovatek",
        "3a": "Vzdálenost", "3b": "Vzdálenost", "4": "Délka úseku",
        "5": "Největší povolená hmotnost", "6": "Za mokra (za deště)",
        "7a": "Směrová šipka pro směr přímo",
        "7b": "Směrová šipka pro odbočení",
        "8a": "Začátek úseku", "8b": "Průběh úseku",
        "8c": "Konec úseku", "8d": "Úsek platnosti",
        "8e": "Úsek platnosti", "9": "Druh vozidla",
        "10": "Tvar křížení pozemní komunikace s dráhou",
        "11a": "Bez časového poplatku", "11b": "S časovým poplatkem",
        "11c": "Bez mýtného", "11d": "S mýtným",
        "11e": "Bez časového poplatku a mýtného",
        "11f": "S časovým poplatkem a mýtným",
        "12a": "Jízda cyklistů v protisměru",
        "12b": "Vjezd cyklistů v protisměru povolen",
        "12c": "Povolený směr jízdy cyklistů",
        "13": "Text nebo symbol", "14": "Tranzit", "15": "Kategorie tunelu",
        "16": "Vzdálenost k příští čerpací stanici",
        "17": "Nedostatečný průjezdní profil vozovky",
    }
    by_code = {sign["code"]: sign for sign in records}
    grouped = {"E 2b": "E 2a", "E 2c": "E 2a",
               "E 3b": "E 3a", "E 8e": "E 8d"}
    if len(titles) != 33 or set(grouped) & by_code.keys():
        raise ValueError("Annex 6 printed grouping changed")
    for code, existing in grouped.items():
        sign = dict(by_code[existing])
        sign.update(code=code, printedScopes=[], variantTitles=[],
                    sourceIndexPage=None, reviewStatus="LEGAL_INDEX_ONLY",
                    sourceIds=["decree-294-2015"],
                    graphic={"sourceId": "decree-294-2015", "status": "LICENSE_REVIEW_REQUIRED"})
        by_code[code] = sign
    actual = {code for code, sign in by_code.items() if sign["category"] == "additional_panel"}
    expected = {f"E {suffix}" for suffix in titles}
    if actual != expected:
        raise ValueError(f"Annex 6 mismatch: {actual ^ expected}")
    for suffix, title in titles.items():
        code = f"E {suffix}"
        sign = by_code[code]
        sign["titleCs"] = title
        sign["familyCode"] = "E " + re.match(r"\d+", suffix).group()
        sign["graphicVariantCodes"] = [code]
        sign["sourceIds"] = list(dict.fromkeys(["decree-294-2015"] + sign["sourceIds"]))
        sign["sourceProvision"] = f"Příloha č. 6 k vyhlášce č. 294/2015 Sb., {code}"
    return sorted(by_code.values(), key=lambda sign: (PREFIXES_INDEX(sign["code"]), sign["code"]))


def reconcile_marking_annex(records: list[dict]) -> list[dict]:
    """The Annex 8 inventory has forty legal V codes in seven sections."""
    markings = {s["code"]: s for s in records if s["category"] == "road_marking"}
    expected = {f"V {n}{letter}" for n, letters in {
        1:"ab", 2:"abc", 6:"ab", 7:"ab", 8:"abc", 9:"abc",
        10:"abcdefg", 11:"ab", 12:"abcde"}.items() for letter in letters}
    expected |= {f"V {n}" for n in (3, 4, 5, 13, 14, 15, 16, 17, 18, 19, 20)}
    if set(markings) != expected or len(markings) != 40:
        raise ValueError(f"Annex 8 mismatch: {set(markings) ^ expected}")
    markings["V 10f"]["titleCs"] = (
        "Vyhrazené parkoviště pro vozidlo přepravující osobu těžce "
        "postiženou nebo osobu těžce pohybově postiženou")
    for code, sign in markings.items():
        sign["familyCode"] = "V " + re.match(r"\d+", code.split()[1]).group()
        sign["graphicVariantCodes"] = [code]
        sign["sourceProvision"] = f"Příloha č. 8 k vyhlášce č. 294/2015 Sb., {code}"
    return records


def reconcile_signal_annex(records: list[dict]) -> list[dict]:
    """Annex 9 names the individual light aspects inside six S families."""
    aspect_titles = {
        "S 1": ["Signál s červeným světlem „Stůj!“",
                "Signál se žlutým světlem „Pozor!“",
                "Signál se zeleným světlem „Volno“"],
        "S 2": ["Signál se směrovou šipkou s červeným světlem „Stůj!“",
                "Signál se směrovou šipkou se žlutým světlem „Pozor!“",
                "Signál se směrovou šipkou se zeleným světlem „Volno“"],
        "S 3": ["Signál s kombinovanou směrovou šipkou s červeným světlem „Stůj!“",
                "Signál s kombinovanou směrovou šipkou se žlutým světlem „Pozor!“",
                "Signál s kombinovanou směrovou šipkou se zeleným světlem „Volno“"],
        "S 9": ["Signál pro chodce se znamením „Stůj!“",
                "Signál pro chodce se znamením „Volno“"],
        "S 10": ["Signál pro cyklisty se znamením „Stůj!“",
                 "Signál pro cyklisty se znamením „Pozor!“",
                 "Signál pro cyklisty se znamením „Volno“"],
        "S 11": ["Signál pro chodce a cyklisty se znamením „Stůj!“",
                 "Signál pro chodce a cyklisty se znamením „Volno“"],
    }
    original = {s["code"]: s for s in records if s["category"] == "light_signal"}
    if len(original) != 29 or not set(aspect_titles) <= original.keys():
        raise ValueError("Unexpected Annex 9 grouped signal inventory")
    by_code = {s["code"]: s for s in records if s["category"] != "light_signal"}
    for code, sign in original.items():
        if code in aspect_titles:
            for index, title in enumerate(aspect_titles[code]):
                aspect_code = code + chr(ord("a") + index)
                aspect = dict(sign)
                aspect.update(code=aspect_code, titleCs=title,
                              printedScopes=[], variantTitles=[],
                              familyCode=code, graphicVariantCodes=[aspect_code],
                              sourceProvision=f"Příloha č. 9 k vyhlášce č. 294/2015 Sb., {aspect_code}")
                by_code[aspect_code] = aspect
        else:
            sign["familyCode"] = "S " + re.match(r"\d+", code.split()[1]).group()
            sign["graphicVariantCodes"] = [code]
            sign["sourceProvision"] = f"Příloha č. 9 k vyhlášce č. 294/2015 Sb., {code}"
            by_code[code] = sign
    if len([s for s in by_code.values() if s["category"] == "light_signal"]) != 39:
        raise ValueError("Annex 9 atomic aspect count changed")
    return sorted(by_code.values(), key=lambda sign: (PREFIXES_INDEX(sign["code"]), sign["code"]))


def validate(data: dict, sources: dict) -> dict:
    codes: set[str] = set()
    categories = Counter()
    used_graphics: set[Path] = set()
    graphic_hashes: dict[str, tuple[str, Path, bool]] = {}
    graphics_root = (CONTENT / "graphics").resolve()
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
        if sign["graphic"]["status"] not in {"LICENSE_REVIEW_REQUIRED", "VERSION_REVIEW_REQUIRED", "VERIFIED"}:
            raise ValueError(f"Invalid image status: {code}")
        graphic = sign["graphic"]
        if graphic.get("path"):
            path = Path(graphic["path"])
            if (path.is_absolute() or len(path.parts) < 2 or path.parts[0] != "graphics" or
                any(not re.fullmatch(r"[a-zA-Z0-9._-]+", part) or part in {".", ".."}
                    for part in path.parts)):
                raise ValueError(f"Unsafe or missing image: {code}")
            candidate = CONTENT / path
            if not candidate.is_file() or graphics_root not in candidate.resolve().parents:
                raise ValueError(f"Unsafe or missing image: {code}")
            if graphic["status"] != "VERIFIED":
                raise ValueError(f"Unverified image cannot be bundled: {code}")
            expected = graphic.get("sha256", "")
            if not re.fullmatch(r"[a-f0-9]{64}", expected) or sha256(candidate) != expected:
                raise ValueError(f"Image hash mismatch: {code}")
            if graphic.get("mime") != image_mime(candidate):
                raise ValueError(f"Image MIME mismatch: {code}")
            other = graphic_hashes.get(expected)
            if other and (other[1] != path or not other[2] or not graphic.get("shared", False)):
                raise ValueError(f"Unexplained duplicate image hash: {other[0]} / {code}")
            graphic_hashes[expected] = (code, path, bool(graphic.get("shared", False)))
            used_graphics.add(candidate.resolve())
        elif graphic["status"] == "VERIFIED":
            raise ValueError(f"Verified image missing local file: {code}")
        if sign["reviewStatus"] in {"INDEX_ONLY", "LEGAL_INDEX_ONLY"} and any(sign.get(key) for key in ("meaningCs", "explanationCs", "titleRu", "titleUk")):
            raise ValueError(f"Unreviewed explanation: {code}")
        if sign["reviewStatus"] == "LEGAL_INDEX_ONLY" and not sign.get("sourceProvision"):
            raise ValueError(f"Missing legal appendix: {code}")
        if sign.get("sourceProvision", "").split(",")[-1].strip() != code:
            raise ValueError(f"Sign legal provision is not code-specific: {code}")
        categories[sign["category"]] += 1
    if graphics_root.exists():
        orphans = {path.resolve() for path in graphics_root.rglob("*") if path.is_file()} - used_graphics
        if orphans:
            raise ValueError(f"Orphan sign graphics: {len(orphans)}")
    if len(data["signs"]) != data["inventoryCount"]:
        raise ValueError("Inventory count mismatch")
    return {"total": len(codes), "categories": dict(sorted(categories.items())),
            "csTitles": sum(bool(s["titleCs"]) for s in data["signs"]),
            "ruTitles": sum(bool(s["titleRu"]) for s in data["signs"]),
            "ukTitles": sum(bool(s["titleUk"]) for s in data["signs"]),
            "bundledImages": sum(bool(s["graphic"].get("path")) for s in data["signs"]),
            "licenseReviewRequired": sum(s["graphic"]["status"] != "VERIFIED" for s in data["signs"])}


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
        if any(re.search(r"[\u0400-\u04ff]", card[key]) for key in
               ("titleCs", "meaningCs", "simpleCs", "mistakeCs", "memoryCs", "driverActionsCs")):
            raise ValueError(f"Non-Czech script in Czech teaching field: {code}")
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
    audit["mandatoryLegalCodesVerified"] = sum(s["category"] == "mandatory" for s in data["signs"])
    audit["mandatoryGraphicExecutionsIndexed"] = sum(
        len(s["graphicVariantCodes"]) for s in data["signs"] if s["category"] == "mandatory")
    audit["zoneLegalCodesVerified"] = sum(s["category"] == "information_zone" for s in data["signs"])
    audit["zoneGraphicExecutionsIndexed"] = sum(
        len(s["graphicVariantCodes"]) for s in data["signs"] if s["category"] == "information_zone")
    audit["trafficLegalCodesVerified"] = sum(
        s["category"] == "information_traffic" for s in data["signs"])
    audit["trafficGraphicExecutionsIndexed"] = sum(
        len(s["graphicVariantCodes"]) for s in data["signs"] if s["category"] == "information_traffic")
    audit["directionLegalCodesVerified"] = sum(
        s["category"] == "information_direction" for s in data["signs"])
    audit["directionGraphicExecutionsIndexed"] = sum(
        len(s["graphicVariantCodes"]) for s in data["signs"] if s["category"] == "information_direction")
    audit["otherInfoLegalCodesVerified"] = sum(
        s["category"] == "information_other" for s in data["signs"])
    audit["panelLegalCodesVerified"] = sum(
        s["category"] == "additional_panel" for s in data["signs"])
    audit["markingLegalCodesVerified"] = sum(
        s["category"] == "road_marking" for s in data["signs"])
    audit["signalAtomicAspectsIndexed"] = sum(
        s["category"] == "light_signal" for s in data["signs"])
    audit["graphicVersionReviewRequired"] = sum(
        s["graphic"]["status"] == "VERSION_REVIEW_REQUIRED" for s in data["signs"])
    audit["canonicalFamilies"] = None
    audit["canonicalVariants"] = None
    audit["atomicLegalEntries"] = audit["total"]
    audit["totalCards"] = len(cards["cards"])
    audit["ruTitles"] = sum(bool(card["titleRu"]) for card in cards["cards"])
    audit["ukTitles"] = sum(bool(card["titleUk"]) for card in cards["cards"])
    audit["completeCardsCs"] = sum(all(card.get(k) for k in ("titleCs", "meaningCs", "simpleCs")) for card in cards["cards"])
    audit["completeCardsRu"] = sum(all(card.get(k) for k in ("titleRu", "ru")) for card in cards["cards"])
    audit["completeCardsUk"] = sum(all(card.get(k) for k in ("titleUk", "uk")) for card in cards["cards"])
    audit["completeCardsAllLanguages"] = sum(all(card.get(k) for k in
        ("titleRu", "titleUk", "meaningCs", "simpleCs", "ru", "uk", "sourceProvision", "checkedAt"))
        for card in cards["cards"])
    audit["graphicsRequired"] = audit["total"]
    audit["graphicsPresent"] = audit["bundledImages"]
    audit["graphicsMissing"] = audit["total"] - audit["bundledImages"]
    audit["graphicsMissingCodes"] = sorted(s["code"] for s in data["signs"]
                                            if not s["graphic"].get("path"))
    audit["graphicLicenseReviewRequiredCodes"] = sorted(s["code"] for s in data["signs"]
        if s["graphic"]["status"] == "LICENSE_REVIEW_REQUIRED")
    audit["graphicVersionReviewRequiredCodes"] = sorted(s["code"] for s in data["signs"]
        if s["graphic"]["status"] == "VERSION_REVIEW_REQUIRED")
    audit["graphicLicenseVerified"] = sum(s["graphic"]["status"] == "VERIFIED"
                                         for s in data["signs"])
    audit["graphicLicenseReviewRequired"] = len(audit["graphicLicenseReviewRequiredCodes"])
    audit["unreviewedCodes"] = sorted({s["code"] for s in data["signs"]} -
                                      {card["code"] for card in cards["cards"]})
    audit["legalVerified"] = len(cards["cards"])
    audit["unreviewed"] = audit["unreviewedTeachingCards"]
    audit["linkedQuestionsVerified"] = len({link["officialId"] for c in cards["cards"]
        for link in c.get("questionLinks", []) if link["reviewStatus"] == "VERIFIED"})
    audit["linkedRelationshipsVerified"] = sum(link["reviewStatus"] == "VERIFIED" for c in cards["cards"]
        for link in c.get("questionLinks", []))
    audit["linkedQuestionsReviewRequired"] = sum(link["reviewStatus"] == "REVIEW_REQUIRED" for c in cards["cards"]
        for link in c.get("questionLinks", []))
    audit["productionReady"] = (not audit["unreviewedCodes"] and
                                not audit["graphicsMissingCodes"] and
                                not audit["graphicLicenseReviewRequiredCodes"] and
                                not audit["graphicVersionReviewRequiredCodes"] and
                                audit["canonicalVariants"] is not None)
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
    records = apply_2025_revision(parse_index(text))
    for reconcile in (add_legal_appendices, reconcile_warning_annex,
                      reconcile_priority_annex, reconcile_prohibition_annex,
                      reconcile_mandatory_annex, reconcile_zone_annex,
                      reconcile_traffic_annex, reconcile_direction_annex,
                      reconcile_other_info_annex, reconcile_panel_annex,
                      reconcile_marking_annex, reconcile_signal_annex):
        records = reconcile(records)
    data = {"schemaVersion": 1, "scope": "ministry-graphic-index-inventory",
            "verifiedAt": "2026-09-27", "inventoryCount": len(records), "signs": records}
    audit = full_audit(data, sources)
    CONTENT.mkdir(parents=True, exist_ok=True)
    (CONTENT / "catalog.json").write_bytes(canonical_bytes(data))
    (CONTENT / "audit.json").write_bytes(canonical_bytes(audit))
    print(audit)


if __name__ == "__main__":
    main()
