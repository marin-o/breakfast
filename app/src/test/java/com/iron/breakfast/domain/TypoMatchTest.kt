package com.iron.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypoMatchTest {
    private fun food(id: String, name: String, aliases: List<String> = emptyList()) = FoodRecord(
        id = id, name = name, aliases = aliases, category = "Test", origin = FoodOrigin.GENERIC,
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(100.0, 1.0, 1.0, 1.0)),
        defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM,
    )

    private val matcher = FoodMatcher(FoodCatalog.fixtures)

    @Test fun aTypoInALongWordStillFindsTheFood() {
        assertEquals("Banana", matcher.best("bananna")?.food?.name)
        assertEquals("Banana", matcher.best("бананна")?.food?.name)
        assertEquals("buckwheat", FoodMatcher(listOf(food("buckwheat", "Buckwheat"))).best("buckweat")?.food?.id)
    }

    @Test fun aFiveLetterWordAllowsOneEdit() {
        val kefir = FoodMatcher(listOf(food("kefir", "Kefir")))
        assertEquals("kefir", kefir.best("kefer")?.food?.id)
        assertTrue(kefir.suggestions("kefre2").isEmpty())
    }

    @Test fun shortWordsNeverMatchOnATypo() {
        assertTrue(matcher.suggestions("eggz").isEmpty())
        assertTrue(FoodMatcher(listOf(food("ox", "Oat"))).suggestions("ot").isEmpty())
    }

    @Test fun anExactOrStemmedMatchBeatsATypo() {
        val pair = FoodMatcher(listOf(food("kefir", "Kefir"), food("kefer", "Kefer")))
        assertEquals("kefer", pair.best("kefer")?.food?.id)
        val beans = FoodMatcher(listOf(food("bean", "Bean"), food("bens", "Bens")))
        assertEquals("bean", beans.best("beans")?.food?.id)
    }

    @Test fun typosAreCheckedAgainstNamesOnly() {
        // "chicken" is a word in this food's source description, not its name, so a typo of it finds nothing.
        val described = FoodRecord(
            id = "described", name = "Turkey", aliases = emptyList(), category = "Test", origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(100.0, 1.0, 1.0, 1.0)),
            defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM, sourceDescription = "chicken style",
        )
        assertTrue(FoodMatcher(listOf(described)).suggestions("chickenn").isEmpty())
    }
}
