package com.bosa.breakfast.domain

import com.bosa.breakfast.testFood
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
        assertEquals(draft, pins.refresh(draft, listOf(milkB.copy(aliases = milkB.aliases + "other"))))
        pins.forget(used)
        assertTrue(pins.current().isEmpty())
    }

    @Test fun aRenameRewritesThePartAndKeepsThePick() {
        val draft = "200 g Mleko"
        pins.add(draft, draft, milkB)
        val renamed = milkB.copy(name = "Sveze mleko")
        val next = pins.refresh(draft, listOf(renamed))
        assertEquals("200 g Sveze mleko", next)
        val only = LogPlanner(FoodMatcher(listOf(milkA, renamed)), pins = pins.keys(next) { LogPlanner(FoodMatcher(listOf(milkA, renamed)), pins = it) }).plan(next).entries.single()
        assertEquals("b", only.food.id)
        assertEquals(200.0 to MeasureUnit.GRAM, only.amount to only.unit)
    }

    @Test fun otherPinsMoveWithTheTextOfARename() {
        val draft = "Milk , 100 g Milk 3.2"
        pins.add(draft, "Milk", milk)
        pins.add(draft, "100 g Milk 3.2", milk32)
        val next = pins.refresh(draft, listOf(milk.copy(name = "Whole milk"), milk32))
        assertEquals("Whole milk , 100 g Milk 3.2", next)
        assertEquals(listOf(0, 19), pins.current().map { it.nameStart })
        assertEquals(listOf("milk", "milk32"), pins.current().map { it.food.id })
    }

    @Test fun twoPartsPinnedToOneFoodBothFollowARename() {
        val draft = "Mleko , Mleko "
        pins.add(draft, "Mleko", milkA)
        pins.add(draft, "Mleko", milkA, 8)
        assertEquals("Kefir , Kefir ", pins.refresh(draft, listOf(milkA.copy(name = "Kefir"))))
        assertEquals(listOf(0, 8), pins.current().map { it.nameStart })
    }

    /** The planner over [foods] with this draft's pins: what Add would log. */
    private fun planOver(draft: String, vararg foods: FoodRecord): List<String> {
        val over = { keys: Map<String, FoodRecord> -> LogPlanner(FoodMatcher(foods.toList()), pins = keys) }
        return over(pins.keys(draft, over)).plan(draft).entries.map { "${it.amount} ${it.food.id}" }
    }

    @Test fun aRenameToANameWithACommaKeepsThePickAndMovesTheRest() {
        val draft = "200 g Mleko, 100 g Milk"
        pins.add(draft, "200 g Mleko", milkB)
        pins.add(draft, "100 g Milk", milk, 13)
        val renamed = milkB.copy(name = "Mleko, sveze")
        val next = pins.refresh(draft, listOf(renamed, milk))
        assertEquals("200 g Mleko, sveze, 100 g Milk", next)
        assertEquals(listOf(6, 26), pins.current().map { it.nameStart })
        assertEquals(setOf("200 g Mleko, sveze", "100 g Milk"), pins.keys(next, ::planner).keys)
        assertEquals(listOf("200.0 b", "100.0 milk"), planOver(next, renamed, milk))
    }

    @Test fun aRenameFromANameWithACommaKeepsThePickAndMovesTheRest() {
        val commaMilk = milkB.copy(name = "Mleko, sveze")
        val draft = "200 g Mleko, sveze, 100 g Milk"
        pins.add(draft, "200 g Mleko, sveze", commaMilk)
        pins.add(draft, "100 g Milk", milk, 20)
        val renamed = commaMilk.copy(name = "Kefir")
        val next = pins.refresh(draft, listOf(renamed, milk))
        assertEquals("200 g Kefir, 100 g Milk", next)
        assertEquals(listOf(6, 19), pins.current().map { it.nameStart })
        assertEquals(listOf("200.0 b", "100.0 milk"), planOver(next, renamed, milk))
    }

    @Test fun aRenameBetweenNamesWithCommasIsSavedAndRestored() {
        val commaMilk = milkB.copy(name = "Mleko, sveze")
        val draft = "200 g Mleko, sveze"
        pins.add(draft, draft, commaMilk)
        val renamed = commaMilk.copy(name = "Mleko, kozje, sveze")
        val next = pins.refresh(draft, listOf(renamed))
        assertEquals("200 g Mleko, kozje, sveze", next)
        val saved = pins.keys(next, ::planner)
        assertEquals(renamed, saved[next])
        assertEquals(saved, DraftPins().apply { restore(next, saved) }.keys(next, ::planner))
    }

    @Test fun aPartNeverPinnedStaysAsTyped() {
        val draft = "Milk , Mleko"
        pins.add(draft, "Mleko", milkB, 7)
        assertEquals("Milk , Kefir", pins.refresh(draft, listOf(milk.copy(name = "Whole milk"), milkB.copy(name = "Kefir"))))
    }

    @Test fun aRenamedPinIsSavedAndRestored() {
        val draft = "2 Mleko , Mleko"
        pins.add(draft, "2 Mleko", milkA)
        pins.add(draft, "Mleko", milkB, 10)
        val renamed = milkA.copy(name = "Kefir")
        val next = pins.refresh(draft, listOf(renamed, milkB))
        val saved = pins.keys(next, ::planner)
        assertEquals("a", saved["2 Kefir"]?.id)
        val restored = DraftPins().apply { restore(next, saved) }
        assertEquals(saved, restored.keys(next, ::planner))
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
