# Stage 4B: independent interface and learning languages

Starting branch: `stage4b-premium-ui`, HEAD `d555d91f6c318bb1bb0a7bca070227ffd3eab5e1`.
Scope: Signs catalog/detail/chrome only. Approved premium design and sign content stay intact.

## Language ownership

| Element | Language source |
|---|---|
| Catalog/detail app-bar titles | Interface Language |
| Search label, clear action, categories, count/plurals, filters | Interface Language |
| Viewed/favorite labels and accessibility descriptions | Interface Language |
| Three section headings, additional/source controls, errors | Interface Language |
| Translation tip, popup close/save/saved/status/fallback, accessibility word actions | Interface Language |
| Sign code, Czech name and Czech teaching text | Original Czech content |
| Name/paragraph helper, dictionary translation and short explanation | Learning Material Language |
| RU/UA marker in popup | Selected dictionary/helper language |
| Official source citation | Original legal metadata, verbatim |

`InterfaceLanguage` extracts the existing MainActivity configuration-context mechanism. It provides
both LocalContext and LocalConfiguration, so app resources and Material/accessibility resources
use the same selected UI locale. Popup and filter-dialog windows explicitly carry that context/configuration into their
separate Android composition, including updates while a popup is already open. The provider does not
mutate the device/default locale or material policy.
Sign destinations continue reading observable settings inside the existing navigation graph.

Hardcoded Czech chrome is replaced with CS/RU/UK resources. Existing legal category keys map to
resource IDs; four informative families have distinct localized names. Counts use Android plurals,
including Czech versus Russian/Ukrainian handling of 21/22. Search still matches codes/Czech names
and existing helper-name indexes; changing UI locale does not alter query/category/filter scope.

The dictionary asset, sign assets/texts/provenance, Room schema and strict-exam policy are unchanged.
There is no popup audio action. Existing SavedWord persistence remains in use.

## Acceptance matrix

All nine combinations are covered, including UI RU + CS/UA and UI UA + CS/RU.
CS-only displays Czech learning text without helper translation or word popup, independently of UI.

| Interface | Material | Expected chrome | Expected helpers/popup |
|---|---|---|---|
| CS | CS-only | CS | No helper/popup |
| CS | CS + RU | CS | RU |
| CS | CS + UA | CS | UA |
| RU | CS-only | RU | No helper/popup |
| RU | CS + RU | RU | RU |
| RU | CS + UA | RU | UA |
| UA | CS-only | UA | No helper/popup |
| UA | CS + RU | UA | RU |
| UA | CS + UA | UA | UA |

## Automated verification

- LocaleResourcesTest: counts 0/1/2/5/11/21/22/25/408, distinct category labels in all UI locales.
- SignsLocalizationTest: real catalog/detail content under the production locale provider, all nine
  combinations, filters/search/state semantics, all teaching sections/additional/source, popup
  translation/explanation versus chrome, source error, unknown/loading/error states and dismissed tip.
- SignsNavigationTest: locale/material changes reach cached destinations, category and exact catalog
  scroll restore after Back, visited/favorite and popup Back behavior retain their existing tests.
- Native SignTranslationDeviceTest: real ViewModel/settings/packaged dictionary for all nine pairs,
  catalog/filter-dialog checks and cross-language screenshots; existing outside-tap/Back/TTS-control tests remain.
- Existing dictionary/corpus/content/Room/exam tests and APK asset verification remain required.

## Manual device check

1. Install 0.4.5-stage4b as an update. Set Interface RU + Material CS/UA.
2. Catalog title/count/categories/search/filter/state actions should be RU; Czech names remain primary,
   with UA helper names. Search A 12a, select warning/filter, open the sign.
3. All three headings and additional/source controls should be RU; paragraphs remain Czech + UA.
4. Tap Chodci: UA translation/context, RU close/save controls, no audio. Close outside and with Back.
5. Change only Interface to UA: chrome changes to UA, material stays UA. Change only Material to CS/RU:
   chrome stays UA, helper/dictionary becomes RU. Source expansion and open-detail state remain.
6. CS-only removes helper/popup without forcing a Czech interface. Strict exam still forbids hints.
7. Back restores category/query/filters/scroll; viewed/favorite and saved words survive restart.
