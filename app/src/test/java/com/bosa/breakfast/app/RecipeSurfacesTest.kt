package com.bosa.breakfast.app

import com.bosa.breakfast.scan.ProductReviewOrigin
import com.bosa.breakfast.domain.DraftRow
import com.bosa.breakfast.resolve.food
import com.bosa.breakfast.domain.asFood
import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.domain.Measure
import com.bosa.breakfast.domain.TestFoods
import com.bosa.breakfast.domain.MealSlot
import com.bosa.breakfast.recipes.chili
import com.bosa.breakfast.today.entry
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
        app.recipes.log(chili, MealSlot.SNACKS, 2.0, com.bosa.breakfast.domain.MeasureUnit.PORTION)
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
        val kefir = com.bosa.breakfast.resolve.food("custom-kefir", "Kefir")
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
        app.recipeForm.addIngredient(kefir, 200.0, Measure(MeasureUnit.GRAM))
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
        app.recipeForm.onTotal("900")
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
        val eaten = entry("e1").copy(foodId = chili.id, foodName = chili.name, enteredUnit = com.bosa.breakfast.domain.MeasureUnit.PORTION)
        app.diaryHas(eaten)
        val food = app.entryEdit.foodOf(chili.id)!!
        assertEquals(chili.id, food.id)
        assertEquals(
            listOf(com.bosa.breakfast.domain.MeasureUnit.PORTION, com.bosa.breakfast.domain.MeasureUnit.GRAM),
            com.bosa.breakfast.entry.entryUnits(food),
        )
        assertTrue(app.entryEdit.amountEditable(eaten))
        // 300 g of 1600 g of a 1250 kcal dish.
        assertEquals(234.375, app.entryEdit.preview(eaten, chili.id, 300.0, com.bosa.breakfast.domain.MeasureUnit.GRAM)!!.nutrition.calories, 1e-9)
        // With a total volume too, the edit offers ml: 300 ml of 1500 ml.
        app.recipesAre(chili.copy(totalVolume = 1500.0))
        val withVolume = app.entryEdit.foodOf(chili.id)!!
        assertEquals(
            listOf(com.bosa.breakfast.domain.MeasureUnit.PORTION, com.bosa.breakfast.domain.MeasureUnit.GRAM, com.bosa.breakfast.domain.MeasureUnit.MILLILITER),
            com.bosa.breakfast.entry.entryUnits(withVolume),
        )
        assertEquals(250.0, app.entryEdit.preview(eaten, chili.id, 300.0, com.bosa.breakfast.domain.MeasureUnit.MILLILITER)!!.nutrition.calories, 1e-9)
        // Deleted, the entry scales its own snapshot in its own unit only.
        app.recipesAre()
        assertNull(app.entryEdit.foodOf(chili.id))
        assertNull(app.entryEdit.preview(eaten, chili.id, 300.0, com.bosa.breakfast.domain.MeasureUnit.GRAM))
        assertEquals(140.0, app.entryEdit.preview(eaten, chili.id, 2.0, com.bosa.breakfast.domain.MeasureUnit.PORTION)!!.nutrition.calories, 1e-9)
    }

    @Test fun aReplacedDiaryClosesTheRecipePage() {
        app.surfaces.openRecipes()
        app.recipes.openRecipe(chili)
        app.owners.session.write(DiaryChange.DiaryReplaced) {}
        shows("RECIPES")
    }

    /** F4: a Log draft's pin holds the recipe as it was picked; after an edit it logs the new numbers. */
    @Test fun aLogPinToARecipeFollowsItsEdit() {
        val halved = chili.copy(portions = 8.0)
        app.plannerFoods = TestFoods.fixtures + chili.asFood()
        app.log.onDraft("1 portion chil")
        app.log.chooseSuggestion(chili.asFood())
        assertEquals(chili.asFood(), app.log.pinsSnapshot().values.single())
        app.owners.session.write(DiaryChange.RecipesChanged) { app.diary.recipes = listOf(halved) }
        assertEquals(halved.asFood(), app.log.pinsSnapshot().values.single())
    }

    /** Renamed, the draft takes the new name and the part stays that recipe. */
    @Test fun aLogPinToARecipeRenamedFollowsIt() {
        app.plannerFoods = TestFoods.fixtures + chili.asFood()
        app.log.onDraft("1 portion chil")
        app.log.chooseSuggestion(chili.asFood())
        val named = app.log.draft
        val stew = chili.copy(name = "Stew")
        app.owners.session.write(DiaryChange.RecipesChanged) { app.diary.recipes = listOf(stew) }
        assertEquals(named.replace(chili.name, "Stew"), app.log.draft)
        assertEquals(listOf(stew.asFood()), app.log.pinsSnapshot().values.toList())
    }

    /** F4: the recipe form's pin to a food follows that food's edit too, so Save stores its new numbers. */
    @Test fun aRecipeFormPinFollowsItsFoodsEdit() {
        val kefir = food("custom-kefir", "Kefir")
        val richer = kefir.copy(nutrition = kefir.nutrition.copy(values = kefir.nutrition.values.copy(calories = 999.0)))
        app.diary.foods = listOf(kefir)
        app.owners.session.refresh()
        app.recipeForm.openNew()
        app.recipeForm.addIngredient(kefir, 250.0, Measure(MeasureUnit.GRAM))
        app.owners.session.write(DiaryChange.FoodsChanged) { app.diary.foods = listOf(richer) }
        assertEquals(richer, app.recipeForm.pinsSnapshot().values.single())
    }

    private fun searchingANewFood() {
        app.plannerFoods = TestFoods.fixtures
        app.libraryFoods = emptyList()
        app.recipeForm.openNew()
        app.recipeForm.onIngredients("300 g kefyr")
        app.recipeForm.commitIngredients()
        app.recipeForm.resolveNotInFoods(app.recipeForm.ingredientsView().draft.rows.filterIsInstance<DraftRow.Unresolved>().single())
    }

    /** F3: a part in no food says so, and the way on is the search's: not a blank list. */
    @Test fun anIngredientSearchThatFindsNothingOffersTheWayOn() {
        searchingANewFood()
        assertEquals("kefyr", app.recipeForm.query)
        assertTrue(app.recipeForm.noMatch)
        app.recipeForm.onQuery("")
        assertFalse(app.recipeForm.noMatch)
    }

    /** F3: a food made there comes back as the part's pick, with its typed amount; nothing is logged. */
    @Test fun aPersonalFoodMadeFromTheIngredientSearchIsThePartsPick() {
        searchingANewFood()
        app.foodForm.open("Kefyr")
        shows("DAILY", "RECIPE_FORM", "RECIPE_SEARCH", "FOOD_FORM")
        app.foodForm.draft.calories = "100"
        app.foodForm.save()
        // No notice: nothing covers the form.
        shows("DAILY", "RECIPE_FORM")
        assertEquals("Kefyr 300 g", app.recipeForm.fields.ingredients)
        assertEquals("kefyr", app.recipeForm.pinsSnapshot().values.single().id)
        assertEquals(listOf("save food Kefyr"), app.calls)
    }

    /** The origin stays through Rescan, so that sheet shows Add and no meal too. */
    @Test fun aRescanFromTheIngredientSearchKeepsTheSheetWithoutAMeal() {
        val scanned = food("off-kefyr", "Kefyr")
        app.barcodes["4006381333931"] = scanned
        searchingANewFood()
        app.search.open("kefyr", reviewOrigin = ProductReviewOrigin.LOG_PART)
        app.search.choose(food("off-other", "Other"))
        app.review.rescan()
        app.scan.onBarcode("4006381333931")
        assertEquals(ProductReviewOrigin.LOG_PART, app.review.origin)
        app.review.addToLog()
        assertEquals("off-kefyr", app.recipeForm.pinsSnapshot().values.single().id)
        assertTrue(app.calls.none { it.startsWith("review log") })
    }

    /** A food made from the magnifier: the sheet goes without adding, and the search must not say it does not exist. */
    @Test fun dismissingTheSheetForANewFoodSearchesAgain() {
        app.plannerFoods = TestFoods.fixtures
        app.libraryFoods = emptyList()
        app.recipeForm.openNew()
        app.recipeForm.openSearch()
        app.recipeForm.onQuery("kefyr")
        assertTrue(app.recipeForm.noMatch)
        app.foodForm.open("Kefyr")
        app.foodForm.draft.calories = "100"
        app.foodForm.save()
        assertEquals("kefyr", app.recipeForm.adding?.id)
        app.libraryFoods = listOf(food("kefyr", "Kefyr"))
        app.recipeForm.dismissAdding()
        assertFalse(app.recipeForm.noMatch)
        assertEquals(listOf("kefyr"), app.recipeForm.results.map { it.id })
    }

    /** A review stores the food it logs over the stored one: a Log pin to it follows, so Add logs the corrected kcal. */
    @Test fun aLogPinFollowsAKcalCorrectedThroughAScanReview() {
        val kefir = food("custom-kefir", "Kefir")
        val app = AppKit(foods = listOf(kefir))
        app.plannerFoods = listOf(kefir)
        app.log.onDraft("200 g kef")
        app.log.chooseSuggestion(kefir)
        val before = app.log.draftView().logged.single().calculation.nutrition.calories
        app.review.choose(kefir, fromScan = true, origin = ProductReviewOrigin.MEAL)
        app.review.onCalories("999")
        app.review.add(MealSlot.LUNCH)
        val after = app.log.pinsSnapshot().values.single()
        assertEquals(999.0, after.nutrition.values.calories, 0.0)
        assertTrue(app.log.draftView().logged.single().calculation.nutrition.calories > before)
    }

    @Test fun aQueuedPartsWriteRefreshesPinsToo() {
        val kefir = food("custom-kefir", "Kefir")
        val richer = kefir.copy(nutrition = kefir.nutrition.copy(values = kefir.nutrition.values.copy(calories = 999.0)))
        val app = AppKit(foods = listOf(kefir))
        app.plannerFoods = listOf(kefir)
        app.log.onDraft("200 g kef")
        app.log.chooseSuggestion(kefir)
        app.owners.session.write(DiaryChange.QueuedPartLogged) { app.diary.foods = listOf(richer) }
        assertEquals(richer, app.log.pinsSnapshot().values.single())
    }

    /** F3: closing what it opened comes back to the search, with the part still waiting. */
    @Test fun leavingThePersonalFoodFormReturnsToTheIngredientSearch() {
        searchingANewFood()
        app.foodForm.open("Kefyr")
        app.foodForm.close()
        shows("DAILY", "RECIPE_FORM", "RECIPE_SEARCH")
        assertEquals("300 g kefyr", app.recipeForm.fields.ingredients)
    }

    /** F3: an Open Food Facts product reviewed from there comes back the same way. */
    @Test fun aReviewedProductFromTheIngredientSearchIsThePartsPick() {
        searchingANewFood()
        app.search.open("kefyr", reviewOrigin = ProductReviewOrigin.LOG_PART)
        app.search.choose(food("off-kefyr", "Kefyr"))
        assertTrue("RECIPE_SEARCH" in app.shown() && "PRODUCT_REVIEW" in app.shown())
        assertEquals("the sheet shows Add and no meal", ProductReviewOrigin.LOG_PART, app.review.origin)
        // What that sheet's Add calls.
        app.review.addToLog()
        shows("DAILY", "RECIPE_FORM")
        assertEquals("off-kefyr", app.recipeForm.pinsSnapshot().values.single().id)
        assertEquals("stored, and no diary entry written", listOf("review store off-kefyr"), app.calls)
        assertEquals("the Log's draft is not touched", "", app.log.draft)
    }
}
