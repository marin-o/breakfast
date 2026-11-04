package mk.bosa.breakfast.ui.components

import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import mk.bosa.breakfast.ui.MacroPart
import mk.bosa.breakfast.ui.label
import mk.bosa.breakfast.ui.lineText
import mk.bosa.breakfast.ui.theme.Macro
import mk.bosa.breakfast.ui.theme.Mark
import mk.bosa.breakfast.ui.theme.markColor
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import mk.bosa.breakfast.ui.theme.BreakfastType
import mk.bosa.breakfast.ui.theme.LocalBreakfastColors

/**
 * Whether fibre is shown beside the macros ([mk.bosa.breakfast.domain.fibreShown]): its Meals column, its dots and its
 * part of the macro lines. `MainActivity` provides it; off where nothing does.
 */
internal val LocalFibreShown = compositionLocalOf { false }

/** One macro column (or fibre's): [share] is its fraction of the 4dp track, in [kind]'s colour; [value] is ink and [unit] soft. */
data class MacroStat(val kind: Mark, val label: String, val value: String, val unit: String, val share: Float, val target: String? = null) {
    constructor(kind: Macro, label: String, value: String, unit: String, share: Float, target: String? = null) :
        this(Mark.OfMacro(kind), label, value, unit, share, target)
}

/** The one big figure of a screen: 72sp kcal, its caption, and three macro columns (none when [macros] is empty). */
@Composable
fun DayTotal(
    kilocalories: String,
    macros: List<MacroStat>,
    modifier: Modifier = Modifier,
    caption: String = "kilocalories today",
    /** One soft line under the macros, or nothing. */
    note: String? = null,
    /** The kcal target's soft line between the figure and its caption, as a macro's sits between amount and label. */
    target: String? = null,
) {
    val colors = LocalBreakfastColors.current
    val stacked = isLargeFont(LocalDensity.current.fontScale)
    // Any target gives every column the line, so the three stay the same height.
    val anyTarget = macros.any { it.target != null }
    Column(modifier.fillMaxWidth()) {
        Text(kilocalories, color = colors.ink, style = BreakfastType.Hero)
        target?.let { Text(it, color = colors.soft, style = BreakfastType.Secondary) }
        Text(caption, color = colors.soft, style = BreakfastType.Secondary)
        if (macros.isEmpty()) {
            // Only the kcal: "Macros on today" is hidden.
        } else if (stacked) {
            // Large font: one macro per row, value, unit and label on one line, each with its own bar above.
            Column(Modifier.fillMaxWidth().padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                macros.forEach { macro ->
                    Column(Modifier.fillMaxWidth()) {
                        MacroTrack(macro.share, macro.kind)
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                            Text(macro.value, color = colors.ink, style = BreakfastType.MacroValue)
                            Text(" ${macro.unit}", color = colors.soft, style = BreakfastType.Secondary)
                            macro.target?.let { Text(" $it", color = colors.soft, style = BreakfastType.Secondary) }
                            Text(macro.label, color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                macros.forEach { macro ->
                    Column(Modifier.weight(1f)) {
                        MacroTrack(macro.share, macro.kind)
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                            Text(macro.value, color = colors.ink, style = BreakfastType.MacroValue)
                            Text(" ${macro.unit}", color = colors.soft, style = BreakfastType.Secondary)
                        }
                        if (anyTarget) Text(macro.target.orEmpty(), color = colors.soft, style = BreakfastType.Secondary, minLines = 1, maxLines = 1)
                        Text(macro.label, color = colors.soft, style = BreakfastType.Meta)
                    }
                }
            }
        }
        note?.let { Text(it, color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(top = 10.dp)) }
    }
}

/** 4dp `line` track, its corners 2dp, with the macro's (or fibre's) share as a fill in its colour, clipped to the track. */
@Composable
internal fun MacroTrack(share: Float, mark: Mark) {
    val colors = LocalBreakfastColors.current
    Box(Modifier.fillMaxWidth().height(MacroTrackHeight).clip(MacroTrackShape).background(colors.line)) {
        Box(Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).height(MacroTrackHeight).background(colors.markColor(mark)))
    }
}

private val MacroTrackHeight = 4.dp
private val MacroTrackShape = RoundedCornerShape(2.dp)
internal val MacroDotSize = 8.dp
internal val MacroDotGap = 5.dp
private val MacroLinePartGap = 10.dp

/** A label with the [mark]'s dot centred on its first line, or the label alone with none. The dot says nothing to TalkBack. */
@Composable
internal fun MacroLabel(text: String, mark: Mark?, color: Color, style: TextStyle, modifier: Modifier = Modifier) {
    Row(modifier) {
        if (mark != null) {
            val measurer = rememberTextMeasurer()
            val firstLine = remember(measurer, style) { measurer.measure("0", style).size.height }
            Box(Modifier.padding(end = MacroDotGap).height(with(LocalDensity.current) { firstLine.toDp() }), contentAlignment = Alignment.Center) {
                Box(Modifier.size(MacroDotSize).background(LocalBreakfastColors.current.markColor(mark), CircleShape))
            }
        }
        Text(text, color = color, style = style)
    }
}

/**
 * A soft line of [parts], each macro's with its dot; it wraps between parts, each row placed by [alignment].
 * No commas on screen; TalkBack hears the one line with them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MacroLine(parts: List<MacroPart>, style: TextStyle, modifier: Modifier = Modifier, alignment: Alignment.Horizontal = Alignment.Start, color: Color = LocalBreakfastColors.current.soft) {
    val spoken = parts.lineText()
    FlowRow(modifier.clearAndSetSemantics { text = AnnotatedString(spoken) }, horizontalArrangement = Arrangement.spacedBy(MacroLinePartGap, alignment)) {
        parts.forEach { MacroLabel(it.text, it.mark, color, style) }
    }
}
