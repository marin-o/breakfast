package com.bosa.breakfast.domain

import com.bosa.breakfast.testFood
import com.bosa.breakfast.ui.screens.previewQuantityLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A personal food's named serving typed in the Log bar (docs/measurements-spec.md, section 2), each through the real
 * planner. "Candybar" is the food the bug was seen on: the words "bar" and "can" are both letters of its one name word.
 */
class NamedServingLogBarTest {
    private fun food(
        id: String, name: String, kcal: Double, vararg servings: Pair<String, Double>,
    ) = testFood(
        id, name, category = "Personal food", values = NutritionValues(kcal, 1.0, 1.0, 1.0),
        conversions = listOf(ServingConversion(MeasureUnit.GRAM, 100.0, "custom basis")) +
            servings.map { (label, grams) -> ServingConversion(MeasureUnit.GRAM, grams, "personal serving", label = label) },
        defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM, personalOverride = true,
    )

    private val candybar = food("custom-candybar", "Candybar", 534.0, "bar" to 20.0)

    private fun planner(vararg foods: FoodRecord) = LogPlanner(FoodMatcher(TestFoods.fixtures + foods))

    private fun SegmentResolution.quantity() = previewQuantityLine(amount, unit, calculation.approximate, label)

    private fun assertLogsBars(planner: LogPlanner, text: String, bars: Double, quantity: String, kcal: Double) {
        val part = planner.plan(text).entries.singleOrNull() ?: throw AssertionError("$text logs ${planner.plan(text)}")
        assertEquals(text, "custom-candybar", part.food.id)
        assertEquals(text, bars, part.amount, 1e-9)
        assertEquals(text, MeasureUnit.SERVING to "bar", part.unit to part.label)
        assertEquals(text, bars * 20.0, part.calculation.resolvedBaseAmount, 1e-9)
        assertEquals(text, kcal, part.calculation.nutrition.calories, 1e-9)
        assertEquals(text, quantity, part.quantity())
    }

    @Test fun aServingNameThatIsAlsoLettersOfTheFoodsNameIsTheServing() {
        val planner = planner(candybar)
        assertLogsBars(planner, "1 bar Candybar", 1.0, "1 bar", 106.8)
        assertLogsBars(planner, "Candybar 1 bar", 1.0, "1 bar", 106.8)
    }

    @Test fun plurals() {
        val planner = planner(candybar)
        assertLogsBars(planner, "2 bars Candybar", 2.0, "2 bars", 213.6)
        assertLogsBars(planner, "Candybar 2 bars", 2.0, "2 bars", 213.6)
    }

    @Test fun anyCase() {
        val planner = planner(candybar)
        assertLogsBars(planner, "2 BARS candybar", 2.0, "2 bars", 213.6)
        assertLogsBars(planner, "1 Bar CANDYBAR", 1.0, "1 bar", 106.8)
    }

    @Test fun aBareCountOnAFoodWithOneNamedServingCountsIt() {
        val planner = planner(candybar)
        assertLogsBars(planner, "1 Candybar", 1.0, "1 bar", 106.8)
        assertLogsBars(planner, "2 Candybars", 2.0, "2 bars", 213.6)
        assertLogsBars(planner, "a Candybar", 1.0, "1 bar", 106.8)
    }

    @Test fun theNameAloneKeepsTheDefaultAmount() {
        val part = planner(candybar).plan("Candybar").entries.single()
        assertEquals(100.0 to MeasureUnit.GRAM, part.amount to part.unit)
        assertNull(part.label)
        assertEquals(534.0, part.calculation.nutrition.calories, 1e-9)
    }

    @Test fun aWeightStaysAWeight() {
        val part = planner(candybar).plan("100 g Candybar").entries.single()
        assertEquals(100.0 to MeasureUnit.GRAM, part.amount to part.unit)
        assertEquals(534.0, part.calculation.nutrition.calories, 1e-9)
        assertEquals("100 g", part.quantity())
    }

