package com.iron.breakfast.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class ReceiptColors(
    val paper: Color, val ink: Color, val soft: Color, val red: Color,
    val redFill: Color, val onRedFill: Color, val pendingOnInk: Color,
    val scrim: Color, val cameraGround: Color,
)

val LightReceiptColors = ReceiptColors(
    Color(0xFFEEEFEC), Color(0xFF2E3036), Color(0xFF5E616A), Color(0xFFB8263A),
    Color(0xFFB8263A), Color(0xFFEEEFEC), Color(0xFFF2A79B), Color(0x8C2E3036), Color(0xFF141218),
)
val DarkReceiptColors = ReceiptColors(
    Color(0xFF232428), Color(0xFFE2E3DE), Color(0xFF9C9FA8), Color(0xFFEE7280),
    Color(0xFFB8263A), Color(0xFFF0F0EC), Color(0xFFB8263A), Color(0x99000000), Color(0xFF141218),
)

val LocalReceiptColors = staticCompositionLocalOf { LightReceiptColors }

/** Camera registration marks stay light against the fixed camera ground in both themes. */
val CameraMarkColor = Color(0xFFE2E3DE)
