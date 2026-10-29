package com.iron.breakfast.recipes

import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.LogPlan
import com.iron.breakfast.domain.LogPlanner
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.Recipe
import com.iron.breakfast.domain.RecipeIngredient
import com.iron.breakfast.domain.parseDecimal
import com.iron.breakfast.domain.splitAliases
import com.iron.breakfast.domain.validYield
import com.iron.breakfast.ui.screens.editableNumber
import kotlinx.serialization.Serializable

// The recipe form's pure decisions, unit-tested: what its fields read as, when it may save, and how a recipe goes into
// the form and comes out of it.

/** The recipe form's fields as typed. [ingredients] is typed like the Log bar. */
@Serializable
internal data class RecipeFields(
    val name: String = "",
    val portions: String = "",
    val totalWeight: String = "",
    val totalVolume: String = "",
    val aliases: String = "",
    val ingredients: String = "",
)

/** A yield field: a positive number, or null (blank, or not one). */
internal fun yieldOf(text: String): Double? = parseDecimal(text)?.takeIf(::validYield)

/** A yield field with something typed that is not a positive number: it shows as an error. */
internal fun yieldError(text: String): Boolean = text.isNotBlank() && yieldOf(text) == null

/**
 * Save is offered once the recipe is whole: a name, portions, a total weight or a total volume (at least one, and none
 * typed wrong),
 * and ingredients that all resolve ([ingredientsComplete]: at least one, none "Not in your foods" or still a choice).
 */
internal fun recipeCanSave(fields: RecipeFields, ingredientsComplete: Boolean): Boolean =
    fields.name.isNotBlank() && listOf(fields.portions, fields.totalWeight, fields.totalVolume).let { yields ->
        yields.none(::yieldError) && yields.any { yieldOf(it) != null }
    } && ingredientsComplete

/** Every part of the finished ingredients resolves, and there is at least one. */
internal fun ingredientsComplete(plan: LogPlan): Boolean = plan.entries.isNotEmpty() && plan.unresolved.isEmpty()

/** The ingredients a finished [plan] saves: each part's food, its amount as entered and as resolved, and its numbers. */
internal fun ingredientsOf(plan: LogPlan): List<RecipeIngredient> = plan.entries.map { part ->
    RecipeIngredient(
        foodId = part.food.id,
        foodName = part.food.name,
        enteredAmount = part.amount,
        enteredUnit = part.unit,
        resolvedBaseAmount = part.calculation.resolvedBaseAmount,
        resolvedBaseUnit = part.food.nutrition.unit,
        nutritionSnapshot = part.calculation.nutrition,
    )
}

/** The recipe the form saves: [id] blank for a new one. Call only when [recipeCanSave]. */
internal fun recipeOf(id: String?, fields: RecipeFields, plan: LogPlan): Recipe = Recipe(
    id = id.orEmpty(),
    name = fields.name.trim(),
    aliases = splitAliases(fields.aliases).distinct(),
    portions = yieldOf(fields.portions),
    totalWeight = yieldOf(fields.totalWeight),
    totalVolume = yieldOf(fields.totalVolume),
    ingredients = ingredientsOf(plan),
)

/** What Edit opens with: the recipe's fields, its ingredients typed back as the Log writes a choice, and their pins. */
internal data class RecipeEdit(val fields: RecipeFields, val pins: Map<String, FoodRecord>)

/**
 * [recipe] in the form. Each ingredient becomes a part ("500 g Beef mince") pinned to its food, so it reads back as
 * that food whatever else matches the name; an ingredient whose food was deleted is pinned to a food made of its own
 * numbers ([snapshotFood]), so it keeps them.
 */
internal fun recipeEdit(recipe: Recipe, foods: List<FoodRecord>, planner: LogPlanner): RecipeEdit {
    val byId = foods.associateBy { it.id }
    val pins = LinkedHashMap<String, FoodRecord>()
    val parts = recipe.ingredients.map { ingredient ->
        val food = byId[ingredient.foodId] ?: snapshotFood(ingredient)
        planner.partText(food, ingredient.enteredAmount, ingredient.enteredUnit).also { pins[it] = food }
    }
    val fields = RecipeFields(
        name = recipe.name,
        portions = editableNumber(recipe.portions),
        totalWeight = editableNumber(recipe.totalWeight),
        totalVolume = editableNumber(recipe.totalVolume),
        aliases = recipe.aliases.joinToString(", "),
        ingredients = parts.joinToString(", "),
    )
    return RecipeEdit(fields, pins)
}

/** The category of a [snapshotFood], so the form can tell such a pin from one to a stored food. */
internal const val SNAPSHOT_CATEGORY = "Recipe ingredient"

/** A food made of an ingredient's own numbers, for one whose food was deleted: its amount gives exactly them. */
internal fun snapshotFood(ingredient: RecipeIngredient): FoodRecord = FoodRecord(
    id = ingredient.foodId,
    name = ingredient.foodName,
    aliases = emptyList(),
    category = SNAPSHOT_CATEGORY,
    origin = FoodOrigin.CUSTOM,
    nutrition = NutritionBasis(ingredient.enteredAmount.takeIf { it > 0.0 } ?: 1.0, ingredient.enteredUnit, ingredient.nutritionSnapshot),
    defaultAmount = ingredient.enteredAmount,
    defaultUnit = ingredient.enteredUnit,
)
