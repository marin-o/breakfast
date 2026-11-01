package com.bosa.breakfast.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList

/** Every Material role maps onto a Monochrome token so stock components never add another colour. */
private fun BreakfastColors.toMaterial(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = ink, onPrimary = bg, primaryContainer = bg, onPrimaryContainer = ink,
        secondary = soft, onSecondary = bg, secondaryContainer = bg, onSecondaryContainer = ink,
        tertiary = ok, onTertiary = onOk, tertiaryContainer = bg, onTertiaryContainer = ink,
        background = bg, onBackground = ink,
        surface = bg, onSurface = ink, surfaceVariant = bg, onSurfaceVariant = soft,
        surfaceTint = bg, inverseSurface = ink, inverseOnSurface = bg, inversePrimary = ink,
        error = bad, onError = bg, errorContainer = bg, onErrorContainer = bad,
        outline = line, outlineVariant = line, scrim = scrim,
        surfaceBright = bg, surfaceDim = bg, surfaceContainerLowest = bg, surfaceContainerLow = bg,
        surfaceContainer = bg, surfaceContainerHigh = bg, surfaceContainerHighest = bg,
    )
}

@Composable
fun BreakfastTheme(dark: Boolean = isSystemInDarkTheme(), macroPalette: MacroPalette = MacroPalette.STANDARD, content: @Composable () -> Unit) {
    val colors = breakfastColors(dark, macroPalette)
    CompositionLocalProvider(LocalBreakfastColors provides colors) {
        MaterialTheme(colorScheme = colors.toMaterial(dark), typography = BreakfastTypography, shapes = BreakfastShapes) {
            // Tabular numbers and the Macedonian shaping locale apply to every text that does not set its own.
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.merge(TextStyle(fontFamily = Geologica, fontFeatureSettings = "tnum", localeList = LocaleList("mk"))),
                content = content,
            )
        }
    }
}
