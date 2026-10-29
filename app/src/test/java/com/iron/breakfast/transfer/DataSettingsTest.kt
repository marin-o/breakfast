package com.iron.breakfast.transfer

import com.iron.breakfast.data.SAFETY_COPY_NAME
import com.iron.breakfast.data.deleteSafetyCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import com.iron.breakfast.settings.enumOf
import com.iron.breakfast.settings.ExportReminder

/** B9 Data group: Export reminder, Safety copy, Delete all data. */
class DataSettingsTest {
    private val day = 86_400_000L
    private val now = 100L * day

    @Test fun neverIsTheDefaultAndNeverReminds() {
        assertEquals(ExportReminder.NEVER, enumOf(null, ExportReminder.NEVER))
        assertNull(exportReminderIntervalMs(ExportReminder.NEVER))
        assertFalse(exportReminderDue(ExportReminder.NEVER, null, null, null, now))
        assertFalse(exportReminderDue(ExportReminder.NEVER, now - 400 * day, null, now - 400 * day, now))
    }

    @Test fun weeklyAndMonthlyAreSevenAndThirtyDays() {
        assertEquals(7 * day, exportReminderIntervalMs(ExportReminder.WEEKLY))
        assertEquals(30 * day, exportReminderIntervalMs(ExportReminder.MONTHLY))
    }

    @Test fun theFirstReminderComesOneIntervalAfterItIsTurnedOn() {
        assertFalse(exportReminderDue(ExportReminder.WEEKLY, null, null, now, now))
        assertFalse(exportReminderDue(ExportReminder.WEEKLY, null, null, now - 7 * day + 1, now))
        assertTrue(exportReminderDue(ExportReminder.WEEKLY, null, null, now - 7 * day, now))
        // An export long before it was turned on does not make it due at once.
        assertFalse(exportReminderDue(ExportReminder.MONTHLY, now - 90 * day, null, now - day, now))
        // No baseline at all: not due.
        assertFalse(exportReminderDue(ExportReminder.WEEKLY, null, null, null, now))
    }

    @Test fun itIsDrawnOnlyOnDailyItself() {
        assertTrue(exportReminderOnScreen(due = true, covered = false))
        assertFalse(exportReminderOnScreen(due = false, covered = false))
        assertFalse(exportReminderOnScreen(due = true, covered = true))
    }

    @Test fun theReminderComesOneIntervalAfterTheLastExport() {
        assertFalse(exportReminderDue(ExportReminder.WEEKLY, now - 7 * day + 1, null, null, now))
        assertTrue(exportReminderDue(ExportReminder.WEEKLY, now - 7 * day, null, null, now))
        assertFalse(exportReminderDue(ExportReminder.MONTHLY, now - 29 * day, null, null, now))
        assertTrue(exportReminderDue(ExportReminder.MONTHLY, now - 30 * day, null, null, now))
    }

    @Test fun shownOnceThenAgainOnlyAfterTheNextInterval() {
        // Shown a day ago, no export since: not again until a week after it was shown.
        assertFalse(exportReminderDue(ExportReminder.WEEKLY, now - 20 * day, now - day, null, now))
        assertTrue(exportReminderDue(ExportReminder.WEEKLY, now - 20 * day, now - 7 * day, null, now))
        // An export after it was shown restarts the interval from the export.
        assertFalse(exportReminderDue(ExportReminder.WEEKLY, now - 2 * day, now - 9 * day, null, now))
    }

    @Test fun aClockSetBackIsNotDue() {
        assertFalse(exportReminderDue(ExportReminder.WEEKLY, now + day, null, null, now))
    }

    @Test fun importWritesTheSafetyCopyOnlyWithKeep() {
        assertEquals(SafetyCopyStep.WRITE, safetyCopyStep(keep = true, deletingAll = false))
        assertEquals(SafetyCopyStep.DELETE, safetyCopyStep(keep = false, deletingAll = false))
    }

    @Test fun deleteAllAlwaysWritesTheSafetyCopyFirst() {
        assertEquals(SafetyCopyStep.WRITE, safetyCopyStep(keep = true, deletingAll = true))
        assertEquals(SafetyCopyStep.WRITE, safetyCopyStep(keep = false, deletingAll = true))
    }

    @Test fun deleteAllRunsOnlyFromItsDialog() {
        assertTrue(deleteAllAllowed(dialogOpen = true, canWrite = true))
        assertFalse(deleteAllAllowed(dialogOpen = false, canWrite = true))
        assertFalse("storage closed or a write running", deleteAllAllowed(dialogOpen = true, canWrite = false))
    }

    @Test fun dontKeepRemovesTheCopyAndItsTempFile() {
        val dir = Files.createTempDirectory("safety").toFile()
        try {
            File(dir, SAFETY_COPY_NAME).writeText("{}")
            File(dir, "$SAFETY_COPY_NAME.tmp").writeText("{")
            File(dir, "other.json").writeText("{}")
            assertTrue(deleteSafetyCopy(dir))
            assertEquals(listOf("other.json"), dir.list()!!.toList())
            // Nothing to delete is fine too.
            assertTrue(deleteSafetyCopy(dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}
