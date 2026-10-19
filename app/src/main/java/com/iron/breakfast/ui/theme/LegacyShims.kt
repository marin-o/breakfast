package com.iron.breakfast.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// TEMPORARY (slice 1a only): maps the Receipt names still used by ReceiptComponents.kt onto the
// Monochrome tokens so the build stays green. Removed in slice 1b, when Components.kt replaces it.

typealias ReceiptColors = BreakfastColors
val LocalReceiptColors = LocalBreakfastColors
val ReceiptTypography = BreakfastTypography
val ReceiptShapes: Shapes = BreakfastShapes
val BreakfastColors.paper: Color get() = bg
val BreakfastColors.red: Color get() = bad
val BreakfastColors.redFill: Color get() = bad
val BreakfastColors.onRedFill: Color get() = bg
val BreakfastColors.pendingOnInk: Color get() = bad
val ReceiptPaperShape = RoundedCornerShape(0.dp)
val ReceiptPillShape = RoundedCornerShape(0.dp)
val ReceiptFieldPillShape = RoundedCornerShape(0.dp)
val ReceiptStickerShape = RoundedCornerShape(0.dp)
val CameraMarkColor: Color = Color(0xFFFFFFFF)
val TicketBandHeight = 52.dp
val TicketAddHeight = 50.dp
val QuickAddHeight = 54.dp
