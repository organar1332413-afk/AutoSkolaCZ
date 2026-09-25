"""Reproducible offline acquisition of the current official-public Bulletin.

This adapter models the observed website, not a Ministry export contract. Its
output is intentionally separate from the Android application runtime.
"""

import hashlib
import json
import mimetypes
import re
from concurrent.futures import ThreadPoolExecutor, as_completed
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import quote, urlparse

from .fetch import Fetcher
from .parser import AREAS, GROUPS, ORIGIN, ParseError, parse_bulletin, parse_list, parse_sample_test
from tools.official_import.build_package import BLUEPRINT, SECTIONS


MIME = {".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg",
        ".webp": "image/webp", ".gif": "image/gif", ".mp4": "video/mp4", ".webm": "video/webm"}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def media_signature_matches(body, mime):
    return {"image/png": lambda: body.startswith(b"\x89PNG\r\n\x1a\n"),
            "image/jpeg": lambda: body.startswith(b"\xff\xd8\xff"),
            "image/gif": lambda: body.startswith((b"GIF87a", b"GIF89a")),
            "image/webp": lambda: body.startswith(b"RIFF") and body[8:12] == b"WEBP",
            "video/mp4": lambda: len(body) > 12 and body[4:8] == b"ftyp",
            "video/webm": lambda: body.startswith(b"\x1a\x45\xdf\xa3")}[mime]()


