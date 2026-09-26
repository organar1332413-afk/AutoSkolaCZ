"""Compare normalized source snapshots by stable official ID."""

import json
from pathlib import Path


FIELDS = {"textCs": "TEXT CHANGED", "answers": "ANSWER CHANGED", "points": "POINTS CHANGED",
          "category": "CATEGORY CHANGED", "media": "MEDIA CHANGED",
          "eligibilityEvidence": "ELIGIBILITY EVIDENCE CHANGED"}


def compare(old, new):
    before = {q["officialId"]: q for q in old["questions"]}
    after = {q["officialId"]: q for q in new["questions"]}
    changes = []
    for code in sorted(before.keys() | after.keys()):
        if code not in before:
            changes.append({"officialId": code, "change": "ADDED QUESTION"})
        elif code not in after:
            changes.append({"officialId": code, "change": "REMOVED QUESTION"})
        else:
            for field, label in FIELDS.items():
                if field == "answers":
                    left = [{k: v for k, v in a.items() if k != "correct"} for a in before[code][field]]
                    right = [{k: v for k, v in a.items() if k != "correct"} for a in after[code][field]]
                else:
                    left, right = before[code][field], after[code][field]
                if left != right:
                    changes.append({"officialId": code, "change": label})
            if [a["code"] for a in before[code]["answers"] if a["correct"]] != [a["code"] for a in after[code]["answers"] if a["correct"]]:
                changes.append({"officialId": code, "change": "CORRECT ANSWER CHANGED"})
    return changes


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))
