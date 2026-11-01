package com.iron.breakfast.app

import com.iron.breakfast.scan.ProductReviewOrigin
import androidx.compose.runtime.snapshots.Snapshot
import com.iron.breakfast.domain.TestFoods
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.resolve.food
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wiring of [AppOwners] that no single owner's test sees, over the app as the view model builds it ([AppKit]):
 * the saved state, what runs when storage opens, the Library's delete reaching the other owners, the session
 * listeners and the settings observers.
 */
class AppOwnersTest {
    @Test fun theLogDraftTheDayAndTheMealComeBackAfterProcessDeath() {
        val before = AppKit()
        before.selectDay("2026-10-20")
        before.openMeal(MealSlot.DINNER)
        before.log.open(MealSlot.DINNER)
        before.log.onDraft("2 eggs")
        // What the main thread's frame does on Android: the changes reach the mirrors.
        Snapshot.sendApplyNotifications()
        val after = AppKit(before.savedState)
        assertEquals("2 eggs", after.log.draft)
        assertEquals("2026-10-20", after.day.selectedDayKey)
        assertEquals(MealSlot.DINNER, after.meal)
    }

    @Test fun aKnownBarcodeFromTheLogComesBackAsAPartOfTheDraft() {
        val coke = food("coke", "Coke Zero")
        val app = AppKit(foods = listOf(coke))
        app.barcodes["4006381333931"] = coke
        app.log.open(null)
        app.log.onDraft("100 g oil,")
        app.scan.begin(forLog = true)
        app.scan.onBarcode("4006381333931")
        assertEquals(ProductReviewOrigin.LOG_PART, app.review.origin)
        app.review.onAmount("330")
        app.review.onUnit("g")
        app.review.addToLog()
        assertEquals("100 g oil, 330 g Coke Zero", app.log.draft)
        assertTrue(app.log.open)
        assertEquals(coke.id, app.log.pinsSnapshot()["330 g Coke Zero"]?.id)
        assertEquals("nothing is logged until the Log's Add", ProductReviewOrigin.LIBRARY, app.review.origin)
    }

    @Test fun backingOutOfTheScannerOrTheSheetLeavesTheDraftAlone() {
        val coke = food("coke", "Coke Zero")
        val app = AppKit(foods = listOf(coke))
        app.barcodes["4006381333931"] = coke
        app.log.open(null)
        app.log.onDraft("100 g oil")
        app.scan.begin(forLog = true)
        app.scan.end()
        assertEquals("100 g oil", app.log.draft)
        assertTrue(app.log.open)
        app.scan.begin(forLog = true)
        app.scan.onBarcode("4006381333931")
        app.review.close()
        assertEquals("100 g oil", app.log.draft)
        assertTrue(app.log.open)
    }

    @Test fun anUnknownBarcodeFromTheLogGoesThroughNewFoodToTheSameReview() {
        val app = AppKit()
        app.settings.lookUpBarcodesSetting.set(false)
        app.log.open(null)
        app.log.onDraft("100 g oil")
        app.scan.begin(forLog = true)
        app.scan.onBarcode("4006381333931")
        assertTrue(app.foodForm.open)
        app.foodForm.draft.name = "Ajvar"
        app.foodForm.draft.calories = "100"
        app.foodForm.save()
        assertFalse(app.foodForm.open)
        assertTrue("Look up unknown barcodes is Off: no request", app.calls.none { it.startsWith("fetch") })
        assertEquals(ProductReviewOrigin.LOG_PART, app.review.origin)
        assertEquals("Ajvar", app.review.product?.name)
        assertEquals("100 g oil", app.log.draft)
        assertTrue(app.log.open)
    }

