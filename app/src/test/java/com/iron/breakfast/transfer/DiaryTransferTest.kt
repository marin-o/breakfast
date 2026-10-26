package com.iron.breakfast.transfer

import com.iron.breakfast.app.DiaryChange
import com.iron.breakfast.app.Notices
import com.iron.breakfast.data.DiaryImport
import com.iron.breakfast.data.decodeDiary
import com.iron.breakfast.settings.AppSettings
import com.iron.breakfast.settings.FakeSettingsStore
import com.iron.breakfast.settings.SettingsState
import com.iron.breakfast.settings.UndoWindow
import com.iron.breakfast.today.OpenDiary
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryTransferTest {
    private val events = mutableListOf<String>()
    private val diary = OpenDiary()
    private val files = FakeFiles(events)
    private val data = FakeData(events)
    private var now = 5_000L
    private val settings = SettingsState(AppSettings(FakeSettingsStore())) { now }
    private val notices = Notices()
    private val log = RecordingLog()
    private var exported = 0
    private val transfer = DiaryTransfer(
        diary.session, data, files, settings, notices, { now }, diary.scope, Dispatchers.Unconfined, log, onExported = { exported++ },
    )

    @Test fun anExportIsWrittenReadBackAndCountedAsTheLastExport() {
        transfer.export("diary.json")
        assertTrue(decodeDiary(files.documents.getValue("diary.json")) is DiaryImport.Valid)
        assertEquals("Diary exported.", notices.message)
        assertEquals(5_000L, settings.lastExportMs)
        assertEquals(1, exported)
        assertFalse(diary.session.busy)
        assertTrue(diary.published.isEmpty())
    }

    @Test fun anExportThatCannotBeWrittenOrDiffersOnReadBackIsDeleted() {
        files.readBackDiffers = true
        transfer.export("diary.json")
        assertEquals(listOf("write diary.json", "delete diary.json"), events)
        assertEquals("Could not export the diary.", notices.message)
        assertNull(settings.lastExportMs)
        assertEquals(0, exported)
        events.clear()
        files.readBackDiffers = false
        files.writeFailure = IllegalStateException("disk full")
        transfer.export("other.json")
        assertEquals(listOf("delete other.json"), events)
        assertTrue(log.lines.last().startsWith("E Diary export failed: write failed"))
    }

    @Test fun anImportIsCheckedThenWaitsForReplaceDiary() {
        files.documents["in.json"] = validDiaryText()
        transfer.import("in.json")
        assertNotNull(transfer.pendingImport)
        assertTrue(events.isEmpty())
        transfer.cancelImport()
        assertNull(transfer.pendingImport)
    }

    @Test fun aFileThatFailsAnyCheckIsRefusedAndChangesNothing() {
        files.documents["bad.json"] = "{ not a diary"
        transfer.import("bad.json")
        assertNull(transfer.pendingImport)
        assertEquals("That file can't be imported.", notices.message)
        files.tooBig = true
        files.documents["big.json"] = validDiaryText()
        transfer.import("big.json")
        assertEquals("W Diary import refused: file too big", log.lines.last())
        transfer.import("missing.json")
        assertNull(transfer.pendingImport)
        assertTrue(events.isEmpty())
    }

    @Test fun replaceDiaryWritesTheSafetyCopyFirstThenReplacesAndPublishesTheReplace() {
        files.documents["in.json"] = validDiaryText()
        transfer.import("in.json")
        transfer.confirmImport()
        assertEquals(listOf("safety copy", "replace"), events)
        assertNull(transfer.pendingImport)
        assertEquals(listOf<DiaryChange>(DiaryChange.DiaryReplaced), diary.published)
        assertNull(notices.message)
    }

    @Test fun noSafetyCopyNoReplace() {
        files.documents["in.json"] = validDiaryText()
        transfer.import("in.json")
        files.safetyCopyFailure = IllegalStateException("no space")
        transfer.confirmImport()
        assertTrue(events.isEmpty())
        assertTrue(diary.published.isEmpty())
        assertEquals("That file can't be imported.", notices.message)
    }

    @Test fun withSafetyCopyDontKeepTheOldCopyGoesAndNoneIsWritten() {
        settings.chooseKeepSafetyCopy(false)
        files.documents["in.json"] = validDiaryText()
        transfer.import("in.json")
        transfer.confirmImport()
        assertEquals(listOf("delete safety copy", "replace"), events)
    }

    @Test fun deleteAllAlwaysWritesTheSafetyCopyAndKeepsSettingsWithoutTheChip() {
        settings.chooseKeepSafetyCopy(false)
        settings.chooseUndoWindow(UndoWindow.entries.last())
        transfer.openDeleteAll()
        transfer.confirmDeleteAll()
        assertEquals(listOf("safety copy", "delete all alsoPreferences=false"), events)
        assertFalse(transfer.deleteAllOpen)
        assertEquals(listOf<DiaryChange>(DiaryChange.DiaryReplaced), diary.published)
        assertFalse(settings.keepSafetyCopy)
        assertEquals(UndoWindow.entries.last(), settings.undoWindow)
    }

    @Test fun deleteAllWithTheChipAlsoDeletesLearnedPicksAndResetsEverySetting() {
        settings.chooseUndoWindow(UndoWindow.entries.last())
        transfer.openDeleteAll()
        transfer.toggleDeleteAllAlsoSettings()
        transfer.confirmDeleteAll()
        assertEquals(listOf("safety copy", "delete all alsoPreferences=true"), events)
        assertEquals(AppSettings(FakeSettingsStore()).undoWindow, settings.undoWindow)
        // The chip is off again the next time the dialog opens.
        transfer.openDeleteAll()
        assertFalse(transfer.deleteAllAlsoSettings)
    }

    @Test fun aFailedDeleteAllSaysSoAndKeepsTheDiaryAndSettings() {
        settings.chooseUndoWindow(UndoWindow.entries.last())
        data.failure = IllegalStateException("locked")
        transfer.openDeleteAll()
        transfer.toggleDeleteAllAlsoSettings()
        transfer.confirmDeleteAll()
        assertEquals("Could not delete the diary.", notices.message)
        assertTrue(diary.published.isEmpty())
        assertEquals(UndoWindow.entries.last(), settings.undoWindow)
    }

    @Test fun deleteAllRunsOnlyFromItsDialog() {
        transfer.confirmDeleteAll()
        assertTrue(events.isEmpty())
    }

    @Test fun thePickerIsAskedForOnlyWhenNoWriteRunsAndIsConsumedOnce() {
        transfer.requestPicker(DiaryPicker.IMPORT)
        assertEquals(DiaryPicker.IMPORT, transfer.pickerRequest)
        transfer.pickerLaunched()
        assertNull(transfer.pickerRequest)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        diary.session.write(DiaryChange.FoodsChanged) { gate.await() }
        transfer.requestPicker(DiaryPicker.EXPORT)
        transfer.openDeleteAll()
        assertNull(transfer.pickerRequest)
        assertFalse(transfer.deleteAllOpen)
        gate.complete(Unit)
    }

    @Test fun safetyCopyDontKeepRemovesTheCopyKeptSoFar() {
        files.safetyCopy = "old"
        transfer.removeSafetyCopy()
        assertNull(files.safetyCopy)
    }
}
