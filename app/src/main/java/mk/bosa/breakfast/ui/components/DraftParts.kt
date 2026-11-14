package mk.bosa.breakfast.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import mk.bosa.breakfast.domain.DraftRow
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.LogDraftView
import mk.bosa.breakfast.domain.SegmentResolution
import mk.bosa.breakfast.ui.screens.choiceKcalDetail
import mk.bosa.breakfast.ui.screens.formatKcal
import mk.bosa.breakfast.ui.screens.noValueLine
import mk.bosa.breakfast.ui.screens.previewQuantityLine
import mk.bosa.breakfast.ui.theme.BreakfastType
import mk.bosa.breakfast.ui.theme.LocalBreakfastColors

// What the Log page and a recipe's ingredients share: a draft's field, and its parts under it.

/**
 * A draft's parts as the Log page shows them, under its field: the rows in typed order (an entry, or "Not in your
 * foods", which [onResolve] takes), then the choice group for the part being typed ("which one?" for real choices),
 * whose tap is [onChoose]. The Log page and a recipe's ingredients both draw it.
 */
@Composable
internal fun DraftParts(
    draft: LogDraftView,
    onChoose: (FoodRecord) -> Unit,
    onResolve: (DraftRow.Unresolved) -> Unit,
    /** The rows Add could not match, drawn by [queuedRow] instead. */
    queued: List<String> = emptyList(),
    queuedRow: @Composable (DraftRow.Unresolved) -> Unit = {},
) {
    val colors = LocalBreakfastColors.current
    // Rows in typed order. Typed text that matches no food stays visible, so it is never lost when Add is pressed.
    // Not animated: a row has no identity that lasts while its text is typed (DESIGN.md, Motion).
    draft.rows.forEach { row ->
        when (row) {
            is DraftRow.Entry -> FoodRow(
                name = row.resolution.food.name,
                quantityLine = previewQuantityLine(row.resolution.amount, row.resolution.unit, row.resolution.calculation.approximate, row.resolution.label),
                calories = formatKcal(row.resolution.calculation.nutrition.calories),
                modifier = Modifier.padding(top = 4.dp),
            )
            is DraftRow.Unresolved -> if (row.text in queued) queuedRow(row) else NotInYourFoodsRow(row.text, Modifier.padding(top = 4.dp), onClick = { onResolve(row) }, noValue = row.noValue)
        }
    }
    val suggestions = draft.choices
    if (suggestions.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Bottom) {
            Text(foodText(draft.activePhrase), color = colors.ink, style = BreakfastType.Body, modifier = Modifier.weight(1f))
            if (draft.choiceHint) Text("which one?", color = colors.soft, style = BreakfastType.Meta)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEachIndexed { index, ranked ->
                ChoiceOption(
                    label = ranked.food.name,
                    selected = index == 0 && draft.preselected,
                    autoPick = index == 0 && draft.firstIsAddPick,
                    onClick = { onChoose(ranked.food) },
                    modifier = Modifier.widthIn(min = 168.dp),
                    detail = draft.choiceCalculations.getOrNull(index)?.let(::choiceKcalDetail),
                )
            }
        }
    }
}

/** A typed part no food was logged for: no food behind it, or, with [noValue], its food in a unit that has no value. */
@Composable
internal fun NotInYourFoodsRow(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, noValue: SegmentResolution? = null) {
    FoodRow(
        name = noValue?.food?.name ?: text,
        quantityLine = noValue?.let(::noValueLine) ?: "Not in your foods",
        calories = "",
        modifier = modifier,
        onClick = onClick,
    )
}

/** The typed line and the line under it, with [trailing] at its end. [text] takes the modifier that places it. */
@Composable
internal fun DraftLine(trailing: @Composable () -> Unit = {}, text: @Composable (Modifier) -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        text(Modifier.weight(1f).padding(top = 18.dp))
        trailing()
    }
    Hairline(Modifier.padding(top = 22.dp))
}

/** A draft's field as it shows now, and what a change to it does. */
internal class DraftField(val value: TextFieldValue, val onChange: (TextFieldValue) -> Unit)

/**
 * The field for draft [text], with the one caret rule ([CaretField]): a change made from outside (a tapped choice) moves
 * the caret to the end; each change goes to [onText]. By default one logical line: Done finishes it, so a newline is
 * never typed and becomes a space (the Log bar). With [multiline] a newline stays, so each line can be a part (a recipe's
 * ingredients); line endings are normalised to a single line feed.
 */
@Composable
internal fun rememberDraftField(text: String, onText: (String) -> Unit, multiline: Boolean = false): DraftField {
    val caret = rememberCaretField(text)
    return DraftField(caret.value) { next ->
        val flat = if (multiline) next.text.replace("\r\n", "\n").replace('\r', '\n') else next.text.replace("\n", " ")
        caret.onChange(next.copy(text = flat), onText)
    }
}
