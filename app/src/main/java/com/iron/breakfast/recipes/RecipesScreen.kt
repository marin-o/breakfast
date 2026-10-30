package com.iron.breakfast.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iron.breakfast.app.Confirmations
import com.iron.breakfast.app.Surfaces
import com.iron.breakfast.domain.MacroTotal
import com.iron.breakfast.domain.Measure
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.Recipe
import com.iron.breakfast.domain.asFood
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.domain.nutritionFor
import com.iron.breakfast.domain.shownServing
import com.iron.breakfast.domain.yields
import com.iron.breakfast.library.FoodAmountSheet
import com.iron.breakfast.library.LibraryFactRow
import com.iron.breakfast.library.libraryPresetMeal
import com.iron.breakfast.log.previewQuantityLine
import com.iron.breakfast.ui.components.AddCircle
import com.iron.breakfast.ui.components.BottomNav
import com.iron.breakfast.ui.components.ConfirmationSnackbar
import com.iron.breakfast.ui.components.DestructiveButton
import com.iron.breakfast.ui.components.FoodRow
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.NavTab
import com.iron.breakfast.ui.components.SectionHeading
import com.iron.breakfast.ui.components.TertiaryButton
import com.iron.breakfast.ui.components.TopBar
import com.iron.breakfast.ui.components.foodText
import com.iron.breakfast.ui.components.rememberMotion
import com.iron.breakfast.ui.components.LocalScreenBody
import com.iron.breakfast.ui.pretty
import com.iron.breakfast.ui.screens.basisLabel
import com.iron.breakfast.ui.screens.countedQuantity
import com.iron.breakfast.ui.screens.formatKcal
import com.iron.breakfast.ui.theme.BreakfastType
import com.iron.breakfast.ui.theme.Gutter
import com.iron.breakfast.ui.theme.LocalBreakfastColors

/**
 * The Recipes tab, laid out as the Library tab: the title, its count line and New recipe, then a row per recipe (its
 * name, a portion or 100 g, and that kcal) with a "+" that opens the amount sheet. "Logged!" floats over the end of the
 * list, as on the Library.
 */
@Composable
internal fun RecipesScreen(recipes: RecipeBook, confirmations: Confirmations, onNew: (() -> Unit)?, modifier: Modifier) {
    val colors = LocalBreakfastColors.current
    val motion = rememberMotion()
    val list = recipes.ordered()
    Box(modifier.fillMaxSize().background(colors.bg)) {
        LazyColumn(Modifier.fillMaxSize().then(LocalScreenBody.current).padding(horizontal = Gutter)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Recipes", color = colors.ink, style = BreakfastType.Section)
                        Text(recipeCountLabel(list.size), color = colors.soft, style = BreakfastType.Meta)
                    }
                    if (onNew != null) TertiaryButton("New recipe", onNew)
                }
                Hairline()
            }
            // Rows fade in and out as the list changes, and move to their new place (a logged recipe goes to the top).
            items(list, key = { it.id }) { recipe ->
                RecipeRow(recipes, recipe, Modifier.animateItem(fadeInSpec = motion.listItem(), placementSpec = motion.listItem(), fadeOutSpec = motion.listItem()))
            }
            // The list's closing line, only under rows: an empty list keeps just the header's line.
            if (list.isNotEmpty()) item { Hairline(Modifier.padding(top = 8.dp)) }
        }
        ConfirmationSnackbar(confirmations.message, Modifier.align(Alignment.BottomCenter).padding(horizontal = Gutter))
    }
}

@Composable
private fun RecipeRow(recipes: RecipeBook, recipe: Recipe, modifier: Modifier) {
    val (amount, unit) = recipe.shownServing()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FoodRow(
            name = recipe.name,
            quantityLine = amountLine(amount, unit),
            calories = formatKcal(recipe.nutritionFor(amount, unit)?.values?.calories ?: 0.0),
            modifier = Modifier.weight(1f),
            onClick = { recipes.openRecipe(recipe) },
        )
        AddCircle(contentDescription = "Add ${recipe.name} to a meal", onClick = { recipes.beginLog(recipe) })
    }
}

