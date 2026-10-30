package com.iron.breakfast.domain

import com.iron.breakfast.testFood
import com.iron.breakfast.ui.screens.noValueLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The amount and unit never choose the food: they are flagged on the food the words name. */
class AmountNeverPicksTest {
    private val can = ServingConversion(MeasureUnit.MILLILITER, 330.0, "personal serving", label = "can")
    // Counted by the piece: a piece has no tablespoon.
    private val kefir = testFood("kefir", "Kefir", basisAmount = 1.0, unit = MeasureUnit.PIECE, defaultAmount = 1.0, defaultUnit = MeasureUnit.PIECE)
    private val coke = testFood("coke", "Coke", unit = MeasureUnit.MILLILITER, defaultAmount = 100.0, defaultUnit = MeasureUnit.MILLILITER)
    private val cokeZero = testFood("zero", "Coke Zero", unit = MeasureUnit.MILLILITER, defaultAmount = 100.0, defaultUnit = MeasureUnit.MILLILITER, conversions = listOf(can))
    private val banana = testFood("banana", "Banana", defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM)
    private val planner = LogPlanner(FoodMatcher(listOf(kefir, coke, cokeZero, banana)))

    @Test fun aUnitTheFoodLacksFlagsThatFoodAndDoesNotLog() {
        val draft = planner.draft("2 tbsp Kefir", typing = false)
        val row = draft.rows.single() as DraftRow.Unresolved
        assertEquals("kefir", row.noValue?.food?.id)
        assertEquals("No value for tbsp", noValueLine(row.noValue!!))
        assertTrue(planner.plan("2 tbsp Kefir").entries.isEmpty())
        assertFalse(planner.needsAttention("2 tbsp Kefir"))
        assertTrue(draft.hasNoValue)
        assertFalse(draft.needsMatch)
        assertFalse(draft.addLogs)
    }

    @Test fun thePartBeingTypedIsFlaggedToo() {
        val row = planner.draft("2 tbsp Kefir", typing = true).rows.single() as DraftRow.Unresolved
        assertEquals("kefir", row.noValue?.food?.id)
    }

    @Test fun anAmountOfZeroIsNotAMissingValue() {
        val draft = planner.draft("0 Banana", typing = false)
        assertNull((draft.rows.single() as DraftRow.Unresolved).noValue)
    }

    @Test fun aPartWithNoFoodHasNoFlag() {
        val draft = planner.draft("xyzzy", typing = false)
        assertNull((draft.rows.single() as DraftRow.Unresolved).noValue)
        assertTrue(draft.needsMatch)
    }

    @Test fun aServingNameTheTopFoodLacksFlagsItAndDropsNoWord() {
        val two = planner.resolve("2 cans coke")!!
        assertEquals("coke", two.food.id)
        assertFalse(two.supported)
        assertTrue(two.noValue)
        assertEquals("No value for cans", noValueLine(two))
        assertTrue(planner.plan("2 cans coke").entries.isEmpty())
        val row = planner.draft("2 cans coke", typing = false).rows.single() as DraftRow.Unresolved
        assertEquals("coke", row.noValue?.food?.id)
    }

    @Test fun theFoodThatHasTheServingStillReadsIt() {
        val zero = planner.resolve("2 cans coke zero")!!
        assertEquals("zero", zero.food.id)
        assertEquals(MeasureUnit.SERVING, zero.unit)
        assertEquals("can", zero.label?.removeSuffix("s"))
        assertTrue(zero.supported)
    }

    @Test fun aFlaggedPartOffersTheFoodsThatHaveTheUnitNotGreen() {
        val draft = planner.draft("2 cans coke", typing = true)
        assertEquals(listOf("zero"), draft.choices.map { it.food.id })
        assertNull(draft.addPick)
        assertEquals("2 cans Coke Zero", planner.choose("2 cans coke", cokeZero).input)
    }

    @Test fun aWordNoFoodHasAsAServingIsAChoiceAsBefore() {
        val big = planner.resolve("2 big bananas")!!
        assertEquals("banana", big.food.id)
        assertTrue(big.needsChoice)
        assertFalse(big.noValue)
        assertEquals(listOf("2 big bananas"), planner.plan("2 big bananas").unresolved)
        assertNull((planner.draft("2 big bananas", typing = false).rows.single() as DraftRow.Unresolved).noValue)
    }

    @Test fun portionsOfAFoodThatHasNoneAreFlagged() {
        val two = planner.resolve("2 portions Banana")!!
        assertEquals("banana", two.food.id)
        assertTrue(two.noValue)
        assertEquals("No value for portions", noValueLine(two))
    }

    @Test fun anAmountOfZeroLeavesNothingToAdd() {
        assertTrue(planner.draft("0 Banana", typing = false).hasNoValue)
    }

    @Test fun countOffStillShowsWhichChipAddWillLog() {
        val off = LogPlanner(FoodMatcher(listOf(banana, testFood("bread", "Banana bread", defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM))), countTyping = false)
        val draft = off.draft("ban", typing = true)
        assertEquals("the finished plan logs this", off.plan("ban").entries.single().food.id, draft.addPick?.food?.id)
        assertEquals(draft.addPick?.food?.id, draft.choices.first().food.id)
        assertNull("the sum still follows the setting", draft.counted)
        assertTrue(draft.logged.isEmpty())
    }

    @Test fun aPartBeingTypedWithSuggestionsDoesNotAskForMatches() {
        val draft = planner.draft("Banana x, ban", typing = true)
        assertFalse("ban" in draft.needsMatchParts)
        assertTrue(planner.draft("xyzzy", typing = true).needsMatchParts == listOf("xyzzy"))
    }

    @Test fun somethingToLogBesideAnUnmatchedFood() {
        val draft = planner.draft("Banana, polenta", typing = false)
        assertTrue(draft.addLogs)
        assertTrue(draft.needsMatch)
    }
}
