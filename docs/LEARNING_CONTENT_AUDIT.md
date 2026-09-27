# Stage 4 learning content audit (26 September 2026)

## 27 September 2026 mandatory-sign update

Annex 4 has **34 distinct legal codes**, including `C 5b`, which was hidden
inside the combined `C 5a a 5b` VL index row. The mandatory group has
**51 indexed graphic executions**. The current catalog has **350 indexed
rows from 347 VL families**, with **130/350** CS/RU/UK teaching cards and
220 rows without reviewed teaching text. The warning, priority, prohibition
and mandatory annexes are code-by-code reconciled; the remaining categories
and graphic executions are not. There is one VERIFIED sign/question
relationship, no bundled sign graphics, and the full legal sign/variant
count is still undetermined. The earlier counts below are historical.

## 27 September 2026 sign-catalog checkpoint

The next content pass also reconciled all eight Annex 2 priority codes and
all 40 Annex 3 prohibition codes, with **13** and **80** indexed graphic
executions respectively. There are now **96/349** source-provision-tagged
CS/RU/UK cards; 253 indexed rows still require card review. The hash-bound
official question ID reference list covers the Stage 3B snapshot's 1136
questions. One relationship is VERIFIED from a question that explicitly names
`B 20a` (`RP2202014`); no image similarity or keyword-only relationship
has been promoted. The other sign-to-question relationships remain unknown.
The app only opens such a link when that official question is present in
its current local question set.

The figures in the original 26 September audit below are historical. The
catalog now has **349 indexed rows**, still derived from **347 indexed
families**, after splitting the legally distinct A 31a/b/c advance boards.
All **44 Annex 1 warning codes** have individual CS/RU/UK teaching cards and
exact source-provision pointers; five numbered VL sheet pairs are tracked as
graphic executions of one legal code each, making **49 indexed warning
graphic executions**. At the warning-only checkpoint the total was
**50/349**, with 299 rows unreviewed. The full legal family/variant count for
the whole catalog is **not yet known**. Images are still absent. See
[graphics rights review](SIGN_GRAPHICS_LICENSE_REVIEW.md) and
`content/learning/signs/audit.json` for the current counts.

## Existing app

The 19 topic labels exist in `LearnScreen`; real production lessons do not.
`SignsScreen` was a placeholder; `FirstAidScreen` still is. The `Lesson`,
`LessonBlock`, translation and `LessonQuestion` Room
tables can represent short topic lessons and links to immutable question
revisions. Debug demo lessons do not count as reviewed production content.

The lesson model has one unstructured `source` string. It cannot track a source
version, effective date, graphic license review, individual sign code or
per-question tagging evidence. The separate `content/learning/signs` catalog
keeps these fields without a risky Room migration. Its JSON is a versioned
import candidate; the Android catalog renders the indexed Czech names and
only 16 separately sourced teaching cards. Future production lesson import should use the existing Room records
plus a source registry and evidence table, not put a huge JSON blob in UI code.

## Verified inventory, not finished teaching cards

`catalog.json` contains **347 unique code families**. The 278 vertical sign
families were transcribed by an
offline, hash-checked compiler from the printed index (PDF pp. 11–17) of the
Ministry-approved [VL 6.1 (2019)](https://pjpk.rsd.cz/data/USR_001_2_10_VL/VL_6.1_2019_FINAL.pdf),
with four new code families and replacement sheet provenance from
[change 1 (2025)](https://pjpk.rsd.cz/data/USR_001_2_10_VL/VL_6.1_Zmena_c._1_brezen_2025.pdf).
Some rows in the official index cover multiple graphic variants. Their printed
scope remains in `printedScopes`; **278 is not a count of all individual
graphic variants or all legal sign codes**. A separate manually reviewed
index of 40 road marking codes (decree annex 8) and 29 light signal code
families (annex 9) is kept in `legal_appendix_inventory.json`. Grouped signal
families S 1–3, 9–11 have additional individual aspects; these 29 are not a
count of all light aspects. The March 2025 document cancels
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
| Road markings | 40 |
| Light signals | 29 |

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
entry therefore carries `LICENSE_REVIEW_REQUIRED`. Six signs have short
CS/RU/UK explanations in `curated.json`, based on the Ministry's
[explanation of the 2025 amendment](https://md.gov.cz/Media/Media-a-tiskove-zpravy/TEST).
Ten warning signs have source-backed summaries from annex 1 of the consolidated
decree effective 1 July 2025. These are our teaching summaries, separate from
official wording. The other 331 indexed entries have no explanations or translations
yet. `guide.json` adds four
short blocks from §§ 2–4 of the current decree; its one verbatim legal excerpt
is kept in `officialTextCs`, separate from our summaries. eTesty question
links remain absent rather than guessed.

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
