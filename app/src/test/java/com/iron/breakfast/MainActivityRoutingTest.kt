package com.iron.breakfast

import com.iron.breakfast.domain.MealSlot
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityRoutingTest {
    @Test fun mealWordsInsideFoodNamesAreNotMarkers() {
        assertEquals(emptyList<MealSlot>(), mealMarkerSlots("breakfast cereal, 100g oats"))
        assertEquals(emptyList<MealSlot>(), mealMarkerSlots("100g oats, lunch meat"))
    }

    @Test fun onlyWholeCommaSegmentMarkerIsRemovedFromDraft() {
        assertEquals("100g oats", removeMealMarkerToken("breakfast, 100g oats", MealSlot.BREAKFAST))
    }
}
