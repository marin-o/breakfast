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

    fun save(input: String, nowMs: Long = System.currentTimeMillis()): SavedMeal {
        val entries = parser.split(input).mapNotNull { raw ->
            val parsed = parser.parse(raw)
            val match = matcher().best(parsed.foodPhrase, store.preferenceMap()) ?: return@mapNotNull null
            val amount = parsed.amount ?: match.food.defaultAmount
            val unit = parsed.unit ?: match.food.defaultUnit
            val calculation = calculator.calculate(match.food, amount, unit)
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
                savedAtEpochMs = nowMs
            )
        }
        store.saveEntries(entries)
        return SavedMeal(entries, entries.fold(NutritionValues.ZERO) { total, entry -> total + entry.nutritionSnapshot })
    }

    fun reinforce(foodId: String) = store.reinforce(foodId)

    fun foods(): List<FoodRecord> = store.foods()

    fun totalsForToday(): NutritionValues = store.entries()
        .fold(NutritionValues.ZERO) { total, entry -> total + entry.nutritionSnapshot }
}
