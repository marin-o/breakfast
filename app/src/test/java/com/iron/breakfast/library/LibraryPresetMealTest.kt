package com.iron.breakfast.library

import com.iron.breakfast.domain.MealSlot
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Library food page's preselected meal follows the Meal setting. */
class LibraryPresetMealTest {
    @Test fun theLibraryPreselectsTheSettingsMealAndStaysChangeable() {
        assertEquals(MealSlot.DINNER, libraryPresetMeal(MealSlot.DINNER, MealSlot.BREAKFAST))
        assertEquals(MealSlot.BREAKFAST, libraryPresetMeal(null, MealSlot.BREAKFAST))
        // Ask: today's preselection.
        assertEquals(MealSlot.LUNCH, libraryPresetMeal(null, null))
    }
}
