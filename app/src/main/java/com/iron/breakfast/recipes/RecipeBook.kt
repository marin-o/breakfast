package com.iron.breakfast.recipes

import com.iron.breakfast.domain.lastLoggedEntry
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iron.breakfast.app.DiaryChange
import com.iron.breakfast.app.DiarySession
import com.iron.breakfast.app.Notices
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.LogPlanner
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.Recipe
import com.iron.breakfast.domain.asFood
import com.iron.breakfast.domain.recipeRecencyOrder

/** What the Recipes tab and the recipe form read and write; the view model adapts `BreakfastEngine`. */
internal interface RecipeStore {
    /** The planner for ingredients: the stored foods only (a recipe is never an ingredient), with [pins] resolving their parts. */
    fun ingredientPlanner(pins: Map<String, FoodRecord> = emptyMap()): LogPlanner
    /** The foods whose name matches [query], for the ingredient search. */
    suspend fun foodsMatching(query: String): List<FoodRecord>
    /** Saves [recipe] (a new one has a blank id) and returns it as stored. */
    suspend fun saveRecipe(recipe: Recipe): Recipe
    /** Deletes the recipe for good; entries keep their own name and numbers. */
    suspend fun deleteRecipe(id: String)
    /** Logs [amount] [unit] of [recipe] (as a food, [asFood]) to [meal] on [dayKey], as one entry with its own snapshot. */
    suspend fun logRecipe(recipe: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String)
}

/**
 * The Recipes tab: whether it is [open], the list ([ordered]), the recipe page ([page]), logging one through the amount
 * sheet ([logging], [log]: one entry, "Logged!"), and Delete recipe, which asks first ([deleteRequest]) and deletes for
 * good. The page, the sheet and the question name a recipe by id and show only while the diary still has it.
 *
 * Writes go through [session]; [selectedDay] is the day a log lands on.
 */
internal class RecipeBook(
    private val session: DiarySession,
    private val store: RecipeStore,
    private val notices: Notices,
    private val selectedDay: () -> String,
    /** A recipe was logged to the meal and day the user chose: "Logged!" (see `Confirmations`). */
    private val confirmLogged: () -> Unit,
    /** Recipe [id] was deleted: a form editing it closes, and a Log part pinned to it is matched again. */
    private val onDeleted: (id: String) -> Unit,
) {
    var open by mutableStateOf(false); private set
    private var pageId by mutableStateOf<String?>(null)
    private var loggingId by mutableStateOf<String?>(null)
    private var deleteId by mutableStateOf<String?>(null)

    /** The recipe page on show. */
    val page: Recipe? get() = find(pageId)
    /** The recipe whose amount sheet is open (its row's "+"). */
    val logging: Recipe? get() = find(loggingId)
    /** The recipe "Delete recipe" asks about. */
    val deleteRequest: Recipe? get() = find(deleteId)

    private fun find(id: String?): Recipe? = id?.let { wanted -> session.recipes.firstOrNull { it.id == wanted } }

    /** The list: the most recently logged recipe first, then by name, as the Library orders foods. */
    fun ordered(): List<Recipe> = recipeRecencyOrder(session.recipes, session.entries)

    fun show() { open = true }

    /** Leaves the tab and everything open on it. */
    fun close() {
        open = false
        reset()
    }

    fun openRecipe(recipe: Recipe) { pageId = recipe.id }
    fun closeRecipe() { pageId = null }

    /** A row's "+": the amount sheet with the recipe's units and the meal choice. */
    fun beginLog(recipe: Recipe) { loggingId = recipe.id }
    fun dismissLog() { loggingId = null }

    /** The entry this recipe was last logged as, whose amount the sheet starts with. */
    fun lastLoggedEntry(recipe: Recipe): MealEntry? = lastLoggedEntry(session.entries, recipe.id)

    /**
     * The sheet's Add: [amount] [unit] of [recipe] to [meal] on the selected day, as one entry with its own snapshot,
     * then "Logged!". The sheet goes at once, as the Library's does; a failure says so.
     */
    fun log(recipe: Recipe, meal: MealSlot, amount: Double, unit: MeasureUnit) {
        val day = selectedDay()
        val started = session.write(
            change = DiaryChange.FoodsLogged,
            onSuccess = { _: Unit -> confirmLogged() },
            onFailure = { notices.show(it.message ?: "Could not log this food.") },
        ) { store.logRecipe(recipe.asFood(), amount, unit, meal, day) }
        if (started != null) loggingId = null
    }

    /** "Delete recipe" on its page or its form: the question opens first. */
    fun askDelete(recipe: Recipe) { deleteId = recipe.id }
    fun cancelDelete() { deleteId = null }

    /** The question's "Delete recipe": the recipe goes for good; past entries keep their own name and numbers. No Undo. */
    fun confirmDelete() {
        val id = deleteId ?: return
        // A tap while another write runs is ignored, as elsewhere; the question stays for another tap.
        if (!session.canWrite) return
        deleteId = null
        session.write(
            change = DiaryChange.RecipesChanged,
            onSuccess = { _: Unit ->
                if (pageId == id) pageId = null
                if (loggingId == id) loggingId = null
                onDeleted(id)
            },
            onFailure = { notices.show(it.message ?: "Could not delete this recipe.") },
        ) { store.deleteRecipe(id) }
    }

    /** The diary was replaced: nothing open here refers to it any more. */
    fun reset() {
        pageId = null
        loggingId = null
        deleteId = null
    }
}
