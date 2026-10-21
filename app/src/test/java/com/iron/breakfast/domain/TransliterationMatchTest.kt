package com.iron.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TransliterationMatchTest {
    private fun food(id: String, name: String, category: String, aliases: List<String> = emptyList()) = FoodRecord(
        id = id, name = name, aliases = aliases, category = category, origin = FoodOrigin.GENERIC,
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(100.0, 1.0, 1.0, 1.0)),
        defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM,
    )

    private val kiselo = food("kiselo", "Кисело млеко 2.8%", "Dairy")
    private val pindzur = food("pindzur", "Пинџур домашен", "Spread")
    private val gjevrek = food("gjevrek", "Ѓеврек со сусам", "Bread")
    private val jogurt = food("jogurt", "Jogurt", "Dairy")
    private val chevap = food("chevap", "Chevap", "Meat")
    private val matcher = FoodMatcher(listOf(kiselo, pindzur, gjevrek, jogurt, chevap))

    @Test fun latinTypingFindsMacedonianNames() {
        assertEquals("kiselo", matcher.best("kiselo mleko")?.food?.id)
        assertEquals("pindzur", matcher.best("pindzur")?.food?.id)
        assertEquals("gjevrek", matcher.best("gjevrek")?.food?.id)
        assertEquals("gjevrek", matcher.best("djevrek")?.food?.id)
    }

    @Test fun cyrillicTypingFindsLatinNames() {
        assertEquals("jogurt", matcher.best("јогурт")?.food?.id)
        assertEquals("chevap", matcher.best("чевап")?.food?.id)
        assertEquals("kiselo", matcher.best("кисело")?.food?.id)
    }

    @Test fun sameScriptMatchesRankAboveTransliteratedOnes() {
        val pair = FoodMatcher(listOf(food("latin", "Pindzur", "Spread"), food("cyrillic", "Пинџур", "Spread")))
        assertEquals("latin", pair.best("pindzur")?.food?.id)
        assertEquals("cyrillic", pair.best("пинџур")?.food?.id)
    }

    @Test fun canonicalFormsAgreeAcrossScriptsAndAsciiForms() {
        assertEquals(Transliteration.canonical("кисело млеко"), Transliteration.canonical("kiselo mleko"))
        assertEquals(Transliteration.canonical("Ѓеврек"), Transliteration.canonical("gjevrek"))
        assertEquals(Transliteration.canonical("Ѓеврек"), Transliteration.canonical("djevrek"))
        assertEquals(Transliteration.canonical("Пинџур"), Transliteration.canonical("pindzur"))
        assertEquals(Transliteration.canonical("чевап"), Transliteration.canonical("čevap"))
        assertEquals(Transliteration.canonical("шпанец"), Transliteration.canonical("španec"))
        assertEquals(Transliteration.canonical("жито"), Transliteration.canonical("zhito"))
    }
}
