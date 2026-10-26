package com.iron.breakfast.transfer

import com.iron.breakfast.settings.ExportReminder
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
