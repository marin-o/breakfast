package com.bosa.breakfast.recipes

import com.bosa.breakfast.ui.screens.yieldLine
import com.bosa.breakfast.ui.screens.macroFact
import com.bosa.breakfast.ui.screens.previewQuantityLine
import com.bosa.breakfast.ui.countLabel
import com.bosa.breakfast.app.DiaryChange
import com.bosa.breakfast.app.Notices
import com.bosa.breakfast.domain.TestFoods
import com.bosa.breakfast.domain.FoodMatcher
import com.bosa.breakfast.domain.FoodRecord
import com.bosa.breakfast.domain.LogPlanner
import com.bosa.breakfast.domain.MealEntry
import com.bosa.breakfast.domain.MealSlot
import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.domain.NutritionValues
import com.bosa.breakfast.domain.Recipe
import com.bosa.breakfast.domain.RecipeIngredient
import com.bosa.breakfast.today.OpenDiary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal val mince = RecipeIngredient("custom-mince", "Beef mince", 500.0, MeasureUnit.GRAM, 500.0, MeasureUnit.GRAM, NutritionValues(1250.0, 100.0, 0.0, 90.0))
internal val chili = Recipe("recipe-chili", "Chili", listOf("чили"), portions = 4.0, totalWeight = 1600.0, ingredients = listOf(mince))

/** The store the recipe owners write through, recording each write. */
internal class FakeRecipeStore(private val diary: OpenDiary) : RecipeStore {
    val calls = mutableListOf<String>()
    var foods: List<FoodRecord> = TestFoods.fixtures
    var failDelete = false
    var failSave = false
    var failLog = false
    override fun ingredientPlanner(pins: Map<String, FoodRecord>) = LogPlanner(FoodMatcher(foods), pins = pins)
    override suspend fun foodsMatching(query: String) = foods.filter { it.name.contains(query, ignoreCase = true) }
    override suspend fun cacheFood(food: FoodRecord): FoodRecord {
        if (failSave) throw IllegalStateException()
        calls += "store ${food.id}"
        foods = foods.filterNot { it.id == food.id } + food
        return food
    }
    override suspend fun saveRecipe(recipe: Recipe): Recipe {
        if (failSave) throw IllegalStateException()
        val stored = recipe.copy(id = recipe.id.ifBlank { "recipe-new" })
        calls += "save ${stored.id}"
        diary.recipes = diary.recipes.filterNot { it.id == stored.id } + stored
        return stored
    }
    override suspend fun deleteRecipe(id: String) {
        if (failDelete) throw IllegalStateException()
        calls += "delete $id"
        diary.recipes = diary.recipes.filterNot { it.id == id }
    }
    override suspend fun logRecipe(recipe: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String) {
        if (failLog) throw IllegalStateException()
        calls += "log ${recipe.id} $amount ${unit.name} $meal $dayKey"
    }
}

class RecipeBookTest {
    private val diary = OpenDiary().apply { recipes = listOf(chili) }
    private val store = FakeRecipeStore(diary)
    private val notices = Notices()
    private var confirmed = 0
    private val deleted = mutableListOf<String>()
    private val recipes = RecipeBook(
        diary.session, store, notices,
        selectedDay = { "2026-10-28" },
        confirmLogged = { confirmed++ },
        onDeleted = { deleted += it },
    )

    init { diary.session.refresh() }

    /** F12: a failed log says so, and "Logged!" does not show. */
    @Test fun aFailedLogSaysSoAndConfirmsNothing() {
        store.failLog = true
        recipes.beginLog(chili)
        recipes.log(chili, MealSlot.LUNCH, 1.0, MeasureUnit.PORTION)
        assertEquals(0, confirmed)
        assertEquals("Could not log this food.", notices.message)
        assertTrue(store.calls.isEmpty())
    }

    @Test fun theListIsOrderedByTheLastLogThenByName() {
        val soup = chili.copy(id = "recipe-soup", name = "Soup")
        val ajvar = chili.copy(id = "recipe-ajvar", name = "ajvar")
        diary.recipes = listOf(soup, chili, ajvar)
        diary.rows = listOf(MealEntry("e", soup.id, "Soup", 1.0, MeasureUnit.PORTION, 1.0, NutritionValues.ZERO, "", false, 9L))
        diary.session.refresh()
        assertEquals(listOf("recipe-soup", "recipe-ajvar", "recipe-chili"), recipes.ordered().map { it.id })
    }

