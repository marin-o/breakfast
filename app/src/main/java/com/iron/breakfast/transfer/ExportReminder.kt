package com.iron.breakfast.transfer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iron.breakfast.app.DiarySession
import com.iron.breakfast.settings.ExportReminder
import com.iron.breakfast.settings.SettingsState
import com.iron.breakfast.Overlay

/** The time between reminders: 7 days, 30 days, or none for Never. */
internal fun exportReminderIntervalMs(reminder: ExportReminder): Long? = when (reminder) {
    ExportReminder.NEVER -> null
    ExportReminder.WEEKLY -> 7L * DAY_MS
    ExportReminder.MONTHLY -> 30L * DAY_MS
}

private const val DAY_MS = 86_400_000L

/**
 * True when "Export your diary." is due: a whole interval has passed since the latest of the last export, the last
 * time the reminder was on screen, and the moment it was turned on ([enabledMs]). So the first one comes one interval
 * after it is turned on, it shows once, and again one interval later if no export was made. With no baseline at all
 * it is not due; a clock set back before the baseline is not due either.
 */
internal fun exportReminderDue(reminder: ExportReminder, lastExportMs: Long?, lastShownMs: Long?, enabledMs: Long?, nowMs: Long): Boolean {
    val interval = exportReminderIntervalMs(reminder) ?: return false
    val since = listOfNotNull(lastExportMs, lastShownMs, enabledMs).maxOrNull() ?: return false
    return nowMs >= since && nowMs - since >= interval
}

/** The export reminder is drawn only on Daily itself: not under the Log page or any screen or sheet over Daily. */
internal fun exportReminderOnScreen(due: Boolean, logOpen: Boolean, overlay: Overlay?): Boolean = due && !logOpen && overlay == null

/**
 * "Export your diary." on Daily: whether it is due now ([visible]), and recording that it was shown.
 *
 * - [check] marks it due by [exportReminderDue] (on opening storage, on a return to the app, and when the setting
 *   changes); turning the reminder on from Never starts its interval now.
 * - It counts as shown only once Daily draws it ([onScreenNow]), at most once per time it became due.
 * - [hide] takes it away: leaving the app, a successful export, the reminder's Export, or the setting set to Never.
 *
 * Its times are settings ([SettingsState]); [clock] gives now.
 */
internal class ExportReminders(
    private val settings: SettingsState,
    private val session: DiarySession,
    private val clock: () -> Long,
    /** Opens the save picker, as Settings, Export diary does. */
    private val requestExport: () -> Unit,
) {
    var visible by mutableStateOf(false); private set
    private var recorded = false

    /** Marks the reminder due when [exportReminderDue]; it is recorded as shown only once Daily draws it. */
    fun check() {
        if (visible || !session.ready) return
        if (settings.exportReminder != ExportReminder.NEVER && settings.exportReminderEnabledMs == null) settings.exportReminderEnabledMs = clock()
        if (!exportReminderDue(settings.exportReminder, settings.lastExportMs, settings.exportReminderShownMs, settings.exportReminderEnabledMs, clock())) return
        recorded = false
        visible = true
    }

    /** The setting changed: Never hides it, anything else checks it again. */
    fun settingChanged(reminder: ExportReminder) { if (reminder == ExportReminder.NEVER) hide() else check() }

    /** Daily draws it now ([exportReminderOnScreen]); the first time, it counts as shown. */
    fun onScreenNow() {
        if (!visible || recorded) return
        recorded = true
        settings.exportReminderShownMs = clock()
    }

    /** True when Daily should draw it, given what else is on screen. */
    fun shownOnDaily(logOpen: Boolean, overlay: Overlay?): Boolean = exportReminderOnScreen(visible, logOpen, overlay)

    fun hide() { visible = false }

    /** The reminder's Export: the same save picker as Settings, Export diary. */
    fun export() {
        if (!session.ready || session.busy) return
        visible = false
        requestExport()
    }
}
