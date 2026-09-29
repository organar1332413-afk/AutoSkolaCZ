"""Import remaining sign graphics by resolving public e-Sbírka file ids.

The e-Sbírka portal exposes the authoritative binary through sbr-externi but its
public HTML SPA does not expose the file id in server-rendered markup.  This
offline import helper uses the current consolidated legal table mirrored by
nasezakony.cz only to recover the public e-Sbírka file id + official TIFF
filename.  Every binary byte is then downloaded directly from e-sbirka.gov.cz,
decoded, losslessly converted to WebP, and recorded with its official file id
and SHA-256.

The mirror is mapping evidence only; it is never used as the binary source.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import urllib.request
from collections import defaultdict
from pathlib import Path

from lxml import html
from PIL import Image

from tools.learning_content.signs import CONTENT, canonical_bytes, full_audit

REFERENCE_PAGE = "https://nasezakony.cz/z/dopravni-znacky"
OFFICIAL_BINARY = (
    "https://e-sbirka.gov.cz/sbr-externi/souborove-dokumenty/"
    "{file_id}/ORIGINAL/STAHNI/{filename}"
)
SOURCE_ID = "esbirka-294-2015-current-binary"
CODE_RE = re.compile(r"\b(?:IJ|IP|IS|IZ|A|B|C|E|P|S|V)\s+\d+[a-z]?\b")
FILE_RE = re.compile(r"(sbcr[^\s\"'<>]+\.tif)", re.I)
ID_RE = re.compile(r"/souborove-dokumenty/(\d+)/(?:NAHLED|ZOBRAZ|STAHNI)")
UA = "AutoSkolaCZ sign-source verifier/1.0"


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=90) as response:
        return response.read()


def sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def image_name(anchor) -> str | None:
    values = [
        anchor.text_content(),
        anchor.get("title") or "",
        anchor.get("aria-label") or "",
        anchor.get("alt") or "",
        anchor.get("src") or "",
        anchor.get("href") or "",
    ]
    for img in anchor.xpath(".//img"):
        values.extend([img.get("alt") or "", img.get("title") or "", img.get("src") or ""])
    for value in values:
        match = FILE_RE.search(value)
        if match:
            return match.group(1)
    return None


def codes_for_anchor(anchor) -> list[str]:
    row = anchor.xpath("ancestor::tr[1]")
    if row:
        current = row[0]
        for _ in range(4):
            text = " ".join(current.itertext())
            codes = CODE_RE.findall(text)
            if codes:
                return list(dict.fromkeys(codes))
            current = current.getprevious()
            if current is None:
                break

    # Do not infer from a broad parent block. Annex pages contain many adjacent
    # rows and a broad fallback can accidentally attach one illustration to
    # unrelated later codes. Rows without an explicit local code stay unresolved.
    return []


def expand_codes(codes: list[str], signs: dict[str, dict]) -> list[str]:
    result: list[str] = []
    for code in codes:
        if code in signs:
            result.append(code)
            continue
        children = sorted(
            key for key, sign in signs.items()
            if sign.get("familyCode") == code
        )
        result.extend(children)
    return list(dict.fromkeys(result))


def webp_lossless(raw: bytes) -> tuple[bytes, tuple[int, int], str]:
    with Image.open(io.BytesIO(raw)) as source:
        source.load()
        mode = "RGBA" if "A" in source.getbands() else "RGB"
        original = source.convert(mode)
        size = original.size
        pixels = original.tobytes()
        out = io.BytesIO()
        original.save(out, "WEBP", lossless=True, method=6)
        encoded = out.getvalue()
    with Image.open(io.BytesIO(encoded)) as check:
        check.load()
        decoded = check.convert(mode)
        if decoded.size != size or decoded.tobytes() != pixels:
            raise ValueError("Lossless WebP pixel verification failed")
    return encoded, size, mode


def _bare_code_chunk(text: str) -> bool:
    cleaned = CODE_RE.sub("", text)
    cleaned = re.sub(r"[\\s,;/–—()]+", "", cleaned)
    return not cleaned


def _family_root(code: str) -> str:
    match = re.match(r"^([A-Z]{1,2})\\s+(\\d+)", code)
    return f"{match.group(1)} {match.group(2)}" if match else code


def build_mapping(page_raw: bytes, signs: dict[str, dict]) -> tuple[dict[str, list[dict]], list[dict]]:
    """Map each source image from the closest code tokens in document order.

    The reference page has rowspans that make ancestor-row text unsafe: one HTML
    row can contain the previous legal code plus many later rows.  Instead, walk
    the rendered source order and bind an image to the nearest code-bearing text
    immediately before it. Consecutive bare code chunks of the same family are
    grouped (e.g. IS 1a / IS 1b / IS 1c). Images without a new code inherit the
    previous legal row, which preserves legitimate multi-illustration rows.
    """
    doc = html.fromstring(page_raw)
    mapping: dict[str, list[dict]] = defaultdict(list)
    unresolved: list[dict] = []
    recent_code_chunks: list[tuple[str, list[str]]] = []
    last_targets: list[str] = []
    image_count = 0

    def consume_text(value: str | None) -> None:
        if not value:
            return
        text_value = " ".join(value.split())
        codes = list(dict.fromkeys(CODE_RE.findall(text_value)))
        if codes:
            recent_code_chunks.append((text_value, codes))
            del recent_code_chunks[:-8]

    def targets_from_recent() -> list[str]:
        if not recent_code_chunks:
            return []
        text_value, codes = recent_code_chunks[-1]
        selected = list(codes)
        if _bare_code_chunk(text_value) and len(codes) == 1:
            root = _family_root(codes[0])
            for previous_text, previous_codes in reversed(recent_code_chunks[:-1]):
                if not _bare_code_chunk(previous_text):
                    break
                if any(_family_root(code) != root for code in previous_codes):
                    break
                selected = previous_codes + selected
        return expand_codes(list(dict.fromkeys(selected)), signs)

    def walk(node) -> None:
        nonlocal last_targets, image_count
        consume_text(node.text)
        for child in node:
            if getattr(child, "tag", None) == "img":
                src = child.get("src") or ""
                id_match = ID_RE.search(src)
                filename = image_name(child)
                if id_match and filename:
                    image_count += 1
                    targets = targets_from_recent() or last_targets
                    if targets:
                        item = {
                            "fileId": id_match.group(1),
                            "filename": filename,
                            "referenceHref": src,
                            "rawCodes": list(targets),
                        }
                        for code in targets:
                            if item not in mapping[code]:
                                mapping[code].append(item)
                        last_targets = list(targets)
                    else:
                        unresolved.append({"href": src, "filename": filename, "codes": []})
                    recent_code_chunks.clear()
            else:
                walk(child)
            consume_text(child.tail)

    walk(doc)
    print(f"Reference mapping elements={image_count} mappedCodes={len(mapping)} unresolved={len(unresolved)}")
    return mapping, unresolved


def all_images(sign: dict) -> list[dict]:
    graphic = sign.get("graphic", {})
    if not graphic.get("path"):
        return []
    return [graphic] + graphic.get("additionalImages", [])


def normalize_shared(catalog: dict) -> None:
    users: dict[str, list[dict]] = defaultdict(list)
    for sign in catalog["signs"]:
        for item in all_images(sign):
            users[item["path"]].append(item)
    for items in users.values():
        if len(items) > 1:
            for item in items:
                item["shared"] = True


def run(*, apply: bool) -> dict:
    catalog_path = CONTENT / "catalog.json"
    audit_path = CONTENT / "audit.json"
    sources_path = CONTENT / "sources.json"
    report_path = CONTENT / "graphics-public-file-import-report.json"

    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    sources = json.loads(sources_path.read_text(encoding="utf-8"))
    signs = {sign["code"]: sign for sign in catalog["signs"]}
    missing_before = sorted(code for code, sign in signs.items() if not sign["graphic"].get("path"))

    page_raw = fetch(REFERENCE_PAGE)
    mapping, unresolved = build_mapping(page_raw, signs)

    graphics_dir = CONTENT / "graphics"
    downloaded: dict[tuple[str, str], dict] = {}
    imported: dict[str, list[str]] = {}
    no_mapping: list[str] = []

    for code in missing_before:
        candidates = [
            candidate for candidate in mapping.get(code, [])
            if candidate_allowed(code, candidate["filename"])
        ]
        if not candidates:
            no_mapping.append(code)
            continue

        # Preserve all distinct official illustrations associated with the same
        # legal row/card. Duplicate TIFF pixels collapse to one WebP.
        rendered: list[dict] = []
        seen_webp: set[str] = set()
        for candidate in candidates:
            key = (candidate["fileId"], candidate["filename"])
            if key not in downloaded:
                url = OFFICIAL_BINARY.format(file_id=key[0], filename=key[1])
                raw = fetch(url)
                try:
                    with Image.open(io.BytesIO(raw)) as im:
                        fmt = im.format
                        im.verify()
                except Exception as exc:
                    raise ValueError(f"Official binary is not an image for {code}: {url}") from exc
                if fmt != "TIFF":
                    raise ValueError(f"Expected TIFF from e-Sbírka, got {fmt}: {url}")
                encoded, size, mode = webp_lossless(raw)
                digest = sha256(encoded)
                filename = digest[:24] + ".webp"
                downloaded[key] = {
                    "encoded": encoded,
                    "graphic": {
                        "mime": "image/webp",
                        "path": "graphics/" + filename,
                        "sha256": digest,
                        "sourceId": SOURCE_ID,
                        "sourceFileId": key[0],
                        "sourceImageName": key[1],
                        "sourceTiffSha256": sha256(raw),
                        "sourceBinaryUrl": url,
                        "status": "VERIFIED",
                        "pixelSize": [size[0], size[1]],
                        "pixelMode": mode,
                        "mappingEvidence": {
                            "referencePage": REFERENCE_PAGE,
                            "referenceHref": candidate["referenceHref"],
                            "method": "public-file-id lookup; binary fetched directly from e-Sbírka",
                        },
                    },
                }
            item = downloaded[key]
            graphic = dict(item["graphic"])
            if graphic["sha256"] in seen_webp:
                continue
            seen_webp.add(graphic["sha256"])
            rendered.append(graphic)

        if not rendered:
            no_mapping.append(code)
            continue

        primary, *extra = rendered
        signs[code]["graphic"] = primary
        if extra:
            signs[code]["graphic"]["additionalImages"] = extra
        if SOURCE_ID not in signs[code]["sourceIds"]:
            signs[code]["sourceIds"].append(SOURCE_ID)
        imported[code] = [g["sourceImageName"] for g in rendered]

    normalize_shared(catalog)

    remaining = sorted(code for code, sign in signs.items() if not sign["graphic"].get("path"))
    report = {
        "referencePage": REFERENCE_PAGE,
        "missingBefore": len(missing_before),
        "importedCards": len(imported),
        "downloadedOfficialBinaries": len(downloaded),
        "imported": imported,
        "remainingCodes": remaining,
        "noMappingCodes": sorted(set(no_mapping)),
        "unresolvedReferenceAnchors": unresolved[:100],
    }

    if apply:
        graphics_dir.mkdir(parents=True, exist_ok=True)
        referenced_paths = {
            image["path"]
            for sign in catalog["signs"]
            for image in all_images(sign)
        }
        for item in downloaded.values():
            path = item["graphic"]["path"]
            if path not in referenced_paths:
                continue
            target = CONTENT / path
            encoded = item["encoded"]
            if target.exists() and target.read_bytes() != encoded:
                raise ValueError(f"Content-addressed graphic collision: {path}")
            target.write_bytes(encoded)

        sources[SOURCE_ID] = {
            "title": "e-Sbírka 294/2015 Sb. — current consolidated official binary illustrations",
            "url": "https://e-sbirka.gov.cz/sb/2015/294/2025-07-01",
            "type": "official-consolidated-annex-binary",
            "versionDate": "2025-07-01",
            "applicableFrom": "2025-07-01",
            "applicableTo": None,
            "retrievedAt": "2026-09-29",
            "checkedAt": "2026-09-29",
            "scope": (
                "Binary TIFFs fetched directly from e-Sbírka sbr-externi by public file id. "
                "The reference page is used only to recover the public file id/filename mapping."
            ),
        }
        sources_path.write_bytes(canonical_bytes(sources))
        catalog_path.write_bytes(canonical_bytes(catalog))
        audit_path.write_bytes(canonical_bytes(full_audit(catalog, sources)))
        report_path.write_bytes(canonical_bytes(report))

    print(json.dumps(report, ensure_ascii=False, indent=2))
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    run(apply=args.apply)
