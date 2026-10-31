package com.iron.breakfast.recipes

import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.LogPlan
import com.iron.breakfast.domain.LogPlanner
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.Recipe
import com.iron.breakfast.domain.RecipeIngredient
import com.iron.breakfast.domain.parseDecimal
import com.iron.breakfast.domain.millilitresIn
import com.iron.breakfast.domain.parseStrictAmountUnit
import com.iron.breakfast.domain.splitAliases
import com.iron.breakfast.domain.validYield
import com.iron.breakfast.domain.editableNumber
import kotlinx.serialization.Serializable

// The recipe form's pure decisions, unit-tested: what its fields read as, when it may save, and how a recipe goes into
// the form and comes out of it.

/** The recipe form's fields as typed. [ingredients] is typed like the Log bar. */
@Serializable
internal data class RecipeFields(
    val name: String = "",
    val portions: String = "",
    /** The whole dish, typed in any weight or volume unit ("1.6 kg", "1.5 l"), or a bare number in [totalUnit]. */
    val total: String = "",
    /** g or ml: what a bare [total] is in. */
    val totalUnit: MeasureUnit = MeasureUnit.GRAM,
    val aliases: String = "",
    val ingredients: String = "",
)

/** A yield field: a positive number, or null (blank, or not one). */
internal fun yieldOf(text: String): Double? = parseDecimal(text)?.takeIf(::validYield)

/** A yield field with something typed that is not a positive number: it shows as an error. */
internal fun yieldError(text: String): Boolean = text.isNotBlank() && yieldOf(text) == null

/**
 * The Total as g or ml of the dish: a bare number in [unit], or an amount typed in any weight unit (as g) or volume
 * unit (as ml): "1.6 kg" is 1600 g, "1.5 l" 1500 ml, "2 cups" 480 ml. Null when blank or not one.
 */
internal fun totalOf(text: String, unit: MeasureUnit): Pair<MeasureUnit, Double>? {
    yieldOf(text)?.let { return unit to it }
    val typed = parseStrictAmountUnit(text) ?: return null
    val ml = millilitresIn(typed.unit)
    return when {
        typed.unit == MeasureUnit.GRAM -> MeasureUnit.GRAM to typed.amount
        ml != null -> MeasureUnit.MILLILITER to typed.amount * ml
        else -> null
    }
}

/** A Total typed that is no weight or volume: it shows as an error. */
internal fun totalError(fields: RecipeFields): Boolean = fields.total.isNotBlank() && totalOf(fields.total, fields.totalUnit) == null

/**
 * Save is offered once the recipe is whole: a name, portions or a total (at least one, and neither typed wrong), and
 * ingredients that all resolve ([ingredientsComplete]: at least one, none "Not in your foods" or still a choice).
 */
internal fun recipeCanSave(fields: RecipeFields, ingredientsComplete: Boolean): Boolean =
    fields.name.isNotBlank() && !yieldError(fields.portions) && !totalError(fields) &&
        (yieldOf(fields.portions) != null || totalOf(fields.total, fields.totalUnit) != null) && ingredientsComplete

/** Every part of the finished ingredients resolves, and there is at least one. */
internal fun ingredientsComplete(plan: LogPlan): Boolean = plan.entries.isNotEmpty() && plan.unresolved.isEmpty()

/** The ingredients a finished [plan] saves: each part's food, its amount as entered and as resolved, and its numbers. */
internal fun ingredientsOf(plan: LogPlan): List<RecipeIngredient> = plan.entries.map { part ->
    // An ingredient has no serving name of its own: a serving ("2 slices") is kept as what it weighs on the food's basis.
    val serving = part.unit == MeasureUnit.SERVING
    RecipeIngredient(
        foodId = part.food.id,
        foodName = part.food.name,
        enteredAmount = if (serving) part.calculation.resolvedBaseAmount else part.amount,
        enteredUnit = if (serving) part.food.nutrition.unit else part.unit,
        resolvedBaseAmount = part.calculation.resolvedBaseAmount,
        resolvedBaseUnit = part.food.nutrition.unit,
        nutritionSnapshot = part.calculation.nutrition,
    )
}

/**
 * The recipe the form saves: [id] blank for a new one. Call only when [recipeCanSave]. The form shows one total, the
 * weight when there is one. A Total left as [kept] has it keeps both, exactly; any other Total is the only one, since a
 * volume kept beside another weight would no longer match the dish.
 */
internal fun recipeOf(id: String?, fields: RecipeFields, plan: LogPlan, kept: Recipe? = null): Recipe {
    val total = totalOf(fields.total, fields.totalUnit)
    val same = kept?.takeIf { totalUntouched(fields, total, it) }
    return Recipe(
        id = id.orEmpty(),
        name = fields.name.trim(),
        aliases = splitAliases(fields.aliases).distinct(),
        portions = yieldOf(fields.portions),
        totalWeight = if (same != null) same.totalWeight else total?.takeIf { it.first == MeasureUnit.GRAM }?.second,
        totalVolume = if (same != null) same.totalVolume else total?.takeIf { it.first == MeasureUnit.MILLILITER }?.second,
        ingredients = ingredientsOf(plan),
    )
}

/** The Total still reads as the one [recipe] shows, as Edit filled it or typed to the same amount. */
private fun totalUntouched(fields: RecipeFields, total: Pair<MeasureUnit, Double>?, recipe: Recipe): Boolean {
    val shown = recipe.totalWeight ?: recipe.totalVolume ?: return false
    val unit = if (recipe.totalWeight != null) MeasureUnit.GRAM else MeasureUnit.MILLILITER
    return (fields.totalUnit == unit && fields.total == editableNumber(shown)) || total == (unit to shown)
}

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
        // One total: the weight when it has one, else the volume.
        total = editableNumber(recipe.totalWeight ?: recipe.totalVolume),
        totalUnit = if (recipe.totalWeight == null && recipe.totalVolume != null) MeasureUnit.MILLILITER else MeasureUnit.GRAM,
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
