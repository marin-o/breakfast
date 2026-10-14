package com.iron.breakfast.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import com.iron.breakfast.BreakfastViewModel
import com.iron.breakfast.ContributionAction
import com.iron.breakfast.QuickAddPreview
import com.iron.breakfast.macro
import com.iron.breakfast.pretty
import com.iron.breakfast.label
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.ui.components.ReceiptBottomTray
import com.iron.breakfast.ui.components.ReceiptChoiceChip
import com.iron.breakfast.ui.components.ReceiptEntryRow
import com.iron.breakfast.ui.components.ReceiptNavigationBar
import com.iron.breakfast.ui.components.ReceiptQuickAddField
import com.iron.breakfast.ui.components.ReceiptSlip
import com.iron.breakfast.ui.components.ReceiptSnackbar
import com.iron.breakfast.ui.components.ReceiptSticker
import com.iron.breakfast.ui.components.ReceiptTonalChip
import com.iron.breakfast.ui.components.ReceiptTicket
import com.iron.breakfast.ui.components.ReceiptTokenVisualTransformation
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.ui.theme.Ground
import com.iron.breakfast.ui.theme.Ink
import com.iron.breakfast.ui.theme.OnGround
import com.iron.breakfast.ui.theme.OnGroundSoft
import com.iron.breakfast.ui.theme.PaperFresh
import com.iron.breakfast.ui.theme.Tray
import com.iron.breakfast.ui.theme.doubleWidth
import com.iron.breakfast.ui.theme.usesSingleWidthAt
import com.iron.breakfast.ui.theme.PenTonalInk
import com.iron.breakfast.ui.theme.PenOnDarkInk
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun DailyScreen(vm: BreakfastViewModel, modifier: Modifier) {
    if (vm.input.isNotBlank()) {
        QuickAddDaily(vm, modifier)
        return
    }
    val selected = vm.entries.filter { it.dayKey == vm.selectedDayKey }
    val foods = vm.foods.associateBy { it.id }
    val dailyTotal = selected.fold(NutritionValues.ZERO) { total, entry -> total + entry.nutritionSnapshot }
    val (dayName, subtitle) = dayTitle(vm.selectedDayKey)
    val isToday = vm.selectedDayKey == dayKeyForToday()
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val swipeThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 96.dp.toPx() }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(Ground).pointerInput(vm.selectedDayKey) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var dx = 0f
                var dy = 0f
                var horizontal = false
                var vertical = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.shiftSelectedDay(-1) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous day")
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        val headingStyle = if (usesSingleWidthAt(androidx.compose.ui.platform.LocalDensity.current.fontScale)) MaterialTheme.typography.headlineMedium else doubleWidth(MaterialTheme.typography.headlineMedium)
                        Text(dayName, style = headingStyle, color = OnGround)
                        Text(subtitle, style = MaterialTheme.typography.titleSmall, color = OnGroundSoft)
                    }
                    IconButton(onClick = { vm.shiftSelectedDay(1) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next day")
                    }
                }
                if (!isToday) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = vm::goToday) { Text("Today", color = PenOnDarkInk, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
                    }
                }
                ReceiptSlip(
                    label = if (isToday) "Today" else shortDateLabel(vm.selectedDayKey),
                    total = receiptKcal(dailyTotal.calories),
                    macros = listOf(
                        "P ${dailyTotal.protein.macro()}",
                        "C ${dailyTotal.carbohydrates.macro()}",
                        "F ${dailyTotal.fat.macro()}",
                    ),
                )
            }
        }
        vm.mealDraftError?.let { message -> item { Text(message, color = OnGround) } }
        itemsIndexed(MealSlot.entries) { _, slot ->
            val entries = selected.filter { it.mealSlot == slot }
            MealCard(slot, entries, foods, vm)
        }
        vm.pendingContribution?.let { item { ContributionAction(vm) } }
    }
}

@Composable
private fun QuickAddDaily(vm: BreakfastViewModel, modifier: Modifier) {
    val selected = vm.entries.filter { it.dayKey == vm.selectedDayKey }
    val foods = vm.foods.associateBy { it.id }
    val target = vm.quickAddTargetMeal() ?: MealSlot.LUNCH
    val existing = selected.filter { it.mealSlot == target }
    val previews = vm.quickAddPreviews()
    val total = (existing.fold(NutritionValues.ZERO) { sum, entry -> sum + entry.nutritionSnapshot } +
        previews.fold(NutritionValues.ZERO) { sum, preview -> sum + preview.calculation.nutrition })
    Column(modifier.fillMaxSize().background(Ground).padding(horizontal = 14.dp)) {
        ReceiptSlip(label = shortDateLabel(vm.selectedDayKey), total = receiptKcal(total.calories))
        ReceiptTicket(mealName = target.label(), total = receiptKcal(total.calories), time = earliestMealTime(existing)) {
            existing.forEachIndexed { index, entry ->
                val quantity = quantityRateDisplay(entry, foods[entry.foodId])
                ReceiptEntryRow(name = entry.foodName, quantityLine = quantity.line, calories = receiptKcal(entry.nutritionSnapshot.calories), source = sourceLetter(foods[entry.foodId]), approximate = quantity.approximateLabel, first = index == 0)
            }
            previews.forEachIndexed { index, preview ->
                val offset = existing.size + index
                ReceiptEntryRow(name = preview.food.name, quantityLine = "${preview.amount.pretty()} ${preview.unit.displayLabel()}", calories = receiptKcal(preview.calculation.nutrition.calories), source = sourceLetter(preview.food), first = offset == 0)
            }
        }
    }
}

