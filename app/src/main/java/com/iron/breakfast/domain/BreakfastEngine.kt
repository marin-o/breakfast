package com.iron.breakfast.domain

import com.iron.breakfast.data.BreakfastStore
import com.iron.breakfast.data.DiaryRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class BreakfastEngine(
    private val store: BreakfastStore,
    /** Settings, "Count what I'm typing", read at each use (the planner is called off the main thread). */
    private val countTypingSetting: () -> Boolean = { true },
    /** Settings, "Learn from my picks", read at each use. */
    private val learnFromPicksSetting: () -> Boolean = { true },
) {
    private val parser = ShorthandParser()
    private val calculator = NutritionCalculator()
    private val matchers = MatcherCache()

    suspend fun initialize() = store.initialize()

    /** Every stored row, for Export diary. */
    internal suspend fun exportRows(): DiaryRows = store.exportRows()

    /** Import diary: the stored diary becomes [rows], or stays as it was if the write fails. */
    internal suspend fun replaceDiary(rows: DiaryRows) = store.replaceDiary(rows)

    /** Delete all data, in one transaction; see [BreakfastStore.deleteAllData]. */
    internal suspend fun deleteAllData(alsoPreferences: Boolean) = store.deleteAllData(alsoPreferences)

    private var cachedPlanner: LogPlanner? = null
    private var plannerMatcher: FoodMatcher? = null
    private var plannerPreferences: Map<String, Preference>? = null
    private var plannerPins: Map<String, FoodRecord>? = null
    private var plannerCountsTyping: Boolean? = null

    /** "Count what I'm typing" (Settings): passed to every planner; see [LogPlanner]. */
    val countTyping: Boolean get() = countTypingSetting()

    /**
     * "Learn from my picks" (Settings). Off: ranking ignores the learned preferences and a pick is not learned; the
     * stored preferences stay as they are.
     */
    val learnFromPicks: Boolean get() = learnFromPicksSetting()

    /** The learned preferences ranking uses: none while [learnFromPicks] is off. */
    private fun preferences(): Map<String, Preference> = rankingPreferences(learnFromPicks, store.preferenceMap())

    /**
     * The matcher for the stored foods, rebuilt only when [BreakfastStore.foods] is a new list: the store keeps the
     * same list after an entry or preference write (MOB-028), so only a food write re-indexes the catalog, the costly
     * part of matching. Preferences are not in the index; they are passed to each ranking call.
     */
    private fun matcher(): FoodMatcher = matchers.matcherFor(store.foods())

    /** The matcher in use now; tests check which writes keep it. */
    internal fun matcherForTesting(): FoodMatcher = matcher()

    /** Called after every write to the foods, so the next [matcher] call indexes the stored foods again. */
    private fun invalidateMatcher() = matchers.invalidate()

    fun parse(input: String): ParsedSegment = parser.parse(input)

    fun search(query: String): List<RankedFood> = matcher().suggestions(query, preferences(), 20)

    fun alternativesFor(entry: MealEntry): List<RankedFood> {
        val phrase = parser.parse(entry.sourcePhrase).foodPhrase.ifBlank { entry.foodName }
        return matcher().suggestions(phrase, preferences(), 3)
            .filterNot { it.food.id == entry.foodId }
            .take(2)
    }

    suspend fun save(
        input: String,
        nowMs: Long = System.currentTimeMillis(),
        mealSlot: MealSlot = MealSlot.BREAKFAST,
        dayKey: String,
        pins: Map<String, FoodRecord> = emptyMap(),
    ): SavedMeal {
        // Planning is CPU work (tens of ms on a phone with a year of foods): off the caller's (main) thread, before
        // the store's lock is taken.
        val plan = withContext(Dispatchers.Default) { planner(pins).plan(input) }
        val entries = plan.entries.mapIndexed { index, resolved ->
            MealEntry(
                id = UUID.randomUUID().toString(),
                foodId = resolved.food.id,
                foodName = resolved.food.name,
                enteredAmount = resolved.amount,
                enteredUnit = resolved.unit,
                resolvedBaseAmount = resolved.calculation.resolvedBaseAmount,
                nutritionSnapshot = resolved.calculation.nutrition,
                conversionProvenance = resolved.calculation.provenance,
                approximateConversion = resolved.calculation.approximate,
                savedAtEpochMs = orderedSavedAt(nowMs, index),
                sourcePhrase = resolved.raw,
                mealSlot = mealSlot,
                dayKey = dayKey,
                resolvedBaseUnit = resolved.food.nutrition.unit
            )
        }
        if (entries.isNotEmpty()) store.saveEntries(entries)
        return SavedMeal(
            entries = entries,
            totals = nutritionTotalOf(entries),
            unresolvedSegments = plan.unresolved
        )
    }

    /** The planner for the current foods and preferences. [save] and the Log page both resolve through it. */
    @Synchronized
    fun planner(pins: Map<String, FoodRecord> = emptyMap()): LogPlanner {
        val matcher = matcher()
        val preferences = preferences()
        val counts = countTyping
        // The same foods, preferences, pins and settings give the same planner, so its per-part cache carries across keystrokes.
        cachedPlanner?.let { if (matcher === plannerMatcher && preferences === plannerPreferences && pins == plannerPins && counts == plannerCountsTyping) return it }
        return LogPlanner(matcher, preferences, calculator, parser, pins, counts).also {
            cachedPlanner = it; plannerMatcher = matcher; plannerPreferences = preferences; plannerPins = pins; plannerCountsTyping = counts
        }
    }

    /**
     * Applies [edit] to entry [entryId] as it is stored, inside the store's write (see [BreakfastStore.editEntry]), so an
     * edit never starts from a stale snapshot and never re-creates a deleted entry. [missing] when the entry is gone.
     */
    private suspend fun editEntry(entryId: String, missing: String, edit: (MealEntry) -> ReplacementResult): ReplacementResult {
        var result: ReplacementResult = ReplacementResult.Failure(missing)
        store.editEntry(entryId) { stored -> edit(stored).also { result = it }.let { (it as? ReplacementResult.Success)?.entry } }
        return result
    }

    suspend fun replaceEntry(entryId: String, foodId: String): ReplacementResult =
        editEntry(entryId, "That saved entry is no longer available.") { old ->
            val food = store.foods().firstOrNull { it.id == foodId }
                ?: return@editEntry ReplacementResult.Failure("That catalogue record is no longer available.")
            if (!old.enteredAmount.isFinite() || old.enteredAmount <= 0.0) return@editEntry ReplacementResult.Failure("Enter a positive amount.")
            val calculation = calculator.calculate(food, old.enteredAmount, old.enteredUnit)
            if (!calculation.supported) {
                return@editEntry ReplacementResult.Failure(
                    "${food.name} has no ${old.enteredUnit.displayLabel()} conversion. Re-enter this segment with a compatible amount such as ml or g."
                )
            }
            ReplacementResult.Success(
                old.copy(
                    foodId = food.id,
                    foodName = food.name,
                    resolvedBaseAmount = calculation.resolvedBaseAmount,
                    nutritionSnapshot = calculation.nutrition,
                    conversionProvenance = calculation.provenance,
                    approximateConversion = calculation.approximate,
                    resolvedBaseUnit = food.nutrition.unit
                )
            )
        }

    /**
     * Recalculates a saved entry for its original food record, changing only
     * the entered amount and unit. The parser is deliberately strict here:
     * quick edit must contain an amount and a supported unit, and it must not
     * be allowed to fall back to the food's defaults.
     */
    suspend fun replaceEntryAmountUnit(entryId: String, text: String): ReplacementResult =
        editEntry(entryId, "That entry is no longer available.") { old ->
            val parsed = parseStrictAmountUnit(text)
                ?: return@editEntry ReplacementResult.Failure("Enter a positive amount and a supported unit, such as 100 g or 1 tbsp.")
            val food = store.foods().firstOrNull { it.id == old.foodId }
                ?: return@editEntry snapshotScaled(old, parsed.amount, parsed.unit)?.let { ReplacementResult.Success(old.withScaled(parsed.amount, it)) }
                    ?: ReplacementResult.Failure("The original food for this entry is no longer available.")
            val calculation = calculator.calculate(food, parsed.amount, parsed.unit)
            if (!calculation.supported) {
                return@editEntry ReplacementResult.Failure(
                    "That amount/unit is not supported for ${food.name}."
                )
            }
            ReplacementResult.Success(
                old.copy(
                    enteredAmount = parsed.amount,
                    enteredUnit = parsed.unit,
                    resolvedBaseAmount = calculation.resolvedBaseAmount,
                    nutritionSnapshot = calculation.nutrition,
                    conversionProvenance = calculation.provenance,
                    approximateConversion = calculation.approximate,
                    resolvedBaseUnit = food.nutrition.unit
                )
            )
        }

    suspend fun replaceEntryFromText(entryId: String, text: String): ReplacementResult {
        // Matched before the write: indexing never runs inside the store's transaction.
        val parsed = parser.parse(text)
        val match = matcher().best(parsed.foodPhrase, preferences())
        return editEntry(entryId, "That entry is no longer available.") { old ->
            if (match == null) return@editEntry ReplacementResult.Failure("No matching food.")
            val amount = parsed.amount ?: old.enteredAmount
            val unit = parsed.unit ?: old.enteredUnit
            val calculation = calculator.calculate(match.food, amount, unit)
            if (!calculation.supported) return@editEntry ReplacementResult.Failure("That amount/unit is not supported for ${match.food.name}.")
            ReplacementResult.Success(old.copy(foodId = match.food.id, foodName = match.food.name, enteredAmount = amount, enteredUnit = unit,
                resolvedBaseAmount = calculation.resolvedBaseAmount, nutritionSnapshot = calculation.nutrition,
                conversionProvenance = calculation.provenance, approximateConversion = calculation.approximate, sourcePhrase = text,
                resolvedBaseUnit = match.food.nutrition.unit))
        }
    }

    /** Replaces every editable field in one storage transaction, retaining the entry id. */
    suspend fun replaceEntryDraft(
        entryId: String,
        foodId: String,
        amount: Double,
        unit: MeasureUnit,
        mealSlot: MealSlot,
        dayKey: String,
    ): ReplacementResult = editEntry(entryId, "That saved entry is no longer available.") { old ->
        val food = store.foods().firstOrNull { it.id == foodId }
        // Its food deleted, an entry kept on it can still change its amount (in its own unit), meal and day.
        if (food == null) {
            val scaled = snapshotScaled(old, amount, unit).takeIf { foodId == old.foodId }
                ?: return@editEntry ReplacementResult.Failure("That catalogue record is no longer available.")
            return@editEntry ReplacementResult.Success(old.withScaled(amount, scaled).copy(mealSlot = mealSlot, dayKey = dayKey))
        }
        if (!amount.isFinite() || amount <= 0.0) return@editEntry ReplacementResult.Failure("Enter a positive amount.")
        val calculation = calculator.calculate(food, amount, unit)
        if (!calculation.supported) return@editEntry ReplacementResult.Failure("That amount/unit is not supported for ${food.name}.")
        ReplacementResult.Success(
            old.copy(
                foodId = food.id,
                foodName = food.name,
                enteredAmount = amount,
                enteredUnit = unit,
                resolvedBaseAmount = calculation.resolvedBaseAmount,
                nutritionSnapshot = calculation.nutrition,
                conversionProvenance = calculation.provenance,
                approximateConversion = calculation.approximate,
                sourcePhrase = food.name,
                mealSlot = mealSlot,
                dayKey = dayKey,
                resolvedBaseUnit = food.nutrition.unit,
            )
        )
    }

    /** Learns a pick; nothing while [learnFromPicks] is off. */
    suspend fun reinforce(foodId: String) { if (learnFromPicks) store.reinforce(foodId) }

    /** Reloads the stored diary when the read after a committed write failed; true when it reloaded. */
    suspend fun reloadIfStale(): Boolean = store.reloadIfStale()

    /** A damaged diary was set aside and the user has not seen the notice yet (see `data/DamagedDiaries`). */
    fun damagedNoticePending(): Boolean = store.damagedNoticePending()

    fun damagedNoticeSeen() = store.damagedNoticeSeen()

    fun foods(): List<FoodRecord> = store.foods()

    suspend fun foodByBarcode(barcode: String): FoodRecord? = store.foodByBarcode(barcode)

    suspend fun deleteEntry(id: String) = store.deleteEntry(id)

    suspend fun restoreEntry(entry: MealEntry) = store.saveEntries(listOf(entry))

    /** Undoes a logged batch in one transaction; an entry changed since [entries] were saved stays. Returns the ids deleted. */
    suspend fun deleteEntries(entries: List<MealEntry>): List<String> = store.deleteEntries(entries)

    /** Deletes a food for good; past entries keep their own name and numbers. */
    suspend fun deleteFood(foodId: String) {
        store.deleteFood(foodId)
        invalidateMatcher()
    }

    suspend fun updateFood(food: FoodRecord) {
        store.upsertFood(food.copy(personalOverride = true))
        invalidateMatcher()
    }

    suspend fun foodById(foodId: String): FoodRecord? = store.foodById(foodId)

    suspend fun foodsMatching(query: String = ""): List<FoodRecord> = store.foodsMatching(query)

    suspend fun saveCustomAndLog(
        name: String,
        brand: String?,
        barcode: String?,
        alias: String? = null,
        nutrition: NutritionValues,
        basisAmount: Double,
        basisUnit: MeasureUnit,
        mealSlot: MealSlot,
        amount: Double,
        amountUnit: MeasureUnit,
        nowMs: Long = System.currentTimeMillis(),
        dayKey: String,
        sourceName: String? = null,
        sourceDescription: String? = null
    ): MealEntry {
        require(name.isNotBlank()) { "Name is required" }
        require(basisAmount.isFinite() && basisAmount > 0.0) { "Nutrition basis must be positive" }
        require(amount.isFinite() && amount > 0.0) { "Amount must be positive" }
        require(nutrition.calories.isFinite() && nutrition.calories >= 0.0) { "Calories are required" }
        val id = "custom-" + UUID.randomUUID()
        val food = FoodRecord(
            id = id,
            name = name.trim(),
            aliases = alias?.let(::splitAliases).orEmpty(),
            category = "Personal food",
            origin = FoodOrigin.CUSTOM,
            nutrition = NutritionBasis(basisAmount, basisUnit, nutrition),
            conversions = listOf(ServingConversion(basisUnit, basisAmount, "custom basis")),
            defaultAmount = amount,
            defaultUnit = amountUnit,
            brand = brand?.trim()?.ifBlank { null },
            barcode = barcode?.trim()?.ifBlank { null },
            sourceName = sourceName,
            sourceId = barcode?.trim()?.ifBlank { null },
            sourceDescription = sourceDescription,
            personalOverride = true
        )
        val calculation = calculator.calculate(food, amount, amountUnit)
        require(calculation.supported) { "Amount unit is incompatible with the nutrition basis" }
        val entry = MealEntry(UUID.randomUUID().toString(), id, food.name, amount, amountUnit,
            calculation.resolvedBaseAmount, calculation.nutrition, calculation.provenance,
            calculation.approximate, nowMs, name.trim(), mealSlot, dayKey, basisUnit)
        store.addFoodAndEntry(food, entry)
        invalidateMatcher()
        return entry
    }

    suspend fun saveCustomFood(
        name: String, brand: String?, barcode: String?, alias: String? = null, nutrition: NutritionValues,
        basisAmount: Double, basisUnit: MeasureUnit, sourceName: String? = null,
        sourceDescription: String? = null
    ): FoodRecord {
        require(name.isNotBlank()) { "Name is required" }
        require(basisAmount.isFinite() && basisAmount > 0.0) { "Nutrition basis must be positive" }
        require(nutrition.calories.isFinite() && nutrition.calories >= 0.0) { "Calories are required" }
        val food = FoodRecord("custom-" + UUID.randomUUID(), name.trim(), alias?.let(::splitAliases).orEmpty(), "Personal food", FoodOrigin.CUSTOM,
            NutritionBasis(basisAmount, basisUnit, nutrition), listOf(ServingConversion(basisUnit, basisAmount, "custom basis")),
            basisAmount, basisUnit, sourceId = barcode?.trim()?.ifBlank { null }, sourceName = sourceName, sourceDescription = sourceDescription,
            personalOverride = true,
            brand = brand?.trim()?.ifBlank { null }, barcode = barcode?.trim()?.ifBlank { null })
        store.upsertFood(food)
        invalidateMatcher()
        return food
    }

    /** Stores a food for later logs without logging it: the food a Log part was picked as. Returns the food as it is stored. */
    suspend fun cacheFood(food: FoodRecord): FoodRecord {
        val effectiveFood = cachedFoodForLogging(food, store.foodById(food.id), food.barcode?.let { store.foodByBarcode(it) })
        store.addFood(effectiveFood)
        invalidateMatcher()
        return effectiveFood
    }

    suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, mealSlot: MealSlot, dayKey: String): MealEntry {
        val effectiveFood = cachedFoodForLogging(
            food,
            store.foodById(food.id),
            food.barcode?.let { store.foodByBarcode(it) }
        )
        require(amount.isFinite() && amount > 0.0) { "Enter a positive amount." }
        val calculation = calculator.calculate(effectiveFood, amount, unit)
        require(calculation.supported) { "Amount unit is incompatible with the nutrition basis" }
        val now = System.currentTimeMillis()
        val entry = MealEntry(UUID.randomUUID().toString(), effectiveFood.id, effectiveFood.name, amount, unit,
            calculation.resolvedBaseAmount, calculation.nutrition, calculation.provenance,
            calculation.approximate, now, effectiveFood.name, mealSlot, dayKey, effectiveFood.nutrition.unit)
        store.addFoodAndEntry(effectiveFood, entry)
        invalidateMatcher()
        return entry
    }

    fun persistedMeal(): SavedMeal {
        val entries = store.entries()
        return SavedMeal(
            entries = entries,
            totals = nutritionTotalOf(entries)
        )
    }

    fun totalsForToday(): NutritionValues = nutritionTotalOf(store.entries())
}

