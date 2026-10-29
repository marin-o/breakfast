package com.iron.breakfast.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iron.breakfast.app.DiarySession
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.decimalInput
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.library.FoodAmountSheet
import com.iron.breakfast.library.defaultServingCalories
import com.iron.breakfast.log.DraftParts
import com.iron.breakfast.log.inputFieldFor
import com.iron.breakfast.log.syncedInputField
import com.iron.breakfast.settings.SettingsState
import com.iron.breakfast.ui.components.ButtonKind
import com.iron.breakfast.ui.components.DestructiveButton
import com.iron.breakfast.ui.components.Field
import com.iron.breakfast.ui.components.FoodRow
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.PrimaryButton
import com.iron.breakfast.ui.components.SearchField
import com.iron.breakfast.ui.components.TopBar
import com.iron.breakfast.ui.components.rememberMotion
import com.iron.breakfast.ui.pretty
import com.iron.breakfast.ui.theme.Gutter
import com.iron.breakfast.ui.theme.IconTarget
import com.iron.breakfast.ui.theme.LocalBreakfastColors

/**
 * New recipe and Edit, laid out as New food: the fields, then Save at the foot. The ingredients are typed like the Log
 * bar, and under them the same rows, "which one?" choices and "Not in your foods" the Log page shows; the magnifier opens
 * the ingredient search. Save waits until the recipe is whole ([recipeCanSave]).
 */
@Composable
internal fun RecipeFormScreen(form: RecipeForm, recipes: RecipeBook, session: DiarySession, settings: SettingsState) {
    val colors = LocalBreakfastColors.current
    val fields = form.fields
    // One planner pass per text, off the main thread, as on the Log page; the last view shows until the next is ready.
    var view by remember { mutableStateOf(form.ingredientsView()) }
    val pins = form.pinsSnapshot()
    LaunchedEffect(fields.ingredients, form.committed, pins, session.foods, session.ready, settings.countTyping, settings.learnFromPicks) {
        view = form.computeView(fields.ingredients, form.committed, pins)
    }
    val editing = form.editingRecipeId?.let { id -> session.recipes.firstOrNull { it.id == id } }
    val canSave = recipeCanSave(fields, view.complete) && !session.busy
    Surface(Modifier.fillMaxSize(), color = colors.bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(bottom = 12.dp)) {
                TopBar(if (form.editingRecipeId != null) "Edit recipe" else "New recipe", form::leave, Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                Column(Modifier.padding(horizontal = Gutter).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Field("Name", fields.name, form::onName, required = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        YieldField("Portions", fields.portions, form::onPortions, hint = null, Modifier.weight(1f))
                        YieldField("Total weight", fields.totalWeight, form::onTotalWeight, hint = "g", Modifier.weight(1f))
                    }
                    Column {
                        IngredientsField(form)
                        // The Log's rows and choices: a tapped choice pins its food; "Not in your foods" opens the search.
                        DraftParts(view.draft, onChoose = form::chooseSuggestion, onResolve = form::resolveNotInFoods)
                    }
                    Field("Alias", fields.aliases, form::onAliases)
                    if (editing != null) DestructiveButton("Delete recipe", { recipes.askDelete(editing) }, Modifier.fillMaxWidth(), outlined = true)
                }
            }
            Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = Gutter, vertical = 8.dp)) {
                PrimaryButton("Save", form::save, Modifier.padding(top = 8.dp), enabled = canSave, kind = ButtonKind.Constructive)
            }
        }
    }
}

/** Portions or Total weight: a decimal field whose typed value that is not a positive number shows as an error. */
@Composable
private fun YieldField(label: String, value: String, onValue: (String) -> Unit, hint: String?, modifier: Modifier) {
    val error = yieldError(value)
    Field(
        label, value, { onValue(it.decimalInput()) }, modifier,
        error = error,
        errorMessage = if (error) "Enter a positive amount." else null,
        hint = hint,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

/**
 * The ingredients, typed like the Log bar: Done finishes the last part, and the magnifier opens the ingredient search.
 * The field keeps its own caret; a change made from outside (a tapped choice, an added ingredient) moves it to the end.
 */
@Composable
private fun IngredientsField(form: RecipeForm) {
    val colors = LocalBreakfastColors.current
    val text = form.fields.ingredients
    var field by remember { mutableStateOf(inputFieldFor(text)) }
    LaunchedEffect(text) { field = syncedInputField(field, text) }
    Field(
        label = "Ingredients",
        value = field,
        onValueChange = { next ->
            // One logical line, as on the Log page: Done finishes it, so a newline is never typed.
            val typed = next.text.replace("\n", " ")
            field = next.copy(text = typed)
            form.onIngredients(typed)
        },
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
 * tapped opens the amount sheet over it (or, for a "Not in your foods" part, takes its place).
 */
@Composable
internal fun RecipeIngredientSearch(form: RecipeForm) {
    val colors = LocalBreakfastColors.current
    val motion = rememberMotion()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Surface(Modifier.fillMaxSize(), color = colors.bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = Gutter)) {
            TopBar("Search", form::closeSearch, Modifier.padding(top = 8.dp))
            SearchField(
                form.query, form::onQuery, onClear = { form.onQuery("") }, label = "Search foods",
                modifier = Modifier.padding(top = 6.dp), fieldModifier = Modifier.focusRequester(focus),
            )
            Hairline(Modifier.padding(top = 12.dp))
            LazyColumn(Modifier.weight(1f).imePadding()) {
                items(form.results, key = { it.id }) { food ->
                    FoodRow(
                        name = food.name,
                        quantityLine = "${food.defaultAmount.pretty()} ${food.defaultUnit.displayLabel()}",
                        calories = defaultServingCalories(food),
                        modifier = Modifier.animateItem(fadeInSpec = motion.listItem(), placementSpec = motion.listItem(), fadeOutSpec = motion.listItem()),
                        onClick = { form.pick(food) },
                    )
                }
            }
        }
    }
}

/** A food picked in the ingredient search: the Library's amount sheet, without a meal; its Add adds the ingredient. */
@Composable
internal fun IngredientAmountSheet(form: RecipeForm, food: FoodRecord) {
    FoodAmountSheet(
        food = food,
        startAmount = food.defaultAmount,
        startUnit = food.defaultUnit,
        startMeal = null,
        onAdd = { amount, unit, _ -> form.addIngredient(food, amount, unit) },
        onInvalid = {},
        onDismiss = form::dismissAdding,
    )
}
