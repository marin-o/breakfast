package mk.bosa.breakfast.app

import mk.bosa.breakfast.domain.MealSlot
import mk.bosa.breakfast.scan.ProductReviewOrigin
import mk.bosa.breakfast.today.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A new food saved while logging, over the app as the view model builds it: an amount typed is what logs, with none typed
 * the review asks for one and keeps the meal, Back leaves the food saved and nothing logged, and the queue waits for it.
 */
class NewFoodWhileLoggingTest {
    private val app = AppKit()

    private fun AppKit.saveNew(name: String, reviewAfterSave: ProductReviewOrigin? = null, fromResolver: Boolean = false, typing: () -> Unit = {}) {
        foodForm.open(name, fromResolver = fromResolver, reviewAfterSave = reviewAfterSave)
        foodForm.draft.calories = "100"
        typing()
        foodForm.save()
    }

    private fun addLeaves(vararg parts: String, meal: MealSlot? = MealSlot.LUNCH) {
        app.unmatched = parts.toList()
        app.log.open(meal)
        app.log.onDraft("2 eggs, ${parts.joinToString(", ")}")
        app.log.submit()
    }

    private fun logged() = app.calls.filter { it.contains("log") && !it.startsWith("log save") }

    // ---- With a typed amount ----

    @Test fun aServingAddedInTheFormLogsAsTyped() {
        addLeaves("1 bar Candybar")
        app.saveNew("Candybar", fromResolver = true) {
            app.foodForm.draft.openServing(app.foodForm.draft.basis())
            app.foodForm.draft.servingLabel = "bar"
            app.foodForm.draft.servingAmount = "20"
        }
        assertEquals(listOf("save and log Candybar 1.0 bar LUNCH"), logged())
        assertEquals("Logged!", app.confirmations.message)
        assertNull("nothing left to review", app.review.product)
        assertEquals("the part is done", emptyList<String>(), app.resolve.parts)
    }

    @Test fun aTypedAmountAdvancesTheQueueToTheNextPart() {
        addLeaves("ayran 200 g", "kefir")
        app.saveNew("Ayran", fromResolver = true)
        assertEquals(listOf("save and log Ayran 200.0 g LUNCH"), logged())
        assertEquals(listOf("kefir"), app.resolve.parts)
        assertTrue(app.resolve.queueActive)
    }

    @Test fun aServingTypedForAFoodSavedWithoutItReachesTheReviewOnThatAmount() {
        addLeaves("1 can Ayran")
        app.saveNew("Ayran", fromResolver = true)
        assertEquals("nothing logged", emptyList<String>(), logged())
        assertEquals(ProductReviewOrigin.QUEUE, app.review.origin)
        assertEquals("1", app.review.amount)
    }

    @Test fun aPartSentToTheLibraryKeepsItsAmountInTheDraft() {
        app.openMeal(MealSlot.DINNER)
        app.log.open(MealSlot.DINNER)
        app.log.onDraft("1 bar Candybar")
        app.log.resolveNotInFoods(mk.bosa.breakfast.domain.DraftRow.Unresolved("1 bar Candybar", 0, 14))
        assertTrue(app.resolve.resolvingPart)
        app.saveNew("Candybar") {
            app.foodForm.draft.openServing(app.foodForm.draft.basis())
            app.foodForm.draft.servingLabel = "bar"
            app.foodForm.draft.servingAmount = "20"
        }
        assertEquals("not logged until the Log's Add", listOf("save food Candybar"), app.calls)
        assertTrue(app.log.open)
        assertTrue(app.log.draft, app.log.draft.startsWith("Candybar"))
    }

    // ---- With no amount typed ----

    @Test fun aMealsLibraryGoesToTheReviewForThatMeal() {
        app.openFocusedLibrary(MealSlot.DINNER)
        app.saveNew("Ajvar")
        assertEquals("stored alone", listOf("save food Ajvar"), app.calls)
        assertEquals("Ajvar", app.review.product?.name)
        assertEquals(ProductReviewOrigin.MEAL, app.review.origin)
        assertEquals(MealSlot.DINNER, app.review.initialMeal())
        assertNull("no \"Logged!\" yet", app.confirmations.message)
        app.review.onAmount("150")
        app.review.add(app.review.initialMeal())
        assertEquals("review log ajvar DINNER", app.calls.last())
        assertEquals("Logged!", app.confirmations.message)
        assertNull(app.review.product)
    }

    @Test fun theMealScreenGoesToTheReviewForThatMeal() {
        app.openMeal(MealSlot.LUNCH)
        app.saveNew("Ajvar")
        assertEquals(MealSlot.LUNCH, app.review.initialMeal())
        assertEquals(listOf("save food Ajvar"), app.calls)
    }

