package com.iron.breakfast.scan

import kotlinx.serialization.Serializable
import com.iron.breakfast.domain.isPositiveAmount
import com.iron.breakfast.domain.fieldNumber
import com.iron.breakfast.library.libraryPresetMeal
import com.iron.breakfast.domain.editableNumber
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iron.breakfast.app.DiaryChange
import com.iron.breakfast.app.DiarySession
import com.iron.breakfast.app.Notices
import com.iron.breakfast.contribution.contributionDraftAfterLocalSave
import com.iron.breakfast.data.OffContributionDraft
import com.iron.breakfast.domain.Calculation
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionCalculator
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.TypedPart
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.domain.toMeasureUnit
import com.iron.breakfast.resolve.ResolveQueueView
import com.iron.breakfast.resolve.queueLogPlan
import com.iron.breakfast.domain.ServingConversion
import com.iron.breakfast.suspendRunCatching
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The writes product review makes; the view model adapts `BreakfastEngine`. */
internal interface ProductStore {
    /** Stores [food] as needed and logs it. */
    suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String)
    suspend fun updateFood(food: FoodRecord)
    /** Stores [food] without a log; returns it as stored. */
    suspend fun cacheFood(food: FoodRecord): FoodRecord
    /** The full Open Food Facts product for [barcode] (a search result lacks its serving size); null when there is none. */
    suspend fun fullProduct(barcode: String): FoodRecord? = null
    /**
     * Adds [servings] to the stored food [expected] names, those it has no serving by that name for, only while it is
     * stored exactly as [expected] (nobody edited it since); nothing else of it changes. True when it added one.
     */
    suspend fun addServings(expected: FoodRecord, servings: List<ServingConversion>): Boolean = false
}

/**
 * Product review: a product found by a scan or the Open Food Facts search, before anything is written.
 *
 * - **State:** the [product], whether it came from a scan ([fromScan], which makes it a contribution candidate after a
 *   save), its [origin] (a meal, the Library, or the resolve queue), and the editable fields ([amount], [unit], [name]
 *   and the nutrition values), which [calculation] reads.
 * - **Writes:** [add] logs it to the chosen meal (the queue's own while a queue is being resolved, which then
 *   advances); [saveToLibrary] stores it, from the Library only. While a Log part is being resolved ([resolvingPart]),
 *   either stores it and hands the stored food to [onPartResolved] instead.
 *
 * - **Servings of a search result:** an Open Food Facts search result has no serving size, so a reviewed one with a
 *   barcode also fetches its full product in [scope] on [io] ([ProductStore.fullProduct]). The review opens at once from
 *   the search data and nothing waits on or shows the fetch: when it arrives, only its servings in the product's basis
 *   are added, quietly ([servingsToAdd]): to the product while it is reviewed, or, once it was saved or logged, to the
 *   stored food, unless that was edited since. Its nutrition is never taken. Leaving the review cancels it.
 *
 * Inputs from the screens around it are explicit: the day ([selectedDay]), the meal open beneath ([openMeal]) and the
 * resolve queue ([queue], with its current part as the Log read it). Closing tells the coordinator ([onClosed]); a log reports
 * whether it advanced the queue ([onLogged]); [findBetterMatch] and [rescan] leave through [openSearch] and [startScan].
 */
