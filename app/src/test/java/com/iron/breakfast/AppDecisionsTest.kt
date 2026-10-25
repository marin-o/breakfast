package com.iron.breakfast

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
    ) = topOverlay(scanner, entryDetail, productReview, resolver, offSearch, foodForm, libraryFood, entryEdit)

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
        assertEquals(Overlay.LIBRARY_FOOD, overlay(libraryFood = true))
        assertEquals(Overlay.ENTRY_EDIT, overlay(entryEdit = true))
    }

    @Test fun theTopmostWins() {
        // The review sheet is drawn over the resolver, and the resolver over a food form.
        assertEquals(Overlay.PRODUCT_REVIEW, overlay(productReview = true, resolver = true, foodForm = true))
        assertEquals(Overlay.RESOLVER, overlay(resolver = true, foodForm = true))
        assertEquals(Overlay.SCANNER, overlay(scanner = true, foodForm = true, entryEdit = true))
    }

    @Test fun anOverlayHidesTheNoMatchScreen() {
        assertEquals(true, isLibraryNoMatch(true, true, "zz", "zz", noResults = true, otherScreenOpen = overlay() != null))
        assertEquals(false, isLibraryNoMatch(true, true, "zz", "zz", noResults = true, otherScreenOpen = overlay(libraryFood = true) != null))
    }
}
