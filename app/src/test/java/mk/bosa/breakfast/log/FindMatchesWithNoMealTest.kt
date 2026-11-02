package mk.bosa.breakfast.log

import mk.bosa.breakfast.QueueDispatcher
import mk.bosa.breakfast.app.DiaryChange
import mk.bosa.breakfast.app.Notices
import mk.bosa.breakfast.domain.FoodMatcher
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.LogPlanner
import mk.bosa.breakfast.domain.MealSlot
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.NutritionValues
import mk.bosa.breakfast.domain.SavedMeal
import mk.bosa.breakfast.domain.TestFoods
import mk.bosa.breakfast.domain.DraftRow
import mk.bosa.breakfast.resolve.ResolveFlow
import mk.bosa.breakfast.resolve.ResolveStore
import mk.bosa.breakfast.resolve.food
import mk.bosa.breakfast.scan.ProductReview
import mk.bosa.breakfast.scan.ProductReviewOrigin
import mk.bosa.breakfast.scan.ProductStore
import mk.bosa.breakfast.today.OpenDiary
import mk.bosa.breakfast.today.entry
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Log's Find matches with no meal known, over the real queue and product review, wired as `AppOwners` wires them. */
class FindMatchesWithNoMealTest {
    private val writes = mutableListOf<String>()
    private val diary = OpenDiary()
    private var automatic: MealSlot? = null
    private val planner = { pins: Map<String, FoodRecord> -> LogPlanner(FoodMatcher(TestFoods.fixtures), pins = pins) }

