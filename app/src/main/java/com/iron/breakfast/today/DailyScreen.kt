package com.iron.breakfast.today

import com.iron.breakfast.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animate
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.iron.breakfast.app.BreakfastViewModel
import com.iron.breakfast.contribution.ContributionAction
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MacroTotal
import com.iron.breakfast.domain.MacroTotals
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.nutritionTotalOf
import com.iron.breakfast.domain.macroTotalsOf
import com.iron.breakfast.ui.label
import com.iron.breakfast.ui.pretty
import com.iron.breakfast.ui.components.BottomNav
import com.iron.breakfast.ui.components.ChoiceOption
import com.iron.breakfast.ui.components.Composer
import com.iron.breakfast.ui.components.DayHeader
import com.iron.breakfast.ui.components.DayTotal
import com.iron.breakfast.ui.components.FoodRow
import com.iron.breakfast.ui.components.MacroStat
import com.iron.breakfast.ui.components.MealHeader
import com.iron.breakfast.ui.components.Snackbar
import com.iron.breakfast.ui.components.ConfirmationSnackbar
import com.iron.breakfast.ui.components.rememberTick
import com.iron.breakfast.ui.components.AnimatedRows
import com.iron.breakfast.ui.components.Motion
import com.iron.breakfast.ui.components.Reveal
import com.iron.breakfast.ui.components.rememberMotion
import com.iron.breakfast.ui.theme.LocalBreakfastColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import com.iron.breakfast.ui.screens.formatKcal
import com.iron.breakfast.ui.screens.earliestMealTime
import com.iron.breakfast.ui.screens.quantityRateDisplay
import com.iron.breakfast.ui.screens.plainLine


@Composable
internal fun DailyScreen(vm: BreakfastViewModel, modifier: Modifier) {
    val selected = vm.entries.filter { it.dayKey == vm.day.selectedDayKey }
    val foods = vm.foods.associateBy { it.id }
    val total = nutritionTotalOf(selected)
    val isToday = vm.day.selectedDayKey == vm.day.todayKey
    val colors = LocalBreakfastColors.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val swipeThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 96.dp.toPx() }
    var pickerOpen by remember { mutableStateOf(false) }
    val motion = rememberMotion()
    val daySlide = rememberDaySlide(vm.day.selectedDayKey, motion)

    Box(modifier.fillMaxSize().background(colors.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                // Another day comes in from the side it lies on: a later day from the right.
                translationX = daySlide.offset * motion.screenSlidePx
                alpha = 1f - abs(daySlide.offset)
            }.pointerInput(vm.day.selectedDayKey) {
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
                    if (horizontal && abs(dx) >= swipeThreshold) vm.day.shift(if (dx < 0f) 1 else -1)
                }
            },
            // Room for the composer and nav that float over the end of the list.
            contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 260.dp),
        ) {
            item {
                DayHeader(
                    title = shortDateLabel(vm.day.selectedDayKey),
                    onPrevious = { vm.day.shift(-1) },
                    onNext = { vm.day.shift(1) },
                    modifier = Modifier.padding(top = 8.dp),
                    showToday = !isToday,
                    onToday = vm.day::goToday,
                    nextEnabled = !isToday,
                    onTitleClick = { pickerOpen = true },
                )
                val macros = macroTotalsOf(selected.map { it.nutritionSnapshot })
                DayTotal(
                    formatKcal(total.calories),
                    if (vm.settings.showMacros) dailyMacroStats(total.calories, macros) else emptyList(),
                    Modifier.padding(top = 18.dp),
                    note = dailyMacroNote(macros, showMacros = vm.settings.showMacros, showNote = vm.settings.showMissingNote),
                )
            }
            vm.log.error?.let { message -> item { Text(message, color = colors.bad, style = BreakfastType.Meta, modifier = Modifier.padding(vertical = 6.dp)) } }
            items(MealSlot.entries) { slot ->
                DailyMealSection(slot, selected.filter { it.mealSlot == slot }, foods, vm, rows = vm.day.selectedDayKey to vm.ready)
            }
            item {
                if (vm.contribution.actionShown) ContributionAction(vm)
            }
        }
        // Hidden during an entry edit, which owns the input and its own sheet.
        if (!vm.entryEdit.editing) DailyComposer(vm, Modifier.align(Alignment.BottomCenter))
    }
    if (pickerOpen) DayPickerDialog(
        dayKey = vm.day.selectedDayKey,
        todayKey = vm.day.todayKey,
        onDismiss = { pickerOpen = false },
        onPick = { dayKey -> vm.day.goTo(dayKey); pickerOpen = false },
    )
}

