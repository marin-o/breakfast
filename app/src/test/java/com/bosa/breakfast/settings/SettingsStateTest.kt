package com.bosa.breakfast.settings

import com.bosa.breakfast.domain.MealSlot
import com.bosa.breakfast.ui.components.LongDraft
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** An in-memory [SettingsStore], as SharedPreferences stores the values. */
internal class FakeSettingsStore : SettingsStore {
    val values = mutableMapOf<String, Any>()
    /** How many writes had to reach the disk before returning. */
    var commits = 0
    override fun commit(block: SettingsStore.Editor.() -> Unit) { commits++; edit(block) }
    override fun getInt(key: String, default: Int) = values[key] as Int? ?: default
    override fun getLong(key: String, default: Long) = values[key] as Long? ?: default
    override fun getBoolean(key: String, default: Boolean) = values[key] as Boolean? ?: default
    override fun getString(key: String, default: String?) = values[key] as String? ?: default
    override fun edit(block: SettingsStore.Editor.() -> Unit) {
        object : SettingsStore.Editor {
            override fun putInt(key: String, value: Int) { values[key] = value }
            override fun putLong(key: String, value: Long) { values[key] = value }
            override fun putBoolean(key: String, value: Boolean) { values[key] = value }
            override fun putString(key: String, value: String) { values[key] = value }
            override fun clear() { values.clear() }
        }.block()
    }
}

class SettingsStateTest {
    /** "Also settings and learned picks" clears the settings; the tutorial stays done, and stays not done. */
    @Test fun aResetLeavesTheTutorialAsItWas() {
        val done = FakeSettingsStore()
        AppSettings(done).tutorialDone = true
        AppSettings(done).haptics = true
        SettingsState(AppSettings(done)) { 0L }.resetToDefaults()
        assertTrue(AppSettings(done).tutorialDone)
        assertFalse(AppSettings(done).haptics)
        val notYet = FakeSettingsStore()
        SettingsState(AppSettings(notYet)) { 0L }.resetToDefaults()
        assertFalse(AppSettings(notYet).tutorialDone)
    }

    private val store = FakeSettingsStore()
    private var now = 1_000L
    private val state = SettingsState(AppSettings(store)) { now }
    private val defaults = AppSettings(FakeSettingsStore())

    @After fun pointAgain() { DisplayDecimal.mark = DecimalMark.POINT }

    /** A stored value other than [value], for any setting type; a new type fails here until it is handled. */
    private fun other(value: Any?): Any = when (value) {
        is Boolean -> !value
        is Int -> (value + 5) % 24
        is Enum<*> -> value.declaringJavaClass.enumConstants.let { it[(value.ordinal + 1) % it.size] }
        is MealBounds -> MealBounds(11, 16, 21)
        else -> throw AssertionError("No other value for a setting of type ${value?.javaClass}")
    }

    @Suppress("UNCHECKED_CAST")
    private fun SettingsState.Setting<*>.setAny(value: Any) = (this as SettingsState.Setting<Any>).set(value)

    @Test fun everySettingStartsAtItsDefault() {
        state.all.forEach { setting ->
            assertEquals(setting.name, AppSettings::class.java.getMethod(getter(setting.name)).invoke(defaults), setting.value)
        }
    }

    @Test fun everySettingWritesTheScreenAndThePhone() {
        state.all.forEach { setting ->
            val before = setting.value
            setting.setAny(other(before))
            assertNotEquals(setting.name, before, setting.value)
            // A fresh adapter over the same store reads the new value back: it was persisted.
            assertEquals(setting.name, AppSettings::class.java.getMethod(getter(setting.name)).invoke(AppSettings(store)), setting.value)
        }
    }

    @Test fun resetReturnsEveryDeclaredSettingToItsDefaultAndTellsItsObservers() {
        val told = mutableListOf<String>()
        state.all.forEach { setting -> setting.observe { told += setting.name } }
        state.all.forEach { it.setAny(other(it.value)) }
        state.lastExportMs = 5L
        told.clear()
        state.resetToDefaults()
        state.all.forEach { setting ->
            assertEquals(setting.name, AppSettings::class.java.getMethod(getter(setting.name)).invoke(defaults), setting.value)
        }
        assertEquals(state.all.map { it.name }, told)
        assertTrue("every stored value is cleared, the export times too", store.values.isEmpty())
        assertNull(state.lastExportMs)
    }