internal class ProductReview(
    private val session: DiarySession,
    private val store: ProductStore,
    private val notices: Notices,
    private val selectedDay: () -> String,
    private val openMeal: () -> MealSlot?,
    /** The Meal setting's meal now, null for Ask: where the choice starts with no meal open. */
    private val automaticMeal: () -> MealSlot?,
    private val queue: () -> ResolveQueueView?,
    /** A Log part is being resolved in the Library. */
    private val resolvingPart: () -> Boolean,
    private val offerContribution: (OffContributionDraft?) -> Unit,
    private val onPartResolved: (FoodRecord) -> Unit,
    /** [addToLog] stored the food: the Log takes it as a part of its draft. */
    private val onLogPart: (food: FoodRecord, amount: Double, unit: MeasureUnit) -> Unit,
    private val onLogged: (advancesQueue: Boolean) -> Unit,
    private val onClosed: () -> Unit,
    /** The search for "Find better match"; [origin] goes on to what it picks, when this review was logging. */
    private val openSearch: (fromResolver: Boolean, origin: ProductReviewOrigin?) -> Unit,
    private val startScan: () -> Unit,
    /** Add logged the product to the meal the user chose: "Logged!" (see `Confirmations`). */
    private val confirmLogged: () -> Unit,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    var product by mutableStateOf<FoodRecord?>(null); private set
    var fromScan by mutableStateOf(false); private set
    var origin by mutableStateOf(ProductReviewOrigin.LIBRARY); private set
    var amount by mutableStateOf("100"); private set
    var unit by mutableStateOf("g"); private set
    var name by mutableStateOf(""); private set
    var calories by mutableStateOf(""); private set
    var protein by mutableStateOf(""); private set
    var carbs by mutableStateOf(""); private set
    var fat by mutableStateOf(""); private set
    private val calculator = NutritionCalculator()
    /** The exact amount [amount] was filled with, which it stands for until edited. */
    private var filledAmount: Double? = null

    /**
     * The fetch of a reviewed search result's full product. [servings] are what it brought, once it arrived; [saved] the
     * food as stored once the review saved or logged it, which they then go to.
     */
    private class ServingFetch(val productId: String) {
        var job: Job? = null
        var servings: List<ServingConversion>? = null
        var saved: FoodRecord? = null
    }
    private var fetch: ServingFetch? = null

    /**
     * Reviews [food]. [origin] is what the entry point opened it for (a scan, or the search reached from one); without
     * one, the queue's part while there is a queue, a meal's while one is open, else Save to Library, so a search from
     * Daily's "Not right?" never writes a second entry. A queued part prefills its typed amount. [fetchServings] false
     * goes to no network: a food just made by hand has no product to fetch.
     */
    fun choose(food: FoodRecord, fromScan: Boolean = false, origin: ProductReviewOrigin? = null, fetchServings: Boolean = true) {
        val resolveQueue = queue()
        product = food
        this.fromScan = fromScan
        this.origin = origin ?: productReviewOrigin(libraryTab = openMeal() == null, queueActive = resolveQueue != null)
        val prefill = resolveQueue?.let { productPrefill(food, it.current) } ?: ProductPrefill(100.0, food.nutrition.unit)
        filledAmount = prefill.amount
        amount = editableNumber(prefill.amount)
        unit = prefill.unit.displayLabel()
        name = food.name
        calories = editableNumber(food.nutrition.values.calories)
        protein = editableNumber(food.nutrition.values.protein)
        carbs = editableNumber(food.nutrition.values.carbohydrates)
        fat = editableNumber(food.nutrition.values.fat)
        leaveFetch()
        // A scanned product is already the full one; a search result is fetched by its barcode for its servings.
        food.barcode?.takeIf { !fromScan && fetchServings }?.let { startFetch(food, it) }
    }

    private fun startFetch(food: FoodRecord, barcode: String) {
        val started = ServingFetch(food.id)
        fetch = started
        started.job = scope.launch {
            val full = withContext(io) { suspendRunCatching { store.fullProduct(barcode) }.getOrNull() } ?: return@launch
            val servings = servingsToAdd(food, full)
            if (servings.isEmpty()) return@launch
            started.servings = servings
            val saved = started.saved
            val reviewed = product
            when {
                saved != null -> mergeInto(saved, servings)
                fetch === started && reviewed?.id == started.productId -> product = reviewed.copy(conversions = reviewed.conversions + servingsToAdd(reviewed, full))
            }
        }
    }

    /** The review was left without a save: its fetch, if any, is cancelled. One that a save is waiting for goes on. */
    private fun leaveFetch() {
        fetch?.takeIf { it.saved == null }?.job?.cancel()
        fetch = null
    }

    /**
     * The review saved or logged [stored] (the food as the diary now has it): the fetch's servings go to it, now if
     * they arrived without being added to the product, else when they arrive.
     */
    private fun fetchSaved(stored: FoodRecord?) {
        val current = fetch ?: return
        fetch = null
        if (stored == null) { current.job?.cancel(); return }
        current.saved = stored
        current.servings?.let { servings -> if (servingsToAdd(stored, stored.copy(conversions = servings)).isNotEmpty()) mergeInto(stored, servings) }
    }

    /**
     * Adds [servings] to the stored food, outside the user's write gate ([DiarySession.background]), so a tap meanwhile
     * is never refused; a food change follows, so an open food page shows them.
     */
    private fun mergeInto(stored: FoodRecord, servings: List<ServingConversion>) {
        session.background(DiaryChange.FoodsChanged) { store.addServings(stored, servings) }
    }

    /** The food as the diary has it now, by [id]: what a fetch's servings go to after a save. */
    private fun storedFood(id: String): FoodRecord? = session.foods.firstOrNull { it.id == id }

    /** The sheet or its backdrop closed: nothing is written; the coordinator decides what shows next. */
    fun close() {
        leaveFetch()
        product = null
        fromScan = false
        origin = ProductReviewOrigin.LIBRARY
        onClosed()
    }

    /** Another screen took the review's place (a resolved part returning to the Log). */
    fun hide() { leaveFetch(); product = null }

    /** A food was deleted: a review still showing it closes, so it cannot store that food again. */
    fun foodDeleted(foodId: String) { if (product?.id == foodId) close() }

    /** "Find better match": the search, which returns to the resolver when the review came from a queue. */
    fun findBetterMatch() {
        val logging = origin.takeIf { it.logs }
        close()
        openSearch(queue() != null, logging)
    }

    /** "Rescan": the scanner again. */
    fun rescan() { close(); startScan() }

    fun onAmount(value: String) { amount = value }
    fun onUnit(value: String) { unit = value }
    fun onName(value: String) { name = value }
    fun onCalories(value: String) { calories = value }
    fun onProtein(value: String) { protein = value }
    fun onCarbs(value: String) { carbs = value }
    fun onFat(value: String) { fat = value }

    /** The queue's meal, shown as a line rather than a choice, when this review is for a queued part. */
    fun queueMeal(): MealSlot? = queue()?.meal?.takeIf { origin == ProductReviewOrigin.QUEUE }

    /** The resolve queue as the sheet shows it. */
    fun resolveQueue(): ResolveQueueView? = queue()

    /** The meal the sheet's choice starts on. */
    fun initialMeal(): MealSlot = queueMeal() ?: libraryPresetMeal(openMeal(), automaticMeal())

    fun calculation(food: FoodRecord): Calculation? = draft(food)?.calculation

    /** Add in the review sheet: logs the reviewed product to [chosen] (or the queue's meal) on the selected day. */
    fun add(chosen: MealSlot) {
        val food = product ?: return
        if (!session.canWrite) return
        if (resolvingPart()) { resolvePart(); return }
        val draft = draft(food) ?: run {
            notices.show("Enter a positive amount, a supported unit, and valid calories.")
            return
        }
        val resolveQueue = queue()
        // Never null: [chosen] is a meal, and an active queue has its own ([queueLogPlan]). If that ever breaks, crash
        // loudly rather than ignore the tap.
        val plan = queueLogPlan(queueActive = resolveQueue != null, resolveQueue?.meal, chosen)
            ?: error("Resolve queue is active but has no meal")
        val target = plan.meal
        val scanned = fromScan
        val day = selectedDay()
        session.write(
            change = DiaryChange.FoodsLogged,
            onSuccess = {
                fetchSaved(storedFood(draft.food.id))
                product = null
                offerContribution(contributionDraftAfterLocalSave(draft.food, scanned))
                fromScan = false
                origin = ProductReviewOrigin.LIBRARY
                onLogged(plan.advancesQueue)
                confirmLogged()
            },
            onFailure = { notices.show(it.message ?: "This amount cannot be logged.") },
        ) { store.cacheFoodAndLog(draft.food, draft.amount, draft.unit, target, day) }
    }

    /** Save to Library: only when reviewing from the Library, never for a queued part. */
    fun saveToLibrary() {
        val food = product ?: return
        if (resolvingPart()) { resolvePart(); return }
        val resolveQueue = queue()
        if (!canSaveProductToLibrary(origin, queueActive = resolveQueue != null)) return
        if (!session.canWrite) return
        val draft = draft(food) ?: run { notices.show("Enter a positive amount, a supported unit, and valid calories."); return }
        session.write(
            change = DiaryChange.FoodsChanged,
            onSuccess = {
                fetchSaved(storedFood(draft.food.id))
                product = null
                fromScan = false
                origin = ProductReviewOrigin.LIBRARY
                notices.show("Saved ${draft.food.name} to your food library.")
            },
            onFailure = { notices.show(it.message ?: "Could not save this food.") },
        ) { store.updateFood(draft.food) }
    }

    /** Add from the Log page: the food is stored, then goes back as a part of the draft. The meal is the Log's, at its Add. */
    fun addToLog() {
        val food = product ?: return
        if (!session.canWrite) return
        val draft = draft(food) ?: run {
            notices.show("Enter a positive amount, a supported unit, and valid calories.")
            return
        }
        val scanned = fromScan
        session.write(
            change = DiaryChange.FoodsChanged,
            onSuccess = { stored ->
                fetchSaved(storedFood(stored.id))
                product = null
                offerContribution(contributionDraftAfterLocalSave(draft.food, scanned))
                fromScan = false
                origin = ProductReviewOrigin.LIBRARY
                onLogPart(stored, draft.amount, draft.unit)
            },
            onFailure = { notices.show(it.message ?: "Could not save this food.") },
        ) { store.cacheFood(draft.food) }
    }

    /** A product picked while a Log part is being resolved: stored as the food it is, then picked for the part. */
    private fun resolvePart() {
        val food = product ?: return
        if (!session.canWrite) return
        val draft = draft(food) ?: run {
            notices.show("Enter a positive amount, a supported unit, and valid calories.")
            return
        }
        val scanned = fromScan
        session.write(
            change = DiaryChange.FoodsChanged,
            onSuccess = { stored ->
                fetchSaved(storedFood(stored.id))
                offerContribution(contributionDraftAfterLocalSave(draft.food, scanned))
                fromScan = false
                onPartResolved(stored)
            },
            onFailure = { notices.show(it.message ?: "Could not save this food.") },
        ) { store.cacheFood(draft.food) }
    }

    /** The product as the fields now describe it, with what the amount comes to; null while a field does not read. */
    private fun draft(food: FoodRecord): ProductDraft? {
        val amount = fieldNumber(amount, filledAmount)?.takeIf(::isPositiveAmount)
        val unit = parseProductUnit(unit)
        val kcal = fieldNumber(calories, food.nutrition.values.calories)
        if (amount == null || unit == null ||
            kcal == null || !kcal.isFinite() || kcal < 0.0
        ) return null
        val draftFood = food.copy(
            name = name.trim().ifBlank { food.name },
            nutrition = NutritionBasis(
                food.nutrition.amount,
                food.nutrition.unit,
                NutritionValues(kcal, fieldNumber(protein, food.nutrition.values.protein), fieldNumber(carbs, food.nutrition.values.carbohydrates), fieldNumber(fat, food.nutrition.values.fat)),
            ),
        )
        val calculation = calculator.calculate(draftFood, amount, unit)
        return ProductDraft(draftFood, amount, unit, calculation).takeIf { calculation.supported }
    }
}

