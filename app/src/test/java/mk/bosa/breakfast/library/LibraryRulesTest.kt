package mk.bosa.breakfast.library

import mk.bosa.breakfast.domain.FoodOrigin
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.MealSlot
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.NutritionBasis
import mk.bosa.breakfast.domain.NutritionValues
import mk.bosa.breakfast.domain.ServingConversion
import mk.bosa.breakfast.domain.defaultServingCalculation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Library's rules (`LibraryRules.kt`), its log drafts and bar input (`LibraryInput.kt`), and its rows' default serving. */
class LibraryRulesTest {
    @Test fun aLibraryOpenedFromAMealGoesBackToThatMeal() {
        assertEquals(LibraryExit.MEAL, exitLibraryTarget(resolving = false, origin = MealSlot.LUNCH))
    }

    @Test fun theLibraryTabGoesBackToDaily() {
        assertEquals(LibraryExit.DAILY, exitLibraryTarget(resolving = false, origin = null))
    }

    @Test fun aResolveAlwaysGoesBackToTheLog() {
        // A resolve opens the Library on the Log's meal, but it returns to the Log, not to that meal.
        assertEquals(LibraryExit.LOG, exitLibraryTarget(resolving = true, origin = MealSlot.BREAKFAST))
        assertEquals(LibraryExit.LOG, exitLibraryTarget(resolving = true, origin = null))
    }

    @Test fun anOverlayHidesTheNoMatchScreen() {
        assertEquals(true, isLibraryNoMatch(true, true, "zz", "zz", noResults = true, otherScreenOpen = false))
        assertEquals(false, isLibraryNoMatch(true, true, "zz", "zz", noResults = true, otherScreenOpen = true))
    }

    @Test
    fun noMatchShowsOnlyForAFinishedEmptySearchWithNothingOverIt() {
        assertTrue(isLibraryNoMatch(true, true, "barley", "barley", true, false))
        assertFalse(isLibraryNoMatch(false, true, "barley", "barley", true, false))
        assertFalse(isLibraryNoMatch(true, false, "barley", "barley", true, false))
        assertFalse(isLibraryNoMatch(true, true, "  ", "  ", true, false))
        assertFalse("results still for an older query", isLibraryNoMatch(true, true, "barley", "bar", true, false))
        assertFalse(isLibraryNoMatch(true, true, "barley", "barley", false, false))
        assertFalse("another screen is on top", isLibraryNoMatch(true, true, "barley", "barley", true, true))
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
