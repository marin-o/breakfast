package com.bosa.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** An exact name or alias ranks first; a learned preference only orders matches of equal standing. */
class ExactNameRanksFirstTest {
    private val now = 1_000L
    private val matcher = FoodMatcher(TestFoods.fixtures, nowMs = { now })
    private val eggplant = "usda_2685577_eggplant_raw"

    private fun prefer(vararg ids: Pair<String, Int>) =
        ids.associate { (id, count) -> id to Preference(id, explicitCount = count, lastReinforcedEpochMs = now) }

    @Test fun aPreferenceForEggplantNeverLiftsItAboveEgg() {
        val planner = LogPlanner(matcher, prefer(eggplant to 5))
        listOf("egg", "Egg", "1 egg", "1 piece Egg", "2 eggs").forEach { input ->
            assertEquals(input, "egg", planner.resolve(input)?.food?.id)
        }
        // Eggplant itself is still found, and still first for its own name.
        assertEquals(eggplant, planner.resolve("eggplant")?.food?.id)
    }

    private fun milk(id: String, name: String) = FoodRecord(
        id = id, name = name, aliases = emptyList(), category = "milk dairy", origin = FoodOrigin.GENERIC,
        nutrition = NutritionBasis(100.0, MeasureUnit.MILLILITER, NutritionValues(60.0, 3.0, 5.0, 3.0)),
        defaultAmount = 200.0, defaultUnit = MeasureUnit.MILLILITER,
    )

    @Test fun aPreferenceStillPicksAmongMatchesOfEqualStanding() {
        // Neither milk is named exactly "milk", so the preferred one is chosen.
        val milks = FoodMatcher(listOf(milk("goat", "Goat milk"), milk("sheep", "Sheep milk"), milk("cow", "Cow milk")), nowMs = { now })
        assertEquals("cow", LogPlanner(milks).resolve("milk")?.food?.id)
        assertEquals("sheep", LogPlanner(milks, prefer("sheep" to 3)).resolve("milk")?.food?.id)
        assertEquals("goat", LogPlanner(milks, prefer("sheep" to 1, "goat" to 4)).resolve("200 ml milk")?.food?.id)
    }

    @Test fun aPreferenceDoesNotLiftAMilkAboveTheOneAliasedMilk() {
        // Milk 2.8% has the alias "milk"; a preference for Milk 3.2% orders it second, as before this rule.
        assertEquals("milk_28", LogPlanner(matcher, prefer("milk_32" to 5)).resolve("milk")?.food?.id)
        assertEquals("milk_32", LogPlanner(matcher, prefer("milk_32" to 5)).resolve("milk")?.ranked?.get(1)?.food?.id)
    }
}
