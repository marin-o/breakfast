package com.iron.breakfast

import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionCalculator
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.ServingConversion
import com.iron.breakfast.domain.ShorthandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogDraftTotalsTest {
    private val calculator = NutritionCalculator()
    private val parser = ShorthandParser()

    private val peanutButter = FoodRecord(
        id = "pb", name = "Peanut butter smooth", aliases = listOf("pb"), category = "Nuts", origin = FoodOrigin.GENERIC,
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(620.0, 25.0, 20.0, 50.0)),
        defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM,
    )
    private val banana = FoodRecord(
        id = "banana", name = "Banana", aliases = emptyList(), category = "Fruit", origin = FoodOrigin.GENERIC,
        nutrition = NutritionBasis(1.0, MeasureUnit.PIECE, NutritionValues(100.0, 1.0, 23.0, 0.3)),
        defaultAmount = 1.0, defaultUnit = MeasureUnit.PIECE,
    )

    private fun segment(text: String, food: FoodRecord) = resolveLogSegment(food, parser.parse(text), calculator)

    @Test fun preselectedTeaspoonMatchCountsInTheBarSum() {
        // "1 tsp pb" with peanut butter preselected: 1 tsp is 5 g, 31 kcal, and Add logs exactly that.
        val logged = logDraftPreviews(amountlessSearch = false, finished = emptyList(), active = segment("1 tsp pb", peanutButter))
        assertEquals(1, logged.size)
        assertEquals(31.0, logged.sumOf { it.calculation.nutrition.calories }, 1e-9)
    }

    @Test fun finishedSegmentsAndActiveMatchAreBothSummed() {
        val finished = listOf(segment("1 banana", banana)!!)
        val logged = logDraftPreviews(false, finished, segment("1 tsp pb", peanutButter))
        assertEquals(2, logged.size)
        assertEquals(131.0, logged.sumOf { it.calculation.nutrition.calories }, 1e-9)
    }

    @Test fun unsupportedActiveMatchIsNotSummed() {
        // A tablespoon has no conversion for a piece-based food, so save() leaves the segment unresolved.
        assertNull(segment("2 tbsp banana", banana))
        val logged = logDraftPreviews(false, listOf(segment("1 banana", banana)!!), segment("2 tbsp banana", banana))
        assertEquals(1, logged.size)
    }

    @Test fun amountlessDraftOpensSearchAndLogsNothing() {
        // "pb" alone makes Add open the Library search, so the bar must not promise kcal.
        val logged = logDraftPreviews(amountlessSearch = true, finished = emptyList(), active = segment("pb", peanutButter))
        assertTrue(logged.isEmpty())
    }
}
