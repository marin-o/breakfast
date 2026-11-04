package mk.bosa.breakfast.recipes

import mk.bosa.breakfast.ui.components.PageSection
import mk.bosa.breakfast.ui.components.LocalFibreShown
import mk.bosa.breakfast.ui.components.FoodRowWithAdd
import mk.bosa.breakfast.ui.components.listRowMotion
import mk.bosa.breakfast.ui.components.TabList
import mk.bosa.breakfast.ui.components.FoodAmountSheet
import mk.bosa.breakfast.ui.components.NutritionFacts
import mk.bosa.breakfast.ui.components.ItemPage
import mk.bosa.breakfast.ui.screens.yieldLine
import mk.bosa.breakfast.ui.screens.macroFact
import mk.bosa.breakfast.ui.screens.previewQuantityLine
import mk.bosa.breakfast.ui.countLabel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mk.bosa.breakfast.app.Surfaces
import mk.bosa.breakfast.domain.Measure
import mk.bosa.breakfast.domain.MealSlot
import mk.bosa.breakfast.domain.Recipe
import mk.bosa.breakfast.domain.asFood
import mk.bosa.breakfast.domain.nutritionFor
import mk.bosa.breakfast.domain.shownServing
import mk.bosa.breakfast.domain.presetMeal
import mk.bosa.breakfast.ui.components.DestructiveButton
import mk.bosa.breakfast.ui.components.FoodRow
import mk.bosa.breakfast.ui.components.TertiaryButton
import mk.bosa.breakfast.ui.components.rememberMotion
import mk.bosa.breakfast.ui.screens.basisLabel
import mk.bosa.breakfast.ui.screens.formatKcal
import mk.bosa.breakfast.ui.theme.BreakfastType
import mk.bosa.breakfast.ui.theme.LocalBreakfastColors

/**
 * The Recipes tab, laid out as the Library tab: the title, its count line and New recipe, then a row per recipe (its
 * name, a portion or 100 g, and that kcal) with a "+" that opens the amount sheet. "Logged!" floats over the end of the
 * list, as on the Library (from the bottom area, `app/BaseBottomArea.kt`).
 */
@Composable
internal fun RecipesScreen(recipes: RecipeBook, onNew: () -> Unit, modifier: Modifier) {
    val motion = rememberMotion()
    val list = recipes.ordered()
    TabList("Recipes", countLabel(list.size, "recipe", "recipes"), list.isNotEmpty(), modifier, trailing = { TertiaryButton("New recipe", onNew) }) {
        // A logged recipe goes to the top.
        items(list, key = { it.id }) { recipe ->
            RecipeRow(recipes, recipe, listRowMotion(motion))
        }
    }
}

@Composable
private fun RecipeRow(recipes: RecipeBook, recipe: Recipe, modifier: Modifier) {
    val (amount, unit) = recipe.shownServing()
    Box(modifier) {
        FoodRowWithAdd(
            name = recipe.name,
            quantityLine = previewQuantityLine(amount, unit),
            calories = formatKcal(recipe.nutritionFor(amount, unit)?.values?.calories ?: 0.0),
            onClick = { recipes.openRecipe(recipe) },
            addDescription = "Add ${recipe.name} to a meal",
            onAdd = { recipes.beginLog(recipe) },
        )
    }
}

/**
 * A recipe's page, laid out as a food's: its name and yield, the ingredient lines with their amounts and kcal, the
 * nutrition per portion (or per 100 g), then Delete recipe; Edit sits in the bar, as on a food's page.
 */
@Composable
internal fun RecipePageScreen(recipes: RecipeBook, recipe: Recipe, onEdit: (Recipe) -> Unit) {
    val colors = LocalBreakfastColors.current
    val (amount, unit) = recipe.shownServing()
    val nutrition = recipe.nutritionFor(amount, unit)
    ItemPage("Recipe", recipes::closeRecipe, recipe.name, { recipes.closeRecipe(); onEdit(recipe) }) {
        Text(yieldLine(recipe), color = colors.soft, style = BreakfastType.Secondary)
        PageSection("Ingredients")
        recipe.ingredients.forEach { ingredient ->
            FoodRow(
                name = ingredient.foodName,
                quantityLine = previewQuantityLine(ingredient.enteredAmount, ingredient.enteredUnit, approximate = false),
                calories = formatKcal(ingredient.nutritionSnapshot.calories),
            )
        }
        PageSection(basisLabel(amount, unit))
        val macros = nutrition?.macros
        NutritionFacts(
            formatKcal(nutrition?.values?.calories ?: 0.0),
            fat = macros?.let { macroFact(it.fat, noneIsUnknown = true) },
            carbs = macros?.let { macroFact(it.carbohydrates, noneIsUnknown = true) },
            protein = macros?.let { macroFact(it.protein, noneIsUnknown = true) },
            fibre = nutrition?.fibre?.takeIf { LocalFibreShown.current }?.let { macroFact(it, noneIsUnknown = true) },
        )
        DestructiveButton("Delete recipe", { recipes.askDelete(recipe) }, Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), outlined = true)
    }
}

/** A row's "+": the amount sheet with the recipe's units, then its meal, as the Library logs a food. */
@Composable
internal fun RecipeLogSheet(recipes: RecipeBook, surfaces: Surfaces, automaticMeal: () -> MealSlot?, recipe: Recipe) {
    val food = recipe.asFood()
    val last = recipes.lastLoggedEntry(recipe)?.takeIf { entry -> recipe.nutritionFor(entry.enteredAmount, entry.enteredUnit) != null }
    FoodAmountSheet(
        food = food,
        startAmount = last?.enteredAmount ?: food.defaultAmount,
        startMeasure = Measure(last?.enteredUnit ?: food.defaultUnit),
        startMeal = presetMeal(surfaces.mealOnScreen(), automaticMeal()),
        onAdd = { amount, measure, meal -> recipes.log(recipe, meal!!, amount, measure.unit) },
        onDismiss = recipes::dismissLog,
    )
}
