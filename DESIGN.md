---
name: Breakfast
description: A shop counter at night. Meals are torn-off thermal tickets, and pen blue marks what you can tap.
colors:
  ground: "#2A2730"
  tray: "#1F1D24"
  on-ground: "#ECEBE6"
  on-ground-soft: "#B5B0BF"
  paper: "#E6E6DF"
  sticker: "#F0EFE9"
  paper-fresh: "#D6D6CC"
  ink: "#1E1B24"
  ink-soft: "#544F5E"
  rule: "#B3B0BD"
  box: "#8C8798"
  print-red: "#9C2F25"
  on-red-soft: "#F2D4CC"
  pen-blue: "#3D63D9"
  on-pen: "#FFFFFF"
  pen-tonal: "#D3DAEC"
  pen-tonal-ink: "#1F3D94"
  pen-on-dark: "#9DB2EC"
  pen-on-dark-ink: "#DDE4F7"
  nav-indicator: "#AFC0EE"
  nav-indicator-ink: "#12245E"
  scrim: "rgba(15, 13, 18, 0.62)"
  camera-ground: "#141218" # camera preview ground only (scanner screen); never used as a surface elsewhere
typography:
  display-double:
    fontFamily: "Noto Sans Mono, monospace"
    fontSize: "17px"
    fontWeight: 700
    lineHeight: "24px"
    fontFeature: "tnum"
  figure-total:
    fontFamily: "Noto Sans Mono, monospace"
    fontSize: "26px"
    fontWeight: 700
    lineHeight: "30px"
    fontFeature: "tnum"
  title-large:
    fontFamily: "Noto Sans Mono, monospace"
    fontSize: "20px"
    fontWeight: 700
    lineHeight: "26px"
    fontFeature: "tnum"
  figure-row:
    fontFamily: "Noto Sans Mono, monospace"
    fontSize: "18px"
    fontWeight: 700
    lineHeight: "24px"
    fontFeature: "tnum"
  title:
    fontFamily: "Noto Sans Mono, monospace"
    fontSize: "17px"
    fontWeight: 700
    lineHeight: "24px"
  body:
    fontFamily: "Noto Sans Mono, monospace"
    fontSize: "17px"
    fontWeight: 400
    lineHeight: "24px"
    fontFeature: "tnum"
  input:
    fontFamily: "Noto Sans Mono, monospace"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: "24px"
  label-button:
    fontFamily: "Noto Sans Mono, monospace"
    fontSize: "16px"
    fontWeight: 700
    lineHeight: "24px"
  label-chip:
    fontFamily: "Noto Sans Mono, monospace"
    fontSize: "15px"
    fontWeight: 400
    lineHeight: "20px"
  body-secondary:
    fontFamily: "Noto Sans Mono, monospace"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: "20px"
    fontFeature: "tnum"
  label-nav:
    fontFamily: "Noto Sans Mono, monospace"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: "18px"
  label-tag:
    fontFamily: "Noto Sans Mono, monospace"
    fontSize: "13px"
    fontWeight: 400
    lineHeight: "18px"
rounded:
  handle: "2px"
  tag: "3px"
  paper: "6px"
  sticker: "8px"
  sheet: "16px"
  pill: "24px"
  field-pill: "27px"
spacing:
  hairline: "4px"
  tight: "6px"
  gap: "8px"
  gap-wide: "10px"
  row-y: "11px"
  margin: "14px"
  ticket-gap: "16px"
  sheet: "20px"
