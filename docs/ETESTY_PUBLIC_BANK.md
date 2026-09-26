# Stage 3B: official-public eTesty snapshot

This is an **official-public-source snapshot**, acquired by our offline adapter from public Ministry website pages. It is neither a Ministry structured export nor a documented API. The Android app remains offline and never calls the website. A future official export adapter can emit the same normalized model and package v2; compare snapshots by `officialId` before releasing a new `databaseVersion`.

## Observed website contract (2 April 2026 Bulletin)

- `/ro/Bulletin` names the dated current Bulletin and links area `99` (all questions) and thematic areas `52`–`58`.
- `/ro/Bulletin/List?id=<area>&pageSize=1000&pagex=<page>` renders paginated `QuestionPanel` elements. Area 99 is an independent discovery cross-check; each thematic area supplies exactly one canonical category. Do not infer licence groups from areas.
- The displayed bracketed `QuestionCode` is the stable `officialId`. The numeric `answer-container-<id>` is the internal web ID, stored only in source metadata.
- `QuestionImagePanel` contains the exact displayed Czech question text; answer rows have `answer-checkbox`, `answer-text` or `answer-image`, and `data-isCorrect`. Image-only answers legitimately have empty text. The parser decodes HTML entities and preserves source whitespace. Source page SHA-256 is retained per question.
- For image-only choices, the adapter cross-checks `/api/v1/PublicWeb/Question/<internalId>` and retains its underlying Czech answer string separately in normalized provenance. Some are placeholders (`.`) or sign codes; the package keeps the Bulletin's empty displayed text and original answer image. The web endpoint is an offline acquisition implementation detail.
- Media URLs point to `/binary_content_storage/`; question and answer media are distinct. Some answers have images, and question media includes images, GIF animations, and MP4 video. The original bytes, MIME, source URL, size and SHA-256 are preserved. GIF and MP4 are not asserted renderable in the present Android client.
- `points` are derived from the already documented official section blueprint (2/2/1/4/1/2/1); each derived value is marked `derivedFromOfficialBlueprint`. The generated sample-test JSON also contains `pointsCount`, internal ID and `correctAnswerId`, which the adapter cross-checks for observed questions.
- `/co/DLTest/SampleTest/A`, `/B`, `/BE`, `/C`, `/CE`, `/D`, `/DE` render a live `new SampleTest({...})` payload. Every generated question gives positive **observed** group evidence. The corresponding `SamplePaperTest` route renders a PDF of a generated test; it does not provide a complete group matrix. The internal `/api/v1/PublicWeb/Question/<id>` is an implementation detail, not used by Android or required by this adapter.

| Bulletin area | Canonical category |
| --- | --- |
| 52 Znalost pravidel provozu na pozemních komunikacích | `rules` |
| 53 Znalost zásad bezpečné jízdy a ovládání vozidla | `safe_driving` |
| 54 Znalost dopravních značek… | `signs` |
| 55 Schopnost řešení dopravních situací | `situations` |
| 56 Znalost předpisů o podmínkách provozu vozidel… | `vehicle` |
| 57 Znalost předpisů souvisejících s provozem… | `related` |
| 58 Znalost zdravotnické přípravy | `first_aid` |

No public complete `question ↔ licenceGroup` matrix was established by this adapter. Absence of an observation is `UNKNOWN`, never false or universal. `eligibilityComplete` is **false for all seven groups**, regardless of sampling saturation; therefore this package cannot unlock a real exam. The collected questions can serve learning through a future all-bank view even where the selected-group positive mapping is still unknown.

## Reproduce

From repository root, with Python 3.10+ and network access to the public Ministry site:

```sh
python3 -m tools.etesty_public crawl output/etesty-2026-04-02 --resume --sample-runs 20
python3 -m tools.etesty_public validate output/etesty-2026-04-02
python3 -m tools.etesty_public build output/etesty-2026-04-02
python3 -m tools.etesty_public archive output/etesty-2026-04-02
python3 -m tools.etesty_public diff OLD/normalized.json NEW/normalized.json
```

The current date is discovered automatically; the output directory name above is merely illustrative. `--slice 28` makes a seven-section proof run; omit it for a full crawl. `--refresh` explicitly refetches cached responses. The response cache uses URL SHA-256 keys and atomic writes; re-running after interruption reuses completed downloads. Rate delay and two media workers keep load modest. Failed resources remain in quarantine; build refuses missing media. Do not run live crawl in routine CI; recorded HTML fixtures cover default tests. Review `normalized.json`, `bank-audit.json`, `quarantine.json` if present, and `state.json` before accepting a snapshot.

The normalized model preserves `officialId`, internal ID, exact Czech text, answers, correctness, thematic category, derived-points provenance, raw page SHA, source URLs, original media hashes and positive group observations. `validate` checks cached source hashes, media inventory and references, and exact media bytes before `build`. `package-v2.json` is app-owned interchange, not Ministry JSON. Media paths are relative to its adjacent `media/` directory. `bank-audit.json` binds the package SHA and summarizes the crawl. Snapshot comparison distinguishes text, answer, correctness, points, category, media and eligibility-evidence changes.

`archive` verifies raw-page and media hashes, writes a deterministic `source-pages.zip`, and splits a deterministic `media.tar` into 256 MiB `media.tar.partNNN` files. `artifacts/artifact-manifest.json` records every part SHA, the concatenated tar SHA, package SHA and normalized snapshot SHA. Reassemble with `cat media.tar.part* > media.tar`, verify the recorded SHA-256, then extract beside `package-v2.json`. Never extract untrusted archives without checking paths; this archive contains only validated `media/<filename>` entries.

A future structured export must be independently checked against this snapshot by official ID, exact text, answers, correctness, points, category, media and eligibility. Replace only the source adapter, validate again, and publish a new content version. Neither current sampling nor section membership is proof of group-mapping completeness.
