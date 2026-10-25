# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

The primary user is the developer: one person in North Macedonia who tracks calories and macros every day and wants that to take less effort than their current tracker. They log mostly in English. Some custom food names are Macedonian, in Latin or Cyrillic script. They weigh meat raw. They log the same familiar foods repeatedly, often one-handed on a phone and mid-meal.

It is built for this user first. A public open-source or Play Store release may follow once the app is solid, so decisions should not shut out other trackers. They should not be tuned for a hypothetical audience either.

## Product Purpose

Breakfast is an Android calorie and macro tracker built around fast natural-language logging. The user types shorthand such as `2 tbsp oats, half a tbsp peanut butter, banana` into a meal. The app parses quantities and units, resolves each food against local and personal records, and calculates nutrition from real food records.

Success means familiar meals are logged without touching a suggestion, a new combination needs at most one correction, and both are faster than the user's current tracker, including the time spent on corrections. An evaluation protocol is proposed (five representative breakfasts, timed and counted against the current tracker, then repeated after preferences are taught) but has not been run.

## Positioning

Logging is shorthand-first and resolution is personal. The user's own aliases, custom foods and deliberate past selections outrank global matches, so `1 tsp pb` resolves to their usual local brand. Automatic guesses never train preferences; only an explicit selection or Confirm does. Parsing, matching and nutrition calculation are separate deterministic steps, with no LLM and no estimated nutrition presented as fact. All data stays on the device.

The diary, search and food-detail patterns follow MyFitnessPal so the app feels familiar. Breakfast does not copy MyFitnessPal's branding, visuals, account model or wider feature set.

## Operating Context

- The diary (the **Today** tab, called Daily in the design files) opens first. It is a dated diary that swipes horizontally between days, with a Today control, daily totals, and four fixed meals: Breakfast, Lunch, Dinner, Snacks. Logging targets the selected date.
- A fixed quick-add field sits above the bottom navigation and the keyboard, and stays visible without scrolling. Bottom navigation has exactly two destinations: **Today** and **Library**.
- **Logged entries** have a confirmed gesture contract: tap opens entry detail, double-tap opens amount-only quick edit, long press opens full entry edit, and a right-to-left swipe inside meal detail deletes with Undo. Every gesture has an explicit accessibility action.
- **Library** holds reusable food records: bundled, cached from Open Food Facts (OFF), and personal foods, all in one list. Recently logged foods come first, with a quick repeat into any meal. A meal-focused variant opens from a meal's Add food. Scan sits on the composer (the diary and meal detail) and in the Library.
- A logged entry and a library food are different objects. Editing a library food affects future logs only. Past entries keep their nutrition snapshot and conversion provenance.
- Foods that don't resolve go into a queue: local match first, then explicit OFF search or barcode scan, then personal-food creation. The typed quantity and context are preserved throughout.
- OFF search is explicit; there is no network search-as-you-type. Contributing to OFF is a separate, optional action from food detail. It never submits logging estimates as public nutrition facts and never persists the OFF password.
- Core logging works offline. OFF search, barcode lookup and contribution need a connection.

## Capabilities and Constraints

- Kotlin, Jetpack Compose and Material 3 on Android (minSdk 26, targetSdk 35). Storage is local Room through 1.0, and cloud sync is out of scope before then. Barcodes are scanned with CameraX and ML Kit.
- A bundled catalogue of USDA FoodData Central generic foods (pinned build-time snapshots, no runtime USDA dependency or API key) plus OFF for packaged and regional products. Unqualified meat resolves to raw records.
- Calories are required to create a personal food; protein, carbs and fat may be unknown. Uncertain serving conversions are visibly marked as approximate.
- Out of scope: recipes, saved meals, goals, weight, progress, reports, exercise, water, onboarding or profile, accounts, subscriptions or premium gating, and configurable meal names. Recipes and saved meals may come later.
- This is a personal, open-source project with limited engineering capacity. Reuse existing components before adding dependencies.
- Undecided: how a deleted personal food is presented (the recommended default is to archive it while keeping it readable in history); whether OFF results are cached automatically or saved explicitly (the recommended default is to keep the current automatic save, with a source label); and whether to keep a contribution history (the recommended default is to defer it). A preparation-preference setting has been postponed.

## Brand Commitments

- The product name is **Breakfast**. **Iron** was its development name and is still the repository name.
- UI copy is minimal. Labels are short and specific, with no slogans, helper paragraphs or instructional empty states. If an action needs explaining, fix the affordance instead.
- The design must have its own identity. A first design canvas was rejected for looking like a generic AI-minimal default. An earlier brief asked for a modern, slick, elegant feel with a restrained amount of green. Treat that as a prior request, not a settled palette.
- Familiarity comes from MyFitnessPal's interaction patterns, never from its visuals.

## Evidence on Hand

- A working prototype covering the diary, meal detail, Library, entry editing, OFF search, scan and contribution (`app/`). Current-app screenshots and a manifest are in `design/claude-ui-handoff/screenshots` and `SCREENSHOT_MANIFEST.md`.
- Product and UX documents: `docs/breakfast-ux-redesign.md` and `docs/mfp-ux-workflow-map.md`. The previous design brief is `design/claude-ui-handoff/CLAUDE_DESIGN_PROMPT.md`.
- USDA source-backed fixture records with their FDC IDs and release dates, plus English and Cyrillic alias fixtures.
- Nothing exists yet in the way of users, testimonials, usage metrics or speed benchmarks. Do not invent logging-speed claims or comparisons before the evaluation has been run.

## Product Principles

1. **Typing beats tapping.** The shorthand field is the primary path. Every other flow is a fallback and should cost no more interaction than it saves.
2. **The user's foods win.** Personal aliases, custom foods and explicit history outrank global data. Preferences are learned only from deliberate choices.
3. **Nutrition is recorded fact.** Values come from real records, and approximations are labelled. History is never silently rewritten.
4. **Correctable, never interruptive.** Resolution feedback is brief and dismissible. Typing is never blocked by a picker or modal.
5. **Local and free.** Core logging works offline and without an account or subscription.

## Accessibility & Inclusion

The baseline is Android defaults: WCAG AA contrast, support for system font scaling, 48dp touch areas (controls drawn at 44dp keep that size; DESIGN.md), and TalkBack custom actions for every gesture-only interaction (detail, quick edit, full edit, delete). Food names in English and Macedonian, in both Latin and Cyrillic, must render and wrap correctly, including long names. Light and dark themes are expected.

**Theme:** Monochrome light and dark tokens, following the system setting.
