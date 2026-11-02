package mk.bosa.breakfast.app

import androidx.compose.runtime.snapshots.Snapshot
import mk.bosa.breakfast.domain.MealEntry
import mk.bosa.breakfast.domain.MealSlot
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.ReplacementResult
import mk.bosa.breakfast.resolve.food
import mk.bosa.breakfast.scan.ProductReviewOrigin
import mk.bosa.breakfast.today.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every way on from "Not right?"'s Search, from Daily and from the Meal screen, over the app as the view model builds it:
 * what is found replaces the entry's food, nothing is logged beside it, and a food added stands alone until then.
 */
class NotRightKeepsOneEntryTest {
    private val wrong: MealEntry = entry("wrong", approximate = true).copy(mealSlot = MealSlot.LUNCH, dayKey = "2026-10-28")
    private val found = food("found", "Found")
    private val barcode = "4006381333931"

    /** The app with the strip open on [wrong], on the Meal screen or on Daily. */
    private fun appOn(meal: MealSlot?, savedState: androidx.lifecycle.SavedStateHandle = androidx.lifecycle.SavedStateHandle()): AppKit = AppKit(savedState).apply {
        replacement = ReplacementResult.Success(wrong)
        offFoods = listOf(found)
        diaryHas(wrong)
        meal?.let(::openMeal)
        feedback.onChange(DiaryChange.BatchLogged("1 food added", listOf(wrong)))
    }

    /** Both screens the strip shows on. */
    private fun onEachScreen(test: (AppKit) -> Unit) { test(appOn(MealSlot.LUNCH)); test(appOn(null)) }

    private fun AppKit.searchOnTheStrip() = feedback.search(wrong)

    private fun AppKit.assertOneEntryReplaced(food: String) {
        assertEquals(calls.toString(), listOf(food), calls.filter { it.startsWith("replace") }.map { it.split(" ")[3] })
        assertTrue(calls.toString(), calls.none { it.contains("log") })
    }

    @Test fun anOpenFoodFactsResultReplacesTheEntry() = onEachScreen { app ->
        app.searchOnTheStrip()
        app.search.choose(app.search.results.single())
        assertEquals(ProductReviewOrigin.CORRECTION, app.review.origin)
        app.review.addToLog()
        app.assertOneEntryReplaced("found")
        assertNull(app.review.product)
        assertFalse("the strip closes", app.feedback.isOpen(wrong))
    }

    @Test fun addInTheMealFormOfTheSheetStillOnlyReplaces() = onEachScreen { app ->
        app.searchOnTheStrip()
        app.search.choose(found)
        app.review.add(MealSlot.DINNER)
        app.assertOneEntryReplaced("found")
        assertTrue(app.calls.toString(), "replace wrong with found 100.0 g LUNCH 2026-10-28" in app.calls)
    }

    @Test fun theReviewStartsOnTheEntrysAmountAndAddKeepsItsMealAndDay() {
        val app = appOn(MealSlot.LUNCH)
        app.diaryHas(wrong.copy(enteredAmount = 200.0, enteredUnit = MeasureUnit.GRAM))
        app.searchOnTheStrip()
        app.search.choose(found)
        assertEquals("200", app.review.amount)
        app.review.onAmount("150")
        app.review.addToLog()
        assertTrue(app.calls.toString(), "replace wrong with found 150.0 g LUNCH 2026-10-28" in app.calls)
    }

    @Test fun aNewFoodFromASearchWithNoResultsReplacesTheEntry() = onEachScreen { app ->
        app.offFoods = emptyList()
        app.searchOnTheStrip()
        // "Create personal food" on the search's status, as the screen opens it.
        app.foodForm.open("Mine", reviewAfterSave = app.search.reviewOrigin)
        app.foodForm.draft.calories = "100"
        app.foodForm.save()
        assertEquals("stored alone, not logged", listOf("save food Mine"), app.calls)
        app.review.addToLog()
        app.assertOneEntryReplaced("mine")
    }

    @Test fun findBetterMatchKeepsItACorrection() = onEachScreen { app ->
        app.searchOnTheStrip()
        app.search.choose(found)
        app.review.findBetterMatch()
        assertEquals(ProductReviewOrigin.CORRECTION, app.search.reviewOrigin)
        app.search.onQuery("found")
        app.search.submit()
        app.search.choose(found)
        app.review.addToLog()
        app.assertOneEntryReplaced("found")
    }

