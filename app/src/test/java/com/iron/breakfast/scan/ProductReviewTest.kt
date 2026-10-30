package com.iron.breakfast.scan

import com.iron.breakfast.app.DiaryChange
import com.iron.breakfast.app.Notices
import com.iron.breakfast.data.OffContributionDraft
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.TypedPart
import com.iron.breakfast.domain.ServingConversion
import com.iron.breakfast.domain.withServingsAdded
import com.iron.breakfast.resolve.ResolveQueueView
import com.iron.breakfast.today.OpenDiary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductReviewTest {
    private inner class FakeStore : ProductStore {
        val calls = mutableListOf<String>()
        /** The full product a fetch by barcode answers with; [fetchFails] makes it throw, [fetchHeld] keeps it out. */
        var full: FoodRecord? = null
        var fetchFails = false
        var fetchHeld: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        /** Keeps a merge running, as a slow write would. */
        var mergeHeld: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        val fetches = mutableListOf<String>()
        override suspend fun fullProduct(barcode: String): FoodRecord? {
            fetches += barcode
            fetchHeld?.await()
            if (fetchFails) throw java.io.IOException("offline")
            return full
        }
        override suspend fun addServings(expected: FoodRecord, servings: List<ServingConversion>): Boolean {
            mergeHeld?.await()
            val merged = withServingsAdded(diary.foods.firstOrNull { it.id == expected.id }, expected, servings)
            calls += "add servings ${servings.map { it.name }} to ${expected.id}: ${merged != null}"
            if (merged != null) diary.foods = diary.foods.map { if (it.id == merged.id) merged else it }
            return merged != null
        }
        override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String) {
            calls += "log ${food.name} $amount ${unit.label} $meal $dayKey"
        }
        override suspend fun updateFood(food: FoodRecord) {
            calls += "update ${food.name} ${food.nutrition.values.calories}"
            diary.foods = diary.foods.filterNot { it.id == food.id } + food
        }
        override suspend fun cacheFood(food: FoodRecord): FoodRecord { calls += "cache ${food.name}"; return food.copy(id = "stored-${food.id}") }
    }

    private val diary = OpenDiary()
    private val store = FakeStore()
    private val notices = Notices()
    /** How often "Logged!" was asked for. */
    private var confirmed = 0
    private var meal: MealSlot? = null
    private var onDaily = false
    private var automatic: MealSlot? = null
    private var queue: ResolveQueueView? = null
    private var resolvingPart = false
    private val offers = mutableListOf<OffContributionDraft?>()
    private val events = mutableListOf<String>()
    private val review = ProductReview(
        diary.session, store, notices,
        selectedDay = { "2026-10-28" },
        openMeal = { meal },
        libraryTab = { meal == null && !onDaily },
        automaticMeal = { automatic },
        queue = { queue },
        resolvingPart = { resolvingPart },
        offerContribution = { offers += it },
        onPartResolved = { events += "resolved ${it.id}" },
        onLogged = { events += "logged advances=$it" },
        onClosed = { events += "closed" },
        openSearch = { events += "search fromResolver=$it" },
        startScan = { events += "scan" },
        confirmLogged = { confirmed++ },
        scope = diary.scope,
        io = kotlinx.coroutines.Dispatchers.Unconfined,
    )

    /** A queue as the resolve flow builds it: its current part as the planner reads it ("nutella200g" is nutella, 200 g). */
    private fun queueOf(meal: MealSlot?, parts: List<String>, total: Int) = ResolveQueueView(
        meal, parts, total,
        parts.first().let { raw -> if (raw == "nutella200g") TypedPart("nutella", 200.0, MeasureUnit.GRAM) else TypedPart(raw, null, null) },
    )

    private val nutella = FoodRecord(
        "off-1", "Nutella", emptyList(), "Spreads", FoodOrigin.GENERIC,
        NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(539.0, 6.3, 57.5, 30.9)), barcode = "3017620422003",
    )

    // ---- A search result's servings, fetched by its barcode ----

    /** The full product as a barcode lookup gives it: a can, its package, and other numbers that are not taken. */
    private val nutellaFull = nutella.copy(
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(530.0, 6.0, 57.0, 31.0)),
        conversions = listOf(
            ServingConversion(MeasureUnit.GRAM, 15.0, "Open Food Facts serving size", label = "serving"),
            ServingConversion(MeasureUnit.GRAM, 400.0, "Open Food Facts package", label = "package"),
        ),
    )

    @Test fun aFetchThatArrivesBeforeTheSaveAddsItsServingsToTheProductQuietly() {
        store.full = nutellaFull
        review.choose(nutella)
        assertEquals(listOf("3017620422003"), store.fetches)
        // Only the servings: the search result's numbers, name and the rest stay.
        assertEquals(listOf("serving", "package"), review.product!!.conversions.mapNotNull { it.name })
        assertEquals(nutella.nutrition, review.product!!.nutrition)
        assertEquals("539", review.calories)
        assertNull(notices.message)
        review.saveToLibrary()
        assertEquals(listOf("serving", "package"), diary.foods.single().conversions.mapNotNull { it.name })
        assertEquals(539.0, diary.foods.single().nutrition.values.calories, 0.0)
    }

    @Test fun aFetchThatArrivesAfterTheSaveAddsItsServingsToTheStoredFood() {
        store.full = nutellaFull
        store.fetchHeld = kotlinx.coroutines.CompletableDeferred()
        review.choose(nutella)
        // The save does not wait for it.
        review.saveToLibrary()
        assertTrue(diary.foods.single().conversions.isEmpty())
        store.fetchHeld!!.complete(Unit)
        assertEquals("add servings [serving, package] to off-1: true", store.calls.last())
        assertEquals(listOf("serving", "package"), diary.foods.single().conversions.mapNotNull { it.name })
        assertEquals(nutella.nutrition, diary.foods.single().nutrition)
        // The save's change, then the merge's: an open food page shows the servings.
        assertEquals(listOf<DiaryChange>(DiaryChange.FoodsChanged, DiaryChange.FoodsChanged), diary.published)
        assertEquals(listOf("serving", "package"), diary.session.foods.single().conversions.mapNotNull { it.name })
    }

    /** Fix pass, 2: a merge running never holds the user's gate, so a tap meanwhile is not refused. */
    @Test fun aMergeRunningNeverRefusesAUserWrite() {
        store.full = nutellaFull
        store.fetchHeld = kotlinx.coroutines.CompletableDeferred()
        store.mergeHeld = kotlinx.coroutines.CompletableDeferred()
        review.choose(nutella)
        review.saveToLibrary()
        store.fetchHeld!!.complete(Unit)
        // The merge has started and waits on the store; the user can still write.
        assertTrue(diary.session.canWrite)
        assertFalse(diary.session.busy)
        diary.commit(DiaryChange.EntryEdited)
        store.mergeHeld!!.complete(Unit)
        assertEquals(listOf<DiaryChange>(DiaryChange.FoodsChanged, DiaryChange.EntryEdited, DiaryChange.FoodsChanged), diary.published)
    }

    @Test fun aFetchThatFailsChangesNothing() {
        store.fetchFails = true
        review.choose(nutella)
        assertEquals(nutella, review.product)
        assertNull(notices.message)
        review.saveToLibrary()
        assertTrue(diary.foods.single().conversions.isEmpty())
        assertTrue(store.calls.none { it.startsWith("add servings") })
    }

    @Test fun aFoodEditedBeforeTheFetchArrivesIsLeftAsTheUserMadeIt() {
        store.full = nutellaFull
        store.fetchHeld = kotlinx.coroutines.CompletableDeferred()
        review.choose(nutella)
        review.saveToLibrary()
        // Edited in between: a serving of the user's own, and a new name.
        val edited = diary.foods.single().copy(name = "My Nutella", conversions = listOf(ServingConversion(MeasureUnit.GRAM, 20.0, "personal serving", label = "spoon")))
        diary.foods = listOf(edited)
        diary.session.refresh()
        store.fetchHeld!!.complete(Unit)
        assertEquals("add servings [serving, package] to off-1: false", store.calls.last())
        assertEquals(listOf(edited), diary.foods)
        // Nothing was merged: no change is published for it.
        assertEquals(listOf<DiaryChange>(DiaryChange.FoodsChanged), diary.published)
    }

    @Test fun leavingTheReviewCancelsItsFetch() {
        store.full = nutellaFull
        store.fetchHeld = kotlinx.coroutines.CompletableDeferred()
        review.choose(nutella)
        review.close()
        store.fetchHeld!!.complete(Unit)
        assertTrue(store.calls.none { it.startsWith("add servings") })
        assertNull(review.product)
        // A scanned product is the full one already: it is not fetched again.
        store.fetches.clear()
        review.choose(nutella, fromScan = true)
        assertTrue(store.fetches.isEmpty())
    }

    @Test fun onlyServingsInTheProductsBasisAreTaken() {
        val inMl = nutellaFull.copy(nutrition = NutritionBasis(100.0, MeasureUnit.MILLILITER, nutella.nutrition.values),
            conversions = listOf(ServingConversion(MeasureUnit.MILLILITER, 330.0, "Open Food Facts serving size", label = "can")))
        assertTrue(servingsToAdd(nutella, inMl).isEmpty())
        val has = nutella.copy(conversions = listOf(ServingConversion(MeasureUnit.GRAM, 10.0, "personal serving", label = "Serving")))
        assertEquals(listOf("package"), servingsToAdd(has, nutellaFull).map { it.name })
        assertNull(withServingsAdded(null, nutella, nutellaFull.conversions))
        assertNull("nothing to add", withServingsAdded(nutellaFull, nutellaFull, nutellaFull.conversions))
    }

    @Test fun aReviewFromDailyLogsStartingOnTheAutomaticMeal() {
        onDaily = true
        automatic = MealSlot.DINNER
        review.choose(nutella)
        assertEquals(ProductReviewOrigin.MEAL, review.origin)
        assertEquals(MealSlot.DINNER, review.initialMeal())
        automatic = null
        assertEquals("Ask has no meal of its own", MealSlot.LUNCH, review.initialMeal())
        review.add(MealSlot.DINNER)
        assertEquals("log Nutella 100.0 g DINNER 2026-10-28", store.calls.single())
    }

    @Test fun aReviewFromDailyCannotJustSaveToTheLibrary() {
        onDaily = true
        review.choose(nutella)
        review.saveToLibrary()
        assertTrue(store.calls.isEmpty())
    }

    @Test fun aTinyOpenFoodFactsValueSurvivesTheReview() {
        val salty = nutella.copy(nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(0.5, 5.0E-4, 0.0, null)))
        review.choose(salty)
        assertEquals("0.0005", review.protein)
        assertEquals(5.0E-4, review.calculation(salty)!!.nutrition.protein!! * 100.0 / 100.0, 1e-9)
    }

    @Test fun aReviewStartsFromTheProductAndWhereItOpened() {
        review.choose(nutella)
        assertEquals(nutella, review.product)
        assertEquals(ProductReviewOrigin.LIBRARY, review.origin)
        assertEquals("100", review.amount)
        assertEquals("g", review.unit)
        assertEquals("Nutella", review.name)
        assertEquals("539", review.calories)
        assertEquals(MealSlot.LUNCH, review.initialMeal())
        meal = MealSlot.DINNER
        review.choose(nutella)
        assertEquals(ProductReviewOrigin.MEAL, review.origin)
        assertEquals(MealSlot.DINNER, review.initialMeal())
    }

    @Test fun aQueuedPartPrefillsItsTypedAmountAndShowsItsPlace() {
        queue = queueOf(MealSlot.BREAKFAST, listOf("nutella200g", "kefir"), 3)
        review.choose(nutella, fromScan = true)
        assertEquals(ProductReviewOrigin.QUEUE, review.origin)
        assertEquals("200", review.amount)
        assertEquals(MealSlot.BREAKFAST, review.queueMeal())
        assertEquals(MealSlot.BREAKFAST, review.initialMeal())
        assertEquals("2 of 3", review.resolveQueue()?.position())
        assertEquals("kefir", review.resolveQueue()?.next)
    }

    @Test fun addLogsTheEditedProductToTheChosenMeal() {
        review.choose(nutella, fromScan = true)
        review.onAmount("15")
        review.onName("Nutella jar")
        review.add(MealSlot.SNACKS)
        assertEquals(listOf("log Nutella jar 15.0 g SNACKS 2026-10-28"), store.calls)
        assertEquals(listOf<DiaryChange>(DiaryChange.FoodsLogged), diary.published)
        assertEquals(1, confirmed)
        assertNull(notices.message)
        assertNull(review.product)
        assertFalse(review.fromScan)
        assertEquals(listOf("logged advances=false"), events)
        assertEquals("a scanned product saved here is a contribution candidate", "3017620422003", offers.single()?.barcode)
    }

    @Test fun whileAQueueIsResolvedAddUsesItsMealAndAdvancesIt() {
        queue = queueOf(MealSlot.BREAKFAST, listOf("nutella200g"), 1)
        review.choose(nutella)
        review.add(MealSlot.SNACKS)
        assertEquals(listOf("log Nutella 200.0 g BREAKFAST 2026-10-28"), store.calls)
        assertEquals(listOf("logged advances=true"), events)
        assertEquals("a product from the search is no candidate", listOf<OffContributionDraft?>(null), offers)
    }

    @Test fun anAmountThatDoesNotReadIsRefusedBeforeAnyWrite() {
        review.choose(nutella)
        review.onAmount("0")
        assertNull(review.calculation(nutella))
        review.add(MealSlot.LUNCH)
        review.onAmount("20")
        review.onUnit("furlong")
        review.add(MealSlot.LUNCH)
        assertEquals("Enter a positive amount, a supported unit, and valid calories.", notices.message)
        assertTrue(store.calls.isEmpty())
        review.onUnit("g")
        review.onCalories("400")
        assertEquals(80.0, review.calculation(nutella)!!.nutrition.calories, 1e-9)
    }

    @Test fun saveToLibraryStoresTheEditedFoodOnlyFromTheLibrary() {
        meal = MealSlot.LUNCH
        review.choose(nutella)
        review.saveToLibrary()
        assertTrue("not from a meal", store.calls.isEmpty())
        meal = null
        review.choose(nutella)
        queue = queueOf(MealSlot.BREAKFAST, listOf("nutella"), 1)
        review.saveToLibrary()
        assertTrue("not for a queued part", store.calls.isEmpty())
        queue = null
        review.onCalories("530")
        review.saveToLibrary()
        assertEquals(listOf("update Nutella 530.0"), store.calls)
        assertEquals(listOf<DiaryChange>(DiaryChange.FoodsChanged), diary.published)
        assertEquals("Saved Nutella to your food library.", notices.message)
        assertNull(review.product)
    }

    @Test fun whileALogPartIsResolvedTheProductIsStoredAndPickedForIt() {
        resolvingPart = true
        review.choose(nutella, fromScan = true)
        review.add(MealSlot.LUNCH)
        assertEquals(listOf("cache Nutella"), store.calls)
        assertEquals(listOf<DiaryChange>(DiaryChange.FoodsChanged), diary.published)
        assertEquals(listOf("resolved stored-off-1"), events)
        assertEquals("3017620422003", offers.single()?.barcode)
        assertFalse(review.fromScan)
        // Save to Library takes the same path.
        review.saveToLibrary()
        assertEquals(listOf("cache Nutella", "cache Nutella"), store.calls)
    }

    @Test fun closingWritesNothingAndLetsTheCoordinatorReturn() {
        review.choose(nutella, fromScan = true, origin = ProductReviewOrigin.QUEUE)
        review.close()
        assertNull(review.product)
        assertFalse(review.fromScan)
        assertEquals(ProductReviewOrigin.LIBRARY, review.origin)
        assertEquals(listOf("closed"), events)
        assertTrue(store.calls.isEmpty())
    }

    @Test fun findBetterMatchAndRescanLeaveThroughTheirScreens() {
        queue = queueOf(MealSlot.BREAKFAST, listOf("nutella"), 1)
        review.choose(nutella)
        review.findBetterMatch()
        assertEquals(listOf("closed", "search fromResolver=true"), events)
        queue = null
        review.choose(nutella)
        review.rescan()
        assertEquals(listOf("closed", "search fromResolver=true", "closed", "scan"), events)
    }

    @Test fun nothingIsWrittenWhileAnotherWriteRuns() {
        diary.session.write(DiaryChange.FoodsChanged) { kotlinx.coroutines.CompletableDeferred<Unit>().await() }
        review.choose(nutella)
        review.add(MealSlot.LUNCH)
        review.saveToLibrary()
        assertTrue(store.calls.isEmpty())
        assertEquals(nutella, review.product)
    }
}
