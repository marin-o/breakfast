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

- Colours are only the tokens above. Interactive elements are ink. Green (`ok`) is only for constructive actions (add, save, log). Red (`bad`) is only for delete and error. Nothing else is coloured.
- One big figure per screen at most (the 72sp day total or entry kcal). Everything else is calm.
- No cards, no shadows, no gradients (the composer's `bg` fade is the only one), no chips or pills, no fills behind content.
- Sentence case everywhere. No all-caps labels, no middle dots, no emoji, no decimal commas. Decimals use a dot; numbers are tabular with no thousands separators.
- Source (Bundled, Personal, Open Food Facts) appears only as a soft line on entry, food and search detail. It never appears in lists.
- Approximate amounts are written plainly in the quantity line: `≈1 tbsp, 16 g`.
- Icons: stroke 1.8, round caps and joins, 20 to 22dp, ink or soft.
- Controls are drawn at 44dp or more; every tappable control has a 48dp touch area. The touch area is Compose's minimum touch target (hit testing and the TalkBack bounds both reach 48dp around a smaller control), so it adds no layout space and no pixels; `minimumInteractiveComponentSize()` is not used because it would grow the layout. No spinners: progress is a 2dp `line` track with a 2dp `ink` sweep and a soft caption.

## Controls

- **PrimaryButton**: `ok` fill with `on-ok` text for constructive actions; `ink` fill with `bg` text for neutral ones (Continue, Retry, Edit amount). 16sp/500, radius 16, 52dp. One per screen. Disabled at 45% alpha.
- **SecondaryButton**: 1dp `line` outline, ink text, radius 14, 48dp.
- **TertiaryButton**: ink text, 44dp, no underline. `soft` for low emphasis.
- **DestructiveButton**: `bad` text. Inside sheets and confirmations, a full-width 1dp `bad` outline, radius 16, 52dp.
- **ChoiceOption**: unselected 1dp `line` outline with soft text; selected 1.5dp `ink` outline with ink 500. Radius 12, min 44dp. Optional soft second line (kcal).
- **AddCircle**: 32dp circle, 1.5dp ink outline and a `+` (or another icon), in a 44dp box with a 48dp touch area.
- **Field**: label 13sp soft above; 1dp `line` box, radius 12, min 52dp, 16sp text. Focus 1.5dp `ink`. Error 1.5dp `bad` and 13sp `bad` text below. Optional soft trailing hint.
- **SearchField**: a Field with a leading magnifier and a trailing clear.
- **Composer**: `ink` bar, radius 16, 52dp, placeholder in `bg` at 60%, scan icon on the right. Fixed at the bottom over a `bg` fade; one for all the base screens, its text the Log opener or the Library's search.
- **BottomNav**: centred text tabs "Meals" (Daily) and "Library", 40dp apart, 14sp/500. Active ink, inactive soft. At the right end a 22dp soft Settings icon in a 44dp target opens Settings over the screen; Back returns to it.
- **Sheet**: `bg`, top radius 20, 36x5 `line` grabber, 24dp padding, `scrim` behind.
- **Snackbar**: `ink` fill, radius 14, `bg` text, the action in `bg` 500 on the right, 16dp from the edges.

## Foods

- A personal food's page and Edit food end with **Delete food** (DestructiveButton, outlined). It asks first in a dialog: "Past entries keep their numbers." with **Delete food** and **Cancel**. Deleting is for good (no Undo); past entries keep their own name and numbers. There is no Archived list and no Restore.
- The Library header on the Library tab is the title (17sp/500, at Daily's height, as every header), its count line and **New food**; there is no menu. A meal's Library has the back arrow and "Wed 14 Oct", the one way a day is written.
- Numbers: a macro nothing knows reads "—", never 0. Under a kcal figure the macros read "Protein 8 g, carbs 40 g, fat 4 g"; inside the scan review's line, "P 2, C 9, F 8". A field shows at most three decimals, and that is display only: left as it was, it keeps the exact stored value.
- The nutrient fields are two by two, 12dp apart on New food and 16dp on Edit food; the scan review has them in one row, 8dp apart, while a label and a number with its padding fit a quarter.
- A scan's review is for where it started: the Library tab saves to the Library; Daily, a meal or a meal's Library logs, with the amount and the meal (starting on the automatic meal); the Log page adds a part to its draft, with the amount and "Add", no meal. An unknown barcode goes through New food to the same step.
- Foods Add could not match stay on the Log, for the same meal: each "Not in your foods" row opens and closes (Quiet motion, 200ms; a chevron turns), the first starts open, and under it, inset 16dp (`PartInset`), are its Open Food Facts results as FoodRows, then its ways on: Search Open Food Facts, Create personal food, Search your foods, Skip. While searching it shows the search's progress line; offline, on an error or with no results it says so in the search screen's words. A tapped result opens the review for that part; a resolved one leaves the list, the next opens, and the last closes the Log. Open Food Facts is searched only on Add, one food at a time, for foods with no local match. A part with several local matches reads "which one?" and opens and closes the same way, with those foods inset under it (the amount it would log, and kcal); one tap logs that food to the meal and takes the part out of the draft. A result is shown only with a name word in the script typed, on the search screen too. While rows remain, the Log shows the Undo snackbar of the batch that logged, "Not right?" waits until the Log closes, and the meal button changes where they log.
- A tapped Log suggestion fills the name and a space when no amount was typed, so one can follow it ("Extra virgin olive oil 200 g"); a typed amount is kept. With none added, Add logs the food's default, as the row shows.

## Lists and totals

- **FoodRow**: name 16sp ink, quantity line 14sp soft beneath, kcal right-aligned in soft (ink 500 for totals). 9dp top padding, no separators.
- **MealHeader**: a 30dp gap, a 1dp hairline, then 16dp, then the heading (20sp/500), the time (13sp soft), the total (17sp/500) and an AddCircle. At large font the time drops under the heading. "Nothing yet" in soft for an empty meal.
- **DayHeader**: date title 17sp/500 on the left, a "Go To Today" text button when not on today, previous and next arrows on the right.
- **DayTotal**: 72sp/300 kcal, "kilocalories today" in soft 14sp, then three macro columns: a 2dp `line` track with a 2dp `ink` fill (the macro's share of kcal), the value 20sp/500 with its unit in soft 14sp, and a soft 13sp label. At large font (2.0) the macros stack one per row, with the value, unit and label on one line under each bar. A macro no entry has reads "—" (as in the Library); a sum that leaves out entries without the macro reads "12 g ?", the "?" in the unit's soft style, and one soft 13sp line "? some foods have no value" follows the macros.

## Layout

- Gutters are 24dp. The status bar is 40dp as the system gives it. The header is 52dp.
- Regions are separated by 30dp of space, a 1dp `line` hairline, and 16dp.
- Large font: the layouts switch at a font scale of 1.8 (`isLargeFont` in `Components.kt`). 1.3 keeps the standard layout. Fixed-width controls keep a minimum width and grow with their text; nothing shrinks below the type scale.

## Motion

Quiet: short and smooth, standard easing (`FastOutSlowInEasing`), no bounce or overshoot. Things glide in and out and stop; nothing waits for an animation, and a screen or row on its way out can no longer be used.

- **Screens over the base** (Log, entry detail and full edit, Library food page, food form, Settings, Open Food Facts search, resolver, "No local match", contribution screens): fade and a 24dp rise, 220ms in, 160ms out. The base stays still under the entry detail as it comes in. The scanner opens and closes without motion (its brackets keep their 280ms slide above the review sheet).
- **Base screens** (Daily, a meal, the Library, a meal's Library, Recipes): the new screen fades in over the old one, 240ms in, 200ms out. The bottom area (the snackbars, the ink bar, the tabs) is one, over every base screen, and stays where it is: only the bar's text crosses (the Log opener to the Library's search and back, a meal's "Add to" line), 220ms; on Recipes the bar fades where it stands, so the tabs do not move; under a meal or a meal's Library the tabs fold away, 200ms. The list moves too: to another tab it comes 24dp from the side that tab lies on in the bar (Meals, Library, Recipes, the gear last) and the old one leaves the other way; into a meal or a meal's Library it rises 24dp, and back out the old one falls 24dp. The screen on its way out can no longer be used.
- **Bottom bar:** the active item (the gear while Settings is open) is ink with a 2dp ink line under it, the others soft; when it changes, the line slides to the new item and the colours cross, 220ms.
- **Settings pages:** a page fades in from 24dp at the end, Back brings the one before in from the start (Licences lies under About), 220ms in, 160ms out.
- **Sheets:** Material's own slide up, not doubled. **Dialogs and menus:** the platform's and Material's own.
- **Undo snackbars and "Not right?":** fade and an 8dp rise, 180ms; "Not right?" opens and closes its space with it.
- **Lists** (meal rows on Daily and the Meal screen, the Library list): a new row fades in, a removed row collapses out, 200ms; Library rows that move (a logged food goes to the top) glide to their place. Another day shows its rows at once. The Log's rows change without motion: a row has no identity that lasts while its text is typed.
- **Day change** (swipe, arrows, Today, date picker): the day's content fades in from 24dp to the side the day lies on, 220ms.
- **Swipe to delete:** the row follows the finger and the `bad` panel (a rounded button shaped like the primary buttons, inset 8dp from the row and 6dp top and bottom) shows only as wide as the row has moved; dragging right shuts an open row, and a tap on an open row shuts it ("Delete" stays on one line, cut by the panel's edge until it is open); on every release (a tap too) it settles open or shut in 180ms; Delete collapses it, Undo fades it back.
- **Reduced motion** (Settings, Motion is Reduced, or the phone's animations are removed): all of this is off and every change is instant; a sheet is simply there (dragged or tapped away, it still uses Material's slide). The one rule is `ui/components/Motion.kt`.

## Shared components

The single implementation of each control lives in `ui/components/Components.kt`. Screens build their controls from those components; no screen draws its own copy of a control.

## Source of truth

The locked references are `design/canvas/BRIEF-mono-system.md` (tokens, type and controls) and the `M-*` mocks in `design/canvas/project/` and `design/canvas/shots/`. Where a state cannot be built as drawn, the closest honest version is built and the gap is recorded in the gaps ledger (`docs/design-gaps.md`).
