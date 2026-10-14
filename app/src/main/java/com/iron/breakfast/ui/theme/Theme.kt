package com.iron.breakfast.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ReceiptDarkColors = darkColorScheme(
    primary = PenBlue,
    onPrimary = OnPen,
    primaryContainer = PenTonal,
    onPrimaryContainer = PenTonalInk,
    secondary = PenOnDark,
    onSecondary = Ground,
    secondaryContainer = PenTonal,
    onSecondaryContainer = PenTonalInk,
    tertiary = PrintRed,
    onTertiary = OnGround,
    background = Ground,
    onBackground = OnGround,
    surface = Ground,
    onSurface = OnGround,
    surfaceContainer = Tray,
    surfaceVariant = Tray,
    onSurfaceVariant = OnGroundSoft,
    inverseSurface = Paper,
    inverseOnSurface = Ink,
    error = PrintRed,
    onError = OnGround,
    outline = PenOnDark,
    outlineVariant = Rule,
    scrim = Scrim,
)

@Composable
fun BreakfastTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ReceiptDarkColors,
        typography = ReceiptTypography,
        shapes = ReceiptShapes,
        content = content,
    )
}