/** The preferences ranking reads: [stored] when picks are learned, else none (the same empty map every time). */
internal fun rankingPreferences(learn: Boolean, stored: Map<String, Preference>): Map<String, Preference> =
    if (learn) stored else NO_PREFERENCES

private val NO_PREFERENCES: Map<String, Preference> = emptyMap()

/**
 * An entry whose food was deleted, at [amount] of its own unit: its own snapshot scaled in proportion, the only honest
 * figure left. Null for another unit (nothing to convert with) or a stored amount that is not a positive number.
 */
internal fun snapshotScaled(entry: MealEntry, amount: Double, unit: MeasureUnit): Calculation? {
    if (unit != entry.enteredUnit || !amount.isFinite() || amount <= 0.0 || !snapshotScalable(entry)) return null
    val factor = amount / entry.enteredAmount
    return Calculation(entry.nutritionSnapshot * factor, entry.resolvedBaseAmount * factor, entry.conversionProvenance, entry.approximateConversion, supported = true)
}

/** Whether an entry's amount can change without its food: [snapshotScaled] needs a positive stored amount. */
internal fun snapshotScalable(entry: MealEntry): Boolean = entry.enteredAmount.isFinite() && entry.enteredAmount > 0.0

private fun MealEntry.withScaled(amount: Double, scaled: Calculation) =
    copy(enteredAmount = amount, resolvedBaseAmount = scaled.resolvedBaseAmount, nutritionSnapshot = scaled.nutrition)

