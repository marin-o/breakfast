package com.iron.breakfast.app

import androidx.compose.runtime.snapshots.Snapshot
import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.resolve.food
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun aLogAddOffersItsUndoAndANewDayStartMovesAFollowerOfToday() {
        val app = AppKit()
        app.log.open(MealSlot.LUNCH)
        app.log.onDraft("2 eggs")
        app.log.submit()
        assertEquals("1 food added to Lunch", app.undo.loggedMessage)
        // Noon: a day that starts at 13:00 has not started yet, so today is yesterday, and the follower goes with it.
        while (app.settings.dayStartHour != 13) app.settings.shiftDayStart(1)
        assertEquals("2026-10-27", app.day.todayKey)
        assertEquals("2026-10-27", app.day.selectedDayKey)
    }
}
