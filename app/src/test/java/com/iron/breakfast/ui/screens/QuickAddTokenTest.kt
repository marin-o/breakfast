package com.iron.breakfast.ui.screens

import com.iron.breakfast.ui.components.ReceiptTokenKind
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickAddTokenTest {
    private fun underlined(input: String, known: Set<String>): List<String> =
        tokenRangesForInput(input) { it.trim() in known }
            .filter { it.kind == ReceiptTokenKind.Food }
            .map { input.substring(it.range) }

    @Test
    fun onlyRecognisedFoodsAreUnderlined() {
        assertEquals(listOf("pb"), underlined("lunch, 100g buckwheat, 1 tsp pb, ", setOf("pb")))
    }

    @Test
    fun segmentStillBeingTypedIsNeverUnderlined() {
        assertEquals(listOf("pb"), underlined("1 tsp pb, 1 ban", setOf("pb", "ban")))
    }

    @Test
    fun trailingCommaCompletesTheLastSegment() {
        assertEquals(listOf("pb", "banana"), underlined("1 tsp pb, 1 banana,", setOf("pb", "banana")))
    }

    @Test
    fun mealMarkerAndQuantitiesStillMarked() {
        val kinds = tokenRangesForInput("lunch, 100g buckwheat") { false }.map { it.kind }
        assertEquals(listOf(ReceiptTokenKind.Meal, ReceiptTokenKind.Quantity), kinds)
    }
}
