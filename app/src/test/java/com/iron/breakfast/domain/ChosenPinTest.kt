package com.iron.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** A tapped choice stays the tapped food: the chosen text is pinned to it, whatever the ranking prefers. */
class ChosenPinTest {
    private val now = 1_000L
    private val matcher = FoodMatcher(TestFoods.fixtures, nowMs = { now })
    private val eggplant = "usda_2685577_eggplant_raw"
    private val prefs = mapOf(eggplant to Preference(eggplant, explicitCount = 1, lastReinforcedEpochMs = now))
    private val egg = TestFoods.fixtures.first { it.id == "egg" }
    private val planner = LogPlanner(matcher, prefs)

    private fun pinned(chosen: ChosenDraft) = LogPlanner(matcher, prefs, pins = mapOf(requireNotNull(chosen.part) to egg))

    @Test fun theTappedEggStaysEggWhenAPreferenceRanksEggplantFirst() {
        val chosen = planner.choose("1 egg", egg)
        assertEquals(chosen.part, chosen.input)
        // The pin holds the tapped Egg whatever the ranking does with the chosen text (an exact name now ranks first,
        // so the text alone also reads Egg; the pin keeps it so if two foods ever share the name).
        val after = pinned(chosen)
        assertEquals(listOf("egg"), after.plan(chosen.input).entries.map { it.food.id })
        val draft = after.draft(chosen.input)
        assertEquals(listOf("egg"), draft.logged.map { it.food.id })
        assertSame(draft.counted, draft.activeRow)
        assertEquals("egg", draft.activeRow?.food?.id)
    }

    @Test fun theChosenPartAfterEarlierFoodsIsPinnedToo() {
        val chosen = planner.choose("1 banana, egg", egg)
        val after = pinned(chosen)
        assertEquals(listOf("banana", "egg"), after.plan(chosen.input).entries.map { it.food.id })
        assertEquals(listOf("banana", "egg"), after.draft(chosen.input).logged.map { it.food.id })
    }

    @Test fun nothingIsPinnedWhenNothingIsBeingTyped() {
        assertNull(planner.choose("1 egg", egg, typing = false).part)
        assertNull(planner.choose("1 egg, ", egg).part)
    }
}