/** The day's content offset after a change of day: from ±1 (a whole [Motion.screenSlidePx] to the side) to 0. */
private class DaySlide(var day: String) { var offset by mutableFloatStateOf(0f) }

/**
 * Slides the day's content in from the side the new [day] lies on (later days from the right), whichever way the day
 * changed: a swipe, the arrows, Today or the date picker. Set as the day changes, so its first frame is already offset.
 */
@Composable
private fun rememberDaySlide(day: String, motion: Motion): DaySlide {
    val slide = remember { DaySlide(day) }
    if (slide.day != day) {
        slide.offset = if (motion.reduced) 0f else if (day > slide.day) 1f else -1f
        slide.day = day
    }
    LaunchedEffect(day) {
        if (slide.offset != 0f) animate(slide.offset, 0f, animationSpec = motion.day()) { value, _ -> slide.offset = value }
    }
    return slide
}

/** Date picker opened from the day title. Future days are not selectable, as with Next. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPickerDialog(dayKey: String, todayKey: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val todayMillis = LocalDate.parse(todayKey).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = LocalDate.parse(dayKey).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis == null) onDismiss() else onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

/**
 * Macro columns for the day total. Each bar is the macro's known share of the day's kcal. A macro no entry has reads
 * "—", as the Library shows an unknown value; a sum that leaves out entries without the macro reads "12 g ?".
 */
internal fun dailyMacroStats(kcal: Double, macros: MacroTotals): List<MacroStat> {
    fun stat(label: String, total: MacroTotal, kcalPerGram: Double): MacroStat {
        val share = if (kcal > 0.0) (total.sum * kcalPerGram / kcal).toFloat() else 0f
        return when {
            total.allUnknown -> MacroStat(label, "—", "g", share)
            total.partial -> MacroStat(label, total.sum.pretty(), "g ?", share)
            else -> MacroStat(label, total.sum.pretty(), "g", share)
        }
    }
    return listOf(
        stat("Protein", macros.protein, 4.0),
        stat("Carbs", macros.carbohydrates, 4.0),
        stat("Fat", macros.fat, 9.0),
    )
}

/**
 * The one line under the macros when any of them shows "?". Never drawn when the macros are hidden ("Macros on today"
 * Hide) or the note is ("Missing values note" Hide).
 */
internal fun dailyMacroNote(macros: MacroTotals, showMacros: Boolean = true, showNote: Boolean = true): String? =
    if (showMacros && showNote && macros.anyPartial) "? some foods have no value" else null

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DailyMealSection(slot: MealSlot, entries: List<MealEntry>, foods: Map<String, FoodRecord>, vm: BreakfastViewModel, rows: Any) {
    val total = nutritionTotalOf(entries)
    MealHeader(
        name = slot.label(),
        total = formatKcal(total.calories),
        onAdd = { vm.log.open(slot) },
        addDescription = "Add food to ${slot.label()}",
        time = earliestMealTime(entries),
        emptyText = if (entries.isEmpty()) "Nothing yet" else null,
        modifier = Modifier.clickable(role = Role.Button) { vm.surfaces.openMeal(slot) },
    )
    // Rows fade in and collapse out; another day (or the first load) shows its rows at once.
    AnimatedRows(entries, { it.id }, reset = rows) { entry -> Column(Modifier.fillMaxWidth()) {
        val quantity = quantityRateDisplay(entry, foods[entry.foodId], includeRateUnit = false, asciiMultiply = true)
        FoodRow(
            name = entry.foodName,
            quantityLine = quantity.plainLine(),
            calories = formatKcal(entry.nutritionSnapshot.calories),
            modifier = Modifier.semantics {
                onClick(label = "Open details for ${entry.foodName}") { vm.entryEdit.openDetail(entry); true }
                customActions = listOf(
                    CustomAccessibilityAction("Quick edit amount") { vm.entryEdit.beginQuick(entry); true },
                    CustomAccessibilityAction("Edit food and amount") { vm.entryEdit.beginFull(entry); true },
                    CustomAccessibilityAction("Delete ${entry.foodName}") { vm.undo.delete(entry); true },
                )
            }.combinedClickable(onClick = { vm.entryEdit.openDetail(entry) }, onDoubleClick = { vm.entryEdit.beginQuick(entry) }, onLongClick = { vm.entryEdit.beginFull(entry) }),
        )
        Reveal(entry.takeIf { vm.feedback.isOpen(it) }, resize = true) { CorrectionStrip(vm, it) }
    } }
}

