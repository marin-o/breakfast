package com.iron.breakfast.food

import androidx.lifecycle.SavedStateHandle
import com.iron.breakfast.resolve.ReviewLogPlan
import com.iron.breakfast.app.DiaryChange
import com.iron.breakfast.app.Notices
import com.iron.breakfast.app.restoreFoodForm
import com.iron.breakfast.app.saveFoodForm
import com.iron.breakfast.data.OffContributionDraft
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.TypedPart
import com.iron.breakfast.resolve.ResolveQueueView
import com.iron.breakfast.today.OpenDiary
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodFormTest {
    private class FakeStore : FoodFormStore {
        val calls = mutableListOf<String>()
        val updated = mutableListOf<FoodRecord>()
        val saved = mutableListOf<NewFood>()
        var hold: CompletableDeferred<Unit>? = null
        override suspend fun updateFood(food: FoodRecord) { hold?.await(); calls += "update ${food.id}"; updated += food }
        override suspend fun saveFood(food: NewFood): FoodRecord { hold?.await(); calls += "save ${food.name}"; saved += food; return stored(food) }
        override suspend fun saveFoodAndLog(food: NewFood, meal: MealSlot, dayKey: String): FoodRecord {
            hold?.await(); calls += "save and log ${food.name} to $meal on $dayKey"; saved += food; return stored(food)
        }
        private fun stored(food: NewFood) = record("custom-${food.name}", food.name, food.barcode)
    }

    private val diary = OpenDiary()
    private val store = FakeStore()
    private val notices = Notices()
    /** How often "Logged!" was asked for. */
    private var confirmed = 0
    private var meal: MealSlot? = null
    private var queue: ResolveQueueView? = null
    private var resolvingPart = false
    private val offers = mutableListOf<OffContributionDraft?>()
    private val events = mutableListOf<String>()
    /** Where the scanner the form opened hands its barcode. */
    private var scanned: ((String) -> Unit)? = null
    private val form = FoodForm(
        diary.session, store, notices,
        selectedDay = { "2026-10-28" },
        openMeal = { meal },
        queue = { queue },
        resolvingPart = { resolvingPart },
        offerContribution = { offers += it },
        startScanFor = { onBarcode -> events += "scan"; scanned = onBarcode },
        onOpen = { events += "open fromResolver=$it" },
        onClose = { events += "close" },
        onSaved = { food, newFood, advances -> events += "saved ${food.name} new=$newFood advances=$advances" },
        confirmLogged = { confirmed++ },
    )

    private val kefir = record("kefir", "Kefir", "4006381333931")

    init {
        diary.foods = listOf(kefir)
        diary.session.refresh()
    }

    private fun typeFood(name: String = "Ayran", kcal: String = "40") {
        form.draft.name = name
        form.draft.calories = kcal
    }

    @Test fun newFoodOpensPrefilledAndTellsTheCoordinator() {
        form.open("Ayran", "Dukat", " 036000291452 ", fromResolver = true)
        assertTrue(form.open)
        assertNull(form.editingFoodId)
        assertEquals("Ayran", form.draft.name)
        assertEquals("036000291452", form.draft.barcode)
        assertTrue("a prefilled barcode came from a scan", form.draft.barcodeFromScan)
        assertEquals(listOf("open fromResolver=true"), events)
    }

    @Test fun aNewFoodWithoutAMealIsSavedAlone() {
        form.open()
        typeFood()
        form.save()
        assertEquals(listOf("save Ayran"), store.calls)
        assertEquals(listOf<DiaryChange>(DiaryChange.FoodsChanged), diary.published)
        assertEquals("Saved to your food library.", notices.message)
        assertEquals("saved alone, nothing was logged", 0, confirmed)
        assertFalse(form.open)
        assertEquals("", form.draft.name)
        assertEquals(listOf("open fromResolver=false", "saved Ayran new=true advances=false"), events)
        assertEquals("a save that offers no candidate drops an earlier one", listOf<OffContributionDraft?>(null), offers)
    }

    @Test fun aNewFoodWithAMealOpenIsSavedAndLoggedThere() {
        meal = MealSlot.LUNCH
        form.open()
        typeFood()
        form.save()
        assertEquals(listOf("save and log Ayran to LUNCH on 2026-10-28"), store.calls)
        assertEquals(listOf<DiaryChange>(DiaryChange.FoodsLogged), diary.published)
        assertEquals(1, confirmed)
        assertNull(notices.message)
    }

    @Test fun whileAQueueIsResolvedItsMealWinsAndTheQueueAdvances() {
        meal = MealSlot.LUNCH
        queue = ResolveQueueView(MealSlot.DINNER, listOf("ayran 200ml"), 1, TypedPart("ayran", 200.0, MeasureUnit.MILLILITER))
        form.open(fromResolver = true)
        typeFood()
        form.save()
        assertEquals(listOf("save and log Ayran to DINNER on 2026-10-28"), store.calls)
        assertEquals("saved Ayran new=true advances=true", events.last())
    }

    @Test fun aQueueWithoutAMealRefusesTheSave() {
        queue = ResolveQueueView(null, listOf("ayran"), 1, TypedPart("ayran", null, null))
        form.open(fromResolver = true)
        typeFood()
        form.save()
        assertEquals("Choose a meal before saving this food.", notices.message)
        assertTrue(store.calls.isEmpty())
        assertTrue(form.open)
    }

    @Test fun anEditUpdatesTheFoodAndNeverLogs() {
        meal = MealSlot.LUNCH
        form.openEdit(kefir)
        assertEquals("kefir", form.editingFoodId)
        assertEquals("Kefir", form.draft.name)
        form.draft.name = "Kefir 2%"
        form.save()
        assertEquals(listOf("update kefir"), store.calls)
        assertEquals("Kefir 2%", store.updated.single().name)
        assertEquals("an unchanged barcode keeps its spelling", "4006381333931", store.updated.single().barcode)
        assertEquals(listOf<DiaryChange>(DiaryChange.FoodsChanged), diary.published)
        assertEquals("Personal food updated for future logs.", notices.message)
        assertEquals("an edit logs nothing, so no \"Logged!\"", 0, confirmed)
        assertEquals("the edit reports the food as it was", "saved Kefir new=false advances=false", events.last())
    }

    @Test fun aNewOrChangedBarcodeIsStoredInItsCanonicalForm() {
        form.open()
        typeFood()
        form.draft.barcode = "036000291452"
        form.save()
        assertEquals("0036000291452", store.saved.single().barcode)
    }

    @Test fun aRefusedNewFoodMovesFocusToTheFieldEveryTime() {
        form.open()
        form.draft.calories = "40"
        form.save()
        assertEquals("Name and calories are required.", notices.message)
        assertEquals(FoodFormFocus(FoodFormField.NAME, 1L), form.focus)
        form.focusHandled()
        assertNull(form.focus)
        form.save()
        assertEquals("a repeated refusal is a new request", FoodFormFocus(FoodFormField.NAME, 2L), form.focus)
        form.draft.name = "Ayran"
        form.draft.barcode = "4006381333931"
        form.save()
        assertEquals("That barcode already belongs to Kefir.", notices.message)
        assertEquals(FoodFormField.BARCODE, form.focus?.field)
        assertTrue(store.calls.isEmpty())
    }

    @Test fun aRefusedEditShowsTheNoticeWithoutAFocusRequest() {
        form.openEdit(kefir)
        form.draft.name = ""
        form.save()
        assertEquals("Name and calories are required.", notices.message)
        assertNull(form.focus)
    }

    @Test fun backClosesAnUnchangedFormAndAsksForAChangedOne() {
        form.open("Ayran")
        form.leave()
        assertFalse(form.open)
        assertEquals("close", events.last())
        form.open("Ayran")
        form.draft.brand = "Dukat"
        form.leave()
        assertTrue(form.discardOpen)
        form.keepEditing()
        assertFalse(form.discardOpen)
        assertTrue(form.open)
        form.leave()
        form.discard()
        assertFalse(form.open)
        assertFalse(form.discardOpen)
    }

    @Test fun backAndDiscardWaitWhileASaveRuns() {
        store.hold = CompletableDeferred()
        form.open()
        typeFood()
        form.save()
        form.leave()
        form.discard()
        assertTrue(form.open)
        assertFalse(form.discardOpen)
        store.hold!!.complete(Unit)
        assertFalse(form.open)
    }

    @Test fun theBarcodeFieldsScanComesBackToTheForm() {
        form.open("Ayran")
        form.beginBarcodeRescan()
        assertEquals("scan", events.last())
        scanned!!("4006381333931")
        assertEquals("4006381333931", form.draft.barcode)
        assertTrue(form.draft.barcodeFromScan)
        assertTrue("the form stays under the scanner", form.open)
    }

    @Test fun whileALogPartIsResolvedANewFoodIsSavedAloneAndPickedForThePart() {
        // B15 review fix: it used to be logged to the Library's meal too, and Add then logged it again from the draft.
        resolvingPart = true
        meal = MealSlot.LUNCH
        form.open("Ayran")
        typeFood()
        assertNull("not kept for process death", form.savedForm())
        form.save()
        assertEquals(listOf("save Ayran"), store.calls)
        assertEquals("Undo is kept", listOf<DiaryChange>(DiaryChange.FoodsChanged), diary.published)
        assertEquals("Saved to your food library.", notices.message)
        assertEquals("saved Ayran new=true advances=false", events.last())
    }

    @Test fun theSaveTargetFollowsThePartTheQueueAndTheOpenMeal() {
        val queue = ResolveQueueView(MealSlot.DINNER, listOf("ayran"), 1, TypedPart("ayran", null, null))
        assertNull(foodSaveTarget(resolvingPart = true, queue = null, openMeal = MealSlot.LUNCH))
        assertEquals(ReviewLogPlan(MealSlot.DINNER, true), foodSaveTarget(false, queue, MealSlot.LUNCH))
        assertNull("a queue without a meal", foodSaveTarget(false, queue.copy(meal = null), MealSlot.LUNCH))
        assertEquals(ReviewLogPlan(MealSlot.LUNCH, false), foodSaveTarget(false, null, MealSlot.LUNCH))
        assertNull(foodSaveTarget(false, null, null))
    }

    @Test fun anOpenFormSurvivesProcessDeathButNotWhileResolving() {
        val state = SavedStateHandle()
        form.open("Ayran")
        form.draft.brand = "Dukat"
        saveFoodForm(state, form.savedForm())
        val restored = FoodForm(
            diary.session, store, notices, { "2026-10-28" }, { null }, { null }, { false }, {}, {}, {}, {}, { _, _, _ -> }, {},
        )
        restoreFoodForm(SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) }))?.let(restored::restore)
        assertTrue(restored.open)
        assertEquals("Dukat", restored.draft.brand)
        restored.leave()
        assertTrue("the restored baseline still sees the change", restored.discardOpen)
        resolvingPart = true
        assertNull(form.savedForm())
        resolvingPart = false
        queue = ResolveQueueView(MealSlot.DINNER, listOf("ayran"), 1, TypedPart("ayran", null, null))
        assertNull("nor while a queue is resolved", form.savedForm())
        queue = null
        form.close()
        assertNull(form.savedForm())
    }

    @Test fun aRestoredEditWhoseFoodIsGoneCloses() {
        form.openEdit(kefir)
        form.closeIfFoodGone()
        assertTrue(form.open)
        diary.foods = emptyList()
        diary.session.refresh()
        form.closeIfFoodGone()
        assertFalse(form.open)
    }

    @Test fun deletingTheFoodBeingEditedClosesItsFormAndNoOtherForm() {
        form.openEdit(kefir)
        form.foodDeleted("ayran")
        assertTrue("another food's delete leaves the edit open", form.open)
        form.foodDeleted(kefir.id)
        assertFalse(form.open)
        assertNull(form.editingFoodId)
        assertEquals("close", events.last())
        // A New food edits no stored food, so no delete closes it.
        form.open("Kefir")
        form.foodDeleted(kefir.id)
        assertTrue(form.open)
    }

    @Test fun anOpenFoodFactsProductSavedHereBecomesAContributionCandidate() {
        form.open("Ayran", "Dukat", "036000291452", fromOff = true)
        form.draft.calories = "40"
        form.save()
        val candidate = offers.single()!!
        assertEquals("036000291452", candidate.barcode)
        assertEquals("Ayran", candidate.name)
        assertEquals("en", candidate.languageCode)
        assertEquals("Open Food Facts", store.saved.single().sourceName)
    }
}