components:
  button-primary:
    backgroundColor: "{colors.pen-blue}"
    textColor: "{colors.on-pen}"
    typography: "{typography.label-button}"
    rounded: "{rounded.pill}"
    height: "48px"
    padding: "0 20px"
  button-tonal:
    backgroundColor: "{colors.pen-tonal}"
    textColor: "{colors.pen-tonal-ink}"
    typography: "{typography.label-button}"
    rounded: "{rounded.pill}"
    height: "48px"
    padding: "0 20px"
  button-outlined-ground:
    backgroundColor: "transparent"
    textColor: "{colors.pen-on-dark-ink}"
    typography: "{typography.label-button}"
    rounded: "{rounded.pill}"
    height: "48px"
    padding: "0 20px"
  button-text-ground:
    backgroundColor: "transparent"
    textColor: "{colors.pen-on-dark-ink}"
    typography: "{typography.label-button}"
    height: "48px"
    padding: "0 14px"
  button-text-paper:
    backgroundColor: "transparent"
    textColor: "{colors.pen-tonal-ink}"
    typography: "{typography.label-button}"
    height: "48px"
    padding: "0 14px"
  button-destructive:
    backgroundColor: "transparent"
    textColor: "{colors.print-red}"
    typography: "{typography.label-button}"
    rounded: "{rounded.pill}"
    height: "48px"
    padding: "0 20px"
  button-disabled:
    backgroundColor: "transparent"
    textColor: "{colors.box}"
  button-row-repeat:
    backgroundColor: "{colors.pen-tonal}"
    textColor: "{colors.pen-tonal-ink}"
    rounded: "{rounded.pill}"
    size: "48px"
  chip-choice:
    backgroundColor: "transparent"
    textColor: "{colors.pen-tonal-ink}"
    typography: "{typography.label-chip}"
    rounded: "{rounded.pill}"
    height: "48px"
    padding: "0 18px"
  chip-choice-selected:
    backgroundColor: "{colors.pen-tonal}"
    textColor: "{colors.pen-tonal-ink}"
    typography: "{typography.label-chip}"
    rounded: "{rounded.pill}"
    height: "48px"
    padding: "0 18px"
  chip-choice-ground:
    backgroundColor: "transparent"
    textColor: "{colors.pen-on-dark-ink}"
    rounded: "{rounded.pill}"
    height: "48px"
  chip-choice-ground-selected:
    backgroundColor: "{colors.nav-indicator}"
    textColor: "{colors.nav-indicator-ink}"
    rounded: "{rounded.pill}"
    height: "48px"
  chip-suggestion-best:
    backgroundColor: "{colors.pen-tonal}"
    textColor: "{colors.pen-tonal-ink}"
    typography: "{typography.label-chip}"
    rounded: "{rounded.pill}"
    height: "48px"
    padding: "0 16px"
  chip-suggestion:
    backgroundColor: "transparent"
    textColor: "{colors.pen-on-dark-ink}"
    typography: "{typography.label-chip}"
    rounded: "{rounded.pill}"
    height: "48px"
    padding: "0 16px"
  field-quick-add:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    typography: "{typography.input}"
    rounded: "{rounded.field-pill}"
    height: "54px"
    padding: "0 18px"
  field-form-paper:
    backgroundColor: "{colors.sticker}"
    textColor: "{colors.ink}"
    typography: "{typography.input}"
    rounded: "{rounded.paper}"
    height: "48px"
    padding: "0 14px"
  field-form-ground:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    typography: "{typography.input}"
    rounded: "{rounded.paper}"
    height: "48px"
    padding: "0 14px"
  ticket:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.paper}"
  ticket-band:
    backgroundColor: "{colors.print-red}"
    textColor: "{colors.on-ground}"
    typography: "{typography.display-double}"
    height: "52px"
    padding: "0 14px"
  ticket-subhead:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    typography: "{typography.title}"
    height: "52px"
    padding: "0 14px"
  ticket-add-tab:
    backgroundColor: "{colors.pen-tonal}"
    textColor: "{colors.pen-tonal-ink}"
    typography: "{typography.label-chip}"
    height: "50px"
    padding: "0 14px"
  entry-row:
    backgroundColor: "transparent"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    height: "56px"
    padding: "11px 14px"
  entry-row-fresh:
    backgroundColor: "{colors.paper-fresh}"
    textColor: "{colors.ink}"
  approx-tag:
    backgroundColor: "{colors.print-red}"
    textColor: "{colors.on-ground}"
    typography: "{typography.label-tag}"
    rounded: "{rounded.tag}"
    padding: "1px 6px"
  source-box:
    backgroundColor: "transparent"
    textColor: "{colors.ink-soft}"
    typography: "{typography.label-tag}"
    rounded: "{rounded.tag}"
    size: "22px"
  slip:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    rounded: "{rounded.paper}"
    padding: "12px 16px"
  sticker-card:
    backgroundColor: "{colors.sticker}"
    textColor: "{colors.ink}"
    rounded: "{rounded.sticker}"
    padding: "8px 10px 9px"
  bottom-sheet:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    rounded: "{rounded.sheet}"
    padding: "12px 20px 24px"
  snackbar:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    rounded: "{rounded.paper}"
    height: "48px"
    padding: "0 4px 0 16px"
  bottom-tray:
    backgroundColor: "{colors.tray}"
    textColor: "{colors.on-ground}"
    padding: "12px 14px 8px"
  nav-bar:
    backgroundColor: "{colors.tray}"
    textColor: "{colors.on-ground-soft}"
    typography: "{typography.label-nav}"
    height: "80px"
  nav-indicator:
    backgroundColor: "{colors.nav-indicator}"
    textColor: "{colors.nav-indicator-ink}"
    rounded: "{rounded.sheet}"
    width: "64px"
    height: "32px"
  top-app-bar:
    backgroundColor: "{colors.ground}"
    textColor: "{colors.on-ground}"
    typography: "{typography.title}"
    height: "48px"
    padding: "34px 4px 6px"
