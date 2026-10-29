package com.iron.breakfast.recipes

import com.iron.breakfast.QueueDispatcher
import com.iron.breakfast.app.DiaryChange
import com.iron.breakfast.app.Notices
import com.iron.breakfast.domain.DraftRow
import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.Recipe
import com.iron.breakfast.resolve.food
import com.iron.breakfast.today.OpenDiary
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** New recipe and Edit over the production [RecipeForm]: typed ingredients, the search and its sheet, Back and Save. */
class RecipeFormTest {
    private val egg = FoodCatalog.fixtures.first { it.id == "egg" }
    private val oats = FoodCatalog.fixtures.first { it.id == "rolled_oats" }
    private val kefir = food("custom-kefir", "Kefir")
    private val diary = OpenDiary().apply { foods = FoodCatalog.fixtures + kefir }
    private val store = FakeRecipeStore(diary).apply { foods = FoodCatalog.fixtures + kefir }
    private val notices = Notices()
    private val io = QueueDispatcher()
    private val saved = mutableListOf<Recipe>()
    private val form = RecipeForm(
        diary.session, store, notices, diary.scope, io, Dispatchers.Unconfined, Dispatchers.Unconfined,
        onSaved = { saved += it },
    )

    init { diary.session.refresh() }

    private fun typed(name: String = "Breakfast bowl", portions: String = "2", weight: String = "", ingredients: String) {
        form.openNew()
        form.onName(name)
        form.onPortions(portions)
        form.onTotal(weight)
        form.onIngredients(ingredients)
        form.commitIngredients()
    }

    @Test fun ingredientsReadAsTheLogReadsThem() {
        typed(ingredients = "2 eggs, 40 g oats, ayranxyz")
        val view = form.ingredientsView()
        val rows = view.draft.rows
        assertEquals(listOf("egg", "rolled_oats"), rows.filterIsInstance<DraftRow.Entry>().map { it.resolution.food.id })
        assertEquals("Not in your foods", (rows.last() as DraftRow.Unresolved).text.let { "Not in your foods" })
        assertFalse("a part not in your foods keeps Save back", view.complete)
        form.onIngredients("2 eggs, 40 g oats")
        assertTrue(form.ingredientsView().complete)
    }

    @Test fun saveWritesTheRecipeWithItsIngredientsAndOpensIt() {
        typed(ingredients = "2 eggs, 40 g oats", weight = "350", portions = "")
        form.onAliases("bowl, чинија")
        form.save()
        assertEquals(listOf("save recipe-new"), store.calls)
        assertEquals(listOf<DiaryChange>(DiaryChange.RecipesChanged), diary.published)
        val recipe = saved.single()
        assertEquals("Breakfast bowl", recipe.name)
        assertEquals(listOf("bowl", "чинија"), recipe.aliases)
        assertNull(recipe.portions)
        assertEquals(350.0, recipe.totalWeight!!, 0.0)
        assertEquals(listOf("egg", "rolled_oats"), recipe.ingredients.map { it.foodId })
        assertEquals(2.0 to MeasureUnit.PIECE, recipe.ingredients[0].let { it.enteredAmount to it.enteredUnit })
        assertEquals(40.0, recipe.ingredients[1].enteredAmount, 0.0)
        assertTrue(recipe.ingredients[1].nutritionSnapshot.calories > 0.0)
        assertFalse("the form closes", form.open)
    }

    @Test fun aRecipeThatIsNotWholeDoesNotSave() {
        for (fields in listOf(
            RecipeFields(name = "", portions = "2", ingredients = "2 eggs"),
            RecipeFields(name = "Bowl", ingredients = "2 eggs"),
            RecipeFields(name = "Bowl", portions = "0", ingredients = "2 eggs"),
            RecipeFields(name = "Bowl", portions = "2", total = "x", ingredients = "2 eggs"),
            RecipeFields(name = "Bowl", portions = "2", ingredients = ""),
            RecipeFields(name = "Bowl", portions = "2", ingredients = "2 eggs, ayranxyz"),
        )) {
            form.openNew()
            form.onName(fields.name); form.onPortions(fields.portions); form.onTotal(fields.total); form.onIngredients(fields.ingredients)
            form.save()
            assertTrue("$fields", store.calls.isEmpty())
            assertTrue("$fields stays open", form.open)
        }
    }

