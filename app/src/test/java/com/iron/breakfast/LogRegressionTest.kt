package com.iron.breakfast

import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.FoodMatcher
import com.iron.breakfast.domain.LogPlanner
import com.iron.breakfast.domain.MeasureUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Inputs that resolve on the bundled catalogue today. Each must keep resolving to the same food, amount and unit
 * through [LogPlanner.plan], the path Add saves with (amountless drafts open library search instead, so they are not logged). Expected values were recorded from the code before
 * the smarter parsing; a change here means a real Log result changed, so it needs a reason.
 */
class LogRegressionTest {
    private data class Saved(val foodId: String, val amount: Double, val unit: MeasureUnit)

    private fun s(foodId: String, amount: Double, unit: MeasureUnit) = Saved(foodId, amount, unit)
    private val g = MeasureUnit.GRAM
    private val ml = MeasureUnit.MILLILITER
    private val tbsp = MeasureUnit.TABLESPOON
    private val tsp = MeasureUnit.TEASPOON
    private val piece = MeasureUnit.PIECE

    private val expected: List<Pair<String, List<Saved>>> = listOf(
        "1 banana" to listOf(s("banana", 1.0, piece)),
        "2 eggs" to listOf(s("egg", 2.0, piece)),
        "100g rolled oats" to listOf(s("rolled_oats", 100.0, g)),
        "1 tbsp peanut butter" to listOf(s("peanut_butter_smooth", 1.0, tbsp)),
        "1 tsp pb" to listOf(s("peanut_butter_smooth", 1.0, tsp)),
        "pb" to listOf(s("peanut_butter_smooth", 1.0, tbsp)),
        "half a tbsp peanut butter" to listOf(s("peanut_butter_smooth", 0.5, tbsp)),
        "peanut butter half a tbsp" to listOf(s("peanut_butter_smooth", 0.5, tbsp)),
        "1/2 banana" to listOf(s("banana", 0.5, piece)),
        "1 1/2 tbsp peanut butter" to listOf(s("peanut_butter_smooth", 1.5, tbsp)),
        "milk 3.2%" to listOf(s("milk_32", 100.0, ml)),
        "milk 2.8% 200 ml" to listOf(s("milk_28", 200.0, ml)),
        "100 ml milk 3.2%" to listOf(s("milk_32", 100.0, ml)),
        "200 ml oat milk" to listOf(s("oat_milk", 200.0, ml)),
        "oat milk 150ml" to listOf(s("oat_milk", 150.0, ml)),
        "2 tbsp milk 2.8%" to listOf(s("milk_28", 2.0, tbsp)),
        "lunch, 100g rolled oats" to listOf(s("rolled_oats", 100.0, g)),
        "dinner, 1 banana" to listOf(s("banana", 1.0, piece)),
        "breakfast, 1 egg, 1 tsp pb" to listOf(s("egg", 1.0, piece), s("peanut_butter_smooth", 1.0, tsp)),
        "snack, 2 eggs" to listOf(s("egg", 2.0, piece)),
        "2 банана" to emptyList(), // Cyrillic name of a Latin-named food: no longer matches (scripts never cross).
        "2 јајца" to emptyList(), // Cyrillic name of a Latin-named food: no longer matches (scripts never cross).
        "домашно млеко 200 ml" to listOf(s("custom_home_milk", 200.0, ml)),
        "ajvar" to listOf(s("custom_grandmas_ajvar", 1.0, tbsp)),
        "1 tbsp ketchup" to listOf(s("usda_747693_ketchup", 1.0, tbsp)),
        "2 eggs, 1 banana, 100g chicken breast" to listOf(
            s("egg", 2.0, piece), s("banana", 1.0, piece), s("usda_2646170_chicken_breast_raw", 100.0, g),
        ),
        "100g ground beef 90/10 raw" to listOf(s("usda_2514743_ground_beef_raw", 100.0, g)),
        "ground beef 90/10 raw 100 g" to listOf(s("usda_2514743_ground_beef_raw", 100.0, g)),
        "1 red pepper" to listOf(s("usda_2258590_red_pepper_raw", 1.0, g)),
        "1 cucumber, 1 roma tomato" to listOf(s("usda_2346406_cucumber_raw", 1.0, g), s("usda_1999634_tomato_raw", 1.0, g)),
        "2 tbsp crunchy peanut butter" to listOf(s("peanut_butter_crunchy", 2.0, tbsp)),
        "crunchy peanut butter 1 tbsp" to listOf(s("peanut_butter_crunchy", 1.0, tbsp)),
        "1 tbsp smooth pb" to listOf(s("peanut_butter_smooth", 1.0, tbsp)),
        "a banana" to listOf(s("banana", 1.0, piece)),
        "a egg" to listOf(s("egg", 1.0, piece)),
        "3 eggs,1 banana" to listOf(s("egg", 3.0, piece), s("banana", 1.0, piece)),
        "1 banana," to listOf(s("banana", 1.0, piece)),
        "100 g salmon" to listOf(s("usda_2684441_salmon_raw", 100.0, g)),
        "salmon 150g" to listOf(s("usda_2684441_salmon_raw", 150.0, g)),
        "1 tbsp light tuna" to listOf(s("usda_334194_tuna_canned", 1.0, tbsp)),
        "1 yellow onion" to listOf(s("usda_790646_onion_raw", 1.0, g)),
        "1 cabbage" to listOf(s("usda_2346407_cabbage_raw", 1.0, g)),
        "150g skinless chicken" to listOf(s("usda_2646170_chicken_breast_raw", 150.0, g)),
        "1 banana, lunch" to listOf(s("banana", 1.0, piece)),
    )

    @Test fun resolvedInputsKeepTheirFoodAmountAndUnit() {
        val planner = LogPlanner(FoodMatcher(FoodCatalog.fixtures))
        val drifted = expected.mapNotNull { (input, want) ->
            val got = planner.plan(input).entries.map { Saved(it.food.id, it.amount, it.unit) }
            if (got == want) null else "$input: want $want, got $got"
        }
        assertEquals(emptyList<String>(), drifted)
    }

    @Test fun regressionSetIsBigEnough() {
        assertEquals(true, expected.size >= 25)
    }
}
