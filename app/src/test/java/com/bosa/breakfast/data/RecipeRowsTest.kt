package com.bosa.breakfast.data

import com.bosa.breakfast.testFood
import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.domain.NutritionValues
import com.bosa.breakfast.domain.Recipe
import com.bosa.breakfast.domain.RecipeIngredient
import com.bosa.breakfast.domain.ServingConversion
import com.bosa.breakfast.domain.current
import com.bosa.breakfast.domain.following
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** A recipe as Room stores it and back, and an ingredient following its food. */
class RecipeRowsTest {
    private val mince = RecipeIngredient("custom-mince", "Beef mince", 500.0, MeasureUnit.GRAM, 500.0, MeasureUnit.GRAM, NutritionValues(1250.0, 100.0, null, 90.0))
    private val beans = RecipeIngredient("custom-beans", "Kidney beans", 1.0, MeasureUnit.PIECE, 240.0, MeasureUnit.GRAM, NutritionValues(300.0, 20.0, 50.0, 1.0))
    private val chili = Recipe("recipe-chili", "Чили", listOf("chili", "чили"), portions = 4.0, totalWeight = null, ingredients = listOf(mince, beans))

    private fun food(id: String, kcalPer100: Double, unit: MeasureUnit = MeasureUnit.GRAM, conversions: List<ServingConversion> = emptyList()) =
        testFood(id, "Edited $id", category = "Personal food", unit = unit, values = NutritionValues(kcalPer100, 10.0, 0.0, 5.0), conversions = conversions)

    @Test fun aRecipeComesBackFromItsRowsInTypedOrder() {
        val rows = chili.toIngredientEntities()
        assertEquals(listOf(0, 1), rows.map { it.position })
        // Stored in any order, read in typed order.
        val read = recipesOf(listOf(chili.toEntity()), chili.toAliasEntities().reversed(), rows.reversed()).single()
        assertEquals(chili.copy(aliases = chili.aliases.reversed()), read)
        val weighed = chili.copy(id = "recipe-soup", portions = null, totalWeight = 900.0, aliases = emptyList(), ingredients = emptyList())
        assertEquals(listOf(chili.copy(aliases = chili.aliases.reversed()), weighed), recipesOf(listOf(chili.toEntity(), weighed.toEntity()), chili.toAliasEntities().reversed(), rows))
    }

    @Test fun anIngredientFollowsItsFoodWhileItExists() {
        val edited = mince.following(food("custom-mince", 200.0))
        assertEquals("Edited custom-mince", edited.foodName)
        assertEquals(1000.0, edited.nutritionSnapshot.calories, 1e-9)
        assertEquals(50.0, edited.nutritionSnapshot.protein!!, 1e-9)
        assertEquals(500.0, edited.resolvedBaseAmount, 0.0)
        // Its food deleted, or no longer taking its unit: it keeps its own numbers.
        assertSame(mince, mince.following(null))
        assertSame(beans, beans.following(food("custom-beans", 100.0, MeasureUnit.MILLILITER)))
        val withPiece = beans.following(food("custom-beans", 100.0, conversions = listOf(ServingConversion(MeasureUnit.PIECE, 400.0, "can"))))
        assertEquals(400.0, withPiece.nutritionSnapshot.calories, 1e-9)
        assertEquals(400.0, withPiece.resolvedBaseAmount, 0.0)
    }

    @Test fun aRecipeFollowsEveryFoodItHas() {
        val now = chili.current(mapOf("custom-mince" to food("custom-mince", 200.0)))
        assertEquals(listOf(1000.0, 300.0), now.ingredients.map { it.nutritionSnapshot.calories })
        assertEquals(chili.name, now.name)
    }
}
