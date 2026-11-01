package com.bosa.breakfast.app

import androidx.compose.runtime.snapshots.Snapshot
import com.bosa.breakfast.domain.MealEntry
import com.bosa.breakfast.domain.MealSlot
import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.domain.ReplacementResult
import com.bosa.breakfast.resolve.food
import com.bosa.breakfast.scan.ProductReviewOrigin
import com.bosa.breakfast.today.entry
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

    private fun AppKit.searchOnTheStrip() {
        feedback.search(wrong)
        search.submit()
    }

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
