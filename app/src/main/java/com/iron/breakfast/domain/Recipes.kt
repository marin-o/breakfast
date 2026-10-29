package com.iron.breakfast.domain

import kotlinx.serialization.Serializable

/**
 * One ingredient line of a recipe: the food, the amount as entered and as resolved on the food's basis, and the
 * nutrition of that amount. While the food exists the line follows it ([current]); the stored numbers are what is
 * left once the food is deleted, as an entry keeps its own.
 */
@Serializable
data class RecipeIngredient(
    val foodId: String,
    val foodName: String,
    val enteredAmount: Double,
    val enteredUnit: MeasureUnit,
    val resolvedBaseAmount: Double,
    val resolvedBaseUnit: MeasureUnit,
    val nutritionSnapshot: NutritionValues,
)

/**
 * A dish made from foods. It yields [portions], a [totalWeight] in grams (the weighed finished dish), or both: at least
 * one. Its name and aliases are matched like a personal food's. Recipes are never ingredients.
 */
@Serializable
data class Recipe(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val portions: Double? = null,
    val totalWeight: Double? = null,
    val ingredients: List<RecipeIngredient> = emptyList(),
)

/** Recipe ids start with this, so an entry or a preference can tell a recipe from a food by its id alone. */
internal const val RECIPE_ID_PREFIX = "recipe-"

/** True when [id] names a recipe rather than a food. */
fun isRecipeId(id: String): Boolean = id.startsWith(RECIPE_ID_PREFIX)

/**
 * The ingredient as it counts now: computed from [food] when it still exists and still supports the entered unit,
 * else as stored. Editing a personal food so changes every recipe that uses it.
 */
internal fun RecipeIngredient.following(food: FoodRecord?, calculator: NutritionCalculator = NutritionCalculator()): RecipeIngredient {
    if (food == null) return this
    val calculation = calculator.calculate(food, enteredAmount, enteredUnit)
    if (!calculation.supported) return this
    return copy(
        foodName = food.name,
        resolvedBaseAmount = calculation.resolvedBaseAmount,
        resolvedBaseUnit = food.nutrition.unit,
        nutritionSnapshot = calculation.nutrition,
    )
}

/** [this] with every ingredient following its food in [foodsById] (see [following]). */
internal fun Recipe.current(foodsById: Map<String, FoodRecord>): Recipe {
    val calculator = NutritionCalculator()
    return copy(ingredients = ingredients.map { it.following(foodsById[it.foodId], calculator) })
}

/** A positive, finite yield: what a recipe's portions and total weight must be when given. */
internal fun validYield(value: Double?): Boolean = value != null && value.isFinite() && value > 0.0
