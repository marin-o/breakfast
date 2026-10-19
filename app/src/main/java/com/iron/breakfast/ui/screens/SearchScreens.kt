package com.iron.breakfast.ui.screens

import com.iron.breakfast.ui.theme.*

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.iron.breakfast.BreakfastViewModel
import com.iron.breakfast.label
import com.iron.breakfast.pretty
import com.iron.breakfast.resolvePosition
import com.iron.breakfast.ui.components.foodText
import com.iron.breakfast.ui.components.FoodRow
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.LinearProgress
import com.iron.breakfast.ui.components.PrimaryButton
import com.iron.breakfast.ui.components.ButtonKind
import com.iron.breakfast.ui.components.SearchField
import com.iron.breakfast.ui.components.SecondaryButton
import com.iron.breakfast.ui.components.SectionHeading
import com.iron.breakfast.ui.components.TertiaryButton
import com.iron.breakfast.ui.components.TopBar
import com.iron.breakfast.ui.theme.LocalBreakfastColors

/** Local search that found nothing: the way on is Open Food Facts or a personal food. */
@Composable internal fun LibraryNoMatchScreen(vm: BreakfastViewModel) {
    val colors = LocalBreakfastColors.current
    val query = vm.libraryQuery
    // Leaving a no-match result must keep the Daily quick-add draft intact.
    val clear = { vm.onLibraryQuery("") }
    BackHandler(onBack = clear)
    Surface(Modifier.fillMaxSize(), color = colors.bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = Gutter)) {
            TopBar("Library", clear, Modifier.padding(top = 8.dp))
            SearchField(query, vm::onLibraryQuery, onClear = clear, label = "Search library", modifier = Modifier.padding(top = 8.dp))
            Column(Modifier.fillMaxWidth().padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeading("No local match", Modifier.semantics { heading() })
                Text(query, color = colors.soft, style = BreakfastType.Secondary)
            }
            Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Search Open Food Facts", { vm.openSearch(query.trim()) }, kind = ButtonKind.Neutral)
                SecondaryButton("Create personal food", { vm.openCustom(query.trim()) })
            }
        }
    }
}

/** Explicit Open Food Facts search: nothing is sent until Search (or the keyboard's search key) is used. */
@Composable internal fun OffSearchScreen(vm: BreakfastViewModel) {
    val colors = LocalBreakfastColors.current
    BackHandler(onBack = vm::closeSearch)
    Surface(Modifier.fillMaxSize(), color = colors.bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = Gutter).verticalScroll(rememberScrollState())) {
            TopBar("Open Food Facts", vm::closeSearch, Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SearchField(vm.searchQuery, vm::onSearch, onClear = { vm.onSearch("") }, label = "Search Open Food Facts", modifier = Modifier.weight(1f), onSearch = vm::submitSearch)
                if (vm.searchBusy) SecondaryButton("Search", vm::submitSearch, Modifier.width(104.dp), enabled = false, fillWidth = false)
                else PrimaryButton("Search", vm::submitSearch, Modifier.width(104.dp), kind = ButtonKind.Neutral, fillWidth = false)
            }
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    vm.searchBusy -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgress("Searching Open Food Facts", Modifier.padding(vertical = 14.dp))
                        TertiaryButton("Cancel", vm::closeSearch)
                    }
                    vm.offResults.isNotEmpty() -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${vm.offResults.size} results", color = colors.soft, style = BreakfastType.Secondary)
                            Text("/${vm.offResults.first().nutrition.amount.pretty()} ${vm.offResults.first().nutrition.unit.label}", color = colors.soft, style = BreakfastType.Secondary)
                        }
                        vm.offResults.forEach { food ->
                            FoodRow(food.name, food.brand.orEmpty(), receiptKcal(food.nutrition.values.calories), onClick = { vm.chooseProduct(food) })
                        }
                    }
                }
                vm.offSearchMessage?.let { message ->
                    if (vm.offSearchUnavailable) {
                        SectionHeading("No connection", Modifier.semantics { heading() })
                        Text("Open Food Facts needs internet. Local foods still work.", color = colors.soft, style = BreakfastType.Secondary)
                        SecondaryButton("Retry", vm::submitSearch)
                        SecondaryButton("Create personal food", { vm.openCustom(vm.searchQuery.trim(), fromResolver = vm.resolutionContinuation) })
                    } else if (message.startsWith("No usable result")) {
                        SectionHeading("No results", Modifier.semantics { heading() })
                        SecondaryButton("Create personal food", { vm.openCustom(vm.searchQuery.trim(), fromResolver = vm.resolutionContinuation) })
                    } else {
                        SectionHeading("Search failed", Modifier.semantics { heading() })
                        Text("Open Food Facts returned an error.", color = colors.soft, style = BreakfastType.Secondary)
                        SecondaryButton("Retry", vm::submitSearch)
                        SecondaryButton("Create personal food", { vm.openCustom(vm.searchQuery.trim(), fromResolver = vm.resolutionContinuation) })
                    }
                }
            }
        }
    }
}