    @Test fun aRescannedKnownBarcodeReplacesTheEntry() = onEachScreen { app ->
        app.barcodes[barcode] = found
        app.searchOnTheStrip()
        app.search.choose(found)
        app.review.rescan()
        app.scan.onBarcode(barcode)
        assertEquals(ProductReviewOrigin.CORRECTION, app.review.origin)
        app.review.addToLog()
        app.assertOneEntryReplaced("found")
    }

    @Test fun aRescannedUnknownBarcodeGoesThroughNewFoodAndReplacesTheEntry() = onEachScreen { app ->
        app.searchOnTheStrip()
        app.search.choose(found)
        app.review.rescan()
        app.scan.onBarcode(barcode)
        assertTrue(app.foodForm.open)
        app.foodForm.draft.name = "Scanned"
        app.foodForm.draft.calories = "100"
        app.foodForm.save()
        app.review.addToLog()
        app.assertOneEntryReplaced("scanned")
    }

    @Test fun aPickAfterTheWindowStillReplaces() {
        val app = appOn(MealSlot.LUNCH)
        app.searchOnTheStrip()
        app.nowMs += 60_000L
        app.search.choose(found)
        app.review.addToLog()
        app.assertOneEntryReplaced("found")
    }

    @Test fun aRefusedReplacementSaysWhyAndLogsNothing() {
        val app = appOn(MealSlot.LUNCH)
        app.replacement = ReplacementResult.Failure("That saved entry is no longer available.")
        app.searchOnTheStrip()
        app.search.choose(found)
        app.review.addToLog()
        assertEquals("That saved entry is no longer available.", app.notices.message)
        assertTrue(app.calls.none { it.contains("log") })
    }

    @Test fun aFormLeftOpenComesBackAfterProcessDeathStillACorrection() {
        val before = appOn(MealSlot.LUNCH)
        before.offFoods = emptyList()
        before.searchOnTheStrip()
        before.foodForm.open("Mine", reviewAfterSave = before.search.reviewOrigin)
        Snapshot.sendApplyNotifications()
        val after = AppKit(before.savedState).apply { replacement = ReplacementResult.Success(wrong); diaryHas(wrong) }
        assertTrue(after.foodForm.open)
        after.foodForm.draft.calories = "100"
        after.foodForm.save()
        after.review.addToLog()
        after.assertOneEntryReplaced("mine")
    }

    private fun AppKit.assertGoneSaidAndNothingWritten() {
        assertEquals("That saved entry is no longer available.", notices.message)
        assertNull("the sheet closes", review.product)
        assertEquals(ProductReviewOrigin.LIBRARY, review.origin)
        assertTrue(calls.toString(), calls.none { it.startsWith("replace") || it.contains("log") || it.startsWith("review store") })
    }

    @Test fun aReviewOpenWhenTheDiaryIsReplacedSaysTheEntryIsGoneAndCloses() {
        val added = appOn(MealSlot.LUNCH)
        added.searchOnTheStrip()
        added.search.choose(found)
        added.feedback.onChange(DiaryChange.DiaryReplaced)
        added.review.add(MealSlot.LUNCH)
        added.assertGoneSaidAndNothingWritten()

        val toLog = appOn(null)
        toLog.searchOnTheStrip()
        toLog.search.choose(found)
        toLog.feedback.onChange(DiaryChange.DiaryReplaced)
        toLog.review.addToLog()
        toLog.assertGoneSaidAndNothingWritten()
    }

    @Test fun aFormOpenWhenTheDiaryIsReplacedLeadsToAReviewThatSaysTheEntryIsGone() {
        val app = appOn(MealSlot.LUNCH)
        app.offFoods = emptyList()
        app.searchOnTheStrip()
        app.foodForm.open("Mine", reviewAfterSave = app.search.reviewOrigin)
        app.feedback.onChange(DiaryChange.DiaryReplaced)
        app.foodForm.draft.calories = "100"
        app.foodForm.save()
        assertEquals(ProductReviewOrigin.CORRECTION, app.review.origin)
        app.review.addToLog()
        app.assertGoneSaidAndNothingWritten()
    }

