package mk.bosa.breakfast.ui.components

import mk.bosa.breakfast.domain.MeasureUnit
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mk.bosa.breakfast.domain.MealSlot
import mk.bosa.breakfast.ui.label
import mk.bosa.breakfast.ui.theme.BreakfastType
import mk.bosa.breakfast.ui.theme.ChoiceMinHeight
import mk.bosa.breakfast.ui.theme.ChoiceShape
import mk.bosa.breakfast.ui.theme.LocalBreakfastColors

/** Unit, meal, ambiguous-food or alternative choice. [detail] is an optional second soft line, such as kcal. */
@Composable
fun ChoiceOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    /** Four-up meal row: 14sp, tighter padding, one line that never breaks mid-word (ellipsis as a last resort). */
    compact: Boolean = false,
    /** False for an action chip (Search, an alternative): a plain button to TalkBack, not a radio button. */
    selectable: Boolean = true,
    /** The one the app picks by itself: its border is `ok`, its label stays ink, so it is seen before Add logs it. */
    autoPick: Boolean = false,
    /** The label in the middle of a wide option, as a row of equal options draws it. */
    centred: Boolean = false,
) {
    val colors = LocalBreakfastColors.current
    val marked = selected || autoPick
    Column(
        modifier
            .heightIn(min = ChoiceMinHeight)
            .border(if (marked) 1.5.dp else 1.dp, if (autoPick) colors.ok else if (selected) colors.ink else colors.line, ChoiceShape)
            .then(
                if (selectable) Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                else Modifier.clickable(role = Role.Button, onClick = onClick)
            )
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (centred) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            foodText(label),
            color = if (marked) colors.ink else colors.soft,
            style = when {
                compact && marked -> BreakfastType.SecondaryMedium
                compact -> BreakfastType.Secondary
                marked -> BreakfastType.BodyMedium
                else -> BreakfastType.Body
            },
            maxLines = if (compact) 1 else 2,
            softWrap = !compact,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (compact) TextAlign.Center else TextAlign.Start,
        )
        if (detail != null) Text(detail, color = colors.soft, style = BreakfastType.Secondary)
    }
}

/**
 * One choice of [options] as equal compact [ChoiceOption]s under an optional soft [label]: [columns] across, or as many as
 * still hold every label on one line, so a large font or a small screen breaks the rows, never a word.
 */
@Composable
internal fun <T> ChoiceGrid(options: List<T>, text: (T) -> String, selected: T?, onSelect: (T) -> Unit, columns: Int = options.size, label: String? = null) {
    val gap = 8.dp
    Column {
        if (label != null) Text(label, color = LocalBreakfastColors.current.soft, style = BreakfastType.Meta, modifier = Modifier.padding(bottom = 4.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val labels = options.map(text)
            // The compact option's padding and selected outline, each side.
            val fitting = (columns downTo 2).firstOrNull { fitsAColumn(labels, BreakfastType.SecondaryMedium, 19.dp, maxWidth, gap, it) } ?: 1
            Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(gap)) {
                options.chunked(fitting).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { option -> ChoiceOption(text(option), option == selected, { onSelect(option) }, Modifier.weight(1f), compact = true, centred = true) }
                        // A short last row keeps the columns' widths.
                        repeat(fitting - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** The g and ml of a basis or a serving, in the row they sit in; each takes [itemModifier]. */
@Composable
internal fun GramMlChoices(selected: MeasureUnit?, onSelect: (MeasureUnit) -> Unit, itemModifier: Modifier) {
    listOf(MeasureUnit.GRAM, MeasureUnit.MILLILITER).forEach { unit ->
        ChoiceOption(unit.label, selected == unit, { onSelect(unit) }, itemModifier)
    }
}

/** "Meal" over the [MealChoices], one row when it fits (M-Library-AmountSheet, M-Entry-FullEdit, M-Scan-Review). */
@Composable
internal fun MealField(selected: MealSlot, onSelect: (MealSlot) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    Column(modifier.fillMaxWidth()) {
        Text("Meal", color = LocalBreakfastColors.current.soft, style = BreakfastType.Meta, modifier = Modifier.padding(bottom = 4.dp))
        MealChoices(selected, onSelect, singleRow = true, compact = compact)
    }
}

/** The meal choices: a 2x2 grid, or one row of four when [singleRow] (Library repeat and amount sheets). */
@Composable
fun MealChoices(selected: MealSlot?, onSelect: (MealSlot) -> Unit, modifier: Modifier = Modifier, singleRow: Boolean = false, compact: Boolean = false) {
    if (singleRow) {
        // One row only when the longest meal name fits on one line in a quarter; otherwise two by two, never a broken word.
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val style = if (compact) BreakfastType.SecondaryMedium else BreakfastType.BodyMedium
            // 6dp apart in a sheet's row, 8dp in the scan review's compact one, as the mocks draw them.
            val gap = if (compact) 8.dp else 6.dp
            if (fitsAQuarter(MealSlot.entries.map { it.label() }, style, (if (compact) 16.dp else 24.dp) + 2.dp, maxWidth, gap)) {
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    MealSlot.entries.forEach { meal -> ChoiceOption(meal.label(), selected == meal, { onSelect(meal) }, Modifier.weight(1f), compact = compact) }
                }
            } else MealChoices(selected, onSelect, Modifier.fillMaxWidth(), singleRow = false, compact = compact)
        }
        return
    }
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MealSlot.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { meal ->
                    ChoiceOption(meal.label(), selected == meal, { onSelect(meal) }, Modifier.weight(1f), compact = compact)
                }
            }
        }
    }
}
