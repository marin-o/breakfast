package com.iron.breakfast.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iron.breakfast.BreakfastViewModel
import com.iron.breakfast.ContributionAction
import com.iron.breakfast.EntryEditMode
import com.iron.breakfast.QuickAddPreview
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.label
import com.iron.breakfast.macro
import com.iron.breakfast.pretty
import com.iron.breakfast.ui.components.CharacterRule
import com.iron.breakfast.ui.components.ReceiptBottomTray
import com.iron.breakfast.ui.components.ReceiptChoiceChip
import com.iron.breakfast.ui.components.ReceiptEntryRow
import com.iron.breakfast.ui.components.ReceiptNavigationBar
import com.iron.breakfast.ui.components.ReceiptOutlinedButton
import com.iron.breakfast.ui.components.ReceiptQuickAddField
import com.iron.breakfast.ui.components.ReceiptSnackbar
import com.iron.breakfast.ui.components.ReceiptSticker
import com.iron.breakfast.ui.components.ReceiptTokenVisualTransformation
import com.iron.breakfast.ui.components.TotalBlock
import com.iron.breakfast.ui.theme.LocalReceiptColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun DailyScreen(vm: BreakfastViewModel, modifier: Modifier) {
    if (vm.input.isNotBlank() && vm.entryEditMode == EntryEditMode.NONE) {
        QuickAddDaily(vm, modifier)
        return
    }
    val selected = vm.entries.filter { it.dayKey == vm.selectedDayKey }
    val foods = vm.foods.associateBy { it.id }
    val total = selected.fold(NutritionValues.ZERO) { sum, entry -> sum + entry.nutritionSnapshot }
    val (dayName, date) = dayTitle(vm.selectedDayKey)
    val isToday = vm.selectedDayKey == dayKeyForToday()
    val colors = LocalReceiptColors.current
    val fontScale = LocalDensity.current.fontScale
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val swipeThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 96.dp.toPx() }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.paper).pointerInput(vm.selectedDayKey) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var dx = 0f
                var dy = 0f
                var horizontal = false
                var vertical = false
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    dx += change.position.x - change.previousPosition.x
                    dy += change.position.y - change.previousPosition.y
                    if (!horizontal && !vertical && kotlin.math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        horizontal = abs(dx) > abs(dy)
                        vertical = !horizontal
                    }
                    if (horizontal) change.consume()
                    if (!change.pressed) break
                }
                if (horizontal && abs(dx) >= swipeThreshold) vm.shiftSelectedDay(if (dx < 0f) 1 else -1)
            }
        },
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shiftSelectedDay(-1) }) { Icon(Icons.Default.ChevronLeft, "Previous day", tint = colors.ink) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        dailyHeaderTitle(dayName, fontScale),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.ink,
                        textAlign = TextAlign.Center,
                    )
                    Text(date, style = MaterialTheme.typography.bodyLarge, color = colors.soft)
                    Text("Receipt ${receiptNumber(vm.selectedDayKey)}", color = colors.soft, style = MaterialTheme.typography.bodyLarge)
                }
                IconButton(onClick = { vm.shiftSelectedDay(1) }) { Icon(Icons.Default.ChevronRight, "Next day", tint = colors.ink) }
            }
            if (!isToday) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = vm::goToday) { Text("TODAY", color = colors.ink, textDecoration = TextDecoration.Underline) }
                }
            }
            CharacterRule('-', modifier = Modifier.padding(top = 4.dp))
            TotalBlock(total = receiptKcal(total.calories))
            MacroTable(total, fontScale)
            CharacterRule('=', modifier = Modifier.padding(top = 8.dp))
        }
        vm.mealDraftError?.let { message -> item { Text(message, color = colors.red, modifier = Modifier.padding(vertical = 6.dp)) } }
        items(MealSlot.entries) { slot ->
            DailyMealSection(slot, selected.filter { it.mealSlot == slot }, foods, vm, fontScale)
        }
        item {
            DailyFooter(selected, foods, vm.selectedDayKey)
            vm.pendingContribution?.let { ContributionAction(vm) }
        }
    }
}

