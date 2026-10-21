package com.iron.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A quantity directly after food words belongs to that food. Two quantities in a row cut between them. A bare number
 * after food words leads the next food, so "2 egg 1 banana" is two foods.
 */
class QuantityAttachmentTest {
    private val planner = LogPlanner(FoodMatcher(FoodCatalog.fixtures))

    private fun parts(input: String) = planner.segments(input).map { it.text }

    @Test fun twoAdjacentQuantitiesCutBetweenThem() {
        assertEquals(listOf("kiselo mleko 200g", "1 kg rolled oats"), parts("kiselo mleko 200g 1 kg rolled oats"))
    }

    @Test fun aTrailingUnitQuantityBelongsToTheFoodBeforeIt() {
        assertEquals(listOf("oats 60g", "milk 200ml"), parts("oats 60g milk 200ml"))
    }

    @Test fun aLeadingQuantityOfEachFoodStaysWithItsFood() {
        assertEquals(listOf("100g buckwheat", "1 tsp pb"), parts("100g buckwheat 1 tsp pb"))
        assertEquals(listOf("2 egg", "1 banana"), parts("2 egg 1 banana"))
    }

    @Test fun aBareNumberBeforeAFoodLeadsThatFood() {
        assertEquals(listOf("Milk 2.8%", "1 banana"), parts("Milk 2.8% 1 banana"))
    }

    @Test fun ambiguousBananaOneEggTwoDocumentedReading() {
        // Chosen reading: "1" leads "egg" (a bare number before a food), so "2" stays with egg as its trailing count.
        // "1 egg 2" then matches no food, so it stays visible as a Not in your foods row instead of being guessed.
        assertEquals(listOf("banana", "1 egg 2"), parts("banana 1 egg 2"))
        val draft = planner.draft("banana 1 egg 2", typing = false)
        assertEquals(listOf("1 egg 2"), draft.unresolved)
        assertEquals(listOf("banana"), draft.logged.map { it.raw })
    }
}