/** The Recipes tab's bottom bar: the tabs and Settings, as on the Library tab. */
@Composable
internal fun RecipesBottomBar(surfaces: Surfaces) {
    val colors = LocalBreakfastColors.current
    Column(Modifier.fillMaxWidth().background(colors.bg).navigationBarsPadding().padding(horizontal = Gutter, vertical = 8.dp)) {
        BottomNav(
            selected = NavTab.RECIPES,
            onMeals = surfaces::closeRecipes,
            onLibrary = surfaces::openLibrary,
            onRecipes = {},
            onSettings = surfaces::openSettings,
            settingsOpen = surfaces.settingsOpen,
        )
    }
}

/**
 * A recipe's page, laid out as a food's: its name and yield, the ingredient lines with their amounts and kcal, the
 * nutrition per portion (or per 100 g), then Delete recipe; Edit sits in the bar, as on a food's page.
 */
@Composable
internal fun RecipePageScreen(recipes: RecipeBook, recipe: Recipe, onEdit: ((Recipe) -> Unit)?) {
    val colors = LocalBreakfastColors.current
    val (amount, unit) = recipe.shownServing()
    val nutrition = recipe.nutritionFor(amount, unit)
    Column(Modifier.fillMaxSize().background(colors.bg).windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 4.dp)) {
        TopBar(
            title = "Recipe",
            onBack = recipes::closeRecipe,
            modifier = Modifier.padding(top = 8.dp),
            trailing = { if (onEdit != null) TertiaryButton("Edit", { recipes.closeRecipe(); onEdit(recipe) }) },
        )
        Hairline(Modifier.padding(top = 8.dp))
        Text(foodText(recipe.name), color = colors.ink, style = BreakfastType.Section, modifier = Modifier.padding(top = 8.dp))
        Text(yieldLine(recipe), color = colors.soft, style = BreakfastType.Secondary)
        Hairline(Modifier.padding(top = 16.dp))
        SectionHeading("Ingredients", Modifier.padding(top = 16.dp))
        recipe.ingredients.forEach { ingredient ->
            FoodRow(
                name = ingredient.foodName,
                quantityLine = previewQuantityLine(ingredient.enteredAmount, ingredient.enteredUnit, approximate = false),
                calories = formatKcal(ingredient.nutritionSnapshot.calories),
            )
        }
        Hairline(Modifier.padding(top = 16.dp))
        SectionHeading(basisLabel(amount, unit), Modifier.padding(top = 16.dp))
        LibraryFactRow("Energy", formatKcal(nutrition?.values?.calories ?: 0.0), "kcal")
        nutrition?.macros?.let { macros ->
            MacroFact("Fat", macros.fat)
            MacroFact("Carbohydrate", macros.carbohydrates)
            MacroFact("Protein", macros.protein)
        }
        DestructiveButton("Delete recipe", { recipes.askDelete(recipe) }, Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), outlined = true)
    }
}

/** A macro as the day total shows one: "—" when no ingredient has it, "12 g ?" when only some do. */
@Composable
private fun MacroFact(label: String, total: MacroTotal) {
    val (number, unit) = macroFact(total)
    LibraryFactRow(label, number, unit)
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
        startMeal = libraryPresetMeal(surfaces.mealOnScreen(), automaticMeal()),
        onAdd = { amount, measure, meal -> recipes.log(recipe, meal!!, amount, measure.unit) },
        onInvalid = {},
        onDismiss = recipes::dismissLog,
    )
}

/** "1 recipe", "4 recipes": the Recipes tab's count line. */
internal fun recipeCountLabel(count: Int): String = if (count == 1) "1 recipe" else "$count recipes"

/** "1 portion", "2 portions", "100 g": an amount as a row's quantity line reads it. */
internal fun amountLine(amount: Double, unit: MeasureUnit): String = countedQuantity(amount, unit) ?: "${amount.pretty()} ${unit.displayLabel()}"

/** What a recipe makes: "4 portions", "1600 g", "1500 ml", or what it has of them, "4 portions, 1600 g, 1500 ml". */
internal fun yieldLine(recipe: Recipe): String = recipe.yields().joinToString(", ") { (unit, amount) -> amountLine(amount, unit) }

/** A macro's value and unit on the page: "—" with none known, "12" and "g ?" with some unknown, else "12" and "g". */
internal fun macroFact(total: MacroTotal): Pair<String, String> = when {
    total.allUnknown || total.known == 0 -> "—" to "g"
    total.partial -> total.sum.pretty() to "g ?"
    else -> total.sum.pretty() to "g"
}