@Composable
private fun MacroTable(total: NutritionValues, fontScale: Float) {
    val colors = LocalReceiptColors.current
    val continuationIndent = with(LocalDensity.current) { 36.sp.toPx().toDp() }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        val macros = listOf(
            "Protein" to (total.protein to total.protein?.times(4.0)),
            "Carbs" to (total.carbohydrates to total.carbohydrates?.times(4.0)),
            "Fat" to (total.fat to total.fat?.times(9.0)),
        )
        if (dailyMacroUsesTwoLines(fontScale)) {
            macros.forEach { (label, values) ->
                Text(
                    "$label ${values.first?.macro()?.removeSuffix(" g") ?: "0"} g",
                    color = colors.ink,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${receiptKcal(values.second ?: 0.0)} kcal",
                    color = colors.soft,
                    modifier = Modifier.padding(start = continuationIndent),
                )
            }
        } else {
            val gramsWidth = 54.dp * fontScale.coerceAtLeast(1f)
            val kcalWidth = 58.dp * fontScale.coerceAtLeast(1f)
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Text("g", color = colors.soft, textAlign = TextAlign.End, modifier = Modifier.width(gramsWidth))
                Text("kcal", color = colors.soft, textAlign = TextAlign.End, modifier = Modifier.width(kcalWidth))
            }
            macros.forEach { (label, values) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(label, color = colors.ink, modifier = Modifier.weight(1f))
                    Text(values.first?.macro()?.removeSuffix(" g") ?: "0", color = colors.ink, textAlign = TextAlign.End, modifier = Modifier.width(gramsWidth))
                    Text(receiptKcal(values.second ?: 0.0), color = colors.ink, textAlign = TextAlign.End, modifier = Modifier.width(kcalWidth))
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DailyMealSection(slot: MealSlot, entries: List<MealEntry>, foods: Map<String, FoodRecord>, vm: BreakfastViewModel, fontScale: Float) {
    val colors = LocalReceiptColors.current
    val total = entries.fold(NutritionValues.ZERO) { sum, entry -> sum + entry.nutritionSnapshot }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) { vm.openMeal(slot) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(slot.name.uppercase(Locale.ENGLISH), style = MaterialTheme.typography.headlineMedium, color = colors.red, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (!dailyMealTimeOwnLine(fontScale)) Text(earliestMealTime(entries).orEmpty(), color = colors.soft, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, end = 8.dp))
                Text(receiptKcal(total.calories), style = MaterialTheme.typography.titleLarge, color = colors.red, fontWeight = FontWeight.Bold)
            }
            if (dailyMealTimeOwnLine(fontScale) && earliestMealTime(entries) != null) {
                Text(earliestMealTime(entries).orEmpty(), color = colors.soft, style = MaterialTheme.typography.bodySmall)
            }
        }
        entries.forEach { entry ->
            val quantity = quantityRateDisplay(entry, foods[entry.foodId], includeRateUnit = false, asciiMultiply = true)
            ReceiptEntryRow(
                name = entry.foodName,
                quantityLine = quantity.line,
                approximate = quantity.approximateLabel,
                calories = receiptKcal(entry.nutritionSnapshot.calories),
                source = sourceLetter(foods[entry.foodId]),
                fresh = vm.feedbackOpen(entry),
                first = true,
                showRule = false,
                modifier = Modifier.semantics {
                    onClick(label = "Open details for ${entry.foodName}") { vm.openEntryDetail(entry); true }
                    customActions = listOf(
                        CustomAccessibilityAction("Quick edit amount") { vm.beginQuickEdit(entry); true },
                        CustomAccessibilityAction("Edit food and amount") { vm.beginFullEdit(entry); true },
                        CustomAccessibilityAction("Delete ${entry.foodName}") { vm.delete(entry); true },
                    )
                }.combinedClickable(onClick = { vm.openEntryDetail(entry) }, onDoubleClick = { vm.beginQuickEdit(entry) }, onLongClick = { vm.beginFullEdit(entry) }),
            )
            if (vm.feedbackOpen(entry)) CorrectionStrip(vm, entry)
        }
        // Keep clear space around the label as the system font grows. Meal sections
        // stack vertically, so the scaled target remains within the 360dp canvas.
        ReceiptOutlinedButton(
            "+ Add food",
            onClick = { vm.openFocusedLibrary(slot) },
            fillWidth = false,
            modifier = Modifier.widthIn(min = 128.dp * fontScale.coerceAtLeast(1f)).padding(top = 4.dp, bottom = 8.dp),
        )
        CharacterRule('-', modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun DailyFooter(entries: List<MealEntry>, foods: Map<String, FoodRecord>, dayKey: String) {
    val colors = LocalReceiptColors.current
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val kcalWidth = 54.dp * fontScale
    val sourceWidth = 20.dp * fontScale
    val sourceTotals = listOf("B" to "Bundled", "P" to "Personal", "O" to "Open Food Facts")
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("ITEMS", color = colors.soft, modifier = Modifier.weight(1f))
            Text(entries.size.toString(), color = colors.soft, textAlign = TextAlign.End, modifier = Modifier.width(kcalWidth))
            Spacer(Modifier.width(sourceWidth))
        }
        sourceTotals.forEach { (letter, label) ->
            val sourceEntries = entries.filter { sourceLetter(foods[it.foodId]) == letter }
            Row(Modifier.fillMaxWidth()) {
                Text(label, color = colors.soft, modifier = Modifier.weight(1f))
                Text(receiptKcal(sourceEntries.sumOf { it.nutritionSnapshot.calories }), color = colors.soft, textAlign = TextAlign.End, modifier = Modifier.width(kcalWidth))
                Text(letter, color = colors.soft, textAlign = TextAlign.End, modifier = Modifier.width(sourceWidth))
            }
        }
        CharacterRule('=', modifier = Modifier.padding(top = 8.dp))
        Text(buildString { append(receiptDate(dayKey)); receiptDateTimeRange(entries)?.let { append("  ").append(it) } }, color = colors.soft, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center)
        if (entries.isNotEmpty()) Text("Receipt ${receiptNumber(dayKey)} of ${dayKey.take(4)}", color = colors.soft, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun QuickAddDaily(vm: BreakfastViewModel, modifier: Modifier) {
    val colors = LocalReceiptColors.current
    val selected = vm.entries.filter { it.dayKey == vm.selectedDayKey }
    val foods = vm.foods.associateBy { it.id }
    val target = vm.quickAddTargetMeal() ?: MealSlot.LUNCH
    val existing = selected.filter { it.mealSlot == target }
    val previews = vm.quickAddPreviews()
    val total = selected.fold(NutritionValues.ZERO) { sum, entry -> sum + entry.nutritionSnapshot }
    val mealTotal = existing.fold(NutritionValues.ZERO) { sum, entry -> sum + entry.nutritionSnapshot }
    val pendingKcal = previews.sumOf { it.calculation.nutrition.calories }
    Column(modifier.fillMaxSize().background(colors.paper).padding(horizontal = 14.dp)) {
        TotalBlock(receiptKcal(total.calories), if (previews.isEmpty()) null else "+${receiptKcal(pendingKcal)}", Modifier.padding(top = 8.dp))
        CharacterRule('-', modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
            Text(target.name.uppercase(Locale.ENGLISH), style = MaterialTheme.typography.headlineMedium, color = colors.red, fontWeight = FontWeight.Bold)
            Text(earliestMealTime(existing).orEmpty(), color = colors.soft, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp).weight(1f))
            Text(receiptKcal(mealTotal.calories), style = MaterialTheme.typography.titleLarge, color = colors.red, fontWeight = FontWeight.Bold)
        }
        existing.forEach { entry ->
            val quantity = quantityRateDisplay(entry, foods[entry.foodId], includeRateUnit = false, asciiMultiply = true)
            ReceiptEntryRow(entry.foodName, quantity.line, receiptKcal(entry.nutritionSnapshot.calories), sourceLetter(foods[entry.foodId]), quantity.approximateLabel, first = true, showRule = false)
        }
        CharacterRule('=', modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
internal fun DailyComposer(vm: BreakfastViewModel) {
    val colors = LocalReceiptColors.current
    val previews = vm.quickAddPreviews()
    val suggestions = vm.quickAddSuggestions()
    val placeholder = if (vm.selectedDayKey == dayKeyForToday()) "2 eggs, 1 banana" else "Add to ${shortDateLabel(vm.selectedDayKey)}"
    Column(Modifier.fillMaxWidth().background(colors.paper).navigationBarsPadding()) {
        vm.loggedBatchMessage?.let { ReceiptSnackbar(it, "Undo", vm::undoLoggedBatch, Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) }
        vm.undoEntry?.let { ReceiptSnackbar("${it.foodName} deleted", "Undo", vm::undo, Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) }
        ReceiptBottomTray(
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (previews.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { previews.forEach { PendingSticker(it) } }
                    if (suggestions.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { suggestions.forEachIndexed { index, ranked -> ReceiptChoiceChip(vm.quickAddSuggestionLabel(ranked.food), index == 0, { vm.chooseQuickSuggestion(ranked.food) }, Modifier.width(200.dp)) } }
                    ReceiptQuickAddField(vm.input, vm::onInput, vm::submit, placeholder, vm.input.contains(',') || vm.input.length > 28, ReceiptTokenVisualTransformation(tokenRangesForInput(vm.input, vm::isRecognizedFood), colors.redFill, colors.onRedFill), vm::submit)
                }
            },
            nav = { if (vm.input.isBlank()) ReceiptNavigationBar(true, { vm.closeMeal(); vm.closeLibrary() }, { vm.closeMeal(); vm.openLibrary() }) },
        )
    }
}

@Composable
private fun PendingSticker(preview: QuickAddPreview) {
    ReceiptSticker(preview.food.name, receiptKcal(preview.calculation.nutrition.calories), "${preview.amount.pretty()} ${preview.unit.displayLabel()}", listOf("${preview.calculation.resolvedBaseAmount.pretty()} ${preview.food.nutrition.unit.displayLabel()}", sourceLetter(preview.food)), preview.calculation.approximate)
}

@Composable
private fun CorrectionStrip(vm: BreakfastViewModel, entry: MealEntry) {
    val colors = LocalReceiptColors.current
    Row(Modifier.fillMaxWidth().background(colors.paper).horizontalScroll(rememberScrollState()).padding(start = 14.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReceiptChoiceChip("Confirm", true, { vm.confirm(entry) }, Modifier.width(150.dp))
        vm.alternatives(entry).forEach { alternative -> ReceiptChoiceChip(alternative.food.name, false, { vm.chooseAlternative(entry, alternative.food) }, Modifier.width(200.dp)) }
        ReceiptChoiceChip("Search", false, { vm.openSearch(entry.foodName) }, Modifier.width(150.dp))
    }
}

private fun dayKeyForToday(): String = com.iron.breakfast.domain.dayKeyForEpochMs(System.currentTimeMillis())

private fun shortDateLabel(dayKey: String): String = runCatching { LocalDate.parse(dayKey).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)) }.getOrDefault(dayKey)

internal fun dailyHeaderTitle(dayName: String, fontScale: Float): String =
    if (fontScale >= 1.8f) dayName.take(3).uppercase(Locale.ENGLISH) else dayName.uppercase(Locale.ENGLISH)

internal fun dailyMealTimeOwnLine(fontScale: Float): Boolean = fontScale >= 1.25f

internal fun dailyMacroUsesTwoLines(fontScale: Float): Boolean = fontScale >= 1.8f
