package com.iron.breakfast.recipes

import com.iron.breakfast.domain.LatestSearch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iron.breakfast.app.DiaryChange
import com.iron.breakfast.app.DiarySession
import com.iron.breakfast.app.Notices
import com.iron.breakfast.domain.DraftRow
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.Measure
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.Recipe
import com.iron.breakfast.domain.libraryRecencyOrder
import com.iron.breakfast.domain.replaceDraftPart
import com.iron.breakfast.food.FoodFormBack
import com.iron.breakfast.food.foodFormBack
import com.iron.breakfast.log.LogDraftView
import com.iron.breakfast.log.logDraftViewOf
import com.iron.breakfast.log.rowStillInDraft
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the ingredients field shows for one text: the Log's rows and choices, and whether every part resolves. */
internal data class IngredientsView(val draft: LogDraftView, val complete: Boolean)

/** An open recipe form, for `SavedStateHandle`: what it edits, its fields, the fields it opened with, and its pins. */
@Serializable
internal data class SavedRecipeForm(
    val editingRecipeId: String?,
    val fields: RecipeFields,
    val baseline: RecipeFields,
    val committed: Boolean,
    val pins: Map<String, FoodRecord>,
)

/**
 * New recipe and Edit: one form over [fields].
 *
 * - **Fields:** the name, portions and one total in g or ml (at least one of the two), the aliases, and the ingredients, typed like the
 *   Log bar and read by the same planner, over the foods alone ([RecipeStore.ingredientPlanner]): the same rows, "which
 *   one?" choices and "Not in your foods". A choice or a pick pins its food to the part text, as on the Log, for as long
 *   as the form is open.
 * - **The ingredient search** ([searchOpen]): the foods, filtered by [query] ([results], read on [io] and applied on
 *   [main] while still current). A food picked there opens the amount sheet ([adding]); its Add appends the part
 *   ([addIngredient]). Opened from a "Not in your foods" row ([resolveNotInFoods]), a pick replaces that part instead,
 *   with its typed amount, as the Library does for the Log.
 * - **Back** asks before discarding a changed form ([discardOpen]), by the food form's rule ([foodFormBack]).
 * - **Save** writes the recipe through [session] ([DiaryChange.RecipesChanged]) and hands it to [onSaved].
 * - **Process death:** [savedForm] is what the coordinator mirrors into `SavedStateHandle` and [restore] puts back; the
 *   search and the sheet do not come back.
 */
