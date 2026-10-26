package com.iron.breakfast

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.ui.components.LongDraft
import java.util.Locale

/**
 * The user's settings, in plain SharedPreferences (`settings`). The view model reads them once and writes each change
 * straight back. Every default is the app's behaviour before the setting existed, so nothing changes until one is set.
 */
internal class AppSettings(context: Context) {
    private val prefs = (context.applicationContext ?: context).getSharedPreferences("settings", Context.MODE_PRIVATE)

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

    /** "Count what I'm typing": the word being typed counts and saves (P9); on by default. */
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

    /** When the export reminder was last shown, or null when it never was. */
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
        const val LEARN_FROM_PICKS_KEY = "learn_from_picks"
    }
}

/** A stored enum choice by name; nothing stored, or an unknown name, is [default]. */
internal inline fun <reified T : Enum<T>> enumOf(name: String?, default: T): T = enumValues<T>().firstOrNull { it.name == name } ?: default

/** The stored "Long draft" choice; nothing stored, or an unknown name, is End. */
internal fun longDraftOf(name: String?): LongDraft = enumOf(name, LongDraft.END)

/** The day start one step from [hour]; it wraps 23 to 00 and 00 to 23. */
internal fun shiftDayStartHour(hour: Int, delta: Int): Int = Math.floorMod(hour + delta, 24)

/** "04:00". */
internal fun dayStartLabel(hour: Int): String = String.format(Locale.ROOT, "%02d:00", hour)

/** The "Undo" choice. */
internal enum class UndoWindow { FIFTEEN_SECONDS, FIVE_SECONDS, THIRTY_SECONDS, UNTIL_NEXT_ACTION }

/** How long an offer of [window] stays, in ms; null until the next log, edit or delete replaces it. */
internal fun undoWindowMs(window: UndoWindow): Long? = when (window) {
    UndoWindow.FIVE_SECONDS -> 5_000L
    UndoWindow.FIFTEEN_SECONDS -> UNDO_WINDOW_MS
    UndoWindow.THIRTY_SECONDS -> 30_000L
    UndoWindow.UNTIL_NEXT_ACTION -> null
}

/** The "Meal" choice: what a log with no chosen, typed or preset meal goes to. */
internal enum class MealMode { ASK, BY_TIME, LAST_USED }

/** "By time": before [breakfastUntil] o'clock is Breakfast, then Lunch, then Dinner, then Snacks. Each is later than the one before. */
internal data class MealBounds(val breakfastUntil: Int = 12, val lunchUntil: Int = 17, val dinnerUntil: Int = 22) {
    /** True when every boundary is a whole hour from 01:00 to 23:00 and later than the one above it. */
    val valid: Boolean get() = breakfastUntil in 1..23 && lunchUntil in 1..23 && dinnerUntil in 1..23 && breakfastUntil < lunchUntil && lunchUntil < dinnerUntil
}

/** Stored boundaries, or the defaults when any is missing or they are not in order. */
internal fun mealBoundsOf(breakfastUntil: Int, lunchUntil: Int, dinnerUntil: Int): MealBounds =
    MealBounds(breakfastUntil, lunchUntil, dinnerUntil).takeIf { it.valid } ?: MealBounds()

/** The boundary that ends [meal]'s time; Snacks has none. */
internal fun MealBounds.untilOf(meal: MealSlot): Int? = when (meal) {
    MealSlot.BREAKFAST -> breakfastUntil
    MealSlot.LUNCH -> lunchUntil
    MealSlot.DINNER -> dinnerUntil
    MealSlot.SNACKS -> null
}

/** [bounds] with [meal]'s boundary moved [delta] hours; unchanged when that would break the order or leave 01:00 to 23:00. */
internal fun shiftMealBound(bounds: MealBounds, meal: MealSlot, delta: Int): MealBounds {
    val shifted = when (meal) {
        MealSlot.BREAKFAST -> bounds.copy(breakfastUntil = bounds.breakfastUntil + delta)
        MealSlot.LUNCH -> bounds.copy(lunchUntil = bounds.lunchUntil + delta)
        MealSlot.DINNER -> bounds.copy(dinnerUntil = bounds.dinnerUntil + delta)
        MealSlot.SNACKS -> bounds
    }
    return if (shifted.valid) shifted else bounds
}

