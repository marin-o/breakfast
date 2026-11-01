package com.bosa.breakfast.domain

import com.bosa.breakfast.testFood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A tapped suggestion writes no amount the user did not type; the amount can follow the name, and the food stays. */
class SuggestionNameOnlyTest {
    private val oil = testFood("oil", "Extra virgin olive oil", defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM)
    private val other = testFood("other", "Extra virgin rapeseed oil", defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM)
    private val egg = testFood("egg", "Egg", conversions = listOf(ServingConversion(MeasureUnit.PIECE, 50.0, "one piece")), defaultAmount = 1.0, defaultUnit = MeasureUnit.PIECE)
    private val bar = testFood("bar", "Protein 2 bar", conversions = listOf(ServingConversion(MeasureUnit.PIECE, 50.0, "one piece")), defaultAmount = 1.0, defaultUnit = MeasureUnit.PIECE)
    private val otherBar = testFood("bar2", "Protein bar", conversions = listOf(ServingConversion(MeasureUnit.PIECE, 50.0, "one piece")), defaultAmount = 1.0, defaultUnit = MeasureUnit.PIECE)
    private val noodles = testFood("noodles", "Cup noodles", conversions = listOf(ServingConversion(MeasureUnit.PIECE, 50.0, "one piece")), defaultAmount = 1.0, defaultUnit = MeasureUnit.PIECE)
    private val bread = testFood(
        "bread", "Bread", defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM,
        conversions = listOf(ServingConversion(MeasureUnit.GRAM, 30.0, "personal serving", label = "slice")),
    )
    private val foods = listOf(oil, other, egg, bar, otherBar, noodles, bread)
    private val matcher = FoodMatcher(foods)
    private val planner = LogPlanner(matcher)
    /** The planner with [food] pinned to the parts of [input] that hold its name, as `DraftPins` gives them. */
    private fun pinned(food: FoodRecord, input: String) = DraftPins().let { pins ->
        pins.add(input, food.name, food, input.indexOf(food.name))
        LogPlanner(matcher, pins = pins.keys(input) { LogPlanner(matcher, pins = it) })
    }

    private fun entries(after: LogPlanner, input: String) =
        after.plan(input).also { assertTrue("unresolved: ${it.unresolved}", it.unresolved.isEmpty()) }.entries

    @Test fun noTypedAmountFillsOnlyTheNameAndASpace() {
        assertEquals(ChosenDraft("Extra virgin olive oil ", "Extra virgin olive oil"), planner.choose("extra virgin", oil))
        assertEquals("2 Egg, Extra virgin olive oil ", planner.choose("2 Egg, extra", oil).input)
    }

    @Test fun aTypedAmountIsKept() {
        assertEquals("200 g Extra virgin olive oil", planner.choose("200 g extra", oil).input)
        assertEquals("1 piece Egg", planner.choose("an egg", egg).input)
    }

    @Test fun addingNothingLogsTheDefaultAndNeedsNoChoice() {
        val after = pinned(oil, "Extra virgin olive oil ")
        val only = entries(after, "Extra virgin olive oil ").single()
        assertEquals("oil", only.food.id)
        assertEquals(100.0 to MeasureUnit.GRAM, only.amount to only.unit)
        val view = after.draft("Extra virgin olive oil ")
        assertEquals(listOf("oil"), view.previews.map { it.food.id })
        assertTrue(view.choices.isEmpty())
    }

    @Test fun anAmountAfterTheNameCountsForThatFood() {
        val typed = entries(pinned(oil, "Extra virgin olive oil 200 g"), "Extra virgin olive oil 200 g").single()
        assertEquals("oil", typed.food.id)
        assertEquals(200.0 to MeasureUnit.GRAM, typed.amount to typed.unit)
        val second = entries(pinned(oil, "2 Egg, Extra virgin olive oil 200 g"), "2 Egg, Extra virgin olive oil 200 g")
        assertEquals(listOf("egg", "oil"), second.map { it.food.id })
        assertEquals(200.0, second.last().amount, 0.0)
    }

    @Test fun aNameWithANumberOrAUnitWordTakesTheAmountAfterIt() {
        val bars = entries(pinned(bar, "Protein 2 bar 200 g"), "Protein 2 bar 200 g").single()
        assertEquals("bar", bars.food.id)
        assertEquals(200.0 to MeasureUnit.GRAM, bars.amount to bars.unit)
        val cups = entries(pinned(noodles, "Cup noodles 2 pieces"), "Cup noodles 2 pieces").single()
        assertEquals("noodles", cups.food.id)
        assertEquals(2.0 to MeasureUnit.PIECE, cups.amount to cups.unit)
    }

    @Test fun aServingNameAfterTheNameCounts() {
        val slices = entries(pinned(bread, "Bread 2 slices"), "Bread 2 slices").single()
        assertEquals("bread", slices.food.id)
        assertEquals(2.0, slices.amount, 0.0)
        assertEquals(MeasureUnit.SERVING, slices.unit)
        assertEquals("slice", slices.label)
    }

    @Test fun aCommaNextStartsANewPart() {
        assertEquals(listOf("oil"), entries(pinned(oil, "Extra virgin olive oil , "), "Extra virgin olive oil , ").map { it.food.id })
        assertEquals(listOf("oil", "egg"), entries(pinned(oil, "Extra virgin olive oil , 2 Egg"), "Extra virgin olive oil , 2 Egg").map { it.food.id })
        assertEquals(listOf("oil", "egg"), entries(pinned(oil, "Extra virgin olive oil 200 g, 2 Egg"), "Extra virgin olive oil 200 g, 2 Egg").map { it.food.id })
    }

    @Test fun wordsThatAreNotAnAmountReadAsTyped() {
        val input = "Extra virgin olive oil zzq"
        assertEquals(emptyMap<String, FoodRecord>(), DraftPins().let { it.add(input, oil.name, oil); it.keys(input) { pins -> LogPlanner(matcher, pins = pins) } })
    }
}
