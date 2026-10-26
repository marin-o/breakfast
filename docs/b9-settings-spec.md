# B9 settings page: spec for owner approval

Status: **approved by the owner 2026-10-26** (structure, defaults and every string). Being built in B9.

## Rules this spec follows

- **Defaults keep today's behaviour.** Installing B9 changes nothing until a setting is changed.
- **Monochrome design is locked.** The page uses only components the app already has:
  - `TopBar` with the back arrow;
  - `SectionHeading` for group titles, as "Servings" in the food form;
  - a plain 64dp row: label on the left, control on the right;
  - the −/+ `AddCircle` stepper from "Day starts at";
  - `ChoiceOption` chips from "Long draft";
  - `Hairline` between rows;
  - the existing `AlertDialog` for confirmations.

  Anything else is flagged below as **needs design**.
- **Copy rules apply:** minimal labels, no helper or explanatory text, no middle dots, no uppercase, sentence case like the existing rows. Every string below is proposed and needs the owner's approval.
- **Storage:** plain `SharedPreferences` (`AppSettings`), as today. No new dependency, no Room schema change.
- **Entry point:** Library ⋮ → Settings (unchanged).

## Structure (owner: page per group)

Settings is a list of the six groups, in this order: Logging, Display, Accessibility, Scanner, Data, About.
- Each group is a row using the existing tap-row style (the same as "Export diary"). Tapping it opens that group's page.
- A group page has the `TopBar` with the group name and the back arrow. Back and the arrow return to the list.
- No new components. Tabs were considered and rejected: six tabs don't fit a phone width and the app has no tab strip besides the bottom bar.

## Group pages

Within each page, rows run top to bottom. `[A] [B]` are choice chips; the chip in **bold** is the default. `(−) x (+)` is the stepper.

### Logging

| # | Row label | Control | Options | Default |
|---|---|---|---|---|
| 1 | Day starts at | stepper | 00:00 … 23:00, wraps | **00:00** |
| 4 | Undo | chips | **15 s**, 5 s, 30 s, Until next action | 15 s |
| 5 | Meal | chips | **Ask**, By time, Last used | Ask |
| 6 | Breakfast until / Lunch until / Dinner until | three steppers, shown only when Meal is "By time" | whole hours; each must be later than the one above | 12:00 / 17:00 / 22:00 (owner); Snacks after |
| 7 | Count what I'm typing | chips | **On**, Off | On |
| 8 | Learn from my picks | chips | **On**, Off | On |

- **#5 "Last used":** the meal of the most recent Add. "Ask" is today's behaviour (the meal picker opens).
- **#7 Off:** a word still being typed is only counted and saved once it is picked or finished. This changes P9's rule when switched.
- **#8 Off:** learned preferences are ignored when ranking; stored preferences are kept, not deleted.

### Display

| # | Row label | Control | Options | Default |
|---|---|---|---|---|
| 9 | Theme | chips | **System**, Light, Dark | System |
| 2 | Long draft | chips | **End**, Start, Two lines | End |
| 10 | Macros on today | chips | **Show**, Hide | Show |
| 11 | Missing values note | chips | **Show**, Hide | Show |
| 12 | Decimal mark | chips | **Point**, Comma | Point |

- **#9 (owner: the launch window follows the setting):** with Light or Dark forced, the launch window uses that mode's `bg` as well. This uses the platform's native night-mode switch (`AppCompatDelegate`-free: `UiModeManager.setApplicationNightMode` on API 31+, and the activity's own theme overlay before `setContentView` below that). No new dependency. Verify on device with a cold start in each mode.
- **#10 Hide:** Daily shows only kcal (the protein/carbs/fat block and its "?" note are not drawn).
- **#12:** affects how numbers are *shown* only. Input keeps accepting both, per the one decimal rule.

### Accessibility

| # | Row label | Control | Options | Default |
|---|---|---|---|---|
| 13 | Motion | chips | **System**, Reduced | System |
| 14 | Text size | chips | **System**, Larger | System |
| 15 | Haptics | chips | On, **Off** | Off |

