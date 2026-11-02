package mk.bosa.breakfast.log

import mk.bosa.breakfast.domain.TestFoods
import mk.bosa.breakfast.domain.FoodMatcher
import mk.bosa.breakfast.domain.LogPlanner
import mk.bosa.breakfast.domain.MealSlot
import mk.bosa.breakfast.domain.draftLogPlan
import mk.bosa.breakfast.domain.mealMarkerSlots
import mk.bosa.breakfast.domain.removeAllMealMarkerTokens
import mk.bosa.breakfast.domain.removeMealMarkerToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Log's rules (`LogRules.kt`) and the meal words and drafts they read. */
class LogRulesTest {
    @Test fun mealWordsInsideFoodNamesAreNotMarkers() {
        assertEquals(emptyList<MealSlot>(), mealMarkerSlots("breakfast cereal, 100g oats"))
        assertEquals(emptyList<MealSlot>(), mealMarkerSlots("100g oats, lunch meat"))
    }

    @Test fun onlyWholeCommaSegmentMarkerIsRemovedFromDraft() {
        assertEquals("100g oats", removeMealMarkerToken("breakfast, 100g oats", MealSlot.BREAKFAST))
    }

    @Test fun aBareWordIsAFoodNotASearch() {
        // Done and Add never route to library search: a bare word is planned like any other part.
        assertEquals(listOf("banana"), draftLogPlan("banana", LogPlanner(FoodMatcher(TestFoods.fixtures))).entries.map { it.food.id })
        assertEquals(listOf("banana"), draftLogPlan("lunch, banana", LogPlanner(FoodMatcher(TestFoods.fixtures))).entries.map { it.food.id })
    }

    @Test fun aBareAmbiguousWordCommitsItsTopMatchOnAdd() {
        // "milk" names several foods: committed (Done or Add) it logs the top match, like any finished part.
        val entries = draftLogPlan("milk", LogPlanner(FoodMatcher(TestFoods.fixtures))).entries
        assertEquals(1, entries.size)
        assertTrue(entries.single().food.id in listOf("milk_28", "milk_32"))
    }

    @Test fun mealWordsInsideNamesStayFoodText() {
        assertEquals(emptyList<MealSlot>(), mealMarkerSlots("breakfast cereal"))
    }

    @Test fun logMealPickerLabelFollowsTargetOrReadsMeal() {
        assertEquals("Meal", logMealPickerLabel(null))
        assertEquals("Dinner", logMealPickerLabel(MealSlot.DINNER))
    }

    @Test fun logTargetPrecedenceIsChosenThenTypedThenPreset() {
        // (a) a meal picked from the Log menu wins over a typed word and a preset.
        assertEquals(MealSlot.LUNCH, logTargetMealOf(MealSlot.LUNCH, listOf(MealSlot.DINNER), MealSlot.SNACKS))
        // (b) a single typed word wins over a preset.
        assertEquals(MealSlot.DINNER, logTargetMealOf(null, listOf(MealSlot.DINNER), MealSlot.BREAKFAST))
        // (c) otherwise the preset.
        assertEquals(MealSlot.BREAKFAST, logTargetMealOf(null, emptyList(), MealSlot.BREAKFAST))
        // (d) otherwise none.
        assertEquals(null, logTargetMealOf(null, emptyList(), null))
    }

    @Test fun twoDifferentTypedMealWordsGiveNoTargetAndAnError() {
        val markers = listOf(MealSlot.LUNCH, MealSlot.DINNER)
        assertEquals(null, logTargetMealOf(null, markers, null))
        assertEquals(
            LogSubmitPlan.Error("Choose one meal marker, or remove the extra markers."),
            logSubmitPlan(canWrite = true, input = "lunch, 2 eggs, dinner", typedMarkers = markers, chosen = null, preset = null),
        )
    }

    @Test fun addWithNoMealOpensTheMenuAndChoosingAMealSubmits() {
        assertEquals(LogSubmitPlan.OpenMenu, logSubmitPlan(true, "2 eggs", emptyList(), null, null))
        // Once a meal is chosen from the menu, the same input saves to it.
        assertEquals(LogSubmitPlan.Save(MealSlot.DINNER), logSubmitPlan(true, "2 eggs", emptyList(), MealSlot.DINNER, null))
    }

    @Test fun addWithAPresetOrTypedMealSavesWithoutTheMenu() {
        assertEquals(LogSubmitPlan.Save(MealSlot.SNACKS), logSubmitPlan(true, "2 eggs", emptyList(), null, MealSlot.SNACKS))
        assertEquals(LogSubmitPlan.Save(MealSlot.LUNCH), logSubmitPlan(true, "lunch, 2 eggs", listOf(MealSlot.LUNCH), null, MealSlot.SNACKS))
    }

    @Test fun busyOrNotReadyIgnoresSubmitSoADoubleTapSavesOnce() {
        assertEquals(LogSubmitPlan.Ignore, logSubmitPlan(false, "2 eggs", emptyList(), MealSlot.LUNCH, null))
    }

    @Test fun blankInputIsAnErrorNotAMenu() {
        assertEquals(LogSubmitPlan.Error("Add at least one food."), logSubmitPlan(true, "  ", emptyList(), null, null))
    }

    @Test fun theLogFieldStaysDownWhileFoodsAreBeingMatched() {
        assertEquals(LogFieldFocus.KEYBOARD_DOWN, logFieldFocusOf(matching = true, menuOpen = false, writing = false))
        assertEquals(LogFieldFocus.KEYBOARD_DOWN, logFieldFocusOf(matching = true, menuOpen = true, writing = false))
        assertEquals(LogFieldFocus.KEYBOARD_DOWN, logFieldFocusOf(matching = true, menuOpen = false, writing = true))
    }

    @Test fun theLogFieldWaitsForTheMenuAndAWrite() {
        assertEquals(LogFieldFocus.LEAVE, logFieldFocusOf(matching = false, menuOpen = true, writing = false))
        assertEquals(LogFieldFocus.LEAVE, logFieldFocusOf(matching = false, menuOpen = false, writing = true))
    }

    @Test fun theLogFieldTakesFocusOtherwise() {
        assertEquals(LogFieldFocus.FOCUS, logFieldFocusOf(matching = false, menuOpen = false, writing = false))
    }

    @Test fun removeAllMealMarkerTokensDropsMealWordsOnly() {
        assertEquals("2 eggs, 1 banana", removeAllMealMarkerTokens("lunch, 2 eggs, dinner, 1 banana"))
    }
}