    @Test fun findBetterMatchFromTheLogThenCreateFoodComesBackToTheLog() {
        val coke = food("coke", "Coke Zero")
        val app = AppKit(foods = listOf(coke))
        app.barcodes["4006381333931"] = coke
        app.log.open(null)
        app.log.onDraft("100 g oil")
        app.scan.begin(forLog = true)
        app.scan.onBarcode("4006381333931")
        app.review.findBetterMatch()
        assertEquals(ProductReviewOrigin.LOG_PART, app.owners.search.reviewOrigin)
        app.foodForm.open("Ajvar", reviewAfterSave = app.owners.search.reviewOrigin)
        app.foodForm.draft.calories = "100"
        app.foodForm.save()
        assertEquals(ProductReviewOrigin.LOG_PART, app.review.origin)
        assertEquals("Ajvar", app.review.product?.name)
        app.review.addToLog()
        assertTrue(app.log.draft, app.log.draft.startsWith("100 g oil, "))
        assertTrue(app.log.draft, app.log.draft.endsWith("Ajvar"))
    }

    @Test fun notRightSearchOnDailyStillSavesToTheLibrary() {
        val coke = food("coke", "Coke Zero")
        val app = AppKit()
        app.owners.search.open("coke")
        app.owners.search.choose(coke)
        assertEquals(ProductReviewOrigin.LIBRARY, app.review.origin)
    }

    /** "polenta, argeta" with nothing in the diary's foods: Add leaves both on the Log. */
    private fun unmatched(app: AppKit) {
        app.unmatched = listOf("polenta", "argeta")
        app.log.open(MealSlot.LUNCH)
        app.log.onDraft("polenta, argeta")
        app.log.submit()
    }

    @Test fun typingSearchesNothingAndAddSearchesEachUnmatchedFood() {
        val app = AppKit()
        app.unmatched = listOf("polenta", "argeta")
        app.log.open(MealSlot.LUNCH)
        app.log.onDraft("polenta, argeta")
        assertEquals(emptyList<String>(), app.offSearched)
        app.log.submit()
        assertEquals(listOf("polenta", "argeta"), app.offSearched)
        assertTrue("the Log stays, not the resolver", app.log.open)
        assertFalse(app.resolve.resolverOpen)
        assertEquals(listOf("polenta", "argeta"), app.resolve.parts)
        assertEquals(MealSlot.LUNCH, app.resolve.queueMeal)
    }

    @Test fun aTappedResultIsForItsOwnPartAndTheNextFollows() {
        val app = AppKit()
        val argeta = food("off-argeta", "Argeta")
        unmatched(app)
        // The second food's result, while the first is still open.
        app.resolve.focus("argeta")
        app.review.choose(argeta, origin = com.iron.breakfast.scan.ProductReviewOrigin.QUEUE)
        app.review.onAmount("100")
        app.review.add(MealSlot.LUNCH)
        assertTrue(app.calls.toString(), app.calls.any { it.contains("review log off-argeta LUNCH") })
        assertEquals(listOf("polenta"), app.resolve.parts)
        assertEquals("polenta", app.log.draft)
        assertTrue(app.log.open)
        assertFalse(app.resolve.resolverOpen)
        // The last one: the Log closes.
        val polenta = food("off-polenta", "Polenta")
        app.review.choose(polenta, origin = com.iron.breakfast.scan.ProductReviewOrigin.QUEUE)
        app.review.onAmount("100")
        app.review.add(MealSlot.LUNCH)
        assertTrue(app.calls.toString(), app.calls.any { it.contains("review log off-polenta LUNCH") })
        assertFalse(app.log.open)
    }

    @Test fun anEditKeepsTheOtherUnmatchedPartAndStartsNoSearch() {
        val app = AppKit()
        unmatched(app)
        val asked = app.offSearched.toList()
        app.log.onDraft("polenta, argeta, x")
        assertEquals(listOf("polenta", "argeta"), app.resolve.parts)
        app.log.onDraft("polent, argeta")
        assertEquals("the retyped part leaves the queue", listOf("argeta"), app.resolve.parts)
        assertTrue(app.resolve.queueActive)
        assertEquals(asked, app.offSearched)
        assertEquals(MealSlot.LUNCH, app.log.targetMeal())
        app.log.onDraft("x")
        assertFalse(app.resolve.queueActive)
    }

