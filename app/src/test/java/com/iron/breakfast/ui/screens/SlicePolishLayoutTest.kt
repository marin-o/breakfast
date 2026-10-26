package com.iron.breakfast.ui.screens

import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.ServingConversion
import com.iron.breakfast.ui.components.mealRowFits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.iron.breakfast.search.OffSearchStatus
import com.iron.breakfast.search.offSearchStatus
import com.iron.breakfast.library.servingRowName
import com.iron.breakfast.scan.nutrientFieldsFitRow

class SlicePolishLayoutTest {
    @Test fun offlineWinsOverMessage() {
        assertEquals(OffSearchStatus.Offline, offSearchStatus(unavailable = true, message = "No usable result"))
        assertEquals(OffSearchStatus.Offline, offSearchStatus(unavailable = true, message = "timeout"))
    }

    @Test fun noUsableResultIsNoResults() {
        assertEquals(OffSearchStatus.NoResults, offSearchStatus(unavailable = false, message = "No usable result for this query."))
    }

    @Test fun anyOtherMessageIsFailed() {
        assertEquals(OffSearchStatus.Failed, offSearchStatus(unavailable = false, message = "Open Food Facts returned an error."))
    }

    @Test fun servingNameKeepsItsCount() {
        val bundled = ServingConversion(MeasureUnit.TABLESPOON, 6.5, "generic tablespoon estimate", true)
        assertEquals("1 tbsp", servingRowName(bundled))
        val personal = ServingConversion(MeasureUnit.GRAM, 330.0, "personal", label = "bottle")
        assertEquals("1 bottle", servingRowName(personal))
        val typedCount = ServingConversion(MeasureUnit.GRAM, 40.0, "personal", label = "2 slices")
        assertEquals("2 slices", servingRowName(typedCount))
        val blankLabel = ServingConversion(MeasureUnit.TABLESPOON, 15.0, "household", label = "  ")
        assertEquals("1 tbsp", servingRowName(blankLabel))
    }

    @Test fun fourMealChoicesStayOnOneRowOnlyWhenWideEnough() {
        // 360dp screen with the 24dp sheet padding each side leaves 312dp: too narrow for "Breakfast" in one row.
        assertFalse(mealRowFits(312f, 1f))
        // 412dp screen leaves 364dp: wide enough at normal font.
        assertTrue(mealRowFits(364f, 1f))
        // Large font shrinks the row back to the grid even on the wide screen.
        assertFalse(mealRowFits(364f, 1.3f))
    }

    @Test fun fourNutrientFieldsStayOnOneRowOnlyWhenWideEnough() {
        // 390dp mock width leaves 342dp: 79.5dp per field, enough at normal font.
        assertTrue(nutrientFieldsFitRow(342f, 1f))
        // 360dp screen leaves 312dp: 72dp per field, too narrow, so a 2x2 grid.
        assertFalse(nutrientFieldsFitRow(312f, 1f))
        // Large font on the mock width also switches to the grid.
        assertFalse(nutrientFieldsFitRow(342f, 1.3f))
    }
}
