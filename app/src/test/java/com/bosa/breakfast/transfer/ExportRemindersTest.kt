package com.bosa.breakfast.transfer

import com.bosa.breakfast.app.DiaryChange
import com.bosa.breakfast.settings.AppSettings
import com.bosa.breakfast.settings.ExportReminder
import com.bosa.breakfast.settings.FakeSettingsStore
import com.bosa.breakfast.settings.SettingsState
import com.bosa.breakfast.today.OpenDiary
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportRemindersTest {
    private val day = 86_400_000L
    private var now = 1_000L
    private val diary = OpenDiary()
    private val settings = SettingsState(AppSettings(FakeSettingsStore())) { now }
    private var exports = 0
    private val reminders = ExportReminders(settings, diary.session, { now }) { exports++ }

    @Test fun neverIsNeverDue() {
        now += 365 * day
        reminders.check()
        assertFalse(reminders.visible)
    }

    @Test fun theFirstReminderComesOneIntervalAfterItWasTurnedOnAndCountsOnceDrawn() {
        settings.chooseExportReminder(ExportReminder.WEEKLY)
        now += 7 * day - 1
        reminders.check()
        assertFalse(reminders.visible)
        now += 1
        reminders.check()
        assertTrue(reminders.visible)
        // Not shown until Daily draws it, and then only once.
        assertEquals(null, settings.exportReminderShownMs)
        reminders.onScreenNow()
        assertEquals(now, settings.exportReminderShownMs)
        now += 10
        reminders.onScreenNow()
        assertEquals(now - 10, settings.exportReminderShownMs)
        // Hidden, it comes back one interval after it was shown.
        reminders.hide()
        reminders.check()
        assertFalse(reminders.visible)
        now += 7 * day
        reminders.check()
        assertTrue(reminders.visible)
    }

    @Test fun aRecentExportPutsItOff() {
        settings.chooseExportReminder(ExportReminder.WEEKLY)
        now += 6 * day
        settings.lastExportMs = now
        now += 6 * day
        reminders.check()
        assertFalse(reminders.visible)
    }

    @Test fun turningItToNeverHidesItAndAnythingElseChecksAgain() {
        settings.chooseExportReminder(ExportReminder.WEEKLY)
        now += 7 * day
        reminders.settingChanged(ExportReminder.WEEKLY)
        assertTrue(reminders.visible)
        reminders.settingChanged(ExportReminder.NEVER)
        assertFalse(reminders.visible)
    }

    @Test fun itIsDrawnOnlyOnDailyItself() {
        settings.chooseExportReminder(ExportReminder.MONTHLY)
        now += 30 * day
        reminders.check()
        assertTrue(reminders.shownOnDaily(covered = false))
        assertFalse(reminders.shownOnDaily(covered = true))
    }

    @Test fun itsExportOpensTheSavePickerAndHidesIt() {
        settings.chooseExportReminder(ExportReminder.WEEKLY)
        now += 7 * day
        reminders.check()
        reminders.export()
        assertEquals(1, exports)
        assertFalse(reminders.visible)
    }

    @Test fun itsExportWaitsForNoWriteToRun() {
        settings.chooseExportReminder(ExportReminder.WEEKLY)
        now += 7 * day
        reminders.check()
        val gate = CompletableDeferred<Unit>()
        diary.session.write(DiaryChange.FoodsChanged) { gate.await() }
        reminders.export()
        assertEquals(0, exports)
        assertTrue(reminders.visible)
        gate.complete(Unit)
    }
}
