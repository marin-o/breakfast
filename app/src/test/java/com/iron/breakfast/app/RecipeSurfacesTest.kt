package com.iron.breakfast.app

import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.recipes.chili
import com.iron.breakfast.today.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Recipes tab and what opens over it, through [Surfaces] as the app wires it ([AppKit]): what is drawn, what system
 * Back does, and the top check, as [SurfacesTest] pins them for the other screens.
 */
class RecipeSurfacesTest {
    private val app = AppKit().apply { recipesAre(chili) }

    private fun shows(vararg surfaces: String) = assertEquals(surfaces.toList(), app.shown())

    @Test fun theRecipesTabIsABaseScreenAndBackGoesToDaily() {
        app.surfaces.openRecipes()
        shows("RECIPES")
        assertFalse(app.surfaces.baseCovered())
        assertTrue(app.back())
        shows("DAILY")
        assertFalse(app.recipes.open)
    }

    @Test fun theTabsReplaceEachOther() {
        app.openLibrary()
        app.surfaces.openRecipes()
        shows("RECIPES")
        assertFalse(app.library.open)
        app.openLibrary()
        shows("LIBRARY")
        assertFalse(app.recipes.open)
        app.surfaces.openRecipes()
        app.surfaces.closeRecipes()
        shows("DAILY")
        // From a meal, the Recipes tab replaces it; leaving goes to Daily.
        app.openMeal(MealSlot.LUNCH)
        app.surfaces.openRecipes()
        shows("RECIPES")
        assertNull(app.meal)
    }

    @Test fun settingsOpensOverTheRecipesTabAndBackReturnsThere() {
        app.surfaces.openRecipes()
        app.openSettings()
        shows("RECIPES", "SETTINGS")
        assertTrue(app.surfaces.baseCovered())
        app.back()
        shows("RECIPES")
        // Leaving the tab closes Settings with it.
        app.openSettings()
        app.surfaces.closeRecipes()
        shows("DAILY")
        assertFalse(app.settingsOpen)
    }

    @Test fun theRecipePageCoversTheTabAndBackClosesIt() {
        app.surfaces.openRecipes()
        app.recipes.openRecipe(chili)
        shows("RECIPES", "RECIPE")
        assertTrue(app.surfaces.baseCovered())
        app.back()
        shows("RECIPES")
        // Delete recipe asks over the page; the dialog takes Back in its own window.
        app.recipes.openRecipe(chili)
        app.recipes.askDelete(chili)
        shows("RECIPES", "RECIPE", "DELETE_RECIPE")
        app.recipes.confirmDelete()
        assertEquals(listOf("delete recipe recipe-chili"), app.calls)
        shows("RECIPES")
    }

    @Test fun aRowsPlusOpensTheAmountSheetAndItsAddLogsAndSaysLogged() {
        app.surfaces.openRecipes()
        app.recipes.beginLog(chili)
        shows("RECIPES", "RECIPE_LOG")
        app.recipes.log(chili, MealSlot.SNACKS, 2.0, com.iron.breakfast.domain.MeasureUnit.PORTION)
        assertEquals(listOf("log recipe recipe-chili 2.0 PORTION SNACKS"), app.calls)
        shows("RECIPES")
        assertEquals(LOGGED, app.confirmations.message)
    }

    @Test fun aRecipeEntrysOpenInLibraryOpensItsRecipe() {
        val eaten = entry("e1").copy(foodId = chili.id, foodName = chili.name)
        app.diaryHas(eaten)
        app.entryEdit.openDetail(eaten)
        shows("ENTRY_DETAIL")
        app.openLibraryFoodDetail(eaten)
        shows("RECIPES", "RECIPE")
        assertEquals(chili, app.recipes.page)
        // A deleted recipe's entry opens the tab alone.
        app.surfaces.closeRecipes()
        app.recipesAre()
        app.entryEdit.openDetail(eaten)
        app.openLibraryFoodDetail(eaten)
        shows("RECIPES")
    }

    @Test fun newRecipeOpensOverTheTabAndBackAsksOnlyWhenChanged() {
        app.surfaces.openRecipes()
        app.recipeForm.openNew()
        shows("RECIPES", "RECIPE_FORM")
        assertTrue(app.surfaces.baseCovered())
        app.back()
        shows("RECIPES")
        app.recipeForm.openNew()
        app.recipeForm.onName("Soup")
        app.back()
        shows("RECIPES", "RECIPE_FORM", "DISCARD_RECIPE")
        app.recipeForm.discard()
        shows("RECIPES")
    }

