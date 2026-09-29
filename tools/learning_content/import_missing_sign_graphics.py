"""Batch-import still-missing sign graphics from the official e-Sbírka annex ZIP.

Only an annex table row that resolves to exactly one legal sign code is accepted
automatically. Multiple illustrations in that exact row are preserved as
additionalImages. Ambiguous grouped rows are reported and left untouched.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import zipfile
from collections import defaultdict
from pathlib import Path

from lxml import html
from PIL import Image

from tools.learning_content.signs import CONTENT, canonical_bytes, full_audit

SOURCE_ID = "esbirka-294-2015-2025-07-01-zip"
TIFF_NAME = re.compile(r"_(\d+)_pril_.*_frag_(\d+)_IZ\.tiff$")
CODE = re.compile(r"\b(?:IJ|IP|IS|IZ|A|B|C|E|P|S|V)\s+\d+[a-z]?\b")


def sha256_bytes(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def annex_codes(fragment: dict, ordinal: int):
    tree = html.fromstring(fragment["xhtml"])
    images = tree.xpath("//img")
    if not 0 < ordinal <= len(images):
        raise ValueError(f"Image ordinal outside annex fragment: {ordinal}")
    element = images[ordinal - 1]
    row = element.xpath("ancestor::tr[1]")[0]
    while True:
        cells = row.xpath("./th|./td")
        heading = " ".join(cells[0].itertext()) if cells else ""
        codes = CODE.findall(heading)
        if codes:
            return codes, element.get("data-souborovy-dokument-id"), element.get("alt")
        row = row.getprevious()
        if row is None:
            raise ValueError("No annex code for illustration")


def webp_bytes(raw_tiff: bytes) -> bytes:
    with Image.open(io.BytesIO(raw_tiff)) as source:
        image = source.convert("RGBA") if "A" in source.getbands() else source.convert("RGB")
        out = io.BytesIO()
        image.save(out, format="WEBP", lossless=True, method=6)
        encoded = out.getvalue()
    # Verify that the lossless derivative preserves decoded pixels.
    with Image.open(io.BytesIO(raw_tiff)) as a, Image.open(io.BytesIO(encoded)) as b:
        aa, bb = a.convert("RGBA"), b.convert("RGBA")
        if aa.size != bb.size or aa.tobytes() != bb.tobytes():
            raise ValueError("Lossless WebP pixel verification failed")
    return encoded


def main(archive_path: Path, *, apply: bool) -> dict:
    catalog_path = CONTENT / "catalog.json"
    sources_path = CONTENT / "sources.json"
    audit_path = CONTENT / "audit.json"
    report_path = CONTENT / "graphics-import-report.json"

    data = json.loads(catalog_path.read_text(encoding="utf-8"))
    sources = json.loads(sources_path.read_text(encoding="utf-8"))
    signs = {s["code"]: s for s in data["signs"]}
    missing = {code for code, sign in signs.items() if not sign.get("graphic", {}).get("path")}

    archive_hash = sha256_bytes(archive_path.read_bytes())
    expected = sources.get(SOURCE_ID, {}).get("sha256")

    candidates: dict[str, list[dict]] = defaultdict(list)
    ambiguous = []
    unmapped = []

    with zipfile.ZipFile(archive_path) as archive:
        metadata_name = next(n for n in archive.namelist() if n.endswith("_IZ.json"))
        metadata = json.loads(archive.read(metadata_name))
        fragments = {f["fragmentId"]: f for f in metadata["fragmenty"]}

        if expected and archive_hash != expected:
            checked = 0
            names = set(archive.namelist())
            for sign in signs.values():
                graphic = sign.get("graphic", {})
                images = ([graphic] if graphic.get("path") else []) + graphic.get("additionalImages", [])
                for item in images:
                    source_path = item.get("sourceArchivePath")
                    source_hash = item.get("sourceTiffSha256")
                    if not source_path or not source_hash:
                        continue
                    if source_path not in names:
                        raise ValueError(f"Regenerated archive lost verified TIFF: {source_path}")
                    if sha256_bytes(archive.read(source_path)) != source_hash:
                        raise ValueError(f"Regenerated archive changed verified TIFF: {source_path}")
                    checked += 1
            if checked < 300:
                raise ValueError(f"Archive hash drift not sufficiently anchored: only {checked} TIFF checks")
            print(f"ZIP container hash changed but {checked} recorded TIFF hashes are identical; accepting regenerated container.")

        for name in sorted(n for n in archive.namelist() if n.endswith(".tiff")):
            match = TIFF_NAME.search(name)
            if not match:
                continue
            raw = archive.read(name)
            try:
                codes, source_file_id, source_name = annex_codes(
                    fragments[int(match[2])], int(match[1])
                )
            except Exception as exc:
                unmapped.append({"archivePath": name, "reason": str(exc)})
                continue
            relevant = [code for code in codes if code in missing]
            if not relevant:
                continue
            if len(codes) != 1:
                ambiguous.append({"archivePath": name, "codes": codes, "relevant": relevant})
                continue
            code = codes[0]
            if code not in signs:
                unmapped.append({"archivePath": name, "reason": f"Unknown catalog code {code}"})
                continue
            candidates[code].append({
                "archivePath": name,
                "sourceFileId": source_file_id,
                "sourceImageName": source_name,
                "sourceTiffSha256": sha256_bytes(raw),
                "raw": raw,
            })

    graphics_dir = CONTENT / "graphics"
    imported = {}
    for code in sorted(candidates):
        generated = []
        for item in candidates[code]:
            encoded = webp_bytes(item["raw"])
            digest = sha256_bytes(encoded)
            filename = digest[:24] + ".webp"
            path = graphics_dir / filename
            if apply:
                graphics_dir.mkdir(parents=True, exist_ok=True)
                if path.exists() and path.read_bytes() != encoded:
                    raise ValueError(f"Hash-named graphic collision: {filename}")
                path.write_bytes(encoded)
            generated.append({
                "mime": "image/webp",
                "path": "graphics/" + filename,
                "sha256": digest,
                "sourceArchivePath": item["archivePath"],
                "sourceFileId": item["sourceFileId"],
                "sourceImageName": item["sourceImageName"],
                "sourceTiffSha256": item["sourceTiffSha256"],
            })

        # De-duplicate identical official illustrations while preserving order.
        unique = []
        seen = set()
        for graphic in generated:
            if graphic["sha256"] not in seen:
                seen.add(graphic["sha256"])
                unique.append(graphic)
        if not unique:
            continue

        primary, *extra = unique
        signs[code]["graphic"] = {
            **primary, "sourceId": SOURCE_ID, "status": "VERIFIED"
        }
        if extra:
            signs[code]["graphic"]["additionalImages"] = extra
        if SOURCE_ID not in signs[code]["sourceIds"]:
            signs[code]["sourceIds"].append(SOURCE_ID)
        imported[code] = len(unique)

    sources[SOURCE_ID] = {
        "title": "e-Sbírka: 294/2015 Sb., informativní znění k 1. 7. 2025, ZIP s přílohami",
        "url": "https://e-sbirka.gov.cz/sb/2015/294/2025-07-01.zip",
        "type": "official-consolidated-annex-archive",
        "versionDate": "2025-07-01",
        "applicableFrom": "2025-07-01",
        "applicableTo": None,
        "retrievedAt": "2026-09-29",
        "checkedAt": "2026-09-29",
        "sha256": archive_hash,
        "scope": "Exact TIFF pixels from the official consolidated annex ZIP; individual file IDs and hashes recorded per graphic",
    }

    report = {
        "archiveSha256": archive_hash,
        "missingBefore": len(missing),
        "importedCards": len(imported),
        "importedImages": sum(imported.values()),
        "imported": imported,
        "ambiguousRows": ambiguous,
        "unmappedRows": unmapped,
        "remainingCodes": sorted(missing - set(imported)),
    }

    if apply:
        catalog_path.write_bytes(canonical_bytes(data))
        sources_path.write_bytes(canonical_bytes(sources))
        audit_path.write_bytes(canonical_bytes(full_audit(data, sources)))
        report_path.write_bytes(canonical_bytes(report))

    print(json.dumps(report, ensure_ascii=False, indent=2))
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    main(args.archive, apply=args.apply)
