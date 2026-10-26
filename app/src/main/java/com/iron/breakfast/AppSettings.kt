package com.iron.breakfast

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
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

    private companion object {
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
