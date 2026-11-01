package com.bosa.breakfast.log

import com.bosa.breakfast.app.AppKit
import com.bosa.breakfast.domain.DraftPins
import com.bosa.breakfast.domain.DraftRow
import com.bosa.breakfast.domain.FoodMatcher
import com.bosa.breakfast.domain.FoodOrigin
import com.bosa.breakfast.domain.FoodRecord
import com.bosa.breakfast.domain.LogPlanner
import com.bosa.breakfast.domain.Measure
import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.domain.NutritionBasis
import com.bosa.breakfast.domain.NutritionValues
import com.bosa.breakfast.domain.Preference
import com.bosa.breakfast.domain.ServingConversion
import com.bosa.breakfast.domain.logDraftViewOf
import com.bosa.breakfast.scan.ProductReviewOrigin
import com.bosa.breakfast.ui.screens.noValueLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A stored USDA food in the Log: commas in its name and servings, its units, and what a tap teaches. */
class UsdaInTheLogTest {
    private fun food(id: String, name: String, vararg servings: Pair<String, Double>, cup: Double? = null) = FoodRecord(
        id, name, emptyList(), "USDA FoodData Central", FoodOrigin.GENERIC,
        NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(200.0, 20.0, 5.0, 10.0)),
        conversions = listOf(ServingConversion(MeasureUnit.GRAM, 1.0, "per 100 g")) +
            listOfNotNull(cup?.let { ServingConversion(MeasureUnit.CUP, it, "portion", approximate = true) }) +
            servings.map { (label, grams) -> ServingConversion(MeasureUnit.GRAM, grams, "portion", approximate = true, label = label) },
        defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM,
    )

    private val chicken = food("fndds_1", "Chicken, breast, coated", "cup, cooked, diced" to 140.0, "large breast" to 185.0)
    private val egg = food("fndds_2", "Egg, whole, raw")

    private fun planner(vararg foods: FoodRecord, pins: Map<String, FoodRecord> = emptyMap(), preferences: Map<String, Preference> = emptyMap()) =
        LogPlanner(FoodMatcher(foods.toList()), preferences, pins = pins)

    // ---- A comma in a serving or a name ----

    @Test fun `a comma in a serving's label does not split its part`() {
        val pins = DraftPins()
        val text = planner(chicken).partText(chicken, 2.0, Measure(MeasureUnit.SERVING, "cup, cooked, diced"))
        assertEquals("2 cups, cooked, diced Chicken, breast, coated", text)
        val draft = "1 banana, $text"
        pins.add(draft, text, chicken, draft.indexOf(text))
        val keys = pins.keys(draft) { planner(chicken, pins = it) }
        assertEquals(setOf(text), keys.keys)
        val view = planner(chicken, pins = keys).draft(draft, typing = false)
        val row = view.rows.last() as DraftRow.Entry
        assertEquals(2, view.rows.size)
        assertEquals(chicken.id, row.resolution.food.id)
        assertEquals("cup, cooked, diced", row.resolution.label)
        assertEquals(560.0, row.resolution.calculation.nutrition.calories, 1e-9)
    }

    @Test fun `editing the amount before a comma-holding serving keeps the pin`() {
        val pins = DraftPins()
        val text = "2 cups, cooked, diced ${chicken.name}"
        pins.add(text, text, chicken, 0)
        val next = "3" + text.drop(1)
        pins.edited(text, next)
        val keys = pins.keys(next) { planner(chicken, pins = it) }
        val row = planner(chicken, pins = keys).draft(next, typing = false).rows.single() as DraftRow.Entry
        assertEquals(840.0, row.resolution.calculation.nutrition.calories, 1e-9)
    }

    @Test fun `a stored name with several commas reads as one part when typed`() {
        val parts = planner(egg).segments("Egg, whole, raw, 2 Egg, whole, raw")
        assertEquals(listOf("Egg, whole, raw", "2 Egg, whole, raw"), parts.map { it.text })
    }

    @Test fun `a part from the review reads back through its pin`() {
        val pins = DraftPins()
        val text = planner(egg).partText(egg, 100.0, MeasureUnit.GRAM)
        pins.add(text, text, egg, 0)
        val view = planner(egg, pins = pins.keys(text) { planner(egg, pins = it) }).draft(text, typing = true)
        assertEquals(egg.id, (view.rows.single() as DraftRow.Entry).resolution.food.id)
        assertEquals("", view.catalogueQuery)
    }

    // ---- Units ----

    @Test fun `a USDA food with no cup answers no value, not 240 ml`() {
        val resolved = planner(egg).resolve("1 cup Egg, whole, raw")
        val row = planner(egg).draft("1 cup egg", typing = false).rows.single() as DraftRow.Unresolved
        assertEquals("No value for cup", noValueLine(row.noValue!!))
        assertFalse(resolved?.supported == true)
        listOf("1 tbsp egg", "1 tsp egg").forEach { typed ->
            assertTrue(typed, (planner(egg).draft(typed, typing = false).rows.single() as DraftRow.Unresolved).noValue != null)
        }
    }

    @Test fun `a USDA food with a cup uses it, and another food keeps the generic cup`() {
        val rice = food("fndds_3", "Rice", cup = 160.0)
        assertEquals(320.0, (planner(rice).draft("1 cup rice", typing = false).rows.single() as DraftRow.Entry).resolution.calculation.nutrition.calories, 1e-9)
        val own = rice.copy(id = "custom-rice", conversions = emptyList())
        assertEquals(480.0, (planner(own).draft("1 cup rice", typing = false).rows.single() as DraftRow.Entry).resolution.calculation.nutrition.calories, 1e-9)
    }

    // ---- A bare count ----

    private val rawEgg = food("fndds_9", "Egg, whole, raw", "egg" to 50.0, cup = 243.0)

    @Test fun `a bare count of a stored USDA food counts its named serving, never grams`() {
        val row = planner(rawEgg).draft("2 eggs", typing = false).rows.single() as DraftRow.Entry
        assertEquals("egg", row.resolution.label)
        assertEquals(MeasureUnit.SERVING, row.resolution.unit)
        assertEquals(200.0, row.resolution.calculation.nutrition.calories, 1e-9)
        assertEquals("2 eggs Egg, whole, raw", planner(rawEgg).chosenText("2 eggs", rawEgg))
    }

    @Test fun `an article on a stored USDA food counts one serving, and with none to count stays the default`() {
        val row = planner(rawEgg).draft("an egg", typing = false).rows.single() as DraftRow.Entry
        assertEquals("egg", row.resolution.label)
        assertEquals(1.0, row.resolution.amount, 1e-9)
        assertEquals(100.0, row.resolution.calculation.nutrition.calories, 1e-9)
        val rice = food("fndds_10", "Rice", "cup, cooked" to 160.0)
        val plain = planner(rice).draft("a rice", typing = false).rows.single() as DraftRow.Entry
        assertEquals(MeasureUnit.GRAM, plain.resolution.unit)
        assertEquals(rice.defaultAmount, plain.resolution.amount, 1e-9)
    }

    @Test fun `a bare count with no serving to count does not log, and says so`() {
        val rice = food("fndds_10", "Rice", "cup, cooked" to 160.0)
        val view = planner(rice).draft("2 rice", typing = false)
        val row = view.rows.single() as DraftRow.Unresolved
        assertEquals("No value for pieces", noValueLine(row.noValue!!))
        assertEquals(emptyList<Any>(), view.logged)
    }

    @Test fun `no amount, a typed unit and another food's bare count stay as they were`() {
        assertEquals(100.0, planner(rawEgg).resolve("egg")!!.amount, 1e-9)
        assertEquals(MeasureUnit.GRAM, planner(rawEgg).resolve("100 g egg")!!.unit)
        val own = rawEgg.copy(id = "custom-egg")
        assertEquals(MeasureUnit.GRAM, planner(own).resolve("2 egg")!!.unit)
    }

    // ---- A row for older text ----

    @Test fun `a USDA row for older text does nothing when tapped`() {
        val kit = AppKit(foods = listOf(egg))
        kit.plannerFoods = listOf(egg)
        kit.log.open(null)
        kit.log.onDraft("milk r")
        val parts = UnmatchedParts(kit.owners.resolve, kit.owners.offParts, kit.owners.search, kit.owners.foodForm, kit.owners.review, kit.log, kit.owners.usdaParts)
        parts.pickForTypedPart("milk", chicken)
        assertNull(kit.owners.review.product)
        parts.pickForTypedPart("milk r", chicken)
        assertEquals(chicken, kit.owners.review.product)
    }

    // ---- What the catalogue is asked ----

    @Test fun `the catalogue is asked the food text of the part being typed, a space after it too`() {
        val planner = planner(egg)
        assertEquals("kefir", planner.draft("kefir").catalogueQuery)
        assertEquals("kefir", planner.draft("kefir ").catalogueQuery)
        assertEquals("kefir", planner.draft("200 g kefir").catalogueQuery)
        assertEquals("kefir", planner.draft("a kefir").catalogueQuery)
    }

    @Test fun `a separator, a finished draft or a meal word asks nothing`() {
        val planner = planner(egg)
        assertEquals("", planner.draft("kefir,").catalogueQuery)
        assertEquals("", planner.draft("kefir", typing = false).catalogueQuery)
        assertEquals("", planner.draft("breakfast").catalogueQuery)
    }

    // ---- Green, and what a tap teaches ----

    private val eggs = arrayOf(food("a", "Egg whites"), food("b", "Egg yolks"))

    private fun prefer(vararg ids: Pair<String, Int>) =
        ids.associate { (id, count) -> id to Preference(id, explicitCount = count, lastReinforcedEpochMs = System.currentTimeMillis()) }

    @Test fun `of two saved foods matching a word, the higher score goes green`() {
        listOf("a" to "b", "b" to "a").forEach { (high, low) ->
            val view = logDraftViewOf(planner(*eggs, preferences = prefer(high to 5, low to 1)), "egg", committed = false, ready = true)
            assertEquals(high, view.choices.first().food.id)
            assertTrue(view.firstIsAddPick)
        }
    }

    @Test fun `a tap on a chip counts as a pick`() {
        val kit = AppKit(foods = eggs.toList())
        kit.plannerFoods = eggs.toList()
        kit.log.open(null)
        kit.log.onDraft("egg")
        kit.log.chooseSuggestion(eggs[0])
        assertEquals(listOf("a"), kit.reinforced)
    }

    @Test fun `a green food logged with no tap teaches nothing`() {
        val kit = AppKit(foods = eggs.toList())
        kit.plannerFoods = eggs.toList()
        kit.log.open(com.bosa.breakfast.domain.MealSlot.LUNCH)
        kit.log.onDraft("egg")
        kit.log.submit()
        assertTrue(kit.calls.any { it.startsWith("log save") })
        assertEquals(emptyList<String>(), kit.reinforced)
    }

    @Test fun `a USDA row's review put back takes the place of the typed part, with the rest of the draft kept`() {
        val kit = AppKit(foods = listOf(egg, chicken))
        kit.plannerFoods = listOf(egg, chicken)
        kit.log.open(null)
        kit.log.onDraft("2 Egg, whole, raw, 200 g kefir")
        val part = kit.log.partBeingTyped()!!
        assertEquals("200 g kefir", part.text)
        kit.log.addScannedPart(chicken, 200.0, Measure(MeasureUnit.GRAM), replaces = part)
        assertEquals("2 Egg, whole, raw, 200 g Chicken, breast, coated", kit.log.draft)
        assertEquals(chicken.id, kit.log.pinsSnapshot().values.single().id)
    }

    @Test fun `a part changed behind the review is left alone, and the food goes after it`() {
        val kit = AppKit(foods = listOf(egg, chicken))
        kit.plannerFoods = listOf(egg, chicken)
        kit.log.open(null)
        kit.log.onDraft("kefir")
        val part = kit.log.partBeingTyped()!!
        kit.log.onDraft("milk")
        kit.log.addScannedPart(chicken, 100.0, Measure(MeasureUnit.GRAM), replaces = part)
        assertEquals("milk, 100 g Chicken, breast, coated", kit.log.draft)
    }

    // ---- A rename in the review of the part it replaces ----

    private val apple = food("fndds_3", "Apple")

    /** Draft `100 g Apple, appl`, Apple pinned to the first part, its USDA row's review open under `appl`. */
    private fun reviewingAppleUnderItsOwnPart() = AppKit(foods = listOf(apple)).apply {
        plannerFoods = listOf(apple)
        log.open(null)
        log.addScannedPart(apple, 100.0, Measure(MeasureUnit.GRAM))
        log.onDraft("100 g Apple, appl")
        val part = log.partBeingTyped()!!
        assertEquals("appl", part.text)
        review.choose(apple, origin = ProductReviewOrigin.LOG_PART, typed = log.typedOf(part), replaces = part)
    }

    @Test fun `a rename in the review still replaces the part it was opened for`() {
        val kit = reviewingAppleUnderItsOwnPart()
        kit.review.onName("Green apple")
        kit.review.onAmount("50")
        kit.review.addToLog()
        assertEquals("100 g Green apple, 50 g Green apple", kit.log.draft)
        assertEquals(setOf("100 g Green apple", "50 g Green apple"), kit.log.pinsSnapshot().keys)
        assertEquals(setOf("Green apple"), kit.log.pinsSnapshot().values.map { it.name }.toSet())
    }

    @Test fun `a rename at the amount the part started on leaves no third part`() {
        val kit = reviewingAppleUnderItsOwnPart()
        kit.review.onName("Green apple")
        kit.review.addToLog()
        assertEquals("100 g Green apple, 100 g Green apple", kit.log.draft)
    }

    @Test fun `a rename to a name with a comma still replaces the part it was opened for`() {
        val kit = reviewingAppleUnderItsOwnPart()
        kit.review.onName("Apple, green")
        kit.review.onAmount("50")
        kit.review.addToLog()
        assertEquals("100 g Apple, green, 50 g Apple, green", kit.log.draft)
    }

    @Test fun `a rename follows the part a review left for a better match`() {
        val kit = reviewingAppleUnderItsOwnPart()
        kit.review.findBetterMatch()
        kit.diary.foods = listOf(apple.copy(name = "Green apple"))
        kit.owners.session.refresh()
        kit.log.refreshPins(kit.owners.session.foods)
        kit.review.choose(egg, origin = ProductReviewOrigin.LOG_PART)
        kit.review.addToLog()
        assertEquals("100 g Green apple, 100 g Egg, whole, raw", kit.log.draft)
    }

    @Test fun `a rename inside the part being replaced still replaces it`() {
        val kit = AppKit(foods = listOf(apple))
        kit.plannerFoods = listOf(apple)
        kit.log.open(null)
        kit.log.addScannedPart(apple, 100.0, Measure(MeasureUnit.GRAM))
        val part = kit.log.partBeingTyped()!!
        assertEquals("100 g Apple", part.text)
        kit.review.choose(apple, origin = ProductReviewOrigin.LOG_PART, replaces = part)
        kit.review.onName("Green apple")
        kit.review.onAmount("50")
        kit.review.addToLog()
        assertEquals("50 g Green apple", kit.log.draft)
    }

    @Test fun `the part being typed is null after a separator`() {
        val kit = AppKit()
        kit.log.open(null)
        kit.log.onDraft("kefir,")
        assertNull(kit.log.partBeingTyped())
    }
}
