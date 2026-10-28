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
import com.iron.breakfast.resolve.ResolveQueueView
import com.iron.breakfast.today.OpenDiary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductReviewTest {
    private class FakeStore : ProductStore {
        val calls = mutableListOf<String>()
        override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String) {
            calls += "log ${food.name} $amount ${unit.label} $meal $dayKey"
        }
        override suspend fun updateFood(food: FoodRecord) { calls += "update ${food.name} ${food.nutrition.values.calories}" }
        override suspend fun cacheFood(food: FoodRecord): FoodRecord { calls += "cache ${food.name}"; return food.copy(id = "stored-${food.id}") }
    }

    private val diary = OpenDiary()
    private val store = FakeStore()
    private val notices = Notices()
    private var meal: MealSlot? = null
    private var queue: ResolveQueueView? = null
    private var resolvingPart = false
    private val offers = mutableListOf<OffContributionDraft?>()
    private val events = mutableListOf<String>()
    private val review = ProductReview(
        diary.session, store, notices,
        selectedDay = { "2026-10-28" },
        openMeal = { meal },
        queue = { queue },
        resolvingPart = { resolvingPart },
        typed = { raw -> if (raw == "nutella200g") TypedPart("nutella", 200.0, MeasureUnit.GRAM) else TypedPart(raw, null, null) },
        offerContribution = { offers += it },
        onPartResolved = { events += "resolved ${it.id}" },
        onLogged = { events += "logged advances=$it" },
        onClosed = { events += "closed" },
        openSearch = { events += "search fromResolver=$it" },
        startScan = { events += "scan" },
    )

    private val nutella = FoodRecord(
        "off-1", "Nutella", emptyList(), "Spreads", FoodOrigin.GENERIC,
        NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(539.0, 6.3, 57.5, 30.9)), barcode = "3017620422003",
    )

    @Test fun aReviewStartsFromTheProductAndWhereItOpened() {
        review.choose(nutella)
        assertEquals(nutella, review.product)
        assertEquals(ProductReviewOrigin.LIBRARY, review.origin)
        assertEquals("100.0", review.amount)
        assertEquals("g", review.unit)
        assertEquals("Nutella", review.name)
        assertEquals("539.0", review.calories)
        assertEquals(MealSlot.LUNCH, review.initialMeal())
        meal = MealSlot.DINNER
        review.choose(nutella)
        assertEquals(ProductReviewOrigin.MEAL, review.origin)
        assertEquals(MealSlot.DINNER, review.initialMeal())
    }

    @Test fun aQueuedPartPrefillsItsTypedAmountAndShowsItsPlace() {
        queue = ResolveQueueView(MealSlot.BREAKFAST, listOf("nutella200g", "kefir"), 3)
        review.choose(nutella, fromScan = true)
        assertEquals(ProductReviewOrigin.QUEUE, review.origin)
        assertEquals("200.0", review.amount)
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
        assertEquals("Added to Snacks", notices.message)
        assertNull(review.product)
        assertFalse(review.fromScan)
        assertEquals(listOf("logged advances=false"), events)
        assertEquals("a scanned product saved here is a contribution candidate", "3017620422003", offers.single()?.barcode)
    }

    @Test fun whileAQueueIsResolvedAddUsesItsMealAndAdvancesIt() {
        queue = ResolveQueueView(MealSlot.BREAKFAST, listOf("nutella200g"), 1)
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
        queue = ResolveQueueView(MealSlot.BREAKFAST, listOf("nutella"), 1)
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
        queue = ResolveQueueView(MealSlot.BREAKFAST, listOf("nutella"), 1)
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
