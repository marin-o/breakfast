package mk.bosa.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Library food page's preselected meal follows the Meal setting. */
class PresetMealTest {
    @Test fun theLibraryPreselectsTheSettingsMealAndStaysChangeable() {
        assertEquals(MealSlot.DINNER, presetMeal(MealSlot.DINNER, MealSlot.BREAKFAST))
        assertEquals(MealSlot.BREAKFAST, presetMeal(null, MealSlot.BREAKFAST))
        // Ask: today's preselection.
        assertEquals(MealSlot.LUNCH, presetMeal(null, null))
    }
}
