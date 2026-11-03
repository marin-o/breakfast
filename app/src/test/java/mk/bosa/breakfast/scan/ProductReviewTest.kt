package mk.bosa.breakfast.scan

import mk.bosa.breakfast.app.DiaryChange
import mk.bosa.breakfast.app.Notices
import mk.bosa.breakfast.data.OffContributionDraft
import mk.bosa.breakfast.domain.FoodOrigin
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.MealSlot
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.NutritionBasis
import mk.bosa.breakfast.domain.NutritionValues
import mk.bosa.breakfast.domain.TypedPart
import mk.bosa.breakfast.domain.ServingConversion
import mk.bosa.breakfast.domain.withServingsAdded
import mk.bosa.breakfast.domain.servingNamed
import mk.bosa.breakfast.resolve.ResolveQueueView
import mk.bosa.breakfast.ui.screens.labelFor
import mk.bosa.breakfast.today.OpenDiary
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
        override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String, servingName: String?) {
            calls += "log ${food.name} $amount ${servingName ?: unit.label} $meal $dayKey"
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
    private var automatic: MealSlot? = null
    private var queue: ResolveQueueView? = null
    private var resolvingPart = false
    private val offers = mutableListOf<OffContributionDraft?>()
    private val events = mutableListOf<String>()
    private val review = ProductReview(
        diary.session, store, notices,
        selectedDay = { "2026-10-28" },
        openMeal = { meal },
        automaticMeal = { automatic },
        queue = { queue },
        resolvingPart = { resolvingPart },
        offerContribution = { offers += it },
        onPartResolved = { events += "resolved ${it.id}" },
        onLogPart = { food, amount, measure, replaces -> events += "part ${food.id} $amount ${measure.label}" + (replaces?.let { " for ${it.text}" } ?: "") },
        onLogged = { events += "logged advances=$it" },
        onClosed = { events += "closed" },
        openSearch = { fromResolver, origin -> events += "search fromResolver=$fromResolver" + (origin?.let { " $it" } ?: "") },
        startScan = { events += "scan" },
        confirmLogged = { confirmed++ },
        scope = diary.scope,
        io = kotlinx.coroutines.Dispatchers.Unconfined,
        onCataloguePicked = { events += "picked $it" },
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

    // ---- A food form's contribution offer ----

    private val saved = nutella.copy(id = "custom-ayran", name = "Ayran")

    @Test fun addKeepsTheOfferOfTheFoodFormThatCameHere() {
        review.choose(saved, origin = ProductReviewOrigin.MEAL, fetchServings = false, offersContribution = true)
        assertFalse("not a scan: the screens beneath stay", review.fromScan)
        review.add(MealSlot.LUNCH)
        assertEquals(saved.barcode, offers.single()!!.barcode)
    }

    @Test fun addToTheLogKeepsTheOfferToo() {
        review.choose(saved, origin = ProductReviewOrigin.LOG_PART, fetchServings = false, offersContribution = true)
        review.addToLog()
        assertEquals(saved.barcode, offers.single()!!.barcode)
    }

    @Test fun backTouchesNoOffer() {
        review.choose(saved, origin = ProductReviewOrigin.MEAL, fetchServings = false, offersContribution = true)
        review.dismiss()
        assertEquals(emptyList<OffContributionDraft?>(), offers)
    }

    @Test fun aReviewThatOffersNothingStillDropsAnEarlierCandidate() {
        review.choose(saved, origin = ProductReviewOrigin.MEAL, fetchServings = false)
        review.add(MealSlot.LUNCH)
        assertEquals(listOf<OffContributionDraft?>(null), offers)
    }

    // ---- A USDA row ----

    private val chicken = FoodRecord(
        "fndds_1", "Chicken, breast", emptyList(), "USDA FoodData Central", FoodOrigin.GENERIC,
        NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(200.0, 20.0, 5.0, 10.0)),
        conversions = listOf(
            ServingConversion(MeasureUnit.GRAM, 1.0, "per 100 g"),
            ServingConversion(MeasureUnit.GRAM, 140.0, "portion", approximate = true, label = "cup, cooked, diced"),
        ),
        defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM,
    )

    @Test fun aUsdaRowStartsAt100GramsWithNoAmountTypedAndWritesNothingBeforeAdd() {
        review.choose(chicken, origin = ProductReviewOrigin.LOG_PART, typed = TypedPart("chicken", null, null))
        assertEquals("100", review.amount)
        assertEquals("g", review.unit)
        assertEquals(emptyList<String>(), store.calls)
        assertEquals(emptyList<String>(), events)
    }

    @Test fun aUsdaRowTakesTheTypedAmountAndPutsTheFoodInThePlaceOfItsPart() {
        val part = mk.bosa.breakfast.domain.SegmentSpan("200 g chicken", 0, 13)
        review.choose(chicken, origin = ProductReviewOrigin.LOG_PART, typed = TypedPart("chicken", 200.0, MeasureUnit.GRAM), replaces = part)
        assertEquals("200", review.amount)
        review.addToLog()
        assertEquals("part stored-fndds_1 200.0 g for 200 g chicken", events.first())
    }

    @Test fun aChosenServingCountsItsWeightAndGoesBackByItsName() {
        review.choose(chicken, origin = ProductReviewOrigin.LOG_PART)
        review.onMeasure(mk.bosa.breakfast.domain.Measure(MeasureUnit.SERVING, "cup, cooked, diced"))
        review.onAmount("2")
        assertEquals(560.0, review.calculation(chicken)!!.nutrition.calories, 1e-9)
        review.addToLog()
        assertEquals("part stored-fndds_1 2.0 cup, cooked, diced", events.first())
    }

    @Test fun addingAUsdaFoodCountsItsTapAsAPickButAClosedReviewDoesNot() {
        review.choose(chicken, origin = ProductReviewOrigin.LOG_PART)
        review.close()
        assertTrue(events.none { it.startsWith("picked") })
        review.choose(chicken, origin = ProductReviewOrigin.LOG_PART)
        review.addToLog()
        assertTrue("picked stored-fndds_1" in events)
    }

    private fun served(id: String, name: String, vararg servings: String) = chicken.copy(
        id = id, name = name,
        conversions = listOf(chicken.conversions.first()) + servings.map { ServingConversion(MeasureUnit.GRAM, 50.0, "portion", approximate = true, label = it) },
    )

    private val breast = served("fndds_2", "Chicken breast", "cup, cooked, diced", "small breast", "medium breast", "large breast", "breast, NS as to size", "oz, cooked")
    private val milk = served("fndds_3", "Milk, NFS", "fl oz", "individual school container")
    private val cupOnly = served("fndds_4", "Rice", "cup, cooked", "fl oz")

    @Test fun aBareCountStartsOnTheServingTheTypedWordsName() {
        assertEquals(ProductPrefill(2.0, MeasureUnit.SERVING, "breast, NS as to size"), productPrefill(breast, TypedPart("chicken breast", 2.0, null)))
        assertEquals(ProductPrefill(100.0, MeasureUnit.GRAM), productPrefill(breast, TypedPart("chicken breast", null, null)))
        assertEquals(ProductPrefill(2.0, MeasureUnit.GRAM), productPrefill(nutella, TypedPart("nutella", 2.0, null)))
    }

    @Test fun aBareCountWithNoServingNamedPrefersAWholeItemOverACupOrFlOz() {
        assertEquals("individual school container", productPrefill(milk, TypedPart("milk", 2.0, null)).servingName)
        assertEquals("medium", productPrefill(served("fndds_5", "Apple", "small", "medium", "large", "slice"), TypedPart("apple", 2.0, null)).servingName)
    }

    @Test fun aBareCountWithNoWholeItemServingStartsOnPiecesAndSaysItHasNoValue() {
        assertEquals(ProductPrefill(2.0, MeasureUnit.PIECE), productPrefill(cupOnly, TypedPart("rice", 2.0, null)))
        review.choose(cupOnly, origin = ProductReviewOrigin.LOG_PART, typed = TypedPart("rice", 2.0, null))
        assertEquals("piece", review.unit)
        assertNull(review.calculation(cupOnly))
        assertEquals(MeasureUnit.PIECE, review.noValue(cupOnly)!!.unit)
    }

    @Test fun aCupTheFoodHasNoPortionForIsNoValueInTheReview() {
        review.choose(milk, origin = ProductReviewOrigin.LOG_PART, typed = TypedPart("milk", 1.0, MeasureUnit.CUP))
        val noValue = review.noValue(milk)!!
        assertEquals("No value for cup", mk.bosa.breakfast.ui.screens.noValueLine(noValue.servingName ?: noValue.unit.labelFor(noValue.amount)))
        review.onUnit("g")
        assertNull(review.noValue(milk))
    }

    @Test fun pickingAUsdaFoodAlreadyStoredReviewsItAsStoredAndKeepsEditsMadeThere() {
        val stored = chicken.copy(name = "My chicken", nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(111.0, 1.0, 1.0, 1.0)), personalOverride = true)
        diary.foods = listOf(stored)
        diary.session.refresh()
        review.choose(chicken, origin = ProductReviewOrigin.LOG_PART)
        assertEquals(stored, review.product)
        assertEquals("My chicken", review.name)
        assertEquals("111", review.calories)
        review.addToLog()
        assertEquals("nothing edited, nothing updated", listOf("cache My chicken"), store.calls)
        store.calls.clear()
        review.choose(chicken, origin = ProductReviewOrigin.LOG_PART)
        review.onCalories("150")
        review.addToLog()
        assertEquals(listOf("update My chicken 150.0", "cache My chicken"), store.calls)
    }

    private val leftPart = mk.bosa.breakfast.domain.SegmentSpan("2 chicken", 0, 9)

    @Test fun findBetterMatchKeepsThePartAndItsAmountForTheFoodItFinds() {
        review.choose(chicken, origin = ProductReviewOrigin.LOG_PART, typed = TypedPart("chicken", 2.0, MeasureUnit.GRAM), replaces = leftPart)
        review.findBetterMatch()
        assertTrue("search fromResolver=false LOG_PART" in events)
        review.choose(nutella, origin = ProductReviewOrigin.LOG_PART)
        assertEquals("2", review.amount)
        review.addToLog()
        assertTrue(events.any { it.endsWith("for 2 chicken") })
    }

    @Test fun rescanKeepsThePartAndItsAmountForWhatItReads() {
        review.choose(chicken, origin = ProductReviewOrigin.LOG_PART, typed = TypedPart("chicken", 2.0, MeasureUnit.GRAM), replaces = leftPart)
        review.rescan()
        review.choose(nutella, fromScan = true, origin = ProductReviewOrigin.LOG_PART)
        review.addToLog()
        assertTrue(events.any { it.endsWith("for 2 chicken") })
    }

    @Test fun aSearchLeftWithNothingPickedForgetsThePart() {
        review.choose(chicken, origin = ProductReviewOrigin.LOG_PART, typed = TypedPart("chicken", 2.0, MeasureUnit.GRAM), replaces = leftPart)
        review.findBetterMatch()
        review.forgetPartLeft()
        review.choose(nutella, origin = ProductReviewOrigin.LOG_PART)
        assertEquals("100", review.amount)
        review.addToLog()
        assertTrue(events.none { it.endsWith("for 2 chicken") })
    }

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

    @Test fun aReviewFromTheLogStoresTheFoodAndHandsItBackWithItsAmount() {
        review.choose(nutella, fromScan = true, origin = ProductReviewOrigin.LOG_PART)
        assertEquals(ProductReviewOrigin.LOG_PART, review.origin)
        review.onAmount("30")
        review.addToLog()
        assertEquals("cache Nutella", store.calls.single())
        assertEquals("part stored-off-1 30.0 g", events.last())
        assertNull(review.product)
        assertEquals("no Logged!: nothing is in the diary yet", 0, confirmed)
    }

    @Test fun aNewPartOverAQueueDoesNotTakeTheQueuedAmount() {
        queue = queueOf(MealSlot.BREAKFAST, listOf("nutella200g"), 1)
        review.choose(nutella, fromScan = true, origin = ProductReviewOrigin.LOG_PART)
        assertEquals("100", review.amount)
        assertNull(review.queueMeal())
    }

    @Test fun closingAReviewFromTheLogWritesNothing() {
        review.choose(nutella, fromScan = true, origin = ProductReviewOrigin.LOG_PART)
        review.close()
        assertTrue(store.calls.isEmpty())
    }

    @Test fun aReviewFromDailyLogsStartingOnTheAutomaticMeal() {
        automatic = MealSlot.DINNER
        review.choose(nutella, origin = ProductReviewOrigin.MEAL)
        assertEquals(ProductReviewOrigin.MEAL, review.origin)
        assertEquals(MealSlot.DINNER, review.initialMeal())
        automatic = null
        assertEquals("Ask has no meal of its own", MealSlot.LUNCH, review.initialMeal())
        review.add(MealSlot.DINNER)
        assertEquals("log Nutella 100.0 g DINNER 2026-10-28", store.calls.single())
    }

    @Test fun aReviewFromDailyCannotJustSaveToTheLibrary() {
        review.choose(nutella, origin = ProductReviewOrigin.MEAL)
        review.saveToLibrary()
        assertTrue(store.calls.isEmpty())
    }

    @Test fun withNoOriginGivenAReviewWithNoMealOpenSavesToTheLibrary() {
        review.choose(nutella)
        assertEquals(ProductReviewOrigin.LIBRARY, review.origin)
    }

    @Test fun aFoodJustMadeByHandFetchesNothing() {
        store.full = nutellaFull
        review.choose(nutella, origin = ProductReviewOrigin.MEAL, fetchServings = false)
        assertTrue("no network request", store.fetches.isEmpty())
        assertEquals(nutella, review.product)
    }

    @Test fun findBetterMatchCarriesALoggingOriginOnly() {
        review.choose(nutella, origin = ProductReviewOrigin.LOG_PART)
        review.findBetterMatch()
        assertEquals("search fromResolver=false LOG_PART", events.last())
        review.choose(nutella)
        review.findBetterMatch()
        assertEquals("search fromResolver=false", events.last())
    }

    @Test fun anUntouchedReviewKeepsTheExactNumbers() {
        val third = nutella.copy(nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(100.0 / 3, 1.0 / 3, 0.0, null)))
        review.choose(third, origin = ProductReviewOrigin.MEAL)
        assertEquals("0.333", review.protein)
        assertEquals(1.0 / 3, review.calculation(third)!!.nutrition.protein!!, 1e-12)
        review.onProtein("0.5")
        assertEquals(0.5, review.calculation(third)!!.nutrition.protein!!, 1e-12)
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

    // ---- Servings added under "Edit details" ----

    private fun addBar() {
        review.servingForm.openServing(MeasureUnit.GRAM)
        review.servingForm.servingLabel = "bar"
        review.servingForm.servingAmount = "20"
    }

    @Test fun aServingAddedInAReviewIsCountedAndGoesWithWhatIsStored() {
        // Saved from the Library.
        review.choose(nutella, fromScan = true, origin = ProductReviewOrigin.LIBRARY)
        assertTrue(review.isNewProduct(nutella))
        addBar()
        assertTrue(review.servingForm.commitServing())
        val reviewed = review.withServings(nutella)
        assertEquals(20.0, reviewed.servingNamed("bar")!!.baseUnitsPerServing, 0.0)
        review.onMeasure(mk.bosa.breakfast.domain.Measure(MeasureUnit.SERVING, "bar"))
        review.onAmount("2")
        assertEquals(215.6, review.calculation(nutella)!!.nutrition.calories, 1e-9)
        review.saveToLibrary()
        assertEquals("update Nutella 539.0", store.calls.single())
        assertEquals(20.0, diary.foods.single().servingNamed("bar")!!.baseUnitsPerServing, 0.0)
    }

    @Test fun aServingStillInItsFormGoesWithAddAndOneThatDoesNotReadStopsIt() {
        var stored: FoodRecord? = null
        val logged = object : ProductStore by store {
            override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String, servingName: String?) { stored = food }
        }
        val review = ProductReview(
            diary.session, logged, notices, { "2026-10-28" }, { MealSlot.LUNCH }, { null }, { null }, { false }, {}, {},
            { _, _, _, _ -> }, {}, {}, { _, _ -> }, {}, {}, diary.scope, kotlinx.coroutines.Dispatchers.Unconfined,
        )
        review.choose(nutella, fromScan = true, origin = ProductReviewOrigin.MEAL)
        review.servingForm.openServing(MeasureUnit.GRAM)
        review.servingForm.servingAmount = "20"
        review.add(MealSlot.LUNCH)
        assertEquals("Name is required", notices.message)
        assertNull(stored)
        review.servingForm.servingLabel = "bar"
        review.add(MealSlot.LUNCH)
        assertEquals(20.0, stored!!.servingNamed("bar")!!.baseUnitsPerServing, 0.0)
    }

    @Test fun aServingGoesToThePartAReviewedProductIsPickedFor() {
        var stored: FoodRecord? = null
        val kept = object : ProductStore by store {
            override suspend fun cacheFood(food: FoodRecord): FoodRecord { stored = food; return food }
        }
        val review = ProductReview(
            diary.session, kept, notices, { "2026-10-28" }, { null }, { null }, { null }, { true }, {}, {},
            { _, _, _, _ -> }, {}, {}, { _, _ -> }, {}, {}, diary.scope, kotlinx.coroutines.Dispatchers.Unconfined,
        )
        review.choose(nutella, fromScan = true)
        review.servingForm.openServing(MeasureUnit.GRAM)
        review.servingForm.servingLabel = "bar"
        review.servingForm.servingAmount = "20"
        review.addToLog()
        assertEquals(20.0, stored!!.servingNamed("bar")!!.baseUnitsPerServing, 0.0)
    }

    @Test fun aReviewWithNoServingAddedLeavesTheFoodAsItIs() {
        review.choose(chicken, origin = ProductReviewOrigin.LOG_PART)
        assertTrue(review.withServings(chicken) === chicken)
    }

    @Test fun aStoredProductHasNoNewServings() {
        diary.foods = listOf(nutella.copy(id = "custom-mine"))
        diary.session.refresh()
        assertFalse("the same barcode", review.isNewProduct(nutella))
        assertFalse("the same id", review.isNewProduct(nutella.copy(id = "custom-mine", barcode = null)))
    }
}