    @Test fun backEndsTheQueueAndLeavesTheText() {
        val other = AppKit()
        unmatched(other)
        other.log.close()
        assertFalse(other.resolve.queueActive)
        assertEquals("polenta, argeta", other.log.draft)
    }

    @Test fun aScanFromTheLogDuringAQueueAppendsAndLeavesTheQueue() {
        val coke = food("coke", "Coke Zero")
        val app = AppKit(foods = listOf(coke))
        app.barcodes["4006381333931"] = coke
        unmatched(app)
        app.scan.begin(forLog = true)
        app.scan.onBarcode("4006381333931")
        assertEquals(ProductReviewOrigin.LOG_PART, app.review.origin)
        assertEquals("100", app.review.amount)
        app.review.onUnit("g")
        app.review.addToLog()
        assertEquals("polenta, argeta, 100 g Coke Zero", app.log.draft)
        assertEquals(listOf("polenta", "argeta"), app.resolve.parts)
        assertTrue(app.resolve.queueActive)
        assertTrue(app.calls.none { it.startsWith("review log") })
        // Resolving a queued part afterwards takes only that part out of the draft.
        app.review.choose(food("off-argeta", "Argeta"), origin = ProductReviewOrigin.QUEUE)
        app.review.add(MealSlot.LUNCH)
        assertEquals("argeta, 100 g Coke Zero", app.log.draft)
        // The last queued part leaves the scanned one for the Log's Add.
        app.review.choose(food("off-argeta", "Argeta"), origin = ProductReviewOrigin.QUEUE)
        app.review.add(MealSlot.LUNCH)
        assertEquals("100 g Coke Zero", app.log.draft)
        assertTrue(app.log.open)
    }

    @Test fun anUnknownBarcodeFromTheLogDuringAQueueComesBackWithTheRowsIntact() {
        val app = AppKit()
        unmatched(app)
        app.settings.lookUpBarcodesSetting.set(false)
        app.scan.begin(forLog = true)
        app.scan.onBarcode("4006381333931")
        assertTrue(app.foodForm.open)
        app.foodForm.draft.name = "Ajvar"
        app.foodForm.draft.calories = "100"
        app.foodForm.save()
        assertEquals(ProductReviewOrigin.LOG_PART, app.review.origin)
        assertTrue("not logged", app.calls.none { it.startsWith("save and log") })
        app.review.addToLog()
        assertTrue(app.log.draft, app.log.draft.startsWith("polenta, argeta, "))
        assertEquals(listOf("polenta", "argeta"), app.resolve.parts)
        assertTrue(app.resolve.queueActive)
    }

    @Test fun aPickInTheLibraryReplacesItsPartAndTheQueueGoesOn() {
        val yogurt = food("yogurt", "Yogurt")
        val app = AppKit()
        unmatched(app)
        app.log.resolveNotInFoods(com.iron.breakfast.domain.DraftRow.Unresolved("polenta", 0, 7))
        assertFalse(app.log.open)
        app.resolve.finishPart(yogurt)
        assertTrue(app.log.open)
        assertEquals("Yogurt, argeta", app.log.draft)
        assertEquals(listOf("argeta"), app.resolve.parts)
        assertTrue(app.resolve.queueActive)
        app.review.choose(food("off-argeta", "Argeta"), origin = ProductReviewOrigin.QUEUE)
        app.review.add(MealSlot.LUNCH)
        assertEquals("the pick stays, the deleted text does not return", "Yogurt", app.log.draft)
        assertTrue(app.log.open)
    }

    @Test fun leavingTheLibraryWithoutAPickKeepsTheQueue() {
        val app = AppKit()
        unmatched(app)
        app.log.resolveNotInFoods(com.iron.breakfast.domain.DraftRow.Unresolved("polenta", 0, 7))
        assertTrue(app.resolve.cancelPart())
        assertEquals("polenta, argeta", app.log.draft)
        assertEquals(listOf("polenta", "argeta"), app.resolve.parts)
        assertTrue(app.resolve.queueActive)
    }

