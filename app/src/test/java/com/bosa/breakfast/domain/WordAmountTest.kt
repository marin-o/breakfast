package com.bosa.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordAmountTest {
    private val parser = ShorthandParser()
    private val planner = LogPlanner(FoodMatcher(TestFoods.fixtures))

    private fun amountOf(input: String) = parser.parse(input).amount

    @Test fun englishNumberWordsAreAmounts() {
        assertEquals(1.0, amountOf("one banana"))
        assertEquals(2.0, amountOf("two eggs"))
        assertEquals(3.0, amountOf("three eggs"))
        assertEquals(10.0, amountOf("ten eggs"))
        assertEquals(0.5, amountOf("half a banana"))
        assertEquals(0.5, amountOf("a half banana"))
        assertEquals(0.25, amountOf("quarter banana"))
        assertEquals(2.0, amountOf("a couple of eggs"))
    }

    @Test fun wordAmountTakesItsUnitAndLeavesTheFood() {
        val parsed = parser.parse("two tbsp peanut butter")
        assertEquals(2.0, parsed.amount)
        assertEquals(MeasureUnit.TABLESPOON, parsed.unit)
        assertEquals("peanut butter", parsed.foodPhrase)
        assertEquals("peanut butter", parser.parse("peanut butter half a tbsp").foodPhrase)
        assertEquals(0.5, parser.parse("peanut butter half a tbsp").amount)
        assertEquals(MeasureUnit.TABLESPOON, parser.parse("peanut butter half a tbsp").unit)
        assertEquals("eggs", parser.parse("a couple of eggs").foodPhrase)
    }

    @Test fun macedonianNumberWordsAreAmounts() {
        assertEquals(1.0, amountOf("еден банана"))
        assertEquals(1.0, amountOf("една банана"))
        assertEquals(2.0, amountOf("два банана"))
        assertEquals(2.0, amountOf("две банана"))
        assertEquals(3.0, amountOf("три јајца"))
        assertEquals(4.0, amountOf("четири јајца"))
        assertEquals(5.0, amountOf("пет јајца"))
        assertEquals(0.5, amountOf("половина банана"))
    }

    @Test fun multiplierOnEitherSideOfTheFood() {
        assertEquals(2.0, amountOf("2x egg"))
        assertEquals(2.0, amountOf("egg x2"))
        assertEquals("egg", parser.parse("egg x2").foodPhrase)
        assertEquals("egg", parser.parse("2x egg").foodPhrase)
    }

    @Test fun resolvesWordAmountsToTheirFoods() {
        val two = requireNotNull(planner.resolve("two eggs"))
        assertEquals("egg", two.food.id)
        assertEquals(2.0, two.amount, 0.0)
        assertEquals(2.0, requireNotNull(planner.resolve("2x egg")).amount, 0.0)
        assertEquals(2.0, requireNotNull(planner.resolve("a couple of eggs")).amount, 0.0)
    }

    @Test fun aArticleIsNeverAFood() {
        assertEquals("banana", planner.resolve("a banana")?.food?.id)
        assertEquals(listOf("banana"), planner.plan("a banana").entries.map { it.food.id })
    }

    @Test fun wordAmountsSplitWithoutCommas() {
        assertEquals(listOf("1 banana", "two eggs"), parser.split("1 banana two eggs"))
        assertEquals(listOf("pb", "a banana"), planner.segments("pb a banana").map { it.text })
        assertEquals(listOf("peanut butter half a tbsp"), planner.segments("peanut butter half a tbsp").map { it.text })
    }

    @Test fun anArticleBananaLogsOneBanana() {
        assertEquals(listOf("banana"), planner.plan("a banana").entries.map { it.food.id })
    }
}
