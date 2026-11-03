package mk.bosa.breakfast.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import mk.bosa.breakfast.ui.theme.BreakfastType
import mk.bosa.breakfast.ui.theme.LocalBreakfastColors

/** Food names and typed text use the Macedonian shaping locale. */
fun foodText(value: String): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(localeList = LocaleList("mk"))) { append(value) }
}

/** Section heading inside a screen (meal, form section). */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, color = LocalBreakfastColors.current.ink, style = BreakfastType.Section, modifier = modifier)
}

/** 1dp `line` hairline. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(LocalBreakfastColors.current.line))
}

/** Font scale from which the large-font layouts take over (2.0 is the reference; 1.3 keeps the normal layout). */
internal const val LargeFontScale = 1.8f

/** True at the large-font layout: stacked macros and meal times under headings, and stacked add bars. */
internal fun isLargeFont(fontScale: Float): Boolean = fontScale >= LargeFontScale
