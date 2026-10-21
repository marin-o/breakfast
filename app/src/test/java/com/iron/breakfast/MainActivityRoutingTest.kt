package com.iron.breakfast

import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.ServingConversion
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityRoutingTest {
    @Test fun contributionAuthCannotDismissWhileSubmitting() {
        assertEquals(false, contributionAuthCanDismiss(true))
        assertEquals(true, contributionAuthCanDismiss(false))
    }

    @Test fun scannedProductReviewKeepsLibraryOriginSeparateFromMealAndQueue() {
        assertEquals(ProductReviewOrigin.LIBRARY, productReviewOrigin(null, resolving = false, queueNotEmpty = false))
        assertEquals(ProductReviewOrigin.MEAL, productReviewOrigin(MealSlot.DINNER, resolving = false, queueNotEmpty = false))
        assertEquals(ProductReviewOrigin.QUEUE, productReviewOrigin(null, resolving = true, queueNotEmpty = true))
    }

    @Test fun libraryOriginCanSaveWithoutCreatingDiaryLogPlan() {
        assertEquals(true, canSaveProductToLibrary(null, resolutionContinuation = false, queueNotEmpty = false))
        assertEquals(false, canSaveProductToLibrary(null, resolutionContinuation = true, queueNotEmpty = true))
    }

    @Test fun mealWordsInsideFoodNamesAreNotMarkers() {
        assertEquals(emptyList<MealSlot>(), mealMarkerSlots("breakfast cereal, 100g oats"))
        assertEquals(emptyList<MealSlot>(), mealMarkerSlots("100g oats, lunch meat"))
    }

    @Test fun onlyWholeCommaSegmentMarkerIsRemovedFromDraft() {
        assertEquals("100g oats", removeMealMarkerToken("breakfast, 100g oats", MealSlot.BREAKFAST))
    }

    @Test fun amountlessDailyFoodOpensLocalSearchWithOptionalMealContext() {
        assertEquals(AmountlessDailySearch(null, "banana"), amountlessDailySearch("banana"))
        assertEquals(AmountlessDailySearch(MealSlot.LUNCH, "banana"), amountlessDailySearch("lunch, banana"))
        assertEquals(AmountlessDailySearch(MealSlot.LUNCH, "banana"), amountlessDailySearch("banana, lunch"))
    }

    @Test fun quantifiedDailyInputContinuesToLogAndMealWordsInsideNamesStayFoodText() {
        assertEquals(null, amountlessDailySearch("1 banana"))
        assertEquals(null, amountlessDailySearch("lunch, 1 banana"))
        assertEquals(AmountlessDailySearch(null, "breakfast cereal"), amountlessDailySearch("breakfast cereal"))
        assertEquals(emptyList<MealSlot>(), mealMarkerSlots("breakfast cereal"))
    }

    @Test fun libraryRepeatDraftCarriesClickedMealAndSingleEntryIntent() {
        val food = FoodRecord(
            id = "banana", name = "Banana", aliases = emptyList(), category = "Fruit", origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(89.0, 1.1, 23.0, 0.3)),
            defaultAmount = 1.0, defaultUnit = MeasureUnit.PIECE
        )
        val draft = libraryLogDraft(food, MealSlot.DINNER, 2.0, MeasureUnit.PIECE)
        assertEquals(MealSlot.DINNER, draft.target)
        assertEquals(2.0, draft.amount, 0.0)
    }

    @Test fun mixedLibraryInputRetainsQuantifiedAndAmountlessSegments() {
        val segments = libraryInputSegments("100g chicken, pesto")
        assertEquals(2, segments.size)
        assertEquals("chicken", segments[0].foodPhrase)
        assertEquals(100.0, segments[0].amount)
        assertEquals("pesto", segments[1].foodPhrase)
        assertEquals(null, segments[1].amount)
    }

    @Test fun defaultServingCalculationUsesDefaultQuantityInsteadOfBasisCalories() {
        val food = FoodRecord(
            id = "oats", name = "Oats", aliases = emptyList(), category = "Grain", origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(50.0, null, null, null)),
            conversions = listOf(ServingConversion(MeasureUnit.PIECE, 100.0, "one serving")),
            defaultAmount = 2.0, defaultUnit = MeasureUnit.PIECE
        )
        assertEquals(100.0, defaultServingCalculation(food).nutrition.calories, 0.0)
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
            logSubmitPlan(ready = true, busy = false, input = "lunch, 2 eggs, dinner", typedMarkers = markers, chosen = null, preset = null),
        )
    }

    @Test fun addWithNoMealOpensTheMenuAndChoosingAMealSubmits() {
        assertEquals(LogSubmitPlan.OpenMenu, logSubmitPlan(true, false, "2 eggs", emptyList(), null, null))
        // Once a meal is chosen from the menu, the same input saves to it.
        assertEquals(LogSubmitPlan.Save(MealSlot.DINNER), logSubmitPlan(true, false, "2 eggs", emptyList(), MealSlot.DINNER, null))
    }

    @Test fun addWithAPresetOrTypedMealSavesWithoutTheMenu() {
        assertEquals(LogSubmitPlan.Save(MealSlot.SNACKS), logSubmitPlan(true, false, "2 eggs", emptyList(), null, MealSlot.SNACKS))
        assertEquals(LogSubmitPlan.Save(MealSlot.LUNCH), logSubmitPlan(true, false, "lunch, 2 eggs", listOf(MealSlot.LUNCH), null, MealSlot.SNACKS))
    }

    @Test fun busyOrNotReadyIgnoresSubmitSoADoubleTapSavesOnce() {
        assertEquals(LogSubmitPlan.Ignore, logSubmitPlan(true, true, "2 eggs", emptyList(), MealSlot.LUNCH, null))
        assertEquals(LogSubmitPlan.Ignore, logSubmitPlan(false, false, "2 eggs", emptyList(), MealSlot.LUNCH, null))
    }

    @Test fun blankInputIsAnErrorNotAMenu() {
        assertEquals(LogSubmitPlan.Error("Add at least one food."), logSubmitPlan(true, false, "  ", emptyList(), null, null))
    }

    @Test fun removeAllMealMarkerTokensDropsMealWordsOnly() {
        assertEquals("2 eggs, 1 banana", removeAllMealMarkerTokens("lunch, 2 eggs, dinner, 1 banana"))
    }
}
