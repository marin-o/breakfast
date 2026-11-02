package mk.bosa.breakfast.app

import androidx.lifecycle.SavedStateHandle
import mk.bosa.breakfast.domain.FoodOrigin
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.NutritionBasis
import mk.bosa.breakfast.domain.NutritionValues
import mk.bosa.breakfast.domain.ServingConversion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import mk.bosa.breakfast.food.FoodDraft
import mk.bosa.breakfast.food.FoodDraftFields

/** The log draft and an open food form come back after the process is killed; added, cleared or closed, they do not. */
class SavedDraftsTest {
    /** What a new process gets: a handle built from the values the old one saved. */
    private fun afterProcessDeath(state: SavedStateHandle) = SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) })

    @Test fun aTypedDraftSurvives() {
        val state = SavedStateHandle()
        saveLogDraft(state, SavedLogDraft("2 eggs, 1 banana", committed = false))
        assertEquals(SavedLogDraft("2 eggs, 1 banana", false), restoreLogDraft(afterProcessDeath(state)))
    }

    @Test fun aCommittedDraftKeepsItsCommittedFlag() {
        val state = SavedStateHandle()
        saveLogDraft(state, SavedLogDraft("млеко", committed = true))
        assertEquals(SavedLogDraft("млеко", true), restoreLogDraft(afterProcessDeath(state)))
    }

    @Test fun anAddedOrClearedDraftDropsItsKeys() {
        val state = SavedStateHandle()
        saveLogDraft(state, SavedLogDraft("1 banana", committed = true))
        saveLogDraft(state, SavedLogDraft("", committed = false))
        assertFalse(state.contains(LOG_DRAFT_KEY))
        assertFalse(state.contains(LOG_DRAFT_COMMITTED_KEY))
        assertEquals(SavedLogDraft("", false), restoreLogDraft(afterProcessDeath(state)))
    }

    @Test fun nothingSavedRestoresAnEmptyDraft() {
        assertEquals(SavedLogDraft("", false), restoreLogDraft(SavedStateHandle()))
    }

    @Test fun aHalfFilledFoodFormSurvivesWithItsFlags() {
        val draft = FoodDraft().apply {
            reset(); name = "Ajvar"; barcode = "5310000000000"; barcodeFromScan = true; fromOff = true
        }
        val baseline = draft.fields()
        draft.apply {
            brand = "Podravka"; shorthand = "per 100g: 120 kcal"; calories = "120"; caloriesEdited = true
            protein = "2,5"; aliases = listOf("ајвар"); aliasInput = "pindjur"; aliasFieldOpen = true
            servings = listOf(ServingConversion(MeasureUnit.TABLESPOON, 15.0, "personal serving", label = "spoon"))
            servingFormOpen = true; servingUnit = MeasureUnit.PIECE; servingAmount = "280"; servingLabel = "jar"
        }
        val state = SavedStateHandle()
        saveFoodForm(state, SavedFoodForm(null, draft.state(), baseline))

        val restored = restoreFoodForm(afterProcessDeath(state))!!
        assertNull(restored.editingFoodId)
        assertEquals(baseline, restored.baseline)
        val again = FoodDraft().apply { restore(restored.draft) }
        assertEquals(draft.state(), again.state())
        assertTrue(again.isDirty(restored.baseline))
    }

    @Test fun anEditFormKeepsWhichFoodItEdits() {
        val food = FoodRecord(
            id = "ajvar", name = "Ajvar", aliases = emptyList(), category = "Spread", origin = FoodOrigin.CUSTOM,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(120.0, null, null, null)),
            defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM,
        )
        val draft = FoodDraft().apply { load(food) }
        val state = SavedStateHandle()
        saveFoodForm(state, SavedFoodForm("ajvar", draft.state(), draft.fields()))
        val restored = restoreFoodForm(afterProcessDeath(state))!!
        assertEquals("ajvar", restored.editingFoodId)
        assertFalse(FoodDraft().apply { restore(restored.draft) }.isDirty(restored.baseline))
    }

    @Test fun aClosedOrSavedFormDropsItsKey() {
        val state = SavedStateHandle()
        saveFoodForm(state, SavedFoodForm(null, FoodDraft().state(), FoodDraftFields()))
        saveFoodForm(state, null)
        assertFalse(state.contains(FOOD_FORM_KEY))
        assertNull(restoreFoodForm(afterProcessDeath(state)))
    }

    @Test fun anUnreadableFormIsDropped() {
        assertNull(restoreFoodForm(SavedStateHandle(mapOf(FOOD_FORM_KEY to "{not json"))))
    }

    @Test fun followingTodayStoresNoDate() {
        val state = SavedStateHandle()
        saveSelectedDay(state, SavedDay("2026-10-20", followingToday = false))
        saveSelectedDay(state, SavedDay("2026-10-25", followingToday = true))
        assertFalse(state.contains(SELECTED_DAY_KEY))
        assertEquals(SavedDay(null, followingToday = true), restoreSelectedDay(afterProcessDeath(state)))
    }

    @Test fun aPickedDayComesBack() {
        val state = SavedStateHandle()
        saveSelectedDay(state, SavedDay("2026-10-20", followingToday = false))
        assertEquals(SavedDay("2026-10-20", followingToday = false), restoreSelectedDay(afterProcessDeath(state)))
    }

    @Test fun nothingSavedMeansToday() {
        assertEquals(SavedDay(null, followingToday = true), restoreSelectedDay(SavedStateHandle()))
        // A date from a build before the flag stays the picked day.
        assertEquals(SavedDay("2026-10-20", followingToday = false), restoreSelectedDay(SavedStateHandle(mapOf(SELECTED_DAY_KEY to "2026-10-20"))))
    }

    @Test fun onlyTheDraftKeysAreWritten() {
        // The Open Food Facts login lives in other view-model fields; nothing here can write it.
        val state = SavedStateHandle()
        saveLogDraft(state, SavedLogDraft("1 egg", committed = false))
        saveFoodForm(state, SavedFoodForm(null, FoodDraft().state(), FoodDraftFields()))
        assertEquals(setOf(LOG_DRAFT_KEY, LOG_DRAFT_COMMITTED_KEY, FOOD_FORM_KEY), state.keys())
    }
}