private fun record(id: String, name: String, barcode: String?) = FoodRecord(
    id, name, emptyList(), "Personal food", FoodOrigin.CUSTOM,
    NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(60.0, 3.0, 4.0, 3.0)), barcode = barcode,
)

/** The barcode rule the field shows as it is typed is the one Save refuses by, on New food and on Edit food. */
class FoodBarcodeErrorTest {
    private val foods = listOf(record("a", "Oats", "4006381333931"), record("b", "Milk", "036000291452"))

    @Test fun blankIsNoBarcode() = assertEquals(null, foodBarcodeError("", foods, null))

    @Test fun aCodeAnotherFoodHoldsIsRefusedNamingIt() {
        assertEquals("That barcode already belongs to Oats.", foodBarcodeError("4006381333931", foods, null))
        assertEquals("That barcode already belongs to Oats.", foodBarcodeError("4006381333931", foods, "b"))
    }

    @Test fun aFoodKeepsItsOwnCodeInAnySpelling() {
        assertEquals(null, foodBarcodeError("036000291452", foods, "b"))
        assertEquals(null, foodBarcodeError("0036000291452", foods, "b"))
    }

    @Test fun aPartCodeIsRefused() {
        assertEquals("Enter an 8, 12, 13 or 14 digit barcode or clear the field.", foodBarcodeError("123", foods, "b"))
    }
}