internal class RecipeForm(
    private val session: DiarySession,
    private val store: RecipeStore,
    private val notices: Notices,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val main: CoroutineDispatcher,
    /** Where the ingredients are planned ([computeView]) and a save plans them: off the main thread in the app. */
    private val compute: CoroutineDispatcher,
    /** Saved: the recipe as stored. */
    private val onSaved: (Recipe) -> Unit,
) {
    var open by mutableStateOf(false); private set
    /** The recipe Edit is for; null for New recipe. */
    var editingRecipeId by mutableStateOf<String?>(null); private set
    var fields by mutableStateOf(RecipeFields()); private set
    private var baseline by mutableStateOf(RecipeFields())
    /** Done finished the ingredients, so their last part is no longer being typed. */
    var committed by mutableStateOf(false); private set
    // Observable, so a pin added without a text change still recomputes the rows.
    private val pins = mutableStateMapOf<String, FoodRecord>()
    var discardOpen by mutableStateOf(false); private set

    var searchOpen by mutableStateOf(false); private set
    var query by mutableStateOf(""); private set
    var results by mutableStateOf<List<FoodRecord>>(emptyList()); private set
    /** The food whose amount sheet is open, from the search. */
    var adding by mutableStateOf<FoodRecord?>(null); private set
    /** The "Not in your foods" part the search was opened for; a food picked there replaces it. */
    private var resolving: DraftRow.Unresolved? = null
    private val searching = LatestSearch<List<FoodRecord>>(scope, io, main)

    /** New recipe. */
    fun openNew() = begin(null, RecipeFields(), emptyMap())

    /** Edit [recipe]: its fields, its ingredients typed back and pinned to their foods ([recipeEdit]). */
    fun openEdit(recipe: Recipe) {
        val edit = recipeEdit(recipe, session.foods, store.ingredientPlanner())
        begin(recipe.id, edit.fields, edit.pins)
    }

    private fun begin(id: String?, start: RecipeFields, startPins: Map<String, FoodRecord>) {
        editingRecipeId = id
        fields = start
        baseline = start
        committed = id != null
        pins.clear()
        pins.putAll(startPins)
        discardOpen = false
        endSearch()
        open = true
    }

    fun onName(value: String) { fields = fields.copy(name = value) }
    fun onPortions(value: String) { fields = fields.copy(portions = value) }
    fun onTotal(value: String) { fields = fields.copy(total = value) }
    fun onTotalUnit(unit: MeasureUnit) { fields = fields.copy(totalUnit = unit) }
    fun onAliases(value: String) { fields = fields.copy(aliases = value) }

    /** The ingredients' typing. Clearing them also drops the foods picked for them. */
    fun onIngredients(value: String) {
        fields = fields.copy(ingredients = value)
        committed = false
        if (value.isBlank()) pins.clear()
    }

    /** The keyboard's Done on the ingredients: their last word is no longer being typed. */
    fun commitIngredients() { committed = true }

    /** The pins as they are now, for a view computed off the main thread. */
    fun pinsSnapshot(): Map<String, FoodRecord> = pins.toMap()

    /** The rows and choices for [text], as the Log page shows them, on [compute]. */
    suspend fun computeView(text: String, committed: Boolean, pins: Map<String, FoodRecord>): IngredientsView =
        withContext(compute) { ingredientsView(text, committed, pins) }

    fun ingredientsView(text: String = fields.ingredients, committed: Boolean = this.committed, pins: Map<String, FoodRecord> = pinsSnapshot()): IngredientsView {
        val planner = store.ingredientPlanner(pins)
        return IngredientsView(logDraftViewOf(planner, text, committed, session.ready), ingredientsComplete(planner.plan(text)))
    }

    /** A choice or a suggestion tapped: the part becomes the amount, unit and name it would save, pinned to that food. */
    fun chooseSuggestion(food: FoodRecord) {
        val picked = store.ingredientPlanner(pins.toMap()).choose(fields.ingredients, food, typing = !committed)
        picked.part?.let { pins[it] = food }
        fields = fields.copy(ingredients = picked.input)
        committed = false
    }

    /** A "Not in your foods" row: the search opens on its food text, and the food picked there takes its place. */
    fun resolveNotInFoods(row: DraftRow.Unresolved) {
        if (!rowStillInDraft(fields.ingredients, row)) return
        val part = fields.ingredients.substring(row.start, row.end)
        showSearch(store.ingredientPlanner(pins.toMap()).libraryQueryFor(part))
        resolving = row
    }

    /** The search affordance on the ingredients: all foods, the last logged first. */
    fun openSearch() = showSearch("")

    private fun showSearch(start: String) {
        resolving = null
        adding = null
        searchOpen = true
        onQuery(start)
    }

    /** The search's arrow and Back: the form as it was. */
    fun closeSearch() = endSearch()

    private fun endSearch() {
        searching.cancel()
        searchOpen = false
        query = ""
        results = emptyList()
        resolving = null
        adding = null
    }

    fun onQuery(value: String) {
        query = value
        if (!session.ready) return
        val entries = session.entries
        searching.launch(
            load = { libraryRecencyOrder(store.foodsMatching(value), entries) },
            wanted = { searchOpen },
        ) { results = it }
    }

    /**
     * A food tapped in the search: the amount sheet for it, as in the Library; opened for a "Not in your foods" part,
     * the food takes that part's place with its typed amount ([com.iron.breakfast.domain.LogPlanner.pickedText]).
     */
    fun pick(food: FoodRecord) {
        val row = resolving
        if (row == null) { adding = food; return }
        if (rowStillInDraft(fields.ingredients, row)) {
            val text = store.ingredientPlanner(pins.toMap()).pickedText(row.text, food)
            pins[text] = food
            fields = fields.copy(ingredients = replaceDraftPart(fields.ingredients, row.start, row.end, text))
        }
        endSearch()
    }

    /** The amount sheet went without adding: the search stays. */
    fun dismissAdding() { adding = null }

    /** The sheet's Add: [amount] [unit] of [food] is the last ingredient, pinned to it, and the form shows again. */
    fun addIngredient(food: FoodRecord, amount: Double, unit: MeasureUnit) = addIngredient(food, amount, Measure(unit))

    /** The sheet's Add: [amount] of [measure] of [food] ("2 slices") is the last ingredient, pinned to it. */
    fun addIngredient(food: FoodRecord, amount: Double, measure: Measure) {
        val planner = store.ingredientPlanner(pins.toMap())
        val text = planner.partText(food, amount, measure)
        pins[text] = food
        fields = fields.copy(ingredients = planner.withPartAdded(fields.ingredients, text))
        endSearch()
    }

    /** Back or the arrow: a changed form asks before it is discarded; while a save runs, nothing happens. */
    fun leave() {
        when (foodFormBack(saving = session.busy, dirty = fields != baseline)) {
            FoodFormBack.IGNORE -> Unit
            FoodFormBack.ASK -> discardOpen = true
            FoodFormBack.CLOSE -> close()
        }
    }

    fun keepEditing() { discardOpen = false }
    fun discard() { if (session.busy) return; close() }

    fun close() {
        open = false
        editingRecipeId = null
        fields = RecipeFields()
        baseline = RecipeFields()
        committed = false
        pins.clear()
        discardOpen = false
        endSearch()
    }

    /** Save. Refused (nothing written) while it cannot save: the button is not offered then ([recipeCanSave]). */
    fun save() {
        if (!session.canWrite) return
        val id = editingRecipeId
        val captured = fields
        val capturedPins = pins.toMap()
        session.write(
            change = { saved: Recipe? -> saved?.let { DiaryChange.RecipesChanged } },
            onSuccess = { saved ->
                if (saved == null) return@write
                close()
                onSaved(saved)
            },
            onFailure = { notices.show(it.message ?: "Could not save this recipe.") },
        ) {
            val plan = withContext(compute) { store.ingredientPlanner(capturedPins).plan(captured.ingredients) }
            if (!recipeCanSave(captured, ingredientsComplete(plan))) null
            else store.saveRecipe(recipeOf(id, captured, plan))
        }
    }

    /** Recipe [id] was deleted: an Edit of it closes, so it cannot store the recipe again. */
    fun recipeDeleted(id: String) { if (editingRecipeId == id) close() }

    /** Food [id] was deleted: a part pinned to it is matched again, so Save cannot keep that food. */
    fun dropPinsOf(foodId: String) { pins.entries.removeAll { it.value.id == foodId } }

    /** A restored or replaced diary: an Edit of a recipe it does not have has nothing to edit. */
    fun closeIfRecipeGone() {
        if (open && editingRecipeId?.let { id -> session.recipes.none { it.id == id } } == true) close()
    }

    /**
     * The diary was replaced (Import, Delete all): an Edit of a recipe it does not have closes, and a pin to a food it
     * does not have goes, as the Log's pins do, so the part is matched again in the new diary. A pin made of an
     * ingredient's own numbers ([snapshotFood]) stays: it never named a stored food.
     */
    fun diaryReplaced() {
        closeIfRecipeGone()
        val foodIds = session.foods.mapTo(HashSet()) { it.id }
        pins.entries.removeAll { it.value.id !in foodIds && it.value.category != SNAPSHOT_CATEGORY }
    }

    /** The open form for `SavedStateHandle`; null when closed. */
    fun savedForm(): SavedRecipeForm? = if (!open) null else SavedRecipeForm(editingRecipeId, fields, baseline, committed, pins.toMap())

    /** Puts back a form saved before the process was killed. */
    fun restore(form: SavedRecipeForm) {
        begin(form.editingRecipeId, form.baseline, form.pins)
        fields = form.fields
        committed = form.committed
    }
}
