package com.iron.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A segment resolves on its own only when every identity token matches the food. A partial match is a choice. */
class PartialMatchTest {
    private val buckwheat = FoodRecord(
        id = "buckwheat", name = "Buckwheat", aliases = emptyList(), category = "Grain", origin = FoodOrigin.CUSTOM,
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(340.0, 12.0, 70.0, 3.0)),
        defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM,
    )
    private val planner = LogPlanner(FoodMatcher(FoodCatalog.fixtures + buckwheat))

    @Test fun kiseloMlekoIsOnlyAPartialMatchToHomeMilk() {
        val resolution = requireNotNull(planner.resolve("kiselo mleko"))
        assertTrue(resolution.needsChoice)
        assertTrue(resolution.ranked.any { it.food.id == "custom_home_milk" })
        assertFalse(resolution.ranked.first().fullMatch)
        assertTrue(planner.plan("kiselo mleko").entries.isEmpty())
        assertEquals(listOf("kiselo mleko"), planner.plan("kiselo mleko").unresolved)
    }

    @Test fun thePartialMatchIsOfferedWithNothingPreselected() {
        val draft = planner.draft("kiselo mleko")
        assertTrue(draft.choices.isNotEmpty())
        assertFalse(draft.preselected)
        assertEquals(listOf("kiselo mleko"), draft.unresolved)
        assertTrue(draft.logged.isEmpty())
    }

    @Test fun aFullMatchStillResolvesAndIsPreselected() {
        assertTrue(requireNotNull(planner.draft("1 ban").active).ranked.first().fullMatch)
        // A clean single candidate is an entry row, not a preselected chip.
        listOf("1 ban", "oats", "100g buckwheat").forEach { input ->
            val draft = planner.draft(input)
            assertTrue(input, draft.activeRow != null)
            assertTrue(input, draft.choices.isEmpty())
        }
        // "pb" matches both peanut butters, so it is a real choice with the clean top preselected.
        val pb = planner.draft("1 tsp pb")
        assertTrue(pb.choices.size > 1)
        assertTrue(pb.preselected)
    }

    @Test fun theOwnersTypicalInputsStillResolveWithoutAChoice() {
        listOf("1 tsp pb", "1 ban", "100g buckwheat", "oats").forEach { input ->
            val resolution = requireNotNull(planner.resolve(input)) { input }
            assertFalse(input, resolution.needsChoice)
            assertEquals(input, 1, planner.plan(input).entries.size)
        }
    }

    @Test fun anAllTokensMatchingFoodStillResolvesOnItsOwn() {
        assertEquals("custom_home_milk", requireNotNull(planner.resolve("домашно млеко")).food.id)
        assertFalse(requireNotNull(planner.resolve("домашно млеко")).needsChoice)
    }
}
