# More nutrients and a fibre target

Owner, 2026-11-04. Foods get saturated fat, sugars, fibre and salt, the four lines that food labels nearly always show.
Fibre also gets an optional daily target, shown on the Meals page in its own colour. Micronutrients are out: labels
rarely list them.

## Nutrients

- **Saturated fat** sits under Fat, and **sugars** under Carbs, as on a label ("of which saturates", "of which sugars").
  **Fibre** and **salt** stand on their own.
- All four are optional, in grams per the food's basis, and unknown when empty, exactly like protein, carbs and fat
  today. Unknown is never read as 0.
- **Salt only.** Labels list salt, so there is no sodium field and the shorthand doesn't read sodium.
- **Spelling:** "Fibre", British, matching "Licences".

## Where they go

1. **Model and storage.** `NutritionValues` gets the four as nullable fields. Room schema 13 adds four nullable columns
   to every table that keeps nutrition: foods, entry snapshots, and any recipe tables that store their own numbers.
   The migration is additive only; old rows read as unknown.
2. **Logged entries** keep the four in their snapshot, like the macros, so past days keep what they were logged with.
3. **Scaling, sums and recipes** carry the four the same way they carry the macros (`times`, `plus`, recipe totals).
4. **Food forms:** New food, Edit food and the scan review, through the shared field components. Saturated fat goes
   next to or under Fat, sugars next to or under Carbs, then fibre and salt. Layout follows the existing nutrient grid;
   no new component if an existing one fits.
5. **Food page:** the nutrition list (`NutritionFacts` in `ui/components/Rows.kt`) shows the four when known.
   "Saturated fat" is indented under Fat and "Sugars" under Carbohydrate, with no "of which".
6. **Nutrition shorthand** (`domain/NutritionShorthand.kt`): number first, as now. `2 sat fat`, `2 satfat`, `2 sat f`,
   `2 satf`, `2g saturated`, `2 saturates` (owner); `5 sugar`, `5 sugars`; `3 fibre`, `3 fiber`; `1 salt`. There are no single letters: `s` could be any
   of three. `2g sat fat` must not also fill Fat. `3 fiber` must fill fibre, not fat; it fills nothing today.
7. **Open Food Facts:** read `saturated-fat_100g`, `sugars_100g`, `fiber_100g` and `salt_100g`. A contribution sends
   them too when the user entered them.
8. **USDA catalogue:** rebuild with `tools/build_usda_catalogue.py`, adding fibre (nutrient 1079), total sugars (2000),
   saturated fat (1258) and salt from sodium (1093, mg × 2.5 / 1000, to grams). This is the catalogue's own
   conversion, not a sodium field. Bump the catalogue version.
9. **Export and import:** the four go into the export. An old export without them still imports, as unknown.

## Fibre target

- **Profile, Goals:** one optional field, "Fibre, g", after the macro fields. It doesn't belong to the macro
  percentages or Apply: fibre is not part of the kcal split. Empty means no target.
- **Meals page:** with a fibre target set, the day's header shows fibre beside protein, carbs and fat, with its total
  and target, in the same style as the macros, and its own colour. Without a target, nothing changes.
- **Day total:** the sum of the entries' known fibre, the same way the macros are summed.

## Tracking fibre (owner, 2026-11-04, replaces the Settings checkbox)

- **Always, for everyone:** fibre's field in New food, Edit food and the scan review, and its line on the food page,
  always with its green dot, like the macros' dots.
- **"Track fibre"** is a checkbox in Profile → Goals, after the macro fields, off by default. Ticking it shows the
  optional "Fibre, g" target field under it. Unticking hides the field and keeps the stored target, the way Tracking
  only keeps macro targets. There is no Settings option.
- **With Track fibre on**, fibre is treated like the macros:
  - the Meals header column (with no target: its total only, the way a macro looks without a target);
  - the macro lines that list protein, carbs and fat as numbers, with fibre after fat (the amount sheet, Edit entry,
    the entry detail, the Nutrition page's daily average, the recipe page);
  - the "some foods have no value" note counts fibre (owner: keep it).
  It is never part of a kcal split or a kcal bar.
- **With it off**, fibre is in none of those, even with a target stored.

## Fibre colour

The green of the avocado in the logo: skin `#2F6B3A` and flesh `#CFE08A`. Suggested: `#2F6B3A` in light mode and
`#CFE08A`, or a green near it, in dark mode. It must stay clearly apart from the action green `ok` (`#13804F` light,
`#3DBE7E` dark), and pass contrast for a small mark on `bg`.

Every macro palette gets a fibre colour: Standard, Protanopia/Deuteranopia, Tritanopia and Monochrome (ink there, like
the others). Under protan and deutan vision, green collides with the gold of fat and the red family, so pick a colour
those palettes can tell apart from protein, carbs and fat. Check it with a simulation.

## Wording to approve before shipping

Proposed; the owner approves:

| Where | Text |
| --- | --- |
| Food form fields | "Saturated fat, g", "Sugars, g", "Fibre, g", "Salt, g" |
| Food page | "Saturated fat", "Sugars", "Fibre", "Salt" (owner: no "of which"; the indent shows the nesting) |
| Profile, Goals | "Fibre, g" |
| Meals header | "Fibre", in the same form as "Protein" |
| Profile → Goals | "Track fibre" (checkbox) |
| Macro lines | "fibre 3 g" / "Fibre 3 g", following the case of the line's other macros |

## Out of scope

- Mono, poly and trans fat; micronutrients; sodium; targets for sugars, salt or saturated fat.
- Totals of sugars, salt or saturated fat on the Nutrition page.
