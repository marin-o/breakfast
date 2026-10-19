package com.iron.breakfast.ui.screens

import com.iron.breakfast.ui.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
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
import com.iron.breakfast.ui.components.ChoiceOption
import com.iron.breakfast.ui.components.DayHeader
import com.iron.breakfast.ui.components.DayTotal
import com.iron.breakfast.ui.components.Field
import com.iron.breakfast.ui.components.FoodRow
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.MacroStat
import com.iron.breakfast.ui.components.MealHeader
import com.iron.breakfast.ui.components.Snackbar
import com.iron.breakfast.ui.components.BottomNav
import com.iron.breakfast.ui.components.SectionHeading
import com.iron.breakfast.ui.theme.LocalBreakfastColors
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
    val colors = LocalBreakfastColors.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val swipeThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 96.dp.toPx() }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.bg).pointerInput(vm.selectedDayKey) {
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
        contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 28.dp),
    ) {
        item {
            DayHeader(
                title = "$dayName $date",
                onPrevious = { vm.shiftSelectedDay(-1) },
                onNext = { vm.shiftSelectedDay(1) },
                modifier = Modifier.padding(top = 8.dp),
                showToday = !isToday,
                onToday = vm::goToday,
            )
            DayTotal(receiptKcal(total.calories), dailyMacroStats(total), Modifier.padding(top = 18.dp))
        }
        vm.mealDraftError?.let { message -> item { Text(message, color = colors.bad, style = BreakfastType.Meta, modifier = Modifier.padding(vertical = 6.dp)) } }
        items(MealSlot.entries) { slot ->
            DailyMealSection(slot, selected.filter { it.mealSlot == slot }, foods, vm)
        }
        item {
            vm.pendingContribution?.let { ContributionAction(vm) }
        }
    }
}

