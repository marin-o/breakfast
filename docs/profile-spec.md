# Profile

Owner's request, 2026-10-31. Everything here is optional: an empty profile changes nothing in the app.

## What it holds
- Name or nickname.
- A calorie target, in kcal per day.
- Macro targets: protein, carbs and fat, in grams per day.
- Current weight and target weight, in kg.
- The weight curve: one point per diary day, or several with "Several weights a day" on.

## Where it lives
A Profile page of its own, separate from Settings (owner, 2026-10-31). It opens from a small profile icon at the left end of the bottom nav, mirroring the Settings gear at the right end. The icon follows the gear's size, touch target, colours and selected state, and the page opens and goes back the way Settings does, sharing that code rather than copying it.

## Updating the weight
- Changing current weight shows a tick, on by default: "Add to curve". With the tick on, saving adds a point to the curve. With it off, only current weight changes.
- "Several weights a day" is a tick on the Profile page, off by default (owner, 2026-10-31: some people weigh morning and evening).
  - Off: a second point on the same diary day replaces that day's latest point. The day uses the "Day starts at" setting, like the diary.
  - On: every save with "Add to curve" ticked adds a point, even at the same number, and the tick shows whenever current weight is valid. It starts ticked for a changed weight, or when today has no point; to record the same number again, tick it. So editing the name, or a double tap on Save, adds nothing.
  - Turning it off keeps every point already there.
- Current weight stays what was last saved, even if the tick was off.

## The curve
- Drawn with a chart library (owner, 2026-10-31: "just use some library for curves don't custom build it"), behind one wrapper in `ui/components`, so no screen knows the library.
- It has axes: dates along the bottom, kg up the side, with readable labels.
- Monochrome, per DESIGN.md. The target weight shows as a line when it is set.
- Empty until there is a point. One point is one day's weight, so it draws as one dot, with its axes.
- Points sit at their real time, so two on one day are apart. The date labels stay dates.
- Under the curve, the points are listed newest first. A day with several shows the time on each ("Sat 31 Oct, 07:52"). A wrong point can be deleted, with the shared confirm dialog.

## Calorie target and presets
- The profile also holds a daily calorie target. Meals shows it next to the day's total, the way macros show theirs.
- Presets sit above the macro fields. A preset fills the fields, which stay editable, and saves nothing until Save:
  - Kcal and protein: 2 g of protein per kg of current weight (25% of kcal when no weight is set). Carbs and fat stay empty.
  - Balanced: 30% protein, 40% carbs, 30% fat.
  - High protein: 40% protein, 30% carbs, 30% fat.
  - Low carb: 30% protein, 20% carbs, 50% fat.
- Percentages are of the calorie target, at 4 kcal per gram of protein and carbs and 9 per gram of fat, rounded to whole grams. With no calorie target, a preset that needs one asks for it through the field's own error, and changes nothing.
- The conversion is one pure, unit-tested function.

## Macro targets on Meals
- No target: the macro bar works as it does today.
- Target set: the bar fills toward it, capped at full, and the amount reads "28.6 / 120 g". Going over target is not coloured or scolded.

## Data
- Weight points go in Room: schema 9 to 10 added the table. Schema 10 to 11 (approved by the owner's request) rebuilds only that table so a day can hold several points. Every existing point survives with its weight and time, with a migration test.
- Name, targets and weights go in the diary export, and they come back on import. Exports made before this must still import.
- Deleting all data also clears the profile.

## Rules
- Follows `docs/standards/maintainable_mobile_app_development_guide.md`, and `DESIGN.md`.
- Input validation is a pure function: a weight must be a positive number, and a target must be a positive number or empty. Both decimal marks are accepted.
- The chart library is the one new dependency. No DI, navigation library or Gradle modules.
