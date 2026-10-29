package com.iron.breakfast.entry

import com.iron.breakfast.app.DiaryChange
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.ReplacementResult
import com.iron.breakfast.domain.ServingConversion
import com.iron.breakfast.today.OpenDiary
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryEditTest {
    /** Each edit waits until the test answers it. */
    private class FakeStore : EntryEditStore {
        val calls = mutableListOf<String>()
        private var answer: CompletableDeferred<ReplacementResult>? = null
        override suspend fun replaceAmountUnit(entryId: String, text: String): ReplacementResult {
            calls += "quick $entryId $text"
            return CompletableDeferred<ReplacementResult>().also { answer = it }.await()
        }
        override suspend fun replaceEntry(entryId: String, foodId: String, amount: Double, unit: MeasureUnit, servingName: String?, meal: MealSlot, dayKey: String): ReplacementResult {
            calls += "full $entryId $foodId $amount ${unit.label} $meal $dayKey" + (servingName?.let { " $it" } ?: "")
            return CompletableDeferred<ReplacementResult>().also { answer = it }.await()
        }
        fun answer(result: ReplacementResult) { answer!!.complete(result) }
        fun fail(error: Exception) { answer!!.completeExceptionally(error) }
    }

    private val diary = OpenDiary()
    private val store = FakeStore()
    private var openMeal: MealSlot? = null
    private val events = mutableListOf<String>()
    private var savedId: String? = "kept"
    private val edit = EntryEdit(
        diary.session, store,
        openMeal = { openMeal },
        onBegin = { events += "show $it" },
        onEnd = { events += "end toDaily=$it" },
        initialEntryId = "e1",
        saveEntryId = { savedId = it },
    )

    private val oats = FoodRecord(
        "oats", "Oats", emptyList(), "Grains", FoodOrigin.GENERIC,
        NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(380.0, 13.0, 60.0, 7.0)),
        conversions = listOf(ServingConversion(MeasureUnit.TABLESPOON, 10.0, "tbsp")), defaultAmount = 50.0, defaultUnit = MeasureUnit.GRAM,
    )
    private val e1 = MealEntry("e1", "oats", "Oats", 120.0, MeasureUnit.GRAM, 120.0, NutritionValues(456.0, 15.6, 72.0, 8.4), "120 g", false, 0L, mealSlot = MealSlot.BREAKFAST, dayKey = "2026-10-28")

    init {
        diary.foods = listOf(oats)
        diary.rows = listOf(e1)
        diary.session.refresh()
    }

    @Test fun theDetailComesBackByIdAndFollowsTheDiary() {
        edit.diaryReloaded()
        assertEquals(e1, edit.selectedEntry)
        diary.rows = emptyList()
        diary.session.refresh()
        edit.diaryReloaded()
        assertNull("a deleted entry closes its detail", edit.selectedEntry)
        edit.openDetail(e1)
        assertEquals("e1", savedId)
        edit.closeDetail()
        assertNull(savedId)
    }

    @Test fun aQuickEditFromDailyShowsItsMealAndReturnsToDaily() {
        edit.openDetail(e1)
        edit.beginQuick(e1)
        assertNull("the edit replaces the detail", edit.selectedEntry)
        assertEquals(listOf("show BREAKFAST"), events)
        assertEquals(EntryEditMode.QUICK, edit.mode)
        assertTrue("the composers give way to the edit", edit.editing)
        // A late single tap of the double tap does not reopen the detail over the sheet.
        edit.openDetail(e1)
        assertNull(edit.selectedEntry)
        assertTrue(edit.cancel())
        assertEquals(listOf("show BREAKFAST", "end toDaily=true"), events)
        assertEquals(EntryEditMode.NONE, edit.mode)
        assertNull(edit.editingEntryId)
    }

    @Test fun anEditOpenedOnAMealStaysOnThatMeal() {
        openMeal = MealSlot.LUNCH
        edit.beginFull(e1)
        assertTrue("a full edit owns the input too", edit.editing)
        edit.cancel()
        assertFalse(edit.editing)
        assertEquals(listOf("show BREAKFAST", "end toDaily=false"), events)
    }

    @Test fun noEditOpensWhileAWriteRuns() {
        diary.session.write(DiaryChange.FoodsChanged) { CompletableDeferred<Unit>().await() }
        edit.beginQuick(e1)
        edit.beginFull(e1)
        assertEquals(EntryEditMode.NONE, edit.mode)
        assertTrue(events.isEmpty())
    }

    @Test fun aQuickSaveWritesItsOwnTextAndEndsTheEdit() {
        edit.beginQuick(e1)
        edit.saveQuick("2 tbsp")
        assertEquals(listOf("quick e1 2 tbsp"), store.calls)
        assertTrue(diary.session.busy)
        store.answer(ReplacementResult.Success(e1))
        assertEquals(listOf<DiaryChange>(DiaryChange.EntryEdited), diary.published)
        assertEquals(EntryEditMode.NONE, edit.mode)
        assertEquals("end toDaily=true", events.last())
    }

    @Test fun aRefusedQuickSaveShowsItsOwnErrorUntilTheInputChanges() {
        edit.beginQuick(e1)
        edit.saveQuick("0 g")
        store.answer(ReplacementResult.Failure("Enter a positive amount and a supported unit, such as 100 g or 1 tbsp."))
        assertTrue("a refused edit commits nothing", diary.published.isEmpty())
        assertEquals(EntryEditMode.QUICK, edit.mode)
        assertEquals("Enter a positive amount and a supported unit, such as 100 g or 1 tbsp.", edit.error)
        edit.inputChanged()
        assertNull(edit.error)
        edit.saveQuick("50 g")
        store.fail(IllegalStateException("disk full"))
        assertEquals("disk full", edit.error)
        edit.cancel()
        assertNull(edit.error)
    }

    @Test fun aQuickSaveThatCommitsAfterItsEditClosedOnlyReloadsTheDiary() {
        edit.beginQuick(e1)
        edit.saveQuick("2 tbsp")
        // Leaving the sheet while it saves: a quick edit lets go at once; the write keeps the gate.
        assertTrue(edit.cancel())
        edit.beginQuick(e1)
        assertEquals("a second edit waits for the gate", EntryEditMode.NONE, edit.mode)
        diary.entryReads = 0
        store.answer(ReplacementResult.Success(e1))
        assertEquals(listOf<DiaryChange>(DiaryChange.EntryEditedAfterClose), diary.published)
        assertEquals(1, diary.entryReads)
        assertEquals(listOf("show BREAKFAST", "end toDaily=true"), events)
        // The same for a refusal: nothing shows on a later edit.
        edit.beginQuick(e1)
        edit.saveQuick("0 g")
        edit.cancel()
        edit.beginQuick(e1)
        assertEquals(EntryEditMode.NONE, edit.mode)
        store.answer(ReplacementResult.Failure("refused"))
        edit.beginQuick(e1)
        assertNull(edit.error)
    }

    @Test fun aBlankQuickTextEndsTheEditWithoutAWrite() {
        edit.beginQuick(e1)
        edit.saveQuick(" ")
        assertTrue(store.calls.isEmpty())
        assertEquals(EntryEditMode.NONE, edit.mode)
    }

    @Test fun aFullEditChecksItsValuesFirst() {
        edit.beginFull(e1)
        edit.saveFull("oats", "0", MeasureUnit.GRAM, MealSlot.LUNCH, "2026-10-27")
        edit.saveFull("", "50", MeasureUnit.GRAM, MealSlot.LUNCH, "2026-10-27")
        assertEquals("Enter a positive amount and choose a food.", edit.error)
        assertTrue(store.calls.isEmpty())
    }

    /** A serving chosen in the full edit is saved by its name; a unit carries none. */
    @Test fun aFullEditSavesAServingByItsName() {
        edit.beginFull(e1)
        edit.saveFull("oats", "2", MeasureUnit.SERVING, MealSlot.LUNCH, "2026-10-27", servingName = "scoop")
        assertEquals(listOf("full e1 oats 2.0  LUNCH 2026-10-27 scoop"), store.calls)
        store.answer(ReplacementResult.Success(e1))
        edit.beginFull(e1)
        edit.saveFull("oats", "50", MeasureUnit.GRAM, MealSlot.LUNCH, "2026-10-27", servingName = "scoop")
        assertEquals("full e1 oats 50.0 g LUNCH 2026-10-27", store.calls.last())
    }

    @Test fun aFullEditOwnsItsEditUntilItsSaveEnds() {
        edit.beginFull(e1)
        edit.openFoodPicker()
        edit.chooseFood(oats)
        assertFalse(edit.pickerOpen)
        assertEquals(oats, edit.editingFood())
        edit.saveFull("oats", "50", MeasureUnit.GRAM, MealSlot.LUNCH, "2026-10-27")
        assertEquals(listOf("full e1 oats 50.0 g LUNCH 2026-10-27"), store.calls)
        assertFalse("Back waits for the save", edit.cancel())
        assertEquals(EntryEditMode.FULL, edit.mode)
        store.answer(ReplacementResult.Success(e1))
        assertEquals(listOf<DiaryChange>(DiaryChange.EntryEdited), diary.published)
        assertEquals(EntryEditMode.NONE, edit.mode)
        assertEquals("end toDaily=true", events.last())
    }

    @Test fun aFailedFullEditStaysOpenWithItsError() {
        edit.beginFull(e1)
        edit.saveFull("oats", "50", MeasureUnit.GRAM, MealSlot.LUNCH, "2026-10-27")
        store.answer(ReplacementResult.Failure("That catalogue record is no longer available."))
        assertEquals("That catalogue record is no longer available.", edit.error)
        edit.saveFull("oats", "50", MeasureUnit.GRAM, MealSlot.LUNCH, "2026-10-27")
        store.fail(IllegalStateException("disk full"))
        assertEquals("disk full", edit.error)
        assertTrue("a failed save no longer holds the edit", edit.cancel())
    }

    @Test fun thePreviewAndUnitsComeFromTheFood() {
        assertEquals(190.0, edit.preview(e1, "oats", 50.0, MeasureUnit.GRAM)!!.nutrition.calories, 1e-9)
        assertNull("another food that is gone", edit.preview(e1, "gone", 50.0, MeasureUnit.GRAM))
        assertEquals(listOf(MeasureUnit.GRAM, MeasureUnit.TABLESPOON), entryUnits(oats))
    }

    @Test fun anEntryWhoseFoodIsGoneOffersItsAmountOnlyWhenItCanScale() {
        val orphan = e1.copy(id = "o1", foodId = "deleted-food")
        assertTrue(edit.amountEditable(e1))
        assertTrue(edit.amountEditable(orphan))
        val preview = edit.preview(orphan, orphan.foodId, 60.0, MeasureUnit.GRAM)!!
        assertEquals(228.0, preview.nutrition.calories, 1e-9)
        assertNull("no other unit without the food", edit.preview(orphan, orphan.foodId, 1.0, MeasureUnit.TABLESPOON))

        val stuck = orphan.copy(id = "o2", enteredAmount = 0.0)
        assertFalse(edit.amountEditable(stuck))
        edit.beginQuick(stuck)
        assertEquals(EntryEditMode.NONE, edit.mode)
        edit.beginQuick(orphan)
        assertEquals(EntryEditMode.QUICK, edit.mode)
    }
}
