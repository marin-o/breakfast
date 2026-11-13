package mk.bosa.breakfast.recipes

import mk.bosa.breakfast.scan.ProductReviewOrigin
import mk.bosa.breakfast.food.FoodForm
import mk.bosa.breakfast.log.UsdaRows
import mk.bosa.breakfast.log.rememberUsdaRows
import mk.bosa.breakfast.search.OffSearch
import mk.bosa.breakfast.search.NoLocalMatchWithRows
import mk.bosa.breakfast.search.UsdaPartSearch
import mk.bosa.breakfast.ui.components.listRowMotion
import mk.bosa.breakfast.ui.components.AmountWithUnit
import mk.bosa.breakfast.ui.components.FormFields
import mk.bosa.breakfast.ui.components.SaveButton
import mk.bosa.breakfast.ui.components.rememberDraftField
import mk.bosa.breakfast.app.rememberDraftView
import mk.bosa.breakfast.ui.components.FoodAmountSheet
import mk.bosa.breakfast.ui.components.focusOnOpen
import mk.bosa.breakfast.ui.components.DecimalField
import mk.bosa.breakfast.ui.components.SubScreen
import mk.bosa.breakfast.domain.POSITIVE_AMOUNT_MESSAGE
import mk.bosa.breakfast.ui.screens.previewQuantityLine
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import mk.bosa.breakfast.app.DiarySession
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.Measure
import mk.bosa.breakfast.ui.screens.defaultServingCalories
import mk.bosa.breakfast.ui.components.DraftParts
import mk.bosa.breakfast.settings.SettingsState
import mk.bosa.breakfast.ui.components.DestructiveButton
import mk.bosa.breakfast.ui.components.Field
import mk.bosa.breakfast.ui.components.FoodRow
import mk.bosa.breakfast.ui.components.Hairline
import mk.bosa.breakfast.ui.components.SearchField
import mk.bosa.breakfast.ui.components.rememberMotion
import mk.bosa.breakfast.ui.theme.IconTarget
import mk.bosa.breakfast.ui.theme.LocalBreakfastColors

/**
 * New recipe and Edit, laid out as New food: the fields, then Save at the foot. The ingredients are typed like the Log
 * bar, and under them the same rows, "which one?" choices and "Not in your foods" the Log page shows; the magnifier opens
 * the ingredient search. Save waits until the recipe is whole ([recipeCanSave]).
 */
@Composable
internal fun RecipeFormScreen(form: RecipeForm, recipes: RecipeBook, session: DiarySession, settings: SettingsState) {
    val fields = form.fields
    val view = rememberDraftView(fields.ingredients, form.committed, form.pinsSnapshot(), session, settings, { form.ingredientsView() }, form::computeView)
    val editing = form.editing
    val canSave = recipeCanSave(fields, view.complete) && !session.busy
    SubScreen(
        if (form.editingRecipeId != null) "Edit recipe" else "New recipe", form::leave,
        bottom = { SaveButton(form::save, canSave) },
    ) {
        FormFields {
            Field("Name", fields.name, form::onName, required = true)
            PortionsField(form)
            TotalField(form)
            Column {
                IngredientsField(form)
                // The Log's rows and choices: a tapped choice pins its food; "Not in your foods" opens the search.
                DraftParts(view.draft, onChoose = form::chooseSuggestion, onResolve = form::resolveNotInFoods)
            }
            Field("Alias", fields.aliases, form::onAliases)
            if (editing != null) DestructiveButton("Delete recipe", { recipes.askDelete(editing) }, Modifier.fillMaxWidth(), outlined = true)
        }
    }
}

/**
 * The Total, laid out as New food's basis: the amount, then g and ml. It takes any weight or volume unit typed ("1.6 kg"),
 * and the choice shows the family it reads as.
 */
