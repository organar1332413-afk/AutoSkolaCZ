"""Deterministic source-page and chunked media artifacts for a validated snapshot."""

import hashlib
import json
import tarfile
import zipfile
from pathlib import Path

from .pipeline import atomic_json, sha, validate


def create_artifacts(root, chunk_bytes=256 * 1024 * 1024):
    root = Path(root)
    snapshot = json.loads((root / "normalized.json").read_text(encoding="utf-8"))
    problems = validate(snapshot, root)
    if problems:
        raise ValueError(f"Archive withheld: {len(problems)} validation problems")
    if chunk_bytes <= 0:
        raise ValueError("Chunk size must be positive")
    output = root / "artifacts"
    output.mkdir(exist_ok=True)
    pages_zip = output / "source-pages.zip"
    with zipfile.ZipFile(pages_zip, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for record in sorted(snapshot["snapshot"]["rawPages"], key=lambda r: r["sourceUrl"]):
            url = record["sourceUrl"]
            key = sha(url.encode())
            source = root / "raw-cache" / key[:2] / key
            body = source.read_bytes()
            if sha(body) != record["sha256"]:
                raise ValueError(f"Raw source changed: {url}")
            info = zipfile.ZipInfo("raw/" + key)
            info.date_time = (1980, 1, 1, 0, 0, 0)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            archive.writestr(info, body)
    media_tar = output / "media.tar"
    with tarfile.open(media_tar, "w") as archive:
        for item in snapshot["mediaInventory"]:
            file = root / item["path"]
            if sha(file.read_bytes()) != item["sha256"]:
                raise ValueError(f"Media changed: {item['path']}")
            info = tarfile.TarInfo(item["path"])
            info.size = item["size"]
            info.mtime = info.uid = info.gid = 0
            info.uname = info.gname = ""
            with file.open("rb") as stream:
                archive.addfile(info, stream)
    whole_sha = hashlib.sha256()
    parts = []
    with media_tar.open("rb") as source:
        index = 0
        while True:
            part = output / f"media.tar.part{index:03d}"
            count = 0
            part_sha = hashlib.sha256()
            with part.open("wb") as dest:
                while count < chunk_bytes:
                    data = source.read(min(4 * 1024 * 1024, chunk_bytes - count))
                    if not data:
                        break
                    dest.write(data)
                    whole_sha.update(data)
                    part_sha.update(data)
                    count += len(data)
            if count == 0:
                part.unlink()
                break
            parts.append({"file": part.name, "size": count, "sha256": part_sha.hexdigest()})
            index += 1
    media_tar.unlink()
    manifest = {"databaseVersion": snapshot["snapshot"]["databaseVersion"],
                "publicationDate": snapshot["snapshot"]["publicationDate"],
                "packageSha256": sha((root / "package-v2.json").read_bytes()),
                "normalizedSha256": sha((root / "normalized.json").read_bytes()),
                "sourcePagesSha256": sha(pages_zip.read_bytes()),
                "mediaTarSha256": whole_sha.hexdigest(), "mediaParts": parts,
                "mediaInventory": [{k: m[k] for k in ("path", "sha256", "mimeType", "size")}
                                   for m in snapshot["mediaInventory"]]}
    atomic_json(output / "artifact-manifest.json", manifest)
    return manifest
