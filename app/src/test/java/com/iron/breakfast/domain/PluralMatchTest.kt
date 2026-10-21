package com.iron.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluralMatchTest {
    private fun food(id: String, name: String, aliases: List<String> = emptyList()) = FoodRecord(
        id = id, name = name, aliases = aliases, category = "Test", origin = FoodOrigin.CUSTOM,
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(100.0, 1.0, 1.0, 1.0)),
        defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM,
    )

    private val matcher = FoodMatcher(FoodCatalog.fixtures)

    @Test fun pluralTokensMatchTheirSingularNames() {
        assertEquals("Banana", matcher.best("bananas")?.food?.name)
        assertEquals("Egg", matcher.best("eggs")?.food?.name)
        assertEquals("Cucumber raw", matcher.best("cucumbers")?.food?.name)
        assertEquals("Roma tomato raw", matcher.best("tomatoes")?.food?.name)
    }

    @Test fun iesPluralsBecomeYStems() {
        val berries = FoodMatcher(listOf(food("blueberry", "Blueberry")))
        assertEquals("blueberry", berries.best("blueberries")?.food?.id)
        assertEquals("blueberry", berries.best("blueberry")?.food?.id)
    }

    @Test fun exactFormsRankAboveStemmedOnes() {
        val beans = FoodMatcher(listOf(food("bean", "Bean"), food("beans", "Beans")))
        assertEquals("beans", beans.best("beans")?.food?.id)
        assertEquals("bean", beans.best("bean")?.food?.id)
        assertEquals("beans", beans.best("Beans")?.food?.id)
    }

    @Test fun aSingularStemShorterThanThreeLettersNeverMatches() {
        val ox = FoodMatcher(listOf(food("ox", "Ox")))
        assertTrue(ox.suggestions("oxs").isEmpty())
        assertEquals("ox", ox.best("ox")?.food?.id)
    }

    @Test fun pluralMatchingDoesNotBreakTheExistingRules() {
        assertEquals("Rolled oats", matcher.best("oats")?.food?.name)
        assertFalse(matcher.suggestions("eggs").isEmpty())
    }
}