---

# Design System: Breakfast

## Overview

**Creative North Star: "The Shop Counter at Night"**

The screen is a graphite counter after hours. Everything that is recorded fact is printed on paper in black and red ink. Each meal is a torn-off two-colour thermal ticket, and the day total is a plain paper slip. The only other material is pen blue, which marks the things you can act on. A reader should be able to tell what is printed record and what is a control without reading a single label: paper and ink are facts, blue is your pen.

The world is dense and arithmetic. One monospaced face carries everything, and figures line up in tabular columns, so each ticket row reads like a till line: name, a qty × rate line, the kcal, and a boxed source letter. Hierarchy comes from distinct physical pieces, not from size alone: the red band names a ticket, dashed rules divide rows, stickers float pending data above the tray, and pen-blue is spent on at most one solid element per screen.

The system targets Jetpack Compose and Material 3. Structure is stock M3 (small top app bar, navigation bar with two destinations, modal bottom sheet, snackbar, filter and suggestion chips, text fields). The identity lives in colour roles, the single typeface, and the paper and ticket shapes. The user explicitly rejected screen-like, LCD or glowing elements, teal accents, and the generic AI-minimal look of white cards with one brand accent. The world is dark-only at present; a light theme is an open gap (see Do's and Don'ts).

**Key Characteristics:**
- Graphite ground, paper records, black and red ink, one pen blue.
- Noto Sans Mono only, with double-width bold for short titles.
- Tickets with a red identity band, dashed row rules and a sawtooth torn edge.
- At most one solid pen-blue element per screen.
- Flat paper on a flat counter; only pending stickers and the snackbar lift.
- Every figure reconciles: shown grams × shown rate = shown kcal.

## Colors

Two inks on paper over a dark counter, with a blue pen for actions and nothing else.

### Primary
- **Pen Blue** (`pen-blue`): the single solid primary action on a screen: Log in the quick-add field, Save, Add to Lunch, Edit amount, Log to Lunch, Search Open Food Facts on its results screen. Text on it is `on-pen` white. Also the 2px border of a selected choice chip and the 2px focus ring of a field on paper (rings and borders are not fills). M3: `primary` / `onPrimary`.
- **Pen Tonal** (`pen-tonal` with `pen-tonal-ink`): secondary actions on paper or ground: the Add food tab, the per-row repeat `+`, the best-guess suggestion chip, Replace, Manual barcode, the Correction strip chips, selected choice-chip fill. `pen-tonal-ink` alone is the text-button colour on paper (Undo). M3: `secondaryContainer` / `onSecondaryContainer`.
- **Pen on Dark** (`pen-on-dark` line, `pen-on-dark-ink` text): secondary actions drawn directly on the ground or tray: outlined buttons, outlined suggestion chips, the Scan button, text buttons (Save, Edit, Today, Open in Library, Skip), and the quick-add field's focus ring. M3: `outline` for the stroke, a custom `onGroundAction` for the text.
- **Nav Indicator** (`nav-indicator` with `nav-indicator-ink`): the active destination pill in the navigation bar, and the selected state of a choice chip that sits on the ground. M3: `NavigationBarItemDefaults.colors(indicatorColor, selectedIconColor)`.