    @Test fun theLogsMealButtonChangesTheQueuesMeal() {
        val app = AppKit()
        unmatched(app)
        app.log.chooseMeal(MealSlot.DINNER)
        assertEquals(MealSlot.DINNER, app.resolve.queueMeal)
        app.review.choose(food("off-argeta", "Argeta"), origin = ProductReviewOrigin.QUEUE)
        assertEquals(MealSlot.DINNER, app.review.queueMeal())
    }

    /** "bananna" is a typo of several foods, so Add leaves it as a choice; "xyzq" matches nothing. */
    private fun choiceKit(vararg parts: String) = AppKit().also { app ->
        app.plannerFoods = listOf(
            food("banana", "Banana").copy(defaultAmount = 100.0, defaultUnit = com.iron.breakfast.domain.MeasureUnit.GRAM),
            food("bread", "Banana bread").copy(defaultAmount = 100.0, defaultUnit = com.iron.breakfast.domain.MeasureUnit.GRAM),
        )
        app.unmatched = parts.toList()
        app.log.open(MealSlot.LUNCH)
        app.log.onDraft(parts.joinToString(", "))
        app.log.submit()
    }

    @Test fun aLocalChoiceListsItsFoodsAndIsNotSearched() {
        val app = choiceKit("bananna", "xyzq")
        assertEquals(listOf("xyzq"), app.offSearched)
        assertEquals("queued in typed order", listOf("bananna", "xyzq"), app.resolve.parts)
        assertEquals(setOf("banana", "bread"), app.log.localChoices("bananna").map { it.food.id }.toSet())
        assertTrue(app.log.localChoices("xyzq").isEmpty())
    }

    @Test fun pickingALocalChoiceKeepsTheOtherPartAndItsResults() {
        val app = choiceKit("bananna", "xyzq")
        val before = app.owners.offParts.stateOf("xyzq")
        app.resolve.focus("bananna")
        app.resolve.resolve(app.log.localChoices("bananna").first().food)
        assertEquals("logged once", 1, app.queueLogged.size)
        assertEquals("xyzq", app.log.draft)
        assertEquals(listOf("xyzq"), app.resolve.parts)
        assertEquals(before, app.owners.offParts.stateOf("xyzq"))
        assertEquals("no new search", listOf("xyzq"), app.offSearched)
        assertEquals("Logged!", app.owners.confirmations.message)
        assertTrue(app.log.open)
    }

    @Test fun theOrderOfThePartsDoesNotMatter() {
        val app = choiceKit("xyzq", "bananna")
        app.resolve.focus("bananna")
        app.resolve.resolve(app.log.localChoices("bananna").first().food)
        assertEquals("xyzq", app.log.draft)
        assertEquals(listOf("xyzq"), app.resolve.parts)
        assertEquals(1, app.queueLogged.size)
    }

    @Test fun aPickKeepsTheAmountTypedForThePart() {
        val app = choiceKit("200 g bananna")
        val choice = app.log.localChoices("200 g bananna").first()
        assertEquals("200 g", choice.quantityLine)
        app.resolve.focus("200 g bananna")
        app.resolve.resolve(choice.food)
        assertEquals(listOf("200.0 g ${choice.food.id}"), app.queueLogged)
        assertFalse("the last part closes the Log", app.log.open)
    }

    @Test fun aUnitItsFoodLacksIsNeitherQueuedNorSearched() {
        val app = AppKit()
        app.plannerFoods = listOf(food("kefir", "Kefir"))
        app.unmatched = listOf("2 tbsp Kefir", "xyzq")
        app.log.open(MealSlot.LUNCH)
        app.log.onDraft("2 tbsp Kefir, xyzq")
        app.log.submit()
        assertEquals(listOf("xyzq"), app.offSearched)
        assertEquals(listOf("xyzq"), app.resolve.parts)
        assertEquals("2 tbsp Kefir, xyzq", app.log.draft)
    }