    @Test fun aLoggingScanAndATypedFormTakeTheSameStep() {
        app.openMeal(MealSlot.LUNCH)
        app.scan.begin()
        app.settings.lookUpBarcodesSetting.set(false)
        app.scan.onBarcode("4006381333931")
        assertTrue(app.foodForm.open)
        app.foodForm.draft.name = "Scanned"
        app.foodForm.draft.calories = "100"
        app.foodForm.save()
        assertEquals(ProductReviewOrigin.MEAL, app.review.origin)
        assertEquals(MealSlot.LUNCH, app.review.initialMeal())
    }

    @Test fun backFromTheReviewKeepsTheFoodAndLogsNothing() {
        app.openFocusedLibrary(MealSlot.DINNER)
        app.saveNew("Ajvar")
        app.review.dismiss()
        assertNull(app.review.product)
        assertEquals("saved, nothing logged", listOf("save food Ajvar"), app.calls)
        assertNull(app.confirmations.message)
        assertTrue("back on the meal's Library", app.library.open)
    }

    @Test fun aQueuedPartWithNoAmountWaitsForTheReviewsAdd() {
        addLeaves("ayran", "kefir")
        app.saveNew("Ayran", fromResolver = true)
        assertEquals("stored alone", emptyList<String>(), logged())
        assertEquals(ProductReviewOrigin.QUEUE, app.review.origin)
        assertEquals("the part is still queued", listOf("ayran", "kefir"), app.resolve.parts)
        app.review.add(MealSlot.DINNER)
        assertEquals("the queue's meal wins", "review log ayran LUNCH", app.calls.last())
        assertEquals("Logged!", app.confirmations.message)
        assertEquals(listOf("kefir"), app.resolve.parts)
    }

    @Test fun backFromAQueuedPartsReviewLeavesThePartQueuedOnTheLog() {
        addLeaves("ayran", "kefir")
        app.saveNew("Ayran", fromResolver = true)
        app.review.dismiss()
        assertEquals(emptyList<String>(), logged())
        assertEquals(listOf("ayran", "kefir"), app.resolve.parts)
        assertTrue(app.resolve.queueActive)
        assertTrue(app.log.open)
    }

    @Test fun theLibrarysBatchComesBackToItsResolverAfterBack() {
        app.libraryFoods = listOf(mk.bosa.breakfast.resolve.food("egg"))
        app.unmatched = listOf("ayran", "kefir")
        app.openFocusedLibrary(MealSlot.LUNCH)
        app.library.onInput("2 egg, ayran, kefir")
        app.library.submitInput()
        assertTrue(app.resolve.resolverOpen)
        app.saveNew("Ayran", fromResolver = true)
        assertFalse("the review lies in its place", app.resolve.resolverOpen)
        assertTrue("a queue under a review stays", app.resolve.queueActive)
        assertEquals(MealSlot.LUNCH, app.review.queueMeal())
        app.review.dismiss()
        assertTrue(app.resolve.resolverOpen)
        assertEquals(listOf("ayran", "kefir"), app.resolve.parts)
    }

    @Test fun aQueueWithNoMealYetTakesTheFoodIntoTheDraftOnItsTypedAmount() {
        app.plannerFoods = emptyList()
        app.log.open(null)
        app.log.onDraft("200 g Ajvar")
        app.log.submit()
        assertTrue(app.resolve.queueActive)
        assertNull(app.resolve.queueMeal)
        app.saveNew("Ajvar", fromResolver = true)
        assertEquals("nothing logged", listOf("save food Ajvar"), app.calls)
        assertEquals(ProductReviewOrigin.LOG_PART, app.review.origin)
        assertEquals("200", app.review.amount)
    }

    // ---- Undo and "Not right?" ----

    @Test fun undoAndNotRightWaitUntilTheReviewLogs() {
        val logged = entry("logged").copy(mealSlot = MealSlot.LUNCH, dayKey = "2026-10-28")
        app.diaryHas(logged)
        app.openMeal(MealSlot.LUNCH)
        app.undo.delete(logged)
        assertNotNull(app.undo.offer)
        app.saveNew("Ajvar")
        assertNotNull("storing alone keeps the pending Undo", app.undo.offer)
        app.review.add(MealSlot.LUNCH)
        assertNull("a log ends it", app.undo.offer)
        assertFalse("a food logged by hand has no \"Not right?\"", app.feedback.isOpen(logged))
    }

    @Test fun aTypedLogEndsTheUndoAndHasNoNotRight() {
        val logged = entry("logged").copy(mealSlot = MealSlot.LUNCH, dayKey = "2026-10-28")
        app.diaryHas(logged)
        addLeaves("ayran 200 g")
        app.saveNew("Ayran", fromResolver = true)
        assertEquals("Logged!", app.confirmations.message)
        assertFalse(app.feedback.isOpen(logged))
    }

    // ---- With no meal ----

    @Test fun theLibraryTabSavesAlone() {
        app.openLibrary()
        app.saveNew("Ajvar")
        assertEquals(listOf("save food Ajvar"), app.calls)
        assertNull(app.review.product)
        assertEquals("Saved to your food library.", app.notices.message)
    }
}
