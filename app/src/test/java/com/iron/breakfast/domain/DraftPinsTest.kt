package com.iron.breakfast.domain

import com.iron.breakfast.testFood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each pin belongs to its own part: it moves with its text, goes when its name is retyped, and takes no other part. */
class DraftPinsTest {
    private val milkA = testFood("a", "Mleko", defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM)
    private val milkB = testFood("b", "Mleko", defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM)
    private val milk = testFood("milk", "Milk", defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM)
    private val milk32 = testFood("milk32", "Milk 3.2", defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM)
    private val matcher = FoodMatcher(listOf(milkA, milkB, milk, milk32))
    private val pins = DraftPins()
    private fun planner(keys: Map<String, FoodRecord>) = LogPlanner(matcher, pins = keys)
    private fun plan(draft: String) = planner(pins.keys(draft, ::planner)).plan(draft)

    @Test fun twoPartsWithOneNameKeepTheirOwnFoods() {
        var draft = "Mleko "
        pins.add(draft, "Mleko", milkA)
        val next = "Mleko , Mleko "
        pins.edited(draft, next); draft = next
        pins.add(draft, "Mleko", milkB, 8)
        assertEquals(listOf("a", "b"), plan(draft).entries.map { it.food.id })
    }

    @Test fun aPinTakesOnlyItsOwnPart() {
        var draft = "Milk "
        pins.add(draft, "Milk", milk)
        val next = "Milk , Milk 3.2"
        pins.edited(draft, next); draft = next
        assertEquals(listOf("milk", "milk32"), plan(draft).entries.map { it.food.id })
    }

    @Test fun aPartThatBecameAnotherFoodsNameReadsAsThatFood() {
        var draft = "Milk "
        pins.add(draft, "Milk", milk)
        val next = "Milk 3.2"
        pins.edited(draft, next); draft = next
        assertEquals(listOf("milk32"), plan(draft).entries.map { it.food.id })
    }

    @Test fun theLongerPinnedNameWinsWithAnAmount() {
        val draft = "Milk 3.2 200 g"
        pins.add(draft, "Milk 3.2", milk32)
        val only = plan(draft).entries.single()
        assertEquals("milk32", only.food.id)
        assertEquals(200.0 to MeasureUnit.GRAM, only.amount to only.unit)
    }

    @Test fun editingTheAmountKeepsThePinAndRetypingTheNameDropsIt() {
        var draft = "200 g Mleko"
        pins.add(draft, draft, milkB)
        fun edit(next: String) { pins.edited(draft, next); draft = next }
        edit("300 g Mleko")
        assertEquals("b", plan(draft).entries.single().food.id)
        edit("300 g Mlek")
        assertTrue(pins.current().isEmpty())
    }

    /** A save takes the pins it used; a refresh during it made new instances, and those still go. */
    @Test fun forgetFindsAPinARefreshRemade() {
        val draft = "Mleko"
        pins.add(draft, "Mleko", milkB)
        val used = pins.current()
        pins.refresh(listOf(milkB.copy(aliases = milkB.aliases + "other")))
        pins.forget(used)
        assertTrue(pins.current().isEmpty())
    }

    @Test fun earlierTextMovesAPinWithIt() {
        var draft = "Mleko"
        pins.add(draft, "Mleko", milkB)
        val next = "2 Milk, Mleko"
        pins.edited(draft, next); draft = next
        assertEquals(8, pins.current().single().nameStart)
    }

    @Test fun savedKeysComeBackOnTheSameParts() {
        val draft = "Mleko , Mleko "
        pins.add(draft, "Mleko", milkA)
        pins.add(draft, "Mleko", milkB, 8)
        val saved = pins.keys(draft, ::planner)
        val restored = DraftPins().apply { restore(draft, saved) }
        assertEquals(saved, restored.keys(draft, ::planner))
    }
}
