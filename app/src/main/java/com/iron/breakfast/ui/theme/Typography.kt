package com.iron.breakfast.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.iron.breakfast.R

private val IosevkaCharonMono = FontFamily(
    Font(R.font.iosevka_charon_mono_medium, weight = FontWeight(500)),
    Font(R.font.iosevka_charon_mono_bold, weight = FontWeight(700)),
)
private fun receiptStyle(size: Int, lineHeight: Int, weight: FontWeight = FontWeight(500)) = TextStyle(
    fontFamily = IosevkaCharonMono, fontSize = size.sp, lineHeight = lineHeight.sp,
    fontWeight = weight, fontFeatureSettings = "tnum",
)
val ReceiptTypography = androidx.compose.material3.Typography(
    displayLarge = receiptStyle(26, 32, FontWeight(700)), displayMedium = receiptStyle(24, 32, FontWeight(700)),
    displaySmall = receiptStyle(18, 24, FontWeight(700)), headlineLarge = receiptStyle(26, 32, FontWeight(700)),
    headlineMedium = receiptStyle(23, 30, FontWeight(700)), headlineSmall = receiptStyle(22, 28, FontWeight(700)),
    titleLarge = receiptStyle(23, 30, FontWeight(700)), titleMedium = receiptStyle(18, 24, FontWeight(700)),
    titleSmall = receiptStyle(14, 20), bodyLarge = receiptStyle(18, 24), bodyMedium = receiptStyle(18, 24),
    bodySmall = receiptStyle(14, 20), labelLarge = receiptStyle(18, 24, FontWeight(700)),
    labelMedium = receiptStyle(18, 24), labelSmall = receiptStyle(13, 18),
)
