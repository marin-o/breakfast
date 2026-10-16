package com.iron.breakfast.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val ReceiptLightScheme = lightColorScheme(
    primary = LightReceiptColors.ink, onPrimary = LightReceiptColors.paper,
    primaryContainer = LightReceiptColors.ink, onPrimaryContainer = LightReceiptColors.paper,
    secondary = LightReceiptColors.soft, onSecondary = LightReceiptColors.paper,
    secondaryContainer = LightReceiptColors.paper, onSecondaryContainer = LightReceiptColors.ink,
    tertiary = LightReceiptColors.red, onTertiary = LightReceiptColors.paper,
    background = LightReceiptColors.paper, onBackground = LightReceiptColors.ink,
    surface = LightReceiptColors.paper, onSurface = LightReceiptColors.ink,
    surfaceContainer = LightReceiptColors.paper, surfaceVariant = LightReceiptColors.paper,
    onSurfaceVariant = LightReceiptColors.soft, inverseSurface = LightReceiptColors.ink,
    inverseOnSurface = LightReceiptColors.paper, error = LightReceiptColors.red,
    onError = LightReceiptColors.paper, outline = LightReceiptColors.ink,
    outlineVariant = LightReceiptColors.soft, scrim = LightReceiptColors.scrim,
)
private val ReceiptDarkScheme = darkColorScheme(
    primary = DarkReceiptColors.ink, onPrimary = DarkReceiptColors.paper,
    primaryContainer = DarkReceiptColors.ink, onPrimaryContainer = DarkReceiptColors.paper,
    secondary = DarkReceiptColors.soft, onSecondary = DarkReceiptColors.paper,
    secondaryContainer = DarkReceiptColors.paper, onSecondaryContainer = DarkReceiptColors.ink,
    tertiary = DarkReceiptColors.red, onTertiary = DarkReceiptColors.paper,
    background = DarkReceiptColors.paper, onBackground = DarkReceiptColors.ink,
    surface = DarkReceiptColors.paper, onSurface = DarkReceiptColors.ink,
    surfaceContainer = DarkReceiptColors.paper, surfaceVariant = DarkReceiptColors.paper,
    onSurfaceVariant = DarkReceiptColors.soft, inverseSurface = DarkReceiptColors.ink,
    inverseOnSurface = DarkReceiptColors.paper, error = DarkReceiptColors.red,
    onError = DarkReceiptColors.paper, outline = DarkReceiptColors.ink,
    outlineVariant = DarkReceiptColors.soft, scrim = DarkReceiptColors.scrim,
)

@Composable
fun BreakfastTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalReceiptColors provides if (dark) DarkReceiptColors else LightReceiptColors) {
        MaterialTheme(
            colorScheme = if (dark) ReceiptDarkScheme else ReceiptLightScheme,
            typography = ReceiptTypography, shapes = ReceiptShapes, content = content,
        )
    }
}
