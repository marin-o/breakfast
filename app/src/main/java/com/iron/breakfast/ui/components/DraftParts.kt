package com.iron.breakfast.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.iron.breakfast.domain.DraftRow
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.LogDraftView
import com.iron.breakfast.domain.SegmentResolution
import com.iron.breakfast.ui.screens.choiceKcalDetail
import com.iron.breakfast.ui.screens.formatKcal
import com.iron.breakfast.ui.screens.noValueLine
import com.iron.breakfast.ui.screens.previewQuantityLine
import com.iron.breakfast.ui.theme.BreakfastType
import com.iron.breakfast.ui.theme.LocalBreakfastColors

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

/**
 * The field after the text changed from outside the field (a tapped suggestion or choice): the caret goes to the end
 * of the new text. When the text is what the field already shows (the user typed it), the field keeps its caret.
 */
internal fun syncedInputField(field: TextFieldValue, text: String): TextFieldValue =
    if (field.text == text) field else inputFieldFor(text)

/** The Log field for [text] with the caret at the end: a page that opens again starts there, not at the start. */
internal fun inputFieldFor(text: String): TextFieldValue = TextFieldValue(text, TextRange(text.length))

/** A draft's field as it shows now, and what a change to it does. */
internal class DraftField(val value: TextFieldValue, val onChange: (TextFieldValue) -> Unit)

/**
 * The field for draft [text]. It keeps its own caret, and a change made from outside (a tapped choice) moves the caret
 * to the end. One logical line: Done finishes it, so a newline is never typed; each change goes to [onText].
 */
@Composable
internal fun rememberDraftField(text: String, onText: (String) -> Unit): DraftField {
    var field by remember { mutableStateOf(inputFieldFor(text)) }
    LaunchedEffect(text) { field = syncedInputField(field, text) }
    return DraftField(field) { next ->
        val typed = next.text.replace("\n", " ")
        field = next.copy(text = typed)
        onText(typed)
    }
}