    @Test fun anOrdinaryScanAfterACorrectingScanLogsToItsMealAndReplacesNothing() {
        val app = appOn(MealSlot.LUNCH)
        app.barcodes[barcode] = found
        app.searchOnTheStrip()
        app.search.choose(found)
        app.review.rescan()
        assertTrue(app.scan.correcting)
        app.scan.onBarcode(barcode)
        assertEquals(ProductReviewOrigin.CORRECTION, app.review.origin)
        app.review.addToLog()
        app.assertOneEntryReplaced("found")
        app.calls.clear()

        app.scan.begin()
        assertFalse(app.scan.correcting)
        app.scan.onBarcode(barcode)
        assertEquals(ProductReviewOrigin.MEAL, app.review.origin)
        app.review.add(MealSlot.LUNCH)
        assertEquals(listOf("review log found LUNCH"), app.calls.filter { it.contains("log") })
        assertTrue(app.calls.none { it.startsWith("replace") })
    }

    // ---- The USDA foods on the strip's Search ----

    private val usdaEgg = food("fndds_1", "Egg, whole, raw")

    /** What the screen draws: it asks for the query, and the rows that came are left out as the food being corrected. Only the pause ends. */
    private fun AppKit.usdaRowsDrawn(): List<mk.bosa.breakfast.domain.FoodRecord> {
        pausesEnd = true
        owners.usdaCorrection.typed(search.query)
        pausesEnd = false
        return owners.usdaCorrection.rowsFor(search.query).filterNot(search::leavesOut)
    }

    /** The strip's Search with the catalogue answering. */
    private fun AppKit.searchWithRows() {
        usdaFoods = listOf(usdaEgg)
        searchOnTheStrip()
        usdaRowsDrawn()
    }

    @Test fun theStripsSearchShowsTheUsdaFoodsAndAsksOpenFoodFactsOnce() = onEachScreen { app ->
        app.searchWithRows()
        assertEquals(listOf(usdaEgg), app.usdaRowsDrawn())
        assertEquals(listOf(found), app.search.results)
        assertEquals(listOf(wrong.sourcePhrase), app.offSearched)
    }

    private fun AppKit.searchedFor(typed: String, foodName: String = "Egg"): String {
        val logged = wrong.copy(sourcePhrase = typed, foodName = foodName)
        diaryHas(logged)
        feedback.search(logged)
        return offSearched.single()
    }

    @Test fun theSearchAsksWhatWasTypedLeavingItsAmountAsTheLogDoes() {
        assertEquals("eggs", appOn(MealSlot.LUNCH).searchedFor("2 eggs"))
    }

    @Test fun aCyrillicPhraseIsSentCyrillicNotAsTheFoodsLatinName() {
        val app = appOn(MealSlot.LUNCH)
        assertEquals("јајца", app.searchedFor("2 јајца", foodName = "Egg"))
        assertEquals("јајца", app.search.query)
    }

    @Test fun aBlankPhraseFallsBackToTheFoodName() {
        assertEquals("Egg", appOn(MealSlot.LUNCH).searchedFor(" "))
    }

    @Test fun usdaRowsStayWithNoConnection() {
        val app = appOn(MealSlot.LUNCH)
        app.offOffline = true
        app.searchWithRows()
        assertEquals(listOf(usdaEgg), app.usdaRowsDrawn())
        assertTrue(app.search.unavailable)
        app.search.choose(usdaEgg)
        app.review.addToLog()
        app.assertOneEntryReplaced("fndds_1")
    }

    @Test fun aUsdaRowReplacesTheEntryKeepingItsAmountAndIsLearnedOnce() = onEachScreen { app ->
        app.diaryHas(wrong.copy(enteredAmount = 200.0, enteredUnit = MeasureUnit.GRAM))
        app.searchWithRows()
        app.search.choose(app.usdaRowsDrawn().single())
        assertEquals(ProductReviewOrigin.CORRECTION, app.review.origin)
        assertEquals("200", app.review.amount)
        app.review.addToLog()
        assertTrue(app.calls.toString(), "replace wrong with fndds_1 200.0 g LUNCH 2026-10-28" in app.calls)
        app.assertOneEntryReplaced("fndds_1")
        assertEquals("the strip's own learning, once", 1, app.calls.count { it == "reinforce fndds_1" })
        assertEquals("nothing learned beside it", emptyList<String>(), app.reinforced)
        assertFalse("the strip closes", app.feedback.isOpen(wrong))
        assertTrue("no network for its servings", app.calls.none { it.startsWith("fetch") })
    }

    @Test fun aCorrectionThroughFindBetterMatchEndsOnAUsdaRow() {
        val app = appOn(MealSlot.LUNCH)
        app.searchWithRows()
        app.search.choose(found)
        app.review.findBetterMatch()
        assertEquals(ProductReviewOrigin.CORRECTION, app.search.reviewOrigin)
        app.search.onQuery("egg")
        app.search.choose(app.usdaRowsDrawn().single())
        app.review.addToLog()
        app.assertOneEntryReplaced("fndds_1")
    }

