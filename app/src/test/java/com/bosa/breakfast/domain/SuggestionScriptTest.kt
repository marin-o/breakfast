package com.bosa.breakfast.domain

import com.bosa.breakfast.testFood
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Suggestions match the fragment in the script it was typed in; scripts never cross. */
class SuggestionScriptTest {
    private fun food(id: String, name: String, aliases: List<String> = emptyList(), category: String = "Test") =
        testFood(id, name, aliases, category, values = NutritionValues(100.0, 5.0, 10.0, 2.0), defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM)

    private val buckwheat = food("buckwheat", "Buckwheat", category = "Grain")
    private val chicken = TestFoods.fixtures.first { it.id == "usda_2646171_chicken_thigh_raw" }
    private val chevap = food("chevap", "Чевап", category = "Meat")
    private val planner = LogPlanner(FoodMatcher(TestFoods.fixtures + buckwheat + chevap))

    private fun suggested(fragment: String) = planner.draft(fragment).suggestions.map { it.food.id }

    @Test fun twoEggsBuDoesNotSuggestChickenThighThroughItsCyrillicButAlias() {
        // "пилешки бут" is Cyrillic: the Latin fragment "bu" must not match it.
        val ids = planner.draft("2 eggs bu").suggestions.map { it.food.id }
        assertFalse(ids.toString(), chicken.id in ids)
        assertTrue(ids.toString(), "buckwheat" in ids)
    }

    @Test fun aLatinFragmentNeverSuggestsACyrillicOnlyFood() {
        // Чевап has no Latin name, so "cev" does not suggest it: suggestions never cross scripts.
        assertFalse(suggested("cev").toString(), "chevap" in suggested("cev"))
        assertTrue(suggested("cev").toString(), suggested("cev").isEmpty())
    }

    @Test fun aCyrillicFragmentSuggestsTheCyrillicFood() {
        assertTrue(suggested("чев").toString(), "chevap" in suggested("чев"))
    }

    @Test fun domSuggestsHomemadeMilkInCyrillic() {
        assertTrue(suggested("дом").toString(), "custom_home_milk" in suggested("дом"))
    }

    @Test fun aWholeNameStartRanksBeforeALaterWordStart() {
        val ids = planner.draft("bu").suggestions.map { it.food.id }
        assertTrue(ids.toString(), ids.indexOf("buckwheat") in 0 until ids.indexOf("peanut_butter_smooth").let { if (it < 0) Int.MAX_VALUE else it })
    }
}