    @Test fun aResetTellsOnlyTheSettingsItChanged() {
        val told = mutableListOf<String>()
        state.all.forEach { setting -> setting.observe { told += setting.name } }
        state.chooseHaptics(true)
        told.clear()
        state.resetToDefaults()
        assertEquals(listOf("haptics"), told)
    }

    /**
     * Haptics stands in for a Safety copy whose default were Don't keep (both are Booleans; Haptics defaults to off):
     * a reset to such a default is not the user choosing it, so an effect wired to the choice (the safety copy's
     * removal, `AppOwners`) does not run, while the value's observers do.
     */
    @Test fun aResetIsNotAChoice() {
        val chosen = mutableListOf<Boolean>()
        val observed = mutableListOf<Boolean>()
        state.hapticsSetting.onChosen { chosen += it }
        state.hapticsSetting.observe { observed += it }
        state.chooseHaptics(true)
        state.resetToDefaults()
        assertFalse(state.haptics)
        assertEquals("only the user's choice", listOf(true), chosen)
        assertEquals(listOf(true, false), observed)
        // The real Safety copy: a choice runs it, a reset never.
        val keep = mutableListOf<Boolean>()
        state.keepSafetyCopySetting.onChosen { keep += it }
        state.chooseKeepSafetyCopy(false)
        state.resetToDefaults()
        assertEquals(listOf(false), keep)
    }

    @Test fun everyStoredSettingIsDeclaredOnce() {
        // Every AppSettings property with a setter is a setting, except the export reminder's times, which are not
        // shown and go with a reset through the cleared store, and the tutorial's done, which a reset keeps.
        val bookkeeping = setOf("lastExportMs", "exportReminderEnabledMs", "exportReminderShownMs", "tutorialDone")
        val stored = AppSettings::class.java.methods.map { it.name }.filter { it.startsWith("set") }
            .map { it.removePrefix("set").replaceFirstChar(Char::lowercaseChar) }.toSet() - bookkeeping
        val declared = state.all.map { it.name }
        assertEquals(declared.size, declared.toSet().size)
        assertEquals(stored, declared.toSet())
    }

    @Test fun eachSetterChangesItsOwnSetting() {
        state.shiftDayStart(1); assertEquals(1, state.dayStartHour)
        state.shiftDayStart(-2); assertEquals(23, state.dayStartHour)
        state.chooseUndoWindow(UndoWindow.FIVE_SECONDS); assertEquals(UndoWindow.FIVE_SECONDS, state.undoWindow)
        state.chooseMealMode(MealMode.BY_TIME); assertEquals(MealMode.BY_TIME, state.mealMode)
        state.stepMealBound(MealSlot.BREAKFAST, -1); assertEquals(MealBounds(11, 17, 22), state.mealBounds)
        state.stepMealBound(MealSlot.LUNCH, 10); assertEquals(MealBounds(11, 17, 22), state.mealBounds)
        state.chooseBeforeDayStart(BeforeDayStart.ASK); assertEquals(BeforeDayStart.ASK, state.beforeDayStart)
        state.chooseCountTyping(false); assertFalse(state.countTyping)
        state.chooseLearnFromPicks(false); assertFalse(state.learnFromPicks)
        state.chooseTheme(ThemeChoice.DARK); assertEquals(ThemeChoice.DARK, state.theme)
        LongDraft.entries.first { it != LongDraft.END }.let { state.chooseLongDraft(it); assertEquals(it, state.longDraft) }
        state.chooseShowMacros(false); assertFalse(state.showMacros)
        state.chooseShowMissingNote(false); assertFalse(state.showMissingNote)
        state.chooseDecimalMark(DecimalMark.COMMA); assertEquals(DecimalMark.COMMA, state.decimalMark)
        state.chooseMotion(MotionChoice.REDUCED); assertEquals(MotionChoice.REDUCED, state.motion)
        state.chooseTextSize(TextSizeChoice.LARGER); assertEquals(TextSizeChoice.LARGER, state.textSize)
        state.chooseHaptics(true); assertTrue(state.haptics)
        state.chooseFlashlightAtStart(true); assertTrue(state.flashlightAtStart)
        state.chooseLookUpBarcodes(false); assertFalse(state.lookUpBarcodes)
        state.chooseShowContribute(false); assertFalse(state.showContribute)
        state.chooseExportReminder(ExportReminder.WEEKLY); assertEquals(ExportReminder.WEEKLY, state.exportReminder)
        state.chooseKeepSafetyCopy(false); assertFalse(state.keepSafetyCopy)
        // Each one is persisted.
        val reread = SettingsState(AppSettings(store)) { now }
        state.all.zip(reread.all).forEach { (a, b) -> assertEquals(a.name, a.value, b.value) }
    }