    @Test fun aChoiceIsPinnedAsOnTheLog() {
        typed(ingredients = "40 g oat")
        form.onIngredients("40 g oat")
        val choices = form.ingredientsView().draft.choices
        assertTrue(choices.isNotEmpty())
        form.chooseSuggestion(oats)
        assertEquals("40 g ${oats.name}", form.fields.ingredients)
        assertEquals(oats, form.pinsSnapshot()["40 g ${oats.name}"])
    }

    /** The search: a food tapped opens the amount sheet; its Add appends the part, pinned, and the form shows again. */
    @Test fun aFoodPickedInTheSearchIsAddedThroughTheAmountSheet() {
        typed(ingredients = "2 eggs")
        form.openSearch()
        assertTrue(form.searchOpen)
        form.onQuery("kef")
        io.runAll()
        assertEquals(listOf("custom-kefir"), form.results.map { it.id })
        form.pick(kefir)
        assertEquals(kefir, form.adding)
        form.dismissAdding()
        assertNull(form.adding)
        assertTrue("the search stays", form.searchOpen)
        form.pick(kefir)
        form.addIngredient(kefir, 250.0, MeasureUnit.GRAM)
        assertEquals("2 eggs, 250 g Kefir", form.fields.ingredients)
        assertFalse(form.searchOpen)
        assertNull(form.adding)
        assertEquals(listOf("egg", "custom-kefir"), form.ingredientsView().draft.rows.filterIsInstance<DraftRow.Entry>().map { it.resolution.food.id })
    }

    @Test fun aNotInYourFoodsPartIsResolvedInTheSearchWithItsTypedAmount() {
        typed(ingredients = "2 eggs, 300 g kefyr")
        val row = form.ingredientsView().draft.rows.filterIsInstance<DraftRow.Unresolved>().single()
        form.resolveNotInFoods(row)
        assertTrue(form.searchOpen)
        assertEquals("kefyr", form.query)
        form.pick(kefir)
        assertNull("no sheet: the part keeps its amount", form.adding)
        assertFalse(form.searchOpen)
        assertEquals("2 eggs, Kefir 300 g", form.fields.ingredients)
        assertTrue(form.ingredientsView().complete)
    }

    @Test fun aSearchResultForAnOlderQueryIsDropped() {
        typed(ingredients = "2 eggs")
        form.openSearch()
        form.onQuery("kef")
        form.onQuery("egg")
        io.runAll()
        assertEquals(FoodCatalog.fixtures.filter { it.name.contains("egg", ignoreCase = true) }.map { it.id }.toSet(), form.results.map { it.id }.toSet())
        form.onQuery("kef")
        form.closeSearch()
        io.runAll()
        assertTrue(form.results.isEmpty())
    }

    @Test fun backAsksBeforeDiscardingAChangedForm() {
        form.openNew()
        form.leave()
        assertFalse("an unchanged form closes", form.open)
        typed(ingredients = "2 eggs")
        form.leave()
        assertTrue(form.discardOpen)
        form.keepEditing()
        assertTrue(form.open)
        form.leave()
        form.discard()
        assertFalse(form.open)
        assertTrue(store.calls.isEmpty())
    }