/** True when [meal]'s boundary can move [delta] hours. */
internal fun canShiftMealBound(bounds: MealBounds, meal: MealSlot, delta: Int): Boolean = shiftMealBound(bounds, meal, delta) != bounds

/** The meal for the wall-clock [hour] (0 to 23) by [bounds]. */
internal fun mealByTime(hour: Int, bounds: MealBounds): MealSlot = when {
    hour < bounds.breakfastUntil -> MealSlot.BREAKFAST
    hour < bounds.lunchUntil -> MealSlot.LUNCH
    hour < bounds.dinnerUntil -> MealSlot.DINNER
    else -> MealSlot.SNACKS
}

/**
 * The meal a log goes to when none is chosen, typed or preset: null for Ask (the meal menu opens), the meal for
 * [hour] for By time, and [lastUsed] (the meal of the latest logged entry, null when there is none) for Last used.
 */
internal fun automaticMeal(mode: MealMode, hour: Int, bounds: MealBounds, lastUsed: MealSlot?): MealSlot? = when (mode) {
    MealMode.ASK -> null
    MealMode.BY_TIME -> mealByTime(hour, bounds)
    MealMode.LAST_USED -> lastUsed
}

/** The "Theme" choice. */
internal enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/** Whether the app draws dark: the phone's mode ([systemDark]) for System, else the forced one. */
internal fun darkThemeOf(choice: ThemeChoice, systemDark: Boolean): Boolean = when (choice) {
    ThemeChoice.SYSTEM -> systemDark
    ThemeChoice.LIGHT -> false
    ThemeChoice.DARK -> true
}

/**
 * The per-app night mode for `UiModeManager.setApplicationNightMode` (API 31+): the system then applies it to the
 * app's configuration and to its launch window. AUTO removes the override, so the app follows the phone.
 */
internal fun applicationNightModeOf(choice: ThemeChoice): Int = when (choice) {
    ThemeChoice.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
    ThemeChoice.LIGHT -> UiModeManager.MODE_NIGHT_NO
    ThemeChoice.DARK -> UiModeManager.MODE_NIGHT_YES
}

/**
 * Below API 31: the activity's own uiMode with its night bits forced by [choice], applied as an override configuration
 * before the window exists, so its theme (the `values-night` window background) and Compose both follow. Null for
 * System. The system's launch window cannot be changed there; it keeps the phone's mode.
 */
