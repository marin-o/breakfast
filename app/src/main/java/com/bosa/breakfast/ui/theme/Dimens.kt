package com.bosa.breakfast.ui.theme

import androidx.compose.ui.unit.dp

/** Monochrome measurements; design px map directly to dp. */
val Gutter = 24.dp
val HeaderHeight = 52.dp
/** How far what belongs to a row sits in from it: an unmatched food's results. */
val PartInset = 16.dp
/** A snackbar and the bottom area from the screen edge. */
val SnackbarGutter = 16.dp
/**
 * The 44dp box of icon buttons and the add circle. Every control smaller than 48dp still has a 48dp touch area:
 * Compose's minimum touch target extends hit testing and the TalkBack bounds without changing layout (DESIGN.md).
 */
val IconTarget = 44.dp
val FieldMinHeight = 52.dp
val PrimaryHeight = 52.dp
val SecondaryHeight = 48.dp
val TertiaryHeight = 44.dp
val ChoiceMinHeight = 44.dp
val AddCircleSize = 32.dp
/** How far the drawn add circle sits in from the end of its touch target. */
val AddCircleInset = (IconTarget - AddCircleSize) / 2
val ComposerHeight = 52.dp
val CurveHeight = 200.dp
