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

1. **Type what you ate.** "Your entry is just a comma separated list." Example: "2 eggs, 100g oats" with its rows and kcal.
2. **The green one is what the app would log.** "Partial searches also work." Example: "ban" with a green Banana chip; the words sit under it, with an arrow up to the chip.
3. **Not in your foods?** "Tap Find matches to look it up on Open Food Facts." Example: a "Not in your foods" row with the "Find matches" button.
4. **We remember what you pick.** "You can also scan barcodes." Example: a food row and a recipe row.
5. **Your goals.** "Set up your goals in Profile." Buttons: "Open Profile" and "Start logging". Example: macro stats with targets.

Line by line: `docs/onboarding-copy.csv`, the owner's edits.

## Rules
- Follows `docs/standards/maintainable_mobile_app_development_guide.md`, and `DESIGN.md`; onboarding is the one place explanatory sentences are allowed.
- A reusable pager in `ui/components` with pure, unit-tested page state (next, back, where the pager settled, arrival on the last page); skipping and finishing are the owner's. Examples reuse the real rows, chips and bars rather than copying or using pictures.
- The planner is not run on the user's foods; examples use fixed sample values.
- No new dependencies, navigation library or schema change.