def encode(obj):
    return (json.dumps(obj, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8")


def atomic_json(path, obj):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_bytes(encode(obj))
    temporary.replace(path)


def collect(root, *, max_questions=None, question_id=None, category=None, no_media=False, sample_runs=1, refresh=False, delay=0.6):
    """Resume from cached responses. A bounded slice is a validation aid only."""
    root = Path(root)
    cache = Fetcher(root / "raw-cache", delay=delay, refresh=refresh)
    state_file = root / "state.json"
    state = json.loads(state_file.read_text(encoding="utf-8")) if state_file.exists() and not refresh else {}
    now = state.get("retrievedAt") or datetime.now(timezone.utc).isoformat()
    if not state:
        atomic_json(state_file, {"retrievedAt": now, "status": "DISCOVERED"})
    bulletin_url = ORIGIN + "/ro/Bulletin"
    bulletin_record, bulletin_body = cache.record(bulletin_url)
    bulletin = parse_bulletin(bulletin_body)
    questions, conflicts, raw_pages = {}, [], [bulletin_record]
    all_ids = {}
    for area in (99, *AREAS):
        page = 1
        while True:
            url = ORIGIN + f"/ro/Bulletin/List?id={area}&pageSize=1000&pagex={page}"
            record, body = cache.record(url)
            raw_pages.append(record)
            items, pagination = parse_list(body, area, url)
            for item in items:
                item["rawResponseSha256"] = record["sha256"]
                code = item["officialId"]
                if area == 99:
                    if code in all_ids:
                        conflicts.append(f"Duplicate official ID in all-list: {code}")
                    all_ids[code] = item["internalSourceId"]
                    continue
                if code in questions:
                    conflicts.append(f"Thematic/official ID conflict: {code}, {questions[code]['category']} vs {item['category']}")
                else:
                    questions[code] = item
            print(f"area={area} page={page}/{pagination['pages']} discovered={len(all_ids)} classified={len(questions)} cached={cache.cached} downloaded={cache.downloaded}", flush=True)
            if page >= pagination["pages"]:
                break
            page += 1
    for code, internal in all_ids.items():
        if code not in questions or questions[code]["internalSourceId"] != internal:
            conflicts.append(f"All-list/classified mismatch: {code}/{internal}")
    for code in questions.keys() - all_ids.keys():
        conflicts.append(f"Classified question absent from all-list: {code}")
    if conflicts:
        atomic_json(root / "quarantine.json", {"conflicts": conflicts})
        raise ValueError(f"Discovery conflicts ({len(conflicts)}); see quarantine.json")
    atomic_json(state_file, {"retrievedAt": now, "status": "PARSED", "discovered": len(all_ids), "classified": len(questions)})

    selected = dict(sorted(questions.items()))
    if question_id is not None:
        if question_id not in selected:
            raise ValueError(f"Unknown official ID in current Bulletin: {question_id}")
        selected = {question_id: selected[question_id]}
    if category is not None:
        if category not in [entry[0] for entry in AREAS.values()]:
            raise ValueError(f"Unknown thematic category: {category}")
        selected = {code: q for code, q in selected.items() if q["category"] == category}
    if max_questions is not None:
        # Preserve varied areas for a representative proof slice.
        per_area = max(1, max_questions // len(AREAS))
        selected = {q["officialId"]: q for area in AREAS for q in
                    [x for x in questions.values() if x["category"] == AREAS[area][0]][:per_area]}
        selected = dict(sorted(selected.items()))
    # The Bulletin displays some answer choices solely as images. Preserve that
    # displayed text as empty, and record the web UI's underlying answer text
    # separately (which can be a placeholder such as "." or a sign code).
    for code, item in selected.items():
        if not any(not answer["textCs"].strip() for answer in item["answers"]):
            continue
        url = ORIGIN + f"/api/v1/PublicWeb/Question/{item['internalSourceId']}"
        record, body = cache.record(url)
        raw_pages.append(record)
        payload = json.loads(body)
        source_answers = payload["questionAnswers"]
        correct = [index for index, answer in enumerate(source_answers)
                   if answer["answerId"] == payload["correctAnswerId"]]
        if (payload["questionCode"] != code or payload["id"] != item["internalSourceId"]
                or payload["pointsCount"] != item["points"]
                or correct != [i for i, a in enumerate(item["answers"]) if a["correct"]]
                or len(source_answers) != len(item["answers"])):
            conflicts.append(f"Question web payload conflicts with Bulletin: {code}/{url}")
            continue
        item["sourceRefs"].append(url)
        item["apiRawSha256"] = record["sha256"]
        for answer, source_answer in zip(item["answers"], source_answers):
            if not answer["textCs"].strip():
                answer["underlyingSourceTextCs"] = source_answer["multilingualAnswerTexts"].get("cs")
                answer["underlyingSourceTextRef"] = url
    evidence = defaultdict(dict)
    observations = []
    saturation = {group: [] for group in GROUPS}
    official_media_hashes = {}
    for group in GROUPS:
        for run in range(sample_runs):
            url = ORIGIN + "/co/DLTest/SampleTest/" + group + (f"?snapshotRun={run + 1}" if run else "")
            before = {o["officialId"] for o in observations if o["licenceGroup"] == group}
            record, body = cache.record(url)
            raw_pages.append(record)
            observed = parse_sample_test(body, group)
            for item in observed:
                code = item["questionCode"]
                observation = {"licenceGroup": group, "officialId": code, "evidenceType": "OFFICIAL_GENERATOR_OBSERVED",
                               "source": url, "rawSha256": record["sha256"], "retrievedAt": record["retrievedAt"]}
                observations.append(observation)
                if code not in questions:
                    conflicts.append(f"Generator question absent from current Bulletin: {group}/{code}")
                else:
                    bulletin_item = questions[code]
                    official_correct = [i for i, answer in enumerate(item["questionAnswers"])
                                        if answer["answerId"] == item["correctAnswerId"]]
                    bulletin_correct = [i for i, answer in enumerate(bulletin_item["answers"]) if answer["correct"]]
                    if (item["id"] != bulletin_item["internalSourceId"]
                            or item["pointsCount"] != bulletin_item["points"]
                            or official_correct != bulletin_correct):
                        conflicts.append(f"Generator/Bulletin ID, points or correct-answer conflict: {group}/{code}")
                if code in selected:
                    previous = evidence[code].get(group)
                    evidence[code][group] = {**observation, "observedCount": 1 + (previous["observedCount"] if previous else 0)}
                for media_content in [item.get("mediaContent"), *(answer.get("mediaContent") for answer in item["questionAnswers"])]:
                    if media_content and media_content.get("mediaDataHash") and media_content.get("mediaUrl"):
                        media_url = ORIGIN + media_content["mediaUrl"].replace("//", "/")
                        expected_hash = media_content["mediaDataHash"].lower()
                        previous_hash = official_media_hashes.get(media_url)
                        if previous_hash and previous_hash != expected_hash:
                            conflicts.append(f"Generator media hash conflict: {code}/{media_url}")
                        official_media_hashes[media_url] = expected_hash
            after = {o["officialId"] for o in observations if o["licenceGroup"] == group}
            saturation[group].append({"run": run + 1, "unique": len(after), "new": len(after - before)})
            print(f"sample={group} run={run + 1} observed={len(observed)}", flush=True)
    if conflicts:
        atomic_json(root / "quarantine.json", {"conflicts": conflicts})
        raise ValueError(f"Generator conflicts ({len(conflicts)}); see quarantine.json")

    media_inventory = {}
    media_missing = []
    refs_by_url = {ref["sourceUrl"]: ref for q in selected.values() for ref in q["media"]}

    def fetch_media(source_url):
        filename = Path(urlparse(source_url).path).name
        if not re.fullmatch(r"[A-Za-z0-9_-]+\.[A-Za-z0-9]+", filename):
            raise ValueError(f"Unsafe media filename: {source_url}")
        path = "media/" + filename
        mime = MIME.get(Path(filename).suffix.lower())
        if mime is None:
            raise ValueError(f"Unknown MIME for {source_url}")
        body = cache.get(source_url)
        if not media_signature_matches(body, mime):
            raise ValueError(f"Downloaded media bytes do not match MIME: {source_url}")
        destination = root / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        if not destination.is_file() or sha(destination.read_bytes()) != sha(body):
            temporary = destination.with_suffix(destination.suffix + ".tmp")
            temporary.write_bytes(body)
            temporary.replace(destination)
        return {"path": path, "sha256": sha(body), "mimeType": mime, "size": len(body), "sourceUrl": source_url}

    fetched = {}
    if not no_media:
        with ThreadPoolExecutor(max_workers=2) as pool:
            futures = {pool.submit(fetch_media, url): url for url in sorted(refs_by_url)}
            for future in as_completed(futures):
                url = futures[future]
                try:
                    fetched[url] = future.result()
                except (IOError, ValueError) as exc:
                    fetched[url] = {"error": str(exc)}
                if len(fetched) % 25 == 0 or len(fetched) == len(futures):
                    print(f"media={len(fetched)}/{len(futures)} cached={cache.cached} downloaded={cache.downloaded} failed={sum('error' in x for x in fetched.values())}", flush=True)
    for code, q in selected.items():
        normalized_media = []
        for ref in q["media"]:
            source_url = ref["sourceUrl"]
            if not no_media:
                meta = fetched[source_url]
                if "error" in meta:
                    media_missing.append({"officialId": code, "sourceUrl": source_url, "error": meta["error"]})
                    continue
                path = meta["path"]
                if source_url in official_media_hashes and meta["sha256"] != official_media_hashes[source_url]:
                    media_missing.append({"officialId": code, "sourceUrl": source_url,
                                          "error": "Downloaded bytes differ from official web mediaDataHash"})
                    continue
                prior = media_inventory.get(path)
                if prior and prior != meta:
                    media_missing.append({"officialId": code, "sourceUrl": source_url, "error": f"Conflicting media path: {path}"})
                    continue
                media_inventory[path] = meta
                normalized_media.append({"path": path, "sha256": meta["sha256"], "mimeType": meta["mimeType"],
                                         **({"answerCode": ref["answerCode"]} if ref["answerCode"] else {})})
            else:
                media_missing.append({"officialId": code, "sourceUrl": source_url, "error": "Media disabled for slice"})
        q["media"] = normalized_media
        q["eligibilityEvidence"] = [evidence[code][group] for group in GROUPS if group in evidence[code]]
    print(f"discovered={len(all_ids)} selected={len(selected)} media={len(media_inventory)} missing={len(media_missing)} cached={cache.cached} downloaded={cache.downloaded}", flush=True)

    for path, meta in media_inventory.items():
        if meta["sourceUrl"] in official_media_hashes:
            meta["officialWebSha256"] = official_media_hashes[meta["sourceUrl"]]
        meta["uses"] = sorted(({"officialId": code, "answerCode": ref.get("answerCode")}
                               for code, item in selected.items() for ref in item["media"] if ref["path"] == path),
                              key=lambda use: (use["officialId"], use["answerCode"] or ""))

    normalized = {"snapshot": {"source": bulletin_url, "publicationDate": bulletin["publicationDate"],
                               "databaseVersion": "public-etesty-" + bulletin["publicationDate"],
                               "retrievedAt": now, "adapterVersion": "etesty-public-0.1",
                               "bulletinAreas": {str(k): {"officialNameCs": v, "category": AREAS[k][0] if k in AREAS else None}
                                                 for k, v in sorted(bulletin["areas"].items())},
                               "totalDiscovered": len(all_ids), "totalDownloaded": len(selected),
                               "allOfficialIdsSha256": sha(encode(sorted(all_ids))),
                               "rawPages": raw_pages},
                  "questions": list(selected.values()), "observations": observations,
                  "saturation": saturation,
                  "mediaInventory": [media_inventory[k] for k in sorted(media_inventory)],
                  "quarantine": media_missing}
    atomic_json(root / "normalized.json", normalized)
    atomic_json(state_file, {"retrievedAt": now, "status": "MEDIA_COMPLETE" if not media_missing else "FAILED",
                             "discovered": len(all_ids), "classified": len(questions), "mediaDownloaded": len(media_inventory),
                             "failures": len(media_missing)})
    return normalized


def validate(snapshot, root):
    questions = snapshot["questions"]
    ids = [q["officialId"] for q in questions]
    problems = list(snapshot.get("quarantine", []))
    if len(set(ids)) != len(ids):
        problems.append("Duplicate official ID")
    for q in questions:
        if q["category"] not in [v[0] for v in AREAS.values()] or not q["textCs"].strip():
            problems.append(f"Invalid category or Czech text: {q['officialId']}")
        a = q["answers"]
        if len(a) not in (2, 3) or [v["code"] for v in a] != list("ABC")[:len(a)] or sum(v["correct"] for v in a) != 1:
            problems.append(f"Invalid answers: {q['officialId']}")
        if any(not v["textCs"].strip() and not any(m.get("answerCode") == v["code"] and
               m["mimeType"].startswith("image/") for m in q["media"]) for v in a):
            problems.append(f"Answer without text or image: {q['officialId']}")
        if q["category"] in SECTIONS and q["points"] != SECTIONS[q["category"]][1]:
            problems.append(f"Points/section mismatch: {q['officialId']}")
        for m in q["media"]:
            file = Path(root) / m["path"]
            if not file.is_file() or sha(file.read_bytes()) != m["sha256"]:
                problems.append(f"Media missing/hash mismatch: {q['officialId']}/{m['path']}")
    return problems


def build(snapshot, root):
    problems = validate(snapshot, root)
    if problems:
        raise ValueError(f"Snapshot has {len(problems)} unresolved items; package withheld")
    questions = []
    observed_by_group = Counter()
    for q in sorted(snapshot["questions"], key=lambda item: item["officialId"]):
        eligibility = []
        for evidence in q["eligibilityEvidence"]:
            eligibility.append({"licenceGroup": evidence["licenceGroup"], "source": evidence["source"]})
            observed_by_group[evidence["licenceGroup"]] += 1
        questions.append({"officialId": q["officialId"], "category": q["category"],
                          "textCs": q["textCs"], "points": q["points"],
                          "answers": [{key: a[key] for key in ("code", "textCs", "correct")} for a in q["answers"]],
                          "media": q["media"],
                          "eligibility": eligibility, "translations": [], "source": q["sourceRefs"][0]})
    source = snapshot["snapshot"]
    readiness = [{"licenceGroup": group, "blueprintVersion": BLUEPRINT,
                  "eligibilityComplete": False, "contentComplete": all(
                      sum(q["category"] == category and q["points"] == points
                          and any(e["licenceGroup"] == group for e in q["eligibility"])
                          for q in questions) >= count for category, (count, points) in SECTIONS.items()),
                  "mediaComplete": not bool(snapshot["quarantine"]) and all(
                      m["mimeType"] in ("image/png", "image/jpeg", "image/webp") for q in questions
                      for m in q["media"] if any(e["licenceGroup"] == group for e in q["eligibility"])),
                  "source": source["source"]} for group in GROUPS]
    package = {"manifest": {"formatVersion": 2, "databaseVersion": source["databaseVersion"],
                            "publicationDate": source["publicationDate"], "source": source["source"],
                            "retrievedAt": source["retrievedAt"], "sample": False,
                            "groupReadiness": readiness}, "questions": questions}
    data = encode(package)
    if len(data) > 20 * 1024 * 1024:
        raise ValueError("Package exceeds Android importer limit")
    output = Path(root) / "package-v2.json"
    output.write_bytes(data)
    audit = make_audit(snapshot, root)
    audit["packageSha256"] = sha(data)
    atomic_json(Path(root) / "bank-audit.json", audit)
    return audit


def make_audit(snapshot, root):
    questions = snapshot["questions"]
    media = snapshot["mediaInventory"]
    issues = validate(snapshot, root)
    observed = {g: len({o["officialId"] for o in snapshot["observations"] if o["licenceGroup"] == g}) for g in GROUPS}
    return {"publicationDate": snapshot["snapshot"]["publicationDate"],
            "retrievedAt": snapshot["snapshot"]["retrievedAt"],
            "discovered": snapshot["snapshot"]["totalDiscovered"], "downloaded": len(questions),
            "valid": len(questions) if not issues else None, "quarantined": len(snapshot["quarantine"]),
            "uniqueOfficialIds": len({q["officialId"] for q in questions}),
            "duplicateOfficialIds": len(questions) - len({q["officialId"] for q in questions}),
            "unknownThematicCategories": sum(q["category"] not in SECTIONS for q in questions),
            "correctAnswerConflicts": 0, "thematicConflicts": 0,
            "categoryCounts": dict(sorted(Counter(q["category"] for q in questions).items())),
            "answerCounts": dict(sorted(Counter(len(q["answers"]) for q in questions).items())),
            "pointsCounts": dict(sorted(Counter(q["points"] for q in questions).items())),
            "pointsProvenanceCounts": dict(sorted(Counter(q["pointsProvenance"] for q in questions).items())),
            "questionsWithMedia": sum(bool(q["media"]) for q in questions),
            "mediaFileCount": len(media),
            "images": sum(m["mimeType"].startswith("image/") for m in media),
            "videos": sum(m["mimeType"].startswith("video/") for m in media),
            "mediaBytes": sum(m["size"] for m in media), "missingMedia": snapshot["quarantine"],
            "crawlFailures": snapshot["quarantine"],
            "explicitMappings": 0, "observedMappings": sum(len(q["eligibilityEvidence"]) for q in questions),
            "unknownMappings": len(questions) * len(GROUPS) - sum(len(q["eligibilityEvidence"]) for q in questions),
            "observedUniqueByGroup": observed, "rawPages": len(snapshot["snapshot"]["rawPages"]),
            "saturation": snapshot.get("saturation", {}),
            "rawPagesSha256": sha(encode(snapshot["snapshot"]["rawPages"])),
            "unresolved": issues}