### Secondary
- **Print Red** (`print-red`): printed emphasis. Ticket header bands, the ≈ approximate tag, the recognised meal token inside the quick-add text, pending `+374` figures, the Required validation tag and its 2px field ring, the offline icon, and the Delete text button. M3: `tertiary` for bands and tags, `error` for validation and destructive, both at this value.
- **Red Band Soft Ink** (`on-red-soft`): the time inside a red band. Band names and totals use `on-ground`.

### Neutral
- **Counter Graphite** (`ground`): the screen background. M3: `background` and `surface`.
- **Tray Graphite** (`tray`): the fixed bottom area holding the quick-add or search pill and the navigation bar. M3: `surfaceContainer` (NavigationBar container).
- **Chalk** (`on-ground`): text and icons on ground and tray, and text on red bands and tags. M3: `onBackground` / `onSurface` / `onTertiary`.
- **Chalk Soft** (`on-ground-soft`): secondary text on ground (the date under the day title, field labels on ground, inactive nav). M3: `onSurfaceVariant`.
- **Receipt Paper** (`paper`): tickets, slips, sheets, the snackbar, quick-add and ground form fields, the Delete slip. M3: `inverseSurface` (the M3 snackbar already uses it).
- **Sticker Paper** (`sticker`): pending stickers (unsaved quick-add results, the Resolve card) and form fields sitting on paper.
- **Fresh Paper** (`paper-fresh`): background of an entry row that was just logged, and of the Correction strip beneath it. It marks recency on paper without introducing a hue.
- **Ink** (`ink`): text on paper. M3: `inverseOnSurface`.
- **Ink Soft** (`ink-soft`): secondary text on paper: qty × rate lines, captions, counts, the source letter, field labels on paper.
- **Dashed Rule** (`rule`): 1.5px dashed separators on paper, unselected choice-chip borders on paper, sticker field boxes, borders of fields on paper. M3: `outlineVariant`.
- **Box Grey** (`box`): the source-letter box border, the sheet drag handle, disabled controls.
- **Scrim** (`scrim`): behind modal bottom sheets. M3: `scrim` at 62%.

### Named Rules
**The Blue Means Tap Rule.** Blue marks what you can act on; paper, ink and red mark printed fact. Never use any blue for static content. Red is never a fill on a tappable element; its one interactive use is the red text and trash icon of Delete, inside a paper slip.

**The One Pen Rule.** At most one element per screen is filled solid pen blue. Everything else that is actionable is tonal, outlined or text. A screen may have zero solid blue (Daily with an empty field, Resolve, Edit entry).

**The No Glow Rule.** No other hues, no gradients, no glows, no screen or LCD styling, no pure white surfaces, no cream. White exists only as text on pen blue.

## Typography

**Display Font:** Noto Sans Mono (with monospace fallback), rendered double-width
**Body Font:** Noto Sans Mono
**Label/Mono Font:** Noto Sans Mono

**Character:** One monospaced face in weights 400, 500 and 700, chosen because it covers Macedonian Cyrillic and prints like a till. Double-width bold gives titles the stretched look of a thermal printer header without a second family.

All sizes below are sp in Compose (the 390-wide artboards are drawn at 1px = 1dp). Tabular figures (`fontFeatureSettings = "tnum"`) are on wherever numbers appear.

### Hierarchy
- **Display, double-width** (700, 17sp drawn at 2× horizontal scale, 24sp line): day names on Daily, meal names in ticket bands, the Library title. Only words of 9 characters or fewer. In Compose: `Modifier.graphicsLayer(scaleX = 2f, transformOrigin = TransformOrigin(0f, 0.5f))` inside a box sized to the doubled width.
- **Figure, day total** (700, 26sp, 30sp line): the kcal total on the day slip, with the ` kcal` unit at 15sp 500 in ink-soft.
- **Title Large** (700, 20sp, 26sp line): band totals, sheet titles, the unresolved food name on the Resolve sticker, the quick-add pending total.
- **Figure, row** (700, 18sp): the kcal of each entry row and the entry total on Entry detail.
- **Title** (700, 17sp, 24sp line): sub-screen titles in the top app bar, subheads inside paper, food names in identity bands longer than 9 characters.
- **Body** (400, 17sp, 24sp line): food names, row content, detail values. Long names wrap anywhere; they never truncate.
- **Input** (400, 16sp, 24sp line): text inside fields.
- **Button Label** (700, 16sp): full-width and inline buttons.
- **Chip Label** (400 or 700 when selected or best guess, 15sp, 20sp line): chips, the Add food tab, slip macros, the day subtitle.
- **Secondary** (400, 14sp, 20sp line): qty × rate lines, field labels, counts, band times.
- **Nav Label** (400, 700 when active, 14sp, 18sp line).
- **Tag** (400, 13sp, 18sp line): the ≈ tag, the source letter, sticker field boxes.

