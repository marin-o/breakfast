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
}