@Composable
private fun TotalField(form: RecipeForm) {
    val fields = form.fields
    val error = totalError(fields)
    val unit = totalOf(fields.total, fields.totalUnit)?.first ?: fields.totalUnit
    AmountWithUnit("Total", unit, form::onTotalUnit) { amountModifier ->
        Field(
            "Total", fields.total, form::onTotal, amountModifier, labelVisible = false,
            error = error,
            errorMessage = if (error) POSITIVE_AMOUNT_MESSAGE else null,
        )
    }
}

/** Portions: a typed value that is not a positive number shows as an error. */
@Composable
private fun PortionsField(form: RecipeForm) {
    val error = yieldError(form.fields.portions)
    DecimalField("Portions", form.fields.portions, form::onPortions, Modifier.fillMaxWidth(), error = error, errorMessage = if (error) POSITIVE_AMOUNT_MESSAGE else null)
}

/**
 * The ingredients, typed like the Log bar: Done finishes the last part, and the magnifier opens the ingredient search.
 */
@Composable
private fun IngredientsField(form: RecipeForm) {
    val colors = LocalBreakfastColors.current
    val field = rememberDraftField(form.fields.ingredients, form::onIngredients)
    Field(
        label = "Ingredients",
        value = field.value,
        onValueChange = field.onChange,
        singleLine = false,
        minLines = 2,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { form.commitIngredients() }),
        trailing = {
            IconButton(onClick = form::openSearch, modifier = Modifier.size(IconTarget)) {
                Icon(Icons.Outlined.Search, contentDescription = "Search foods", tint = colors.ink, modifier = Modifier.size(20.dp))
            }
        },
    )
}

/**
 * The ingredient search: the foods, the last logged first, as the Library lists them, filtered as it is typed. A food
 * tapped opens the amount sheet over it (or, for a "Not in your foods" part, takes its place). Nothing found: Open Food
 * Facts or a personal food, whose stored food comes back as that pick. Under them the USDA foods for the same text: a
 * tap stores one and it goes on as a stored food's pick does ([RecipeForm.pickCatalogueFood]).
 */
@Composable
internal fun RecipeIngredientSearch(form: RecipeForm, search: OffSearch, foodForm: FoodForm, usda: UsdaPartSearch) {
    val motion = rememberMotion()
    val usdaQuery = form.catalogueQuery
    val usdaRows = rememberUsdaRows(usda, usdaQuery, except = form.results)
    SubScreen("Search", form::closeSearch, scroll = false) {
        SearchField(
            form.query, form::onQuery, onClear = { form.onQuery("") }, label = "Search foods",
            modifier = Modifier.padding(top = 6.dp), fieldModifier = Modifier.focusOnOpen(),
        )
        Hairline(Modifier.padding(top = 12.dp))
        // What it finds goes into the recipe, so its review has no meal to log to, as a part of the Log's draft has none.
        if (form.noMatch) NoLocalMatchWithRows(usdaRows, form.query, search, foodForm, ProductReviewOrigin.LOG_PART, form::pickCatalogueFood)
        else LazyColumn(Modifier.weight(1f)) {
            items(form.results, key = { it.id }) { food ->
                FoodRow(
                    name = food.name,
                    quantityLine = previewQuantityLine(food.defaultAmount, food.defaultUnit),
                    calories = defaultServingCalories(food),
                    modifier = listRowMotion(motion),
                    onClick = { form.pick(food) },
                )
            }
            item { UsdaRows(usdaRows, usdaQuery, onPick = form::pickCatalogueFood) }
        }
    }
}

/** A food picked in the ingredient search: the Library's amount sheet, without a meal; its Add adds the ingredient. */
@Composable
internal fun IngredientAmountSheet(form: RecipeForm, food: FoodRecord) {
    FoodAmountSheet(
        food = food,
        startAmount = food.defaultAmount,
        startMeasure = Measure(food.defaultUnit),
        startMeal = null,
        onAdd = { amount, measure, _ -> form.addIngredient(food, amount, measure) },
        onDismiss = form::dismissAdding,
    )
}
