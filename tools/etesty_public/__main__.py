"""CLI for a cached, explicit live crawl of official-public eTesty pages."""

import argparse
import json
from pathlib import Path

from .diff_snapshots import compare, read
from .pipeline import atomic_json, build, collect, make_audit, validate
from .artifacts import create_artifacts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    crawl = sub.add_parser("crawl")
    crawl.add_argument("output", type=Path)
    crawl.add_argument("--resume", action="store_true", help="Reuse on-disk response cache (default)")
    crawl.add_argument("--refresh", action="store_true", help="Explicitly refetch cached pages")
    crawl.add_argument("--slice", type=int, default=None, help="Representative questions per area (proof run only)")
    crawl.add_argument("--question-id", help="Acquire one official ID for diagnosis")
    crawl.add_argument("--category", help="Acquire one thematic section for diagnosis")
    crawl.add_argument("--no-media", action="store_true")
    crawl.add_argument("--sample-runs", type=int, default=1)
    crawl.add_argument("--delay", type=float, default=0.6)
    for name in ("validate", "build", "audit", "archive"):
        sub.add_parser(name).add_argument("output", type=Path)
    diff = sub.add_parser("diff")
    diff.add_argument("old", type=Path)
    diff.add_argument("new", type=Path)
    args = parser.parse_args()
    if args.command == "crawl":
        try:
            result = collect(args.output, max_questions=args.slice, question_id=args.question_id,
                             category=args.category, no_media=args.no_media,
                             sample_runs=args.sample_runs, refresh=args.refresh, delay=args.delay)
        except Exception as exc:
            state_file = args.output / "state.json"
            state = json.loads(state_file.read_text()) if state_file.exists() else {}
            state.update({"status": "FAILED", "lastError": str(exc)})
            atomic_json(state_file, state)
            raise
        print(f"normalized={len(result['questions'])} bulletin={result['snapshot']['publicationDate']}")
    elif args.command == "diff":
        print(json.dumps(compare(read(args.old), read(args.new)), ensure_ascii=False, indent=2))
    elif args.command == "archive":
        manifest = create_artifacts(args.output)
        print(f"mediaParts={len(manifest['mediaParts'])} mediaTarSha256={manifest['mediaTarSha256']}")
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
