package com.bosa.breakfast.app

import com.bosa.breakfast.domain.MealSlot
import com.bosa.breakfast.ui.components.BaseMove
import com.bosa.breakfast.ui.components.NavTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceTransitionsTest {
    private fun change(before: List<Surface>, now: List<Surface>) = surfaceChange(before, now)

    @Test fun aScreenOverTheBaseGlidesInAndOut() {
        assertEquals(SurfaceChange(setOf(Surface.LOG), emptySet(), emptySet()), change(listOf(Surface.DAILY), listOf(Surface.DAILY, Surface.LOG)))
        assertEquals(SurfaceChange(emptySet(), setOf(Surface.LOG), emptySet()), change(listOf(Surface.DAILY, Surface.LOG), listOf(Surface.DAILY)))
    }

    @Test fun theBaseScreensAreOneSlotAndNeverGlide() {
        assertEquals(SurfaceChange(emptySet(), emptySet(), emptySet()), change(listOf(Surface.DAILY), listOf(Surface.MEAL)))
        assertEquals(SurfaceChange(emptySet(), emptySet(), emptySet()), change(listOf(Surface.MEAL), listOf(Surface.LIBRARY)))
    }

    @Test fun dialogsAndSheetsKeepTheirOwnMotion() {
        for (s in listOf(Surface.NOTICE, Surface.ENTRY_QUICK_EDIT, Surface.LIBRARY_FOOD_ADD, Surface.PRODUCT_REVIEW, Surface.MIXED_MEAL_PICKER, Surface.DISCARD_FOOD)) {
            assertEquals(SurfaceChange(emptySet(), emptySet(), emptySet()), change(listOf(Surface.DAILY), listOf(Surface.DAILY, s)))
        }
    }

    @Test fun theBaseStaysStillUnderTheEntryDetailAsItComesIn() {
        assertEquals(SurfaceChange(setOf(Surface.ENTRY_DETAIL), emptySet(), setOf(Surface.DAILY)), change(listOf(Surface.MEAL), listOf(Surface.ENTRY_DETAIL)))
        // Closing it, the base is simply there again and the detail glides out over it.
        assertEquals(SurfaceChange(emptySet(), setOf(Surface.ENTRY_DETAIL), emptySet()), change(listOf(Surface.ENTRY_DETAIL), listOf(Surface.MEAL)))
    }

    @Test fun aScreenReplacingAnotherCrosses() {
        assertEquals(
            SurfaceChange(setOf(Surface.FOOD_FORM), setOf(Surface.LIBRARY_FOOD), emptySet()),
            change(listOf(Surface.LIBRARY, Surface.LIBRARY_FOOD), listOf(Surface.LIBRARY, Surface.FOOD_FORM)),
        )
    }

    @Test fun theScannerComesAndGoesWithoutMotionAndWhatItCoveredComesBackAsItWas() {
        val none = SurfaceChange(emptySet(), emptySet(), emptySet())
        assertEquals(none, change(listOf(Surface.DAILY, Surface.LOG), listOf(Surface.SCANNER)))
        assertEquals(none, change(listOf(Surface.SCANNER), listOf(Surface.DAILY, Surface.LOG)))
    }

    @Test fun aTabChangeMovesTowardTheNewTabAndAMealRisesAndFalls() {
        assertEquals(BaseMove.FORWARD, baseMove(BaseScreen.DAILY, BaseScreen.LIBRARY))
        assertEquals(BaseMove.FORWARD, baseMove(BaseScreen.LIBRARY, BaseScreen.RECIPES))
        assertEquals(BaseMove.BACK, baseMove(BaseScreen.RECIPES, BaseScreen.DAILY))
        assertEquals(BaseMove.RISE, baseMove(BaseScreen.DAILY, BaseScreen.MEAL))
        assertEquals(BaseMove.RISE, baseMove(BaseScreen.MEAL, BaseScreen.MEAL_LIBRARY))
        assertEquals(BaseMove.FALL, baseMove(BaseScreen.MEAL_LIBRARY, BaseScreen.MEAL))
        assertEquals(BaseMove.FALL, baseMove(BaseScreen.MEAL, BaseScreen.DAILY))
        // Meals from a meal or a meal's Library is a way back out, not a tab change.
        assertEquals(BaseMove.FALL, baseMove(BaseScreen.MEAL_LIBRARY, BaseScreen.DAILY))
        assertEquals(BaseMove.FORWARD, baseMove(BaseScreen.MEAL, BaseScreen.RECIPES))
        assertEquals(BaseMove.BACK, baseMove(BaseScreen.LIBRARY, BaseScreen.MEAL))
        assertEquals(BaseMove.NONE, baseMove(BaseScreen.DAILY, BaseScreen.DAILY))
    }

    @Test fun theLibraryIsAMealsWhileItAddsToOne() {
        assertEquals(BaseScreen.MEAL_LIBRARY, BaseScreen.of(Surface.LIBRARY, libraryForMeal = true))
        assertEquals(BaseScreen.LIBRARY, BaseScreen.of(Surface.LIBRARY, libraryForMeal = false))
        assertEquals(BaseScreen.MEAL, BaseScreen.of(Surface.MEAL, libraryForMeal = false))
        assertEquals(BaseScreen.RECIPES, BaseScreen.of(Surface.RECIPES, libraryForMeal = false))
        assertEquals(BaseScreen.DAILY, BaseScreen.of(Surface.DAILY, libraryForMeal = false))
        assertTrue(Surface.LIBRARY_NO_MATCH.glides)
    }

    @Test fun theOneBarOpensTheLogOnMealsSearchesInTheLibraryAndFadesOnRecipes() {
        assertEquals(BarMode.Log(null), BaseScreen.DAILY.barMode(null))
        assertEquals(BarMode.Log(MealSlot.LUNCH), BaseScreen.MEAL.barMode(MealSlot.LUNCH))
        assertEquals(BarMode.Search, BaseScreen.LIBRARY.barMode(null))
        assertEquals(BarMode.Search, BaseScreen.MEAL_LIBRARY.barMode(MealSlot.LUNCH))
        assertEquals(null, BaseScreen.RECIPES.barMode(null))
    }

    @Test fun theTabsShowOnTheTabsAndFoldAwayUnderAMeal() {
        assertEquals(NavTab.MEALS, BaseScreen.DAILY.navTab())
        assertEquals(NavTab.LIBRARY, BaseScreen.LIBRARY.navTab())
        assertEquals(NavTab.RECIPES, BaseScreen.RECIPES.navTab())
        assertEquals(null, BaseScreen.MEAL.navTab())
        assertEquals(null, BaseScreen.MEAL_LIBRARY.navTab())
    }

    @Test fun onlyScreensInTheAppsWindowAreRecorded() {
        assertTrue(Surface.entries.filter { it.recorded }.all { it.glides || it == Surface.DAILY })
        assertTrue(Surface.entries.none { it.base && it.glides })
        assertTrue(!Surface.SCANNER.glides && !Surface.SCAN_REVIEW_BACKDROP.glides)
    }
}
