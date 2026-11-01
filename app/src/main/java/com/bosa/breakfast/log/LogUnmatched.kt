package com.bosa.breakfast.log

import com.bosa.breakfast.ui.components.NotInYourFoodsRow
import com.bosa.breakfast.ui.components.DraftParts
import com.bosa.breakfast.search.SkipButton
import com.bosa.breakfast.search.SearchYourFoodsButton
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bosa.breakfast.domain.DraftRow
import com.bosa.breakfast.food.FoodForm
import com.bosa.breakfast.resolve.ResolveFlow
import com.bosa.breakfast.scan.ProductReview
import com.bosa.breakfast.scan.ProductReviewOrigin
import com.bosa.breakfast.search.OffPartSearch
import com.bosa.breakfast.search.OffPartState
import com.bosa.breakfast.search.OffSearch
import com.bosa.breakfast.search.OffSearchStatus
import com.bosa.breakfast.search.UsdaPartSearch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bosa.breakfast.domain.FoodRecord
import com.bosa.breakfast.ui.components.SearchOrCreateButtons
import com.bosa.breakfast.search.offStatusText
import com.bosa.breakfast.ui.components.FoodRow
import com.bosa.breakfast.ui.components.LinearProgress
import com.bosa.breakfast.ui.components.rememberMotion
import com.bosa.breakfast.ui.screens.basisLabel
import com.bosa.breakfast.ui.screens.formatKcal
import com.bosa.breakfast.ui.theme.BreakfastType
import com.bosa.breakfast.ui.theme.LocalBreakfastColors
import com.bosa.breakfast.ui.theme.PartInset

/** What an unmatched food's row needs: the queue, its searches, and where its ways on lead. */
internal class UnmatchedParts(
    val resolve: ResolveFlow,
    val results: OffPartSearch,
    val search: OffSearch,
    val foodForm: FoodForm,
    val review: ProductReview,
    val log: LogComposer,
    val usda: UsdaPartSearch,
) {
    /**
     * A USDA row for [query] under the part being typed: its review, with the amount typed, and Add puts the food in that
     * part's place. The rows may still be a frame behind the typing: a tap on one for older text does nothing.
     */
    fun pickForTypedPart(query: String, food: FoodRecord) {
        if (log.draftView().catalogueQuery != query) return
        val part = log.partBeingTyped()
        review.choose(food, origin = ProductReviewOrigin.LOG_PART, typed = part?.let(log::typedOf), replaces = part)
    }
}

/**
 * The foods Add could not match, as the Log draws them: each "Not in your foods" row opens and closes, with its USDA rows
 * and Open Food Facts results inset under it and its ways on after them. The first starts open; a resolved one leaves the list and the
 * next opens. Returns the row's drawing for [DraftParts].
 */
@Composable
internal fun rememberUnmatchedRows(parts: UnmatchedParts): @Composable (DraftRow.Unresolved) -> Unit {
    val queue = parts.resolve.parts
    val opened = remember { mutableStateMapOf<String, Boolean>() }
    // A part resolved or skipped: the next one starts open, whatever was folded before.
    LaunchedEffect(queue.size) { opened.clear() }
    fun isOpen(part: String) = opened[part] ?: (part == queue.firstOrNull())
    return { row ->
        UnmatchedRow(
            row, parts, open = isOpen(row.text),
            onToggle = { opened[row.text] = !isOpen(row.text) },
            // The queue's order changes under this part: what is open stays open.
            onFocus = { queue.forEach { part -> opened[part] = isOpen(part) }; parts.resolve.focus(row.text) },
        )
    }
}

