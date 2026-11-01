package com.bosa.breakfast.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Monochrome tokens. Green (`ok`) and red (`bad`) are the only colours of actions; `protein`, `carbs` and `fat` mark
 * those macros and nothing else, on small marks only (bars and dots).
 */
data class BreakfastColors(
    val bg: Color, val ink: Color, val soft: Color, val line: Color,
    val ok: Color, val onOk: Color, val bad: Color, val scrim: Color,
    val cameraGround: Color, val cameraMark: Color,
    val protein: Color, val carbs: Color, val fat: Color,
)

/** The three macros that have a colour of their own. */
enum class Macro { PROTEIN, CARBS, FAT }

/** The one place a macro meets its colour. */
fun BreakfastColors.macroColor(macro: Macro): Color = when (macro) {
    Macro.PROTEIN -> protein
    Macro.CARBS -> carbs
    Macro.FAT -> fat
}

val LightBreakfastColors = BreakfastColors(
    bg = Color(0xFFD0D4D8), ink = Color(0xFF111214), soft = Color(0xFF56595E), line = Color(0xFFBBBFC2),
    ok = Color(0xFF13804F), onOk = Color(0xFFFFFFFF), bad = Color(0xFFA3291F), scrim = Color(0x52111214),
    cameraGround = Color(0xFF0B0C0E), cameraMark = Color(0xFFFFFFFF),
    protein = Color(0xFF2A63B8), carbs = Color(0xFFAB1F7F), fat = Color(0xFFB08600),
)
val DarkBreakfastColors = BreakfastColors(
    bg = Color(0xFF111214), ink = Color(0xFFF2F3F5), soft = Color(0xFF9A9DA4), line = Color(0xFF26282C),
    ok = Color(0xFF3DBE7E), onOk = Color(0xFF0B1F14), bad = Color(0xFFF0675B), scrim = Color(0x99000000),
    cameraGround = Color(0xFF0B0C0E), cameraMark = Color(0xFFFFFFFF),
    protein = Color(0xFF6AA5F0), carbs = Color(0xFFF36BC4), fat = Color(0xFFE3B52E),
)

/**
 * The text on a `bad` fill (the swipe Delete): white (`cameraMark`) in light mode, 7.26:1; `bg` in dark mode, 6.07:1,
 * where white on the lighter dark `bad` is only 3.09:1.
 */
internal fun swipeLabelOnBad(colors: BreakfastColors): Color = if (colors == DarkBreakfastColors) colors.bg else colors.cameraMark

val LocalBreakfastColors = staticCompositionLocalOf { LightBreakfastColors }