    @Test fun theIngredientSearchAndItsSheetStackOverTheFormAndBackReturnsToIt() {
        val kefir = com.iron.breakfast.resolve.food("custom-kefir", "Kefir")
        app.libraryFoods = listOf(kefir)
        app.surfaces.openRecipes()
        app.recipeForm.openNew()
        app.recipeForm.openSearch()
        shows("RECIPES", "RECIPE_FORM", "RECIPE_SEARCH")
        assertEquals(listOf(kefir), app.recipeForm.results)
        app.back()
        shows("RECIPES", "RECIPE_FORM")
        app.recipeForm.openSearch()
        app.recipeForm.pick(kefir)
        shows("RECIPES", "RECIPE_FORM", "RECIPE_SEARCH", "RECIPE_INGREDIENT_ADD")
        app.recipeForm.addIngredient(kefir, 200.0, com.iron.breakfast.domain.MeasureUnit.GRAM)
        shows("RECIPES", "RECIPE_FORM")
        assertEquals("200 g Kefir", app.recipeForm.fields.ingredients)
    }

    @Test fun editFromThePageThenSaveShowsThePageAgain() {
        app.surfaces.openRecipes()
        app.recipes.openRecipe(chili)
        // The page's Edit: the page closes, the form opens.
        app.recipes.closeRecipe()
        app.recipeForm.openEdit(chili)
        shows("RECIPES", "RECIPE_FORM")
        app.recipeForm.onPortions("8")
        app.recipeForm.save()
        assertEquals(listOf("save recipe recipe-chili"), app.calls)
        shows("RECIPES", "RECIPE")
        assertEquals(8.0, app.recipes.page!!.portions!!, 0.0)
    }

    @Test fun deleteRecipeFromItsFormAsksOverItAndClosesIt() {
        app.surfaces.openRecipes()
        app.recipeForm.openEdit(chili)
        app.recipes.askDelete(chili)
        shows("RECIPES", "RECIPE_FORM", "DELETE_RECIPE")
        app.recipes.confirmDelete()
        shows("RECIPES")
        assertFalse(app.recipeForm.open)
    }

    /** As the food form: an open recipe form comes back after the process was killed, over Daily. */
    @Test fun anOpenRecipeFormComesBackAfterTheProcessWasKilled() {
        app.surfaces.openRecipes()
        app.recipeForm.openNew()
        app.recipeForm.onName("Soup")
        app.recipeForm.onTotalWeight("900")
        // What the main thread's frame does on Android: the changes reach the mirrors.
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        val again = AppKit(app.savedState).apply { recipesAre(chili) }
        assertEquals(listOf("DAILY", "RECIPE_FORM"), again.shown())
        assertEquals("Soup", again.recipeForm.fields.name)
        again.recipeForm.close()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        assertEquals(listOf("DAILY"), again.shown())
        assertEquals(null, again.savedState.get<String>(RECIPE_FORM_KEY))
    }

    @Test fun aReplacedDiaryClosesAnEditOfARecipeItDoesNotHave() {
        app.surfaces.openRecipes()
        app.recipeForm.openEdit(chili)
        app.recipesAre()
        app.owners.session.write(DiaryChange.DiaryReplaced) {}
        shows("RECIPES")
    }

    /** A recipe entry is edited against its recipe while it exists: its units are the recipe's, its preview follows it. */
    @Test fun aRecipeEntryIsEditedAgainstItsRecipe() {
        val eaten = entry("e1").copy(foodId = chili.id, foodName = chili.name, enteredUnit = com.iron.breakfast.domain.MeasureUnit.PORTION)
        app.diaryHas(eaten)
        val food = app.entryEdit.foodOf(chili.id)!!
        assertEquals(chili.id, food.id)
        assertEquals(
            listOf(com.iron.breakfast.domain.MeasureUnit.PORTION, com.iron.breakfast.domain.MeasureUnit.GRAM),
            com.iron.breakfast.entry.entryUnits(food),
        )
        assertTrue(app.entryEdit.amountEditable(eaten))
        // 300 g of 1600 g of a 1250 kcal dish.
        assertEquals(234.375, app.entryEdit.preview(eaten, chili.id, 300.0, com.iron.breakfast.domain.MeasureUnit.GRAM)!!.nutrition.calories, 1e-9)
        // Deleted, the entry scales its own snapshot in its own unit only.
        app.recipesAre()
        assertNull(app.entryEdit.foodOf(chili.id))
        assertNull(app.entryEdit.preview(eaten, chili.id, 300.0, com.iron.breakfast.domain.MeasureUnit.GRAM))
        assertEquals(140.0, app.entryEdit.preview(eaten, chili.id, 2.0, com.iron.breakfast.domain.MeasureUnit.PORTION)!!.nutrition.calories, 1e-9)
    }

    @Test fun aReplacedDiaryClosesTheRecipePage() {
        app.surfaces.openRecipes()
        app.recipes.openRecipe(chili)
        app.owners.session.write(DiaryChange.DiaryReplaced) {}
        shows("RECIPES")
    }
}
