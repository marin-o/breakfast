---
name: Breakfast
description: A thermal till receipt printed in paper, ink, and a second red ink.
colors:
  paper: "#EEEFEC"
  ink: "#2E3036"
  soft: "#5E616A"
  red: "#B8263A"
  red-fill: "#B8263A"
  on-red-fill: "#EEEFEC"
  pending-on-ink: "#F2A79B"
  scrim: "rgba(46,48,54,0.55)"
  camera-ground: "#141218"
dark-colors:
  paper: "#232428"
  ink: "#E2E3DE"
  soft: "#9C9FA8"
  red: "#EE7280"
  red-fill: "#B8263A"
  on-red-fill: "#F0F0EC"
  pending-on-ink: "#B8263A"
  scrim: "rgba(0,0,0,0.6)"
  camera-ground: "#141218"
typography:
  family: "Iosevka Charon Mono"
  weights: [500, 700]
  root: "18sp/24sp 500"
  page-title: "26sp/32sp 700"
  meal-heading: "23sp/30sp 700 red"
  total: "24sp/32sp 700"
  entry-title: "22sp/28sp 700"
  meal-time: "14sp/20sp soft"
shapes:
  corners: "0dp"
  minimum-target: "48dp"
  field-height: "52dp"
---

# Design System: Receipt v6.5

Receipt is a thermal till receipt. The screen is full-bleed paper with printed ink and thermal red as the second ink. Hierarchy comes from bold type, larger type, inverse print, character rules, columns, and spacing. Light is the primary theme; dark is the same receipt printed in negative and follows the dark token table above.

## Rules

- Use Iosevka Charon Mono at weights 500 and 700 only. Weight 500 is the normal body weight; tabular numbers are enabled.
- Keep every corner square. Touch targets are at least 48dp and fields are at least 52dp.
- Rules are repeated `-` and `=` characters, clipped to width. Dashes use `soft`; equals use `ink`.
- Item rows use name, bold kcal in a 5-character column, and a soft source letter in a 2-character column. Quantity lines are soft and indented two characters; use `x`.
- Approximate quantities use inverse red print, for example `≈1 tbsp`.
- Inverse blocks (TOTAL, primary actions, active tabs, and snackbars) use ink fill with paper text in light mode and light ink fill with dark text in dark mode.
- Icons are stroke icons with square caps. There are no spinners, shadows, pills, tonal fills, blue, torn edges, stretched type, or CSS-style rules.
- The camera ground is `#141218` in both themes and its viewfinder marks stay light.

## Controls

Primary is inverse print and appears at most once per screen. Secondary controls have a 2dp ink border and no fill. Selected choices and best guesses use two 1dp ink lines with a 1dp gap. Tertiary actions are underlined ink text. Destructive actions use a 2dp red border and red text after a dashed rule.

Receipt fields have a 2dp ink border, square corners, and 52dp minimum height. Focus is 3dp ink; errors are 3dp red with red text below. The quick-add field starts with a bold `>` prompt and divides the `↵` log block with a 2dp line.

## Shared components

The single implementation lives in `ui/components/ReceiptComponents.kt`: PrimaryButton, SecondaryButton, ChoiceBox, TertiaryLink, DestructiveButton, ReceiptField, CharacterRule, ItemRow, ApproxTag, TotalBlock, ReceiptSheet, ReceiptSnackbar, PrintedProgress, tray/navigation, and square-cap icons. Existing `Receipt*` entry points are compatibility names while screens migrate to the foundation.

## Layout and behavior

Daily and Library alone have the paper tray, an `=` rule, their field, and DAILY/LIBRARY tabs. Sub-screens use a back arrow and bold title without navigation. Preserve all v5 behavior deltas and product rules; v6.5 changes the visual grammar only. Macedonian food names use the font's `mk` locale forms where spans set a locale.

The locked references are in `design/canvas/project/R6-*.dc.html` and `R6D-*.dc.html`; the binding token and control details are in `design/canvas/BRIEF-v65-system.md`. When a state cannot be built as drawn, record the concrete reason in `docs/receipt-design-gaps.md`.
