package com.bosa.breakfast.app

import com.bosa.breakfast.data.OffContributionDraft
import com.bosa.breakfast.domain.TestFoods
import com.bosa.breakfast.domain.FoodRecord
import com.bosa.breakfast.domain.MealSlot
import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.domain.NutritionValues
import com.bosa.breakfast.resolve.food
import com.bosa.breakfast.today.entry
import com.bosa.breakfast.transfer.validDiaryText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B14: Import diary and Delete all data reset the session the same way, through one [SessionReset] on the
 * [DiaryChange.DiaryReplaced] they both publish. Before, import kept the resolve queue, the entry detail and a
 * contribution candidate, which referred to the old diary.
 *
 * Over the app as the view model wires it ([AppKit]), so each step of the production [sessionResetSteps] is checked:
 * the Log's pins and its error, the contribution candidate, the resolve queue and resolver, the entry detail, an Edit
 * food of a food the new diary does not have.
 */
class SessionResetTest {
    private val app = AppKit()
    private val egg = entry("e1").copy(mealSlot = MealSlot.LUNCH)

    /** Everything the session can hold about the old diary, each checked as held. */
    private fun holdTheOldDiary() {
        // Add leaves a part: the Log's error, and the queue for the resolver.
        app.unmatched = listOf("ayran")
        app.log.open(MealSlot.LUNCH)
        app.log.onDraft("2 eggs, ayran")
        app.log.submit()
        assertEquals("Some foods need a match.", app.log.error)
        // A suggestion picked in the draft that is left: a pin. The edit ends the Log's queue.
        app.log.onDraft("ayran, 2 eggs")
        app.log.chooseSuggestion(TestFoods.fixtures.first { it.id == "egg" })
        assertTrue(app.log.pinsSnapshot().isNotEmpty())
        // The resolver's queue, as the Library's mixed batch leaves it.
        app.resolve.afterSave(listOf("ayran"), MealSlot.LUNCH, app.library, app.library.text)
        assertTrue(app.resolve.queueActive)
        assertTrue(app.resolve.resolverOpen)
        // A contribution candidate.
        app.contribution.offerAfterLocalSave(OffContributionDraft("4006381333931", "Kefir", null, 100.0, MeasureUnit.GRAM, NutritionValues(60.0, 3.0, 4.0, 3.0)))
        assertNotNull(app.contribution.pending)
        // The entry detail.
        app.diaryHas(egg)
        app.entryEdit.openDetail(egg)
        assertEquals(egg, app.entryEdit.selectedEntry)
    }

    private fun assertTheSessionWasReset() {
        assertEquals("pins", emptyMap<String, FoodRecord>(), app.log.pinsSnapshot())
        assertNull("the Log's error", app.log.error)
        assertNull("the contribution candidate", app.contribution.pending)
        assertFalse("the resolve queue", app.resolve.queueActive)
        assertEquals(emptyList<String>(), app.resolve.parts)
        assertFalse("the resolver", app.resolve.resolverOpen)
        assertNull("the entry detail", app.entryEdit.selectedEntry)
    }

    private fun import() {
        app.documents.documents["in.json"] = validDiaryText()
        app.transfer.import("in.json")
        app.transfer.confirmImport()
    }

    private fun deleteAll() {
        app.transfer.openDeleteAll()
        app.transfer.confirmDeleteAll()
    }

    @Test fun replaceDiaryResetsTheWholeSession() {
        holdTheOldDiary()
        import()
        assertTheSessionWasReset()
        assertEquals("the draft stays as it was", "ayran, 2 piece Egg", app.log.draft)
    }

    @Test fun deleteAllResetsTheSessionExactlyTheSameWay() {
        holdTheOldDiary()
        deleteAll()
        assertTheSessionWasReset()
        assertEquals("ayran, 2 piece Egg", app.log.draft)
    }

    /** An Edit food whose food the new diary does not have closes; one whose food is there stays. */
    @Test fun aReplacedDiaryClosesAnEditOfAFoodItDoesNotHave() {
        app.foodForm.openEdit(food("jam", "Jam"))
        import()
        assertFalse(app.foodForm.open)
        val kept = AppKit(foods = listOf(food("jam", "Jam")))
        kept.foodForm.openEdit(food("jam", "Jam"))
        kept.documents.documents["in.json"] = validDiaryText()
        kept.transfer.import("in.json")
        kept.transfer.confirmImport()
        assertTrue(kept.foodForm.open)
    }

    @Test fun aReplaceOrDeleteThatFailedResetsNothing() {
        holdTheOldDiary()
        app.data.failure = IllegalStateException("locked")
        import()
        app.notices.dismiss()
        deleteAll()
        assertTrue(app.log.pinsSnapshot().isNotEmpty())
        assertEquals("Some foods need a match.", app.log.error)
        assertNotNull(app.contribution.pending)
        assertTrue(app.resolve.queueActive)
        assertEquals(egg, app.entryEdit.selectedEntry)
    }

    @Test fun otherWritesDoNotReset() {
        holdTheOldDiary()
        checkNotNull(app.owners.session.write(DiaryChange.FoodsLogged) {})
        checkNotNull(app.owners.session.write(DiaryChange.EntryEdited) {})
        assertTrue(app.log.pinsSnapshot().isNotEmpty())
        assertNotNull(app.contribution.pending)
        assertTrue(app.resolve.queueActive)
        assertEquals(egg, app.entryEdit.selectedEntry)
    }
}
