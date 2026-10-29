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

/**
 * A recipe's nutrition: the values summed as entries are ([NutritionValues.plus]), and each macro's known and unknown
 * parts as the day total keeps them ([macroTotalsOf]), so a macro some ingredient has no value for shows "?".
 */
data class RecipeNutrition(val values: NutritionValues, val macros: MacroTotals) {
    /** The same nutrition for [factor] of the amount: the parts known and unknown stay as they are. */
    fun scaled(factor: Double) = RecipeNutrition(
        values * factor,
        MacroTotals(macros.protein.scaled(factor), macros.carbohydrates.scaled(factor), macros.fat.scaled(factor)),
    )
}

private fun MacroTotal.scaled(factor: Double) = copy(sum = sum * factor)

/**
 * The whole dish: the sum of its ingredients at their amounts. A macro no ingredient has a value for stays unknown
 * (null, never zero), so an entry logged from the recipe does not record it as zero.
 */
fun Recipe.total(): RecipeNutrition = ingredients.map { it.nutritionSnapshot }.let { values ->
    RecipeNutrition(values.reduceOrNull { sum, it -> sum + it } ?: NutritionValues.ZERO, macroTotalsOf(values))
}

/**
 * [amount] of [unit]: per portion the total over [Recipe.portions], per gram the total over [Recipe.totalWeight]. Null
 * for a unit the recipe has no yield in.
 */
fun Recipe.nutritionFor(amount: Double, unit: MeasureUnit): RecipeNutrition? {
    val yieldAmount = when (unit) {
        MeasureUnit.PORTION -> portions
        MeasureUnit.GRAM -> totalWeight
        else -> null
    }?.takeIf(::validYield) ?: return null
    return total().scaled(amount / yieldAmount)
}

/** What the list and the page show a recipe by: one portion when it has portions, else 100 g, as foods show their basis. */
fun Recipe.shownServing(): Pair<Double, MeasureUnit> =
    if (validYield(portions)) 1.0 to MeasureUnit.PORTION else 100.0 to MeasureUnit.GRAM

/** The units a recipe logs in: portion when it has portions, g when it has a total weight. */
fun Recipe.units(): List<MeasureUnit> = listOfNotNull(
    MeasureUnit.PORTION.takeIf { validYield(portions) },
    MeasureUnit.GRAM.takeIf { validYield(totalWeight) },
)

/**
 * The recipe as a food the Log matches and logs: its name and aliases, personal (so it ranks as a personal food does),
 * on the basis of its total weight when it has one (portions converted to grams), else of its portions. It logs one
 * portion by default, or 100 g without portions. A logged recipe is one entry with its own snapshot; the food is never
 * stored ([isRecipeId]).
 */
fun Recipe.asFood(): FoodRecord {
    val total = total().values
    val weight = totalWeight?.takeIf(::validYield)
    val portions = portions?.takeIf(::validYield)
    val basis = if (weight != null) NutritionBasis(weight, MeasureUnit.GRAM, total) else NutritionBasis(portions ?: 1.0, MeasureUnit.PORTION, total)
    val conversions = buildList {
        if (weight != null) add(ServingConversion(MeasureUnit.GRAM, weight, "recipe weight"))
        if (portions != null) add(ServingConversion(MeasureUnit.PORTION, if (weight != null) weight / portions else portions, "recipe portion"))
    }
    val (defaultAmount, defaultUnit) = shownServing()
    return FoodRecord(
        id = id,
        name = name,
        aliases = aliases,
        category = "Recipe",
        origin = FoodOrigin.CUSTOM,
        nutrition = basis,
        conversions = conversions,
        defaultAmount = defaultAmount,
        defaultUnit = defaultUnit,
        personalOverride = true,
    )
}

/** The recipes list: the most recently logged first, then by name, as the Library orders foods ([libraryRecencyOrder]). */
internal fun recipeRecencyOrder(recipes: List<Recipe>, entries: List<MealEntry>): List<Recipe> =
    recencyOrder(recipes, entries, Recipe::id, Recipe::name)

/**
 * The recipes as they count now ([current]) and what the Log matches: the foods, then each recipe as a food
 * ([asFood]). Built once per pair of lists (the store keeps a list as the same object until its table is written), so
 * the matcher over them is rebuilt only when foods or recipes change. With no recipes, the foods list itself.
 */
internal class RecipeIndex {
    private var foods: List<FoodRecord>? = null
    private var recipes: List<Recipe>? = null
    private var current: List<Recipe> = emptyList()
    private var matchable: List<FoodRecord> = emptyList()

    @Synchronized
    fun recipes(foods: List<FoodRecord>, recipes: List<Recipe>): List<Recipe> { update(foods, recipes); return current }

    @Synchronized
    fun matchable(foods: List<FoodRecord>, recipes: List<Recipe>): List<FoodRecord> { update(foods, recipes); return matchable }

    private fun update(foods: List<FoodRecord>, recipes: List<Recipe>) {
        if (foods === this.foods && recipes === this.recipes) return
        val byId = foods.associateBy { it.id }
        current = recipes.map { it.current(byId) }
        matchable = if (current.isEmpty()) foods else foods + current.map { it.asFood() }
        this.foods = foods
        this.recipes = recipes
    }
}
