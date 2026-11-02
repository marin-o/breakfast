package mk.bosa.breakfast.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import mk.bosa.breakfast.R

val Geologica = FontFamily(
    Font(R.font.geologica_light, FontWeight.Light),
    Font(R.font.geologica_regular, FontWeight.Normal),
    Font(R.font.geologica_medium, FontWeight.Medium),
    Font(R.font.geologica_semibold, FontWeight.SemiBold),
)

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: TextUnit = TextUnit.Unspecified) = TextStyle(
    fontFamily = Geologica, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight,
    letterSpacing = tracking, fontFeatureSettings = "tnum",
)

/** The type scale from the design brief, named by role. */
object BreakfastType {
    val Hero = style(72, 76, FontWeight.Light, (-0.04).em)
    val LargeInput = style(26, 34, FontWeight.Light)
    val Section = style(20, 26, FontWeight.Medium)
    val Title = style(17, 22, FontWeight.Medium)
    val MacroValue = style(20, 26, FontWeight.Medium)
    val Body = style(16, 22)
    val BodyMedium = style(16, 22, FontWeight.Medium)
    val Button = style(16, 22, FontWeight.Medium)
    val Secondary = style(14, 19)
    val SecondaryMedium = style(14, 19, FontWeight.Medium)
    val Meta = style(13, 18)
}

/** Material slots mapped onto the scale so stock components stay on it. */
val BreakfastTypography = Typography(
    displayLarge = BreakfastType.Hero, displayMedium = BreakfastType.Hero, displaySmall = BreakfastType.LargeInput,
    headlineLarge = BreakfastType.Section, headlineMedium = BreakfastType.Section, headlineSmall = BreakfastType.Title,
    titleLarge = BreakfastType.Section, titleMedium = BreakfastType.Title, titleSmall = BreakfastType.Button,
    bodyLarge = BreakfastType.Body, bodyMedium = BreakfastType.Body, bodySmall = BreakfastType.Secondary,
    labelLarge = BreakfastType.Button, labelMedium = BreakfastType.Secondary, labelSmall = BreakfastType.Meta,
)