### Named Rules
**The One Face Rule.** Noto Sans Mono is the only typeface. Weight, double-width and size make the hierarchy; no second family.

**The Double-Width Rule.** Double-width is for words of 9 characters or fewer (meal names, day names, Library). Longer titles use bold 17sp single-width. Specified but not drawn (unverified): at system font scale 1.3 or more, double-width falls back to single-width bold.

**The Printed Fact Rule.** Sentence case everywhere. No uppercase labels, no letter-spaced overlines, no middle-dot meta strings. Decimals use dots, and figures are whole numbers except real decimals (2.8%, 0.2 g). Shown grams × shown rate must equal the shown kcal after rounding; liquids read in ml ("330 ml × 0.19").

## Layout

A single 390dp phone column on the graphite ground. Content sits at a 14dp side margin. Tickets stack 16dp apart. The day slip sits 6dp under the header.

- **Header:** 34dp top inset, 4dp side inset, a three-column grid of 48dp / flexible / 48dp. Daily centres the day title between previous and next chevrons; the past-date view adds a right-aligned Today text button in its own row below.
- **Rows:** 11dp vertical and 14dp horizontal padding inside paper, minimum 56dp for tappable entry rows, 44dp for read-only detail rows, 52dp for bands and subheads.
- **Bottom tray:** fixed to the bottom on Daily and Library only: the field pill row (12dp top, 14dp sides, 8dp bottom), then the 80dp navigation bar. Sub-screens have no tray and no nav.
- **Quick-add open:** pending stickers in a row, then a horizontally scrolling suggestion-chip row, then the field, all above the keyboard. Chip rows bleed to the screen edge with 12dp start padding.
- **Sheets:** 20dp side padding, 12dp top, 24dp bottom.
- **Choice of four:** the meal choice is a 2×2 grid of half-width chips with an 8dp gap, never a scrolling row.
- **Follow-up actions:** stacked full-width 48dp buttons with 10 to 12dp gaps; the stronger style leads.
- **Touch targets:** 48dp minimum everywhere (repeat buttons, chips, icon buttons, text buttons).

Specified but not drawn (unverified): at large font scale a ticket band's time moves onto a second line under the meal name.

## Elevation & Depth

The system is flat. Depth is told by material: light paper on a dark counter, a darker tray under the scroll, and a scrim behind sheets. Tonal contrast does the work that shadows do elsewhere.

### Shadow Vocabulary
- **Sticker lift** (`box-shadow: 0 1px 2px rgba(0,0,0,0.35)`): pending stickers (unsaved quick-add results, the Resolve card) and the snackbar. In Compose, roughly `shadowElevation = 1.dp`.
- **Focus rings** (`0 0 0 2px`): `pen-on-dark` around a field on the ground or tray; `pen-blue` around a field on paper; `print-red` around an invalid field. These are rings, not elevation.

### Named Rules
**The Paper on the Counter Rule.** Tickets, slips and sheets sit flat with no shadow. Only something not yet committed (a sticker) or transient (the snackbar) lifts, and only by 1dp.

## Shapes

Small, practical corners on paper and full pills for anything you press.