    /** "can" is letters of "Candybar" too, and no food here has a can: it is still no serving and logs nothing. */
    @Test fun aServingTheFoodLacksIsAnUnsupportedUnit() {
        for (text in listOf("1 can Candybar", "2 cans Candybar", "Candybar 1 can", "Candybar 2 cans")) {
            for (planner in listOf(planner(candybar), LogPlanner(FoodMatcher(listOf(candybar))))) {
                val plan = planner.plan(text)
                assertTrue(text, plan.entries.isEmpty())
                assertEquals(text, listOf(text), plan.unresolved)
                val part = planner.resolve(text)!!
                assertEquals(text, "custom-candybar", part.food.id)
                assertTrue(text, part.noValue)
                assertFalse(text, part.needsChoice)
                assertEquals(text, MeasureUnit.SERVING, part.unit)
                assertTrue(text, part.label!!.startsWith("can"))
            }
        }
    }

    /** A word some food names whole is that food, even as letters of another's name: two foods stay two parts. */
    @Test fun aWordThatIsAFoodIsNotLettersOfAnother() {
        val pineapple = food("custom-pineapple", "Pineapple", 50.0)
        val apple = food("custom-apple", "Apple", 52.0)
        val planner = LogPlanner(FoodMatcher(listOf(pineapple, apple)))
        assertEquals(listOf("pineapple", "2 apple"), planner.segments("pineapple 2 apple").map { it.text })
        assertEquals(listOf("custom-pineapple", "custom-apple"), planner.plan("pineapple 2 apple").entries.map { it.food.id })
        assertFalse(planner.resolve("1 apple pineapple")!!.noValue)
    }

    /** With a can of another food in the catalogue the reading is the same: the words never change the food. */
    @Test fun anotherFoodsServingChangesNothing() {
        val coke = food("custom-coke", "Coke", 42.0, "can" to 330.0)
        val part = planner(candybar, coke).resolve("1 can Candybar")!!
        assertEquals("custom-candybar", part.food.id)
        assertTrue(part.noValue)
    }

    @Test fun aFoodWithSeveralNamedServingsKeepsItsDefaultUnitOnABareCount() {
        val two = food("custom-candybar", "Candybar", 534.0, "bar" to 20.0, "box" to 200.0)
        val planner = planner(two)
        val bare = planner.plan("2 Candybars").entries.single()
        assertEquals(2.0 to MeasureUnit.GRAM, bare.amount to bare.unit)
        assertEquals(10.68, bare.calculation.nutrition.calories, 1e-9)
        // Named, each still counts.
        assertEquals(106.8, planner.plan("1 bar Candybar").entries.single().calculation.nutrition.calories, 1e-9)
        assertEquals(1068.0, planner.plan("1 box Candybar").entries.single().calculation.nutrition.calories, 1e-9)
    }

    /** A spoon or a cup measures an amount and counts nothing: only a food's one countable serving is a bare count's. */
    @Test fun aMeasureIsNotWhatABareCountCounts() {
        val spoon = food("custom-honey", "Honey", 300.0).let {
            it.copy(conversions = it.conversions + ServingConversion(MeasureUnit.TABLESPOON, 21.0, "personal serving"))
        }
        val part = planner(spoon).plan("2 Honey").entries.single { it.food.id == "custom-honey" }
        assertEquals(2.0 to MeasureUnit.GRAM, part.amount to part.unit)
    }

    @Test fun aFoodNamedByTheServingWordStillMatches() {
        val bar = food("custom-bar", "Bar", 400.0)
        val planner = planner(candybar, bar)
        assertEquals("custom-bar", planner.plan("Bar").entries.single().food.id)
        planner.plan("1 Bar").entries.single().let {
            assertEquals("custom-bar", it.food.id)
            assertEquals(1.0 to MeasureUnit.GRAM, it.amount to it.unit)
        }
        assertEquals("custom-bar", planner.plan("100 g bar").entries.single().food.id)
        assertLogsBars(planner, "1 bar Candybar", 1.0, "1 bar", 106.8)
    }

