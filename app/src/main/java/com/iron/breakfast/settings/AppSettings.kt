package com.iron.breakfast.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.iron.breakfast.ui.components.LongDraft

/** The few reads and writes [AppSettings] makes; SharedPreferences in the app ([SharedPreferencesStore]), a map in tests. */
internal interface SettingsStore {
    fun getInt(key: String, default: Int): Int
    fun getLong(key: String, default: Long): Long
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getString(key: String, default: String?): String?
    /** Applies the writes [block] makes, as one SharedPreferences edit. */
    fun edit(block: Editor.() -> Unit)
    /** As [edit], on disk before it returns. */
    fun commit(block: Editor.() -> Unit)

    interface Editor {
        fun putInt(key: String, value: Int)
        fun putLong(key: String, value: Long)
        fun putBoolean(key: String, value: Boolean)
        fun putString(key: String, value: String)
        fun clear()
    }
}

/** [SettingsStore] over SharedPreferences; each [edit] is applied asynchronously, as `SharedPreferences.edit {}` does. */
internal class SharedPreferencesStore(private val prefs: SharedPreferences) : SettingsStore {
    override fun getInt(key: String, default: Int) = prefs.getInt(key, default)
    override fun getLong(key: String, default: Long) = prefs.getLong(key, default)
    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun getString(key: String, default: String?) = prefs.getString(key, default)
    override fun edit(block: SettingsStore.Editor.() -> Unit) = write(commit = false, block)
    override fun commit(block: SettingsStore.Editor.() -> Unit) = write(commit = true, block)

    private fun write(commit: Boolean, block: SettingsStore.Editor.() -> Unit) = prefs.edit(commit) {
        val editor = this
        object : SettingsStore.Editor {
            override fun putInt(key: String, value: Int) { editor.putInt(key, value) }
            override fun putLong(key: String, value: Long) { editor.putLong(key, value) }
            override fun putBoolean(key: String, value: Boolean) { editor.putBoolean(key, value) }
            override fun putString(key: String, value: String) { editor.putString(key, value) }
            override fun clear() { editor.clear() }
        }.block()
    }
}

/**
 * The user's settings, in plain SharedPreferences (`settings`): one typed property per stored value. [SettingsState]
 * reads them once and writes each change straight back; `MainActivity` reads Theme and Text size before its window
 * exists. Every default is the app's behaviour before the setting existed, so nothing changes until one is set.
 */
internal class AppSettings(private val prefs: SettingsStore) {
    constructor(context: Context) : this(
        SharedPreferencesStore((context.applicationContext ?: context).getSharedPreferences("settings", Context.MODE_PRIVATE)),
    )

    /** The hour (0 to 23) a diary day starts at; 0 is midnight. */
    var dayStartHour: Int
        get() = prefs.getInt(DAY_START_HOUR_KEY, 0).coerceIn(0, 23)
        set(value) = prefs.edit { putInt(DAY_START_HOUR_KEY, value.coerceIn(0, 23)) }

    /** How the composer shows a long draft; End by default. */
    var longDraft: LongDraft
        get() = longDraftOf(prefs.getString(LONG_DRAFT_KEY, null))
        set(value) = prefs.edit { putString(LONG_DRAFT_KEY, value.name) }

    /** How long an Undo stays offered; 15 s by default. */
    var undoWindow: UndoWindow
        get() = enumOf(prefs.getString(UNDO_WINDOW_KEY, null), UndoWindow.FIFTEEN_SECONDS)
        set(value) = prefs.edit { putString(UNDO_WINDOW_KEY, value.name) }

    /** The meal a log without one goes to; Ask (the meal menu opens) by default. */
    var mealMode: MealMode
        get() = enumOf(prefs.getString(MEAL_MODE_KEY, null), MealMode.ASK)
        set(value) = prefs.edit { putString(MEAL_MODE_KEY, value.name) }

