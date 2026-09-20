#!/usr/bin/env python3
"""Archive a committed checkout and complete local Git history, without cleanup."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import zipfile


def git(root: Path, *args: str) -> bytes:
    return subprocess.check_output(["git", *args], cwd=root)


def backup(root: Path, output: Path) -> dict:
    root, output = root.resolve(), output.resolve()
    if root in output.parents:
        raise ValueError("Keep the backup outside the project directory")
    if output.exists():
        raise ValueError("Backup already exists; use a new filename to preserve it")
    if not (root / ".git").is_dir():
        raise ValueError("A full local checkout is required, not a linked worktree")
    if git(root, "rev-parse", "--is-shallow-repository").strip() == b"true":
        raise ValueError("Shallow history is not a complete checkpoint")
    if (root / ".git/objects/info/alternates").exists():
        raise ValueError("External Git object storage is not a self-contained checkpoint")
    if git(root, "status", "--porcelain", "--untracked-files=all"):
        raise ValueError("Commit useful changes first; the working tree must be clean")
    if list((root / ".git").rglob("*.lock")):
        raise ValueError("A Git operation may be active; finish it before archiving")
    if any(p.is_symlink() for p in (root / ".git").rglob("*")):
        raise ValueError("Symlinked Git storage is not a self-contained checkpoint")
    git(root, "fsck", "--full")
    head = git(root, "rev-parse", "HEAD").decode().strip()
    tracked = [root / p.decode() for p in git(root, "ls-files", "-z").split(b"\0") if p]
    history = [p for p in (root / ".git").rglob("*") if p.is_file()]
    files = sorted(tracked + history)
    # Never follow a link out of the repository into personal or credential files.
    if any(p.is_symlink() or root not in p.resolve().parents for p in files):
        raise ValueError("Symlinked or external files require explicit backup handling")
    for file in files:
        if not file.is_file():
            raise ValueError(f"Missing tracked file: {file.relative_to(root)}")
    output.parent.mkdir(parents=True, exist_ok=True)
    hashes = {}
    with zipfile.ZipFile(output, "x", zipfile.ZIP_DEFLATED) as archive:
        for file in files:
            name = "AutoSkolaCZ/" + file.relative_to(root).as_posix()
            data = file.read_bytes()
            info = zipfile.ZipInfo.from_file(file, name)
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
            hashes[name] = hashlib.sha256(data).hexdigest()
    with zipfile.ZipFile(output) as archive:
        if archive.testzip() is not None:
            raise ValueError("Backup CRC verification failed")
        for name, expected in hashes.items():
            if hashlib.sha256(archive.read(name)).hexdigest() != expected:
                raise ValueError(f"Backup byte verification failed: {name}")
    if git(root, "rev-parse", "HEAD").decode().strip() != head or git(root, "status", "--porcelain", "--untracked-files=all"):
        raise ValueError("Checkout changed during backup; do not use this archive as a checkpoint")
    return {"file": str(output), "size_bytes": output.stat().st_size,
            "sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
            "head": head, "branch": git(root, "branch", "--show-current").decode().strip(),
            "tags": git(root, "tag", "--list").decode().splitlines(),
            "tracked_files": len(tracked), "git_files": len(history),
            "archive_bytes_verified": True,
            "includes": "Tracked source files and complete .git; no build caches or external SSH keys"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path, help="New ZIP path outside the project")
    args = parser.parse_args()
    try:
        result = backup(Path(__file__).resolve().parents[1], args.output)
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except (ValueError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error))