    /** The serving's word may be a whole word of the name; it is still the serving, however many servings the food has. */
    @Test fun aServingWordThatIsAWordOfTheNameIsTheServing() {
        val protein = food("custom-protein", "Protein bar", 400.0, "bar" to 50.0, "box" to 500.0)
        val planner = planner(protein)
        for (text in listOf("1 bar Protein bar", "Protein bar 1 bar", "1 bar protein")) {
            val part = planner.plan(text).entries.single()
            assertEquals(text, MeasureUnit.SERVING to "bar", part.unit to part.label)
            assertEquals(text, 200.0, part.calculation.nutrition.calories, 1e-9)
        }
        // The exact name carries no amount.
        assertEquals(100.0 to MeasureUnit.GRAM, planner.plan("Protein bar").entries.single().let { it.amount to it.unit })
    }

    @Test fun aCyrillicServingNameWorksWithACyrillicAmountWordAndNeverMatchesLatin() {
        val chocolate = food("custom-choc", "Чоколадо", 500.0, "табла" to 100.0)
        val planner = planner(chocolate)
        for (text in listOf("една табла Чоколадо", "1 табла Чоколадо", "Чоколадо 1 табла", "1 ТАБЛА чоколадо")) {
            val part = planner.plan(text).entries.single()
            assertEquals(text, "custom-choc", part.food.id)
            assertEquals(text, MeasureUnit.SERVING to "табла", part.unit to part.label)
            assertEquals(text, 500.0, part.calculation.nutrition.calories, 1e-9)
            assertEquals(text, "1 табла", part.quantity())
        }
        assertTrue(planner.plan("1 tabla Чоколадо").entries.isEmpty())
        assertTrue("a word mixing scripts names nothing", planner.plan("1 тaбла Чоколадо").entries.isEmpty())
        // A Latin name on a Cyrillic food, and letters of its name in the other script, are no serving of it.
        val letters = food("custom-chocbar", "Чоколадобар", 500.0, "бар" to 100.0)
        val second = planner(letters)
        assertEquals(MeasureUnit.SERVING to "бар", second.plan("1 бар Чоколадобар").entries.single().let { it.unit to it.label })
        assertTrue(second.plan("1 bar Чоколадобар").entries.isEmpty())
    }

    /** The row, a tapped choice and what Add saves agree, and the saved text reads back as the same serving. */
    @Test fun theRowAndAPickAgreeWithWhatIsLogged() {
        val planner = planner(candybar)
        val row = planner.draft("1 bar Candybar", typing = false).rows.single() as DraftRow.Entry
        assertEquals("1 bar", row.resolution.quantity())
        assertEquals(1.0 to Measure(MeasureUnit.SERVING, "bar"), planner.quantityOf("1 bar Candybar", candybar))
        assertEquals("1 bar Candybar", planner.chosenText("1 Candybar", candybar))
        assertEquals("2 bars Candybar", planner.chosenText("2 Candybars", candybar))
        assertEquals(106.8, planner.calculationFor("1 bar Candybar", candybar).nutrition.calories, 1e-9)
    }

    /** Letters of a typed word are not a word of their own, for any food: but each word still matches where it is whole. */
    @Test fun lettersOfAnotherTypedWordNameNothing() {
        val matcher = FoodMatcher(listOf(candybar))
        assertFalse(matcher.best("bar candybar")!!.fullMatch)
        assertFalse(matcher.covers("can candybar"))
        assertTrue(matcher.best("candybar")!!.fullMatch)
        assertTrue("alone, a part of a word is as it was", matcher.best("bar")!!.fullMatch)
        assertTrue(FoodMatcher(listOf(food("x", "Candy bar", 1.0))).best("bar candy")!!.fullMatch)
    }
}