/** The unknown-food resolution queue: the kept quantity stays on the sticker; each match logs it for the selected day and meal. */
@Composable internal fun ResolveUnknownScreen(vm: BreakfastViewModel) {
    val colors = LocalBreakfastColors.current
    val raw = vm.unknownQueue.firstOrNull().orEmpty()
    val display = unknownDisplay(raw)
    val (position, total) = resolvePosition(vm.unknownQueueTotal, vm.unknownQueue.size)
    val saved = vm.savedBatchNames()
    val subtitle = listOfNotNull(
        vm.unknownQueueTarget?.label(),
        saved.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { "$it saved" },
    ).joinToString(", ").ifBlank { null }
    LaunchedEffect(raw) { vm.onResolverQuery(display.name) }
    BackHandler(onBack = vm::closeResolver)
    Surface(Modifier.fillMaxSize(), color = colors.bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = Gutter).verticalScroll(rememberScrollState())) {
            TopBar("Resolve", vm::closeResolver, Modifier.padding(top = 8.dp), subtitle = subtitle)
            Column(Modifier.padding(top = 16.dp).fillMaxWidth().semantics(mergeDescendants = true) { }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    if (display.quantity != null) Text(display.quantity, color = colors.ink, style = BreakfastType.BodyMedium) else Spacer(Modifier.size(1.dp))
                    Text("$position of $total", color = colors.soft, style = BreakfastType.Meta)
                }
                Text(foodText(display.name), color = colors.ink, style = BreakfastType.Section, modifier = Modifier.padding(top = 8.dp))
            }
            Hairline(Modifier.padding(top = 16.dp))
            Column(Modifier.padding(top = 16.dp)) {
                if (vm.resolverSearchOpen) {
                    SearchField(vm.resolverQuery, vm::onResolverQuery, onClear = { vm.onResolverQuery("") }, label = "Search your foods", modifier = Modifier.padding(bottom = 16.dp))
                } else {
                    TertiaryButton("Search your foods", vm::beginResolverSearch, Modifier.padding(bottom = 8.dp))
                }
                if (vm.resolverResults.isNotEmpty()) {
                    SectionHeading("Local matches", Modifier.padding(bottom = 4.dp))
                    vm.resolverResults.forEach { ranked ->
                        val match = matchDisplay(ranked.food, raw)
                        val detail = match.approximateLabel?.let { "≈ $it, ${match.detail}" } ?: match.detail
                        FoodRow(
                            name = ranked.food.name,
                            quantityLine = detail,
                            calories = match.kcal,
                            onClick = { vm.resolveUnknown(ranked.food) },
                        )
                    }
                }
                Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Search Open Food Facts", { vm.openSearch(vm.currentUnknownName(), fromResolver = true) })
                    SecondaryButton("Create personal food", { vm.openCustom(vm.currentUnknownName(), fromResolver = true) })
                    TertiaryButton("Skip", vm::skipCurrentUnknown, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
