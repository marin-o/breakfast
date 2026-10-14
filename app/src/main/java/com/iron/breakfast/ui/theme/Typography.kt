package com.iron.breakfast.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
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

/** Longest heading that may be stretched to double width. */
internal const val DoubleWidthMaxChars = 9

/** System font scale at which stretched headings fall back to single-width bold. */
internal const val SingleWidthFontScale = 1.3f

/** Receipt headings switch to a single column at the accessibility font threshold. */
internal fun usesSingleWidthAt(fontScale: Float): Boolean = fontScale >= SingleWidthFontScale

/** True when [text] may be drawn double-width: short enough, and the font scale is below the fallback threshold. */
internal fun useDoubleWidth(fontScale: Float, text: String): Boolean =
    text.length <= DoubleWidthMaxChars && !usesSingleWidthAt(fontScale)

/** The one place the stretched treatment is applied; call sites never branch on font scale themselves. */
fun doubleWidth(style: TextStyle): TextStyle = style.copy(
    textGeometricTransform = TextGeometricTransform(scaleX = 2f),
)

/** Heading style for meal names, day names and Library: double-width when allowed, else the plain bold [base]. */
@Composable
fun receiptHeadingStyle(text: String, base: TextStyle = MaterialTheme.typography.headlineMedium): TextStyle =
    if (useDoubleWidth(LocalDensity.current.fontScale, text)) doubleWidth(base) else base
