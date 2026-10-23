package com.iron.breakfast.domain

import com.iron.breakfast.data.BreakfastStore
import java.util.UUID

class BreakfastEngine(private val store: BreakfastStore) {
    private val parser = ShorthandParser()
    private val calculator = NutritionCalculator()
    private var matcherCatalog: List<FoodRecord>? = null
    private var cachedMatcher: FoodMatcher? = null

    suspend fun initialize() = store.initialize()

    private fun matcher(): FoodMatcher {
        val foods = foodsAvailableForLogging(store.foods())
        if (cachedMatcher == null || foods !== matcherCatalog) {
            matcherCatalog = foods
            cachedMatcher = FoodMatcher(foods)
        }
        return cachedMatcher!!
    }

    fun parse(input: String): ParsedSegment = parser.parse(input)

    fun search(query: String): List<RankedFood> = matcher().suggestions(query, store.preferenceMap(), 20)

    fun alternativesFor(entry: MealEntry): List<RankedFood> {
        val phrase = parser.parse(entry.sourcePhrase).foodPhrase.ifBlank { entry.foodName }
        return matcher().suggestions(phrase, store.preferenceMap(), 3)
            .filterNot { it.food.id == entry.foodId }
            .take(2)
    }

    suspend fun save(
        input: String,
        nowMs: Long = System.currentTimeMillis(),
        mealSlot: MealSlot = MealSlot.BREAKFAST,
        dayKey: String = dayKeyForEpochMs(nowMs),
        pins: Map<String, FoodRecord> = emptyMap(),
    ): SavedMeal {
        val plan = planner(pins).plan(input)
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
            totals = entries.fold(NutritionValues.ZERO) { total, entry -> total + entry.nutritionSnapshot },
            unresolvedSegments = plan.unresolved
        )
    }

    /** The planner for the current foods and preferences. [save] and the Log page both resolve through it. */
    fun planner(pins: Map<String, FoodRecord> = emptyMap()): LogPlanner = LogPlanner(matcher(), store.preferenceMap(), calculator, parser, pins)

    suspend fun replaceEntry(entryId: String, foodId: String): ReplacementResult {
        val old = store.entries().firstOrNull { it.id == entryId }
            ?: return ReplacementResult.Failure("That saved entry is no longer available.")
        val food = store.foods().firstOrNull { it.id == foodId && !it.archived }
            ?: return ReplacementResult.Failure("That catalogue record is no longer available.")
        val calculation = calculator.calculate(food, old.enteredAmount, old.enteredUnit)
        if (!calculation.supported) {
            return ReplacementResult.Failure(
                "${food.name} has no ${old.enteredUnit.displayLabel()} conversion. Re-enter this segment with a compatible amount such as ml or g."
            )
        }
        val updated = old.copy(
            foodId = food.id,
            foodName = food.name,
            resolvedBaseAmount = calculation.resolvedBaseAmount,
            nutritionSnapshot = calculation.nutrition,
            conversionProvenance = calculation.provenance,
            approximateConversion = calculation.approximate,
            resolvedBaseUnit = food.nutrition.unit
        )
        store.replaceEntry(updated)
        return ReplacementResult.Success(updated)
    }

    /**
     * Recalculates a saved entry for its original food record, changing only
     * the entered amount and unit. The parser is deliberately strict here:
     * quick edit must contain an amount and a supported unit, and it must not
     * be allowed to fall back to the food's defaults.
     */
    suspend fun replaceEntryAmountUnit(entryId: String, text: String): ReplacementResult {
        val old = store.entries().firstOrNull { it.id == entryId }
            ?: return ReplacementResult.Failure("That entry is no longer available.")
        val parsed = parseStrictAmountUnit(text)
            ?: return ReplacementResult.Failure("Enter a positive amount and a supported unit, such as 100 g or 1 tbsp.")
        val food = store.foods().firstOrNull { it.id == old.foodId }
            ?: return ReplacementResult.Failure("The original food for this entry is no longer available.")
        val calculation = calculator.calculate(food, parsed.amount, parsed.unit)
        if (!calculation.supported) {
            return ReplacementResult.Failure(
                "That amount/unit is not supported for ${food.name}."
            )
        }
        val updated = old.copy(
            enteredAmount = parsed.amount,
            enteredUnit = parsed.unit,
            resolvedBaseAmount = calculation.resolvedBaseAmount,
            nutritionSnapshot = calculation.nutrition,
            conversionProvenance = calculation.provenance,
            approximateConversion = calculation.approximate,
            resolvedBaseUnit = food.nutrition.unit
        )
        store.replaceEntry(updated)
        return ReplacementResult.Success(updated)
    }

    suspend fun replaceEntryFromText(entryId: String, text: String): ReplacementResult {
        val old = store.entries().firstOrNull { it.id == entryId } ?: return ReplacementResult.Failure("That entry is no longer available.")
        val parsed = parser.parse(text)
        val match = matcher().best(parsed.foodPhrase, store.preferenceMap()) ?: return ReplacementResult.Failure("No matching food.")
        val amount = parsed.amount ?: old.enteredAmount
        val unit = parsed.unit ?: old.enteredUnit
        val calculation = calculator.calculate(match.food, amount, unit)
        if (!calculation.supported) return ReplacementResult.Failure("That amount/unit is not supported for ${match.food.name}.")
        val updated = old.copy(foodId = match.food.id, foodName = match.food.name, enteredAmount = amount, enteredUnit = unit,
            resolvedBaseAmount = calculation.resolvedBaseAmount, nutritionSnapshot = calculation.nutrition,
            conversionProvenance = calculation.provenance, approximateConversion = calculation.approximate, sourcePhrase = text,
            resolvedBaseUnit = match.food.nutrition.unit)
        store.replaceEntry(updated)
        return ReplacementResult.Success(updated)
    }

    /** Replaces every editable field in one storage transaction, retaining the entry id. */
    suspend fun replaceEntryDraft(
        entryId: String,
        foodId: String,
        amount: Double,
        unit: MeasureUnit,
        mealSlot: MealSlot,
        dayKey: String,
    ): ReplacementResult {
        val old = store.entries().firstOrNull { it.id == entryId }
            ?: return ReplacementResult.Failure("That saved entry is no longer available.")
        val food = store.foods().firstOrNull { it.id == foodId && !it.archived }
            ?: return ReplacementResult.Failure("That catalogue record is no longer available.")
        if (!amount.isFinite() || amount <= 0.0) return ReplacementResult.Failure("Enter a positive amount.")
        val calculation = calculator.calculate(food, amount, unit)
        if (!calculation.supported) return ReplacementResult.Failure("That amount/unit is not supported for ${food.name}.")
        val updated = old.copy(
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
        store.replaceEntry(updated)
        return ReplacementResult.Success(updated)
    }

    suspend fun reinforce(foodId: String) = store.reinforce(foodId)

    fun foods(): List<FoodRecord> = store.foods()

    suspend fun foodByBarcode(barcode: String): FoodRecord? = store.foodByBarcode(barcode)

    suspend fun deleteEntry(id: String) = store.deleteEntry(id)

    suspend fun updateEntryPlacement(id: String, mealSlot: MealSlot, dayKey: String) {
        val entry = store.entries().firstOrNull { it.id == id } ?: return
        store.replaceEntry(entry.copy(mealSlot = mealSlot, dayKey = dayKey))
    }

    suspend fun restoreEntry(entry: MealEntry) = store.saveEntries(listOf(entry))

    suspend fun archiveFood(foodId: String) = store.archiveFood(foodId)

    suspend fun restoreFood(foodId: String) = store.restoreFood(foodId)

    suspend fun updateFood(food: FoodRecord) {
        store.upsertFood(food.copy(personalOverride = true))
        matcherCatalog = null
    }

    suspend fun foodById(foodId: String): FoodRecord? = store.foodById(foodId)

    suspend fun foodsMatching(query: String = "", includeArchived: Boolean = false): List<FoodRecord> =
        store.foodsMatching(query, includeArchived)

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
        dayKey: String = dayKeyForEpochMs(nowMs),
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
            aliases = alias?.split(',')?.map(String::trim)?.filter(String::isNotBlank).orEmpty(),
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
        matcherCatalog = null
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
        val food = FoodRecord("custom-" + UUID.randomUUID(), name.trim(), alias?.split(',')?.map(String::trim)?.filter(String::isNotBlank).orEmpty(), "Personal food", FoodOrigin.CUSTOM,
            NutritionBasis(basisAmount, basisUnit, nutrition), listOf(ServingConversion(basisUnit, basisAmount, "custom basis")),
            basisAmount, basisUnit, sourceId = barcode?.trim()?.ifBlank { null }, sourceName = sourceName, sourceDescription = sourceDescription,
            personalOverride = true,
            brand = brand?.trim()?.ifBlank { null }, barcode = barcode?.trim()?.ifBlank { null })
        store.upsertFood(food)
        matcherCatalog = null
        return food
    }

    /** Stores a food for later logs without logging it: the food a Log part was picked as. Returns the food as it is stored. */
    suspend fun cacheFood(food: FoodRecord): FoodRecord {
        val effectiveFood = cachedFoodForLogging(food, store.foodById(food.id), food.barcode?.let { store.foodByBarcode(it) })
        store.addFood(effectiveFood)
        matcherCatalog = null
        return effectiveFood
    }

    suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, mealSlot: MealSlot, dayKey: String = dayKeyForEpochMs(System.currentTimeMillis())): MealEntry {
        val effectiveFood = cachedFoodForLogging(
            food,
            store.foodById(food.id),
            food.barcode?.let { store.foodByBarcode(it) }
        )
        val calculation = calculator.calculate(effectiveFood, amount, unit)
        require(calculation.supported) { "Amount unit is incompatible with the nutrition basis" }
        val now = System.currentTimeMillis()
        val entry = MealEntry(UUID.randomUUID().toString(), effectiveFood.id, effectiveFood.name, amount, unit,
            calculation.resolvedBaseAmount, calculation.nutrition, calculation.provenance,
            calculation.approximate, now, effectiveFood.name, mealSlot, dayKey, effectiveFood.nutrition.unit)
        store.addFoodAndEntry(effectiveFood, entry)
        matcherCatalog = null
        return entry
    }

    fun persistedMeal(): SavedMeal {
        val entries = store.entries()
        return SavedMeal(
            entries = entries,
            totals = entries.fold(NutritionValues.ZERO) { total, entry -> total + entry.nutritionSnapshot }
        )
    }

    fun totalsForToday(): NutritionValues = store.entries()
        .fold(NutritionValues.ZERO) { total, entry -> total + entry.nutritionSnapshot }
}

internal fun cachedFoodForLogging(candidate: FoodRecord, byId: FoodRecord?, byBarcode: FoodRecord?): FoodRecord =
    listOfNotNull(byId, byBarcode).firstOrNull { it.personalOverride && !it.archived } ?: candidate

internal fun foodsAvailableForLogging(foods: List<FoodRecord>): List<FoodRecord> =
    foods.filterNot { it.archived }

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