internal fun cachedFoodForLogging(candidate: FoodRecord, byId: FoodRecord?, byBarcode: FoodRecord?): FoodRecord =
    listOfNotNull(byId, byBarcode).firstOrNull { it.personalOverride } ?: candidate

data class ParsedAmountUnit(val amount: Double, val unit: MeasureUnit)

/** Strict amount/unit parsing used by saved-entry quick edit. */
fun parseStrictAmountUnit(text: String): ParsedAmountUnit? {
    val parsed = ShorthandParser().parse(text)
    val amount = parsed.amount
    val unit = parsed.unit
    if (parsed.foodPhrase.isNotBlank() || amount == null || unit == null ||
        !amount.isFinite() || amount <= 0.0
    ) return null
    return ParsedAmountUnit(amount, unit)
}

/**
 * The saved time of the [index]th entry of one save. Entries saved together share a moment, so each gets its own
 * millisecond in typed order: the diary lists them by saved time, and the typed order is then kept.
 */
internal fun orderedSavedAt(nowMs: Long, index: Int): Long = nowMs + index

/** One [FoodMatcher] per foods list object, built by [build] the first time that list is asked for. */
internal class MatcherCache(
    private val build: (List<FoodRecord>) -> FoodMatcher = { FoodMatcher(it) },
) {
    private var source: List<FoodRecord>? = null
    private var matcher: FoodMatcher? = null

    @Synchronized
    fun matcherFor(foods: List<FoodRecord>): FoodMatcher {
        val current = matcher
        if (current != null && foods === source) return current
        return build(foods).also { matcher = it; source = foods }
    }

    /** The next [matcherFor] builds again, whatever list it gets. */
    @Synchronized
    fun invalidate() {
        source = null
    }
}