    @Test fun aRefusedUsdaReplacementIsNotLearned() {
        val app = appOn(MealSlot.LUNCH)
        app.replacement = ReplacementResult.Failure("That saved entry is no longer available.")
        app.searchWithRows()
        app.search.choose(usdaEgg)
        app.review.addToLog()
        assertEquals("That saved entry is no longer available.", app.notices.message)
        assertTrue(app.calls.toString(), app.calls.none { it.startsWith("reinforce") })
        assertEquals(emptyList<String>(), app.reinforced)
    }

    @Test fun anEntryGoneBeforeAddStoresNoFood() {
        val app = appOn(MealSlot.LUNCH)
        app.searchOnTheStrip()
        app.search.choose(found)
        app.diaryHas()
        app.review.addToLog()
        app.assertGoneSaidAndNothingWritten()
        assertEquals("nothing stored or written at all", emptyList<String>(), app.calls)
    }

    @Test fun usdaRowsFollowTheTypingAndLeaveWithTheSearch() {
        val app = appOn(MealSlot.LUNCH)
        app.searchWithRows()
        app.search.onQuery("milk")
        assertEquals("the old rows go at once", emptyList<Any>(), app.owners.usdaCorrection.rowsFor(app.search.query))
        assertEquals("nothing is sent for typing", listOf(wrong.sourcePhrase), app.offSearched)
        app.search.onQuery("egg")
        assertEquals(listOf(usdaEgg), app.usdaRowsDrawn())
        app.search.close()
        assertEquals(emptyList<Any>(), app.owners.usdaCorrection.rowsFor("egg"))
    }

    @Test fun aLateUsdaAnswerForOlderTextIsNotDrawn() {
        val app = appOn(MealSlot.LUNCH)
        app.usdaFoods = listOf(usdaEgg)
        val usda = app.owners.usdaCorrection
        app.searchOnTheStrip()
        app.io.holding = true
        app.pausesEnd = true
        usda.typed("egg")
        usda.typed("milk")
        assertEquals(emptyList<Any>(), usda.rowsFor("milk"))
        app.io.runAll()
        assertEquals("only for the text as it reads", emptyList<Any>(), usda.rowsFor("egg"))
        assertEquals(listOf(usdaEgg), usda.rowsFor("milk"))
    }

    @Test fun aLateUsdaAnswerAfterTheSearchClosedStaysOut() {
        val app = appOn(MealSlot.LUNCH)
        app.usdaFoods = listOf(usdaEgg)
        val usda = app.owners.usdaCorrection
        app.searchOnTheStrip()
        app.io.holding = true
        app.pausesEnd = true
        usda.typed("egg")
        app.search.close()
        app.io.runAll()
        assertEquals(emptyList<Any>(), usda.rows)
    }

    @Test fun theEntrysOwnFoodIsNotOfferedButAnotherStoredOneIs() {
        val app = appOn(MealSlot.LUNCH)
        app.diaryHas(wrong.copy(foodId = usdaEgg.id))
        val stored = food("fndds_2", "Egg, white")
        app.diary.foods = listOf(stored)
        app.owners.session.refresh()
        app.usdaFoods = listOf(usdaEgg, stored)
        app.searchOnTheStrip()
        assertEquals(listOf(stored), app.usdaRowsDrawn())
    }

    private fun AppKit.entryIsOnStoredProduct() {
        diaryHas(wrong.copy(foodId = "stored"))
        diary.foods = listOf(food("stored", "Mine").copy(barcode = barcode))
        owners.session.refresh()
    }

    @Test fun theEntrysOwnFoodIsNotAnOpenFoodFactsRowByIdOrByBarcode() {
        val byBarcode = appOn(MealSlot.LUNCH)
        byBarcode.entryIsOnStoredProduct()
        val same = food("off-$barcode", "Same").copy(barcode = barcode)
        byBarcode.offFoods = listOf(same, found)
        byBarcode.searchOnTheStrip()
        assertEquals(listOf(found), byBarcode.search.results)

        val byId = appOn(MealSlot.LUNCH)
        byId.diaryHas(wrong.copy(foodId = "off-1"))
        byId.offFoods = listOf(food("off-1", "Same"), found)
        byId.searchOnTheStrip()
        assertEquals(listOf(found), byId.search.results)
    }

