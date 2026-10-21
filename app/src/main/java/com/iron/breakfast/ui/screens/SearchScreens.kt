package com.iron.breakfast.ui.screens

import com.iron.breakfast.ui.theme.*

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextAlign
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
            TopBar("Search", clear, Modifier.padding(top = 8.dp))
            SearchCaption(Modifier.padding(top = 6.dp))
            SearchField(query, vm::onLibraryQuery, onClear = clear, label = "Search library", modifier = Modifier.padding(top = 6.dp))
            Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No local match", color = colors.ink, style = BreakfastType.Title, modifier = Modifier.semantics { heading() })
                Text(
                    "Nothing in your foods matches \"$query\".",
                    color = colors.soft,
                    style = BreakfastType.Secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp).widthIn(max = 290.dp),
                )
            }
            Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            SearchCaption(Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SearchField(vm.searchQuery, vm::onSearch, onClear = { vm.onSearch("") }, label = "Search Open Food Facts", modifier = Modifier.weight(1f), onSearch = vm::submitSearch)
                if (vm.searchBusy) SecondaryButton("Search", vm::submitSearch, Modifier.widthIn(min = 104.dp), enabled = false, fillWidth = false)
                else PrimaryButton("Search", vm::submitSearch, Modifier.widthIn(min = 104.dp), kind = ButtonKind.Neutral, fillWidth = false)
            }
            Column(Modifier.padding(top = 16.dp)) {
                when {
                    vm.searchBusy -> Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgress("Searching Open Food Facts")
                        TertiaryButton("Cancel", vm::closeSearch, soft = true)
                    }
                    vm.offResults.isNotEmpty() -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${vm.offResults.size} results from Open Food Facts", color = colors.soft, style = BreakfastType.Secondary)
                            Text("kcal per ${vm.offResults.first().nutrition.amount.pretty()} ${vm.offResults.first().nutrition.unit.label}", color = colors.soft, style = BreakfastType.Secondary)
                        }
                        Hairline(Modifier.padding(top = 10.dp))
                        vm.offResults.forEach { food ->
                            FoodRow(food.name, food.brand.orEmpty(), receiptKcal(food.nutrition.values.calories), onClick = { vm.chooseProduct(food) })
                        }
                    }
                }
                vm.offSearchMessage?.let { message ->
                    val createPersonal = { vm.openCustom(vm.searchQuery.trim(), fromResolver = vm.resolutionContinuation) }
                    when (offSearchStatus(vm.offSearchUnavailable, message)) {
                        OffSearchStatus.Offline -> SearchStatus(
                            title = "No connection",
                            body = "Open Food Facts needs internet. Local foods still work.",
                            retry = vm::submitSearch,
                            createPersonal = createPersonal,
                        )
                        OffSearchStatus.NoResults -> SearchStatus(title = "No results", body = null, retry = null, createPersonal = createPersonal)
                        OffSearchStatus.Failed -> SearchStatus(
                            title = "Search failed",
                            body = "Open Food Facts returned an error.",
                            retry = vm::submitSearch,
                            createPersonal = createPersonal,
                        )
                    }
                }
            }
        }
    }
}

/** Which status block the Open Food Facts search shows after a query. */
internal enum class OffSearchStatus { Offline, NoResults, Failed }

/** Offline wins; a "No usable result" message is an empty result; anything else is a failed search. */
internal fun offSearchStatus(unavailable: Boolean, message: String): OffSearchStatus = when {
    unavailable -> OffSearchStatus.Offline
    message.startsWith("No usable result") -> OffSearchStatus.NoResults
    else -> OffSearchStatus.Failed
}

/** The soft "Search" label above both search fields. */
@Composable private fun SearchCaption(modifier: Modifier = Modifier) {
    Text("Search", color = LocalBreakfastColors.current.soft, style = BreakfastType.Meta, modifier = modifier)
}

/** A centred status on the Open Food Facts search: title, optional soft body, a Neutral Retry when there is one, and a personal-food secondary. */
@Composable private fun SearchStatus(title: String, body: String?, retry: (() -> Unit)?, createPersonal: () -> Unit) {
    val colors = LocalBreakfastColors.current
    Column(Modifier.fillMaxWidth().padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = colors.ink, style = BreakfastType.Title, modifier = Modifier.semantics { heading() })
        if (body != null) Text(body, color = colors.soft, style = BreakfastType.Secondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp).widthIn(max = 290.dp))
        Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (retry != null) PrimaryButton("Retry", retry, kind = ButtonKind.Neutral)
            SecondaryButton("Create personal food", createPersonal)
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
                    PrimaryButton("Search Open Food Facts", { vm.openSearch(vm.currentUnknownName(), fromResolver = true) }, kind = ButtonKind.Neutral)
                    SecondaryButton("Create personal food", { vm.openCustom(vm.currentUnknownName(), fromResolver = true) })
                    TertiaryButton("Skip", vm::skipCurrentUnknown, Modifier.fillMaxWidth(), soft = true)
                }
            }
        }
    }
}
