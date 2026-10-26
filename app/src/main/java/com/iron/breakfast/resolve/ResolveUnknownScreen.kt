package com.iron.breakfast.resolve

import com.iron.breakfast.ui.theme.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.iron.breakfast.app.BreakfastViewModel
import com.iron.breakfast.ui.label
import com.iron.breakfast.resolvePosition
import com.iron.breakfast.ui.components.foodText
import com.iron.breakfast.ui.components.FoodRow
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.PrimaryButton
import com.iron.breakfast.ui.components.ButtonKind
import com.iron.breakfast.ui.components.SearchField
import com.iron.breakfast.ui.components.SecondaryButton
import com.iron.breakfast.ui.components.SectionHeading
import com.iron.breakfast.ui.components.TertiaryButton
import com.iron.breakfast.ui.components.TopBar
import com.iron.breakfast.ui.theme.LocalBreakfastColors
import com.iron.breakfast.ui.screens.unknownDisplay
import com.iron.breakfast.ui.screens.matchDisplay


/** The unknown-food resolution queue: the kept quantity stays on the sticker; each match logs it for the selected day and meal. */
@Composable internal fun ResolveUnknownScreen(vm: BreakfastViewModel) {
    val colors = LocalBreakfastColors.current
    val raw = vm.unknownQueue.firstOrNull().orEmpty()
    val display = unknownDisplay(raw, vm.typedUnknown(raw))
    val (position, total) = resolvePosition(vm.unknownQueueTotal, vm.unknownQueue.size)
    val saved = vm.undo.lastBatchNames()
    val context = listOfNotNull(
        vm.unknownQueueTarget?.label(),
        saved.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { "$it saved" },
    ).joinToString(", ")
    val meta = listOfNotNull(context.ifBlank { null }, "$position of $total").joinToString(". ")
    val typed = listOfNotNull(display.quantity, display.name).joinToString(" ")
    LaunchedEffect(raw) { vm.onResolverQuery(display.name) }
    BackHandler(onBack = vm::closeResolver)
    Surface(Modifier.fillMaxSize(), color = colors.bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = Gutter).verticalScroll(rememberScrollState())) {
            TopBar("Resolve", vm::closeResolver, Modifier.padding(top = 8.dp))
            Column(Modifier.padding(top = 14.dp).fillMaxWidth().semantics(mergeDescendants = true) { }) {
                Text(meta, color = colors.soft, style = BreakfastType.Meta)
                Text(foodText(typed), color = colors.ink, style = BreakfastType.LargeInput, modifier = Modifier.padding(top = 4.dp))
            }
            Hairline(Modifier.padding(top = 30.dp))
            Column(Modifier.padding(top = 16.dp)) {
                if (vm.resolverSearchOpen) {
                    Text("Search your foods", color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(bottom = 6.dp))
                    SearchField(vm.resolverQuery, vm::onResolverQuery, onClear = { vm.onResolverQuery("") }, label = "Search your foods")
                } else {
                    TertiaryButton("Search your foods", vm::beginResolverSearch, Modifier.padding(bottom = 8.dp))
                }
                if (vm.resolverResults.isNotEmpty()) {
                    SectionHeading("Local matches", Modifier.padding(top = 30.dp, bottom = 4.dp))
                    vm.resolverResults.forEach { ranked ->
                        val match = matchDisplay(ranked.food, raw)
                        val detail = match.approximateLabel?.let { "≈$it, ${match.detail}" } ?: match.detail
                        FoodRow(
                            name = ranked.food.name,
                            quantityLine = detail,
                            calories = match.kcal,
                            onClick = { vm.resolveUnknown(ranked.food) },
                        )
                    }
                }
                Column(Modifier.padding(top = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton("Search Open Food Facts", { vm.search.open(vm.currentUnknownName(), fromResolver = true) }, kind = ButtonKind.Neutral)
                    SecondaryButton("Create personal food", { vm.openCustom(vm.currentUnknownName(), fromResolver = true) })
                    TertiaryButton("Skip", vm::skipCurrentUnknown, Modifier.fillMaxWidth(), soft = true)
                }
            }
        }
    }
}
