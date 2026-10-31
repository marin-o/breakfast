# Onboarding

Owner's request, 2026-10-31: the app is opinionated and unconventional, so it needs an onboarding and tutorial sequence.

## Behaviour
- Shows once, on the first launch after this ships, before the diary. That includes people who already use the app; their data is untouched.
- A short sequence of pages: swipe or Next forward, Back goes back, Skip on every page. Finishing or skipping marks it done for good.
- It can be replayed from a "Show the tutorial" row in Settings.
- Done state lives in app settings. It doesn't enter the diary or the export, and "Delete all data" doesn't reset it. Process death returns to the same page.
- Never blocks use: no permission prompts, no required fields, no network.

## Pages
Each page: a short title, one or two short sentences, and a live example drawn with the app's real components and sample data. Nothing on these pages writes to the diary.

1. **Type what you ate.** "One line, like a text. Commas split foods." Example: "2 eggs, 100 g oats" with its rows and kcal.
2. **The green one gets logged.** "Half a word is enough. Add logs the green match, no extra tap." Example: "ban" with a green Banana chip.
3. **Not in your foods?** "Find matches looks it up on Open Food Facts. Tap a result and carry on." Example: a "Not in your foods" row with the "Find matches" button.
4. **Your foods learn.** "Scan a barcode once and it's yours. Build recipes in Recipes and log them the same way." Example: a food row and a recipe row.
5. **Make it yours.** "Set a name, macro targets and your weight, or don't." Buttons: "Open Profile" and "Start logging". Example: macro stats with targets.

The wording above is a proposal for the owner to judge on the device.

## Rules
- Follows `docs/standards/maintainable_mobile_app_development_guide.md`, `AGENTS.md` and `DESIGN.md`; onboarding is the one place explanatory sentences are allowed.
- A reusable pager in `ui/components` with pure, unit-tested page state (next, back, where the pager settled, arrival on the last page); skipping and finishing are the owner's. Examples reuse the real rows, chips and bars rather than copying or using pictures.
- The planner is not run on the user's foods; examples use fixed sample values.
- No new dependencies, navigation library or schema change.
