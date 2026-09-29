"""Import an unambiguous batch of official sign illustrations from e-Sbírka.

Only a single TIFF in the expected legal annex with an exact catalog code is
accepted per card. Duplicate decoded pixels, shared/grouped rows, and signs
requiring a separate effective-version review are left for manual review.
Each lossless WebP is compared against its source TIFF in a fresh decoder
process before catalog provenance and audit data are written.
"""

import argparse
import hashlib
import io
import json
import subprocess
import sys
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

from PIL import Image

from tools.learning_content.link_esbirka_graphics import SOURCE_ID, TIFF_NAME, annex_codes, pixel_key
from tools.learning_content.signs import CONTENT, canonical_bytes, full_audit

ANNEX = {
    "prohibition": "_pril_3_",
    "mandatory": "_pril_4_",
    "information_zone": "_pril_5-bod_1_",
    "information_traffic": "_pril_5-bod_2_",
    "information_direction": "_pril_5-bod_3_",
    "information_other": "_pril_5-bod_4_",
    "additional_panel": "_pril_6_",
    "road_marking": "_pril_8_",
    "light_signal": "_pril_9_",
}
VERSION_REVIEW = {"IZ 7a", "IZ 7b"}


def import_batch(archive_path: Path, categories: list[str], limit: int, excluded: set[str]):
    catalog = json.loads((CONTENT / "catalog.json").read_text(encoding="utf-8"))
    sources = json.loads((CONTENT / "sources.json").read_text(encoding="utf-8"))
    if hashlib.sha256(archive_path.read_bytes()).hexdigest() != sources[SOURCE_ID]["sha256"]:
        raise ValueError("Archive SHA-256 differs from recorded official source")
    signs = {sign["code"]: sign for sign in catalog["signs"]}
    desired = [sign for sign in catalog["signs"] if sign["category"] in categories and not sign["graphic"].get("path")]
    with zipfile.ZipFile(archive_path) as archive:
        metadata_name = next(name for name in archive.namelist() if name.endswith("_IZ.json"))
        metadata = json.loads(archive.read(metadata_name))
        fragments = {fragment["fragmentId"]: fragment for fragment in metadata["fragmenty"]}
        candidates = defaultdict(list)
        pixel_counts = Counter()
        for name in archive.namelist():
            if not name.endswith(".tiff"):
                continue
            raw = archive.read(name)
            with Image.open(io.BytesIO(raw)) as image:
                key = pixel_key(image)
            pixel_counts[key] += 1
            match = TIFF_NAME.search(name)
            if not match or not any(fragment in name for fragment in (ANNEX[cat] for cat in categories)):
                continue
            try:
                codes, file_id, source_name = annex_codes(fragments[int(match[2])], int(match[1]))
            except (ValueError, IndexError):
                continue
            if len(codes) != 1 or codes[0] not in signs:
                continue
            sign = signs[codes[0]]
            if sign["category"] not in categories or ANNEX[sign["category"]] not in name:
                continue
            candidates[codes[0]].append((name, raw, key, file_id, source_name))

        added = []
        skipped = {}
        created = []
        pending = {}
        attempted = {}
        for sign in desired:
            code = sign["code"]
            matches = candidates[code]
            if code in excluded:
                skipped[code] = "manual review after conversion failure"
            elif code in VERSION_REVIEW:
                skipped[code] = "effective-version review required"
            elif len(matches) != 1:
                skipped[code] = f"expected one distinct annex row, found {len(matches)}"
            elif not matches[0][3] or not matches[0][4]:
                skipped[code] = "annex row lacks source file ID or image name"
            elif pixel_counts[matches[0][2]] != 1:
                skipped[code] = "decoded TIFF pixels are not unique in archive"
            elif len(added) >= limit:
                skipped[code] = "batch limit"
            else:
                name, raw, key, file_id, source_name = matches[0]
                with Image.open(io.BytesIO(raw)) as image:
                    rgb = image.convert("RGB")
                    digest = hashlib.sha256(rgb.tobytes()).hexdigest()
                    path = CONTENT / "graphics" / f"{digest[:24]}.webp"
                    if path.exists():
                        skipped[code] = "content-addressed path already exists"
                        continue
                    attempted[code] = path
                # A fresh encoder process avoids intermittent corruption seen
                # when many TIFFs are converted in one long-lived Pillow process.
                convert = (
                    "import io,sys; from PIL import Image; "
                    "src=Image.open(io.BytesIO(sys.stdin.buffer.read())).convert('RGB'); "
                    "src.save(sys.argv[1],'WEBP',lossless=True,method=6); "
                    "dst=Image.open(sys.argv[1]).convert('RGB'); "
                    "assert src.size==dst.size and src.tobytes()==dst.tobytes()"
                )
                result = subprocess.run([sys.executable, "-c", convert, str(path)], input=raw, capture_output=True)
                if result.returncode or not path.is_file() or not path.stat().st_size:
                    path.unlink(missing_ok=True)
                    skipped[code] = "isolated WebP conversion failed"
                    continue
                created.append(path)
                added.append((code, file_id, name, str(path.relative_to(CONTENT))))
                pending[code] = {
                    "mime": "image/webp", "path": str(path.relative_to(CONTENT)),
                    "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                    "sourceArchivePath": name, "sourceFileId": file_id,
                    "sourceImageName": source_name,
                    "sourceTiffSha256": hashlib.sha256(raw).hexdigest(),
                    "sourceId": SOURCE_ID, "status": "VERIFIED",
                }
        try:
            check = (
                "import io,sys,hashlib; from PIL import Image; "
                "src=Image.open(io.BytesIO(sys.stdin.buffer.read())).convert('RGB'); "
                "dst=Image.open(sys.argv[1]).convert('RGB'); "
                "assert src.size==dst.size and src.tobytes()==dst.tobytes()"
            )
            for code, _, name, rel in list(added):
                path = CONTENT / rel
                verified = subprocess.run(
                    [sys.executable, "-c", check, str(path)], input=archive.read(name), capture_output=True
                )
                if verified.returncode:
                    skipped[code] = "separate-process pixel check failed; manual review"
                    path.unlink(missing_ok=True)
                    created.remove(path)
                    added = [entry for entry in added if entry[0] != code]
                    del pending[code]
            for code, graphic in pending.items():
                signs[code]["graphic"] = graphic
                if SOURCE_ID not in signs[code]["sourceIds"]:
                    signs[code]["sourceIds"].append(SOURCE_ID)
            for code, path in attempted.items():
                if code not in pending:
                    path.unlink(missing_ok=True)
            audit = full_audit(catalog, sources)
            (CONTENT / "catalog.json").write_bytes(canonical_bytes(catalog))
            (CONTENT / "audit.json").write_bytes(canonical_bytes(audit))
        except Exception:
            for path in created:
                path.unlink(missing_ok=True)
            for path in attempted.values():
                path.unlink(missing_ok=True)
            raise
    return {"added": added, "skipped": skipped, "linkedImages": audit["bundledImages"]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--category", action="append", required=True, choices=sorted(ANNEX))
    parser.add_argument("--limit", type=int, default=40)
    parser.add_argument("--exclude-code", action="append", default=[])
    args = parser.parse_args()
    if args.limit < 1:
        parser.error("--limit must be positive")
    print(json.dumps(import_batch(args.archive, args.category, args.limit, set(args.exclude_code)), ensure_ascii=False, indent=2))
