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

    @Test fun aReplacedDiaryClosesTheRecipePage() {
        app.surfaces.openRecipes()
        app.recipes.openRecipe(chili)
        app.owners.session.write(DiaryChange.DiaryReplaced) {}
        shows("RECIPES")
    }
}
