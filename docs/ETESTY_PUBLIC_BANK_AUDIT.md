# Public eTesty bank acquisition audit

**Source:** [Ministry eTesty Bulletin](https://etesty.md.gov.cz/ro/Bulletin), current publication dated **2026-04-02**. This is our official-public-source acquisition, **not** a Ministry structured export or a documented API. The crawl began `2026-09-26T07:29:23.747694+00:00`. The internal content version is `public-etesty-2026-04-02`.

## Acquisition result

| Check | Result |
| --- | ---: |
| Discovered / downloaded / validated unique official IDs | 1136 / 1136 / 1136 |
| Quarantined / unresolved / duplicate IDs | 0 / 0 / 0 |
| Missing Czech text, invalid answers, unknown theme, missing media | 0 |
| Answers: 2 / 3 choices | 48 / 1088 |
| Points: 1 / 2 / 4 | 302 / 680 / 154 |
| Questions with media | 529 |
| Unique original files: images / videos | 377 / 202 |
| Images: PNG / JPEG / GIF | 222 / 105 / 50 |
| Video: MP4 | 202 |
| Original media bytes | 1,437,492,235 |
| Raw pages / image-only web-payload checks / official web media-hash checks | 175 / 25 / 499 |

| Canonical theme | Questions |
| --- | ---: |
| `rules` | 444 |
| `safe_driving` | 157 |
| `signs` | 222 |
| `situations` | 154 |
| `vehicle` | 45 |
| `related` | 79 |
| `first_aid` | 35 |

All 1136 point values are marked `derivedFromOfficialBlueprint` because the Bulletin question panels do not provide a per-question point field. The adapter checked the official generator's `pointsCount`, internal ID, Czech text, displayed non-image answer text and correct answer for its 3500 question occurrences. The web payload cross-check covers 25 image-only questions. Media files retain their source URL, MIME, size and SHA-256; 499 unique files also matched the hash supplied in observed official web payloads. The remaining 80 retain acquisition hashes and source references without claiming an independent web-payload hash.

## Licence-group evidence and readiness

Twenty generated tests were observed per group, 25 questions per test. The following are **unique positive observations**, not a complete question-to-group matrix:

| Group | Observed unique questions | New in run 20 |
| --- | ---: | ---: |
| A | 382 | 12 |
| B | 396 | 12 |
| BE | 378 | 10 |
| C | 383 | 12 |
| CE | 392 | 16 |
| D | 391 | 16 |
| DE | 387 | 19 |

There are 3500 recorded occurrences, 2709 distinct observed question–group pairs, **0 direct explicit complete-mapping claims**, and **5243 UNKNOWN pairs** out of 7952 possible pairs. In addition, 127 questions have no positive group observation yet. Absence from twenty tests is never negative eligibility evidence. The new-question counts in run 20 demonstrate that sampling is not saturated, and saturation would not prove completeness anyway.

For all seven groups, package v2 records `eligibilityComplete=false`, `contentComplete=true` for the observed positive quota coverage, and `mediaComplete=false` because current Android LocalMedia cannot render mandatory GIF/MP4. **Real exam READY is not unlocked.** The complete Czech bank is present in the package for learning and a future all-bank browsing capability; the current selected-group view still uses positive mappings only.

## Reproducibility and artifacts

The [machine-readable audit](../content/public-etesty-2026-04-02/bank-audit.json) and [compact artifact summary](../content/public-etesty-2026-04-02/artifact-summary.json) are committed. The full generated `normalized.json`, `package-v2.json`, `source-pages.zip`, full `artifact-manifest.json` with per-media hashes, and six `media.tar.partNNN` chunks are held as versioned deliverables outside Git. Follow [the acquisition instructions](ETESTY_PUBLIC_BANK.md) to reproduce them from the live public source. Their local output names are under `output/etesty-2026-04-02/`; the supplied full manifest is authoritative for hashes.

- Package v2 SHA-256: `8cc7babce6787b14b4f753c295e5ffcf98b473a7eb2c53e740151215aacacbad` (identical across two builds).
- Reassembled media tar SHA-256: `656b357f462ae48258d1d34ffcb4e68f8fcfbcf88a13dcedcc8146d11bbec6eb`.
- Artifact manifest SHA-256: `5d5bffd39642e80f069f62bd9b6a7321e6ab62e72ff240baf56580261809dfa6` (identical across two archive builds).
- All six part hashes, 579 tar entries and their contents, and 175 zipped raw responses were checked against the manifest and snapshot.

The adapter records original media without replacing video with still images. To use a later official structured export/open data, implement another source adapter into the existing normalized model, diff by official ID and publish a new content version after reviewing text, answers, correctness, themes, media and group eligibility. A trusted complete group mapping and supported playback of mandatory media are still needed before real exam readiness can be asserted.
