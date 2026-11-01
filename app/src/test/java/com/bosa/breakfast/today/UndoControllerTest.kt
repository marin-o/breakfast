package com.bosa.breakfast.today

import com.bosa.breakfast.app.DiaryChange
import com.bosa.breakfast.app.Notices
import com.bosa.breakfast.domain.MealEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoControllerTest {
    private class FakeStore : UndoStore {
        val deleted = mutableListOf<String>()
        val restored = mutableListOf<MealEntry>()
        val batches = mutableListOf<List<MealEntry>>()
        var failure: Exception? = null
        override suspend fun deleteEntry(id: String) { failure?.let { throw it }; deleted += id }
        override suspend fun restoreEntry(entry: MealEntry) { failure?.let { throw it }; restored += entry }
        override suspend fun deleteEntries(entries: List<MealEntry>) { failure?.let { throw it }; batches += entries }
    }

    private val diary = OpenDiary()
    private val store = FakeStore()
    private val notices = Notices()
    private val time = FakeTime()
    private var window: Long? = 15_000L
    /** What TalkBack was told (`app/Announcements`). */
    private val said = mutableListOf<String>()
    private val undo = UndoController(diary.session, store, notices, { window }, time.clock, diary.scope, announce = { said += it }, sleep = time.sleep)
        .also { diary.session.onChange(it::onChange) }

    private val egg = entry("egg")
    private val batch = listOf(entry("a", food = "Oats"), entry("b", food = "Milk"))

    /** Each new offer is said once, with its action; re-timing it or its end says nothing. */
    @Test fun eachNewOfferIsReadOutOnceWithItsUndo() {
        diary.commit(DiaryChange.BatchLogged("Logged!", batch))
        undo.delete(egg)
        assertEquals(listOf("Logged! Undo", "Egg deleted. Undo"), said)
        window = 30_000L
        undo.windowChanged()
        diary.commit(DiaryChange.MatchCorrected)
        time.advance(30_000L)
        assertNull(undo.offer)
        diary.commit(DiaryChange.BatchLogged("Logged!", emptyList()))
        assertEquals("kept, re-timed, ended or a batch that saved nothing: nothing new", 2, said.size)
    }

    @Test fun aDeleteOffersItsUndoUntilTheWindowEnds() {
        undo.delete(egg)
        assertEquals(listOf("egg"), store.deleted)
        assertEquals(UndoOffer.Deleted(egg, time.now), undo.offer)
        assertEquals(egg, undo.deletedEntry)
        assertEquals(listOf(15_000L), time.sleeps)
        time.advance(14_999L)
        assertEquals(egg, undo.deletedEntry)
        time.advance(1L)
        assertNull(undo.offer)
        assertFalse(time.sleeping)
    }

    @Test fun untilNextActionNeverExpiresAndTheNextLogEndsIt() {
        window = null
        undo.delete(egg)
        assertFalse(time.sleeping)
        time.advance(86_400_000L)
        assertEquals(egg, undo.deletedEntry)
        diary.commit(DiaryChange.FoodsLogged)
        assertNull(undo.offer)
    }

    @Test fun aShorterWindowClosesAnOfferAlreadyPastIt() {
        window = 30_000L
        undo.delete(egg)
        time.advance(10_000L)
        window = 5_000L
        undo.windowChanged()
        assertNull(undo.offer)
    }

    @Test fun aLongerWindowReTimesTheOfferFromWhenItWasMade() {
        undo.delete(egg)
        time.advance(10_000L)
        window = 30_000L
        undo.windowChanged()
        time.advance(15_000L)
        assertEquals(egg, undo.deletedEntry)
        time.advance(5_000L)
        assertNull(undo.offer)
    }

    @Test fun switchingToUntilNextActionStopsTheExpiry() {
        undo.delete(egg)
        window = null
        undo.windowChanged()
        time.advance(60_000L)
        assertEquals(egg, undo.deletedEntry)
    }

    @Test fun undoPutsTheDeletedEntryBackAndEndsTheOffer() {
        undo.delete(egg)
        undo.undoDelete()
        assertEquals(listOf(egg), store.restored)
        assertNull(undo.offer)
        assertEquals(listOf(DiaryChange.EntryDeleted(egg), DiaryChange.UndoApplied), diary.published)
    }

    @Test fun anUndoTappedAfterTheWindowRestoresNothing() {
        undo.delete(egg)
        // The clock passed the window before the timer woke (the tap came first).
        time.now += 15_000L
        undo.undoDelete()
        assertTrue(store.restored.isEmpty())
        assertNull(undo.offer)
    }

    @Test fun aLoggedBatchOffersItsOwnUndoAndUndoRemovesTheBatchAsSaved() {
        diary.rows = batch
        diary.commit(DiaryChange.BatchLogged("2 foods added to Lunch", batch))
        assertEquals("2 foods added to Lunch", undo.loggedMessage)
        assertEquals(listOf("Oats", "Milk"), undo.lastBatchNames())
        undo.undoBatch()
        assertEquals(listOf(batch), store.batches)
        assertEquals(DiaryChange.UndoApplied, diary.published.last())
        assertNull(undo.offer)
        assertTrue(undo.lastBatchNames().isEmpty())
    }

    @Test fun aNotRightPickKeepsTheBatchUndoWhichStillPassesTheEntriesAsSaved() {
        // B4: the batch Undo survives a correction; the store then leaves the corrected entry (it changed since).
        diary.commit(DiaryChange.BatchLogged("2 foods added to Lunch", batch))
        val offer = undo.offer
        diary.commit(DiaryChange.MatchCorrected)
        diary.commit(DiaryChange.MatchConfirmed)
        diary.commit(DiaryChange.FoodsChanged)
        assertTrue(offer === undo.offer)
        undo.undoBatch()
        assertEquals(listOf(batch), store.batches)
    }

    @Test fun aBatchThatSavedNothingKeepsThePendingUndo() {
        undo.delete(egg)
        diary.commit(DiaryChange.BatchLogged("0 foods logged to Lunch", emptyList()))
        assertEquals(egg, undo.deletedEntry)
    }

    @Test fun theNextEditOrDeleteEndsOrReplacesTheOfferButALateEditDoesNot() {
        diary.commit(DiaryChange.BatchLogged("2 foods added to Lunch", batch))
        diary.commit(DiaryChange.EntryEditedAfterClose)
        assertEquals("2 foods added to Lunch", undo.loggedMessage)
        undo.delete(egg)
        assertEquals(egg, undo.deletedEntry)
        diary.commit(DiaryChange.EntryEdited)
        assertNull(undo.offer)
    }

    @Test fun replacingTheDiaryEndsUndoAndForgetsTheLastBatch() {
        diary.rows = batch
        diary.commit(DiaryChange.BatchLogged("2 foods added to Lunch", batch))
        diary.commit(DiaryChange.DiaryReplaced)
        assertNull(undo.offer)
        assertTrue(undo.lastBatchNames().isEmpty())
    }

    @Test fun aFailedUndoOrDeleteSaysSoAndChangesNothing() {
        undo.delete(egg)
        store.failure = IllegalStateException("disk")
        undo.undoDelete()
        assertEquals("Could not undo.", notices.message)
        assertEquals(egg, undo.deletedEntry)
        undo.delete(entry("other"))
        assertEquals("Could not edit this entry.", notices.message)
        assertEquals(egg, undo.deletedEntry)
    }
}
