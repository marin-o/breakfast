package mk.bosa.breakfast.scan

import kotlinx.serialization.Serializable
import mk.bosa.breakfast.domain.isPositiveAmount
import mk.bosa.breakfast.domain.fieldNumber
import mk.bosa.breakfast.domain.presetMeal
import mk.bosa.breakfast.domain.editableNumber
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import mk.bosa.breakfast.app.DiaryChange
import mk.bosa.breakfast.app.DiarySession
import mk.bosa.breakfast.app.Notices
import mk.bosa.breakfast.contribution.contributionDraftAfterLocalSave
import mk.bosa.breakfast.data.OffContributionDraft
import mk.bosa.breakfast.domain.Calculation
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.MealEntry
import mk.bosa.breakfast.domain.MealSlot
import mk.bosa.breakfast.domain.Measure
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.SegmentSpan
import mk.bosa.breakfast.domain.isUsdaFoodId
import mk.bosa.breakfast.domain.articleMeasure
import mk.bosa.breakfast.domain.bareCountMeasure
import mk.bosa.breakfast.domain.typedMeasure
import mk.bosa.breakfast.domain.NutritionBasis
import mk.bosa.breakfast.domain.NutritionCalculator
import mk.bosa.breakfast.domain.NutritionValues
import mk.bosa.breakfast.domain.TypedPart
import mk.bosa.breakfast.domain.displayLabel
import mk.bosa.breakfast.domain.toMeasureUnit
import mk.bosa.breakfast.resolve.ResolveQueueView
import mk.bosa.breakfast.resolve.queueLogPlan
import mk.bosa.breakfast.domain.ServingConversion
import mk.bosa.breakfast.domain.sameBarcode
import mk.bosa.breakfast.food.FoodDraft
import mk.bosa.breakfast.suspendRunCatching
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The writes product review makes; the view model adapts `BreakfastEngine`. */
internal interface ProductStore {
    /** Stores [food] as needed and logs it. */
    suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String, servingName: String? = null)
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
 *   either stores it and hands the stored food to [onPartResolved] instead. A review on the way from "Not right?"
 *   ([ProductReviewOrigin.CORRECTION]) has one write whichever is tapped: [correct] stores it and replaces that entry's
 *   food, so no way from there writes a second entry.
 *
 * - **Servings of a search result:** an Open Food Facts search result has no serving size, so a reviewed one with a
 *   barcode also fetches its full product in [scope] on [io] ([ProductStore.fullProduct]). The review opens at once from
 *   the search data and nothing waits on or shows the fetch: when it arrives, only its servings in the product's basis
 *   are added, quietly ([servingsToAdd]): to the product while it is reviewed, or, once it was saved or logged, to the
 *   stored food, unless that was edited since. Its nutrition is never taken. Leaving the review cancels it.
 *
 * - **Servings added here:** "Edit details" of a product not stored yet ([isNewProduct]) has the food form's Servings
 *   section over [servingForm]; what the fields describe, and so what is written, carries them ([withServings]). A stored
 *   food has Edit food for that, and the store keeps it as it is.
 *
 * - **A queue with no meal yet** (the Log's Find matches): a queued part's review is a [ProductReviewOrigin.LOG_PART]
 *   one for that part: Add stores the food and hands it to the Log's draft, and the Log asks for the meal.
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
    /** [addToLog] stored the food: the Log takes it as a part of its draft, in the place of the part it [replaces], if any. */
    private val onLogPart: (food: FoodRecord, amount: Double, measure: Measure, replaces: SegmentSpan?) -> Unit,
    private val onLogged: (advancesQueue: Boolean) -> Unit,
    private val onClosed: () -> Unit,
    /** The search for "Find better match"; [origin] goes on to what it picks, when this review was logging. */
    private val openSearch: (fromResolver: Boolean, origin: ProductReviewOrigin?) -> Unit,
    /** The scanner again, for what this review was for. */
    private val startScan: (from: ProductReviewOrigin) -> Unit,
    /** Add logged the product to the meal the user chose: "Logged!" (see `Confirmations`). */
    private val confirmLogged: () -> Unit,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    /** A USDA row tapped and added: that tap counts as a pick (nothing is written before Add). */
    private val onCataloguePicked: (foodId: String) -> Unit = {},
    /** The entry a correction is for, whose amount it starts on. */
    private val correctingEntry: () -> MealEntry? = { null },
    /** A correction's one write: it runs [store], the entry becomes that amount of the food stored, and [onStored] gets that food; with no entry left, [onGone] runs instead. */
    private val correct: (store: suspend () -> FoodRecord, amount: Double, measure: Measure, onGone: () -> Unit, onStored: (FoodRecord) -> Unit) -> Unit = { _, _, _, _, _ -> },
    /** Where queued part [raw] is in the Log's draft. */
    private val partInDraft: (raw: String) -> SegmentSpan? = { null },
    /** Back from a correction's review: the search it was picked on, if it was. */
    private val backToSearch: () -> Unit = {},
) {
    var product by mutableStateOf<FoodRecord?>(null); private set
    var fromScan by mutableStateOf(false); private set
    /** A write here makes this product a contribution candidate: scanned, or an incomplete Open Food Facts result saved on the way here. */
    private var offersContribution = false
    var origin by mutableStateOf(ProductReviewOrigin.LIBRARY); private set
    var amount by mutableStateOf("100"); private set
    var unit by mutableStateOf("g"); private set
    /** The named serving [amount] counts; [unit] is then empty. */
    var servingName by mutableStateOf<String?>(null); private set
    /** The part of the Log's draft a new part takes the place of. */
    private var replaces: SegmentSpan? = null
    /** The amount the review was opened with for its part. */
    private var typed: TypedPart? = null
    /** What "Find better match" or "Rescan" left: the review that follows is for the same part. */
    private var partLeft: PartLeft? = null
    var name by mutableStateOf(""); private set
    var calories by mutableStateOf(""); private set
    var protein by mutableStateOf(""); private set
    var carbs by mutableStateOf(""); private set
    var fat by mutableStateOf(""); private set
    var saturatedFat by mutableStateOf(""); private set
    var sugars by mutableStateOf(""); private set
    var fibre by mutableStateOf(""); private set
    var salt by mutableStateOf(""); private set
    /** The Servings section's form: only its servings and density are used here. */
    val servingForm = FoodDraft()
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

    private class PartLeft(val origin: ProductReviewOrigin, val typed: TypedPart?, val replaces: SegmentSpan?)

    /**
     * Reviews [food]. [origin] is what the entry point opened it for (a scan, or the search reached from one); without
     * one, the queue's part while there is a queue, a meal's while one is open, else Save to Library. A queued part
     * prefills its typed amount, a correction its entry's amount while [food] can count it. [fetchServings] false
     * goes to no network: a food just made by hand has no product to fetch. A new part of the Log's draft may bring its
     * [typed] amount, and the part it [replaces]; one reached through "Find better match" or "Rescan" takes those on from
     * the review it follows. A USDA food already stored is reviewed as stored, so what shows is what Add logs.
     */
    fun choose(picked: FoodRecord, fromScan: Boolean = false, origin: ProductReviewOrigin? = null, fetchServings: Boolean = true, typed: TypedPart? = null, replaces: SegmentSpan? = null, offersContribution: Boolean = fromScan) {
        val resolveQueue = queue()
        val food = storedFood(picked.id)?.takeIf { isUsdaFoodId(picked.id) } ?: picked
        product = food
        this.fromScan = fromScan
        this.offersContribution = offersContribution
        val opened = origin ?: productReviewOrigin(libraryTab = openMeal() == null, queueActive = resolveQueue != null)
        // No meal to log a queued part to yet: the pick goes to the draft in that part's place.
        val waiting = resolveQueue?.takeIf { it.meal == null && !opened.takesNoMeal }
        this.origin = if (waiting != null) ProductReviewOrigin.LOG_PART else opened
        val left = partLeft?.takeIf { it.origin == this.origin }
        partLeft = null
        this.typed = typed ?: left?.typed ?: waiting?.current
        // A queued part's typed amount, or what the tap brought: a scanned part of the Log's draft starts plain, whatever is queued.
        val prefill = correctionPrefill(food)
            ?: (this.typed ?: resolveQueue?.takeIf { this.origin == ProductReviewOrigin.QUEUE }?.current)?.let { productPrefill(food, it) }
            ?: ProductPrefill(100.0, food.nutrition.unit)
        this.replaces = replaces ?: left?.replaces ?: waiting?.let { partInDraft(it.parts.first()) }
        filledAmount = prefill.amount
        amount = editableNumber(prefill.amount)
        servingName = prefill.servingName
        unit = if (prefill.servingName != null) "" else prefill.unit.displayLabel()
        name = food.name
        calories = editableNumber(food.nutrition.values.calories)
        protein = editableNumber(food.nutrition.values.protein)
        carbs = editableNumber(food.nutrition.values.carbohydrates)
        fat = editableNumber(food.nutrition.values.fat)
        saturatedFat = editableNumber(food.nutrition.values.saturatedFat)
        sugars = editableNumber(food.nutrition.values.sugars)
        fibre = editableNumber(food.nutrition.values.fibre)
        salt = editableNumber(food.nutrition.values.salt)
        servingForm.load(food)
        leaveFetch()
        // A scanned product is already the full one; a search result is fetched by its barcode for its servings.
        food.barcode?.takeIf { !fromScan && fetchServings }?.let { startFetch(food, it) }
    }

    /** [food] is not in the diary yet, by id or barcode, so what it is stored with is what the review says. */
    fun isNewProduct(food: FoodRecord): Boolean = session.foods.none { stored ->
        stored.id == food.id || (food.barcode != null && stored.barcode != null && sameBarcode(food.barcode, stored.barcode))
    }

    /** [food] with the servings and density of [servingForm]; [food] itself while those are as it has them. */
    fun withServings(food: FoodRecord): FoodRecord {
        val servings = servingForm.servingsInBasis() ?: return food
        val density = servingForm.densityValue()
        val own = food.householdServings()
        if (servings == own && density == food.density) return food
        return food.copy(conversions = food.conversions - own.toSet() + servings, density = density)
    }

    /** The amount of the entry being corrected, when [food] has a number for it. */
    private fun correctionPrefill(food: FoodRecord): ProductPrefill? {
        val entry = correctingEntry().takeIf { origin == ProductReviewOrigin.CORRECTION } ?: return null
        val serving = entry.enteredUnitLabel.takeIf { entry.enteredUnit == MeasureUnit.SERVING }
        if (!calculator.calculate(food, entry.enteredAmount, entry.enteredUnit, serving).supported) return null
        return ProductPrefill(entry.enteredAmount, entry.enteredUnit, serving)
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
                fetch === started && reviewed?.id == started.productId -> {
                    val added = servingsToAdd(withServings(reviewed), full)
                    product = reviewed.copy(conversions = reviewed.conversions + added)
                    servingForm.servings = servingForm.servings + added
                }
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

    /** Back or the sheet's scrim: [close], and a correction goes back to where it was found. */
    fun dismiss() {
        val correcting = origin == ProductReviewOrigin.CORRECTION
        close()
        if (correcting) backToSearch()
    }

    /** Another screen took the review's place (a resolved part returning to the Log). */
    fun hide() { leaveFetch(); product = null }

    /** A food was deleted: a review still showing it closes, so it cannot store that food again. */
    fun foodDeleted(foodId: String) { if (product?.id == foodId) close() }

    /** A rename rewrote the Log's draft: the part this review, or the one it left, takes the place of reads as [follow] says. */
    fun draftRenamed(follow: (SegmentSpan) -> SegmentSpan) {
        replaces = replaces?.let(follow)
        partLeft = partLeft?.let { PartLeft(it.origin, it.typed, it.replaces?.let(follow)) }
    }

    /** "Find better match": the search, which returns to the resolver when the review came from a queue. */
    fun findBetterMatch() {
        val logging = origin.takeIf { it.logs }
        val left = leavingPart()
        close()
        partLeft = left
        openSearch(queue() != null, logging)
    }

    /** "Rescan": the scanner again. */
    fun rescan() {
        val from = origin
        val left = leavingPart()
        close()
        partLeft = left
        startScan(from)
    }

    /** What the next review takes on, when this one is left for another way to find its food. */
    private fun leavingPart(): PartLeft? = PartLeft(origin, typed, replaces).takeIf { it.typed != null || it.replaces != null }

    /** The search or scanner a review was left for closed with nothing picked: the next review is a new one. */
    fun forgetPartLeft() { partLeft = null }

    fun onAmount(value: String) { amount = value }
    fun onUnit(value: String) { unit = value; servingName = null }

    /** What [amount] counts now. */
    fun measure(): Measure? = servingName?.let { Measure(MeasureUnit.SERVING, it) } ?: parseProductUnit(unit)?.let { Measure(it) }

    /** A measure's chip was tapped. */
    fun onMeasure(measure: Measure) {
        unit = if (measure.unit == MeasureUnit.SERVING) "" else measure.unit.displayLabel()
        servingName = measure.name.takeIf { measure.unit == MeasureUnit.SERVING }
    }
    fun onName(value: String) { name = value }
    fun onCalories(value: String) { calories = value }
    fun onProtein(value: String) { protein = value }
    fun onCarbs(value: String) { carbs = value }
    fun onFat(value: String) { fat = value }
    fun onSaturatedFat(value: String) { saturatedFat = value }
    fun onSugars(value: String) { sugars = value }
    fun onFibre(value: String) { fibre = value }
    fun onSalt(value: String) { salt = value }

    /** The queue's meal, shown as a line rather than a choice, when this review is for a queued part. */
    fun queueMeal(): MealSlot? = queue()?.meal?.takeIf { origin == ProductReviewOrigin.QUEUE }

    /** The resolve queue as the sheet shows it. */
    fun resolveQueue(): ResolveQueueView? = queue()

    /** The meal the sheet's choice starts on. */
    fun initialMeal(): MealSlot = queueMeal() ?: presetMeal(openMeal(), automaticMeal())

    fun calculation(food: FoodRecord): Calculation? = draft(food)?.calculation

    /** The fields as they stand when they read but the food has no number for what the amount counts; null otherwise. */
    fun noValue(food: FoodRecord): ProductDraft? = filled(food)?.takeUnless { it.calculation.supported }

    /** An edit made here reaches a USDA food already stored: the store otherwise keeps the stored food over what it is handed. */
    private suspend fun keepEditsOfStored(food: FoodRecord) {
        if (isUsdaFoodId(food.id) && storedFood(food.id)?.let { it != food } == true) store.updateFood(food)
    }

    /** Add in the review sheet: logs the reviewed product to [chosen] (or the queue's meal) on the selected day. */
    fun add(chosen: MealSlot) {
        val food = product ?: return
        if (!session.canWrite) return
        if (origin == ProductReviewOrigin.CORRECTION) { addToLog(); return }
        if (resolvingPart()) { resolvePart(); return }
        val draft = writable(food) ?: return
        val resolveQueue = queue()
        // Never null: [chosen] is a meal, and a queue with none reviews as a part of the draft ([choose]). If that ever breaks, crash
        // loudly rather than ignore the tap.
        val plan = queueLogPlan(queueActive = resolveQueue != null, resolveQueue?.meal, chosen)
            ?: error("Resolve queue is active but has no meal")
        val target = plan.meal
        val offers = offersContribution
        val day = selectedDay()
        session.write(
            change = DiaryChange.FoodsLogged,
            onSuccess = {
                fetchSaved(storedFood(draft.food.id))
                product = null
                offerContribution(contributionDraftAfterLocalSave(draft.food, offers))
                fromScan = false
                origin = ProductReviewOrigin.LIBRARY
                onLogged(plan.advancesQueue)
                confirmLogged()
                if (isUsdaFoodId(draft.food.id)) onCataloguePicked(draft.food.id)
            },
            onFailure = { notices.show(it.message ?: "This amount cannot be logged.") },
        ) {
            keepEditsOfStored(draft.food)
            store.cacheFoodAndLog(draft.food, draft.amount, draft.unit, target, day, draft.servingName)
        }
    }

    /** Save to Library: only when reviewing from the Library, never for a queued part. */
    fun saveToLibrary() {
        val food = product ?: return
        if (resolvingPart()) { resolvePart(); return }
        val resolveQueue = queue()
        if (!canSaveProductToLibrary(origin, queueActive = resolveQueue != null)) return
        if (!session.canWrite) return
        val draft = writable(food) ?: return
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

    /**
     * Add with no meal: the food is stored, then goes back as a part of the Log's draft (the meal is the Log's, at its
     * Add), or, with a part being resolved, as that part's pick.
     */
    fun addToLog() {
        val food = product ?: return
        if (resolvingPart()) { resolvePart(); return }
        if (!session.canWrite) return
        val draft = writable(food) ?: return
        val offers = offersContribution
        val measure = Measure(draft.unit, draft.servingName)
        if (origin == ProductReviewOrigin.CORRECTION) {
            // [correct] learns the pick when the entry took it, so a USDA food isn't learned a second time here.
            correct({ storeIt(draft.food) }, draft.amount, measure, ::close) { stored -> finish(stored, draft.food, offers, learnsPick = false) {} }
            return
        }
        session.write(
            change = DiaryChange.FoodsChanged,
            onSuccess = { stored -> finish(stored, draft.food, offers) { onLogPart(stored, draft.amount, measure, replaces) } },
            onFailure = { notices.show(it.message ?: "Could not save this food.") },
        ) { storeIt(draft.food) }
    }

    /** [food] as the store keeps it, with an edit of a stored one kept. */
    private suspend fun storeIt(food: FoodRecord): FoodRecord { keepEditsOfStored(food); return store.cacheFood(food) }

    /**
     * [food] was stored as [stored]: the review is over, unless [keepsProduct] leaves it on screen for what comes next,
     * and what comes next is the caller's. A USDA food counts as picked, unless [learnsPick] says that is already seen to.
     */
    private fun finish(stored: FoodRecord, food: FoodRecord, offers: Boolean, keepsProduct: Boolean = false, learnsPick: Boolean = true, next: () -> Unit) {
        fetchSaved(storedFood(stored.id))
        if (!keepsProduct) {
            product = null
            origin = ProductReviewOrigin.LIBRARY
        }
        offerContribution(contributionDraftAfterLocalSave(food, offers))
        fromScan = false
        next()
        if (learnsPick && isUsdaFoodId(food.id)) onCataloguePicked(stored.id)
    }

    /** A product picked while a part is being resolved (of the Log's draft or a recipe's ingredients): stored as the food it is, then picked for the part. */
    private fun resolvePart() {
        val food = product ?: return
        if (!session.canWrite) return
        val draft = writable(food) ?: return
        val offers = offersContribution
        session.write(
            change = DiaryChange.FoodsChanged,
            onSuccess = { stored -> finish(stored, draft.food, offers, keepsProduct = true) { onPartResolved(stored) } },
            onFailure = { notices.show(it.message ?: "Could not save this food.") },
        ) { storeIt(draft.food) }
    }

    /** What a write takes: an open serving form goes with it; what stops the write is said, and it is null. */
    private fun writable(food: FoodRecord): ProductDraft? {
        servingForm.commitPendingServing()?.let { notices.show(it); return null }
        return draft(food) ?: run {
            notices.show("Enter a positive amount, a supported unit, and valid calories.")
            null
        }
    }

    /** [filled] when the amount comes to something. */
    private fun draft(food: FoodRecord): ProductDraft? = filled(food)?.takeIf { it.calculation.supported }

    /** The product as the fields now describe it, with what the amount comes to; null while a field does not read. */
    private fun filled(food: FoodRecord): ProductDraft? {
        val amount = fieldNumber(amount, filledAmount)?.takeIf(::isPositiveAmount)
        val measure = measure()
        val kcal = fieldNumber(calories, food.nutrition.values.calories)
        if (servingForm.densityError() || amount == null || measure == null ||
            kcal == null || !kcal.isFinite() || kcal < 0.0
        ) return null
        val draftFood = withServings(food).copy(
            name = name.trim().ifBlank { food.name },
            nutrition = NutritionBasis(
                food.nutrition.amount,
                food.nutrition.unit,
                food.nutrition.values.let { stored ->
                    NutritionValues(
                        kcal, fieldNumber(protein, stored.protein), fieldNumber(carbs, stored.carbohydrates), fieldNumber(fat, stored.fat),
                        fieldNumber(saturatedFat, stored.saturatedFat), fieldNumber(sugars, stored.sugars), fieldNumber(fibre, stored.fibre), fieldNumber(salt, stored.salt),
                    )
                },
            ),
        )
        val calculation = calculator.calculate(draftFood, amount, measure.unit, measure.name)
        return ProductDraft(draftFood, amount, measure.unit, calculation, measure.name)
    }
}

@Serializable
enum class ProductReviewOrigin {
    MEAL, LIBRARY, QUEUE, LOG_PART,

    /** For an entry's "Not right?": what is found replaces that entry's food. */
    CORRECTION;

    /** What is made or found for it goes on to its review: a meal's log, a part of the Log's draft, or a correction. */
    val logs: Boolean get() = this == MEAL || this == LOG_PART || this == CORRECTION

    /** Its review has no meal to choose: the food goes to what is already there, and a queue under it waits. */
    val takesNoMeal: Boolean get() = this == LOG_PART || this == CORRECTION
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

internal data class ProductPrefill(val amount: Double, val unit: MeasureUnit, val servingName: String? = null)

/**
 * A typed part's amount and unit, else 100 of the food's basis unit. It starts on what the Log counts it as: a unit word
 * that names a serving counts that serving ("2 slices tomato"), and a bare count or "a" what it means for the food
 * ([bareCountMeasure], [articleMeasure]).
 */
internal fun productPrefill(food: FoodRecord, typed: TypedPart): ProductPrefill {
    val measure = when {
        typed.unit != null -> food.typedMeasure(typed.unit, typed.unitWord)
        typed.amount != null -> food.bareCountMeasure(typed.foodPhrase)
        typed.article -> food.articleMeasure(typed.foodPhrase)
        else -> null
    }
    val servingName = measure?.name?.takeIf { measure.unit == MeasureUnit.SERVING }
    return ProductPrefill(typed.amount ?: if (measure != null) 1.0 else 100.0, measure?.unit ?: food.nutrition.unit, servingName)
}

internal fun parseProductUnit(value: String): MeasureUnit? = value.trim().toMeasureUnit()

internal data class ProductDraft(
    val food: FoodRecord,
    val amount: Double,
    val unit: MeasureUnit,
    val calculation: Calculation,
    val servingName: String? = null,
)
