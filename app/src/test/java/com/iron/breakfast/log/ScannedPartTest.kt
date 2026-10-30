package com.iron.breakfast.log

import com.iron.breakfast.domain.FoodMatcher
import com.iron.breakfast.domain.LogPlanner
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.ServingConversion
import com.iron.breakfast.testFood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A part added by a scan reads back as exactly that food, however similar its name is or whatever words are in it. */
class ScannedPartTest {
    private val piece = listOf(ServingConversion(MeasureUnit.PIECE, 50.0, "one piece"))
    private val coke = testFood("coke", "Coke Zero", unit = MeasureUnit.MILLILITER, defaultAmount = 330.0, defaultUnit = MeasureUnit.MILLILITER)
    private val cokeLike = testFood("coke2", "Coke Zero Lime", unit = MeasureUnit.MILLILITER, defaultAmount = 330.0, defaultUnit = MeasureUnit.MILLILITER)
    private val oil = testFood("oil", "Olive oil")
    private val bar = testFood("bar", "Protein 2 bar", conversions = piece)
    private val barLike = testFood("bar2", "Protein bar", conversions = piece)
    private val noodles = testFood("noodles", "Cup noodles")
    private val matcher = FoodMatcher(listOf(coke, cokeLike, oil, bar, barLike, noodles))
    private val planner = LogPlanner(matcher)

    private fun resolves(input: String, part: String, food: com.iron.breakfast.domain.FoodRecord, amount: Double, unit: MeasureUnit) {
        val after = LogPlanner(matcher, pins = mapOf(part to food))
        val plan = after.plan(input)
        assertTrue("unresolved ${plan.unresolved}", plan.unresolved.isEmpty())
        val last = plan.entries.last()
        assertEquals(food.id, last.food.id)
        assertEquals(amount to unit, last.amount to last.unit)
    }

    @Test fun thePartFollowsTheDraftAfterAComma() {
        val part = planner.partText(coke, 330.0, MeasureUnit.MILLILITER)
        assertEquals("100 g oil, 330 ml Coke Zero", planner.withPartAdded("100 g oil", part))
        assertEquals("100 g oil, 330 ml Coke Zero", planner.withPartAdded("100 g oil,", part))
        assertEquals("100 g oil, 330 ml Coke Zero", planner.withPartAdded("100 g oil, ", part))
        assertEquals("330 ml Coke Zero", planner.withPartAdded("", part))
    }

    @Test fun aSimilarNameStaysTheScannedFood() {
        val part = planner.partText(coke, 330.0, MeasureUnit.MILLILITER)
        resolves("100 g oil, $part", part, coke, 330.0, MeasureUnit.MILLILITER)
    }

    @Test fun aNameWithANumberStaysOneFood() {
        val part = planner.partText(bar, 60.0, MeasureUnit.GRAM)
        assertEquals("60 g Protein 2 bar", part)
        resolves("100 g oil, $part", part, bar, 60.0, MeasureUnit.GRAM)
    }

    @Test fun aNameWithACommaStaysOneFood() {
        val milk = testFood("milk", "Milk, 2% fat")
        val part = planner.partText(milk, 200.0, MeasureUnit.GRAM)
        val after = LogPlanner(FoodMatcher(listOf(milk, oil)), pins = mapOf(part to milk))
        assertEquals(listOf("oil", "milk"), after.plan("100 g Olive oil, $part").entries.map { it.food.id })
    }

    @Test fun aNameWithSeveralCommasStaysOnePart() {
        val yogurt = testFood("yogurt", "Yogurt, strawberry, low fat")
        val part = planner.partText(yogurt, 150.0, MeasureUnit.GRAM)
        val after = LogPlanner(FoodMatcher(listOf(yogurt, oil)), pins = mapOf(part to yogurt))
        val input = "100 g Olive oil, $part"
        assertEquals(listOf("100 g Olive oil", part), after.segments(input).map { it.text })
        assertEquals(listOf("oil", "yogurt"), after.plan(input).entries.map { it.food.id })
    }

    @Test fun aNameWithAUnitWordStaysOneFood() {
        val part = planner.partText(noodles, 80.0, MeasureUnit.GRAM)
        resolves("100 g oil, $part", part, noodles, 80.0, MeasureUnit.GRAM)
    }
}
