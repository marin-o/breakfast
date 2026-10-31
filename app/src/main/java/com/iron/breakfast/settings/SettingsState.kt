package com.iron.breakfast.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.ui.components.LongDraft
import kotlin.reflect.KMutableProperty1

/**
 * Every user setting as observable state, its setter, and the page of Settings that is open.
 *
 * Each setting is declared once, as a [Setting] over its [AppSettings] property. A setter writes the value to the
 * screen state and to the phone; [resetToDefaults] clears the stored settings and reloads every declared setting, so
 * a new setting cannot be left out of the reset. Other owners react to a value through [Setting.observe] (`AppOwners`
 * wires the effects that are not settings: today's day, the Undo window, the export reminder), or, for an effect that
 * acts on the user's own choice and must never follow a reset, through [Setting.onChosen] (the safety copy's removal).
 * The decimal mark shown by `pretty()` is the one effect kept here: [DisplayDecimal] is a global read everywhere, and
 * this class is its only writer.
 */
internal class SettingsState(private val prefs: AppSettings, private val clock: () -> Long) {
    /** One stored setting: its value on screen, its observers, and how it is read from and written to [prefs]. */
    inner class Setting<T> internal constructor(private val property: KMutableProperty1<AppSettings, T>) {
        /** The [AppSettings] property this setting is stored as. */
        val name: String get() = property.name
        var value by mutableStateOf(property.get(prefs)); private set
        private val observers = mutableListOf<(T) -> Unit>()
        private val choiceObservers = mutableListOf<(T) -> Unit>()

        /** [observer] runs with the new value after the user chose one, and after a [resetToDefaults] that changed it. */
        fun observe(observer: (T) -> Unit) { observers += observer }

        /**
         * [observer] runs only after the user chose a value, never after [resetToDefaults]: a reset must not act as a
         * choice (Delete all with its settings chip would otherwise delete the safety copy it just wrote, were the
         * default ever Don't keep).
         */
        fun onChosen(observer: (T) -> Unit) { choiceObservers += observer }

        internal fun set(newValue: T) {
            value = newValue
            property.set(prefs, newValue)
            notifyObservers()
            choiceObservers.forEach { it(value) }
        }

        /** Reads the stored value again; true when that changed it. */
        internal fun reload(): Boolean {
            val before = value
            value = property.get(prefs)
            return value != before
        }

        internal fun notifyObservers() = observers.forEach { it(value) }
    }

    private val declared = mutableListOf<Setting<*>>()
    private fun <T> setting(property: KMutableProperty1<AppSettings, T>): Setting<T> = Setting(property).also { declared += it }

    // The declaration: one line per stored setting, in the order of the Settings pages.
    val dayStartHourSetting = setting(AppSettings::dayStartHour)
    val undoWindowSetting = setting(AppSettings::undoWindow)
    val mealModeSetting = setting(AppSettings::mealMode)
    val mealBoundsSetting = setting(AppSettings::mealBounds)
    val beforeDayStartSetting = setting(AppSettings::beforeDayStart)
    val countTypingSetting = setting(AppSettings::countTyping)
    val learnFromPicksSetting = setting(AppSettings::learnFromPicks)
    val themeSetting = setting(AppSettings::theme)
    val longDraftSetting = setting(AppSettings::longDraft)
    val showMacrosSetting = setting(AppSettings::showMacros)
    val showMissingNoteSetting = setting(AppSettings::showMissingNote)
    val decimalMarkSetting = setting(AppSettings::decimalMark)
    val motionSetting = setting(AppSettings::motion)
    val textSizeSetting = setting(AppSettings::textSize)
    val hapticsSetting = setting(AppSettings::haptics)
    val flashlightAtStartSetting = setting(AppSettings::flashlightAtStart)
    val lookUpBarcodesSetting = setting(AppSettings::lookUpBarcodes)
    val showContributeSetting = setting(AppSettings::showContribute)
    val exportReminderSetting = setting(AppSettings::exportReminder)
    val keepSafetyCopySetting = setting(AppSettings::keepSafetyCopy)

    /** Every declared setting, in declaration order. */
    val all: List<Setting<*>> get() = declared

    init {
        // The decimal mark shown everywhere follows the setting from the start.
        DisplayDecimal.mark = decimalMarkSetting.value
        decimalMarkSetting.observe { DisplayDecimal.mark = it }
    }

    // Logging
    /** The hour a diary day starts at. */
    val dayStartHour: Int get() = dayStartHourSetting.value
    val undoWindow: UndoWindow get() = undoWindowSetting.value
    val mealMode: MealMode get() = mealModeSetting.value
    val mealBounds: MealBounds get() = mealBoundsSetting.value
    val beforeDayStart: BeforeDayStart get() = beforeDayStartSetting.value
    val countTyping: Boolean get() = countTypingSetting.value
    val learnFromPicks: Boolean get() = learnFromPicksSetting.value
    /** The "Day starts at" stepper: one hour on or back, wrapping at midnight. */
    fun shiftDayStart(delta: Int) = dayStartHourSetting.set(shiftDayStartHour(dayStartHour, delta))
    fun chooseUndoWindow(window: UndoWindow) = undoWindowSetting.set(window)
    fun chooseMealMode(mode: MealMode) = mealModeSetting.set(mode)
    /** A "By time" stepper; a step that would break the order is refused (the stepper shows it disabled). */
    fun stepMealBound(meal: MealSlot, delta: Int) = mealBoundsSetting.set(shiftMealBound(mealBounds, meal, delta))
    fun chooseBeforeDayStart(choice: BeforeDayStart) = beforeDayStartSetting.set(choice)
    fun chooseCountTyping(on: Boolean) = countTypingSetting.set(on)
    fun chooseLearnFromPicks(on: Boolean) = learnFromPicksSetting.set(on)

