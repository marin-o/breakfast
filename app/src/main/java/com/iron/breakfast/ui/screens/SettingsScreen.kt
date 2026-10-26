package com.iron.breakfast.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iron.breakfast.BreakfastViewModel
import com.iron.breakfast.DiaryPicker
import com.iron.breakfast.SETTINGS_GROUPS
import com.iron.breakfast.SettingsPage
import com.iron.breakfast.MealMode
import com.iron.breakfast.UndoWindow
import com.iron.breakfast.canShiftMealBound
import com.iron.breakfast.dayStartLabel
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.untilOf
import com.iron.breakfast.ui.components.AddCircle
import com.iron.breakfast.ui.components.ChoiceOption
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.LongDraft
import com.iron.breakfast.ui.components.TopBar
import com.iron.breakfast.ui.theme.BreakfastType
import com.iron.breakfast.ui.theme.Gutter
import com.iron.breakfast.ui.theme.LocalBreakfastColors

/**
 * Settings, opened from the Library menu: the list of groups, each a row that opens the group's own page. On a page
 * the arrow and Back return to the list; on the list they return to the Library.
 */
@Composable
internal fun SettingsScreen(vm: BreakfastViewModel) {
    val colors = LocalBreakfastColors.current
    val page = vm.settingsPage
    BackHandler { vm.settingsBack() }
    // Each page starts at its top.
    key(page) {
        val scroll = rememberScrollState()
        Column(Modifier.fillMaxSize().background(colors.bg).windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(scroll).padding(horizontal = Gutter, vertical = 4.dp)) {
            TopBar(page?.title ?: "Settings", vm::settingsBack, Modifier.padding(top = 8.dp))
            Hairline(Modifier.padding(top = 8.dp))
            when (page) {
                null -> SETTINGS_GROUPS.forEach { group -> SettingsAction(group.title) { vm.openSettingsPage(group) }; Hairline() }
                SettingsPage.LOGGING -> LoggingPage(vm)
                SettingsPage.DISPLAY -> DisplayPage(vm)
                SettingsPage.ACCESSIBILITY -> Unit
                SettingsPage.SCANNER -> Unit
                SettingsPage.DATA -> DataPage(vm)
                SettingsPage.ABOUT -> Unit
                SettingsPage.LICENCES -> Unit
            }
        }
    }
}


@Composable
private fun ColumnScope.LoggingPage(vm: BreakfastViewModel) {
    StepperRow("Day starts at", dayStartLabel(vm.dayStartHour), { vm.shiftDayStart(-1) }, { vm.shiftDayStart(1) })
    Hairline()
    ChoiceRow("Undo", UNDO_CHOICES, vm.undoWindow, vm::chooseUndoWindow)
    Hairline()
    ChoiceRow("Meal", MEAL_MODE_CHOICES, vm.mealMode, vm::chooseMealMode)
    Hairline()
    if (vm.mealMode == MealMode.BY_TIME) {
        MEAL_BOUND_ROWS.forEach { (meal, label) ->
            val bounds = vm.mealBounds
            StepperRow(
                label, dayStartLabel(bounds.untilOf(meal) ?: 0), { vm.stepMealBound(meal, -1) }, { vm.stepMealBound(meal, 1) },
                decreaseEnabled = canShiftMealBound(bounds, meal, -1), increaseEnabled = canShiftMealBound(bounds, meal, 1),
            )
            Hairline()
        }
    }
    ChoiceRow("Count what I'm typing", ON_OFF, vm.countTyping, vm::chooseCountTyping)
    Hairline()
    ChoiceRow("Learn from my picks", ON_OFF, vm.learnFromPicks, vm::chooseLearnFromPicks)
    Hairline()
}

@Composable
private fun ColumnScope.DisplayPage(vm: BreakfastViewModel) {
    ChoiceRow("Long draft", LONG_DRAFT_CHOICES, vm.longDraft, vm::chooseLongDraft)
    Hairline()
}

@Composable
private fun ColumnScope.DataPage(vm: BreakfastViewModel) {
    SettingsAction("Export diary", enabled = vm.ready && !vm.busy) { vm.requestDiaryPicker(DiaryPicker.EXPORT) }
    Hairline()
    SettingsAction("Import diary", enabled = vm.ready && !vm.busy) { vm.requestDiaryPicker(DiaryPicker.IMPORT) }
    Hairline()
}

/** A row that does one thing when tapped, as tall as the other rows. [destructive] draws the label in `bad`. */
@Composable
private fun SettingsAction(label: String, enabled: Boolean = true, destructive: Boolean = false, onClick: () -> Unit) {
    val colors = LocalBreakfastColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = if (destructive) colors.bad else colors.ink, style = BreakfastType.Body)
    }
}

/** The 64dp row with the −/+ stepper on the right, as "Day starts at". A step past a limit is disabled. */
@Composable
private fun StepperRow(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseEnabled: Boolean = true,
    increaseEnabled: Boolean = true,
) {
    val colors = LocalBreakfastColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = colors.ink, style = BreakfastType.Body)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AddCircle("Decrease", onDecrease, enabled = decreaseEnabled, icon = Icons.Outlined.Remove)
            Text(value, Modifier.widthIn(min = 56.dp), color = colors.ink, style = BreakfastType.BodyMedium, textAlign = TextAlign.Center)
            AddCircle("Increase", onIncrease, enabled = increaseEnabled)
        }
    }
}

/** A label over its choice chips, as "Long draft". The chips wrap to a second line when they do not fit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(label: String, choices: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val colors = LocalBreakfastColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = colors.ink, style = BreakfastType.Body)
        FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { (value, text) -> ChoiceOption(text, selected == value, { onSelect(value) }) }
        }
    }
}

private val UNDO_CHOICES = listOf(
    UndoWindow.FIFTEEN_SECONDS to "15 s", UndoWindow.FIVE_SECONDS to "5 s", UndoWindow.THIRTY_SECONDS to "30 s",
    UndoWindow.UNTIL_NEXT_ACTION to "Until next action",
)
private val MEAL_MODE_CHOICES = listOf(MealMode.ASK to "Ask", MealMode.BY_TIME to "By time", MealMode.LAST_USED to "Last used")
private val MEAL_BOUND_ROWS = listOf(MealSlot.BREAKFAST to "Breakfast until", MealSlot.LUNCH to "Lunch until", MealSlot.DINNER to "Dinner until")
private val ON_OFF = listOf(true to "On", false to "Off")
private val LONG_DRAFT_CHOICES = listOf(LongDraft.END to "End", LongDraft.START to "Start", LongDraft.TWO_LINES to "Two lines")
