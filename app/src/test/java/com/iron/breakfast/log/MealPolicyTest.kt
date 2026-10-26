package com.iron.breakfast.log

import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.iron.breakfast.LogSubmitPlan
import com.iron.breakfast.logSubmitPlan
import com.iron.breakfast.lastUsedMeal
import com.iron.breakfast.logTargetMealOf
import com.iron.breakfast.settings.enumOf
import com.iron.breakfast.settings.MealBounds
import com.iron.breakfast.settings.BeforeDayStart
import com.iron.breakfast.settings.MealMode

/** The Meal setting's policy on the Log page: By time (from the day start), Last used, and its place after what the user said. */
class MealPolicyTest {
    @Test fun byTimeCountsFromTheDayStart() {
        val bounds = MealBounds()
        // Day start 00:00: no hour is before it, so "Before day start" has no effect.
        BeforeDayStart.entries.forEach { before ->
            assertEquals(MealSlot.BREAKFAST, mealByTime(0, bounds, 0, before))
            assertEquals(MealSlot.SNACKS, mealByTime(23, bounds, 0, before))
        }
        // Day start 04:00: 00:30 and 02:00 belong to the previous diary day.
        assertEquals(MealSlot.SNACKS, mealByTime(0, bounds, 4, BeforeDayStart.SNACKS))
        assertEquals(MealSlot.DINNER, mealByTime(2, bounds, 4, BeforeDayStart.DINNER))
        assertNull(mealByTime(2, bounds, 4, BeforeDayStart.ASK))
        assertEquals(MealSlot.SNACKS, mealByTime(3, bounds, 4, BeforeDayStart.SNACKS))
        // From the day start on, the boundaries as usual.
        assertEquals(MealSlot.BREAKFAST, mealByTime(4, bounds, 4, BeforeDayStart.ASK))
        assertEquals(MealSlot.BREAKFAST, mealByTime(11, bounds, 4, BeforeDayStart.ASK))
        assertEquals(MealSlot.LUNCH, mealByTime(12, bounds, 4, BeforeDayStart.ASK))
        assertEquals(MealSlot.DINNER, mealByTime(17, bounds, 4, BeforeDayStart.ASK))
        assertEquals(MealSlot.SNACKS, mealByTime(22, bounds, 4, BeforeDayStart.ASK))
        // 23:30 is after the day start: Snacks, whatever "Before day start" says.
        assertEquals(MealSlot.SNACKS, mealByTime(23, bounds, 4, BeforeDayStart.DINNER))
        // The Meal setting passes them through; Ask before the day start opens the menu.
        assertNull(automaticMeal(MealMode.BY_TIME, 2, bounds, MealSlot.LUNCH, dayStartHour = 4, before = BeforeDayStart.ASK))
        assertEquals(LogSubmitPlan.OpenMenu, logSubmitPlan(true, false, "2 eggs", emptyList(), null, null, mealByTime(2, bounds, 4, BeforeDayStart.ASK)))
        assertEquals(BeforeDayStart.SNACKS, enumOf(null, BeforeDayStart.SNACKS))
    }

    @Test fun byTimeUsesTheBoundaries() {
        val bounds = MealBounds()
        assertEquals(MealSlot.BREAKFAST, mealByTime(0, bounds))
        assertEquals(MealSlot.BREAKFAST, mealByTime(11, bounds))
        assertEquals(MealSlot.LUNCH, mealByTime(12, bounds))
        assertEquals(MealSlot.LUNCH, mealByTime(16, bounds))
        assertEquals(MealSlot.DINNER, mealByTime(17, bounds))
        assertEquals(MealSlot.DINNER, mealByTime(21, bounds))
        assertEquals(MealSlot.SNACKS, mealByTime(22, bounds))
        assertEquals(MealSlot.SNACKS, mealByTime(23, bounds))
        assertEquals(MealSlot.LUNCH, automaticMeal(MealMode.BY_TIME, 13, bounds, MealSlot.SNACKS))
    }

    private fun entry(id: String, meal: MealSlot, savedAt: Long) = MealEntry(
        id, "egg", "Egg", 1.0, MeasureUnit.PIECE, 50.0, NutritionValues(70.0, 6.0, 0.5, 5.0), "1 piece", false, savedAt, mealSlot = meal,
    )

    @Test fun lastUsedIsTheMealOfTheLatestLog() {
        assertNull(lastUsedMeal(emptyList()))
        val entries = listOf(entry("a", MealSlot.BREAKFAST, 10L), entry("b", MealSlot.DINNER, 30L), entry("c", MealSlot.LUNCH, 20L))
        assertEquals(MealSlot.DINNER, lastUsedMeal(entries))
        assertEquals(MealSlot.DINNER, automaticMeal(MealMode.LAST_USED, 9, MealBounds(), lastUsedMeal(entries)))
        // Nothing logged yet: Last used asks, as Ask does.
        assertNull(automaticMeal(MealMode.LAST_USED, 9, MealBounds(), null))
    }

    @Test fun theAutomaticMealComesAfterEverythingTheUserSaid() {
        val auto = MealSlot.SNACKS
        assertEquals(MealSlot.LUNCH, logTargetMealOf(MealSlot.LUNCH, listOf(MealSlot.DINNER), MealSlot.BREAKFAST, auto))
        assertEquals(MealSlot.DINNER, logTargetMealOf(null, listOf(MealSlot.DINNER), MealSlot.BREAKFAST, auto))
        assertEquals(MealSlot.BREAKFAST, logTargetMealOf(null, emptyList(), MealSlot.BREAKFAST, auto))
        assertEquals(auto, logTargetMealOf(null, emptyList(), null, auto))
        assertEquals(LogSubmitPlan.Save(auto), logSubmitPlan(true, false, "2 eggs", emptyList(), null, null, auto))
        // Two typed meal words are still an error, whatever the setting.
        assertTrue(logSubmitPlan(true, false, "lunch 2 eggs dinner", listOf(MealSlot.LUNCH, MealSlot.DINNER), null, null, auto) is LogSubmitPlan.Error)
    }
}
