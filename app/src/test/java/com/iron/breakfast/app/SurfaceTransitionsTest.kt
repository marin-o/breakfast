package com.iron.breakfast.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceTransitionsTest {
    private fun change(before: List<Surface>, now: List<Surface>) = surfaceChange(before, now)

    @Test fun aScreenOverTheBaseGlidesInAndOut() {
        assertEquals(SurfaceChange(setOf(Surface.LOG), emptySet(), emptySet()), change(listOf(Surface.DAILY), listOf(Surface.DAILY, Surface.LOG)))
        assertEquals(SurfaceChange(emptySet(), setOf(Surface.LOG), emptySet()), change(listOf(Surface.DAILY, Surface.LOG), listOf(Surface.DAILY)))
    }

    @Test fun theBaseScreensAreOneSlotAndNeverGlide() {
        assertEquals(SurfaceChange(emptySet(), emptySet(), emptySet()), change(listOf(Surface.DAILY), listOf(Surface.MEAL)))
        assertEquals(SurfaceChange(emptySet(), emptySet(), emptySet()), change(listOf(Surface.MEAL), listOf(Surface.LIBRARY)))
    }

    @Test fun dialogsAndSheetsKeepTheirOwnMotion() {
        for (s in listOf(Surface.NOTICE, Surface.ENTRY_QUICK_EDIT, Surface.LIBRARY_FOOD_ADD, Surface.PRODUCT_REVIEW, Surface.MIXED_MEAL_PICKER, Surface.DISCARD_FOOD)) {
            assertEquals(SurfaceChange(emptySet(), emptySet(), emptySet()), change(listOf(Surface.DAILY), listOf(Surface.DAILY, s)))
        }
    }

    @Test fun theBaseStaysStillUnderTheEntryDetailAsItComesIn() {
        assertEquals(SurfaceChange(setOf(Surface.ENTRY_DETAIL), emptySet(), setOf(Surface.DAILY)), change(listOf(Surface.MEAL), listOf(Surface.ENTRY_DETAIL)))
        // Closing it, the base is simply there again and the detail glides out over it.
        assertEquals(SurfaceChange(emptySet(), setOf(Surface.ENTRY_DETAIL), emptySet()), change(listOf(Surface.ENTRY_DETAIL), listOf(Surface.MEAL)))
    }

    @Test fun aScreenReplacingAnotherCrosses() {
        assertEquals(
            SurfaceChange(setOf(Surface.FOOD_FORM), setOf(Surface.LIBRARY_FOOD), emptySet()),
            change(listOf(Surface.LIBRARY, Surface.LIBRARY_FOOD), listOf(Surface.LIBRARY, Surface.FOOD_FORM)),
        )
    }

    @Test fun theScannerComesAndGoesWithoutMotionAndWhatItCoveredComesBackAsItWas() {
        val none = SurfaceChange(emptySet(), emptySet(), emptySet())
        assertEquals(none, change(listOf(Surface.DAILY, Surface.LOG), listOf(Surface.SCANNER)))
        assertEquals(none, change(listOf(Surface.SCANNER), listOf(Surface.DAILY, Surface.LOG)))
    }

    @Test fun onlyScreensInTheAppsWindowAreRecorded() {
        assertTrue(Surface.entries.filter { it.recorded }.all { it.glides || it == Surface.DAILY })
        assertTrue(Surface.entries.none { it.base && it.glides })
        assertTrue(!Surface.SCANNER.glides && !Surface.SCAN_REVIEW_BACKDROP.glides)
    }
}