- **Tickets** have a 6dp top radius, square sides and a sawtooth torn bottom edge: 6dp tall, 8dp teeth, filled with the colour of the last strip (`pen-tonal` under an Add food tab, `paper` otherwise). In Compose, draw it as a `Path` below the ticket rather than as an image.
- **Slips, form fields, the snackbar and the Delete slip** use 6dp on all corners.
- **Stickers** use 8dp.
- **Bottom sheets** use a 16dp top radius with a 32×4dp drag handle at 2dp radius.
- **Buttons and chips** are 48dp tall with a 24dp radius (full pill). The quick-add and search pills are 54dp tall at 27dp radius; the expanded multi-line quick-add uses 26dp.
- **Tags and the source box** use 3dp: the ≈ tag, Required tag, the 22dp source-letter box, and sticker field boxes.
- **Strokes:** 1.5dp for dashed rules, outlines and boxes; 2dp for selected chips and focus rings.
- **Icons:** inline stroke icons, 24dp at 2dp stroke with round caps and joins (20dp on tabs and chips, 1.8dp stroke in the nav). Back is always the `arrow_back` arrow, never a chevron.

### Named Rules
**The Torn Edge Rule.** A ticket ends in a sawtooth edge, never a rounded bottom. A slip has no band and no torn edge.

## Components

### Buttons
Pills that read like pen marks on paper, each style tied to how much the action matters.
- **Shape:** full pill (24dp radius), 48dp tall. Maps to M3 `Button`, `FilledTonalButton`, `OutlinedButton` and `TextButton`.
- **Primary:** `pen-blue` fill, white bold label, at most one per screen.
- **Tonal:** `pen-tonal` fill, `pen-tonal-ink` bold label. Allowed on paper or ground.
- **Outlined (ground):** transparent, 1.5dp `pen-on-dark` stroke, `pen-on-dark-ink` label.
- **Text:** `pen-tonal-ink` bold on paper (Undo), `pen-on-dark-ink` bold on ground (Save, Edit, Today, Open in Library).
- **Disabled:** no fill, `box` grey label or icon (the quick-add Log icon when the field is empty).
- **Destructive:** a text button with `print-red` label and trash icon, always inside an inset paper slip (14dp margins, 6dp radius). Never filled, never full-bleed.
- **Order:** with two follow-ups, the stronger style leads (tonal Search Open Food Facts, then outlined Create personal food). Confirm actions have no check icon.
- **Pressed / focus:** focus-visible is a 2dp `pen-on-dark` outline offset 2dp. Pressed uses the M3 state layer of the content colour (not drawn on the artboards).

### Chips
- **Choice chips** (unit, meal, filter; M3 `FilterChip`): unselected on paper is transparent with a 1.5dp `rule` stroke and `pen-tonal-ink` text; selected is `pen-tonal` fill, 2dp `pen-blue` stroke, bold `pen-tonal-ink` text and a leading 16dp check. On the ground, unselected uses a `pen-on-dark` stroke with `pen-on-dark-ink` text; selected uses a `nav-indicator` fill with `nav-indicator-ink` text and the check.
- **Suggestion chips** (quick-add matches; M3 `SuggestionChip`): the best guess is tonal with a bold name; the others are outlined in `pen-on-dark`. Single line, horizontally scrolling row.
- **Correction chips:** tonal, in a scrolling strip on `paper-fresh` under the just-logged row; the last chip is Search with a leading search icon. The strip has no progress bar; it disappears after its window.

### Cards / Containers
- **Ticket** (signature): paper, 6dp top radius, `overflow` clipped, red band, rows, optional Add food tab, torn edge.
- **Slip:** paper, 6dp radius, 12×16dp padding, no band. For read-only summaries (day total, entry total), with a dashed rule above the figure row.
- **Sticker:** `sticker` paper, 8dp radius, sticker lift. Bold name and value at 15sp, then boxed fields (1.5dp `rule` border, 3dp radius, 13sp). Holds pending, unsaved data.
- **Bottom sheet:** M3 `ModalBottomSheet` in paper, 16dp top radius, `box` drag handle, `scrim` behind with the dimmed screen still drawn.
- **Snackbar:** paper, 6dp radius, ink text at 15sp, a `pen-tonal-ink` text action, sitting above the bottom tray.

### Inputs / Fields
- **Quick-add pill:** paper fill, 54dp tall, 27dp radius, 16sp input, a trailing 46dp round Log button (solid pen blue when there is text, `box` grey icon when empty). Grows to two lines when open. Recognised meal words render as a `print-red` token; matched quantities bold, matched foods underlined at 1.5dp.
- **Search pill (Library):** same pill with a leading search icon in `ink-soft`, and a 48dp outlined Scan button beside it.
- **Form field on paper:** `sticker` fill, 1.5dp `rule` border, 6dp radius, label in `ink-soft` 14sp above.
- **Form field on ground:** `paper` fill, 6dp radius, label in `on-ground-soft` 14sp above.
- **Focus:** 2dp ring, `pen-on-dark` on ground or tray, `pen-blue` on paper.
- **Error:** 2dp `print-red` ring plus a red tag in the label row (for example "Required: kcal").