    @Test fun onlyTheEntrysOwnFoodAnsweringIsNoResults() {
        val app = appOn(MealSlot.LUNCH)
        app.entryIsOnStoredProduct()
        app.offFoods = listOf(food("off-$barcode", "Same").copy(barcode = barcode))
        app.searchOnTheStrip()
        assertEquals(emptyList<Any>(), app.search.results)
        assertTrue(app.search.message.orEmpty().startsWith("No usable result"))
    }

    @Test fun aLaterSearchNotFromTheStripOffersThatFoodAgain() {
        val app = appOn(MealSlot.LUNCH)
        app.entryIsOnStoredProduct()
        val same = food("off-$barcode", "Same").copy(barcode = barcode)
        app.offFoods = listOf(same)
        app.searchOnTheStrip()
        app.search.close()
        app.search.open("same")
        app.search.submit()
        assertEquals(listOf(same), app.search.results)
    }

    @Test fun aPickWhileTheSearchWaitsDropsItSoNothingIsSentLater() {
        val app = appOn(MealSlot.LUNCH)
        app.usdaFoods = listOf(usdaEgg)
        app.io.holding = true
        app.searchOnTheStrip()
        assertTrue(app.search.busy)
        app.search.choose(usdaEgg)
        assertFalse(app.search.busy)
        app.io.runAll()
        assertEquals(emptyList<String>(), app.offSearched)
        assertEquals(emptyList<Any>(), app.search.results)
    }

    @Test fun theStripWaitsThroughTheCorrectionsSearchAndItsReview() {
        val app = appOn(MealSlot.LUNCH)
        assertFalse(app.owners.feedbackHeld())
        app.searchOnTheStrip()
        assertTrue("the search", app.owners.feedbackHeld())
        app.search.choose(found)
        assertTrue("the review", app.owners.feedbackHeld())
        app.review.dismiss()
        assertTrue("the search again", app.owners.feedbackHeld())
        app.search.close()
        assertFalse(app.owners.feedbackHeld())
    }

    @Test fun anOrdinarySearchDoesNotHoldTheStrip() {
        val app = appOn(MealSlot.LUNCH)
        app.search.open("egg")
        assertFalse(app.owners.feedbackHeld())
        app.search.choose(found)
        assertFalse(app.owners.feedbackHeld())
    }

    @Test fun backFromTheReviewReturnsToTheSearchAndWritesNothing() = onEachScreen { app ->
        app.searchWithRows()
        app.search.choose(found)
        app.review.dismiss()
        assertNull(app.review.product)
        assertTrue(app.search.open)
        assertEquals(ProductReviewOrigin.CORRECTION, app.search.reviewOrigin)
        assertEquals(wrong.sourcePhrase, app.search.query)
        assertEquals(listOf(found), app.search.results)
        assertEquals(listOf(usdaEgg), app.usdaRowsDrawn())
        assertEquals("nothing sent again", 1, app.offSearched.size)
        assertEquals("nothing written", emptyList<String>(), app.calls)
        assertTrue("the strip is still there", app.feedback.isOpen(wrong))
        app.search.choose(found)
        app.review.addToLog()
        app.assertOneEntryReplaced("found")
    }

    @Test fun backFromAReviewNotPickedOnTheSearchStaysClosed() {
        val scanned = appOn(MealSlot.LUNCH)
        scanned.barcodes[barcode] = found
        scanned.searchOnTheStrip()
        scanned.search.choose(found)
        scanned.review.rescan()
        scanned.scan.onBarcode(barcode)
        scanned.review.dismiss()
        assertFalse(scanned.search.open)

        val ordinary = appOn(MealSlot.LUNCH)
        ordinary.search.open("egg")
        ordinary.search.choose(found)
        ordinary.review.dismiss()
        assertFalse(ordinary.search.open)
    }

    @Test fun aSearchNotFromTheStripStillLogsToItsMeal() {
        val app = appOn(MealSlot.LUNCH)
        app.searchOnTheStrip()
        app.search.close()
        app.search.open("found")
        app.search.choose(found)
        assertEquals(ProductReviewOrigin.MEAL, app.review.origin)
        app.review.add(MealSlot.LUNCH)
        assertEquals(listOf("review log found LUNCH"), app.calls.filter { it.contains("log") })
        assertTrue(app.calls.none { it.startsWith("replace") })
    }
}
