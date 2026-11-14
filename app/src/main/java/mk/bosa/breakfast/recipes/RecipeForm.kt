package mk.bosa.breakfast.recipes

import mk.bosa.breakfast.domain.DraftPins
import mk.bosa.breakfast.domain.LatestSearch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import mk.bosa.breakfast.app.DiaryChange
import mk.bosa.breakfast.app.DiarySession
import mk.bosa.breakfast.app.Notices
import mk.bosa.breakfast.domain.DraftRow
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.Measure
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.Recipe
import mk.bosa.breakfast.domain.ShorthandParser
import mk.bosa.breakfast.library.librarySearchText
import mk.bosa.breakfast.domain.libraryRecencyOrder
import mk.bosa.breakfast.domain.replaceDraftPart
import mk.bosa.breakfast.domain.FormBack
import mk.bosa.breakfast.domain.formBack
import mk.bosa.breakfast.domain.LogDraftView
import mk.bosa.breakfast.domain.logDraftViewOf
import mk.bosa.breakfast.domain.rowStillInDraft
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
 *   with its typed amount, as the Library does for the Log. With nothing found ([noMatch]), Open Food Facts or a personal
 *   food are the way on, and what they store comes back as that pick.
 * - **Back** asks before discarding a changed form ([discardOpen]), by the food form's rule ([formBack]).
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
    /** A USDA row tapped: that tap counts as a pick. */
    private val onCataloguePicked: (foodId: String) -> Unit = {},
) {
    private val parser = ShorthandParser()
    var open by mutableStateOf(false); private set
    /** The recipe Edit is for; null for New recipe. */
    var editingRecipeId by mutableStateOf<String?>(null); private set
    var fields by mutableStateOf(RecipeFields()); private set
    private var baseline by mutableStateOf(RecipeFields())
    /** Done finished the ingredients, so their last part is no longer being typed. */
    var committed by mutableStateOf(false); private set
    // Observable, so a pin added without a text change still recomputes the rows.
    private val pins = DraftPins()
    var discardOpen by mutableStateOf(false); private set

    var searchOpen by mutableStateOf(false); private set
    var query by mutableStateOf(""); private set
    var results by mutableStateOf<List<FoodRecord>>(emptyList()); private set
    /** The query [results] answer; until then an empty list says nothing. */
    private var answered by mutableStateOf<String?>(null)
    /** The search looked and found nothing: the way on is Open Food Facts or a personal food. */
    val noMatch: Boolean get() = searchOpen && query.isNotBlank() && answered == query && results.isEmpty()
    /** The food whose amount sheet is open, from the search. */
    var adding by mutableStateOf<FoodRecord?>(null); private set
    /** The "Not in your foods" part the search was opened for; a food picked there replaces it. */
    private var resolving: DraftRow.Unresolved? = null
    private val searching = LatestSearch<List<FoodRecord>>(scope, io, main)

    /** New recipe. */
    fun openNew() = begin(null, RecipeFields(), emptyMap())

    /** The recipe Edit is for, while the diary has it. */
    val editing: Recipe? get() = editingRecipeId?.let { id -> session.recipes.firstOrNull { it.id == id } }

    /** Edit [recipe]: its fields, its ingredients typed back and pinned to their foods ([recipeEdit]). */
    fun openEdit(recipe: Recipe) {
        val edit = recipeEdit(recipe, session.foods, store.ingredientPlanner())
        begin(recipe.id, edit.fields, edit.pins)
    }

    /** The form on [start], with [startPins] on its text; [opened] is what Back compares it with. */
    private fun begin(id: String?, start: RecipeFields, startPins: Map<String, FoodRecord>, opened: RecipeFields = start, done: Boolean = id != null) {
        editingRecipeId = id
        fields = start
        baseline = opened
        committed = done
        pins.restore(start.ingredients, startPins)
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
        setIngredients(value)
        committed = false
        if (value.isBlank()) pins.clear()
    }

    /** The ingredients box losing focus: their last word is no longer being typed. */
    fun commitIngredients() { committed = true }

    /** The pins as they are now, for a view computed off the main thread. */
    fun pinsSnapshot(): Map<String, FoodRecord> = pins.keys(fields.ingredients, store::ingredientPlanner)

    private fun setIngredients(value: String) {
        pins.edited(fields.ingredients, value)
        fields = fields.copy(ingredients = value)
    }

    /** The rows and choices for [text], as the Log page shows them, on [compute]. */
    suspend fun computeView(text: String, committed: Boolean, pins: Map<String, FoodRecord>): IngredientsView =
        withContext(compute) { ingredientsView(text, committed, pins) }

    fun ingredientsView(text: String = fields.ingredients, committed: Boolean = this.committed, pins: Map<String, FoodRecord> = pinsSnapshot()): IngredientsView {
        val planner = store.ingredientPlanner(pins)
        return IngredientsView(logDraftViewOf(planner, text, committed, session.ready), ingredientsComplete(planner.plan(text)))
    }

    /** A choice or a suggestion tapped: the part becomes the amount, unit and name it would save, pinned to that food. */
    fun chooseSuggestion(food: FoodRecord) {
        val picked = store.ingredientPlanner(pinsSnapshot()).choose(fields.ingredients, food, typing = !committed)
        val before = fields.ingredients
        setIngredients(picked.input)
        picked.part?.let { pins.add(picked.input, it, food, picked.input.commonPrefixWith(before).length.coerceAtMost(picked.input.lastIndexOf(it))) }
        committed = false
    }

    /** A "Not in your foods" row: the search opens on its food text, and the food picked there takes its place. */
    fun resolveNotInFoods(row: DraftRow.Unresolved) {
        if (!rowStillInDraft(fields.ingredients, row)) return
        val part = fields.ingredients.substring(row.start, row.end)
        showSearch(store.ingredientPlanner(pinsSnapshot()).libraryQueryFor(part))
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
        answered = null
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
        ) { results = it; answered = value }
    }

    /**
     * A food tapped in the search: the amount sheet for it, as in the Library; opened for a "Not in your foods" part,
     * the food takes that part's place with its typed amount ([mk.bosa.breakfast.domain.LogPlanner.pickedText]). A
     * food made or reviewed from the search ([noMatch]) comes back here too.
     */
    fun pick(food: FoodRecord) {
        val row = resolving
        if (row == null) { adding = food; return }
        if (rowStillInDraft(fields.ingredients, row)) {
            val text = store.ingredientPlanner(pinsSnapshot()).pickedText(row.text, food)
            setIngredients(replaceDraftPart(fields.ingredients, row.start, row.end, text))
            pins.add(fields.ingredients, text, food, row.start)
        }
        endSearch()
    }

    /** The search's text as the USDA catalogue is asked it: the food text, without a typed amount. */
    val catalogueQuery: String get() = librarySearchText(query, parser)

    /**
     * A USDA row tapped in the search: the food is stored, then taken as a stored one is. An amount typed in the search
     * ("2 eggs") makes it the last ingredient at once; with none, [pick] asks.
     */
    fun pickCatalogueFood(food: FoodRecord) {
        if (!session.canWrite) return
        val typedText = query
        session.write(
            change = DiaryChange.FoodsChanged,
            onSuccess = { stored ->
                if (searchOpen) {
                    onCataloguePicked(stored.id)
                    val planner = store.ingredientPlanner(pinsSnapshot())
                    if (resolving == null && planner.typed(typedText).amount != null) {
                        val (amount, measure) = planner.quantityOf(typedText, stored)
                        addIngredient(stored, amount, measure)
                    } else pick(stored)
                }
            },
            onFailure = { notices.show(it.message ?: "Could not save this food.") },
        ) { store.cacheFood(food) }
    }

    /** The amount sheet went without adding: the search stays, and reads again, since the food may be new. */
    fun dismissAdding() {
        adding = null
        if (searchOpen) onQuery(query)
    }

    /** The sheet's Add: [amount] of [measure] of [food] ("2 slices") is the last ingredient, pinned to it. */
    fun addIngredient(food: FoodRecord, amount: Double, measure: Measure) {
        val planner = store.ingredientPlanner(pinsSnapshot())
        val text = planner.partText(food, amount, measure)
        setIngredients(planner.withPartAdded(fields.ingredients, text))
        pins.add(fields.ingredients, text, food, fields.ingredients.length - text.length)
        endSearch()
    }

    /** Back or the arrow: a changed form asks before it is discarded; while a save runs, nothing happens. */
    fun leave() {
        when (formBack(saving = session.busy, dirty = fields != baseline)) {
            FormBack.IGNORE -> Unit
            FormBack.ASK -> discardOpen = true
            FormBack.CLOSE -> close()
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
        val capturedPins = pinsSnapshot()
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
            else store.saveRecipe(recipeOf(id, captured, plan, kept = session.recipes.firstOrNull { it.id == id }))
        }
    }

    /** Recipe [id] was deleted: an Edit of it closes, so it cannot store the recipe again. */
    fun recipeDeleted(id: String) { if (editingRecipeId == id) close() }

    /** Foods changed: a pin holds the food as it was picked, so Save stores what it is now; a new name replaces the old in the text. */
    fun refreshPins(current: List<FoodRecord>) { fields = fields.copy(ingredients = pins.refresh(fields.ingredients, current)) }

    /** Food [id] was deleted: a part pinned to it is matched again, so Save cannot keep that food. */
    fun dropPinsOf(foodId: String) { pins.dropFood(foodId) }

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
        pins.dropWhere { it.id !in foodIds && it.category != SNAPSHOT_CATEGORY }
    }

    /** The open form for `SavedStateHandle`; null when closed. */
    fun savedForm(): SavedRecipeForm? = if (!open) null else SavedRecipeForm(editingRecipeId, fields, baseline, committed, pinsSnapshot())

    /** Puts back a form saved before the process was killed. */
    fun restore(form: SavedRecipeForm) {
        begin(form.editingRecipeId, form.fields, form.pins, form.baseline, form.committed)
    }
}
