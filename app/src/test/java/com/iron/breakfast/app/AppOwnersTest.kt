package com.iron.breakfast.app

import com.iron.breakfast.scan.ProductReviewOrigin
import androidx.compose.runtime.snapshots.Snapshot
import com.iron.breakfast.domain.FoodCatalog
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
        app.scan.begin()
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
        app.scan.begin()
        app.scan.end()
        assertEquals("100 g oil", app.log.draft)
        assertTrue(app.log.open)
        app.scan.begin()
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
        app.scan.begin()
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
        app.scan.begin()
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
        val egg = FoodCatalog.fixtures.first { it.id == "egg" }.copy(origin = FoodOrigin.CUSTOM)
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
        assertEquals("SIL Open Font License", AppKit().owners.licenceText())
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
