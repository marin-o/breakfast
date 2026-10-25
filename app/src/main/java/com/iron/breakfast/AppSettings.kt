package com.iron.breakfast

import android.content.Context
import androidx.core.content.edit
import com.iron.breakfast.ui.components.LongDraft
import java.util.Locale

/**
 * The user's settings, in plain SharedPreferences (`settings`). The view model reads them once and writes each change
 * straight back.
 */
internal class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The hour (0 to 23) a diary day starts at; 0 is midnight. */
    var dayStartHour: Int
        get() = prefs.getInt(DAY_START_HOUR_KEY, 0).coerceIn(0, 23)
        set(value) = prefs.edit { putInt(DAY_START_HOUR_KEY, value.coerceIn(0, 23)) }

    /** How the composer shows a long draft; End by default. */
    var longDraft: LongDraft
        get() = longDraftOf(prefs.getString(LONG_DRAFT_KEY, null))
        set(value) = prefs.edit { putString(LONG_DRAFT_KEY, value.name) }

    private companion object {
        const val DAY_START_HOUR_KEY = "day_start_hour"
        const val LONG_DRAFT_KEY = "long_draft"
    }
}

/** The stored "Long draft" choice; nothing stored, or an unknown name, is End. */
internal fun longDraftOf(name: String?): LongDraft = LongDraft.entries.firstOrNull { it.name == name } ?: LongDraft.END

/** The day start one step from [hour]; it wraps 23 to 00 and 00 to 23. */
internal fun shiftDayStartHour(hour: Int, delta: Int): Int = Math.floorMod(hour + delta, 24)

/** "04:00". */
internal fun dayStartLabel(hour: Int): String = String.format(Locale.ROOT, "%02d:00", hour)