### Navigation
- **Navigation bar** (M3 `NavigationBar`): 80dp, on `tray`, exactly two destinations, Daily and Library, only on those two screens. Active: 64×32dp `nav-indicator` pill at 16dp radius, `nav-indicator-ink` icon, bold `on-ground` label. Inactive: `on-ground-soft` icon and label, no pill.
- **Top app bar** (M3 small `TopAppBar`) on sub-screens: 48dp `arrow_back` icon button, start-aligned bold 17sp title (or double-width if 9 characters or fewer), trailing text or icon actions. Daily uses a centred day title between previous and next chevrons.

### Ticket Band and Rows (signature)
- **Red band:** 52dp minimum, 14dp padding, `print-red`. Holds the double-width name, the time in `on-red-soft` 14sp, and the bold 20sp total on the right. Only meal tickets and a food's single identity band get one.
- **Subhead:** every other heading inside paper (Per 100 ml, Servings, Aliases, By last logged, 5 results, Local matches): bold 17sp ink, single-width, count on the right in `ink-soft` 14sp, 52dp tall, dashed rule below.
- **Entry row** (a button, M3 `ListItem` equivalent): grid of name and qty line / bold 18sp kcal / 26dp column with the source box. 56dp minimum. Rows after the first get a 1.5dp dashed `rule` on top.
- **Source box:** 22dp square, 1.5dp `box` border, 3dp radius, 13sp `ink-soft` letter: B bundled, P personal, O Open Food Facts.
- **≈ tag:** one form only. `print-red` chip wrapping the approximate quantity (`≈ 1 tbsp`), 13sp, 1×6dp padding, 3dp radius, no wrap; the rest of the line follows as plain text (`(16 g) × 5.88`). Used in rows, stickers, fields, servings and result lines.
- **Add food tab:** full-width `pen-tonal` strip, 50dp, 20dp stroke `+` in `pen-tonal-ink`, bold 15sp label. No filled disc.
- **Repeat button (Library rows):** 48dp round tonal button with a 22dp `+` (× when open). Never solid blue per row.

## Do's and Don'ts

### Do:
- **Do** spend solid `pen-blue` on at most one element per screen, and make it the action that commits.
- **Do** put every recorded fact on paper in `ink`, and every heading other than a meal ticket or a food's identity band in a single-width subhead.
- **Do** use one ≈ tag form everywhere approximation appears, and make shown grams × rate equal shown kcal.
- **Do** keep 48dp targets, AA contrast, `arrow_back` for back, and an accessibility label on every icon-only button.
- **Do** let long English and Macedonian (Latin and Cyrillic) names wrap anywhere rather than truncate.
- **Do** use a 2dp `pen-blue` focus ring on fields that sit on paper; `pen-on-dark` fails contrast there.

### Don't:
- **Don't** add hues, gradients, glows, LCD or screen-like elements, teal, cream, or pure white surfaces.
- **Don't** make a red or ink element the primary button, or use red as a fill on anything tappable.
- **Don't** use double-width for words longer than 9 characters.
- **Don't** add red bands for section headings, or stack several red-banded tickets where one ticket with subheads will do.
- **Don't** use uppercase labels, letter-spaced overlines, eyebrows, or middle-dot meta strings.
- **Don't** write "estimate" beside the ≈ tag, and don't add a check icon to Confirm.
- **Don't** add a filter or sort control to Library or a progress bar to the Correction strip.
- **Don't** add shadows to tickets, slips or sheets.

### Open (not yet designed)
- **Light theme:** PRODUCT.md expects light and dark; only the dark counter exists. Not invented here.
- **Open Food Facts contribution review and sign-in screens:** not drawn.
- **Loading states:** not drawn.
- **Unverified rules:** the double-width fallback at font scale 1.3 or more and the band time wrapping at large font scale are specified but not drawn.