    /** The "By time" boundaries; 12:00, 17:00 and 22:00 by default. */
    var mealBounds: MealBounds
        get() = mealBoundsOf(prefs.getInt(BREAKFAST_UNTIL_KEY, -1), prefs.getInt(LUNCH_UNTIL_KEY, -1), prefs.getInt(DINNER_UNTIL_KEY, -1))
        set(value) = prefs.edit {
            putInt(BREAKFAST_UNTIL_KEY, value.breakfastUntil); putInt(LUNCH_UNTIL_KEY, value.lunchUntil); putInt(DINNER_UNTIL_KEY, value.dinnerUntil)
        }

    /** "Before day start": where By time puts a log made before "Day starts at"; Snacks by default. */
    var beforeDayStart: BeforeDayStart
        get() = enumOf(prefs.getString(BEFORE_DAY_START_KEY, null), BeforeDayStart.SNACKS)
        set(value) = prefs.edit { putString(BEFORE_DAY_START_KEY, value.name) }

    /** "Count what I'm typing": the word being typed counts (P9); on by default. */
    var countTyping: Boolean
        get() = prefs.getBoolean(COUNT_TYPING_KEY, true)
        set(value) = prefs.edit { putBoolean(COUNT_TYPING_KEY, value) }

    /** "Learn from my picks": learned preferences rank and picks are learned; on by default. */
    var learnFromPicks: Boolean
        get() = prefs.getBoolean(LEARN_FROM_PICKS_KEY, true)
        set(value) = prefs.edit { putBoolean(LEARN_FROM_PICKS_KEY, value) }

    /** Theme: System (the phone's light or dark) by default. */
    var theme: ThemeChoice
        get() = enumOf(prefs.getString(THEME_KEY, null), ThemeChoice.SYSTEM)
        set(value) = prefs.edit { putString(THEME_KEY, value.name) }

    /** "Macros on today": the protein, carbs and fat block on Daily; shown by default. */
    var showMacros: Boolean
        get() = prefs.getBoolean(SHOW_MACROS_KEY, true)
        set(value) = prefs.edit { putBoolean(SHOW_MACROS_KEY, value) }

    /** "Missing values note": the line under the macros when one shows "?"; shown by default. */
    var showMissingNote: Boolean
        get() = prefs.getBoolean(SHOW_MISSING_NOTE_KEY, true)
        set(value) = prefs.edit { putBoolean(SHOW_MISSING_NOTE_KEY, value) }

    /** The decimal mark numbers are shown with; Point by default. Input accepts both either way. */
    var decimalMark: DecimalMark
        get() = enumOf(prefs.getString(DECIMAL_MARK_KEY, null), DecimalMark.POINT)
        set(value) = prefs.edit { putString(DECIMAL_MARK_KEY, value.name) }

    /** Motion: System (the phone's "Remove animations") by default. */
    var motion: MotionChoice
        get() = enumOf(prefs.getString(MOTION_KEY, null), MotionChoice.SYSTEM)
        set(value) = prefs.edit { putString(MOTION_KEY, value.name) }

    /** Text size: System (the phone's font size) by default. */
    var textSize: TextSizeChoice
        get() = enumOf(prefs.getString(TEXT_SIZE_KEY, null), TextSizeChoice.SYSTEM)
        set(value) = prefs.edit { putString(TEXT_SIZE_KEY, value.name) }

    /** Haptics: a tick on Add and Undo; off by default. */
    var haptics: Boolean
        get() = prefs.getBoolean(HAPTICS_KEY, false)
        set(value) = prefs.edit { putBoolean(HAPTICS_KEY, value) }

    /** "Flashlight at start": the scanner opens with the torch on; off by default. */
    var flashlightAtStart: Boolean
        get() = prefs.getBoolean(FLASHLIGHT_AT_START_KEY, false)
        set(value) = prefs.edit { putBoolean(FLASHLIGHT_AT_START_KEY, value) }

    /** "Look up unknown barcodes": ask Open Food Facts for a barcode not in the Library; on by default. */
    var lookUpBarcodes: Boolean
        get() = prefs.getBoolean(LOOK_UP_BARCODES_KEY, true)
        set(value) = prefs.edit { putBoolean(LOOK_UP_BARCODES_KEY, value) }

