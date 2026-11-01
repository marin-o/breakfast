package com.iron.breakfast.settings

import com.iron.breakfast.ui.components.SubScreen
import com.iron.breakfast.ui.components.inert
import com.iron.breakfast.ui.components.rememberMotion
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.semantics
import com.iron.breakfast.app.DiarySession
import com.iron.breakfast.app.Surfaces
import com.iron.breakfast.transfer.DiaryTransfer
import com.iron.breakfast.BuildConfig
import com.iron.breakfast.transfer.DiaryPicker
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.ui.components.AddCircle
import com.iron.breakfast.ui.components.ChoiceOption
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.LongDraft
import com.iron.breakfast.ui.theme.BreakfastType
import com.iron.breakfast.ui.theme.LocalBreakfastColors

/**
 * Settings, opened from the bottom bar's gear over Daily or the Library: the list of groups, each a row that opens the
 * group's own page. On a page the arrow and Back return to the list; on the list they return to the screen beneath.
 */
@Composable
internal fun SettingsScreen(settings: SettingsState, surfaces: Surfaces, transfer: DiaryTransfer<*>, session: DiarySession, licenceText: suspend () -> String) {
    val colors = LocalBreakfastColors.current
    val motion = rememberMotion()
    // A page opened slides in from the end, Back slides the one before in from the start (pageDepth); the page on its
    // way out takes no tap and is not read. Each page starts at its top.
    AnimatedContent(
        targetState = settings.page,
        // Both pages are part faded mid-change: this keeps the screen beneath from showing through them.
        modifier = Modifier.fillMaxSize().background(colors.bg),
        transitionSpec = { motion.pageChange(deeper = pageDepth(targetState) > pageDepth(initialState)) },
        label = "settings page",
    ) { page ->
        // Leaving from the frame the change starts, so a quick second tap cannot reach it.
        val leaving = page != settings.page
        SubScreen(page?.title ?: "Settings", surfaces::settingsBack, if (leaving) Modifier.inert() else Modifier) {
            Hairline(Modifier.padding(top = 8.dp))
            when (page) {
                null -> SETTINGS_GROUPS.forEach { group -> SettingsAction(group.title) { settings.openPage(group) }; Hairline() }
                SettingsPage.LOGGING -> LoggingPage(settings)
                SettingsPage.DISPLAY -> DisplayPage(settings)
                SettingsPage.ACCESSIBILITY -> AccessibilityPage(settings)
                SettingsPage.SCANNER -> ScannerPage(settings)
                SettingsPage.DATA -> DataPage(settings, transfer, session)
                SettingsPage.ABOUT -> AboutPage(settings, surfaces)
                SettingsPage.LICENCES -> LicencesPage(licenceText)
            }
        }
    }
}


@Composable
private fun ColumnScope.LoggingPage(settings: SettingsState) {
    StepperRow("Day starts at", dayStartLabel(settings.dayStartHour), { settings.shiftDayStart(-1) }, { settings.shiftDayStart(1) })
    Hairline()
    ChoiceRow("Undo", UNDO_CHOICES, settings.undoWindow, settings::chooseUndoWindow)
    Hairline()
    ChoiceRow("Meal", MEAL_MODE_CHOICES, settings.mealMode, settings::chooseMealMode)
    Hairline()
    if (settings.mealMode == MealMode.BY_TIME) {
        MEAL_BOUND_ROWS.forEach { (meal, label) ->
            val bounds = settings.mealBounds
            StepperRow(
                label, dayStartLabel(bounds.untilOf(meal) ?: 0), { settings.stepMealBound(meal, -1) }, { settings.stepMealBound(meal, 1) },
                decreaseEnabled = canShiftMealBound(bounds, meal, -1), increaseEnabled = canShiftMealBound(bounds, meal, 1),
            )
            Hairline()
        }
        ChoiceRow("Before day start", BEFORE_DAY_START_CHOICES, settings.beforeDayStart, settings::chooseBeforeDayStart)
        Hairline()
    }
    ChoiceRow("Count what I'm typing", ON_OFF, settings.countTyping, settings::chooseCountTyping)
    Hairline()
    ChoiceRow("Learn from my picks", ON_OFF, settings.learnFromPicks, settings::chooseLearnFromPicks)
    Hairline()
}

@Composable
private fun ColumnScope.DisplayPage(settings: SettingsState) {
    ChoiceRow("Theme", THEME_CHOICES, settings.theme, settings::chooseTheme)
    Hairline()
    ChoiceRow("Long draft", LONG_DRAFT_CHOICES, settings.longDraft, settings::chooseLongDraft)
    Hairline()
    ChoiceRow("Macros on today", SHOW_HIDE, settings.showMacros, settings::chooseShowMacros)
    Hairline()
    ChoiceRow("Missing values note", SHOW_HIDE, settings.showMissingNote, settings::chooseShowMissingNote)
    Hairline()
    ChoiceRow("Decimal mark", DECIMAL_MARK_CHOICES, settings.decimalMark, settings::chooseDecimalMark)
    Hairline()
}

