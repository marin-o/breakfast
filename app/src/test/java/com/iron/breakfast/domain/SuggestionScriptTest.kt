package com.iron.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Suggestions match the fragment in the script it was typed in; transliteration is only a fallback. */
class SuggestionScriptTest {
    private fun food(id: String, name: String, aliases: List<String> = emptyList(), category: String = "Test") = FoodRecord(
        id = id, name = name, aliases = aliases, category = category, origin = FoodOrigin.CUSTOM,
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(100.0, 5.0, 10.0, 2.0)),
        defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM,
    )

    private val buckwheat = food("buckwheat", "Buckwheat", category = "Grain")
    private val chicken = FoodCatalog.fixtures.first { it.id == "usda_2646171_chicken_thigh_raw" }
    private val chevap = food("chevap", "Чевап", category = "Meat")
    private val planner = LogPlanner(FoodMatcher(FoodCatalog.fixtures + buckwheat + chevap))

    private fun suggested(fragment: String) = planner.draft(fragment).suggestions.map { it.food.id }

    @Test fun twoEggsBuDoesNotSuggestChickenThighThroughItsCyrillicButAlias() {
        // "пилешки бут" transliterates to "pileshki but": the Latin fragment "bu" must not match that Cyrillic word.
        val ids = planner.draft("2 eggs bu").suggestions.map { it.food.id }
        assertFalse(ids.toString(), chicken.id in ids)
        assertTrue(ids.toString(), "buckwheat" in ids)
    }

    @Test fun cevSuggestsChevapThroughTheFallback() {
        assertTrue(suggested("cev").toString(), "chevap" in suggested("cev"))
    }

    @Test fun domSuggestsHomemadeMilkInCyrillic() {
        assertTrue(suggested("дом").toString(), "custom_home_milk" in suggested("дом"))
    }

    @Test fun aWholeNameStartRanksBeforeALaterWordStart() {
        val ids = planner.draft("bu").suggestions.map { it.food.id }
        assertTrue(ids.toString(), ids.indexOf("buckwheat") in 0 until ids.indexOf("peanut_butter_smooth").let { if (it < 0) Int.MAX_VALUE else it })
    }
}