/** Macro columns for the day total. Each bar is the macro's share of the day's kcal. */
private fun dailyMacroStats(total: NutritionValues): List<MacroStat> {
    val kcal = total.calories
    fun share(kcalOfMacro: Double?): Float = if (kcal > 0.0 && kcalOfMacro != null) (kcalOfMacro / kcal).toFloat() else 0f
    return listOf(
        MacroStat("Protein", total.protein?.macro()?.removeSuffix(" g") ?: "0", "g", share(total.protein?.times(4.0))),
        MacroStat("Carbs", total.carbohydrates?.macro()?.removeSuffix(" g") ?: "0", "g", share(total.carbohydrates?.times(4.0))),
        MacroStat("Fat", total.fat?.macro()?.removeSuffix(" g") ?: "0", "g", share(total.fat?.times(9.0))),
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DailyMealSection(slot: MealSlot, entries: List<MealEntry>, foods: Map<String, FoodRecord>, vm: BreakfastViewModel) {
    val colors = LocalBreakfastColors.current
    val total = entries.fold(NutritionValues.ZERO) { sum, entry -> sum + entry.nutritionSnapshot }
    MealHeader(
        name = slot.label(),
        total = receiptKcal(total.calories),
        onAdd = { vm.openFocusedLibrary(slot) },
        addDescription = "Add food to ${slot.label()}",
        time = earliestMealTime(entries),
        emptyText = if (entries.isEmpty()) "Nothing yet" else null,
        modifier = Modifier.clickable(role = Role.Button) { vm.openMeal(slot) },
    )
    entries.forEach { entry ->
        val quantity = quantityRateDisplay(entry, foods[entry.foodId], includeRateUnit = false, asciiMultiply = true)
        FoodRow(
            name = entry.foodName,
            quantityLine = quantity.plainLine(),
            calories = receiptKcal(entry.nutritionSnapshot.calories),
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
}

@Composable
private fun QuickAddDaily(vm: BreakfastViewModel, modifier: Modifier) {
    val colors = LocalBreakfastColors.current
    val selected = vm.entries.filter { it.dayKey == vm.selectedDayKey }
    val foods = vm.foods.associateBy { it.id }
    val target = vm.quickAddTargetMeal() ?: MealSlot.LUNCH
    val existing = selected.filter { it.mealSlot == target }
    val total = selected.fold(NutritionValues.ZERO) { sum, entry -> sum + entry.nutritionSnapshot }
    val mealTotal = existing.fold(NutritionValues.ZERO) { sum, entry -> sum + entry.nutritionSnapshot }
    Column(modifier.fillMaxSize().background(colors.bg).padding(horizontal = Gutter)) {
        DayTotal(receiptKcal(total.calories), dailyMacroStats(total), Modifier.padding(top = 8.dp))
        Hairline(Modifier.padding(top = 24.dp))
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Bottom) {
            SectionHeading(target.label(), Modifier.weight(1f))
            Text(receiptKcal(mealTotal.calories), color = colors.ink, style = BreakfastType.Title)
        }
        existing.forEach { entry ->
            val quantity = quantityRateDisplay(entry, foods[entry.foodId], includeRateUnit = false, asciiMultiply = true)
            FoodRow(entry.foodName, quantity.plainLine(), receiptKcal(entry.nutritionSnapshot.calories))
        }
    }
}

@Composable
internal fun DailyComposer(vm: BreakfastViewModel) {
    val colors = LocalBreakfastColors.current
    val previews = vm.quickAddPreviews()
    val suggestions = vm.quickAddSuggestions()
    val placeholder = if (vm.selectedDayKey == dayKeyForToday()) "2 eggs, 1 banana" else "Add to ${shortDateLabel(vm.selectedDayKey)}"
    Column(Modifier.fillMaxWidth().background(colors.bg).navigationBarsPadding()) {
        vm.loggedBatchMessage?.let { Snackbar(it, "Undo", vm::undoLoggedBatch, Modifier.padding(horizontal = Gutter, vertical = 6.dp)) }
        vm.undoEntry?.let { Snackbar("${it.foodName} deleted", "Undo", vm::undo, Modifier.padding(horizontal = Gutter, vertical = 6.dp)) }
        Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (previews.isNotEmpty()) Column { previews.forEach { PendingFood(it) } }
            if (suggestions.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEachIndexed { index, ranked -> ChoiceOption(vm.quickAddSuggestionLabel(ranked.food), index == 0, { vm.chooseQuickSuggestion(ranked.food) }, Modifier.width(200.dp)) }
            }
            QuickAddInput(vm, placeholder)
        }
        if (vm.input.isBlank()) BottomNav(selectedToday = true, onToday = { vm.closeMeal(); vm.closeLibrary() }, onLibrary = { vm.closeMeal(); vm.openLibrary() })
    }
}

/** The Daily tray's text input. Logging moves to the Log page in a later slice; this keeps the current behaviour. */
@Composable
internal fun QuickAddInput(vm: BreakfastViewModel, placeholder: String) {
    val colors = LocalBreakfastColors.current
    Field(
        label = "Food",
        value = vm.input,
        onValueChange = vm::onInput,
        labelVisible = false,
        placeholder = placeholder,
        singleLine = !(vm.input.contains(',') || vm.input.length > 28),
        minLines = if (vm.input.contains(',') || vm.input.length > 28) 2 else 1,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { vm.submit() }),
        trailing = {
            IconButton(onClick = vm::submit, enabled = vm.input.isNotBlank(), modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardReturn,
                    contentDescription = "Log",
                    tint = if (vm.input.isBlank()) colors.soft else colors.ink,
                )
            }
        },
    )
}

@Composable
internal fun PendingFood(preview: QuickAddPreview) {
    val approximate = if (preview.calculation.approximate) "≈" else ""
    FoodRow(
        name = preview.food.name,
        quantityLine = "$approximate${preview.amount.pretty()} ${preview.unit.displayLabel()}",
        calories = receiptKcal(preview.calculation.nutrition.calories),
    )
}

@Composable
internal fun CorrectionStrip(vm: BreakfastViewModel, entry: MealEntry) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceOption("Confirm", true, { vm.confirm(entry) }, Modifier.width(150.dp))
        vm.alternatives(entry).forEach { alternative -> ChoiceOption(alternative.food.name, false, { vm.chooseAlternative(entry, alternative.food) }, Modifier.width(200.dp)) }
        ChoiceOption("Search", false, { vm.openSearch(entry.foodName) }, Modifier.width(150.dp))
    }
}

private fun dayKeyForToday(): String = com.iron.breakfast.domain.dayKeyForEpochMs(System.currentTimeMillis())

private fun shortDateLabel(dayKey: String): String = runCatching { LocalDate.parse(dayKey).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)) }.getOrDefault(dayKey)


