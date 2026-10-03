# Stage 4B: finite offline Signs vocabulary

Starting branch/HEAD: `stage4b-premium-ui` / `4fd177e73d21336e38beb4762eff9b755a55ecbf`.
The prior loader/packaging fix worked, but the production dictionary had only four entries/eight forms.
It could not translate 2,267 of the 2,275 eligible forms in the Signs learning corpus.

## Exact production corpus

`SignEntry.tappableTexts()` mirrors the unchanged detail layout: catalog `titleCs`, `meaningCs`,
`driverActionsCs`, the first distinct `memoryCs`/`mistakeCs`, and distinct remaining
`simpleCs`/`memoryCs`/`mistakeCs`/`exceptionsCs` inside the additional-information expander.
Section headings, source metadata and the tip are not tappable. Sign codes, URLs, numbers,
identifier fragments and SI/single-letter technical labels are excluded without changing displayed text.
BUS/TAXI/SOS/STK/WC and ČR remain meaningful vocabulary labels, with actual explanations.

| Metric | Before | After |
|---|---:|---:|
| Sign cards scanned | 408 | 408 |
| Tappable fields scanned | 1,802 | 1,802 |
| Token occurrences | 11,313 | 11,313 |
| Unique normalized forms | 2,275 | 2,275 |
| Dictionary entries/lemmas | 4 | 1,178 |
| Indexed surface forms (including lemma keys) | 8 | 2,728 |
| Missing dictionary lookup | 2,267 | 0 |
| Missing RU translation | 2,267 | 0 |
| Missing RU explanation | 2,267 | 0 |
| Missing UA translation | 2,267 | 0 |
| Missing UA explanation | 2,267 | 0 |
| Duplicate forms | 0 | 0 |
| Duplicate lemmas | 0 | 0 |
| Conflicting form mappings | 0 | 0 |

The 2,728 keys comprise the 2,275 observed forms plus 453 base/lemma keys needed to identify
those finite lexical families. This is not a general Czech morphology engine or a runtime translator.

## Data and runtime

Authored RU/UA terminology and short road-context explanations live in
`content/dictionary/sign-lexicon.tsv`. Selectors expand against the finite real corpus
only at build/authoring time; there is no wildcard/stemming heuristic at runtime.
`base-v1.json` preserves the original four IDs/definitions and extends the observed vehicle forms.
`tools/build_sign_dictionary.py` rejects overlapping mappings and generates the single production
asset `app/src/main/assets/content/dictionary-v1.json`. Every family is explicitly authored;
translations are not inferred by word alignment. Card-level RU/UA copy informed terminology/context;
Signs educational text, official images and provenance remain unchanged.

The existing `Lexeme.meaning` / `DictionaryTranslation.meaning` field supplies the contextual
explanation; no schema migration or second dictionary/persistence system was introduced.
The existing common initializer loads both variants into Room, preserving SavedWord IDs/counters.
The loader now also rejects blank explanations, duplicate lemmas/forms, unnormalized keys and
conflicting mappings. RU uses locale `ru`; UI UA uses existing locale `uk`.
Tokens retain original character offsets/display, normalize case/punctuation/NFC, then perform exact
lemma/form lookup in the selected-language repository snapshot. Unknown words keep the existing
fallback; loading/error states remain distinct from missing vocabulary. CZ-only/strict-exam lookup
policy remains disabled.

Popup: Czech word, RU/UA, translation, short explanation, save action. Its TTS button, callback,
provider access and TTS error note were removed completely. `CzechSpeech`, `SpeechProvider` and
`SpeechButtons` elsewhere remain intact. Back/outside dismiss preserves detail/catalog state.

## Regression checks

Python validates the complete production corpus, deterministic authoring/asset parity, NFC/diacritics,
technical-token exclusions, finite observed forms, duplicate/conflicting keys, nonblank RU/UA fields
and compact explanations. Failures identify token, sign, field, original Czech context and missing field.
Robolectric checks real packaged assets -> initializer -> Room -> popup lookup for every occurrence,
RU/UA selection, Tvar/punctuation/case, inflected/negated forms, unknown fallback and persistent saves.
Native tests cover real ViewModel/sign popup, Tvar in P 1 in RU/UA, explanations/no audio with a speech
provider present, outside/Back dismissal, original scroll, and global SpeechButtons as a positive control.
`verify_apks.py` checks byte-identical full assets and zero missing coverage in debug and release APKs.

## Device acceptance

1. Install 0.4.4-stage4b (versionCode 6); keep app data when updating a matching-signed APK.
2. In CZ+RU open P 1, tap `Tvar` in Zapamatuj si: “форма, очертание” plus road-context explanation.
3. Switch CZ+UA: “форма, обрис” plus Ukrainian explanation. Popup has no pronunciation action.
4. Tap other words in title/all three blocks/expanded additional information, including forms and punctuation.
5. Save a word; aliases/language switch/restart retain saved state and do not duplicate it.
6. Outside tap and Back close popup first. Detail Back returns to the original category/search/scroll;
   viewed/favorite state remains. CZ-only/strict exam expose no lookup.

Coverage is complete for the current finite Signs corpus. Future text additions fail the regression
until explicitly authored translations/explanations are added. Dictionary wording is learning copy,
not a replacement for the unchanged official sign sources. Personal-phone acceptance remains separate
from automated emulator verification. Release output is unsigned; the CI debug APK is the installable artifact.
