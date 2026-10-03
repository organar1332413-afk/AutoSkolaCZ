# Bilingual sign detail and contextual word lookup

Branch: `stage4b-premium-ui`. Base of this follow-up: `97a112f4ebedd5c205da8da403f268d10f5c1381`.

The approved Soft 3D Premium / Material 3 Expressive Lite design, catalog grid,
separate navigation destinations, favorites, viewed state and catalog restoration remain.

## Content

`content/learning/signs/curated.json` already supplies `titleRu/titleUk` and `ru/uk`
for the sign meaning. It has no action/memory/extra helper translations. Its Czech
copy, source fields, 408 cards and all verified graphics remain unchanged.

`detail-translations.json` is an offline companion keyed by exact original Czech text:
911 unique phrases in both RU and UK, covering existing driver actions, memory advice,
common-mistake copy used as a memory nuance, simple copy and 32 additional exception
phrases. It contains a SHA-256 binding to the original curated file and is explicitly
`TRANSLATION_DRAFT`, not an official translation or new legal-content verification.
Human bilingual review remains advisable. There is no new factual enrichment.
Some original advice remains short; expanding it is a separate sourced content task.

Detail keeps exactly three teaching cards: Co znamená / Co má řidič udělat / Zapamatuj si.
Czech leads; selected RU/UA follows each text in smaller secondary type. Independent
additional text and existing exceptions are collapsed, and the official source is
collapsed. The former permanent official-question links card is removed from this UI;
underlying question/source metadata remains in the catalog adapter.
Exact-source lookup returns an explicit unavailable helper if future copy lacks a
translation; CI currently requires complete helper coverage for all 408 cards.

## Word lookup

`CzechLearningText` retains original text and uses TextLayoutResult to hit-test a single
word, with Czech letters/diacritics, Unicode NFC, case folding, punctuation separation
and wrapped lines. TalkBack custom actions offer the same per-word lookup.
`WordTranslationPolicy` must be explicit. CZ-only and strict exam deny lookup, and a
policy change dismisses an existing popup. Official exam UI/engine is untouched.

A focusable Compose Popup anchors to the selected character, clamps within the window,
and closes on outside tap, Back or close. It opens no destination and leaves list state
in place. Detail LazyColumn sections have stable keys. The one-time small tip is at the
bottom and its dismissal/first lookup is saved in the existing user_settings DataStore.

Lookup reuses `RoomStudyRepository.words`, `DictionaryWord`, translations/forms and
`SavedWord`. No second dictionary database, network translator or paid API is added.
The bundled debug seed still contains only four words: ohrozit, omezit, vozidlo, řidič
(with the existing inflected forms and RU/UK entries). Release has no debug seed; its
coverage depends on imported dictionary entries. Unknown words display a localized
unavailable state and can be saved as an unverified surface form through the existing
repository. Existing untranslated dictionary entries reuse their own ID when saved.
Saved state is reactive; repeated saves use Room IGNORE and preserve review counters.

Pronunciation reuses CzechSpeech, speaks only the selected word, and uses installed
Czech voices that do not require network access. Missing Czech voice disables the
control and shows the existing unavailable state. Voice installation is a device issue.

## Verification

Robolectric Compose tests exercise RU/UA in all three cards, CZ-only hiding, actual taps
in title/meaning/action/memory/extra, known/unknown lookup, punctuation, UI saving,
strict policy, Android outside-window dispatch and Back ordering. Existing navigation
checks retain search/category, exact deep-scroll range, viewed + favorite coexistence.
Room tests save known/unknown words, reopen a disk database and verify duplicates and
review counters. Python guards require exact phrase coverage, source hash, both
languages and unchanged numeric/sign references. Existing sign validations remain.

Native Android instrumentation tests run in the existing API 29 CI emulator alongside
Room device tests: real WindowManager outside tap/Back, unchanged list index/offset,
real A 12a bilingual cards and per-section popup. Device screenshots are archived with
`room-device-test-reports` (the artifact name is retained for compatibility).

## Real-phone acceptance (pending user verification)

1. Install the new debug APK over the existing development build, keeping app data.
2. Open Signs, choose Výstražné, scroll deep and open A 12a (search separately if needed).
3. In CZ + RU verify helper text beneath each of the three Czech blocks and the title.
4. Switch to CZ + UA, then CZ; verify the three helpers change/disappear globally.
5. In CZ + RU tap words in the title, all cards and expanded additional information.
6. Known dictionary examples include řidič/řidiče and vozidlo/vozidla; most other tokens
   currently show unavailable. Check punctuation and words on wrapped lines.
7. Close by tapping outside, then reopen and press Back: popup closes first, detail stays.
8. Save a word, reopen its popup, restart the app and verify saved state/no duplicates.
9. Test pronunciation with an installed offline Czech voice, or verify unavailable state.
10. Back from detail must return to the same catalog position/category/query/filters.
    Viewed/favorite states must remain after reopening/restart.
11. After first lookup or tip dismissal, revisit another detail/restart: tip stays hidden.
12. Strict exam/Exam learning level must offer no popup, helper text or hint.

PRs stay Draft; no merge, force push, asset replacement or importer run is part of this task.