    @Test fun theStoredKeysAreTheOnesTheAppHasAlwaysUsed() {
        state.all.forEach { it.setAny(other(it.value)) }
        state.lastExportMs = 1L; state.exportReminderShownMs = 2L; state.exportReminderEnabledMs = 3L
        assertEquals(
            setOf(
                "day_start_hour", "undo_window", "meal_mode", "breakfast_until", "lunch_until", "dinner_until", "before_day_start",
                "count_typing", "learn_from_picks", "theme", "long_draft", "show_macros", "show_missing_note", "decimal_mark",
                "motion", "text_size", "haptics", "flashlight_at_start", "look_up_barcodes", "show_contribute", "export_reminder",
                "keep_safety_copy", "last_export_ms", "export_reminder_shown_ms", "export_reminder_enabled_ms",
            ),
            store.values.keys,
        )
        assertEquals("FIVE_SECONDS", store.values["undo_window"])
        assertEquals(5, store.values["day_start_hour"])
    }

    @Test fun anObserverRunsAfterEachChangeWithTheNewValue() {
        val seen = mutableListOf<UndoWindow>()
        state.undoWindowSetting.observe { seen += it }
        state.chooseUndoWindow(UndoWindow.THIRTY_SECONDS)
        // Choosing the same value again still tells the observers, as the setters always did.
        state.chooseUndoWindow(UndoWindow.THIRTY_SECONDS)
        assertEquals(listOf(UndoWindow.THIRTY_SECONDS, UndoWindow.THIRTY_SECONDS), seen)
    }

    @Test fun theShownDecimalMarkFollowsTheSetting() {
        store.values["decimal_mark"] = "COMMA"
        val withComma = SettingsState(AppSettings(store)) { now }
        assertEquals(DecimalMark.COMMA, DisplayDecimal.mark)
        withComma.chooseDecimalMark(DecimalMark.POINT)
        assertEquals(DecimalMark.POINT, DisplayDecimal.mark)
        withComma.chooseDecimalMark(DecimalMark.COMMA)
        withComma.resetToDefaults()
        assertEquals(DecimalMark.POINT, DisplayDecimal.mark)
    }

    @Test fun turningTheExportReminderOnFromNeverStampsWhen() {
        now = 7_000L
        state.chooseExportReminder(ExportReminder.WEEKLY)
        assertEquals(7_000L, state.exportReminderEnabledMs)
        now = 9_000L
        // Weekly to Monthly is not a new start; Never and on again is.
        state.chooseExportReminder(ExportReminder.MONTHLY)
        assertEquals(7_000L, state.exportReminderEnabledMs)
        state.chooseExportReminder(ExportReminder.NEVER)
        assertEquals(7_000L, state.exportReminderEnabledMs)
        state.chooseExportReminder(ExportReminder.WEEKLY)
        assertEquals(9_000L, state.exportReminderEnabledMs)
    }

    @Test fun backGoesFromAPageToTheListAndFromLicencesToAbout() {
        assertNull(state.page)
        assertFalse(state.back())
        state.openPage(SettingsPage.ABOUT)
        state.openPage(SettingsPage.LICENCES)
        assertTrue(state.back())
        assertEquals(SettingsPage.ABOUT, state.page)
        assertTrue(state.back())
        assertNull(state.page)
        state.openPage(SettingsPage.DATA)
        state.showList()
        assertNull(state.page)
    }

    private fun getter(name: String) = "get" + name.replaceFirstChar(Char::uppercaseChar)
}
