package com.iron.breakfast.app

import com.iron.breakfast.DiaryChangeKind
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.today.UndoOffer
import com.iron.breakfast.undoAfterLog
import com.iron.breakfast.undoEndsAt
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What each diary write does after it succeeds, as `BreakfastViewModel` does it at 8673e52 (read from the code; the
 * view model cannot be built on the JVM). B11 moved this fan-out into `DiarySession`: each path now publishes a
 * [DiaryChange], and [eachPublishedChangeDoesWhatTheCallSiteDidBefore] checks it reproduces this table.
 */
class WritePathCharacterisationTest {
    /** What a write does to the pending Undo offer. */
    enum class Undo { END, KEEP, REPLACE_WITH_ITS_OWN, AFTER_LOG }

    data class Path(val name: String, val undo: Undo, val refreshesDiary: Boolean, val refreshesLibrary: Boolean)

    companion object {
        val PATHS = listOf(
            // submitToMeal: offerLoggedUndo after refresh(); no refreshLibrary().
            Path("Log Add", Undo.AFTER_LOG, refreshesDiary = true, refreshesLibrary = false),
            // submitLibraryMixedInput: offerLoggedUndo after refresh(); no refreshLibrary().
            Path("Library mixed batch", Undo.AFTER_LOG, refreshesDiary = true, refreshesLibrary = false),
            // logLibraryBatch: offerUndo(null); refresh(); refreshLibrary().
            Path("Library log (single or batch)", Undo.END, refreshesDiary = true, refreshesLibrary = true),
            // addSelected: offerUndo(null) ... refresh(); refreshLibrary().
            Path("Product review Add", Undo.END, refreshesDiary = true, refreshesLibrary = true),
            // saveCustom with a target meal: offerUndo(null); refresh(); refreshLibrary().
            Path("New food saved and logged", Undo.END, refreshesDiary = true, refreshesLibrary = true),
            // saveCustom without a meal, or editing a food: refresh(); refreshLibrary(); Undo untouched.
            Path("New food saved / food edited", Undo.KEEP, refreshesDiary = true, refreshesLibrary = true),
            // resolveUnknown: offerUndo(null); advanceUnknownQueue(); refresh(); no refreshLibrary().
            Path("Resolver pick", Undo.END, refreshesDiary = true, refreshesLibrary = false),
            // submitToMeal edit branch and saveFullEdit, on Success with the same edit session.
            Path("Entry quick or full edit", Undo.END, refreshesDiary = true, refreshesLibrary = false),
            // delete: offerUndo(UndoOffer.Deleted(entry, clock())); refresh().
            Path("Delete entry", Undo.REPLACE_WITH_ITS_OWN, refreshesDiary = true, refreshesLibrary = false),
            // undo / undoLoggedBatch: offerUndo(null); refresh().
            Path("Undo (restore or batch)", Undo.END, refreshesDiary = true, refreshesLibrary = false),
            // chooseAlternative Success: offerUndo(null) only if undoEndsAt(NOT_RIGHT_PICK); refresh().
            Path("Not right? pick", Undo.KEEP, refreshesDiary = true, refreshesLibrary = false),
            // confirm: engine.reinforce only; no refresh().
            Path("Confirm match", Undo.KEEP, refreshesDiary = false, refreshesLibrary = false),
            // archive, restore, saveSelectedToLibrary, resolveWithProduct: refresh(); refreshLibrary().
            Path("Food archived, restored, saved or cached", Undo.KEEP, refreshesDiary = true, refreshesLibrary = true),
            // confirmImport / confirmDeleteAll: offerUndo(null); refresh(); refreshLibrary().
            Path("Replace diary / Delete all", Undo.END, refreshesDiary = true, refreshesLibrary = true),
        )
    }

    private val entry = MealEntry("e", "f", "Egg", 1.0, MeasureUnit.PIECE, 50.0, NutritionValues(70.0, 6.0, 0.5, 5.0), "1 piece", false, 0L)
    private val pending = UndoOffer.Deleted(entry, 1_000L)

    @Test fun theUndoRuleAgreesWithTheHandWrittenCallSites() {
        // The call sites that end Undo by hand are the log, edit and delete kinds of the rule; the others keep it.
        assertEquals(true, undoEndsAt(DiaryChangeKind.LOG))
        assertEquals(true, undoEndsAt(DiaryChangeKind.EDIT))
        assertEquals(true, undoEndsAt(DiaryChangeKind.DELETE))
        assertEquals(false, undoEndsAt(DiaryChangeKind.NOT_RIGHT_PICK))
        assertEquals(false, undoEndsAt(DiaryChangeKind.CONFIRM_MATCH))
        assertEquals(Undo.KEEP, PATHS.single { it.name == "Not right? pick" }.undo)
        assertEquals(Undo.KEEP, PATHS.single { it.name == "Confirm match" }.undo)
    }

    @Test fun aBatchLogKeepsThePendingUndoOnlyWhenItSavedNothing() {
        val logged = UndoOffer.Logged("1 food added to Breakfast", listOf(entry), 2_000L)
        assertEquals(logged, undoAfterLog(pending, logged))
        assertEquals(pending, undoAfterLog(pending, null))
    }

    /** The change each path publishes since B11, as `BreakfastViewModel` builds it at that call site. */
    private fun published(path: String, logged: UndoOffer.Logged?, deleted: UndoOffer.Deleted): DiaryChange = when (path) {
        "Log Add", "Library mixed batch" -> DiaryChange.BatchLogged(logged)
        "Library log (single or batch)", "Product review Add", "New food saved and logged" -> DiaryChange.FoodsLogged
        "New food saved / food edited", "Food archived, restored, saved or cached" -> DiaryChange.FoodsChanged
        "Resolver pick" -> DiaryChange.QueuedPartLogged
        "Entry quick or full edit" -> DiaryChange.EntryEdited
        "Delete entry" -> DiaryChange.EntryDeleted(deleted)
        "Undo (restore or batch)" -> DiaryChange.UndoApplied
        "Not right? pick" -> DiaryChange.MatchCorrected
        "Confirm match" -> DiaryChange.MatchConfirmed
        "Replace diary / Delete all" -> DiaryChange.DiaryReplaced
        else -> error("no change for $path")
    }

    @Test fun eachPublishedChangeDoesWhatTheCallSiteDidBefore() {
        val logged = UndoOffer.Logged("1 food added to Breakfast", listOf(entry), 2_000L)
        val deleted = UndoOffer.Deleted(entry, 3_000L)
        for (path in PATHS) {
            val change = published(path.name, logged, deleted)
            val expectedUndo = when (path.undo) {
                Undo.END -> null
                Undo.KEEP -> pending
                Undo.REPLACE_WITH_ITS_OWN -> deleted
                Undo.AFTER_LOG -> logged
            }
            assertEquals(path.name, expectedUndo, undoAfterChange(pending, change))
            assertEquals(path.name, path.refreshesDiary, change.rowsChanged)
            assertEquals(path.name, path.refreshesLibrary, change.refreshesLibrary)
        }
        // A batch that saved nothing keeps the pending Undo, as offerLoggedUndo did.
        assertEquals(pending, undoAfterChange(pending, DiaryChange.BatchLogged(null)))
        assertEquals(null, undoAfterChange(null, DiaryChange.BatchLogged(null)))
    }

    @Test fun everyWritePathIsListedOnce() {
        assertEquals(PATHS.size, PATHS.map { it.name }.toSet().size)
    }
}
