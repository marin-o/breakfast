package com.bosa.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One decimal rule everywhere: a comma between digits is a decimal separator, in number fields and in the Log. */
class DecimalInputTest {
    private val parser = ShorthandParser()
    private val bread = FoodRecord(
        id = "bread", name = "Bread", aliases = emptyList(), category = "Bread", origin = FoodOrigin.CUSTOM,
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(250.0, 9.0, 49.0, 3.0)),
        defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM,
    )
    private val planner = LogPlanner(FoodMatcher(TestFoods.fixtures + bread))

    @Test fun aFieldReadsTheCommaAsTheDecimalSeparator() {
        assertEquals("12.5", "12,5".decimalInput())
        assertEquals("12.5", "12.5".decimalInput())
        assertEquals("1.23", "1,2.3".decimalInput())
        assertEquals("1.5", "a1 ,5g".decimalInput())
        assertEquals("15", "1٥5".decimalInput())
    }

    @Test fun aTypedNumberParsesTheSameWay() {
        assertEquals(12.5, parseDecimal("12,5")!!, 0.0)
        assertEquals(12.5, parseDecimal(" 12.5 ")!!, 0.0)
        assertNull(parseDecimal(""))
        assertNull(parseDecimal("."))
        assertNull(parseDecimal("abc"))
        assertNull(parseDecimal("12f"))
        assertNull(parseDecimal("1e3"))
    }

    @Test fun aDecimalCommaInTheLogIsOnePart() {
        assertEquals(listOf("1,5 kg bread"), planner.segments("1,5 kg bread").map { it.text })
        val entry = planner.plan("1,5 kg bread").entries.single()
        assertEquals("bread", entry.food.id)
        assertEquals(1500.0, entry.amount, 1e-9)
        assertEquals(MeasureUnit.GRAM, entry.unit)
    }

    @Test fun aCommaNextToALetterStillSeparatesParts() {
        assertEquals(listOf("100g oats", "1 banana"), parser.split("100g oats,1 banana"))
        assertEquals(listOf("2", "3 eggs"), parser.split("2, 3 eggs"))
    }

    @Test fun aCommaBetweenDigitsIsADecimalEvenBeforeACount() {
        assertEquals(listOf("2,3 eggs"), parser.split("2,3 eggs"))
        assertEquals(2.3, parser.parse("2,3 eggs").amount!!, 1e-9)
    }

    @Test fun aFormFieldWithACommaKeepsItsValue() {
        val resolved = resolveNutrition(NutritionInput(calories = "120", protein = "12,5", basisAmount = "100"))
        assertEquals(12.5, resolved.protein!!, 0.0)
        assertFalse(resolved.invalid)
    }

    @Test fun aFieldThatIsNotANumberIsInvalidNotUnknown() {
        assertTrue(resolveNutrition(NutritionInput(calories = "120", protein = "abc")).invalid)
        assertTrue(resolveNutrition(NutritionInput(calories = "120", fat = ".")).invalid)
        assertTrue(resolveNutrition(NutritionInput(calories = "1x0")).invalid)
        assertTrue(resolveNutrition(NutritionInput(calories = "120", basisAmount = ".")).invalid)
    }

    @Test fun aBlankFieldIsStillUnknown() {
        val resolved = resolveNutrition(NutritionInput(calories = "120"))
        assertNull(resolved.protein)
        assertFalse(resolved.invalid)
    }

    @Test fun pickingAChoiceAfterADigitAndACommaKeepsTheAmountsApart() {
        val banana = TestFoods.fixtures.first { it.id == "banana" }
        val picked = planner.withChoice("eggs 2,ban", banana)
        assertEquals("eggs 2, Banana ", picked)
        assertEquals(listOf("eggs 2", "Banana"), planner.segments(picked).map { it.text })
    }

    @Test fun aReplacedPartNeverJoinsTheNextAmountIntoADecimal() {
        assertEquals("Banana 2, 3 eggs", replaceDraftPart("ban,3 eggs", 0, 3, "Banana 2"))
        assertEquals("eggs 2, 7 Days croissant", replaceDraftPart("eggs 2,cro", 7, 10, "7 Days croissant"))
        assertEquals("2, 7 Days", joinDraft("2,", "7 Days"))
        assertEquals("eggs,7 Days", joinDraft("eggs,", "7 Days"))
    }

    @Test fun aliasesSplitOnCommasButNotOnADecimalComma() {
        assertEquals(listOf("mleko 2,8%", "млеко", "milk"), splitAliases("mleko 2,8%, млеко,milk"))
    }
}
