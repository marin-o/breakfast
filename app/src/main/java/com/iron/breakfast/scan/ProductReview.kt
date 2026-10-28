package com.iron.breakfast.scan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iron.breakfast.app.DiaryChange
import com.iron.breakfast.app.DiarySession
import com.iron.breakfast.app.Notices
import com.iron.breakfast.canSaveProductToLibrary
import com.iron.breakfast.contribution.contributionDraftAfterLocalSave
import com.iron.breakfast.data.OffContributionDraft
import com.iron.breakfast.domain.Calculation
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionCalculator
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.ShorthandParser
import com.iron.breakfast.domain.TypedPart
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.domain.toMeasureUnit
import com.iron.breakfast.productReviewOrigin
import com.iron.breakfast.resolve.ResolveQueueView
import com.iron.breakfast.reviewLogPlan
import com.iron.breakfast.ui.label
import com.iron.breakfast.ui.pretty

/** The writes product review makes; the view model adapts `BreakfastEngine`. */
internal interface ProductStore {
    /** Stores [food] as needed and logs it. */
    suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String)
    suspend fun updateFood(food: FoodRecord)
    /** Stores [food] without a log; returns it as stored. */
    suspend fun cacheFood(food: FoodRecord): FoodRecord
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
 * Inputs from the screens around it are explicit: the day ([selectedDay]), the meal open beneath ([openMeal]), the resolve
 * queue ([queue]) and how a queued part reads ([typed]). Closing tells the coordinator ([onClosed]); a log reports
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
    /** A queued part as the Log read it ("mleko200g" is mleko, 200 g). */
    private val typed: (String) -> TypedPart,
    private val offerContribution: (OffContributionDraft?) -> Unit,
    private val onPartResolved: (FoodRecord) -> Unit,
    private val onLogged: (advancesQueue: Boolean) -> Unit,
    private val onClosed: () -> Unit,
    private val openSearch: (fromResolver: Boolean) -> Unit,
    private val startScan: () -> Unit,
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

    /** Reviews [food]; [origin] defaults to where the review opens from. A queued part prefills its typed amount. */
    fun choose(food: FoodRecord, fromScan: Boolean = false, origin: ProductReviewOrigin? = null) {
        val resolveQueue = queue()
        product = food
        this.fromScan = fromScan
        this.origin = origin ?: productReviewOrigin(openMeal(), resolveQueue != null, resolveQueue != null)
        val prefill = resolveQueue?.parts?.firstOrNull()?.let { productPrefill(it, food, typed(it)) }
            ?: ProductPrefill(100.0, food.nutrition.unit)
        amount = prefill.amount.toString()
        unit = prefill.unit.displayLabel()
        name = food.name
        calories = food.nutrition.values.calories.toString()
        protein = food.nutrition.values.protein?.toString().orEmpty()
        carbs = food.nutrition.values.carbohydrates?.toString().orEmpty()
        fat = food.nutrition.values.fat?.toString().orEmpty()
    }

    /** The sheet or its backdrop closed: nothing is written; the coordinator decides what shows next. */
    fun close() {
        product = null
        fromScan = false
        origin = ProductReviewOrigin.LIBRARY
        onClosed()
    }

    /** Another screen took the review's place (a resolved part returning to the Log). */
    fun hide() { product = null }

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
        if (!session.ready || session.busy) return
        if (resolvingPart()) { resolvePart(); return }
        val draft = draft(food) ?: run {
            notices.show("Enter a positive amount, a supported unit, and valid calories.")
            return
        }
        val resolveQueue = queue()
        val plan = reviewLogPlan(resolveQueue != null, resolveQueue != null, resolveQueue?.meal, chosen)
        val target = plan.meal
        val scanned = fromScan
        val day = selectedDay()
        session.write(
            change = DiaryChange.FoodsLogged,
            onSuccess = {
                product = null
                offerContribution(contributionDraftAfterLocalSave(draft.food, scanned))
                fromScan = false
                origin = ProductReviewOrigin.LIBRARY
                onLogged(plan.advancesQueue)
                notices.show("Added to ${target.label()}")
            },
            onFailure = { notices.show(it.message ?: "This amount cannot be logged.") },
        ) { store.cacheFoodAndLog(draft.food, draft.amount, draft.unit, target, day) }
    }

    /** Save to Library: only when reviewing from the Library, never for a queued part. */
    fun saveToLibrary() {
        val food = product ?: return
        if (resolvingPart()) { resolvePart(); return }
        val resolveQueue = queue()
        if (!canSaveProductToLibrary(openMeal(), resolveQueue != null, resolveQueue != null)) return
        if (!session.ready || session.busy) return
        val draft = draft(food) ?: run { notices.show("Enter a positive amount, a supported unit, and valid calories."); return }
        session.write(
            change = DiaryChange.FoodsChanged,
            onSuccess = {
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
        if (!session.ready || session.busy) return
        val draft = draft(food) ?: run {
            notices.show("Enter a positive amount, a supported unit, and valid calories.")
            return
        }
        val scanned = fromScan
        session.write(
            change = DiaryChange.FoodsChanged,
            onSuccess = { stored ->
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

internal data class ProductPrefill(val amount: Double, val unit: MeasureUnit)

internal fun productNutritionBasisLabel(food: FoodRecord): String =
    "Calories per ${food.nutrition.amount.pretty()} ${food.nutrition.unit.label}"

/** [typed] is [raw] as the Log read it; the view model passes the planner's reading, so a glued amount is kept. */
internal fun productPrefill(
    raw: String,
    food: FoodRecord,
    typed: TypedPart = ShorthandParser().parse(raw).let { TypedPart(it.foodPhrase, it.amount, it.unit) },
): ProductPrefill = ProductPrefill(
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
