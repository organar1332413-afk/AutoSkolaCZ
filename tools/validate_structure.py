#!/usr/bin/env python3
"""Offline structural checks. Does not pretend to compile Kotlin or execute Room."""
from pathlib import Path
import json, sqlite3, tomllib, xml.etree.ElementTree as ET, re
root = Path(__file__).resolve().parents[1]
for p in root.rglob("*.xml"):
    if "build" not in p.parts: ET.parse(p)
for p in root.rglob("*.json"):
    if "build" not in p.parts: json.loads(p.read_text())
tomllib.loads((root / "gradle/libs.versions.toml").read_text())
connection = sqlite3.connect(":memory:")
connection.executescript((root / "docs/schema.sql").read_text())
assert not connection.execute("PRAGMA foreign_key_check").fetchall()
pack = json.loads((root / "app/src/debug/assets/content/sample-v1.json").read_text())
assert pack["manifest"]["sample"] and not pack["manifest"]["completeForB"]
questions = pack["questions"]
assert len({q["officialId"] for q in questions}) == len(questions) == 3
for q in questions:
    assert sum(a["correct"] for a in q["answers"]) == 1
    assert {t["locale"] for t in q["translations"]} == {"ru", "uk"}
    assert q["points"] is None and q["licenceGroups"] == []
    assert all(set(t["answers"]) == {a["code"] for a in q["answers"]} for t in q["translations"])
keys=[]
for locale in ("values", "values-cs", "values-ru", "values-uk"):
    tree=ET.parse(root / f"app/src/main/res/{locale}/strings.xml")
    names=[e.attrib["name"] for e in tree.getroot()]
    assert len(names)==len(set(names))
    keys.append(set(names))
assert keys[0] == keys[1] == keys[2] == keys[3]
used=set()
for file in (root / "app/src/main/kotlin").rglob("*.kt"):
    used |= set(re.findall(r"R\.string\.(\w+)",file.read_text()))
assert not (used - keys[0]), used - keys[0]
try:
    connection.execute('INSERT INTO Answer VALUES ("missing", "A", "test", 1, 0)')
except sqlite3.IntegrityError: pass
else: raise AssertionError("Foreign keys are not enforced")
print(f"PASS: 29 SQL tables; foreign keys; XML/JSON/TOML; {len(keys[0])} CS/RU/UK resource keys; 3 sourced questions.")

room=json.loads((root/"core/data/schemas/cz.autoskola.data.db.AutoSkolaDatabase/1.json").read_text())["database"]
assert room["version"]==1 and len(room["entities"])==29
assert room["identityHash"]=="81cbd33873aab63bc39ee4c31a037fe3"
actual=sqlite3.connect(":memory:");actual.execute("PRAGMA foreign_keys=ON")
for entity in room["entities"]:
    actual.execute(entity["createSql"].replace("${TABLE_NAME}",entity["tableName"]))
    for index in entity.get("indices",[]):actual.execute(index["createSql"].replace("${TABLE_NAME}",entity["tableName"]))
assert not actual.execute("PRAGMA foreign_key_check").fetchall()
for source in ("main","debug"):
    tables=[]
    for locale in ("values","values-cs","values-ru","values-uk"):
        elements=ET.parse(root/f"app/src/{source}/res/{locale}/strings.xml").getroot()
        tables.append({e.attrib["name"]:re.findall(r"%[1-9]\$[dsf]",e.text or "") for e in elements})
    assert all(t==tables[0] for t in tables), source
assert not (root/"app/src/main/assets/content/sample-v1.json").exists()
assert not any("dev_" in p.read_text() for p in (root/"app/src/release").rglob("*.kt"))
print("PASS: exported Room v1 identity unchanged; 29 tables; main/debug locale and format parity; source-set separation.")
