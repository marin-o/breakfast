package com.bosa.breakfast.ui.screens

import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.domain.ServingConversion
import org.junit.Assert.assertEquals
import org.junit.Test
import com.bosa.breakfast.search.OffSearchStatus
import com.bosa.breakfast.search.offSearchStatus
import com.bosa.breakfast.library.servingRowName

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
}
