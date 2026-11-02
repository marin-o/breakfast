package com.bosa.breakfast.food

import com.bosa.breakfast.testFood
import androidx.lifecycle.SavedStateHandle
import com.bosa.breakfast.resolve.ReviewLogPlan
import com.bosa.breakfast.app.DiaryChange
import com.bosa.breakfast.app.Notices
import com.bosa.breakfast.app.restoreFoodForm
import com.bosa.breakfast.app.saveFoodForm
import com.bosa.breakfast.data.OffContributionDraft
import com.bosa.breakfast.domain.FoodRecord
import com.bosa.breakfast.domain.MealSlot
import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.domain.NutritionValues
import com.bosa.breakfast.domain.TypedPart
import com.bosa.breakfast.domain.servingNamed
import com.bosa.breakfast.resolve.ResolveQueueView
import com.bosa.breakfast.today.OpenDiary
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
        onSavedToLog = { food, origin -> events += "review ${food.name} $origin" },
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

    @Test fun aNewFoodFromALoggingScanIsStoredAloneThenGoesToTheReview() {
        meal = MealSlot.DINNER
        form.open("Ajvar", barcode = "5901234123457", reviewAfterSave = com.bosa.breakfast.scan.ProductReviewOrigin.LOG_PART)
        form.draft.calories = "100"
        events.clear()
        form.save()
        assertEquals("not logged by the form", 0, confirmed)
        assertNull("no saved-alone notice", notices.message)
        assertEquals(listOf("saved Ajvar new=true advances=false", "review Ajvar LOG_PART"), events)
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

    @Test fun aQueueWithoutAMealYetStoresTheFoodAloneAndGoesToTheReview() {
        queue = ResolveQueueView(null, listOf("ayran"), 1, TypedPart("ayran", null, null))
        form.open(fromResolver = true)
        typeFood()
        events.clear()
        form.save()
        assertEquals("nothing logged", listOf("save Ayran"), store.calls)
        assertEquals(0, confirmed)
        assertNull(notices.message)
        assertFalse(form.open)
        assertEquals(listOf("saved Ayran new=true advances=false", "review Ayran QUEUE"), events)
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
        assertNull("nothing covers the next step", notices.message)
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

    // ---- Servings on the first add ----

    private fun addBar() {
        form.draft.openServing(form.draft.basis())
        form.draft.servingLabel = "bar"
        form.draft.servingAmount = "20"
        assertTrue(form.draft.commitServing())
    }

    private fun assertBarStored() {
        val saved = store.saved.single()
        assertEquals(listOf("bar"), saved.servings.map { it.label })
        assertEquals(20.0, saved.servings.single().baseUnitsPerServing, 0.0)
        assertEquals(103.0, saved.density!!, 0.0)
    }

    private fun typeFoodWithBar() {
        typeFood()
        form.draft.density = "103"
        addBar()
    }

    @Test fun servingsAndDensityGoWithANewFoodSavedAlone() {
        form.open()
        typeFoodWithBar()
        form.save()
        assertEquals(listOf("save Ayran"), store.calls)
        assertBarStored()
    }

    @Test fun servingsAndDensityGoWithANewFoodLoggedToAMeal() {
        meal = MealSlot.LUNCH
        form.open()
        typeFoodWithBar()
        form.save()
        assertEquals("save and log Ayran to LUNCH on 2026-10-28", store.calls.single())
        assertBarStored()
    }

    @Test fun servingsAndDensityGoWithANewFoodPickedForAPart() {
        // A Log part's or a recipe ingredient's: both are a part being resolved.
        resolvingPart = true
        form.open("Ayran")
        typeFoodWithBar()
        form.save()
        assertEquals(listOf("save Ayran"), store.calls)
        assertBarStored()
    }

    @Test fun servingsAndDensityGoWithANewFoodOnItsWayToAReview() {
        form.open("Ayran", barcode = "5901234123457", reviewAfterSave = com.bosa.breakfast.scan.ProductReviewOrigin.MEAL)
        typeFoodWithBar()
        form.save()
        assertEquals("review Ayran MEAL", events.last())
        assertBarStored()
    }

    @Test fun servingsAndDensityGoWithANewFoodForAQueue() {
        queue = ResolveQueueView(MealSlot.DINNER, listOf("ayran"), 1, TypedPart("ayran", null, null))
        form.open("Ayran")
        typeFoodWithBar()
        form.save()
        assertEquals("save and log Ayran to DINNER on 2026-10-28", store.calls.single())
        assertBarStored()
    }

    @Test fun theShorthandsBasisIsTheServingsBasis() {
        form.open()
        form.draft.name = "Milk"
        form.draft.shorthand = "per 100 ml: 50 kcal"
        form.draft.openServing(form.draft.basis())
        assertEquals("the form starts on it", MeasureUnit.MILLILITER, form.draft.servingUnit)
        form.draft.servingLabel = "glass"
        form.draft.servingAmount = "250"
        assertTrue("no density needed", form.draft.commitServing())
        assertEquals(MeasureUnit.MILLILITER, form.draft.shownServingUnit())
        form.save()
        assertEquals(MeasureUnit.MILLILITER, store.saved.single().basisUnit)
        assertEquals(250.0, store.saved.single().servings.single().baseUnitsPerServing, 0.0)
    }

    @Test fun aServingStillInItsFormGoesWithSave() {
        form.open()
        typeFood()
        form.draft.openServing(MeasureUnit.GRAM)
        form.draft.servingLabel = "bar"
        form.draft.servingAmount = "20,5"
        form.save()
        assertEquals(20.5, store.saved.single().servings.single().baseUnitsPerServing, 0.0)
    }

    @Test fun aServingStillInItsFormGoesWithAnEditsSaveToo() {
        form.openEdit(kefir)
        form.draft.openServing(MeasureUnit.GRAM)
        form.draft.servingLabel = "bar"
        form.draft.servingAmount = "20"
        form.save()
        assertEquals(20.0, store.updated.single().servingNamed("bar")!!.baseUnitsPerServing, 0.0)
    }

    @Test fun anUntouchedServingFormDoesNotStopSaveAndAddsNothing() {
        form.open()
        typeFood()
        form.draft.openServing(MeasureUnit.GRAM)
        form.save()
        assertEquals(listOf("save Ayran"), store.calls)
        assertTrue(store.saved.single().servings.isEmpty())
    }

    @Test fun anOpenServingThatDoesNotReadStopsSaveWithNothingStored() {
        form.open()
        typeFood()
        form.draft.openServing(MeasureUnit.GRAM)
        form.draft.servingLabel = "bar"
        form.save()
        assertEquals("a name and no amount", POSITIVE, notices.message)
        form.draft.servingLabel = ""
        form.draft.servingAmount = "20"
        form.save()
        assertEquals("an amount and no name", "Name is required", notices.message)
        form.draft.servingLabel = "bar"
        form.draft.servingUnit = MeasureUnit.MILLILITER
        form.save()
        assertTrue("ml on a food on g, with no density", form.draft.densityError())
        assertEquals(POSITIVE, notices.message)
        assertTrue(store.calls.isEmpty())
        assertTrue("the form keeps what was typed", form.open && form.draft.servingFormOpen && form.draft.servingLabel == "bar")
    }

    @Test fun aBasisChangedAfterAServingGoesThroughTheDensity() {
        form.open()
        typeFood()
        addBar()
        form.draft.basisEdited = true
        form.draft.basisUnit = "ml"
        // No density: it keeps its value in g, and nothing saves it as 20 ml.
        assertEquals(MeasureUnit.GRAM, form.draft.shownServingUnit())
        assertEquals(20.0, form.draft.shownServings().single().baseUnitsPerServing, 0.0)
        assertTrue(form.draft.densityError())
        form.save()
        assertTrue(store.calls.isEmpty())
        // Back on g it is as it was, with no drift.
        form.draft.basisUnit = "g"
        assertFalse(form.draft.densityError())
        form.draft.basisUnit = "ml"
        form.draft.density = "50"
        assertEquals(MeasureUnit.MILLILITER, form.draft.shownServingUnit())
        assertEquals(40.0, form.draft.shownServings().single().baseUnitsPerServing, 1e-9)
        form.save()
        assertEquals(40.0, store.saved.single().servings.single().baseUnitsPerServing, 1e-9)
        assertEquals(MeasureUnit.MILLILITER, store.saved.single().basisUnit)
    }

    @Test fun removingTheServingLiftsTheNeedForADensity() {
        form.open()
        typeFood()
        addBar()
        form.draft.basisEdited = true
        form.draft.basisUnit = "ml"
        assertTrue(form.draft.densityError())
        form.draft.removeServing(0)
        assertFalse(form.draft.densityError())
    }

    @Test fun aNewFoodsServingsSurviveProcessDeathAndCountAsAChange() {
        val state = SavedStateHandle()
        form.open("Ayran")
        val clean = form.savedForm()!!
        assertFalse(form.draft.isDirty(clean.baseline))
        addBar()
        assertTrue(form.draft.isDirty(clean.baseline))
        form.draft.basisEdited = true
        form.draft.basisUnit = "ml"
        saveFoodForm(state, form.savedForm())
        val restored = FoodDraft().apply { restore(restoreFoodForm(SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) }))!!.draft) }
        assertEquals(listOf("bar"), restored.servings.map { it.label })
        assertTrue(restored.isDirty(clean.baseline))
        assertEquals("the basis it was added in comes back too", MeasureUnit.GRAM, restored.shownServingUnit())
        assertTrue(restored.densityError())
    }
}

private val POSITIVE = com.bosa.breakfast.domain.POSITIVE_AMOUNT_MESSAGE

private fun record(id: String, name: String, barcode: String?) =
    testFood(id, name, category = "Personal food", values = NutritionValues(60.0, 3.0, 4.0, 3.0), barcode = barcode)

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

    @Test fun aFoodStoresItsOwnSpellingWhenUnchangedAndTheCanonicalOneWhenNew() {
        assertEquals("036000291452", storedBarcodeFor("0036000291452", foods, "b"))
        assertEquals("0036000291452", storedBarcodeFor("036000291452", foods, null))
    }

    @Test fun onlyNewFoodMarksACodeFromAScan() {
        assertEquals(true, barcodeMarkedFromScan(true, null))
        assertEquals(false, barcodeMarkedFromScan(true, "b"))
        assertEquals(false, barcodeMarkedFromScan(false, null))
    }

    @Test fun aPartCodeIsRefused() {
        assertEquals("Enter an 8, 12, 13 or 14 digit barcode or clear the field.", foodBarcodeError("123", foods, "b"))
    }
}
