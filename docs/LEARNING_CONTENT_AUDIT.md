# Stage 4 learning content audit (26 September 2026)

## Existing app

The 19 topic labels exist in `LearnScreen`; real production lessons do not.
`SignsScreen` and `FirstAidScreen` currently show category labels and a pending
message. The `Lesson`, `LessonBlock`, translation and `LessonQuestion` Room
tables can represent short topic lessons and links to immutable question
revisions. Debug demo lessons do not count as reviewed production content.

The lesson model has one unstructured `source` string. It cannot track a source
version, effective date, graphic license review, individual sign code or
per-question tagging evidence. The separate `content/learning/signs` catalog
keeps these fields without a risky Room migration. Its JSON is a versioned
import candidate; the Android renderer will be enabled only for reviewed
fields. Future production lesson import should use the existing Room records
plus a source registry and evidence table, not put a huge JSON blob in UI code.

## Verified inventory, not finished teaching cards

`catalog.json` contains **278 unique code families** transcribed by an
offline, hash-checked compiler from the printed index (PDF pp. 11–17) of the
Ministry-approved [VL 6.1 (2019)](https://pjpk.rsd.cz/data/USR_001_2_10_VL/VL_6.1_2019_FINAL.pdf),
with four new code families and replacement sheet provenance from
[change 1 (2025)](https://pjpk.rsd.cz/data/USR_001_2_10_VL/VL_6.1_Zmena_c._1_brezen_2025.pdf).
Some rows in the official index cover multiple graphic variants. Their printed
scope remains in `printedScopes`; **278 is not a count of all individual
graphic variants or all legal sign codes**. The March 2025 document cancels
the earlier *graphic sheets* IZ 5a, IZ 5b and IP 32 and supplies replacement
sheets; those signs were not erased from the catalog.

| Group | Indexed code families |
| --- | ---: |
| Warning | 42 |
| Priority | 8 |
| Prohibition | 40 |
| Mandatory | 33 |
| Information, zones | 22 |
| Information, traffic | 38 |
| Information, direction | 39 |
| Information, other | 27 |
| Additional panels | 29 |

The approved VL is a *graphic specification*. Its own technical report states
that sign meanings and conditions of use come from Act 361/2000 Sb., Decree
294/2015 Sb. and associated technical rules. In particular the inventory
cannot be called the complete current legal catalog until reconciled against
the current decree including 386/2023 Sb. and 205/2025 Sb. The official
[e-Sbírka decree](https://e-sbirka.gov.cz/sb/2015/294) is registered for that
review. Ministry commentary confirms substantive changes effective 1 July
2025, including low emission zones and directional signs.

The PDF is linked and hashed, but its page graphics are not bundled into the
APK: republication rights for each illustration have not been verified. Every
entry therefore carries `LICENSE_REVIEW_REQUIRED`. Explanations, RU/UK
translations and eTesty question links remain absent rather than guessed.

## Rebuild and next review gate

```bash
python3 -m tools.learning_content.signs \
  --vl2019 /path/to/VL_6.1_2019_FINAL.pdf \
  --vl2025 /path/to/VL_6.1_Zmena_c._1_brezen_2025.pdf
python3 -m tools.learning_content.signs --check
python3 -m unittest tools.learning_content.test_signs -v
```

The compiler rejects a different PDF hash. `sources.json` records each
document's version and retrieval date. To finish the catalog, reconcile the
current legal annexes code by code, review original graphics' reuse terms,
then add short original explanations and human-reviewed CS/RU/UK translations.
Question associations must be checked against the 1136-question official
snapshot by `officialId`; category membership or a keyword alone is not proof.
No real exam readiness is changed by this inventory.
