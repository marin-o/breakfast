package com.iron.breakfast.ui.screens

import com.iron.breakfast.ui.theme.*

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.iron.breakfast.BreakfastViewModel
import com.iron.breakfast.label
import com.iron.breakfast.resolvePosition
import com.iron.breakfast.pretty
import com.iron.breakfast.ui.components.CharacterRule
import com.iron.breakfast.ui.components.ItemRow
import com.iron.breakfast.ui.components.PrintedProgress
import com.iron.breakfast.ui.components.ReceiptOutlinedButton
import com.iron.breakfast.ui.components.ReceiptPrimaryButton
import com.iron.breakfast.ui.components.ReceiptSearchPill
import com.iron.breakfast.ui.components.receiptFoodName
import com.iron.breakfast.ui.components.ReceiptTopBar
import com.iron.breakfast.ui.components.TertiaryLink
import com.iron.breakfast.ui.theme.LocalReceiptColors
import com.iron.breakfast.ui.theme.ReceiptStickerShape

/** Local search that found nothing: the way on is Open Food Facts or a personal food. */
@Composable internal fun LibraryNoMatchScreen(vm: BreakfastViewModel) {
    val colors = LocalReceiptColors.current
    val query = vm.libraryQuery
    // Leaving a no-match result must keep the Daily quick-add draft intact.
    val clear = { vm.onLibraryQuery("") }
    BackHandler(onBack = clear)
    Surface(Modifier.fillMaxSize(), color = colors.paper) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = clear, Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.ink) }
                ReceiptSearchPill(query, vm::onLibraryQuery, onClear = clear, label = "Search library", modifier = Modifier.weight(1f))
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CharacterRule('-')
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NO LOCAL MATCH", color = colors.ink, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                    Text(query, color = colors.ink, fontWeight = FontWeight.Bold)
                }
                CharacterRule('-')
                Spacer(Modifier.height(8.dp))
                ReceiptPrimaryButton("Search Open Food Facts", { vm.openSearch(query.trim()) })
                ReceiptOutlinedButton("Create personal food", { vm.openCustom(query.trim()) })
            }
        }
    }
}

/** Explicit Open Food Facts search: nothing is sent until Search (or the keyboard's search key) is used. */
@Composable internal fun OffSearchScreen(vm: BreakfastViewModel) {
    val colors = LocalReceiptColors.current
    BackHandler(onBack = vm::closeSearch)
    Surface(Modifier.fillMaxSize(), color = colors.paper) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState())) {
            ReceiptTopBar("Open Food Facts", vm::closeSearch, Modifier.padding(horizontal = 4.dp))
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReceiptSearchPill(vm.searchQuery, vm::onSearch, onClear = { vm.onSearch("") }, label = "Search Open Food Facts", modifier = Modifier.weight(1f), onSearch = vm::submitSearch)
                if (vm.searchBusy) ReceiptOutlinedButton("Search", vm::submitSearch, Modifier.width(104.dp), fillWidth = false, enabled = false)
                else ReceiptPrimaryButton("Search", vm::submitSearch, Modifier.width(104.dp), fillWidth = false)
            }
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    vm.searchBusy -> Column { CharacterRule('-'); PrintedProgress("Searching", vm.searchQuery, modifier = Modifier.padding(vertical = 14.dp)); CharacterRule('-'); TertiaryLink("Cancel", vm::closeSearch, Modifier.padding(top = 4.dp)) }
                    vm.offResults.isNotEmpty() -> {
                        CharacterRule('-')
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${vm.offResults.size} RESULTS", color = colors.soft)
                            Text("/${vm.offResults.first().nutrition.amount.pretty()} ${vm.offResults.first().nutrition.unit.label}", color = colors.soft)
                        }
                        CharacterRule('-')
                        vm.offResults.forEach { food ->
                            ItemRow(food.name, food.brand.orEmpty(), receiptKcal(food.nutrition.values.calories), sourceLetter(food), approximate = null, onClick = { vm.chooseProduct(food) })
                        }
                        CharacterRule('=')
                    }
                }
                vm.offSearchMessage?.let { message ->
                    if (vm.offSearchUnavailable) {
                        CharacterRule('-')
                        Text("NO CONNECTION", color = colors.ink, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                        Text("Open Food Facts needs internet. Local foods still work.", color = colors.soft)
                        ReceiptOutlinedButton("Retry", vm::submitSearch)
                        ReceiptOutlinedButton("Create personal food", { vm.openCustom(vm.searchQuery.trim(), fromResolver = vm.resolutionContinuation) })
                    } else if (message.startsWith("No usable result")) {
                        Text("NO RESULTS", color = colors.ink, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                        ReceiptOutlinedButton("Create personal food", { vm.openCustom(vm.searchQuery.trim(), fromResolver = vm.resolutionContinuation) })
                    } else {
                        CharacterRule('-')
                        Text("SEARCH FAILED", color = colors.ink, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                        Text("Open Food Facts returned an error.", color = colors.soft)
                        ReceiptOutlinedButton("Retry", vm::submitSearch)
                        ReceiptOutlinedButton("Create personal food", { vm.openCustom(vm.searchQuery.trim(), fromResolver = vm.resolutionContinuation) })
                    }
                }
            }
        }
    }
}

/** The unknown-food resolution queue: the kept quantity stays on the sticker; each match logs it for the selected day and meal. */
@Composable internal fun ResolveUnknownScreen(vm: BreakfastViewModel) {
    val colors = LocalReceiptColors.current
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
    Surface(Modifier.fillMaxSize(), color = colors.paper) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState())) {
            ReceiptTopBar("Resolve", vm::closeResolver, Modifier.padding(horizontal = 4.dp), subtitle = subtitle)
            Column(
                Modifier
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp)
                    .fillMaxWidth()
                    .background(colors.paper, ReceiptStickerShape)
                    .drawBehind {
                        drawRect(colors.ink, style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))))
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .semantics(mergeDescendants = true) { },
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    if (display.quantity != null) Text(display.quantity, color = colors.ink, fontWeight = FontWeight.Bold) else Spacer(Modifier.size(1.dp))
                    Text("$position of $total", color = colors.soft, style = MaterialTheme.typography.bodySmall)
                }
                Text(receiptFoodName(display.name), color = colors.ink, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            }
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp)) {
                if (vm.resolverSearchOpen) {
                    ReceiptSearchPill(vm.resolverQuery, vm::onResolverQuery, onClear = { vm.onResolverQuery("") }, label = "Search your foods", modifier = Modifier.padding(bottom = 16.dp))
                } else {
                    TertiaryLink("Search your foods", vm::beginResolverSearch, Modifier.padding(bottom = 16.dp))
                }
                if (vm.resolverResults.isNotEmpty()) {
                CharacterRule('-')
                Text("LOCAL MATCHES", color = colors.soft, fontWeight = FontWeight.Bold)
                CharacterRule('-')
                vm.resolverResults.forEach { ranked ->
                            val match = matchDisplay(ranked.food, raw)
                            ItemRow(
                                name = ranked.food.name,
                                quantityLine = match.detail,
                                approximate = match.approximateLabel,
                                calories = match.kcal,
                                source = sourceLetter(ranked.food),
                                onClick = { vm.resolveUnknown(ranked.food) },
                            )
                        }
                }
                Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReceiptOutlinedButton("Search Open Food Facts", { vm.openSearch(vm.currentUnknownName(), fromResolver = true) })
                    ReceiptOutlinedButton("Create personal food", { vm.openCustom(vm.currentUnknownName(), fromResolver = true) })
                    TertiaryLink("Skip", vm::skipCurrentUnknown)
                }
            }
        }
    }
}
