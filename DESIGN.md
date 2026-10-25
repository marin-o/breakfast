---
name: Breakfast
description: Monochrome. Soft grey ground, ink type, one big figure per screen, green and red only for actions.
colors:
  bg: "#D0D4D8"
  ink: "#111214"
  soft: "#56595E"
  line: "#BBBFC2"
  ok: "#13804F"
  on-ok: "#FFFFFF"
  bad: "#A3291F"
  scrim: "rgba(17,18,20,0.32)"
  camera-ground: "#0B0C0E"
  camera-mark: "#FFFFFF"
dark-colors:
  bg: "#111214"
  ink: "#F2F3F5"
  soft: "#9A9DA4"
  line: "#26282C"
  ok: "#3DBE7E"
  on-ok: "#0B1F14"
  bad: "#F0675B"
  scrim: "rgba(0,0,0,0.6)"
  camera-ground: "#0B0C0E"
  camera-mark: "#FFFFFF"
typography:
  family: "Geologica (300, 400, 500, 600), tabular numbers, mk locale on the root style"
  hero: "72sp/76sp 300, tracking -0.04em, one per screen"
  large-input: "26sp/34sp 300"
  section: "20sp/26sp 500"
  title: "17sp/22sp 500"
  macro-value: "20sp/26sp 500"
  body: "16sp/22sp 400"
  button: "16sp/22sp 500"
  secondary: "14sp/19sp 400 soft"
  meta: "13sp/18sp 400 soft"
shapes:
  field-and-choice: "12dp"
  secondary-and-snackbar: "14dp"
  primary-and-composer: "16dp"
  sheet-top: "20dp"
  add-circle: "full"
  gutter: "24dp"
  header: "52dp"
  icon-target: "44dp"
  primary-height: "52dp"
  secondary-height: "48dp"
  field-min-height: "52dp"
---

# Design System: Monochrome

Breakfast is a calm, content-first calorie log. Numbers and food names carry each screen. Chrome is quiet: soft grey ground, ink type, 1dp hairlines between regions, whitespace for grouping. Light is the primary theme; dark follows the system setting with the token table above.

## Rules

- Colours are only the tokens above. Interactive elements are ink. Green (`ok`) is only for constructive actions (add, save, log, restore). Red (`bad`) is only for delete, archive and error. Nothing else is coloured.
- One big figure per screen at most (the 72sp day total or entry kcal). Everything else is calm.
- No cards, no shadows (the one exception is the Library overflow popover), no gradients (the composer's `bg` fade is the only one), no chips or pills, no fills behind content.
- Sentence case everywhere. No all-caps labels, no middle dots, no emoji, no decimal commas. Decimals use a dot; numbers are tabular with no thousands separators.
- Source (Bundled, Personal, Open Food Facts) appears only as a soft line on entry, food and search detail. It never appears in lists.
- Approximate amounts are written plainly in the quantity line: `≈1 tbsp, 16 g`.
- Icons: stroke 1.8, round caps and joins, 20 to 22dp, ink or soft.
- Touch targets are at least 44dp. No spinners: progress is a 2dp `line` track with a 2dp `ink` sweep and a soft caption.

## Controls

- **PrimaryButton**: `ok` fill with `on-ok` text for constructive actions; `ink` fill with `bg` text for neutral ones (Continue, Retry, Edit amount). 16sp/500, radius 16, 52dp. One per screen. Disabled at 45% alpha.
- **SecondaryButton**: 1dp `line` outline, ink text, radius 14, 48dp.
- **TertiaryButton**: ink text, 44dp, no underline. `soft` for low emphasis.
- **DestructiveButton**: `bad` text. Inside sheets and confirmations, a full-width 1dp `bad` outline, radius 16, 52dp.
- **ChoiceOption**: unselected 1dp `line` outline with soft text; selected 1.5dp `ink` outline with ink 500. Radius 12, min 44dp. Optional soft second line (kcal).
- **AddCircle**: 32dp circle, 1.5dp ink outline and a `+` (or another icon), in a 44dp target.
- **Field**: label 13sp soft above; 1dp `line` box, radius 12, min 52dp, 16sp text. Focus 1.5dp `ink`. Error 1.5dp `bad` and 13sp `bad` text below. Optional soft trailing hint.
- **SearchField**: a Field with a leading magnifier and a trailing clear.
- **Composer**: `ink` bar, radius 16, 52dp, placeholder in `bg` at 60%, scan icon on the right. Fixed at the bottom over a `bg` fade.
- **BottomNav**: centred text tabs "Today" and "Library", 40dp apart, 14sp/500. Active ink, inactive soft.
- **Sheet**: `bg`, top radius 20, 36x5 `line` grabber, 24dp padding, `scrim` behind.
- **Snackbar**: `ink` fill, radius 14, `bg` text, the action in `bg` 500 on the right, 16dp from the edges.

## Lists and totals

- **FoodRow**: name 16sp ink, quantity line 14sp soft beneath, kcal right-aligned in soft (ink 500 for totals). 9dp top padding, no separators.
- **MealHeader**: a 30dp gap, a 1dp hairline, then 16dp, then the heading (20sp/500), the time (13sp soft), the total (17sp/500) and an AddCircle. At large font the time drops under the heading. "Nothing yet" in soft for an empty meal.
- **DayHeader**: date title 17sp/500 on the left, a Today text button when not on today, previous and next arrows on the right.
- **DayTotal**: 72sp/300 kcal, "kilocalories today" in soft 14sp, then three macro columns: a 2dp `line` track with a 2dp `ink` fill (the macro's share of kcal), the value 20sp/500 with its unit in soft 14sp, and a soft 13sp label. At large font (2.0) the macros stack one per row, with the value, unit and label on one line under each bar. A macro no entry has reads "—" (as in the Library); a sum that leaves out entries without the macro reads "12 g ?", the "?" in the unit's soft style, and one soft 13sp line "? some foods have no value" follows the macros.

## Layout

- Gutters are 24dp. The status bar is 40dp as the system gives it. The header is 52dp.
- Regions are separated by 30dp of space, a 1dp `line` hairline, and 16dp.
- Large font: the layouts switch at a font scale of 1.8 (`isLargeFont` in `Components.kt`). 1.3 keeps the standard layout. Fixed-width controls keep a minimum width and grow with their text; nothing shrinks below the type scale.

## Shared components

The single implementation of each control lives in `ui/components/Components.kt`. Screens build their controls from those components; no screen draws its own copy of a control.

## Source of truth

The locked references are `design/canvas/BRIEF-mono-system.md` (tokens, type and controls) and the `M-*` mocks in `design/canvas/project/` and `design/canvas/shots/`. Where a state cannot be built as drawn, the closest honest version is built and the gap is recorded in the gaps ledger (`docs/design-gaps.md`).