    /** "Contribute": the Contribute to Open Food Facts action is offered; shown by default. */
    var showContribute: Boolean
        get() = prefs.getBoolean(SHOW_CONTRIBUTE_KEY, true)
        set(value) = prefs.edit { putBoolean(SHOW_CONTRIBUTE_KEY, value) }

    /** "Export reminder": Never by default. */
    var exportReminder: ExportReminder
        get() = enumOf(prefs.getString(EXPORT_REMINDER_KEY, null), ExportReminder.NEVER)
        set(value) = prefs.edit { putString(EXPORT_REMINDER_KEY, value.name) }

    /** When the diary was last exported, or null when it never was. */
    var lastExportMs: Long?
        get() = prefs.getLong(LAST_EXPORT_KEY, -1L).takeIf { it >= 0L }
        set(value) = prefs.edit { putLong(LAST_EXPORT_KEY, value ?: -1L) }

    /** When the export reminder was turned on (from Never), or null: the first reminder comes one interval after it. */
    var exportReminderEnabledMs: Long?
        get() = prefs.getLong(EXPORT_REMINDER_ENABLED_KEY, -1L).takeIf { it >= 0L }
        set(value) = prefs.edit { putLong(EXPORT_REMINDER_ENABLED_KEY, value ?: -1L) }

    /** When the export reminder was last on screen, or null when it never was. */
    var exportReminderShownMs: Long?
        get() = prefs.getLong(EXPORT_REMINDER_SHOWN_KEY, -1L).takeIf { it >= 0L }
        set(value) = prefs.edit { putLong(EXPORT_REMINDER_SHOWN_KEY, value ?: -1L) }

    /** "Safety copy": Import keeps the diary it replaces in `no_backup`; Keep by default. */
    var keepSafetyCopy: Boolean
        get() = prefs.getBoolean(KEEP_SAFETY_COPY_KEY, true)
        set(value) = prefs.edit { putBoolean(KEEP_SAFETY_COPY_KEY, value) }

    /** Delete all data with "Also settings and learned picks": every setting goes back to its default. */
    fun clear() = prefs.edit { clear() }

    private companion object {
        const val EXPORT_REMINDER_KEY = "export_reminder"
        const val LAST_EXPORT_KEY = "last_export_ms"
        const val EXPORT_REMINDER_SHOWN_KEY = "export_reminder_shown_ms"
        const val EXPORT_REMINDER_ENABLED_KEY = "export_reminder_enabled_ms"
        const val KEEP_SAFETY_COPY_KEY = "keep_safety_copy"
        const val FLASHLIGHT_AT_START_KEY = "flashlight_at_start"
        const val LOOK_UP_BARCODES_KEY = "look_up_barcodes"
        const val SHOW_CONTRIBUTE_KEY = "show_contribute"
        const val MOTION_KEY = "motion"
        const val TEXT_SIZE_KEY = "text_size"
        const val HAPTICS_KEY = "haptics"
        const val THEME_KEY = "theme"
        const val SHOW_MACROS_KEY = "show_macros"
        const val SHOW_MISSING_NOTE_KEY = "show_missing_note"
        const val DECIMAL_MARK_KEY = "decimal_mark"
        const val DAY_START_HOUR_KEY = "day_start_hour"
        const val LONG_DRAFT_KEY = "long_draft"
        const val UNDO_WINDOW_KEY = "undo_window"
        const val MEAL_MODE_KEY = "meal_mode"
        const val BREAKFAST_UNTIL_KEY = "breakfast_until"
        const val LUNCH_UNTIL_KEY = "lunch_until"
        const val DINNER_UNTIL_KEY = "dinner_until"
        const val COUNT_TYPING_KEY = "count_typing"
        const val BEFORE_DAY_START_KEY = "before_day_start"
        const val LEARN_FROM_PICKS_KEY = "learn_from_picks"
    }
}

/** A stored enum choice by name; nothing stored, or an unknown name, is [default]. */
internal inline fun <reified T : Enum<T>> enumOf(name: String?, default: T): T = enumValues<T>().firstOrNull { it.name == name } ?: default

/** The stored "Long draft" choice; nothing stored, or an unknown name, is End. */
internal fun longDraftOf(name: String?): LongDraft = enumOf(name, LongDraft.END)