@Composable
private fun UnmatchedRow(row: DraftRow.Unresolved, parts: UnmatchedParts, open: Boolean, onToggle: () -> Unit, onFocus: () -> Unit) {
    val colors = LocalBreakfastColors.current
    val motion = rememberMotion()
    val turn by animateFloatAsState(if (open) 180f else 0f, motion.nav(), label = "unmatched")
    val name = remember(row.text) { parts.resolve.nameOf(row.text) }
    val choices = remember(row.text) { parts.log.localChoices(row.text) }
    var catalogue by remember(name) { mutableStateOf(emptyList<FoodRecord>()) }
    LaunchedEffect(name) { catalogue = parts.usda.forPart(name) }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                // TalkBack says whether it is open by the action it offers.
                .semantics {
                    if (open) collapse { onToggle(); true } else expand { onToggle(); true }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (choices.isEmpty()) NotInYourFoodsRow(row.text, Modifier.weight(1f), noValue = row.noValue)
            else FoodRow(name = row.text, quantityLine = "which one?", calories = "", modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = colors.soft, modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = turn })
        }
        AnimatedVisibility(open, enter = motion.rowIn(), exit = motion.rowOut()) {
            Column(Modifier.fillMaxWidth().padding(start = PartInset, top = 4.dp).semantics { isTraversalGroup = true }) {
                choices.forEach { choice ->
                    // One tap: this part, with its amount, logs to the queue's meal and leaves the draft; with no meal yet, the Log asks.
                    PartFoodRow(name, choice.food.name, choice.quantityLine, choice.kcal) { onFocus(); parts.resolve.resolve(choice.food) }
                }
                catalogue.forEach { food ->
                    OfferedFoodRow(name, food) { onFocus(); parts.review.choose(food, origin = ProductReviewOrigin.QUEUE) }
                }
                when (val state = parts.results.stateOf(name)) {
                    OffPartState.Searching -> LinearProgress("Searching Open Food Facts", Modifier.padding(vertical = 8.dp))
                    is OffPartState.Found -> state.foods.forEach { food ->
                        OfferedFoodRow(name, food) { onFocus(); parts.review.choose(food, origin = ProductReviewOrigin.QUEUE) }
                    }
                    OffPartState.NoResults -> StatusLine(OffSearchStatus.NoResults)
                    OffPartState.Offline -> StatusLine(OffSearchStatus.Offline)
                    OffPartState.Failed -> StatusLine(OffSearchStatus.Failed)
                    null -> Unit
                }
                Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchOrCreateButtons(
                        onSearch = { onFocus(); parts.search.open(name, fromResolver = true); parts.search.submit() },
                        onCreate = { onFocus(); parts.foodForm.open(name, fromResolver = true) },
                    )
                    SearchYourFoodsButton({ parts.log.resolveNotInFoods(row) }, Modifier.fillMaxWidth())
                    SkipButton { onFocus(); parts.resolve.skip() }
                }
            }
        }
    }
}

/** Asks [search] for [query] as it is typed; the USDA foods to draw for it, those in [except] (already listed there) left out. */
@Composable
internal fun rememberUsdaRows(search: UsdaPartSearch, query: String, except: List<FoodRecord> = emptyList()): List<FoodRecord> {
    LaunchedEffect(query) { search.typed(query) }
    return search.rowsFor(query).filterNot { row -> except.any { it.id == row.id } }
}

/** USDA foods as plain rows: long names don't fit a chip, and arriving they change no selection. */
@Composable
internal fun UsdaRows(rows: List<FoodRecord>, query: String, modifier: Modifier = Modifier, onPick: (FoodRecord) -> Unit) {
    if (rows.isNotEmpty()) Column(modifier) {
        rows.forEach { food -> OfferedFoodRow(query, food) { onPick(food) } }
    }
}

/** A food not stored yet, offered for part [part] by USDA or Open Food Facts: its name, brand, basis and kcal on it. */
@Composable
internal fun OfferedFoodRow(part: String, food: FoodRecord, onClick: () -> Unit) {
    val basis = basisLabel(food.nutrition.amount, food.nutrition.unit)
    val line = listOfNotNull(food.brand?.takeIf { it.isNotBlank() }, basis).joinToString(", ")
    PartFoodRow(part, food.name, line, formatKcal(food.nutrition.values.calories), onClick)
}

/** One food offered for part [part], inset under its row, by USDA, Open Food Facts or the user's own foods; read as that part's. */
@Composable
private fun PartFoodRow(part: String, name: String, line: String, kcal: String, onClick: () -> Unit) {
    FoodRow(
        name = name,
        quantityLine = line,
        calories = kcal,
        modifier = Modifier.semantics { contentDescription = listOf("$part: $name", line, "$kcal kcal".takeIf { kcal.isNotEmpty() }).filterNotNull().joinToString(", ") },
        onClick = onClick,
    )
}

/** Why there are no results: the search screen's own words, on one soft block. */
@Composable
private fun StatusLine(status: OffSearchStatus) {
    val colors = LocalBreakfastColors.current
    val (title, body) = offStatusText(status)
    Text(title, color = colors.ink, style = BreakfastType.BodyMedium, modifier = Modifier.padding(top = 8.dp))
    if (body != null) Text(body, color = colors.soft, style = BreakfastType.Secondary)
}