@Composable
internal fun DailyComposer(vm: BreakfastViewModel) {
    val previews = vm.quickAddPreviews()
    val suggestions = vm.quickAddSuggestions()
    val placeholder = if (vm.selectedDayKey == dayKeyForToday()) "2 eggs, 1 banana" else "Add to ${shortDateLabel(vm.selectedDayKey)}"
    Column(Modifier.fillMaxWidth().background(Tray)) {
        if (vm.loggedBatchMessage != null) vm.loggedBatchMessage?.let { message ->
            ReceiptSnackbar(
                message = message,
                actionLabel = "Undo",
                onAction = vm::undoLoggedBatch,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        } else if (vm.feedbackCount() > 0) {
            ReceiptSnackbar(
                message = "${vm.feedbackCount()} food${if (vm.feedbackCount() == 1) "" else "s"} logged",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        vm.undoEntry?.let { deleted ->
            ReceiptSnackbar(
                message = "${deleted.foodName} deleted",
                actionLabel = "Undo",
                onAction = vm::undo,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        ReceiptBottomTray(
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (previews.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            previews.forEach { preview -> PendingSticker(preview) }
                        }
                    }
                    if (suggestions.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            suggestions.forEachIndexed { index, ranked ->
                                val food = ranked.food
                                if (index == 0) {
                                    ReceiptTonalChip(
                                        label = vm.quickAddSuggestionLabel(food),
                                        onClick = { vm.chooseQuickSuggestion(food) },
                                    )
                                } else {
                                    ReceiptChoiceChip(
                                        label = food.name,
                                        selected = false,
                                        onClick = { vm.chooseQuickSuggestion(food) },
                                    )
                                }
                            }
                        }
                    }
                    ReceiptQuickAddField(
                        value = vm.input,
                        onValueChange = vm::onInput,
                        onLog = vm::submit,
                        onImeDone = vm::submit,
                        placeholder = placeholder,
                        expanded = vm.input.contains(',') || vm.input.length > 28,
                        visualTransformation = ReceiptTokenVisualTransformation(tokenRangesForInput(vm.input)),
                    )
                }
            },
            nav = {
                if (vm.input.isBlank()) {
                ReceiptNavigationBar(
                    selectedDaily = true,
                    onDaily = { vm.closeMeal(); vm.closeLibrary() },
                    onLibrary = { vm.closeMeal(); vm.openLibrary() },
                )
                }
            },
        )
    }
}

@Composable
private fun PendingSticker(preview: QuickAddPreview) {
    val baseUnit = preview.food.nutrition.unit
    ReceiptSticker(
        name = preview.food.name,
        value = receiptKcal(preview.calculation.nutrition.calories),
        detail = "${preview.amount.pretty()} ${preview.unit.displayLabel()}",
        fields = listOf("${preview.calculation.resolvedBaseAmount.pretty()} ${baseUnit.displayLabel()}", sourceLetter(preview.food)),
        approximateDetail = preview.calculation.approximate,
    )
}

@Composable
private fun MealCard(
    slot: MealSlot,
    entries: List<MealEntry>,
    foods: Map<String, FoodRecord>,
    vm: BreakfastViewModel,
) {
    val total = entries.fold(NutritionValues.ZERO) { a, e -> a + e.nutritionSnapshot }
    ReceiptTicket(
        mealName = slot.label(),
        total = receiptKcal(total.calories),
        time = earliestMealTime(entries),
        addFoodLabel = "Add food",
        onAddFood = { vm.openFocusedLibrary(slot) },
        onMealClick = { vm.openMeal(slot) },
    ) {
        entries.forEachIndexed { index, entry ->
            val food = foods[entry.foodId]
            val quantity = quantityRateDisplay(entry, food)
            ReceiptEntryRow(
                name = entry.foodName,
                quantityLine = quantity.line,
                approximate = quantity.approximateLabel,
                calories = receiptKcal(entry.nutritionSnapshot.calories),
                source = sourceLetter(food),
                fresh = vm.feedbackOpen(entry),
                first = index == 0,
                onClick = { vm.openEntryDetail(entry) },
            )
            if (vm.feedbackOpen(entry)) CorrectionStrip(vm, entry)
        }
    }
}

@Composable
private fun CorrectionStrip(vm: BreakfastViewModel, entry: MealEntry) {
    Row(
        Modifier.fillMaxWidth().background(PaperFresh).horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReceiptTonalChip("Confirm", onClick = { vm.confirm(entry) })
        vm.alternatives(entry).forEach { alternative ->
            ReceiptTonalChip(alternative.food.name, onClick = { vm.chooseAlternative(entry, alternative.food) })
        }
        ReceiptTonalChip("Search", onClick = { vm.openSearch(entry.foodName) }, leadingIcon = androidx.compose.material.icons.Icons.Default.Search)
    }
}

private fun dayKeyForToday(): String = com.iron.breakfast.domain.dayKeyForEpochMs(System.currentTimeMillis())

private fun shortDateLabel(dayKey: String): String = runCatching {
    java.time.LocalDate.parse(dayKey).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
}.getOrDefault(dayKey)