internal fun forcedUiMode(baseUiMode: Int, choice: ThemeChoice): Int? {
    val night = when (choice) {
        ThemeChoice.SYSTEM -> return null
        ThemeChoice.LIGHT -> Configuration.UI_MODE_NIGHT_NO
        ThemeChoice.DARK -> Configuration.UI_MODE_NIGHT_YES
    }
    return (baseUiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
}

/** The "Decimal mark" choice: how numbers are shown. Input reads both marks whatever it is. */
internal enum class DecimalMark { POINT, COMMA }

/** The decimal mark shown now. Snapshot state, so a composition that formatted a number reads it again on a change. */
internal object DisplayDecimal {
    var mark by mutableStateOf(DecimalMark.POINT)
}

/** [text] with every decimal point between two digits shown as [mark]. */
internal fun markDecimals(text: String, mark: DecimalMark): String =
    if (mark == DecimalMark.POINT) text else DECIMAL_POINT.replace(text, ",")

private val DECIMAL_POINT = Regex("""(?<=\d)\.(?=\d)""")

/** The "Motion" choice: Reduced turns motion off as the phone's "Remove animations" does. */
internal enum class MotionChoice { SYSTEM, REDUCED }

/** The "Text size" choice: Larger is one step above the phone's font size, inside the app only. */
internal enum class TextSizeChoice { SYSTEM, LARGER }

/** The step "Larger" adds on top of the phone's font scale. */
internal const val LARGER_TEXT_STEP = 1.15f

/** The font scale the app uses over the phone's [systemFontScale]; null for System (no override). */
internal fun appFontScale(systemFontScale: Float, choice: TextSizeChoice): Float? = when (choice) {
    TextSizeChoice.SYSTEM -> null
    TextSizeChoice.LARGER -> systemFontScale * LARGER_TEXT_STEP
}

/**
 * What the activity overrides in its configuration, in `attachBaseContext` before its window exists, so every window it
 * opens (dialogs and sheets too) and Compose's `LocalDensity` follow: the forced night bits below API 31 (see
 * [forcedUiMode]; from 31 the per-app night mode does it) and the Larger font scale ([appFontScale]).
 */
internal data class ActivityOverride(val uiMode: Int?, val fontScale: Float?)

/** The [ActivityOverride] for these settings; null when nothing is overridden (both System). */
internal fun activityOverrideOf(baseUiMode: Int, systemFontScale: Float, sdk: Int, theme: ThemeChoice, textSize: TextSizeChoice): ActivityOverride? {
    val uiMode = if (sdk < 31) forcedUiMode(baseUiMode, theme) else null
    val fontScale = appFontScale(systemFontScale, textSize)
    return if (uiMode == null && fontScale == null) null else ActivityOverride(uiMode, fontScale)
}

/** The standard haptic for the tick on Add and Undo: CONFIRM from API 30, VIRTUAL_KEY before; null when Haptics is off. */
internal fun tickHapticFor(enabled: Boolean, sdk: Int): Int? = when {
    !enabled -> null
    sdk >= 30 -> HapticFeedbackConstants.CONFIRM
    else -> HapticFeedbackConstants.VIRTUAL_KEY
}

/** What a scanned or typed barcode that is not in the Library leads to. */
internal enum class UnknownBarcodeStep { LOOK_UP, MANUAL_FORM }

/** "Look up unknown barcodes" On asks Open Food Facts; Off opens the manual product form with no network request. */
internal fun unknownBarcodeStep(lookUp: Boolean): UnknownBarcodeStep = if (lookUp) UnknownBarcodeStep.LOOK_UP else UnknownBarcodeStep.MANUAL_FORM

/** The Contribute to Open Food Facts action shows where it is [available], unless "Contribute" is Hide. */
internal fun contributionShown(show: Boolean, available: Boolean): Boolean = show && available

/** The "Export reminder" choice. */
internal enum class ExportReminder { NEVER, WEEKLY, MONTHLY }

/** The time between reminders: 7 days, 30 days, or none for Never. */
internal fun exportReminderIntervalMs(reminder: ExportReminder): Long? = when (reminder) {
    ExportReminder.NEVER -> null
    ExportReminder.WEEKLY -> 7L * DAY_MS
    ExportReminder.MONTHLY -> 30L * DAY_MS
}

private const val DAY_MS = 86_400_000L

/**
 * True when Daily should show "Export your diary." now: a whole interval has passed since the later of the last export
 * and the last time the reminder was shown (so it shows once, and again one interval later if no export was made).
 * Never exported and never shown: due at once. A clock set back before either is not due.
 */
internal fun exportReminderDue(reminder: ExportReminder, lastExportMs: Long?, lastShownMs: Long?, nowMs: Long): Boolean {
    val interval = exportReminderIntervalMs(reminder) ?: return false
    val since = listOfNotNull(lastExportMs, lastShownMs).maxOrNull() ?: return true
    return nowMs >= since && nowMs - since >= interval
}

/** What happens to the safety copy before the diary is replaced or deleted. */
internal enum class SafetyCopyStep { WRITE, DELETE }

/**
 * Import with "Safety copy" Keep writes it, with Don't keep removes any old one and writes none. Delete all data always
 * writes it first, whatever the setting.
 */
internal fun safetyCopyStep(keep: Boolean, deletingAll: Boolean): SafetyCopyStep =
    if (keep || deletingAll) SafetyCopyStep.WRITE else SafetyCopyStep.DELETE

/** Delete all data runs only from its confirm dialog, once storage is open and no other write runs. */
internal fun deleteAllAllowed(dialogOpen: Boolean, ready: Boolean, busy: Boolean): Boolean = dialogOpen && ready && !busy

/** About, Version: "0.1 (10)", the versionName then the versionCode. */
internal fun versionLabel(versionName: String, versionCode: Int): String = "$versionName ($versionCode)"

/** About, Licences: the bundled font licence, shown as plain text. */
internal const val LICENCES_ASSET = "OFL.txt"