    @Test fun localChoicesKeepToTheScriptTyped() {
        val app = AppKit()
        app.plannerFoods = listOf(food("c", "Банана"), food("l", "Banana"), food("l2", "Banana bread"))
        assertTrue(app.log.localChoices("bananna").all { it.food.id != "c" })
    }

    @Test fun aPartTypedDuringAQueueIsAddedToItOnTheNextSubmit() {
        val app = AppKit()
        app.unmatched = listOf("xyzq")
        app.log.open(MealSlot.LUNCH)
        app.log.onDraft("xyzq")
        app.log.submit()
        assertEquals(listOf("xyzq"), app.offSearched)
        app.log.onDraft("xyzq, abcq")
        assertEquals("the new part is not queued yet", listOf("abcq"), app.log.draftView().needsMatchParts - app.resolve.parts.toSet())
        app.unmatched = listOf("xyzq", "abcq")
        app.log.submit()
        assertEquals(listOf("xyzq", "abcq"), app.resolve.parts)
        assertEquals("the queued one is not asked again", listOf("xyzq", "abcq"), app.offSearched)
        assertTrue(app.resolve.queueActive)
        assertEquals("xyzq, abcq", app.log.draft)
    }

    @Test fun onlyPartsWithNoValueLeaveNoNeedForAMatch() {
        val app = AppKit()
        app.plannerFoods = listOf(food("kefir", "Kefir"))
        app.unmatched = listOf("2 tbsp Kefir")
        app.log.open(MealSlot.LUNCH)
        app.log.onDraft("2 tbsp Kefir")
        app.log.submit()
        assertEquals(null, app.log.error)
        assertTrue(app.log.open)
        assertFalse(app.resolve.queueActive)
    }

    @Test fun aMixedBatchKeepsAPartWithNoValueOutOfTheResolver() {
        val app = AppKit()
        app.plannerFoods = listOf(food("kefir", "Kefir"))
        app.libraryFoods = listOf(food("egg"))
        app.unmatched = listOf("2 tbsp Kefir", "xyzq")
        app.openFocusedLibrary(MealSlot.LUNCH)
        app.library.onInput("1 Kefir, 2 tbsp Kefir, xyzq")
        app.library.submitInput()
        assertEquals(listOf("xyzq"), app.resolve.parts)
        assertEquals("xyzq, 2 tbsp Kefir", app.library.input)
        app.resolve.skip()
        assertEquals("2 tbsp Kefir", app.library.input)
    }

    @Test fun notRightWaitsWhileTheLogCoversItWithFoodsLeft() {
        val app = AppKit()
        assertFalse(app.owners.feedbackHeld())
        unmatched(app)
        assertTrue(app.owners.feedbackHeld())
        app.log.close()
        assertFalse(app.owners.feedbackHeld())
    }

    @Test fun closingAReviewOfAPartComesBackToTheLogNotTheResolver() {
        val app = AppKit()
        unmatched(app)
        app.review.choose(food("off-argeta", "Argeta"), origin = com.iron.breakfast.scan.ProductReviewOrigin.QUEUE)
        app.review.close()
        assertFalse(app.resolve.resolverOpen)
        assertTrue(app.resolve.queueActive)
        assertTrue(app.log.open)
    }

    @Test fun skippingTheLastPartClosesTheLog() {
        val app = AppKit()
        app.unmatched = listOf("polenta")
        app.log.open(MealSlot.LUNCH)
        app.log.onDraft("polenta")
        app.log.submit()
        app.resolve.skip()
        assertFalse(app.log.open)
    }

    @Test fun aRestoredEditFoodClosesWhenStorageOpensWithoutItsFood() {
        val jam = food("jam", "Jam")
        val before = AppKit(foods = listOf(jam))
        before.foodForm.openEdit(jam)
        Snapshot.sendApplyNotifications()
        assertTrue("the food is still there", AppKit(before.savedState, foods = listOf(jam)).foodForm.open)
        val after = AppKit(before.savedState)
        assertFalse(after.foodForm.open)
        assertNull(after.foodForm.editingFoodId)
    }

