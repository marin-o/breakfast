package com.iron.breakfast.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.GoogleFont.Provider
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.sp
import com.iron.breakfast.R

@OptIn(ExperimentalTextApi::class)
private val NotoSansMonoProvider = Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

@OptIn(ExperimentalTextApi::class)
private val NotoSansMono = FontFamily(
    Font(GoogleFont("Noto Sans Mono"), NotoSansMonoProvider, FontWeight.Normal),
    Font(GoogleFont("Noto Sans Mono"), NotoSansMonoProvider, FontWeight.Medium),
    Font(GoogleFont("Noto Sans Mono"), NotoSansMonoProvider, FontWeight.Bold),
)

private fun receiptStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(
    fontFamily = NotoSansMono,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    fontFeatureSettings = "tnum",
)

/** Material roles mapped to the locked Receipt v5 type scale. */
val ReceiptTypography = androidx.compose.material3.Typography(
    displayLarge = receiptStyle(26, 30, FontWeight.Bold),
    displayMedium = receiptStyle(20, 26, FontWeight.Bold),
    displaySmall = receiptStyle(18, 24, FontWeight.Bold),
    headlineLarge = receiptStyle(20, 26, FontWeight.Bold),
    headlineMedium = receiptStyle(17, 24, FontWeight.Bold),
    headlineSmall = receiptStyle(17, 24, FontWeight.Bold),
    titleLarge = receiptStyle(20, 26, FontWeight.Bold),
    titleMedium = receiptStyle(17, 24, FontWeight.Bold),
    titleSmall = receiptStyle(15, 20, FontWeight.Normal),
    bodyLarge = receiptStyle(17, 24),
    bodyMedium = receiptStyle(16, 24),
    bodySmall = receiptStyle(14, 20),
    labelLarge = receiptStyle(16, 24, FontWeight.Bold),
    labelMedium = receiptStyle(15, 20),
    labelSmall = receiptStyle(13, 18),
)

/** The stretched treatment is reserved for short meal/day names and Library. */
fun doubleWidth(style: TextStyle): TextStyle = style.copy(
    textGeometricTransform = TextGeometricTransform(scaleX = 2f),
)

/** Receipt headings switch to a single column at the accessibility font threshold. */
internal fun usesSingleWidthAt(fontScale: Float): Boolean = fontScale >= 1.3f
