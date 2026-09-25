"""CLI for a cached, explicit live crawl of official-public eTesty pages."""

import argparse
import json
from pathlib import Path

from .diff_snapshots import compare, read
from .pipeline import build, collect, make_audit, validate


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    crawl = sub.add_parser("crawl")
    crawl.add_argument("output", type=Path)
    crawl.add_argument("--resume", action="store_true", help="Reuse on-disk response cache (default)")
    crawl.add_argument("--refresh", action="store_true", help="Explicitly refetch cached pages")
    crawl.add_argument("--slice", type=int, default=None, help="Representative questions per area (proof run only)")
    crawl.add_argument("--no-media", action="store_true")
    crawl.add_argument("--sample-runs", type=int, default=1)
    crawl.add_argument("--delay", type=float, default=0.6)
    for name in ("validate", "build", "audit"):
        sub.add_parser(name).add_argument("output", type=Path)
    diff = sub.add_parser("diff")
    diff.add_argument("old", type=Path)
    diff.add_argument("new", type=Path)
    args = parser.parse_args()
    if args.command == "crawl":
        result = collect(args.output, max_questions=args.slice, no_media=args.no_media,
                         sample_runs=args.sample_runs, refresh=args.refresh, delay=args.delay)
        print(f"normalized={len(result['questions'])} bulletin={result['snapshot']['publicationDate']}")
    elif args.command == "diff":
        print(json.dumps(compare(read(args.old), read(args.new)), ensure_ascii=False, indent=2))
    else:
        snapshot = read(args.output / "normalized.json")
        if args.command == "validate":
            problems = validate(snapshot, args.output)
            print(json.dumps(problems, ensure_ascii=False, indent=2))
            if problems:
                parser.exit(1)
        elif args.command == "build":
            print(json.dumps(build(snapshot, args.output), ensure_ascii=False, indent=2))
        else:
            print(json.dumps(make_audit(snapshot, args.output), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
