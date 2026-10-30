package com.iron.breakfast.domain

import com.iron.breakfast.testEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [nutritionTotalOf], the one total of the day, a meal and a save, as Daily, Meal and the engine each folded it before.
 * The "?" marks come from [macroTotalsOf], not from this total.
 */
class NutritionTotalTest {
    private fun entry(id: String, values: NutritionValues) = testEntry(id, "f-$id", "Food", values)

    @Test fun nothingLoggedIsZero() {
        assertEquals(NutritionValues.ZERO, nutritionTotalOf(emptyList()))
    }

    @Test fun theSnapshotsAreSummedAndAnUnknownMacroAddsNothing() {
        val entries = listOf(
            entry("a", NutritionValues(70.0, 6.0, 0.5, 5.0)),
            entry("b", NutritionValues(105.0, null, 27.0, null)),
        )
        // An unknown macro adds nothing; the total starts from zero, so it is never null.
        assertEquals(NutritionValues(175.0, 6.0, 27.5, 5.0), nutritionTotalOf(entries))
    }
}
