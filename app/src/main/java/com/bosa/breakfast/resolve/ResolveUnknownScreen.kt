package com.bosa.breakfast.resolve

import com.bosa.breakfast.search.SkipButton
import com.bosa.breakfast.search.SearchYourFoodsButton
import com.bosa.breakfast.ui.components.SearchOrCreateButtons
import com.bosa.breakfast.ui.components.SubScreen
import com.bosa.breakfast.ui.screens.plainLine
import com.bosa.breakfast.ui.screens.QuantityDisplay
import com.bosa.breakfast.ui.theme.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bosa.breakfast.app.Confirmations
import com.bosa.breakfast.food.FoodForm
import com.bosa.breakfast.search.OffSearch
import com.bosa.breakfast.today.UndoController
import com.bosa.breakfast.ui.label
import com.bosa.breakfast.ui.components.foodText
import com.bosa.breakfast.ui.components.FoodRow
import com.bosa.breakfast.ui.components.Hairline
import com.bosa.breakfast.ui.components.ToastReveal
import com.bosa.breakfast.ui.components.SearchField
import com.bosa.breakfast.ui.components.SectionHeading
import com.bosa.breakfast.ui.theme.LocalBreakfastColors
import com.bosa.breakfast.ui.screens.unknownDisplay
import com.bosa.breakfast.ui.screens.matchDisplay


/** The unknown-food resolution queue: the kept quantity stays on the sticker; each match logs it for the selected day and meal. */
@Composable internal fun ResolveUnknownScreen(
    resolve: ResolveFlow, undo: UndoController, search: OffSearch, foodForm: FoodForm, confirmations: Confirmations,
) {
    val colors = LocalBreakfastColors.current
    val raw = resolve.parts.firstOrNull().orEmpty()
    val display = unknownDisplay(raw, resolve.typed(raw))
    val (position, total) = resolvePosition(resolve.queueTotal, resolve.parts.size)
    val saved = undo.lastBatchNames()
    val context = listOfNotNull(
        resolve.queueMeal?.label(),
        saved.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { "$it saved" },
    ).joinToString(", ")
    val meta = listOfNotNull(context.ifBlank { null }, "$position of $total").joinToString(". ")
    val typed = listOfNotNull(display.quantity, display.name).joinToString(" ")
    LaunchedEffect(raw) { resolve.onQuery(display.name) }
    // A part logged from a screen the resolver opened says "Logged!" at its foot, while the queue goes on.
    SubScreen("Resolve", resolve::close, toasts = { ToastReveal(confirmations.message, confirmations.slot) }) {
        Column(Modifier.padding(top = 14.dp).fillMaxWidth().semantics(mergeDescendants = true) { }) {
            Text(meta, color = colors.soft, style = BreakfastType.Meta)
            Text(foodText(typed), color = colors.ink, style = BreakfastType.LargeInput, modifier = Modifier.padding(top = 4.dp))
        }
        Hairline(Modifier.padding(top = 30.dp))
        Column(Modifier.padding(top = 16.dp)) {
            if (resolve.searchOpen) {
                Text("Search your foods", color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(bottom = 6.dp))
                SearchField(resolve.query, resolve::onQuery, onClear = { resolve.onQuery("") }, label = "Search your foods")
            } else {
                SearchYourFoodsButton(resolve::beginSearch, Modifier.padding(bottom = 8.dp))
            }
            if (resolve.results.isNotEmpty()) {
                SectionHeading("Local matches", Modifier.padding(top = 30.dp, bottom = 4.dp))
                resolve.results.forEach { ranked ->
                    val match = matchDisplay(ranked.food, raw)
                    FoodRow(
                        name = ranked.food.name,
                        quantityLine = QuantityDisplay(match.detail, match.approximateLabel).plainLine(),
                        calories = match.kcal,
                        onClick = { resolve.resolve(ranked.food) },
                    )
                }
            }
            Column(Modifier.padding(top = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SearchOrCreateButtons({ search.open(resolve.currentName(), fromResolver = true) }, { foodForm.open(resolve.currentName(), fromResolver = true) })
                SkipButton(resolve::skip)
            }
        }
    }
}