@Composable
private fun ColumnScope.AccessibilityPage(settings: SettingsState) {
    ChoiceRow("Motion", MOTION_CHOICES, settings.motion, settings::chooseMotion)
    Hairline()
    ChoiceRow("Text size", TEXT_SIZE_CHOICES, settings.textSize, settings::chooseTextSize)
    Hairline()
    ChoiceRow("Haptics", ON_OFF, settings.haptics, settings::chooseHaptics)
    Hairline()
}

@Composable
private fun ColumnScope.ScannerPage(settings: SettingsState) {
    ChoiceRow("Flashlight at start", ON_OFF, settings.flashlightAtStart, settings::chooseFlashlightAtStart)
    Hairline()
    ChoiceRow("Look up unknown barcodes", ON_OFF, settings.lookUpBarcodes, settings::chooseLookUpBarcodes)
    Hairline()
    ChoiceRow("Contribute", SHOW_HIDE, settings.showContribute, settings::chooseShowContribute)
    Hairline()
}

@Composable
private fun ColumnScope.DataPage(settings: SettingsState, transfer: DiaryTransfer<*>, session: DiarySession) {
    SettingsAction("Export diary", enabled = session.canWrite) { transfer.requestPicker(DiaryPicker.EXPORT) }
    Hairline()
    SettingsAction("Import diary", enabled = session.canWrite) { transfer.requestPicker(DiaryPicker.IMPORT) }
    Hairline()
    ChoiceRow("Export reminder", EXPORT_REMINDER_CHOICES, settings.exportReminder, settings::chooseExportReminder)
    Hairline()
    ChoiceRow("Safety copy", SAFETY_COPY_CHOICES, settings.keepSafetyCopy, settings::chooseKeepSafetyCopy)
    Hairline()
    SettingsAction("Delete all data", enabled = session.canWrite, destructive = true, onClick = transfer::openDeleteAll)
    Hairline()
}

@Composable
private fun ColumnScope.AboutPage(settings: SettingsState, surfaces: Surfaces) {
    ValueRow("Version", versionLabel(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
    Hairline()
    SettingsAction("Show the tutorial", onClick = surfaces::openOnboarding)
    Hairline()
    SettingsAction("Licences") { settings.openPage(SettingsPage.LICENCES) }
    Hairline()
}

/** The bundled licence as plain scrolling text, read off the main thread. */
@Composable
private fun LicencesPage(licenceText: suspend () -> String) {
    val colors = LocalBreakfastColors.current
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { text = licenceText() }
    Text(text, Modifier.fillMaxWidth().padding(vertical = 16.dp), color = colors.ink, style = BreakfastType.Body)
}

/** The 64dp row with a value on the right instead of a control, as About, Version. */
@Composable
private fun ValueRow(label: String, value: String) {
    val colors = LocalBreakfastColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = colors.ink, style = BreakfastType.Body)
        Text(value, color = colors.soft, style = BreakfastType.Body)
    }
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
private val BEFORE_DAY_START_CHOICES = listOf(BeforeDayStart.SNACKS to "Snacks", BeforeDayStart.DINNER to "Dinner", BeforeDayStart.ASK to "Ask")
private val ON_OFF = listOf(true to "On", false to "Off")
private val SHOW_HIDE = listOf(true to "Show", false to "Hide")
private val EXPORT_REMINDER_CHOICES = listOf(ExportReminder.NEVER to "Never", ExportReminder.WEEKLY to "Weekly", ExportReminder.MONTHLY to "Monthly")
private val SAFETY_COPY_CHOICES = listOf(true to "Keep", false to "Don't keep")
private val MOTION_CHOICES = listOf(MotionChoice.SYSTEM to "System", MotionChoice.REDUCED to "Reduced")
private val TEXT_SIZE_CHOICES = listOf(TextSizeChoice.SYSTEM to "System", TextSizeChoice.LARGER to "Larger")
private val THEME_CHOICES = listOf(ThemeChoice.SYSTEM to "System", ThemeChoice.LIGHT to "Light", ThemeChoice.DARK to "Dark")
private val DECIMAL_MARK_CHOICES = listOf(DecimalMark.POINT to "Point", DecimalMark.COMMA to "Comma")
private val LONG_DRAFT_CHOICES = listOf(LongDraft.END to "End", LongDraft.START to "Start", LongDraft.TWO_LINES to "Two lines")