/**
 * The composer, snackbars and tabs, floating over the end of the list on a `bg` fade.
 * Tapping "Log food" opens the Log page with no meal preset; the scan icon opens the scanner.
 */
@Composable
internal fun DailyComposer(vm: BreakfastViewModel, modifier: Modifier = Modifier) {
    val tick = rememberTick()
    ComposerFade(modifier) {
        Reveal(vm.undo.loggedMessage) { Snackbar(it, "Undo", { tick(); vm.undo.undoBatch() }, Modifier.padding(bottom = 8.dp)) }
        Reveal(vm.undo.deletedEntry) { Snackbar("${it.foodName} deleted", "Undo", { tick(); vm.undo.undoDelete() }, Modifier.padding(bottom = 8.dp)) }
        ConfirmationSnackbar(vm.confirmations.message)
        if (vm.reminders.shownOnDaily(covered = vm.surfaces.baseCovered())) {
            LaunchedEffect(Unit) { vm.reminders.onScreenNow() }
            Snackbar("Export your diary.", "Export", vm.reminders::export, Modifier.padding(bottom = 8.dp))
        }
        val draft = vm.log.draft.isNotBlank()
        Composer(
            placeholder = if (draft) vm.log.draft else "Log food",
            onClick = { vm.log.open(null) },
            onScan = vm.scan::begin,
            draft = draft,
            longDraft = vm.settings.longDraft,
        )
        BottomNav(selectedToday = true, onToday = { vm.surfaces.closeMeal(); vm.surfaces.closeLibrary() }, onLibrary = { vm.surfaces.closeMeal(); vm.surfaces.openLibrary() }, onSettings = vm.surfaces::openSettings)
    }
}

/**
 * A `bg` fade with 16dp gutters, above the system navigation bar; shared by the Daily and Meal bottom areas.
 * The list behind it keeps enough bottom padding that its last row clears the composer and nav.
 */
@Composable
internal fun ComposerFade(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalBreakfastColors.current
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to colors.bg.copy(alpha = 0f), 0.22f to colors.bg))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
        content = content,
    )
}

/** A day's correction after a doubtful match: "<food> matched. Not right?" with the alternatives and Search. */
@Composable
internal fun CorrectionStrip(vm: BreakfastViewModel, entry: MealEntry) {
    val colors = LocalBreakfastColors.current
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Text("${entry.foodName} matched. Not right?", color = colors.soft, style = BreakfastType.Meta)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceOption(entry.foodName, true, { vm.feedback.confirm(entry) })
            // The match shows as selected; the alternatives and Search are actions.
            vm.feedback.alternatives(entry).forEach { alternative -> ChoiceOption(alternative.food.name, false, { vm.feedback.chooseAlternative(entry, alternative.food) }, selectable = false) }
            ChoiceOption("Search", false, { vm.search.open(entry.foodName) }, selectable = false)
        }
    }
}

/** "Wed 14 Oct", English. */
internal fun shortDateLabel(dayKey: String): String = runCatching { LocalDate.parse(dayKey).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)) }.getOrDefault(dayKey)