    // Display
    /** The activity applies it. */
    val theme: ThemeChoice get() = themeSetting.value
    /** How the composer shows a long draft. */
    val longDraft: LongDraft get() = longDraftSetting.value
    val showMacros: Boolean get() = showMacrosSetting.value
    val showMissingNote: Boolean get() = showMissingNoteSetting.value
    /** Shown numbers read it through [DisplayDecimal]. */
    val decimalMark: DecimalMark get() = decimalMarkSetting.value
    fun chooseTheme(choice: ThemeChoice) = themeSetting.set(choice)
    fun chooseLongDraft(mode: LongDraft) = longDraftSetting.set(mode)
    fun chooseShowMacros(show: Boolean) = showMacrosSetting.set(show)
    fun chooseShowMissingNote(show: Boolean) = showMissingNoteSetting.set(show)
    fun chooseDecimalMark(mark: DecimalMark) = decimalMarkSetting.set(mark)

    // Accessibility (the activity applies them)
    val motion: MotionChoice get() = motionSetting.value
    val textSize: TextSizeChoice get() = textSizeSetting.value
    val haptics: Boolean get() = hapticsSetting.value
    fun chooseMotion(choice: MotionChoice) = motionSetting.set(choice)
    fun chooseTextSize(choice: TextSizeChoice) = textSizeSetting.set(choice)
    fun chooseHaptics(on: Boolean) = hapticsSetting.set(on)

    // Scanner
    val flashlightAtStart: Boolean get() = flashlightAtStartSetting.value
    val lookUpBarcodes: Boolean get() = lookUpBarcodesSetting.value
    val showContribute: Boolean get() = showContributeSetting.value
    fun chooseFlashlightAtStart(on: Boolean) = flashlightAtStartSetting.set(on)
    fun chooseLookUpBarcodes(on: Boolean) = lookUpBarcodesSetting.set(on)
    fun chooseShowContribute(show: Boolean) = showContributeSetting.set(show)

    // Data
    val exportReminder: ExportReminder get() = exportReminderSetting.value
    val keepSafetyCopy: Boolean get() = keepSafetyCopySetting.value
    /** Turned on from Never: the first reminder comes one interval from now ([exportReminderEnabledMs]). */
    fun chooseExportReminder(reminder: ExportReminder) {
        if (exportReminder == ExportReminder.NEVER && reminder != ExportReminder.NEVER) prefs.exportReminderEnabledMs = clock()
        exportReminderSetting.set(reminder)
    }
    fun chooseKeepSafetyCopy(keep: Boolean) = keepSafetyCopySetting.set(keep)

    /** The export reminder's stored times (see [AppSettings]); cleared with the settings by [resetToDefaults]. */
    var lastExportMs: Long?
        get() = prefs.lastExportMs
        set(value) { prefs.lastExportMs = value }
    var exportReminderEnabledMs: Long?
        get() = prefs.exportReminderEnabledMs
        set(value) { prefs.exportReminderEnabledMs = value }
    var exportReminderShownMs: Long?
        get() = prefs.exportReminderShownMs
        set(value) { prefs.exportReminderShownMs = value }

    /** The tutorial was finished or skipped (see [AppSettings]). */
    var tutorialDone: Boolean
        get() = prefs.tutorialDone
        set(value) { prefs.tutorialDone = value }

    /**
     * Every setting back to its default, on the phone and on screen (Delete all data with "Also settings and learned
     * picks"): the stored settings are cleared, every declared setting is read again, then the [Setting.observe]
     * observers of those whose value changed run. No [Setting.onChosen] observer runs: a reset is not a choice.
     */
    fun resetToDefaults() {
        prefs.clear()
        val changed = declared.filter { it.reload() }
        changed.forEach { it.notifyObservers() }
    }

    /**
     * The page of Settings open over the list of groups; null is the list. Kept with the view model, so it survives a
     * recreation (a theme change); after the process was killed Settings is closed anyway. Whether Settings is open at
     * all is a surface of the app and stays with the view model.
     */
    var page by mutableStateOf<SettingsPage?>(null); private set
    fun openPage(page: SettingsPage) { this.page = page }
    /** The list of groups again (Settings opened or closed). */
    fun showList() { page = null }
    /** Back or the arrow: a page goes back by [settingsPageBack]; false on the list, where Settings itself closes. */
    fun back(): Boolean {
        val current = page ?: return false
        page = settingsPageBack(current)
        return true
    }
}
