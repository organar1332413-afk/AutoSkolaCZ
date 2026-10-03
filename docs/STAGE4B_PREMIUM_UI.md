# Stage 4B: Soft 3D Premium signs vertical slice

Base: `stage4-learning-content` at `e5bb5fe87e1b8d075376fe5c955ba873a7882811`.
Working branch: `stage4b-premium-ui`. PR #11 is Draft against the unchanged Stage 4 branch. PR #10 stays Draft. No merge.

## Scope and architectural decisions

- `core/designsystem`: centralized palette, typography, space, radii, dimensions, motion and elevation. Reusable PremiumCard, AppTopBar, SearchField, CategoryChip, DetailSectionCard and PrimaryButton. Muted teal, navy text, pale opaque surfaces, subtle elevation; no glass/blur or decorative assets. Global theme and existing navigation colors adopt these tokens; other screen layouts and information architecture remain as they were.
- `SignsScreen`: two-column keyed LazyVerticalGrid, locally cached and sampled images through existing Coil, horizontally scrolling categories and an accessible filter dialog. Search uses the established code/CS/RU/UK lookup. All 11 category keys remain unchanged; IZ/IP/IS/IJ now have distinct Czech labels. Filters combine category, query, favorite and viewed/unviewed.
- `SignsNavigation`: real `signs` and `signs/{code}` destinations, with URI-encoded legal codes. Detail marks the sign viewed only when valid data is shown. Catalog stays on the Navigation back stack. Navigation's SaveableStateHolder and LazyGridState.Saver retain index and pixel offset; rememberSaveable retains query, category and filters. Delayed scrolling is not used. A user changing the search/filter scope starts at its beginning. An item becoming viewed disappears from an unviewed-only filter; the remaining lazy list uses stable legal-code keys.
- `SignCatalog`: existing loader retained; only adapter field `driverActionsCs` added. Catalog JSON decoding runs on Dispatchers.IO, with loading/error/retry states. No data/importer/graphics edits.
- `SettingsStore.signs`: SignProgressStore uses the existing `user_settings` DataStore instance. Two independent sets (`signs_viewed`, `signs_favorites`) store stable legal codes. Viewing never clears a favorite; favoriting never marks a sign viewed. No Room migration or second persistence file.
- `SignDetailScreen`: vivid original graphics (including every additional illustration), Czech title and quieter RU/UK title/meaning, sourced meaning/action/memory sections and expandable additional text. Duplicate memory/action text is shown once; when they match, an existing distinct sourced common-mistake tip is used in the memory section. Source/provision is collapsed and its URL opens through a labeled action. Existing verified official-question links remain. The old inline detail, vertical category list, legal-guide wall above the catalog and permanent similar-sign block are removed. Guide and provenance assets remain packaged and unchanged.
- `CzechLearningText` and LearningWordPopup reuse existing Lexeme forms, translations, saveWord and CzechSpeech. A real translation in the selected language is required. Unknown tokens keep ordinary text behavior and never receive invented translations. Full sign vocabulary coverage depends on imported dictionary forms and contextual RU/UK entries. No dictionary mass-authoring was done. CS-only / Exam learning level disables lookup and secondary translation on these destinations. Strict official exam logic is untouched.

## Verification

New tests:
- SignCatalogFilterTest: all categories, composed search/favorite/viewed filtering, 408 unique entries, 408 verified graphics available as assets and action/source fields.
- SignsNavigationTest: actual Compose Navigation detail/back, asynchronous loading and language updates inside a cached graph, category/search retention, exact grid scroll semantics before/after, and viewed+favorite coexistence. Catalog/detail/viewed+favorite PNGs are captured with Robolectric native rendering in `app/build/outputs/premium-ui` and included in the Actions reports artifact.
- SignProgressStoreTest: actual DataStore close/reopen, both states survive, idempotent views, bookmark removal preserves views and material-language preference is unchanged.
- CzechLearningTextTest: real inflected form lookup, no translation across the wrong language, unknown word or CZ-only mode.

Existing Android workflow runs domain tests, data tests, app tests, debug/release assembly, Python structure/content tests, APK source-set/signature/alignment checks, lint and emulator Room tests. Resource validation now covers all string-resource XML files in each locale, retaining duplicate/parity/format checks.

APK: debug package remains `cz.autoskola.study.debug` and retains the repository development signing key. Version is `0.4.1-stage4b` / code 3. The release build remains unsigned and is not a store-ready distribution.

No physical-phone result is claimed by automated verification. The latest Actions run for the final branch HEAD is the authoritative build result.

## Real-phone acceptance checklist

1. Install the new debug APK over the previous debug app; do not uninstall if you want existing settings/history retained.
2. Open Učení -> Dopravní značky; check loading, 408 cards and that images are vivid.
3. Select Výstražné; scroll several rows, including a partial row; open a sign.
4. Confirm a separate full screen, correct title/graphic, sourced sections and optional source disclosure.
5. Use Android system Back and the toolbar Back separately: same category/search/filters and precise position, opened card shows a check and subtle tint.
6. Open a second sign, return and favorite either one. Both viewed marks remain and the favorite coexists.
7. Force-stop/restart the app: viewed/favorite persist. Search/category/filter scope is saveable for navigation/configuration restoration; it is not a new permanent preference across a fresh session.
8. Try a code, Czech name and RU/UK translated name; combine category, viewed/unviewed and favorites; check the empty state/reset action.
9. Test CZ / CZ+RU / CZ+UA via the existing global settings. Check a known dictionary word, outside-tap popup dismissal, optional installed Czech TTS and saving to My words. Unknown words must not show fake meanings.
10. Open a sign with additional illustrations such as IS 16b / IS 20: every verified graphic remains available.
11. Check a narrow phone, large system text, landscape and TalkBack. All cards remain operable and important states have icons/text as well as color.
12. Start a strict official exam if the installed bank is ready: no translation/lookup/hint UI is introduced there.

## Limits and next stage

This is a signs UI slice, not a declaration that the broader teaching catalog is production-ready. Existing canonical-family/variant review and official-question-link coverage remain separate content work. RU/UK translation exists for titles/meaning; action/memory fields have Czech source-backed copy and do not get invented additional translations. Similar signs and a new review scheduler are intentionally absent.

After phone acceptance, apply the approved same tokens/components to Učení and question learning, then Home/Profile. Show and approve each new layout before implementation. First Aid, Rules, Crossroads and complete exam redesign are outside this change. Global root navigation and the official exam engine must not be changed incidentally.
