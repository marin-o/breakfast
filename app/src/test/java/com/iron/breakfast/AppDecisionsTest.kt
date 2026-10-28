package com.iron.breakfast

import com.iron.breakfast.domain.MealSlot
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDecisionsTest {
    @Test fun aLibraryOpenedFromAMealGoesBackToThatMeal() {
        assertEquals(LibraryExit.MEAL, exitLibraryTarget(resolving = false, archivedOnly = false, origin = MealSlot.LUNCH))
    }

    @Test fun theLibraryTabGoesBackToDaily() {
        assertEquals(LibraryExit.DAILY, exitLibraryTarget(resolving = false, archivedOnly = false, origin = null))
    }

    @Test fun archivedFoodsGoBackToTheLibrary() {
        assertEquals(LibraryExit.LIBRARY, exitLibraryTarget(resolving = false, archivedOnly = true, origin = null))
    }

    @Test fun aResolveAlwaysGoesBackToTheLog() {
        // A resolve opens the Library on the Log's meal, but it returns to the Log, not to that meal.
        assertEquals(LibraryExit.LOG, exitLibraryTarget(resolving = true, archivedOnly = false, origin = MealSlot.BREAKFAST))
        assertEquals(LibraryExit.LOG, exitLibraryTarget(resolving = true, archivedOnly = false, origin = null))
    }

    @Test fun anOverlayHidesTheNoMatchScreen() {
        assertEquals(true, isLibraryNoMatch(true, true, "zz", "zz", noResults = true, otherScreenOpen = false))
        assertEquals(false, isLibraryNoMatch(true, true, "zz", "zz", noResults = true, otherScreenOpen = true))
    }
}