    @Test fun thePageAndTheSheetShowOnlyWhileTheDiaryHasTheRecipe() {
        recipes.show()
        recipes.openRecipe(chili)
        recipes.beginLog(chili)
        assertEquals(chili, recipes.page)
        assertEquals(chili, recipes.logging)
        diary.recipes = emptyList()
        diary.session.refresh()
        assertNull(recipes.page)
        assertNull(recipes.logging)
        recipes.close()
        assertTrue(!recipes.open)
    }

    /** "+" then the sheet's Add: one entry, of the recipe as a food, to the chosen meal and day; "Logged!" once it commits. */
    @Test fun theSheetLogsTheRecipeAsOneEntryAndSaysLogged() {
        recipes.beginLog(chili)
        recipes.log(chili, MealSlot.DINNER, 1.5, MeasureUnit.PORTION)
        assertEquals(listOf("log recipe-chili 1.5 PORTION DINNER 2026-10-28"), store.calls)
        assertEquals(listOf<DiaryChange>(DiaryChange.FoodsLogged), diary.published)
        assertEquals(1, confirmed)
        assertNull("the sheet goes", recipes.logging)
    }

    @Test fun theSheetStartsFromTheLastLog() {
        val last = MealEntry("e2", chili.id, "Chili", 300.0, MeasureUnit.GRAM, 300.0, NutritionValues.ZERO, "", false, 9L)
        diary.rows = listOf(last.copy(id = "e1", savedAtEpochMs = 1L, enteredAmount = 2.0, enteredUnit = MeasureUnit.PORTION), last)
        diary.session.refresh()
        assertEquals(last, recipes.lastLoggedEntry(chili))
    }

    @Test fun deleteRecipeAsksFirstThenDeletesAndClosesItsPage() {
        recipes.openRecipe(chili)
        recipes.askDelete(chili)
        assertEquals(chili, recipes.deleteRequest)
        recipes.cancelDelete()
        assertTrue(store.calls.isEmpty())
        recipes.askDelete(chili)
        recipes.confirmDelete()
        assertEquals(listOf("delete recipe-chili"), store.calls)
        assertEquals(listOf<DiaryChange>(DiaryChange.RecipesChanged), diary.published)
        assertEquals(listOf("recipe-chili"), deleted)
        assertNull(recipes.page)
        assertNull(recipes.deleteRequest)
    }

    @Test fun aFailedDeleteSaysSoAndKeepsTheRecipe() {
        store.failDelete = true
        recipes.askDelete(chili)
        recipes.confirmDelete()
        assertEquals("Could not delete this recipe.", notices.message)
        assertTrue(deleted.isEmpty())
        assertEquals(listOf(chili), diary.session.recipes)
    }

    @Test fun aReplacedDiaryClosesWhatWasOpen() {
        recipes.openRecipe(chili)
        recipes.beginLog(chili)
        recipes.askDelete(chili)
        recipes.reset()
        assertNull(recipes.page)
        assertNull(recipes.logging)
        assertNull(recipes.deleteRequest)
    }

    @Test fun theListAndThePageReadAsTheyShouldShow() {
        assertEquals("1 recipe", countLabel(1, "recipe", "recipes"))
        assertEquals("3 recipes", countLabel(3, "recipe", "recipes"))
        assertEquals("0 recipes", countLabel(0, "recipe", "recipes"))
        assertEquals("4 portions, 1600 g", yieldLine(chili))
        assertEquals("1 portion", yieldLine(chili.copy(portions = 1.0, totalWeight = null)))
        assertEquals("900 g", yieldLine(chili.copy(portions = null, totalWeight = 900.0)))
        assertEquals("4 portions, 1600 g, 1500 ml", yieldLine(chili.copy(totalVolume = 1500.0)))
        assertEquals("1500 ml", yieldLine(chili.copy(portions = null, totalWeight = null, totalVolume = 1500.0)))
        assertEquals("100 g", previewQuantityLine(100.0, MeasureUnit.GRAM))
        assertEquals("1 portion", previewQuantityLine(1.0, MeasureUnit.PORTION))
        assertEquals("2 cups", previewQuantityLine(2.0, MeasureUnit.CUP))
        assertEquals("2 pieces", previewQuantityLine(2.0, MeasureUnit.PIECE))
        assertEquals("—" to "g", macroFact(com.bosa.breakfast.domain.MacroTotal(0.0, 0, 2)))
        assertEquals("12.5" to "g ?", macroFact(com.bosa.breakfast.domain.MacroTotal(12.5, 1, 1)))
        assertEquals("12" to "g", macroFact(com.bosa.breakfast.domain.MacroTotal(12.0, 2, 0)))
    }
}
