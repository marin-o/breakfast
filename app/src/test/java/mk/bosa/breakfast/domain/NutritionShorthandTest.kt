package mk.bosa.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionShorthandTest {
    @Test
    fun recognisesLabelledNumbersAndTheirBoldRanges() {
        val text = "per 100g: 120 kcal, 2g protein, 8g fat"
        val parsed = parseNutritionShorthand(text)
        assertEquals(120.0, parsed.calories!!.value, 0.0)
        assertEquals("120", text.substring(parsed.calories!!.range))
        assertEquals("2g", text.substring(parsed.protein!!.range))
        assertEquals("8g", text.substring(parsed.fat!!.range))
        assertEquals("100g", text.substring(parsed.basisAmount!!.range))
        assertNull(parsed.carbohydrates)
        assertEquals(MeasureUnit.GRAM, parsed.basisUnit)
    }

    @Test
    fun keepsTheOriginalLabelledNumberRule() {
        val parsed = parseNutritionShorthand("450 kcal, 12g protein, 20 g carbs, 1,5 g fat")
        assertEquals(450.0, parsed.calories!!.value, 0.0)
        assertEquals(12.0, parsed.protein!!.value, 0.0)
        assertEquals(20.0, parsed.carbohydrates!!.value, 0.0)
        assertEquals(1.5, parsed.fat!!.value, 0.0)
        assertNull(parsed.basisAmount)
    }

    @Test
    fun readsSingleLetterLabels() {
        val glued = parseNutritionShorthand("450 kcal 12p 20c 1,5f")
        assertEquals(12.0, glued.protein!!.value, 0.0)
        assertEquals(20.0, glued.carbohydrates!!.value, 0.0)
        assertEquals(1.5, glued.fat!!.value, 0.0)
        val spaced = parseNutritionShorthand("per 100g: 450 kcal, 12 P, 20g C, 8 g F")
        assertEquals(12.0, spaced.protein!!.value, 0.0)
        assertEquals(20.0, spaced.carbohydrates!!.value, 0.0)
        assertEquals(8.0, spaced.fat!!.value, 0.0)
        assertEquals(100.0, spaced.basisAmount!!.value, 0.0)
        val text = "8f 3p"
        assertEquals("8", text.substring(parseNutritionShorthand(text).fat!!.range))
    }

    @Test
    fun readsPluralAndLongLabels() {
        val parsed = parseNutritionShorthand("120 kcals, 2g proteins, 20g carbohydrates, 8g fats")
        assertEquals(120.0, parsed.calories!!.value, 0.0)
        assertEquals(2.0, parsed.protein!!.value, 0.0)
        assertEquals(20.0, parsed.carbohydrates!!.value, 0.0)
        assertEquals(8.0, parsed.fat!!.value, 0.0)
    }

    @Test
    fun aLabelIsAWholeWord() {
        val text = "2 cups, 3 fiber, 4 pieces, 5 calories, 6 salty, 7 sugary"
        val parsed = parseNutritionShorthand(text)
        assertNull(parsed.carbohydrates)
        assertNull(parsed.fat)
        assertNull(parsed.protein)
        assertNull(parsed.calories)
        assertNull(parsed.salt)
        assertNull(parsed.sugars)
        // "3 fiber" is fibre, never fat.
        assertEquals(3.0, parsed.fibre!!.value, 0.0)
        assertEquals(listOf("3"), parsed.recognisedRanges.map { text.substring(it) })
    }

    private fun saturatedFatOf(text: String) = parseNutritionShorthand(text).saturatedFat?.value

    @Test
    fun readsSaturatedFatInEveryWordingWithoutFillingFat() {
        listOf(
            "2 sat fat", "2g sat fat", "2 SAT FAT", "2 satfat", "2satfat", "2 Satfat", "2 sat f", "2satf", "2 SATF", "2 sat F",
            "2g saturated", "2 saturated fat", "2 Saturates", "2g saturates", "2,0 sat fat",
        ).forEach { text ->
            assertEquals(text, 2.0, saturatedFatOf(text)!!, 0.0)
            assertNull(text, parseNutritionShorthand(text).fat)
        }
    }

    @Test
    fun readsSatFatsAsSaturatedFat() {
        listOf("2 sat fats", "2g sat fats", "2 Sat Fats", "2 sat  fats").forEach { text ->
            assertEquals(text, 2.0, saturatedFatOf(text)!!, 0.0)
            assertNull(text, parseNutritionShorthand(text).fat)
        }
        val text = "8f 2 sat fats 12p 20c"
        val parsed = parseNutritionShorthand(text)
        assertEquals(8.0, parsed.fat!!.value, 0.0)
        assertEquals(2.0, parsed.saturatedFat!!.value, 0.0)
        assertEquals(12.0, parsed.protein!!.value, 0.0)
        assertEquals(20.0, parsed.carbohydrates!!.value, 0.0)
        assertNull(saturatedFatOf("2 sat fatsy"))
    }

    @Test
    fun saturatedFatIsAWholeWord() {
        listOf("2 satfats", "2 sat fatty", "2 satfx", "2 saturateds", "2 sa", "2 s").forEach { assertNull(it, saturatedFatOf(it)) }
    }

    @Test
    fun fatAndSaturatedFatTogether() {
        listOf("8f 2 sat f", "8f 2satf", "2 sat fat 8f", "8 g fat, 2 g satfat", "2 saturates 8 fat").forEach { text ->
            val parsed = parseNutritionShorthand(text)
            assertEquals(text, 8.0, parsed.fat!!.value, 0.0)
            assertEquals(text, 2.0, parsed.saturatedFat!!.value, 0.0)
        }
    }

    @Test
    fun readsSugarsFibreAndSalt() {
        val text = "450 kcal 12p 20c 8f 2 sat fat 5 sugars 3 fibre 1 salt"
        val parsed = parseNutritionShorthand(text)
        assertEquals(450.0, parsed.calories!!.value, 0.0)
        assertEquals(12.0, parsed.protein!!.value, 0.0)
        assertEquals(20.0, parsed.carbohydrates!!.value, 0.0)
        assertEquals(8.0, parsed.fat!!.value, 0.0)
        assertEquals(2.0, parsed.saturatedFat!!.value, 0.0)
        assertEquals(5.0, parsed.sugars!!.value, 0.0)
        assertEquals(3.0, parsed.fibre!!.value, 0.0)
        assertEquals(1.0, parsed.salt!!.value, 0.0)
        assertEquals(listOf("450", "12", "20", "8", "2", "5", "3", "1"), parsed.recognisedRanges.map { text.substring(it) })
        assertEquals(5.0, parseNutritionShorthand("5 sugar").sugars!!.value, 0.0)
        assertEquals(5.0, parseNutritionShorthand("5g Sugars").sugars!!.value, 0.0)
        assertEquals(3.0, parseNutritionShorthand("3 fiber").fibre!!.value, 0.0)
        assertEquals(3.0, parseNutritionShorthand("3g FIBRE").fibre!!.value, 0.0)
        assertEquals(1.5, parseNutritionShorthand("1,5 g salt").salt!!.value, 0.0)
    }

    @Test
    fun sodiumIsNotRead() {
        val parsed = parseNutritionShorthand("400 sodium, 400 mg sodium")
        assertNull(parsed.salt)
        assertEquals(emptyList<IntRange>(), parsed.recognisedRanges)
    }

    @Test
    fun theFourResolveAndAFieldWins() {
        val resolved = resolveNutrition(NutritionInput(shorthand = "100 kcal 2 sat fat 5 sugars 3 fibre 1 salt", fibre = "4"))
        assertEquals(NutritionValues(100.0, null, null, null, 2.0, 5.0, 4.0, 1.0), resolved.values())
        val unknown = resolveNutrition(NutritionInput(shorthand = "100 kcal"))
        assertEquals(NutritionValues(100.0, null, null, null), unknown.values())
        assertNull(resolveNutrition(NutritionInput(shorthand = "3 fibre")).values())
        assertEquals(true, resolveNutrition(NutritionInput(shorthand = "100 kcal", salt = "a lot")).invalid)
    }

    @Test
    fun basisDefaultsToStructuredFieldsAndShorthandPerWins() {
        assertEquals(ResolvedNutrition(120.0, null, null, null, 100.0, MeasureUnit.GRAM), resolveNutrition(NutritionInput(shorthand = "120 kcal")))
        val ml = resolveNutrition(NutritionInput(shorthand = "per 250 ml: 90 kcal", basisAmount = "100", basisUnit = "g"))
        assertEquals(250.0, ml.basisAmount, 0.0)
        assertEquals(MeasureUnit.MILLILITER, ml.basisUnit)
        val structured = resolveNutrition(NutritionInput(calories = "50", basisAmount = "30", basisUnit = "ml"))
        assertEquals(30.0, structured.basisAmount, 0.0)
        assertEquals(MeasureUnit.MILLILITER, structured.basisUnit)
    }

    @Test
    fun basisAcceptsGramWordsDecimalsAndAnyCase() {
        val gram = resolveNutrition(NutritionInput(shorthand = "per 100 gram: 120 kcal"))
        assertEquals(100.0, gram.basisAmount, 0.0)
        assertEquals(MeasureUnit.GRAM, gram.basisUnit)
        val comma = resolveNutrition(NutritionInput(shorthand = "per 1,5 ml: 10 kcal"))
        assertEquals(1.5, comma.basisAmount, 0.0)
        assertEquals(MeasureUnit.MILLILITER, comma.basisUnit)
        val dot = resolveNutrition(NutritionInput(shorthand = "per 1.5 ml: 10 kcal"))
        assertEquals(1.5, dot.basisAmount, 0.0)
        val mixed = resolveNutrition(NutritionInput(shorthand = "PER 250 Ml: 10 KCAL"))
        assertEquals(250.0, mixed.basisAmount, 0.0)
        assertEquals(MeasureUnit.MILLILITER, mixed.basisUnit)
        assertEquals(10.0, mixed.calories!!, 0.0)
    }

    @Test
    fun changedStructuredBasisBeatsShorthandPer() {
        val amountChanged = resolveNutrition(NutritionInput(shorthand = "per 250 ml: 90 kcal", basisAmount = "50", basisUnit = "g"))
        assertEquals(50.0, amountChanged.basisAmount, 0.0)
        assertEquals(MeasureUnit.GRAM, amountChanged.basisUnit)
        val unitChanged = resolveNutrition(NutritionInput(shorthand = "per 250 g: 90 kcal", basisAmount = "100", basisUnit = "ml"))
        assertEquals(100.0, unitChanged.basisAmount, 0.0)
        assertEquals(MeasureUnit.MILLILITER, unitChanged.basisUnit)
    }

    @Test
    fun noRecognisedNumbersLeavesCaloriesMissing() {
        assertNull(resolveNutrition(NutritionInput(shorthand = "tasty stuff")).calories)
        assertEquals(emptyList<IntRange>(), parseNutritionShorthand("tasty stuff").recognisedRanges)
    }
}
