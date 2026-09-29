"""Backfill grouped and multi-image official sign graphics from the e-Sbírka ZIP.

This complements import_esbirka_batch.py. It handles:
- legal annex rows that name several atomic IS codes but show one shared image,
- grouped S families whose annex row uses S 1/S 2/... while the catalog stores
  atomic aspects S 1a/S 1b/...,
- rows with multiple official illustrations for one legal code.

Only exact current e-Sbírka archive rows are accepted. Every generated image is
lossless, pixel-checked against its TIFF source, content-addressed, and carries
source provenance. Ambiguous rows are left untouched and reported.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import subprocess
import sys
import zipfile
from collections import defaultdict
from pathlib import Path

from PIL import Image

from tools.learning_content.link_esbirka_graphics import (
    SOURCE_ID,
    TIFF_NAME,
    annex_codes,
    pixel_key,
)
from tools.learning_content.signs import CONTENT, canonical_bytes, full_audit

TARGET_CATEGORIES = {
    "information_zone",
    "information_traffic",
    "information_direction",
    "additional_panel",
    "road_marking",
    "light_signal",
}

# Annex-family rows that the catalog intentionally expands into atomic aspects.
ATOMIC_FAMILY_PREFIXES = {"S"}


def _lossless_encode(raw: bytes, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    convert = (
        "import io,sys; from PIL import Image; "
        "src=Image.open(io.BytesIO(sys.stdin.buffer.read())).convert('RGB'); "
        "src.save(sys.argv[1],'WEBP',lossless=True,method=6); "
        "dst=Image.open(sys.argv[1]).convert('RGB'); "
        "assert src.size==dst.size and src.tobytes()==dst.tobytes()"
    )
    result = subprocess.run(
        [sys.executable, "-c", convert, str(path)],
        input=raw,
        capture_output=True,
    )
    if result.returncode or not path.is_file() or not path.stat().st_size:
        path.unlink(missing_ok=True)
        raise ValueError(f"Lossless WebP conversion failed: {path.name}")


def _graphic(raw: bytes, archive_name: str, file_id: str, source_name: str) -> tuple[dict, Path]:
    with Image.open(io.BytesIO(raw)) as source:
        rgb = source.convert("RGB")
        pixel_digest = hashlib.sha256(rgb.tobytes()).hexdigest()
    path = CONTENT / "graphics" / f"{pixel_digest[:24]}.webp"
    if not path.exists():
        _lossless_encode(raw, path)
    # Verify in a fresh decoder process as a guard against codec/process issues.
    check = (
        "import io,sys; from PIL import Image; "
        "src=Image.open(io.BytesIO(sys.stdin.buffer.read())).convert('RGB'); "
        "dst=Image.open(sys.argv[1]).convert('RGB'); "
        "assert src.size==dst.size and src.tobytes()==dst.tobytes()"
    )
    verified = subprocess.run(
        [sys.executable, "-c", check, str(path)],
        input=raw,
        capture_output=True,
    )
    if verified.returncode:
        raise ValueError(f"Pixel verification failed: {archive_name}")
    return {
        "mime": "image/webp",
        "path": str(path.relative_to(CONTENT)),
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "sourceArchivePath": archive_name,
        "sourceFileId": file_id,
        "sourceImageName": source_name,
        "sourceTiffSha256": hashlib.sha256(raw).hexdigest(),
        "sourceId": SOURCE_ID,
        "status": "VERIFIED",
    }, path


def _targets_for_row(codes: list[str], signs: dict[str, dict]) -> list[str]:
    if codes and all(code in signs for code in codes):
        return codes
    if len(codes) == 1:
        family = codes[0]
        prefix = family.split()[0]
        if prefix in ATOMIC_FAMILY_PREFIXES:
            children = sorted(
                code for code, sign in signs.items()
                if sign.get("familyCode") == family
            )
            if children:
                return children
    return []


def _all_images(sign: dict) -> list[dict]:
    graphic = sign["graphic"]
    if not graphic.get("path"):
        return []
    return [graphic] + graphic.get("additionalImages", [])


def _normalize_shared_flags(catalog: dict) -> None:
    users: dict[str, list[dict]] = defaultdict(list)
    for sign in catalog["signs"]:
        for image in _all_images(sign):
            users[image["path"]].append(image)
    for items in users.values():
        if len(items) > 1:
            for image in items:
                image["shared"] = True


def import_grouped(archive_path: Path) -> dict:
    catalog = json.loads((CONTENT / "catalog.json").read_text(encoding="utf-8"))
    sources = json.loads((CONTENT / "sources.json").read_text(encoding="utf-8"))
    expected_hash = sources[SOURCE_ID]["sha256"]
    actual_hash = hashlib.sha256(archive_path.read_bytes()).hexdigest()
    if actual_hash != expected_hash:
        raise ValueError(f"Archive SHA-256 differs from recorded source: {actual_hash}")

    signs = {sign["code"]: sign for sign in catalog["signs"]}
    missing_before = {
        code for code, sign in signs.items()
        if sign["category"] in TARGET_CATEGORIES and not sign["graphic"].get("path")
    }

    by_targets: dict[tuple[str, ...], list[tuple[str, bytes, str, str]]] = defaultdict(list)
    unresolved_rows: list[dict] = []

    with zipfile.ZipFile(archive_path) as archive:
        metadata_name = next(name for name in archive.namelist() if name.endswith("_IZ.json"))
        metadata = json.loads(archive.read(metadata_name))
        fragments = {fragment["fragmentId"]: fragment for fragment in metadata["fragmenty"]}

        for name in archive.namelist():
            if not name.endswith(".tiff"):
                continue
            match = TIFF_NAME.search(name)
            if not match:
                continue
            raw = archive.read(name)
            try:
                row_codes, file_id, source_name = annex_codes(
                    fragments[int(match[2])], int(match[1])
                )
            except (ValueError, IndexError, KeyError):
                continue
            targets = _targets_for_row(row_codes, signs)
            if not targets:
                if any(code in missing_before for code in row_codes):
                    unresolved_rows.append({"archivePath": name, "rowCodes": row_codes})
                continue
            wanted = [
                code for code in targets
                if code in missing_before and signs[code]["category"] in TARGET_CATEGORIES
            ]
            if not wanted:
                continue
            if not file_id or not source_name:
                unresolved_rows.append({
                    "archivePath": name,
                    "rowCodes": row_codes,
                    "reason": "missing source file id or image name",
                })
                continue
            # Preserve the whole legal row mapping. If some siblings are already
            # populated, only missing cards are linked, but the shared source is explicit.
            key = tuple(wanted)
            by_targets[key].append((name, raw, file_id, source_name))

        created_paths: set[Path] = set()
        added_codes: set[str] = set()
        added_images = 0
        row_summaries: list[dict] = []

        for target_codes, rows in sorted(by_targets.items()):
            # Multiple TIFFs in the same legal row are legitimate variants.
            # Sort by archive path for deterministic primary/additional order.
            rows = sorted(rows, key=lambda item: item[0])
            graphics: list[dict] = []
            seen_pixel_keys: set[tuple] = set()
            for name, raw, file_id, source_name in rows:
                with Image.open(io.BytesIO(raw)) as source:
                    key = pixel_key(source)
                if key in seen_pixel_keys:
                    continue
                seen_pixel_keys.add(key)
                graphic, path = _graphic(raw, name, file_id, source_name)
                created_paths.add(path)
                graphics.append(graphic)
            if not graphics:
                continue
            shared = len(target_codes) > 1
            for graphic in graphics:
                if shared:
                    graphic["shared"] = True
            for code in target_codes:
                sign = signs[code]
                primary = dict(graphics[0])
                if len(graphics) > 1:
                    primary["additionalImages"] = [dict(item) for item in graphics[1:]]
                sign["graphic"] = primary
                if SOURCE_ID not in sign["sourceIds"]:
                    sign["sourceIds"].append(SOURCE_ID)
                added_codes.add(code)
            added_images += len(graphics)
            row_summaries.append({
                "codes": list(target_codes),
                "images": [item["sourceImageName"] for item in graphics],
            })

    _normalize_shared_flags(catalog)

    # Remove only files created by this run that ended up unreferenced.
    referenced = {
        (CONTENT / image["path"]).resolve()
        for sign in catalog["signs"]
        for image in _all_images(sign)
    }
    for path in created_paths:
        if path.resolve() not in referenced:
            path.unlink(missing_ok=True)

    audit = full_audit(catalog, sources)
    (CONTENT / "catalog.json").write_bytes(canonical_bytes(catalog))
    (CONTENT / "audit.json").write_bytes(canonical_bytes(audit))

    missing_after = [
        sign["code"] for sign in catalog["signs"]
        if not sign["graphic"].get("path")
    ]
    return {
        "missingBefore": len(missing_before),
        "addedCards": len(added_codes),
        "addedImages": added_images,
        "addedCodes": sorted(added_codes),
        "missingAfter": len(missing_after),
        "missingCodes": sorted(missing_after),
        "rows": row_summaries,
        "unresolvedRows": unresolved_rows,
        "graphicsPresent": audit["graphicsPresent"],
        "bundledImages": audit["bundledImages"],
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    args = parser.parse_args()
    print(json.dumps(import_grouped(args.archive), ensure_ascii=False, indent=2))
