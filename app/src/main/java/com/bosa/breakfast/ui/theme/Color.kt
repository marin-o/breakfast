package com.bosa.breakfast.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Monochrome tokens. Green (`ok`) and red (`bad`) are the only semantic colours. */
data class BreakfastColors(
    val bg: Color, val ink: Color, val soft: Color, val line: Color,
    val ok: Color, val onOk: Color, val bad: Color, val scrim: Color,
    val cameraGround: Color, val cameraMark: Color,
)

val LightBreakfastColors = BreakfastColors(
    bg = Color(0xFFD0D4D8), ink = Color(0xFF111214), soft = Color(0xFF56595E), line = Color(0xFFBBBFC2),
    ok = Color(0xFF13804F), onOk = Color(0xFFFFFFFF), bad = Color(0xFFA3291F), scrim = Color(0x52111214),
    cameraGround = Color(0xFF0B0C0E), cameraMark = Color(0xFFFFFFFF),
)
val DarkBreakfastColors = BreakfastColors(
    bg = Color(0xFF111214), ink = Color(0xFFF2F3F5), soft = Color(0xFF9A9DA4), line = Color(0xFF26282C),
    ok = Color(0xFF3DBE7E), onOk = Color(0xFF0B1F14), bad = Color(0xFFF0675B), scrim = Color(0x99000000),
    cameraGround = Color(0xFF0B0C0E), cameraMark = Color(0xFFFFFFFF),
)

/**
 * The text on a `bad` fill (the swipe Delete): white (`cameraMark`) in light mode, 7.26:1; `bg` in dark mode, 6.07:1,
 * where white on the lighter dark `bad` is only 3.09:1.
 */
internal fun swipeLabelOnBad(colors: BreakfastColors): Color = if (colors == DarkBreakfastColors) colors.bg else colors.cameraMark

val LocalBreakfastColors = staticCompositionLocalOf { LightBreakfastColors }