    @Test fun deletingAFoodClosesItsFormDropsItsPinAndClosesItsReview() {
        // A personal food: only those can be deleted.
        val egg = TestFoods.fixtures.first { it.id == "egg" }.copy(origin = FoodOrigin.CUSTOM)
        val app = AppKit(foods = listOf(egg))
        app.log.onDraft("2 eggs")
        app.log.chooseSuggestion(egg)
        assertTrue(app.log.pinsSnapshot().isNotEmpty())
        app.openLibrary()
        app.foodForm.openEdit(egg)
        app.library.askDelete(egg)
        app.library.confirmDelete()
        assertFalse(app.foodForm.open)
        assertTrue(app.log.pinsSnapshot().isEmpty())
        // The same for a review of it.
        val other = AppKit(foods = listOf(egg))
        other.openLibrary()
        other.review.choose(egg)
        other.library.askDelete(egg)
        other.library.confirmDelete()
        assertNull(other.review.product)
    }

    @Test fun addingAFoodFromTheLibraryConfirmsWithoutADialog() {
        val app = AppKit()
        app.openFocusedLibrary(MealSlot.LUNCH)
        app.library.log(food("kefir", "Kefir"), MealSlot.LUNCH, 200.0, com.iron.breakfast.domain.MeasureUnit.MILLILITER)
        assertEquals("Logged!", app.confirmations.message)
        assertNull(app.notices.message)
        assertEquals(listOf("LIBRARY"), app.shown())
    }

    /** The wiring half of `SettingsStateTest.aResetIsNotAChoice`: the safety copy's removal follows only a choice. */
    @Test fun deleteAllWithItsSettingsKeepsTheSafetyCopyItJustWrote() {
        val app = AppKit()
        app.settings.chooseKeepSafetyCopy(false)
        app.transfer.openDeleteAll()
        app.transfer.toggleDeleteAllAlsoSettings()
        app.transfer.confirmDeleteAll()
        assertTrue("settings went back to their defaults", app.settings.keepSafetyCopy)
        assertNotNull(app.documents.safetyCopy)
        // Choosing Don't keep still removes it.
        app.settings.chooseKeepSafetyCopy(false)
        assertNull(app.documents.safetyCopy)
    }

    /** D15: a diary set aside as damaged is told once storage opens, until dismissed; a new process tells it again. */
    @Test fun aDamagedDiaryIsToldUntilTheUserDismissesIt() {
        val app = AppKit(damagedDiary = true)
        assertEquals("The diary could not be read. Import an export to restore it.", app.notices.message)
        app.diaryHas()
        assertEquals("a reload does not stack it", "The diary could not be read. Import an export to restore it.", app.notices.message)
        app.notices.dismiss()
        assertFalse("seen: the marker goes", app.diary.damaged)
        app.diaryHas()
        assertNull(app.notices.message)
    }

    /** About, Licences: read by the owners on their IO dispatcher, not by the screen. */
    @Test fun theLicenceIsReadByTheOwners() = kotlinx.coroutines.runBlocking {
        assertEquals(
            "U.S. Department of Agriculture, Agricultural Research Service. FoodData Central, 2019. fdc.nal.usda.gov.\nFNDDS 2021-2023. CC0 1.0.\n\nSIL Open Font License",
            AppKit().owners.licenceText(),
        )
    }

    @Test fun aLogAddOffersItsUndoAndANewDayStartMovesAFollowerOfToday() {
        val app = AppKit()
        app.log.open(MealSlot.LUNCH)
        app.log.onDraft("2 eggs")
        app.log.submit()
        assertEquals("the batch keeps its Undo, and says Logged!", "Logged!", app.undo.loggedMessage)
        // Noon: a day that starts at 13:00 has not started yet, so today is yesterday, and the follower goes with it.
        while (app.settings.dayStartHour != 13) app.settings.shiftDayStart(1)
        assertEquals("2026-10-27", app.day.todayKey)
        assertEquals("2026-10-27", app.day.selectedDayKey)
    }
}
