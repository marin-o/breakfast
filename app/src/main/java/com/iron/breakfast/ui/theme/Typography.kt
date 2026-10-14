package com.iron.breakfast.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.sp
import com.iron.breakfast.R

/**
 * Bundled (not downloadable) so the one face is always present offline; the Google Fonts provider
 * silently fell back to the system sans on device. A Latin + Cyrillic subset of the variable font,
 * which keeps the Macedonian `locl` forms and `tnum`. Licence: assets/licenses/NotoSansMono-OFL.txt.
 */
@OptIn(ExperimentalTextApi::class)
private fun notoSansMono(weight: FontWeight) = Font(
    resId = R.font.noto_sans_mono,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight), FontVariation.width(100f)),
)

private val NotoSansMono = FontFamily(
    notoSansMono(FontWeight.Normal),
    notoSansMono(FontWeight.Medium),
    notoSansMono(FontWeight.Bold),
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
