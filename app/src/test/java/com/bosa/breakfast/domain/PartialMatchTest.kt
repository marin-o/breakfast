package com.bosa.breakfast.domain

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
    private val planner = LogPlanner(FoodMatcher(TestFoods.fixtures + buckwheat))

    /** "kiselo" is not a word of any food, and "mleko" is Latin: nothing Cyrillic matches it, so no home milk. */
    @Test fun kiseloMlekoDoesNotOfferHomeMilk() {
        assertFalse(planner.resolve("kiselo mleko")?.ranked.orEmpty().any { it.food.id == "custom_home_milk" })
        assertTrue(planner.plan("kiselo mleko").entries.isEmpty())
        // While typing, a part that matches nothing shows nothing; once committed it is a Not-in-your-foods row.
        assertTrue(planner.draft("kiselo mleko").choices.isEmpty())
        val committed = planner.draft("kiselo mleko", typing = false)
        assertTrue(committed.choices.isEmpty())
        assertEquals(listOf("kiselo mleko"), committed.unresolved)
    }

    @Test fun aPartialCyrillicMatchIsOfferedWithNothingPreselected() {
        val resolution = requireNotNull(planner.resolve("кисело млеко"))
        assertTrue(resolution.needsChoice)
        assertTrue(resolution.ranked.any { it.food.id == "custom_home_milk" })
        assertFalse(resolution.ranked.first().fullMatch)
        val draft = planner.draft("кисело млеко")
        assertTrue(draft.choices.isNotEmpty())
        assertFalse(draft.preselected)
        // The choice group is the part's only rendering: no Not-in-your-foods row for the same text.
        assertTrue(draft.unresolved.isEmpty())
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
