# First aid exam study update

Base: e9c6002f7a9e8cfda4dbebebd785ac40df06b7ef, branch stage4c-first-aid-cards.

Native Compose detail uses the existing 16 illustrations, three separate icon hints,
Czech heading, subordinate material-language translation, progress and next-card action.
The clinical recommendation block is no longer rendered. Its source data remains intact.
C05 hints now use the preserved official exam values (approximately 100/min,
4–5 cm, 6–8 breaths/min), explicitly labelled as exam material, not current practice.
C16 displays the official test's 5-minute value with the same qualification.

Every linked question has its own compact card. Selection and word lookup have
separate touch targets. Correct answers are disclosed only after checking; selection
and checking state use rememberSaveable. Navigation retains existing back-stack
entries, filters and lazy scroll state. No official question, option, key or link changed.
RP1102018 officially has two options; adding a third would invent source content.
C06 and C12 still have no direct questions and say so explicitly.

Dictionary: 478 new entries (including 10 expressions), 10 existing entries extended
with inflections; 540 first-aid entries total. Translations remain drafts pending
independent linguistic/medical review. General dictionary IDs and saved words are
preserved. First-aid entries take precedence over general road-traffic meanings.
Expression matching now compares the complete expression instead of its first word.
Unknown words continue to show an explicit unavailable state, never an empty meaning.

Checks:
- Existing 101 Python tests and structure validation passed locally.
- FirstAidDictionaryCoverageTest checks every clickable token in all 16 cards
  (including retained clinical text), all 35 questions and all 104 original options,
  against the shipped dictionaries in both RU and UK; punctuation and diacritics too.
- FirstAidExamTest checks real text taps, translation content, save callbacks,
  independent radio-button selection, delayed results, retry and all 35 keys.
- FirstAidNavigationTest retains filter/back/scroll tests and renders all 16 details
  with three native hints and progress; existing popup and Room persistence tests remain.
- Android build/tests/lint run in GitHub Actions because local Gradle downloads are
  blocked by network access. CI outcome must be checked before delivery.

Publication remains blocked: independent medical review has not been completed.
No merge and no force push.
