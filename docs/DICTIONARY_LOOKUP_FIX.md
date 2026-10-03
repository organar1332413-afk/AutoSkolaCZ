# Offline dictionary runtime repair

Follow-up base: `ad1ef69514d73b341b725479384170165c5f70f3`, branch
`stage4b-premium-ui`. The requested `c71d06df735c313233164e3958e5d1d0f913d484`
was not available through the repository Git API at inspection. No branch rollback,
force push, new branch or PR merge is involved.

## Diagnosis

The original signed debug APK (0.4.2-stage4b) contains
`assets/content/dictionary-v1.json`: **4 words / 8 listed forms**, with both RU and
UA (`uk`) translations. There is no larger local dictionary elsewhere in the tree.
The 911 bilingual sign paragraph translations are not a word dictionary.

A runtime regression test using the original debug Bootstrap, the packaged asset,
and the actual RoomStudyRepository confirmed that `(ŘIDIČE,)` returns `водитель`
and `водій`, and `VOZIDLEM:` returns `транспортное средство`. Thus an all-word
failure was **not reproduced for existing entries in the supplied debug variant**.
`Tvar` and most sign vocabulary are genuinely absent from its dictionary. A screenshot
of an unknown token does not prove a broken language or normalization path.

The release defect is concrete: the asset existed only in the debug source set,
and the release Bootstrap was a no-op. No dictionary was installed in release.
Debug installation was also coupled to permission to import sample questions and
to successful sample import. This was inappropriate for offline learning content.
The user's physical device and exact installed APK variant cannot be inspected here.

## Repair and lookup path

- Move the existing file unchanged to `app/src/main/assets/content/dictionary-v1.json`.
  One copy serves both variants; no new dictionary articles are authored.
- AppContainer owns a common BundledDictionary initializer. MainViewModel calls it
  independently of debug question/lesson samples. It opens `content/dictionary-v1.json`
  with UTF-8, validates nonempty IDs/lemmas and both real translations, and inserts
  into the existing Room tables in one transaction. IGNORE inserts retain imported
  entries, word IDs, SavedWord references and repetition counters. No schema change.
- Word flow still selects RU/UA via MaterialMode -> `ru`/`uk` -> Room translations.
  Navigation observes current words/settings instead of a captured snapshot.
- Original token extraction, Unicode NFC + Czech lowercase, surrounding-punctuation
  handling, and explicit DictionaryForm/lemma matching are reused unchanged.
  Listed inflections work; there is no automatic morphological guesser.
- The popup still anchors to the tapped word, preserves the detail and scroll, uses
  existing save/TTS actions, and closes with Back/outside tap. Strict exam and CZ-only
  remain disabled. Colors, card layout, text styles and spacing are untouched.
- LOADING / READY / ERROR states distinguish a failed or unfinished dictionary load
  from a genuinely unknown word. Failure is logged; it does not block learning or
  initialize fake translations. Saving is disabled until the dictionary is ready.

## Automated verification

- Existing domain/data/app tests and popup/navigation/scroll tests.
- Four BundledDictionaryRuntimeTest tests in **both debug and release**: actual asset
  to Room to lookup, real RU/UA selection, uppercase, punctuation, NFC diacritics,
  listed forms, unknown words, repeat installation, saved counters/database reopen,
  and missing-asset ERROR instead of silently empty READY.
- Popup test distinguishes loading/system failure from missing-entry fallback.
- Native Android test uses actual AppContainer + MainViewModel + Room in a real
  C 7a Sign Detail, not injected Lexeme fixtures: tap řidič / vozidla, save, switch
  RU -> UA on the same screen, assert translated values and a genuine unknown.
- Three Python checks validate single common asset, nonempty bilingual entries and
  listed forms. Existing sign data/content validation remains.
- APK verification opens the assembled debug **and release** archives: dictionary
  exists exactly once, bytes equal the common source, entries have RU/UA and are
  nonempty, and the common initializer is compiled in. Debug sample questions and
  developer controls remain excluded from release. Signature/alignment checks remain.

CI retains full unit tests, both APK assemblies and native device tests, and now
runs the dictionary runtime tests against release as well. The signed phone APK is
0.4.3-stage4b (code 5), the same debug package/signing key as before.

## Honest limits and phone acceptance

**This repair does not provide a full word dictionary.** It restores dictionary
packaging/initialization in both variants; it does not create missing translations.
The bundled content is still 4 words: ohrozit, omezit, vozidlo, řidič; listed forms
include vozidla, vozidlem, řidiče and řidiči. `Tvar` continues to show a missing-entry
fallback because its article does not exist. Adding one special-case translation or
pretending the 911 paragraph helpers are a lexicon would hide this content gap.
Ordinary sign vocabulary requires a separate reviewed local dictionary expansion.
No paid API, cloud translator, network permission or NLP dependency was added.

Physical-phone acceptance remains necessary:

1. Install 0.4.3 over the existing development APK without uninstalling.
2. Select CZ + RU, open C 7a. In the action text tap řidič -> водитель.
3. Close, tap vozidla -> транспортное средство.
4. Save řidič, close/reopen: saved state remains; repeat save is disabled.
5. Switch globally to CZ + UA and return to C 7a: tap řidič -> водій,
   vozidla -> транспортний засіб.
6. Check Czech pronunciation with an installed offline Czech TTS voice.
7. Test unknown respektujte (or Tvar on another detail): only it shows unavailable.
8. Close popup outside and with Back; detail stays open. Back again returns to the
   original catalog position, filters/category/search and visited/favorite state.
9. Restart: saved words and sign state remain. CZ-only/strict exam offer no hints.

The existing Czech content, 408 graphics/cards, source metadata, sign import tools,
Room schema and official exam engine are unchanged.