@Serializable
enum class ProductReviewOrigin {
    MEAL, LIBRARY, QUEUE, LOG_PART;

    /** What is made or found for it goes on to its review: a meal's log, or a part of the Log's draft. */
    val logs: Boolean get() = this == MEAL || this == LOG_PART
}

/**
 * The servings a fetched [full] product adds to [food]: its named servings in [food]'s basis unit that [food] has no
 * serving by that name for. Nothing else of [full] is taken, its nutrition least of all.
 */
internal fun servingsToAdd(food: FoodRecord, full: FoodRecord): List<ServingConversion> =
    full.conversions.filter { serving ->
        val name = serving.name ?: return@filter false
        serving.unit == food.nutrition.unit && full.nutrition.unit == food.nutrition.unit &&
            food.conversions.none { it.name?.equals(name, ignoreCase = true) == true }
    }.distinctBy { it.name!!.lowercase() }

internal data class ProductPrefill(val amount: Double, val unit: MeasureUnit)

/** A queued part's typed amount and unit, else 100 of the food's basis unit. */
internal fun productPrefill(food: FoodRecord, typed: TypedPart): ProductPrefill = ProductPrefill(
    amount = typed.amount ?: 100.0,
    unit = typed.unit ?: food.nutrition.unit
)

internal fun parseProductUnit(value: String): MeasureUnit? = value.trim().toMeasureUnit()

internal data class ProductDraft(
    val food: FoodRecord,
    val amount: Double,
    val unit: MeasureUnit,
    val calculation: Calculation
)
