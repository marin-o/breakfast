package com.iron.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Amounts that cannot be logged, capitalised Cyrillic quantities, a shared alias and apostrophes. */
class ShorthandEdgeCaseTest {
    private val parser = ShorthandParser()
    private val matcher = FoodMatcher(TestFoods.fixtures)
    private val planner = LogPlanner(matcher)

    @Test fun zeroNegativeAndDivideByZeroAmountsAreNotLogged() {
        listOf("0 banana", "1/0 banana", "-1 banana", "0g rolled oats").forEach { input ->
            val plan = planner.plan(input)
            assertTrue(input, plan.entries.isEmpty())
            assertEquals(input, listOf(input), plan.unresolved)
        }
    }

    @Test fun aMinusInsideAWordIsNotASign() {
        assertEquals(2.0, parser.parse("2-3 eggs").amount!!, 0.0)
        assertEquals(3.0, parser.parse("eggs x-3").amount!!, 0.0)
    }

    @Test fun aCalculationRefusesAnAmountItCannotCount() {
        val banana = TestFoods.fixtures.first { it.id == "banana" }
        val calculator = NutritionCalculator()
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { amount ->
            assertFalse(amount.toString(), calculator.calculate(banana, amount, null).supported)
        }
        assertTrue(calculator.calculate(banana, 1.0, null).supported)
    }

    @Test fun capitalisedCyrillicQuantityWordsAreRead() {
        assertEquals(2.0, parser.parse("Два јајца").amount!!, 0.0)
        assertEquals(0.5, parser.parse("Половина банана").amount!!, 0.0)
        assertEquals(1.0, parser.parse("1 Банана").amount!!, 0.0)
    }

    @Test fun noAliasIsSharedByTwoCatalogueFoods() {
        val owners = TestFoods.fixtures
            .flatMap { food -> food.aliases.map { it.trim().lowercase() to food.id } }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.distinct().size > 1 }
        assertEquals(emptyMap<String, List<String>>(), owners)
    }

    @Test fun greenLeafLettuceKeepsTheMacedonianAlias() {
        assertEquals("usda_2346391_leaf_lettuce_raw", planner.plan("100g зелена салата").entries.single().food.id)
    }

    @Test fun aNameWithAnApostropheMatchesWholeWhenTypedWithout() {
        val top = matcher.suggestions("grandmas ajvar").first()
        assertEquals("custom_grandmas_ajvar", top.food.id)
        assertFalse(top.weakMatch)
        assertTrue(top.wholeMatch)
    }
}
