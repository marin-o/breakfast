package com.iron.breakfast.domain

import com.iron.breakfast.data.BreakfastStore
import java.util.UUID

class BreakfastEngine(private val store: BreakfastStore) {
    private val parser = ShorthandParser()
    private val calculator = NutritionCalculator()

    private fun matcher() = FoodMatcher(store.foods())

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

    fun save(input: String, nowMs: Long = System.currentTimeMillis()): SavedMeal {
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
                sourcePhrase = raw
            )
        }
        if (entries.isNotEmpty()) store.saveEntries(entries)
        return SavedMeal(
            entries = entries,
            totals = entries.fold(NutritionValues.ZERO) { total, entry -> total + entry.nutritionSnapshot },
            unresolvedSegments = unresolved
        )
    }

    fun replaceEntry(entryId: String, foodId: String): ReplacementResult {
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

    fun reinforce(foodId: String) = store.reinforce(foodId)

    fun foods(): List<FoodRecord> = store.foods()

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
