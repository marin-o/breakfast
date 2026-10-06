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
        val foods = store.foods()
        if (cachedMatcher == null || foods !== matcherCatalog) {
            matcherCatalog = foods
            cachedMatcher = FoodMatcher(foods)
        }
        return cachedMatcher!!
    }

    fun parse(input: String): ParsedSegment = parser.parse(input)

    fun suggestions(input: String): List<RankedFood> {
        val parsed = parser.parse(input)
        return matcher().suggestions(parsed.foodPhrase, store.preferenceMap())
    }

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
        mealSlot: MealSlot = MealSlot.BREAKFAST
    ): SavedMeal {
        val unresolved = mutableListOf<String>()
        val entries = parser.split(input).mapNotNull { raw ->
            val parsed = parser.parse(raw)
            val match = matcher().best(parsed.foodPhrase, store.preferenceMap())
            if (match == null) {
                unresolved += raw
                return@mapNotNull null
            }
            val amount = parsed.amount ?: match.food.defaultAmount
            val unit = parsed.unit ?: match.food.defaultUnit
            val calculation = calculator.calculate(match.food, amount, unit)
            if (!calculation.supported) {
                unresolved += raw
                return@mapNotNull null
            }
            MealEntry(
                id = UUID.randomUUID().toString(),
                foodId = match.food.id,
                foodName = match.food.name,
                enteredAmount = amount,
                enteredUnit = unit,
                resolvedBaseAmount = calculation.resolvedBaseAmount,
                nutritionSnapshot = calculation.nutrition,
                conversionProvenance = calculation.provenance,
                approximateConversion = calculation.approximate,
                savedAtEpochMs = nowMs,
                sourcePhrase = raw,
                mealSlot = mealSlot,
                dayKey = dayKeyForEpochMs(nowMs)
            )
        }
        if (entries.isNotEmpty()) store.saveEntries(entries)
        return SavedMeal(
            entries = entries,
            totals = entries.fold(NutritionValues.ZERO) { total, entry -> total + entry.nutritionSnapshot },
            unresolvedSegments = unresolved
        )
    }

    suspend fun replaceEntry(entryId: String, foodId: String): ReplacementResult {
        val old = store.entries().firstOrNull { it.id == entryId }
            ?: return ReplacementResult.Failure("That saved entry is no longer available.")
        val food = store.foods().firstOrNull { it.id == foodId }
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
            approximateConversion = calculation.approximate
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
            approximateConversion = calculation.approximate
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
            conversionProvenance = calculation.provenance, approximateConversion = calculation.approximate, sourcePhrase = text)
        store.replaceEntry(updated)
        return ReplacementResult.Success(updated)
    }

    suspend fun reinforce(foodId: String) = store.reinforce(foodId)

    fun foods(): List<FoodRecord> = store.foods()

    suspend fun foodByBarcode(barcode: String): FoodRecord? = store.foodByBarcode(barcode)

    suspend fun deleteEntry(id: String) = store.deleteEntry(id)

    suspend fun restoreEntry(entry: MealEntry) = store.saveEntries(listOf(entry))

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
        nowMs: Long = System.currentTimeMillis()
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
            barcode = barcode?.trim()?.ifBlank { null }
        )
        val calculation = calculator.calculate(food, amount, amountUnit)
        require(calculation.supported) { "Amount unit is incompatible with the nutrition basis" }
        val entry = MealEntry(UUID.randomUUID().toString(), id, food.name, amount, amountUnit,
            calculation.resolvedBaseAmount, calculation.nutrition, calculation.provenance,
            calculation.approximate, nowMs, name.trim(), mealSlot, dayKeyForEpochMs(nowMs))
        store.addFoodAndEntry(food, entry)
        matcherCatalog = null
        return entry
    }

    suspend fun saveCustomFood(
        name: String, brand: String?, barcode: String?, alias: String? = null, nutrition: NutritionValues,
        basisAmount: Double, basisUnit: MeasureUnit
    ): FoodRecord {
        require(name.isNotBlank()) { "Name is required" }
        require(basisAmount.isFinite() && basisAmount > 0.0) { "Nutrition basis must be positive" }
        require(nutrition.calories.isFinite() && nutrition.calories >= 0.0) { "Calories are required" }
        val food = FoodRecord("custom-" + UUID.randomUUID(), name.trim(), alias?.split(',')?.map(String::trim)?.filter(String::isNotBlank).orEmpty(), "Personal food", FoodOrigin.CUSTOM,
            NutritionBasis(basisAmount, basisUnit, nutrition), listOf(ServingConversion(basisUnit, basisAmount, "custom basis")),
            basisAmount, basisUnit, brand = brand?.trim()?.ifBlank { null }, barcode = barcode?.trim()?.ifBlank { null })
        store.upsertFood(food)
        matcherCatalog = null
        return food
    }

    suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, mealSlot: MealSlot): MealEntry {
        val calculation = calculator.calculate(food, amount, unit)
        require(calculation.supported) { "Amount unit is incompatible with the nutrition basis" }
        val now = System.currentTimeMillis()
        val entry = MealEntry(UUID.randomUUID().toString(), food.id, food.name, amount, unit,
            calculation.resolvedBaseAmount, calculation.nutrition, calculation.provenance,
            calculation.approximate, now, food.name, mealSlot, dayKeyForEpochMs(now))
        store.addFoodAndEntry(food, entry)
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
