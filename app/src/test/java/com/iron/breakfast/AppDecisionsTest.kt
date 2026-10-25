package com.iron.breakfast

import com.iron.breakfast.domain.MealSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppDecisionsTest {
    private fun overlay(
        scanner: Boolean = false,
        entryDetail: Boolean = false,
        productReview: Boolean = false,
        resolver: Boolean = false,
        offSearch: Boolean = false,
        foodForm: Boolean = false,
        libraryFood: Boolean = false,
        entryEdit: Boolean = false,
        settings: Boolean = false,
    ) = topOverlay(scanner, entryDetail, productReview, resolver, offSearch, foodForm, libraryFood, entryEdit, settings)

    @Test fun nothingOpenMeansNoOverlay() {
        assertNull(overlay())
    }

    @Test fun eachOverlayIsReportedOnItsOwn() {
        assertEquals(Overlay.SCANNER, overlay(scanner = true))
        assertEquals(Overlay.ENTRY_DETAIL, overlay(entryDetail = true))
        assertEquals(Overlay.PRODUCT_REVIEW, overlay(productReview = true))
        assertEquals(Overlay.RESOLVER, overlay(resolver = true))
        assertEquals(Overlay.OFF_SEARCH, overlay(offSearch = true))
        assertEquals(Overlay.FOOD_FORM, overlay(foodForm = true))
        assertEquals(Overlay.SETTINGS, overlay(settings = true))
        assertEquals(Overlay.LIBRARY_FOOD, overlay(libraryFood = true))
        assertEquals(Overlay.ENTRY_EDIT, overlay(entryEdit = true))
    }

    @Test fun theTopmostWins() {
        // The review sheet is drawn over the resolver, and the resolver over a food form.
        assertEquals(Overlay.PRODUCT_REVIEW, overlay(productReview = true, resolver = true, foodForm = true))
        assertEquals(Overlay.RESOLVER, overlay(resolver = true, foodForm = true))
        assertEquals(Overlay.SCANNER, overlay(scanner = true, foodForm = true, entryEdit = true))
    }

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
        assertEquals(true, isLibraryNoMatch(true, true, "zz", "zz", noResults = true, otherScreenOpen = overlay() != null))
        assertEquals(false, isLibraryNoMatch(true, true, "zz", "zz", noResults = true, otherScreenOpen = overlay(libraryFood = true) != null))
    }
}
