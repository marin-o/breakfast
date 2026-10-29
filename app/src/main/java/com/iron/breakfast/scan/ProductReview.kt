package com.iron.breakfast.scan

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
import com.iron.breakfast.ui.pretty

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
    private val queue: () -> ResolveQueueView?,
    /** A Log part is being resolved in the Library. */
    private val resolvingPart: () -> Boolean,
    private val offerContribution: (OffContributionDraft?) -> Unit,
    private val onPartResolved: (FoodRecord) -> Unit,
    private val onLogged: (advancesQueue: Boolean) -> Unit,
    private val onClosed: () -> Unit,
    private val openSearch: (fromResolver: Boolean) -> Unit,
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

    /** Reviews [food]; [origin] defaults to where the review opens from. A queued part prefills its typed amount. */
    fun choose(food: FoodRecord, fromScan: Boolean = false, origin: ProductReviewOrigin? = null) {
        val resolveQueue = queue()
        product = food
        this.fromScan = fromScan
        this.origin = origin ?: productReviewOrigin(openMeal(), queueActive = resolveQueue != null)
        val prefill = resolveQueue?.let { productPrefill(food, it.current) } ?: ProductPrefill(100.0, food.nutrition.unit)
        amount = prefill.amount.toString()
        unit = prefill.unit.displayLabel()
        name = food.name
        calories = food.nutrition.values.calories.toString()
        protein = food.nutrition.values.protein?.toString().orEmpty()
        carbs = food.nutrition.values.carbohydrates?.toString().orEmpty()
        fat = food.nutrition.values.fat?.toString().orEmpty()
        leaveFetch()
        // A scanned product is already the full one; a search result is fetched by its barcode for its servings.
        food.barcode?.takeIf { !fromScan }?.let { startFetch(food, it) }
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

    /** Adds [servings] to the stored food, without a write of the user's own: it waits for one that runs. */
    private fun mergeInto(stored: FoodRecord, servings: List<ServingConversion>) {
        session.whenIdle(onSuccess = { added: Boolean -> if (added) session.refresh() }) {
            suspendRunCatching { store.addServings(stored, servings) }.getOrDefault(false)
        }
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
    fun findBetterMatch() { close(); openSearch(queue() != null) }

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
    fun initialMeal(): MealSlot = queueMeal() ?: openMeal() ?: MealSlot.LUNCH

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
        if (!canSaveProductToLibrary(openMeal(), queueActive = resolveQueue != null)) return
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
        val amount = amount.toDoubleOrNull()
        val unit = parseProductUnit(unit)
        val kcal = calories.toDoubleOrNull()
        if (amount == null || !amount.isFinite() || amount <= 0.0 || unit == null ||
            kcal == null || !kcal.isFinite() || kcal < 0.0
        ) return null
        val draftFood = food.copy(
            name = name.trim().ifBlank { food.name },
            nutrition = NutritionBasis(
                food.nutrition.amount,
                food.nutrition.unit,
                NutritionValues(kcal, protein.toDoubleOrNull(), carbs.toDoubleOrNull(), fat.toDoubleOrNull()),
            ),
        )
        val calculation = calculator.calculate(draftFood, amount, unit)
        return ProductDraft(draftFood, amount, unit, calculation).takeIf { calculation.supported }
    }
}

enum class ProductReviewOrigin { MEAL, LIBRARY, QUEUE }

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

internal fun productNutritionBasisLabel(food: FoodRecord): String =
    "Calories per ${food.nutrition.amount.pretty()} ${food.nutrition.unit.label}"

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