- **#14 Larger:** one step (×1.15) on top of the system font scale, inside the app only. The large-font layouts already in the app (`isLargeFont`) handle it.
- **#15:** new behaviour; a short tick on Add and Undo using the platform's standard haptic constants (no new permission).

### Scanner and Open Food Facts

| # | Row label | Control | Options | Default |
|---|---|---|---|---|
| 16 | Flashlight at start | chips | On, **Off** | Off |
| 17 | Look up unknown barcodes | chips | **On**, Off | On |
| 18 | Contribute | chips | **Show**, Hide | Show |

- **#17 Off:** a barcode not in the Library opens the manual product form directly; no network request is made.
- **#18 Hide:** "Contribute to Open Food Facts" disappears from food detail and the review sheet.

### Data

| # | Row label | Control | Options | Default |
|---|---|---|---|---|
| 3a | Export diary | action row | opens the save picker | — |
| 3b | Import diary | action row | opens the open picker | — |
| 19 | Export reminder | chips | **Never**, Weekly, Monthly | Never |
| 20 | Safety copy | chips | **Keep**, Don't keep | Keep |
| 21 | Delete all data | action row (destructive red text, as "Archive food") | confirm dialog | — |

- **#19:** when due, a one-line notice on Daily: proposed "Export your diary." with the action "Export". It shows once, and appears again after the next interval if no export was made. Owner to approve wording.
- **#20 Don't keep:** Import no longer writes `no_backup/diary-before-import.json`, and an existing copy is deleted.
- **#21 (owner: ask whether to delete settings and learned picks too):**
  - **Dialog:** no title; one line "This deletes all entries and foods.", then one `ChoiceOption` chip "Also settings and learned picks" (off by default, toggles), then [Cancel] [Delete all].
  - **Safety copy:** a safety copy is written first, as for Import, regardless of #20.
  - **What it deletes:** entries and personal foods (the built-in catalogue is re-seeded). With the chip on: also all settings (back to defaults) and learned preferences.

### About

| # | Row | Content |
|---|---|---|
| 22a | Version | "0.1 (10)": versionName (versionCode) |
| 22b | Licences | opens a plain text page with the bundled `OFL.txt` (owner: the convention, a plain text page), `TopBar` "Licences", scrolling `Body` text |

## Strings for approval

- **Group titles:** Logging, Display, Accessibility, Scanner, Data, About.
- **Row labels:** Undo, Meal, Breakfast until, Lunch until, Dinner until, Count what I'm typing, Learn from my picks, Theme, Macros on today, Missing values note, Decimal mark, Motion, Text size, Haptics, Flashlight at start, Look up unknown barcodes, Contribute, Export reminder, Safety copy, Delete all data, Version, Licences.
- **Chip labels:** 5 s, 15 s, 30 s, Until next action, Ask, By time, Last used, On, Off, System, Light, Dark, Show, Hide, Point, Comma, Reduced, Larger, Never, Weekly, Monthly, Keep, Don't keep.
- **Dialogs and notices:** "This deletes all entries and foods." / "Also settings and learned picks" / Delete all; "Export your diary." / Export.
- **Already approved:** Settings, Day starts at, Long draft, End, Start, Two lines, Export diary, Import diary, Cancel, Replace diary.

## Owner answers (2026-10-26)

1. **Group names and order:** approved.
2. **Meal-by-time boundaries:** breakfast until 12:00, lunch until 17:00, dinner until 22:00, then snacks.
3. **Theme cold start:** the launch window follows the setting.
4. **Delete all data:** ask whether to also delete settings and learned picks (the chip in the dialog).
5. **Licences:** a plain text page, the usual convention.
6. **Structure:** a page per group, as a list of groups.

## Approval

All strings above approved as written (2026-10-26).

## Implementation notes (for the batch)

- One small `AppSettings` field per setting with a typed getter; pure decision functions per setting, with JUnit tests in the existing style.
- Settings that change ranking or logging (#5–#8) go through the existing pure decisions (`logTargetMealOf`, `LogPlanner`, `FoodMatcher` preference input), so each is one switch with tests.
- Export does not include settings changes beyond the two it already writes; Import keeps ignoring them (owner decision, B6).
- Device checks: each setting toggled on the phone once, with recreation (dark-mode toggle) and a force-stop to confirm persistence.
