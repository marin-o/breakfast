package com.iron.breakfast.today

import com.iron.breakfast.domain.ProfileValues
import com.iron.breakfast.entry.entryActions
import com.iron.breakfast.ui.components.followDrag
import com.iron.breakfast.ui.components.DayPickerDialog
import com.iron.breakfast.ui.screens.dailyMacroNote
import com.iron.breakfast.ui.screens.macroStats
import com.iron.breakfast.ui.shortDateLabel
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.iron.breakfast.ui.components.BottomAreaClearance
import com.iron.breakfast.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animate
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.iron.breakfast.app.DiarySession
import com.iron.breakfast.app.Surfaces
import com.iron.breakfast.contribution.ContributionFlow
import com.iron.breakfast.entry.EntryEdit
import com.iron.breakfast.log.LogComposer
import com.iron.breakfast.settings.SettingsState
import com.iron.breakfast.contribution.ContributionAction
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.nutritionTotalOf
import com.iron.breakfast.domain.macroTotalsOf
import com.iron.breakfast.ui.label
import com.iron.breakfast.ui.components.LocalScreenBody
import com.iron.breakfast.ui.components.ChoiceOption
import com.iron.breakfast.ui.components.DayHeader
import com.iron.breakfast.ui.components.DayTotal
import com.iron.breakfast.ui.components.FoodRow
import com.iron.breakfast.ui.components.MealHeader
import com.iron.breakfast.ui.components.AnimatedRows
import com.iron.breakfast.ui.components.Motion
import com.iron.breakfast.ui.components.Reveal
import com.iron.breakfast.ui.components.rememberMotion
import com.iron.breakfast.ui.theme.LocalBreakfastColors
import kotlin.math.abs
import com.iron.breakfast.ui.screens.formatKcal
import com.iron.breakfast.ui.screens.kcalTargetLine
import com.iron.breakfast.ui.screens.earliestMealTime
import com.iron.breakfast.ui.screens.entryQuantityLine


@Composable
internal fun DailyScreen(
    session: DiarySession,
    day: DayState,
    settings: SettingsState,
    log: LogComposer,
    contribution: ContributionFlow,
    entryEdit: EntryEdit,
    surfaces: Surfaces,
    undo: UndoController,
    feedback: MatchFeedback,
    profile: ProfileValues,
    modifier: Modifier,
) {
    val selected = session.entries.filter { it.dayKey == day.selectedDayKey }
    val total = nutritionTotalOf(selected)
    val isToday = day.selectedDayKey == day.todayKey
    val colors = LocalBreakfastColors.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val swipeThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 96.dp.toPx() }
    var pickerOpen by remember { mutableStateOf(false) }
    val motion = rememberMotion()
    val daySlide = rememberDaySlide(day.selectedDayKey, motion)

    Box(modifier.fillMaxSize().background(colors.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().then(LocalScreenBody.current).graphicsLayer {
                // Another day comes in from the side it lies on: a later day from the right.
                translationX = daySlide.offset * motion.screenSlidePx
                alpha = 1f - abs(daySlide.offset)
            }.pointerInput(day.selectedDayKey) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val end = followDrag(down.id, touchSlop) { change, _, _, horizontal -> if (horizontal) change.consume() }
                    if (end.horizontal && abs(end.dx) >= swipeThreshold) day.shift(if (end.dx < 0f) 1 else -1)
                }
            },
            // Room for the bottom area that floats over the end of the list.
            contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = BottomAreaClearance),
        ) {
            item {
                DayHeader(
                    title = shortDateLabel(day.selectedDayKey),
                    onPrevious = { day.shift(-1) },
                    onNext = { day.shift(1) },
                    modifier = Modifier.padding(top = 8.dp),
                    showToday = !isToday,
                    onToday = day::goToday,
                    nextEnabled = !isToday,
                    onTitleClick = { pickerOpen = true },
                )
                val macros = macroTotalsOf(selected.map { it.nutritionSnapshot })
                DayTotal(
                    formatKcal(total.calories),
                    if (settings.showMacros) macroStats(total.calories, macros, profile) else emptyList(),
                    Modifier.padding(top = 18.dp),
                    note = dailyMacroNote(macros, showMacros = settings.showMacros, showNote = settings.showMissingNote),
                    target = kcalTargetLine(profile),
                )
            }
            log.error?.let { message -> item { Text(message, color = colors.bad, style = BreakfastType.Meta, modifier = Modifier.padding(vertical = 6.dp)) } }
            items(MealSlot.entries) { slot ->
                DailyMealSection(slot, selected.filter { it.mealSlot == slot }, log, surfaces, entryEdit, undo, feedback, rows = day.selectedDayKey to session.ready)
            }
            item {
                if (contribution.actionShown) ContributionAction(contribution)
            }
        }
    }
    if (pickerOpen) DayPickerDialog(
        dayKey = day.selectedDayKey,
        todayKey = day.todayKey,
        onDismiss = { pickerOpen = false },
        onPick = { dayKey -> day.goTo(dayKey); pickerOpen = false },
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DailyMealSection(
    slot: MealSlot,
    entries: List<MealEntry>,
    log: LogComposer,
    surfaces: Surfaces,
    entryEdit: EntryEdit,
    undo: UndoController,
    feedback: MatchFeedback,
    rows: Any,
) {
    val total = nutritionTotalOf(entries)
    MealHeader(
        name = slot.label(),
        total = formatKcal(total.calories),
        onAdd = { log.open(slot) },
        addDescription = "Add food to ${slot.label()}",
        time = earliestMealTime(entries),
        emptyText = if (entries.isEmpty()) "Nothing yet" else null,
        modifier = Modifier.clickable(role = Role.Button) { surfaces.openMeal(slot) },
    )
    // Rows fade in and collapse out; another day (or the first load) shows its rows at once.
    AnimatedRows(entries, { it.id }, reset = rows) { entry -> Column(Modifier.fillMaxWidth()) {
        FoodRow(
            name = entry.foodName,
            quantityLine = entryQuantityLine(entry),
            calories = formatKcal(entry.nutritionSnapshot.calories),
            caloriesEnd = AddCircleInset,
            modifier = Modifier.entryActions(entry, entryEdit, undo) { entryEdit.openDetail(entry) },
        )
        Reveal(entry.takeIf { feedback.isOpen(it) }, resize = true) { CorrectionStrip(feedback, it) }
    } }
}

/** A day's correction after a doubtful match: "<food> matched. Not right?" with the alternatives and Search. */
@Composable
internal fun CorrectionStrip(feedback: MatchFeedback, entry: MealEntry) {
    val colors = LocalBreakfastColors.current
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Text("${entry.foodName} matched. Not right?", color = colors.soft, style = BreakfastType.Meta)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceOption(entry.foodName, true, { feedback.confirm(entry) })
            // The match shows as selected; the alternatives and Search are actions.
            feedback.alternatives(entry).forEach { alternative -> ChoiceOption(alternative.food.name, false, { feedback.chooseAlternative(entry, alternative.food) }, selectable = false) }
            ChoiceOption("Search", false, { feedback.search(entry) }, selectable = false)
        }
    }
}
