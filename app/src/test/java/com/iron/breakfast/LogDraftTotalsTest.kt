package com.iron.breakfast

import com.iron.breakfast.domain.FoodMatcher
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.LogPlanner
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.ServingConversion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Log draft sum and the entries Add saves are both [draftLogPlan]; these tests pin that for each draft shape. */
class LogDraftTotalsTest {
    private val peanutButterSmooth = FoodRecord(
        id = "pb-smooth", name = "Peanut butter smooth", aliases = listOf("pb"), category = "Nuts", origin = FoodOrigin.GENERIC,
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(620.0, 25.0, 20.0, 50.0)),
        conversions = listOf(ServingConversion(MeasureUnit.TEASPOON, 5.0, "tsp")),
        defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM,
    )
    private val peanutButterCrunchy = FoodRecord(
        id = "pb-crunchy", name = "Peanut butter crunchy", aliases = emptyList(), category = "Nuts", origin = FoodOrigin.GENERIC,
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(600.0, 24.0, 21.0, 48.0)),
        defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM,
    )
    private val banana = FoodRecord(
        id = "banana", name = "Banana", aliases = emptyList(), category = "Fruit", origin = FoodOrigin.GENERIC,
        nutrition = NutritionBasis(1.0, MeasureUnit.PIECE, NutritionValues(100.0, 1.0, 23.0, 0.3)),
        defaultAmount = 1.0, defaultUnit = MeasureUnit.PIECE,
    )
    private val egg = FoodRecord(
        id = "egg", name = "Egg", aliases = emptyList(), category = "Egg", origin = FoodOrigin.GENERIC,
        nutrition = NutritionBasis(1.0, MeasureUnit.PIECE, NutritionValues(70.0, 6.0, 0.5, 5.0)),
        defaultAmount = 1.0, defaultUnit = MeasureUnit.PIECE,
    )

    private val planner = LogPlanner(FoodMatcher(listOf(peanutButterSmooth, peanutButterCrunchy, banana, egg)))

    private fun draft(input: String) = draftLogPlan(input, planner)
    private fun kcal(input: String) = draft(input).entries.sumOf { it.calculation.nutrition.calories }

    @Test fun ambiguousActiveSegmentLogsThePreselectedChoice() {
        // "pb" matches two peanut butters. The first choice (the one with the alias) is what Add saves.
        val resolution = requireNotNull(planner.resolve("1 tsp pb"))
        assertTrue(resolution.ranked.size >= 2)
        val logged = draft("1 tsp pb").entries
        assertEquals(1, logged.size)
        assertEquals(resolution.ranked.first().food.id, logged.single().food.id)
        assertEquals("pb-smooth", logged.single().food.id)
        assertEquals(31.0, kcal("1 tsp pb"), 1e-9)
    }

    @Test fun twoQuantitiesInOneSegmentSplitIntoTwoFoodsForSaveAndTheBarAlike() {
        // "2 egg 1 banana" has no comma, but a new quantity after a food word starts a new part. Add saves both
        // foods, and the bar sums both, because both use the same plan.
        assertNull(planner.resolve("2 egg 1 banana"))
        val plan = draft("2 egg 1 banana")
        assertEquals(listOf("egg", "banana"), plan.entries.map { it.food.id })
        assertTrue(plan.unresolved.isEmpty())
        assertEquals(240.0, kcal("2 egg 1 banana"), 1e-9)
    }

    @Test fun trailingCommaAddsNoPhantomSegment() {
        assertNull(planner.resolve(""))
        val logged = draft("1 banana,").entries
        assertEquals(1, logged.size)
        assertEquals(100.0, kcal("1 banana,"), 1e-9)
    }

    @Test fun mealWordOnlySegmentIsNotAFood() {
        // "lunch" is only a meal marker: Add has nothing to log, and the bar must say so.
        val lone = draft("lunch")
        assertTrue(lone.entries.isEmpty())
        assertTrue(lone.unresolved.isEmpty())
        // Beside a real segment the meal word is removed, and only the food is summed.
        assertEquals(1, draft("1 banana, lunch").entries.size)
        assertEquals(100.0, kcal("1 banana, lunch"), 1e-9)
    }

    @Test fun amountlessMixedDraftLogsBothSegments() {
        // "pb" has no amount but "1 banana" does, so the draft is saved, not sent to library search.
        assertNull(amountlessDailySearch("pb, 1 banana"))
        val logged = draft("pb, 1 banana").entries
        assertEquals(2, logged.size)
        assertEquals(720.0, kcal("pb, 1 banana"), 1e-9)
    }

    @Test fun amountlessDraftOpensSearchAndLogsNothing() {
        // The real amountlessDailySearch decides; the draft must then log nothing.
        assertNotNull(amountlessDailySearch("pb"))
        assertTrue(draft("pb").entries.isEmpty())
        assertEquals(0.0, kcal("pb"), 1e-9)
    }

    @Test fun duplicateFoodsAreEachLogged() {
        val logged = draft("1 banana, 1 banana").entries
        assertEquals(2, logged.size)
        assertTrue(logged.all { it.food.id == "banana" })
        assertEquals(200.0, kcal("1 banana, 1 banana"), 1e-9)
    }

    @Test fun unsupportedSegmentIsUnresolvedAndNotSummed() {
        // A tablespoon has no conversion for a piece-based food, so Add leaves it unresolved.
        val plan = draft("1 banana, 2 tbsp banana")
        assertEquals(1, plan.entries.size)
        assertEquals(listOf("2 tbsp banana"), plan.unresolved)
        assertEquals(100.0, kcal("1 banana, 2 tbsp banana"), 1e-9)
    }
}
