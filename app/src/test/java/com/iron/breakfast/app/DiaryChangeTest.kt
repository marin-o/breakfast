package com.iron.breakfast.app

import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.today.UndoOffer
import com.iron.breakfast.today.undoAfterLog
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What each [DiaryChange] does once published: to the pending Undo ([undoAfterChange]), to the diary
 * ([DiaryChange.rowsChanged]) and to the Library list ([DiaryChange.refreshesLibrary]). These are the effects the
 * hand-written call sites had at 8673e52, before B11 (`WritePathCharacterisationTest` until 2026-10-29).
 *
 * Which write publishes which change is asserted by the owner's own test, over the production write:
 * - [DiaryChange.BatchLogged]: Log Add (`LogComposerTest.addSavesTheDraftToTheTargetMeal...`), the Library's mixed
 *   batch (`LibraryStateTest.aMixedBatchSavesTheBar...`).
 * - [DiaryChange.FoodsLogged]: Library log (`LibraryStateTest.addingAFoodLogsItToTheMeal`), product review Add
 *   (`ProductReviewTest.addLogsTheEditedProductToTheChosenMeal`), a new food saved with a meal
 *   (`FoodFormTest.aNewFoodWithAMealOpenIsSavedAndLoggedThere`).
 * - [DiaryChange.QueuedPartLogged]: the resolver's pick (`ResolveFlowTest.aPickLogsThePartWithItsTypedAmount...`).
 * - [DiaryChange.EntryEdited], [DiaryChange.EntryEditedAfterClose]: quick and full edits
 *   (`EntryEditTest.aQuickSaveWritesItsOwnTextAndEndsTheEdit`, `aQuickSaveThatCommitsAfterItsEditClosed...`,
 *   `aFullEditOwnsItsEditUntilItsSaveEnds`).
 * - [DiaryChange.EntryDeleted], [DiaryChange.UndoApplied]: delete and the two Undos
 *   (`UndoControllerTest.undoPutsTheDeletedEntryBackAndEndsTheOffer`, `aLoggedBatchOffersItsOwnUndo...`).
 * - [DiaryChange.MatchConfirmed], [DiaryChange.MatchCorrected]: "Not right?"
 *   (`MatchFeedbackTest.confirmLearnsTheFood...`, `aPickReplacesTheEntry...`).
 * - [DiaryChange.FoodsChanged]: a food saved alone or edited (`FoodFormTest.aNewFoodWithoutAMealIsSavedAlone`,
 *   `anEditUpdatesTheFoodAndNeverLogs`; `ResolveFrameTest.aNewFoodMadeForALogPart...`), stored from review
 *   (`ProductReviewTest.saveToLibraryStoresTheEditedFoodOnlyFromTheLibrary`, `whileALogPartIsResolved...`), deleted
 *   (`LibraryStateTest.deleteFoodAsksFirstThenDeletes...`).
 * - [DiaryChange.DiaryReplaced]: import and Delete all (`DiaryTransferTest.replaceDiaryWritesTheSafetyCopyFirst...`,
 *   `deleteAllAlwaysWritesTheSafetyCopy...`).
 */
class DiaryChangeTest {
    /** What a change does to the pending Undo offer. */
    private enum class Undo { END, KEEP, REPLACE_WITH_ITS_OWN }

    private data class Effect(val undo: Undo, val rowsChanged: Boolean, val refreshesLibrary: Boolean)

    private val entry = MealEntry("e", "f", "Egg", 1.0, MeasureUnit.PIECE, 50.0, NutritionValues(70.0, 6.0, 0.5, 5.0), "1 piece", false, 0L)
    private val pending = UndoOffer.Deleted(entry, 1_000L)
    private val now = 3_000L

    /** Exhaustive, so a new kind of change does not compile here until its effect is stated. */
    private fun effectOf(change: DiaryChange): Effect = when (change) {
        is DiaryChange.BatchLogged -> Effect(Undo.REPLACE_WITH_ITS_OWN, rowsChanged = true, refreshesLibrary = false)
        DiaryChange.FoodsLogged -> Effect(Undo.END, rowsChanged = true, refreshesLibrary = true)
        DiaryChange.QueuedPartLogged -> Effect(Undo.END, rowsChanged = true, refreshesLibrary = false)
        DiaryChange.EntryEdited -> Effect(Undo.END, rowsChanged = true, refreshesLibrary = false)
        // Added in B13: it reloads the diary; before, nothing was published and the diary showed the old row.
        DiaryChange.EntryEditedAfterClose -> Effect(Undo.KEEP, rowsChanged = true, refreshesLibrary = false)
        is DiaryChange.EntryDeleted -> Effect(Undo.REPLACE_WITH_ITS_OWN, rowsChanged = true, refreshesLibrary = false)
        DiaryChange.UndoApplied -> Effect(Undo.END, rowsChanged = true, refreshesLibrary = false)
        DiaryChange.MatchCorrected -> Effect(Undo.KEEP, rowsChanged = true, refreshesLibrary = false)
        DiaryChange.MatchConfirmed -> Effect(Undo.KEEP, rowsChanged = false, refreshesLibrary = false)
        DiaryChange.FoodsChanged -> Effect(Undo.KEEP, rowsChanged = true, refreshesLibrary = true)
        DiaryChange.DiaryReplaced -> Effect(Undo.END, rowsChanged = true, refreshesLibrary = true)
    }

    /** One change of each kind; [everyKindOfChangeHasASample] fails when a kind is missing. */
    private val changes = listOf(
        DiaryChange.BatchLogged("1 food added to Breakfast", listOf(entry)), DiaryChange.FoodsLogged, DiaryChange.QueuedPartLogged,
        DiaryChange.EntryEdited, DiaryChange.EntryEditedAfterClose, DiaryChange.EntryDeleted(entry), DiaryChange.UndoApplied,
        DiaryChange.MatchCorrected, DiaryChange.MatchConfirmed, DiaryChange.FoodsChanged, DiaryChange.DiaryReplaced,
    )

    @Test fun everyKindOfChangeHasASample() {
        assertEquals(DiaryChange::class.sealedSubclasses.toSet(), changes.map { it::class }.toSet())
    }

    @Test fun eachChangeDoesWhatItsCallSiteDidBefore() {
        for (change in changes) {
            val effect = effectOf(change)
            val name = change::class.simpleName
            val expectedUndo = when (effect.undo) {
                Undo.END -> null
                Undo.KEEP -> pending
                // Both offers are made at the moment the change is published.
                Undo.REPLACE_WITH_ITS_OWN -> when (change) {
                    is DiaryChange.BatchLogged -> UndoOffer.Logged(change.message, change.entries, now)
                    is DiaryChange.EntryDeleted -> UndoOffer.Deleted(change.entry, now)
                    else -> error("no offer of its own: $name")
                }
            }
            assertEquals(name, expectedUndo, undoAfterChange(pending, change, now))
            assertEquals(name, effect.rowsChanged, change.rowsChanged)
            assertEquals(name, effect.refreshesLibrary, change.refreshesLibrary)
        }
    }

    @Test fun aBatchThatSavedNothingKeepsThePendingUndo() {
        assertEquals(pending, undoAfterChange(pending, DiaryChange.BatchLogged("0 foods logged to Lunch", emptyList()), now))
        assertEquals(null, undoAfterChange(null, DiaryChange.BatchLogged("0 foods logged to Lunch", emptyList()), now))
        val logged = UndoOffer.Logged("1 food added to Breakfast", listOf(entry), 2_000L)
        assertEquals(logged, undoAfterLog(pending, logged))
        assertEquals(pending, undoAfterLog(pending, null))
    }
}
