# First aid learning section — 2026-10-09

## Scope and publication status

16 visually approved cards C01–C16 and 35 official RP are implemented as offline learning content. This is a technical review build, not medical publication approval. Independent clinical review has **not** been available. All CS/RU/UK medical copy, translations and illustrations remain pending independent expert sign-off. No clinical certification is claimed in the UI, JSON or PR.

## Official reconciliation

Downloaded both official complete MD Bulletin PDFs through the links advertised by `https://etesty.md.gov.cz/ro/Bulletin`: 02.04.2026 and 08.10.2026. Compared **each of 35 question texts, all 104 answer alternatives, and every correct answer key** from each PDF with the current official MD HTML category 58. Only whitespace normalization was used. No changes within these 35 RP were found. This does not assert that other sections of the Bulletin are unchanged.

`content/learning/first_aid/verification.json` records source URLs and full-PDF SHA-256. The two official text extracts in this directory preserve all compared texts and keys. `tools/test_first_aid.py` independently compares them again. Original MD text and keys are in `official-questions.json`; authored source-hashed translations are in `question-helpers.json`. RP1102018 has two alternatives, so the total is 104 rather than 105. C06 and C12 have no invented official RP.

## Medical corrections and remaining review

| Card | Correction and limit |
|---|---|
| C03 | Modern responsiveness check uses calling and gently shaking shoulders. Historical ear-pinching answer remains only in the test block. Breathing assessment max 10 s; gasping is abnormal. |
| C04 | Explicit C03/C05 links and transition to CPR. Historical 3–4 attempts do not instruct the user to delay CPR. |
| C05 | Test figures 100/min, 4–5 cm, 6–8 breaths/min separated from adult clinical figures 100–120/min, 5–6 cm, trained 30:2 or continuous compressions. |
| C06 | AED anterolateral pads described; no contact during rhythm analysis/shock, immediate return to CPR. Exact pad placement in original art still needs expert inspection. |
| C07 | No unconditional recovery position after trauma. Distinguishes normal breathing, signs of injury, vomiting and airway protection. No blind finger sweep. |
| C08 | Direct pressure, protection, conditional limb tourniquet, no circumferential neck tightening. |
| C09 | Embedded object remains in place; stabilize and apply pressure around it. Needs medical illustration approval. |
| C10 | No forceful head repositioning or traction; airway/CPR and immediate safety take priority. |
| C11 | No absolute “never remove helmet”. Airway and CPR may require access/removal; avoid delaying life-saving help while waiting for a second rescuer. Art only shows visor assessment, not a removal technique. Detailed technique and CS/RU/UK wording need a trauma/EMS reviewer. |
| C13 | Exact RP1102024 B starts with stopping massive bleeding. Training illustration prioritizes pressure at leg; no universal numbered triage claim. |
| C14 | No drinks after severe injury; avoid routine movement. |
| C15 | Directly covers trapped-person compression answer RP1309001 B. Historical unfavorable-position answer is not asserted equivalent to high-quality CPR on a firm surface. Extrication not restricted to fire; dispatcher guidance, safety and life-saving access. Graphic does not teach an unverified extraction grip. EMS reviewer required. |
| C16 | “5 min.” is the simplified verified exam key, not a clinical deadline or reason to abandon help. |

Sources were checked on 2026-10-09: ERC/ČRR 2025 adult BLS step-by-step, first-aid key messages and positioning algorithm; ZZS Praha calling 155, CPR, AED, unconsciousness, bleeding and serious injury. Card-level source IDs are linked in `sources.json`. Helmet/extrication detailed technique is explicitly flagged pending: the MD key is not clinical certification. All remaining cards also require independent medical and language review.

## Android and persistence

Existing Compose/Premium design tokens, Material 3, Room and navigation remain in use. Catalog supports search, topic filters and a saved lazy-grid position. Separate detail destinations preserve the catalog back-stack entry. Next-card replaces the detail entry, so Back returns to the catalog rather than traversing the entire lesson sequence. Official-question study screens link both ways to cards; **the official exam engine, eligibility and blueprints are unchanged**.

UI controls use CS/RU/UK Android resources. Czech remains primary learning copy, with smaller RU/UK chosen solely by material mode and existing strict-exam policy. Long instructions expand within the clinical block; precise MD keys expand within the exam block. Original alternative answers are labeled as an exact Czech source record. Missing words retain the existing honest unavailable state.

Medical vocabulary is a separate section companion. Only a user-selected word is inserted into existing Room with an `aid-` ID; IGNORE conflict policy preserves existing rows and SavedWord deduplication. No original dictionary asset, importer, schema, signs content or sign destination behavior is changed. The small repository method is necessary to save the companion's actual translation rather than a nonexistent dictionary ID.

## Graphics

16 original images generated through the built-in OpenAI imagegen tool, with no third-party images provided to generation. Full approved screen mockups are references only. Sources are in `content/first_aid/graphics/source/Cxx.png`; offline optimized images are in `content/learning/first_aid/graphics/Cxx.webp`. Source files are deliberately outside the bundled assets root. Manifest records both hashes, dimensions, provenance and QA status. Generation prompts are preserved in `GENERATION_PROMPTS.json`.

OpenAI Europe Terms of Use (`https://openai.com/policies/eu-terms-of-use/`, checked 2026-10-09) assigns output ownership as between the user and OpenAI to the extent permitted by law. The tool does not warrant uniqueness or third-party clearance. No ERC/ČRR poster or stock-photo license is relied on. Provenance/contractual-use check is complete; independent legal exclusivity and clinical approval are not asserted. Commercial publication remains blocked by medical/translation QA.

## Checks

Run `python -m unittest discover -s tools -p 'test_*.py' -v`, `python tools/validate_structure.py`, then Gradle domain/data/app tests and `:app:assembleDebug`. Compose tests cover deep-scroll Back, filters/search, independent live language changes, strict-exam helper suppression, question/card links, next-card back-stack behavior, real word popup and all 16 illustration accessibility labels. Native Robolectric screenshots are exported into `app/build/outputs/first-aid/` and included in CI reports. Device acceptance on the user's Android phone remains separate from build success.

Keep the new first-aid PR Draft. Existing #11 is the **Signs** PR and stays Draft. No merge, no force-push, no branch deletion.
