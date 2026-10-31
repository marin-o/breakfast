# Profile

Owner's request, 2026-10-31. Everything here is optional: an empty profile changes nothing in the app.

## What it holds
- Name or nickname.
- Macro targets: protein, carbs and fat, in grams per day. Calories are not a separate target.
- Current weight and target weight, in kg.
- The weight curve: one point per diary day.

## Where it lives
A Profile page of its own, separate from Settings (owner, 2026-10-31). It opens from a small profile icon at the left end of the bottom nav, mirroring the Settings gear at the right end. The icon follows the gear's size, touch target, colours and selected state, and the page opens and goes back the way Settings does, sharing that code rather than copying it.

## Updating the weight
- Changing current weight shows a tick, on by default: "Add to curve". With the tick on, saving adds a point to the curve. With it off, only current weight changes.
- A second point on the same diary day replaces that day's point. The day uses the "Day starts at" setting, like the diary.
- Current weight stays what was last saved, even if the tick was off.

## The curve
- Drawn with Compose Canvas (no chart library) as a reusable component in `ui/components`. Its scaling is a pure, unit-tested function.
- Monochrome, per DESIGN.md. The target weight shows as a line when it is set.
- Empty until there is a point. One point draws as a dot.
- Under the curve, the points are listed newest first. A wrong point can be deleted, with the shared confirm dialog.

## Macro targets on Meals
- No target: the macro bar works as it does today.
- Target set: the bar fills toward it, capped at full, and the amount reads "28.6 / 120 g". Going over target is not coloured or scolded.

## Data
- Weight points go in Room: schema 9 to 10, one new table, additive only, with a migration test. Existing data must survive untouched.
- Name, targets and weights go in the diary export, and they come back on import. Exports made before this must still import.
- Deleting all data also clears the profile.

## Rules
- Follows `docs/standards/maintainable_mobile_app_development_guide.md`, `AGENTS.md` and `DESIGN.md`.
- Input validation is a pure function: a weight must be a positive number, and a target must be a positive number or empty. Both decimal marks are accepted.
- No new dependencies, DI, navigation library or Gradle modules.
