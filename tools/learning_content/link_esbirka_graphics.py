"""Link already bundled WebP images to the exact TIFF pixels in an e-Sbírka ZIP.

Requires Pillow and lxml. The archive is an input, never committed. A code is
accepted only when the annex table row names it; unsupported grouped rows stop
the import for that image and require a deliberate mapping review.
"""

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


def pixel_key(image):
    rgb = image.convert("RGB")
    return rgb.size, hashlib.sha256(rgb.tobytes()).hexdigest()


def annex_codes(fragment, ordinal):
    tree = html.fromstring(fragment["xhtml"])
    images = tree.xpath("//img")
    if not 0 < ordinal <= len(images):
        raise ValueError(f"Image ordinal outside annex fragment: {ordinal}")
    element = images[ordinal - 1]
    row = element.xpath("ancestor::tr[1]")[0]
    # A rowspan can leave subsequent illustration rows without their own code.
    while not row.xpath("./th[1]") and not CODE.findall(" ".join(row.xpath("./td[1]//text()"))):
        row = row.getprevious()
        if row is None:
            raise ValueError("No annex code for illustration")
    cells = row.xpath("./th|./td")
    heading = " ".join(cells[0].itertext()) if cells else ""
    codes = CODE.findall(heading)
    if not codes:
        raise ValueError(f"No annex code in row: {heading}")
    return codes, element.get("data-souborovy-dokument-id"), element.get("alt")


def link(archive_path, *, apply=False):
    archive_hash = hashlib.sha256(archive_path.read_bytes()).hexdigest()
    with zipfile.ZipFile(archive_path) as archive:
        metadata_name = next(n for n in archive.namelist() if n.endswith("_IZ.json"))
        metadata = json.loads(archive.read(metadata_name))
        fragments = {f["fragmentId"]: f for f in metadata["fragmenty"]}
        by_pixels = defaultdict(list)
        for name in archive.namelist():
            if name.endswith(".tiff"):
                raw = archive.read(name)
                with Image.open(io.BytesIO(raw)) as source:
                    key = pixel_key(source)
                by_pixels[key].append((name, hashlib.sha256(raw).hexdigest()))

        catalog_path = CONTENT / "catalog.json"
        data = json.loads(catalog_path.read_text(encoding="utf-8"))
        signs = {s["code"]: s for s in data["signs"]}
        links = []
        for image_path in sorted((CONTENT / "graphics").glob("*.webp")):
            with Image.open(image_path) as image:
                matches = by_pixels[pixel_key(image)]
            if len(matches) != 1:
                raise ValueError(f"Expected one exact TIFF pixel match for {image_path.name}: {len(matches)}")
            original, source_hash = matches[0]
            match = TIFF_NAME.search(original)
            if not match:
                raise ValueError(f"Unknown archive path: {original}")
            codes, source_file_id, source_name = annex_codes(fragments[int(match[2])], int(match[1]))
            if any(code not in signs for code in codes):
                raise ValueError(f"Annex row needs manual review: {original}: {codes}")
            graphic = {
                "mime": "image/webp", "path": "graphics/" + image_path.name,
                "sha256": hashlib.sha256(image_path.read_bytes()).hexdigest(),
                "sourceArchivePath": original, "sourceFileId": source_file_id,
                "sourceImageName": source_name, "sourceTiffSha256": source_hash,
            }
            if len(codes) > 1:
                graphic["shared"] = True
            for code in codes:
                sign = signs[code]
                existing_paths = [sign["graphic"].get("path")] + [
                    item["path"] for item in sign["graphic"].get("additionalImages", [])
                ]
                if graphic["path"] in existing_paths:
                    continue
                if sign["graphic"].get("path"):
                    sign["graphic"].setdefault("additionalImages", []).append(graphic)
                else:
                    sign["graphic"] = {**graphic, "sourceId": SOURCE_ID, "status": "VERIFIED"}
                if SOURCE_ID not in sign["sourceIds"]:
                    sign["sourceIds"].append(SOURCE_ID)
            links.append((image_path.name, codes, original))

    sources_path = CONTENT / "sources.json"
    sources = json.loads(sources_path.read_text(encoding="utf-8"))
    sources[SOURCE_ID] = {
        "title": "e-Sbírka: 294/2015 Sb., informativní znění k 1. 7. 2025, ZIP s přílohami",
        "url": "https://e-sbirka.gov.cz/sb/2015/294/2025-07-01.zip",
        "type": "official-consolidated-annex-archive", "versionDate": "2025-07-01",
        "applicableFrom": "2025-07-01", "applicableTo": None,
        "retrievedAt": "2026-09-27", "checkedAt": "2026-09-27",
        "sha256": archive_hash,
        "scope": "Exact TIFF pixels from the official consolidated annex ZIP; individual file IDs and hashes recorded per graphic",
    }
    if apply:
        catalog_path.write_bytes(canonical_bytes(data))
        sources_path.write_bytes(canonical_bytes(sources))
        audit = full_audit(data, sources)
        (CONTENT / "audit.json").write_bytes(canonical_bytes(audit))
    return links


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    linked = link(args.archive, apply=args.apply)
    print(f"Exact pixel matches: {len(linked)}; codes: {len({code for _, codes, _ in linked for code in codes})}")
