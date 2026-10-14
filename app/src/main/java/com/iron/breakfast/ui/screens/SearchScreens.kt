package com.iron.breakfast.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iron.breakfast.BreakfastViewModel
import com.iron.breakfast.label
import com.iron.breakfast.resolvePosition
import com.iron.breakfast.ui.components.ReceiptEntryRow
import com.iron.breakfast.ui.components.ReceiptOutlinedButton
import com.iron.breakfast.ui.components.ReceiptPaperTicket
import com.iron.breakfast.ui.components.ReceiptPlainSlip
import com.iron.breakfast.ui.components.ReceiptPrimaryButton
import com.iron.breakfast.ui.components.ReceiptSearchPill
import com.iron.breakfast.ui.components.ReceiptSubhead
import com.iron.breakfast.ui.components.ReceiptTag
import com.iron.breakfast.ui.components.ReceiptTonalButton
import com.iron.breakfast.ui.components.ReceiptTopBar
import com.iron.breakfast.ui.theme.Ground
import com.iron.breakfast.ui.theme.Ink
import com.iron.breakfast.ui.theme.InkSoft
import com.iron.breakfast.ui.theme.OnGround
import com.iron.breakfast.ui.theme.OnGroundSoft
import com.iron.breakfast.ui.theme.PenOnDarkInk
import com.iron.breakfast.ui.theme.PrintRed
import com.iron.breakfast.ui.theme.ReceiptPillShape
import com.iron.breakfast.ui.theme.ReceiptStickerShape
import com.iron.breakfast.ui.theme.Sticker

/** Local search that found nothing: the way on is Open Food Facts or a personal food. */
@Composable internal fun LibraryNoMatchScreen(vm: BreakfastViewModel) {
    val query = vm.libraryQuery
    val clear = { vm.onInput(""); vm.onLibraryQuery("") }
    BackHandler(onBack = clear)
    Surface(Modifier.fillMaxSize(), color = Ground) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = clear, Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnGround) }
                ReceiptSearchPill(query, vm::onLibraryQuery, onClear = clear, label = "Search library", modifier = Modifier.weight(1f))
            }
            ReceiptPlainSlip(Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp)) {
                Text("No local match for $query", color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(16.dp))
                ReceiptPrimaryButton("Search Open Food Facts", { vm.openSearch(query.trim()) })
                Spacer(Modifier.height(10.dp))
                ReceiptTonalButton("Create personal food", { vm.openCustom(query.trim()) })
            }
        }
    }
}

/** Explicit Open Food Facts search: nothing is sent until Search (or the keyboard's search key) is used. */
@Composable internal fun OffSearchScreen(vm: BreakfastViewModel) {
    BackHandler(onBack = vm::closeSearch)
    Surface(Modifier.fillMaxSize(), color = Ground) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState())) {
            ReceiptTopBar("Open Food Facts", vm::closeSearch, Modifier.padding(horizontal = 4.dp))
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReceiptSearchPill(vm.searchQuery, vm::onSearch, onClear = { vm.onSearch("") }, label = "Search Open Food Facts", modifier = Modifier.weight(1f), onSearch = vm::submitSearch)
                // Loading keeps its existing form until a dedicated design arrives: the button reads "Searching…".
                ReceiptPrimaryButton(if (vm.searchBusy) "Searching…" else "Search", vm::submitSearch, enabled = !vm.searchBusy, fillWidth = false)
            }
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (vm.offResults.isNotEmpty()) {
                    ReceiptPaperTicket {
                        ReceiptSubhead(if (vm.offResults.size == 1) "1 result" else "${vm.offResults.size} results")
                        vm.offResults.forEach { food ->
                            ReceiptEntryRow(
                                name = food.name,
                                quantityLine = food.brand.orEmpty(),
                                calories = receiptKcal(food.nutrition.values.calories),
                                calorieCaption = basisCaption(food.nutrition.amount, food.nutrition.unit),
                                source = sourceLetter(food),
                                onClick = { vm.chooseProduct(food) },
                            )
                        }
                    }
                }
                vm.offSearchMessage?.let { message ->
                    if (vm.offSearchUnavailable) {
                        ReceiptPlainSlip {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.WifiOff, contentDescription = null, tint = PrintRed, modifier = Modifier.size(32.dp))
                                Column {
                                    Text("No connection", color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                                    Text("Open Food Facts needs internet. Local foods still work.", color = InkSoft, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                        ReceiptOutlinedButton("Retry", vm::submitSearch)
                        ReceiptTonalButton("Create personal food", { vm.openCustom(vm.searchQuery.trim(), fromResolver = vm.resolutionContinuation) })
                    } else if (message.startsWith("No usable result")) {
                        ReceiptPlainSlip {
                            Text(message, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                            Spacer(Modifier.height(16.dp))
                            ReceiptTonalButton("Create personal food", { vm.openCustom(vm.searchQuery.trim(), fromResolver = vm.resolutionContinuation) })
                        }
                    } else {
                        ReceiptPlainSlip { Text(message, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() }) }
                        ReceiptOutlinedButton("Retry", vm::submitSearch)
                    }
                }
            }
        }
    }
}

/** The unknown-food resolution queue: the kept quantity stays on the sticker; each match logs it for the selected day and meal. */
@Composable internal fun ResolveUnknownScreen(vm: BreakfastViewModel) {
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
    Surface(Modifier.fillMaxSize(), color = Ground) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState())) {
            ReceiptTopBar("Resolve", vm::closeResolver, Modifier.padding(horizontal = 4.dp), subtitle = subtitle)
            Column(
                Modifier
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp)
                    .fillMaxWidth()
                    .shadow(1.dp, ReceiptStickerShape)
                    .background(Sticker, ReceiptStickerShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .semantics(mergeDescendants = true) { },
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    if (display.quantity != null) ReceiptTag(display.quantity) else Spacer(Modifier.size(1.dp))
                    Text("$position of $total", color = InkSoft, style = MaterialTheme.typography.bodySmall)
                }
                Text(display.name, color = Ink, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            }
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp)) {
                ReceiptSearchPill(vm.resolverQuery, vm::onResolverQuery, onClear = { vm.onResolverQuery("") }, label = "Search your foods", modifier = Modifier.padding(bottom = 16.dp))
                if (vm.resolverResults.isNotEmpty()) {
                    ReceiptPaperTicket {
                        ReceiptSubhead("Local matches")
                        vm.resolverResults.forEach { ranked ->
                            val match = matchDisplay(ranked.food, raw)
                            ReceiptEntryRow(
                                name = ranked.food.name,
                                quantityLine = match.detail,
                                approximate = match.approximateLabel,
                                calories = match.kcal,
                                source = sourceLetter(ranked.food),
                                onClick = { vm.resolveUnknown(ranked.food) },
                            )
                        }
                    }
                }
                Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReceiptTonalButton("Search Open Food Facts", { vm.openSearch(vm.currentUnknownName(), fromResolver = true) })
                    ReceiptOutlinedButton("Create personal food", { vm.openCustom(vm.currentUnknownName(), fromResolver = true) })
                    TextButton(onClick = vm::skipCurrentUnknown, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Skip", color = PenOnDarkInk) }
                }
            }
        }
    }
}