    /** Edit: the recipe's fields and ingredients come back, each pinned to its food; one whose food is gone keeps its numbers. */
    @Test fun editOpensTheRecipeAsItIsAndSavesItUnderItsId() {
        val gone = mince.copy(foodId = "custom-gone", foodName = "Old mince")
        val recipe = chili.copy(ingredients = listOf(mince.copy(foodId = "egg", foodName = egg.name, enteredAmount = 2.0, enteredUnit = MeasureUnit.PIECE), gone))
        diary.recipes = listOf(recipe)
        diary.session.refresh()
        form.openEdit(recipe)
        assertEquals("Chili", form.fields.name)
        assertEquals("4", form.fields.portions)
        assertEquals("1600", form.fields.total)
        assertEquals("чили", form.fields.aliases)
        assertEquals("2 piece ${egg.name}, 500 g Old mince", form.fields.ingredients)
        assertTrue(form.ingredientsView().complete)
        form.leave()
        assertFalse("unchanged: it closes", form.open)

        form.openEdit(recipe)
        form.onPortions("5")
        form.save()
        assertEquals(listOf("save recipe-chili"), store.calls)
        val stored = saved.single()
        assertEquals(5.0, stored.portions!!, 0.0)
        assertEquals(gone.nutritionSnapshot, stored.ingredients[1].nutritionSnapshot)
        assertEquals("Old mince", stored.ingredients[1].foodName)
    }

    @Test fun aDeletedRecipeOrFoodLeavesNothingThatCouldStoreItAgain() {
        diary.recipes = listOf(chili)
        diary.session.refresh()
        form.openEdit(chili)
        form.recipeDeleted("recipe-other")
        assertTrue(form.open)
        form.recipeDeleted(chili.id)
        assertFalse(form.open)

        typed(ingredients = "")
        form.addIngredient(kefir, 250.0, MeasureUnit.GRAM)
        assertTrue(form.pinsSnapshot().values.any { it.id == kefir.id })
        form.dropPinsOf(kefir.id)
        assertTrue(form.pinsSnapshot().isEmpty())

        form.openEdit(chili)
        diary.recipes = emptyList()
        diary.session.refresh()
        form.closeIfRecipeGone()
        assertFalse(form.open)
    }

    /** A replaced diary: pins to foods it does not have go, as the Log's do; one made of an ingredient's own numbers stays. */
    @Test fun aReplacedDiaryDropsThePinsOfFoodsItDoesNotHave() {
        val gone = mince.copy(foodId = "custom-gone", foodName = "Old mince")
        val recipe = chili.copy(ingredients = listOf(gone))
        diary.recipes = listOf(recipe)
        diary.session.refresh()
        form.openEdit(recipe)
        form.addIngredient(kefir, 250.0, MeasureUnit.GRAM)
        assertEquals(setOf("custom-gone", "custom-kefir"), form.pinsSnapshot().values.map { it.id }.toSet())
        diary.foods = FoodCatalog.fixtures
        diary.session.refresh()
        form.diaryReplaced()
        assertTrue(form.open)
        assertEquals(setOf("custom-gone"), form.pinsSnapshot().values.map { it.id }.toSet())
        // A New recipe keeps no pin to a food the new diary lacks either.
        typed(ingredients = "")
        form.addIngredient(egg, 2.0, MeasureUnit.PIECE)
        diary.foods = emptyList()
        diary.session.refresh()
        form.diaryReplaced()
        assertTrue(form.pinsSnapshot().isEmpty())
    }

    @Test fun anOpenFormComesBackAfterTheProcessWasKilled() {
        typed(ingredients = "2 eggs")
        form.chooseSuggestion(egg)
        val savedForm = form.savedForm()!!
        val again = RecipeForm(diary.session, store, notices, diary.scope, io, Dispatchers.Unconfined, Dispatchers.Unconfined, onSaved = {})
        again.restore(savedForm)
        assertTrue(again.open)
        assertEquals(form.fields, again.fields)
        assertEquals(form.pinsSnapshot(), again.pinsSnapshot())
        again.leave()
        assertTrue("still changed from what it opened with", again.discardOpen)
        form.close()
        assertNull(form.savedForm())
    }
}