    private val logStore = object : LogStore {
        override fun planner(pins: Map<String, FoodRecord>) = this@FindMatchesWithNoMealTest.planner(pins)
        override suspend fun save(text: String, meal: MealSlot, dayKey: String, pins: Map<String, FoodRecord>): SavedMeal {
            val plan = planner(pins).plan(text)
            writes += "save \"$text\" $meal logs=${plan.entries.map { it.food.id }}"
            return SavedMeal(plan.entries.mapIndexed { index, _ -> entry("e$index") }, NutritionValues.ZERO, plan.unresolved)
        }
    }
    private val resolveStore = object : ResolveStore {
        override fun typed(raw: String) = planner(emptyMap()).typed(raw)
        override fun search(query: String) = emptyList<mk.bosa.breakfast.domain.RankedFood>()
        override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String, servingName: String?) {
            writes += "queue log ${food.id} $meal"
        }
    }
    private val productStore = object : ProductStore {
        override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String, servingName: String?) {
            writes += "review log ${food.id} $meal"
        }
        override suspend fun updateFood(food: FoodRecord) {}
        override suspend fun cacheFood(food: FoodRecord): FoodRecord { writes += "store ${food.id}"; return food }
    }
    private val resolve = ResolveFlow(
        diary.session, resolveStore, Notices(), diary.scope, QueueDispatcher(),
        selectedDay = { "2026-10-28" },
        openLibrary = { _, _ -> },
        returnToLog = { request, pick -> log.resume(request, pick) },
    )
    private val log: LogComposer = LogComposer(
        diary.session, logStore,
        selectedDay = { "2026-10-28" },
        automaticMeal = { automatic },
        openMeal = { null },
        editing = { false },
        afterSave = resolve::afterSave,
        resolvePart = resolve::beginPart,
        compute = Dispatchers.Unconfined,
        onQueueLeft = { resolve.leftBy(it) },
        onDraftEdited = { source, parts -> resolve.sourceEdited(source, parts) },
        onMealChosen = resolve::changeMeal,
        onTextMeal = resolve::textMealChanged,
        onPickPlaced = resolve::pickPlaced,
        queuedParts = { resolve.parts },
        queueWaitsForMeal = { resolve.queueActive && resolve.queueMeal == null },
    )
    private val review = ProductReview(
        diary.session, productStore, Notices(),
        selectedDay = { "2026-10-28" },
        openMeal = { null },
        automaticMeal = { automatic },
        queue = resolve::queueView,
        resolvingPart = { resolve.resolvingPart },
        offerContribution = {},
        onPartResolved = resolve::finishPart,
        onLogPart = log::addScannedPart,
        onLogged = resolve::childLogged,
        onClosed = resolve::childClosed,
        openSearch = { _, _ -> },
        startScan = {},
        confirmLogged = {},
        scope = diary.scope,
        io = Dispatchers.Unconfined,
        partInDraft = log::partInDraft,
    )
    private val ayran = food("ayran", "Ayran")

    private fun findMatchesFor(text: String) {
        log.open(null)
        log.onDraft(text)
        log.submit()
    }

    @Test fun findMatchesOpensNoMenuAndWritesNothing() {
        findMatchesFor("zzqx, qqzz")
        assertFalse(log.mealMenuOpen)
        assertEquals(emptyList<String>(), writes)
        assertEquals(emptyList<DiaryChange>(), diary.published)
        assertEquals(listOf("zzqx", "qqzz"), resolve.parts)
        assertTrue(resolve.queueActive)
        assertNull(resolve.queueMeal)
        assertNull("the meal stays unknown", log.targetMeal())
        assertEquals("zzqx, qqzz", log.draft)
        assertEquals("Some foods need a match.", log.error)
        assertTrue(log.open)
    }

    @Test fun theKeyboardsDoneGoesTheSameWay() {
        log.open(null)
        log.onDraft("zzqx")
        log.commit()
        log.submit()
        assertFalse(log.mealMenuOpen)
        assertEquals(listOf("zzqx"), resolve.parts)
    }

    @Test fun aDraftWithSomethingThatLogsStillAsksFirst() {
        log.open(null)
        log.onDraft("2 eggs, zzqx")
        log.submit()
        assertTrue(log.mealMenuOpen)
        assertEquals(emptyList<String>(), resolve.parts)
        assertEquals(emptyList<String>(), writes)
        log.chooseMeal(MealSlot.LUNCH)
        assertEquals(listOf("save \"2 eggs, zzqx\" LUNCH logs=[egg]"), writes)
        assertEquals(MealSlot.LUNCH, resolve.queueMeal)
        assertEquals(listOf("zzqx"), resolve.parts)
    }

    @Test fun aKnownMealGoesThroughTheSaveAsToday() {
        automatic = MealSlot.DINNER
        findMatchesFor("zzqx")
        assertEquals(listOf("save \"zzqx\" DINNER logs=[]"), writes)
        assertEquals(MealSlot.DINNER, resolve.queueMeal)
        writes.clear()
        automatic = null
        findMatchesFor("lunch, zzqx")
        assertEquals(listOf("save \"lunch, zzqx\" LUNCH logs=[]"), writes)
        assertEquals(MealSlot.LUNCH, resolve.queueMeal)
    }

    @Test fun aReviewedPickGoesToTheDraftAndTheMenuAsks() {
        findMatchesFor("zzqx 200g, qqzz")
        review.choose(ayran, origin = ProductReviewOrigin.QUEUE)
        assertEquals("no meal to show", null, review.queueMeal())
        assertEquals(ProductReviewOrigin.LOG_PART, review.origin)
        assertEquals("the part's typed amount", "200", review.amount)
        review.addToLog()
        assertEquals(listOf("store ayran"), writes)
        assertEquals("200 g Ayran, qqzz", log.draft)
        assertEquals(listOf("qqzz"), resolve.parts)
        assertTrue(log.mealMenuOpen)
        assertNull(review.product)

        log.chooseMeal(MealSlot.SNACKS)
        assertEquals("save \"200 g Ayran, qqzz\" SNACKS logs=[ayran]", writes.last())
        assertEquals("what is left goes on, now for that meal", listOf("qqzz"), resolve.parts)
        assertEquals(MealSlot.SNACKS, resolve.queueMeal)
        assertEquals("qqzz", log.draft)
    }

    @Test fun theMenuDismissedLogsNothingAndKeepsThePick() {
        findMatchesFor("zzqx")
        review.choose(ayran, origin = ProductReviewOrigin.QUEUE)
        review.addToLog()
        assertTrue(log.mealMenuOpen)
        log.dismissMealMenu()
        assertEquals(listOf("store ayran"), writes)
        assertEquals("100 g Ayran", log.draft)
        assertEquals(setOf("100 g Ayran"), log.pinsSnapshot().keys)
        assertNull("nothing is left to match", log.error)
        assertTrue(log.draftView().addLogs)
        assertTrue(log.open)
        // Add asks again, and the pick logs.
        log.submit()
        assertTrue(log.mealMenuOpen)
        log.chooseMeal(MealSlot.LUNCH)
        assertEquals("save \"100 g Ayran\" LUNCH logs=[ayran]", writes.last())
        assertFalse(log.open)
    }

    @Test fun aLocalChoiceTakesThePlaceOfItsPartAndAsks() {
        findMatchesFor("zzqx 200g, qqzz")
        resolve.resolve(ayran)
        assertEquals("nothing logged yet", emptyList<String>(), writes)
        assertEquals("Ayran 200g, qqzz", log.draft)
        assertEquals(listOf("qqzz"), resolve.parts)
        assertTrue(log.mealMenuOpen)
        log.dismissMealMenu()
        assertEquals(emptyList<String>(), writes)
        assertEquals("Ayran 200g, qqzz", log.draft)
        // The next pick asks again; the meal then logs both.
        resolve.resolve(food("boza", "Boza"))
        assertTrue(log.mealMenuOpen)
        log.chooseMeal(MealSlot.DINNER)
        assertEquals(listOf("save \"Ayran 200g, Boza\" DINNER logs=[ayran, boza]"), writes)
    }

    @Test fun skipNeedsNoMealAndLogsNothing() {
        findMatchesFor("zzqx, qqzz")
        resolve.skip()
        assertEquals("qqzz", log.draft)
        assertFalse(log.mealMenuOpen)
        resolve.skip()
        assertEquals(emptyList<String>(), writes)
        assertFalse("nothing left: the Log closes", log.open)
    }

    @Test fun searchYourFoodsBringsThePickBackWithoutAskingUntilAdd() {
        findMatchesFor("zzqx")
        log.resolveNotInFoods(DraftRow.Unresolved("zzqx", 0, 4))
        assertTrue(resolve.resolvingPart)
        resolve.finishPart(ayran)
        assertEquals("Ayran", log.draft)
        assertFalse(log.mealMenuOpen)
        assertEquals(emptyList<String>(), writes)
        assertNull(log.targetMeal())
        log.submit()
        assertTrue(log.mealMenuOpen)
    }

    @Test fun aMealChosenFromTheMenuMeanwhileIsTheQueues() {
        findMatchesFor("zzqx")
        log.openMealMenu()
        log.chooseMeal(MealSlot.BREAKFAST)
        assertEquals(MealSlot.BREAKFAST, resolve.queueMeal)
        assertEquals(emptyList<String>(), writes)
        resolve.resolve(ayran)
        assertEquals(listOf("queue log ayran BREAKFAST"), writes)
        assertFalse(log.mealMenuOpen)
    }

    @Test fun aMealWordTypedMeanwhileIsTheQueues() {
        findMatchesFor("zzqx")
        log.onDraft("dinner, zzqx")
        assertEquals(MealSlot.DINNER, resolve.queueMeal)
        review.choose(ayran, origin = ProductReviewOrigin.QUEUE)
        assertEquals(ProductReviewOrigin.QUEUE, review.origin)
        assertEquals(MealSlot.DINNER, review.queueMeal())
    }

    @Test fun aMealWordOnItsWayToAFoodLeavesNoMeal() {
        findMatchesFor("zzqx")
        log.onDraft("zzqx, snack")
        assertEquals(MealSlot.SNACKS, resolve.queueMeal)
        log.onDraft("zzqx, snack bar")
        assertNull(resolve.queueMeal)
        assertNull(log.targetMeal())
        resolve.resolve(ayran)
        assertEquals("the pick asks", emptyList<String>(), writes)
        assertTrue(log.mealMenuOpen)
    }

    @Test fun aMealWordThatStaysLogsThere() {
        findMatchesFor("zzqx")
        log.onDraft("zzqx, snack")
        log.onDraft("zzqx, snack, qqzz")
        assertEquals(MealSlot.SNACKS, resolve.queueMeal)
        resolve.resolve(ayran)
        assertEquals(listOf("queue log ayran SNACKS"), writes)
        assertFalse(log.mealMenuOpen)
    }

    @Test fun aMealWordDeletedLeavesNoMeal() {
        findMatchesFor("zzqx")
        log.onDraft("dinner, zzqx")
        assertEquals(MealSlot.DINNER, resolve.queueMeal)
        log.onDraft("zzqx")
        assertNull(resolve.queueMeal)
        assertNull(log.targetMeal())
        resolve.resolve(ayran)
        assertEquals(emptyList<String>(), writes)
        assertTrue(log.mealMenuOpen)
    }

    @Test fun aMealChosenStaysWhateverIsTyped() {
        findMatchesFor("zzqx")
        log.openMealMenu()
        log.chooseMeal(MealSlot.LUNCH)
        log.onDraft("dinner, zzqx")
        assertEquals(MealSlot.LUNCH, resolve.queueMeal)
        log.onDraft("zzqx")
        assertEquals(MealSlot.LUNCH, resolve.queueMeal)
    }

    @Test fun twoPartsTypedTheSameLeaveOneAtATime() {
        findMatchesFor("zzqx, zzqx")
        resolve.resolve(ayran)
        assertEquals("Ayran, zzqx", log.draft)
        assertEquals(listOf("zzqx"), resolve.parts)
        log.dismissMealMenu()
        resolve.skip()
        assertEquals("Ayran", log.draft)
        assertEquals(emptyList<String>(), resolve.parts)
        assertFalse(resolve.queueActive)
        assertTrue(log.open)
        assertEquals(emptyList<String>(), writes)
    }

    @Test fun aReviewedPickOfOneOfTwoPartsTypedTheSameLeavesOne() {
        findMatchesFor("zzqx, zzqx")
        review.choose(ayran, origin = ProductReviewOrigin.QUEUE)
        review.addToLog()
        assertEquals("100 g Ayran, zzqx", log.draft)
        assertEquals(listOf("zzqx"), resolve.parts)
    }

    @Test fun aPickThatReadsAsItsPartLeavesTheQueue() {
        findMatchesFor("Ayran, qqzz")
        resolve.resolve(ayran)
        assertEquals("Ayran, qqzz", log.draft)
        assertEquals(listOf("qqzz"), resolve.parts)
        assertTrue(log.mealMenuOpen)
    }

    @Test fun aLastPickThatReadsAsItsPartEndsTheQueue() {
        findMatchesFor("Ayran")
        resolve.resolve(ayran)
        assertEquals("Ayran", log.draft)
        assertEquals(emptyList<String>(), resolve.parts)
        assertFalse(resolve.queueActive)
        assertNull(log.error)
        assertTrue(log.mealMenuOpen)
    }

    @Test fun skippingTheLastPartBesideAPickClearsTheError() {
        findMatchesFor("zzqx, qqzz")
        resolve.resolve(ayran)
        log.dismissMealMenu()
        assertEquals("Some foods need a match.", log.error)
        resolve.skip()
        assertEquals("Ayran", log.draft)
        assertNull(log.error)
        assertTrue(log.open)
    }

    @Test fun skippingTheLastQueuedPartKeepsTheErrorWhileAnotherNeedsAMatch() {
        findMatchesFor("zzqx")
        log.onDraft("zzqx, qqzz")
        resolve.skip()
        assertEquals("qqzz", log.draft)
        assertEquals("Some foods need a match.", log.error)
    }

    @Test fun theQueueHasAMealOnlyOnceOneIsKnown() {
        findMatchesFor("zzqx")
        assertFalse("nothing logged: an earlier batch's Undo stays away", resolve.queueHasMeal)
        log.openMealMenu()
        log.chooseMeal(MealSlot.BREAKFAST)
        assertTrue(resolve.queueHasMeal)
    }

    @Test fun aNewPartScannedOverTheQueueDoesNotAsk() {
        findMatchesFor("zzqx")
        review.choose(ayran, origin = ProductReviewOrigin.LOG_PART)
        review.addToLog()
        assertFalse("not a pick of a queued part", log.mealMenuOpen)
        assertEquals(listOf("zzqx"), resolve.parts)
        assertEquals("zzqx, 100 g Ayran", log.draft)
    }
}
