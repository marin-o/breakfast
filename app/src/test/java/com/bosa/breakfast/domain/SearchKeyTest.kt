package com.bosa.breakfast.domain

import com.bosa.breakfast.contribution.contributionLanguageFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Library and ingredient searches read names as the Log matcher does, and never cross scripts. */
class SearchKeyTest {
    @Test fun accentsApostrophesAndHyphensReadAsTheyDoInTheLog() {
        assertTrue(searchFinds("Ćevapi", "cevapi"))
        assertTrue(searchFinds("cevapi", "Ćevapi"))
        assertTrue(searchFinds("Šljivovica", "sljiv"))
        assertTrue(searchFinds("Jerry's", "jerrys"))
        assertTrue(searchFinds("Coca-Cola Zero", "coca cola"))
        assertTrue(searchFinds("Coca Cola Zero", "coca-cola zero"))
        assertTrue(searchFinds("Mleko 2,8%", "2,8"))
    }

    @Test fun aQueryNeverCrossesScripts() {
        assertFalse(searchFinds("Чевап", "cev"))
        assertFalse(searchFinds("Cevap", "чев"))
        // A Cyrillic а inside Latin letters: neither Latin nor Cyrillic, so it finds nothing.
        assertFalse(searchFinds("banana", "bаnana"))
        assertFalse(searchFinds("bаnana", "bana"))
        assertFalse(searchFinds("bаnana", "bаnana"))
        // A name in both scripts is found by each of its words, word by word.
        assertTrue(searchFinds("Vitaminka-Витаминка", "вита"))
        assertTrue(searchFinds("Vitaminka-Витаминка", "vita"))
    }

    @Test fun twoScriptsJoinedByPunctuationAreTwoWords() {
        assertTrue(searchFinds("Milk/Млеко", "milk"))
        assertTrue(searchFinds("Milk/Млеко", "млеко"))
        assertTrue(searchFinds("Кока-Кола/Coca-Cola", "coca cola"))
        assertTrue(searchFinds("Кока-Кола/Coca-Cola", "кока кола"))
        assertTrue(searchFinds("Млеко(Milk)", "milk"))
    }

    @Test fun aMixedWordMatchesNothingAndJoinsNothing() {
        assertFalse(searchFinds("Foo Bаr Baz", "foo baz"))
        assertTrue(searchFinds("Foo Bаr Baz", "foo"))
        assertTrue(searchFinds("Foo Bаr Baz", "baz"))
    }

    @Test fun aNumberNeverMatchesAMixedWord() {
        val mixed = FoodRecord("m", "2bаr", emptyList(), "Test", FoodOrigin.CUSTOM, NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(1.0, null, null, null)))
        assertTrue(FoodMatcher(listOf(mixed)).suggestions("2").isEmpty())
    }

    @Test fun aServingNameNeverCrossesScripts() {
        assertTrue(servingNameMatches("slices", "slice"))
        assertFalse(servingNameMatches("slice", "слајс"))
        assertFalse(servingNameMatches("slіce", "slіce"))
        assertFalse(servingNameMatches("2", "2"))
    }

    @Test fun onlyPunctuationFindsNothingButAnEmptyQueryFindsAll() {
        val food = FoodRecord("m", "Mleko", emptyList(), "Test", FoodOrigin.CUSTOM, NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(1.0, null, null, null)))
        assertTrue(com.bosa.breakfast.data.foodMatchesQuery(food, ""))
        assertTrue(com.bosa.breakfast.data.foodMatchesQuery(food, "  "))
        assertFalse(com.bosa.breakfast.data.foodMatchesQuery(food, "&"))
        assertFalse(com.bosa.breakfast.data.foodMatchesQuery(food, "-"))
    }

    @Test fun cyrillicLettersAreNotFolded() {
        assertTrue(searchFinds("Ќебапи", "ќеб"))
        assertFalse(searchFinds("Ќебапи", "кеб"))
    }

    @Test fun aPositiveAmountIsOneRule() {
        assertEquals(12.5, positiveAmountOf("12,5")!!, 0.0)
        assertEquals(12.5, positiveAmountOf(" 12.5 ")!!, 0.0)
        assertEquals(5.0, positiveAmountOf("5.")!!, 0.0)
        assertNull(positiveAmountOf("0"))
        assertNull(positiveAmountOf("-1"))
        assertNull(positiveAmountOf(""))
        assertNull(positiveAmountOf("1e5"))
    }

    @Test fun onlyAWhollyCyrillicNameIsMacedonian() {
        assertEquals("mk", contributionLanguageFor("Ловец"))
        assertEquals("en", contributionLanguageFor("Ajvar"))
        assertEquals("en", contributionLanguageFor("Ajvar Ловец"))
    }

    @Test fun scriptsMatchOnlyTheirOwn() {
        assertTrue(sameScript(TextScript.LATIN, TextScript.LATIN))
        assertFalse(sameScript(TextScript.LATIN, TextScript.CYRILLIC))
        assertFalse(sameScript(TextScript.MIXED, TextScript.MIXED))
        assertFalse(sameScript(TextScript.LATIN, TextScript.MIXED))
        assertTrue(sameScript(TextScript.NONE, TextScript.CYRILLIC))
    }
}
