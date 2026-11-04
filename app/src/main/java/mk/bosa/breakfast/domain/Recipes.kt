package mk.bosa.breakfast.domain


/**
 * One ingredient line of a recipe: the food, the amount as entered and as resolved on the food's basis, and the
 * nutrition of that amount. While the food exists the line follows it ([current]); the stored numbers are what is
 * left once the food is deleted, as an entry keeps its own.
 */
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
 * A dish made from foods. It yields [portions], a [totalWeight] in grams (the weighed finished dish), a [totalVolume] in
 * ml (the measured finished dish), or any of them together: at least one. Its name and aliases are matched like a
 * personal food's. Recipes are never ingredients.
 */
data class Recipe(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val portions: Double? = null,
    val totalWeight: Double? = null,
    val totalVolume: Double? = null,
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
 * parts as the day total keeps them ([macroTotalsOf]), so a macro some ingredient has no value for shows "?"; [fibre]
 * the same way, for the page while fibre is shown.
 */
data class RecipeNutrition(val values: NutritionValues, val macros: MacroTotals, val fibre: MacroTotal = macroTotalOf(emptyList())) {
    /** The same nutrition for [factor] of the amount: the parts known and unknown stay as they are. */
    fun scaled(factor: Double) = RecipeNutrition(
        values * factor,
        MacroTotals(macros.protein.scaled(factor), macros.carbohydrates.scaled(factor), macros.fat.scaled(factor)),
        fibre.scaled(factor),
    )
}

private fun MacroTotal.scaled(factor: Double) = copy(sum = sum * factor)

/**
 * The whole dish: the sum of its ingredients at their amounts. A macro no ingredient has a value for stays unknown
 * (null, never zero), so an entry logged from the recipe does not record it as zero.
 */
fun Recipe.total(): RecipeNutrition = ingredients.map { it.nutritionSnapshot }.let { values ->
    RecipeNutrition(values.reduceOrNull { sum, it -> sum + it } ?: NutritionValues.ZERO, macroTotalsOf(values), macroTotalOf(values.map { it.fibre }))
}

/** The recipe's yields that are given, by unit: portions, grams of the dish, ml of the dish, in that order. */
fun Recipe.yields(): List<Pair<MeasureUnit, Double>> = listOfNotNull(
    portions?.takeIf(::validYield)?.let { MeasureUnit.PORTION to it },
    totalWeight?.takeIf(::validYield)?.let { MeasureUnit.GRAM to it },
    totalVolume?.takeIf(::validYield)?.let { MeasureUnit.MILLILITER to it },
)

/**
 * [amount] of [unit]: per portion the total over [Recipe.portions], per gram over [Recipe.totalWeight], per ml over
 * [Recipe.totalVolume]. Null for a unit the recipe has no yield in.
 */
fun Recipe.nutritionFor(amount: Double, unit: MeasureUnit): RecipeNutrition? {
    val yieldAmount = yields().firstOrNull { it.first == unit }?.second ?: return null
    return total().scaled(amount / yieldAmount)
}

/**
 * What the list and the page show a recipe by, and a bare count logs: one portion when it has portions, else 100 g with
 * a weight, else 100 ml, as foods show their basis.
 */
fun Recipe.shownServing(): Pair<Double, MeasureUnit> = when {
    validYield(portions) -> 1.0 to MeasureUnit.PORTION
    validYield(totalWeight) || !validYield(totalVolume) -> 100.0 to MeasureUnit.GRAM
    else -> 100.0 to MeasureUnit.MILLILITER
}

/**
 * The recipe as a food the Log matches and logs: its name and aliases, personal (so it ranks as a personal food does),
 * on the basis of its total weight when it has one, else of its total volume, else of its portions; each other yield
 * converts to that basis (a portion as its share of the grams, an ml as the grams it weighs). It logs one portion by
 * default, else 100 g, else 100 ml ([shownServing]). A logged recipe is one entry with its own snapshot; the food is
 * never stored ([isRecipeId]).
 */
fun Recipe.asFood(): FoodRecord {
    val total = total().values
    val yields = yields()
    val (basisUnit, basisAmount) = yields.firstOrNull { it.first == MeasureUnit.GRAM }
        ?: yields.firstOrNull { it.first == MeasureUnit.MILLILITER }
        ?: yields.firstOrNull()
        ?: (MeasureUnit.PORTION to 1.0)
    val basis = NutritionBasis(basisAmount, basisUnit, total)
    // The basis itself as a conversion, as a personal food has ("custom basis"); every other yield in basis units.
    val conversions = yields.map { (unit, amount) ->
        ServingConversion(unit, if (unit == basisUnit) amount else basisAmount / amount, "recipe ${unit.name.lowercase()}")
    }
    val (defaultAmount, defaultUnit) = shownServing()
    // With both a weight and a volume, the dish's own density crosses g and ml (only a density ever does).
    val weightOf100Ml = (totalWeight?.takeIf(::validYield) to totalVolume?.takeIf(::validYield))
        .let { (weight, volume) -> if (weight != null && volume != null) weight / volume * 100.0 else null }
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
        density = weightOf100Ml,
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
