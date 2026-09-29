"""Resolve the six manually reviewed e-Sbírka annex rows, one family at a time.

Supply an extracted official 2025-07-01 e-Sbírka ZIP as --source-root. Every
TIFF is also fetched by its official file ID and compared byte-for-byte before
writing a lossless WebP. Composite TIFFs are cropped at the inspected white
separators; the row's code order and the panel count must agree exactly.
This is deliberately limited to the 15 previously unresolved cards.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import urllib.request
from pathlib import Path

from lxml import html
from PIL import Image

from tools.learning_content.signs import CONTENT, canonical_bytes, full_audit

SOURCE_ID = "esbirka-294-2015-2025-07-01-zip"
OFFICIAL_BINARY = "https://e-sbirka.gov.cz/sbr-externi/souborove-dokumenty/{}/ORIGINAL/STAHNI/{}"
CODE = re.compile(r"\b(IS\s*\d+[a-z]?|E\s*9)\b")

# TIFF ordinals refer to the image order within the named official fragment.
# Crop coordinates are [left, top, right, bottom] in the original TIFF.
SPECS = {
    "IS 19": (1075596043, ["IS 19a", "IS 19b", "IS 19c"], [51], [0, 151, 253, 349],
              ["8fd8a76745d3f9349dce1b6c333c34020dc1741c12e55c4b8c4ae0d062c7d4f8"]),
    "IS 21": (1075596043, ["IS 21a", "IS 21b", "IS 21c"], [54], [0, 114, 226, 329],
              ["290fb53524f4ed666c05b16e78832dc237aa61d77145c913fee392393fb50c90"]),
    "IS 22": (1075596043, [f"IS 22{x}" for x in "abcdef"], [56], [0, 78, 163, 247, 331, 414, 496],
              ["6511b749b05a54654d5c2cbc4b515de5792ee314e4b5430ad3f49df816c09514"]),
    "IS 20": (1075596043, ["IS 20"], [52, 53], None, [
        "d8efa7b657072d69d0c1ea2f2a2d58eb3dc8edfd3de9ce4ad73b3ab214b3c789",
        "13be0fb853d106a9b0152066926383435c2ef38f4294ffe0c422412b6e00828c",
    ]),
    "E 9": (8661385, ["E 9"], [18], None,
            ["ead0b426051635095fd70255086499227cf60b0544b7042c20a0ca17317b366b"]),
    "IS 16b": (1075596043, ["IS 16b"], [46, 47], None, [
        "a9294b1e5687a8964faef68c83c39afabf8fb6b763957ddaf28c3d4b4102ed20",
        "b9cb494208a2b5699cae1ac5d8c5744b89d1ab5a904ee3224abc3cb759e49bd8",
    ]),
}


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def code_tokens(value: str) -> list[str]:
    return [re.sub(r"^(IS|E)\s*", lambda m: m.group(1) + " ", x) for x in CODE.findall(value)]


def encode_exact(image: Image.Image) -> tuple[bytes, list[int]]:
    source = image.convert("RGB")
    out = io.BytesIO()
    source.save(out, "WEBP", lossless=True, method=6)
    encoded = out.getvalue()
    with Image.open(io.BytesIO(encoded)) as check:
        if check.convert("RGB").tobytes() != source.tobytes() or check.size != source.size:
            raise ValueError("WebP pixels differ from official TIFF crop")
    return encoded, list(source.size)


def resolve(source_root: Path, family: str) -> dict:
    fragment_id, codes, ordinals, cuts, expected_hashes = SPECS[family]
    base = source_root / "Sb_2015_294"
    metadata = json.loads((base / "Sb_2015_294_2025-07-01_IZ.json").read_text())
    fragment = next(f for f in metadata["fragmenty"] if f["fragmentId"] == fragment_id and f.get("xhtml"))
    tree = html.fromstring(fragment["xhtml"])
    all_imgs = tree.xpath("//img")
    row = all_imgs[ordinals[0] - 1].xpath("ancestor::tr[1]")[0]
    heading = " ".join(row.xpath("./th[1]//text()"))
    if code_tokens(heading) != codes:
        raise ValueError(f"Official row code order changed: {family}: {heading}")
    row_imgs = row.xpath(".//img")
    if row_imgs != [all_imgs[n - 1] for n in ordinals]:
        raise ValueError(f"Official row illustration count/order changed: {family}")
    if cuts and (len(ordinals) != 1 or len(cuts) != len(codes) + 1):
        raise ValueError(f"Composite panel count differs from legal code count: {family}")
    if not cuts and len(codes) != 1:
        raise ValueError(f"Grouped legal codes require reviewed panel mapping: {family}")

    catalog_path = CONTENT / "catalog.json"
    data = json.loads(catalog_path.read_text())
    signs = {sign["code"]: sign for sign in data["signs"]}
    if any(signs[code]["graphic"].get("path") for code in codes):
        raise ValueError(f"Refusing to overwrite an existing graphic: {family}")

    images: list[tuple[dict, bytes]] = []
    tif_evidence = []
    for ordinal, expected_hash in zip(ordinals, expected_hashes, strict=True):
        element = all_imgs[ordinal - 1]
        file_id, name = element.get("data-souborovy-dokument-id"), element.get("alt")
        if not file_id or not name or not name.endswith(".tif"):
            raise ValueError(f"Missing official file ID or filename: {family}")
        matches = list((base / "Obrazky").glob(
            f"Sb_2015_294_{ordinal:03d}_pril_*_frag_{fragment_id}_IZ.tiff"))
        if len(matches) != 1:
            raise ValueError(f"Expected one TIFF in the official archive: {family}/{ordinal}")
        source_path = matches[0]
        raw = source_path.read_bytes()
        if sha(raw) != expected_hash:
            raise ValueError(f"Pinned official TIFF hash changed: {source_path}")
        url = OFFICIAL_BINARY.format(file_id, name)
        request = urllib.request.Request(url, headers={"User-Agent": "AutoSkolaCZ manual verifier/1.0"})
        with urllib.request.urlopen(request, timeout=90) as response:
            if response.read() != raw:
                raise ValueError(f"Official e-Sbírka binary differs from archive TIFF: {url}")
        with Image.open(io.BytesIO(raw)) as tif:
            if tif.format != "TIFF":
                raise ValueError(f"Official source is not TIFF: {url}")
            rgb = tif.convert("RGB")
        expected_cuts = cuts or [0, rgb.height]
        if expected_cuts[0] != 0 or expected_cuts[-1] != rgb.height:
            raise ValueError(f"Composite crop extent changed: {family}")
        tif_evidence.append({
            "sourceFileId": file_id, "officialFilename": name,
            "sourceArchivePath": str(source_path.relative_to(source_root)),
            "sourceTiffSha256": expected_hash, "officialBinaryUrl": url,
            "imageOrdinalInFragment": ordinal, "sourcePixelSize": list(rgb.size),
        })
        for panel, (top, bottom) in enumerate(zip(expected_cuts, expected_cuts[1:]), 1):
            box = [0, top, rgb.width, bottom]
            encoded, size = encode_exact(rgb.crop(tuple(box)))
            digest = sha(encoded)
            graphic = {
                "mime": "image/webp", "path": f"graphics/{digest[:24]}.webp",
                "sha256": digest, "sourceId": SOURCE_ID, "status": "VERIFIED",
                "sourceFileId": file_id, "sourceImageName": name,
                "sourceArchivePath": str(source_path.relative_to(source_root)),
                "sourceTiffSha256": expected_hash, "sourceBinaryUrl": url,
                "pixelSize": size,
                "provenance": {
                    "method": "official-composite-panel-order" if cuts else "exact-single-code-row",
                    "annexFragmentId": fragment_id,
                    "rowCodes": codes,
                    "rowImageCount": len(row_imgs),
                    "imageOrdinalInFragment": ordinal,
                    "panelOrdinal": panel if cuts else None,
                    "panelCount": len(codes) if cuts else None,
                    "sourceCropBox": box if cuts else None,
                },
            }
            images.append((graphic, encoded))

    if cuts and len(images) != len(codes):
        raise ValueError(f"Unexpected composite image count: {family}")
    if not cuts and len(images) != len(row_imgs):
        raise ValueError(f"Unexpected single-code image count: {family}")
    target_images = [[item] for item in images] if cuts else [images]
    for code, group in zip(codes, target_images, strict=True):
        sign = signs[code]
        graphics = []
        for graphic, encoded in group:
            graphic = dict(graphic)
            graphic["sourceProvision"] = sign["sourceProvision"]
            graphics.append(graphic)
            path = CONTENT / graphic["path"]
            if path.exists() and path.read_bytes() != encoded:
                raise ValueError(f"Content-addressed image collision: {path}")
            path.write_bytes(encoded)
        sign["graphic"] = graphics[0]
        if len(graphics) > 1:
            sign["graphic"]["additionalImages"] = graphics[1:]
        if SOURCE_ID not in sign["sourceIds"]:
            sign["sourceIds"].append(SOURCE_ID)

    evidence_path = CONTENT / "graphics-manual-resolution.json"
    evidence = json.loads(evidence_path.read_text()) if evidence_path.exists() else {
        "sourceId": SOURCE_ID, "annexVersion": "2025-07-01", "families": {},
    }
    evidence["families"][family] = {
        "legalCodesInOfficialRow": codes,
        "annexFragmentId": fragment_id,
        "officialRowSha256": sha(html.tostring(row, encoding="utf-8")),
        "officialIllustrationCount": len(row_imgs),
        "panelCount": len(codes) if cuts else None,
        "mapping": "top-to-bottom document order, one separate panel per code" if cuts else
                   "one legal code, all TIFFs from its exact official row",
        "officialTiffs": tif_evidence,
        "codeImages": {code: [graphic["path"] for graphic, _ in group]
                       for code, group in zip(codes, target_images, strict=True)},
    }
    if family == "E 9":
        evidence["families"][family]["scopeReview"] = (
            "VL 6.1 prints E 9-1 through E 9-9, while the consolidated legal "
            "annex has one E9 row and one official TIFF. Only that illustrated "
            "legal code is included; no separate legal codes or unseen graphics are invented."
        )
    if family == "IS 20":
        evidence["families"][family]["scopeReview"] = (
            "VL 6.1 prints IS 20-1 through IS 20-5, while the consolidated "
            "legal annex has one IS 20 row and two official TIFFs. Both are "
            "stored on that one legal card, without inventing three graphics."
        )
    sources = json.loads((CONTENT / "sources.json").read_text())
    catalog_path.write_bytes(canonical_bytes(data))
    evidence_path.write_bytes(canonical_bytes(evidence))
    audit = full_audit(data, sources)
    (CONTENT / "audit.json").write_bytes(canonical_bytes(audit))
    return {"family": family, "codes": codes, "graphicsPresent": audit["graphicsPresent"],
            "graphicsMissing": audit["graphicsMissing"]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--family", required=True, choices=SPECS)
    args = parser.parse_args()
    print(json.dumps(resolve(args.source_root, args.family), ensure_ascii=False))
