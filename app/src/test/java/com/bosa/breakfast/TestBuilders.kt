package com.bosa.breakfast

import com.bosa.breakfast.domain.FoodOrigin
import com.bosa.breakfast.domain.FoodRecord
import com.bosa.breakfast.domain.MealEntry
import com.bosa.breakfast.domain.MealSlot
import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.domain.NutritionBasis
import com.bosa.breakfast.domain.NutritionValues
import com.bosa.breakfast.domain.ServingConversion

/** The one way a unit test builds a food: on 100 g unless it says otherwise, with only what the test cares about named. */
internal fun testFood(
    id: String,
    name: String = id,
    aliases: List<String> = emptyList(),
    category: String = "Test",
    origin: FoodOrigin = FoodOrigin.CUSTOM,
    basisAmount: Double = 100.0,
    unit: MeasureUnit = MeasureUnit.GRAM,
    values: NutritionValues = NutritionValues(100.0, 1.0, 1.0, 1.0),
    conversions: List<ServingConversion>? = null,
    defaultAmount: Double? = null,
    defaultUnit: MeasureUnit? = null,
    barcode: String? = null,
    sourceId: String? = null,
    density: Double? = null,
    personalOverride: Boolean = false,
): FoodRecord {
    val plain = FoodRecord(id, name, aliases, category, origin, NutritionBasis(basisAmount, unit, values))
    return plain.copy(
        conversions = conversions ?: plain.conversions,
        defaultAmount = defaultAmount ?: plain.defaultAmount,
        defaultUnit = defaultUnit ?: plain.defaultUnit,
        barcode = barcode,
        sourceId = sourceId,
        density = density,
        personalOverride = personalOverride,
    )
}

/** The one way a unit test builds an entry: one piece of an egg unless it says otherwise. */
internal fun testEntry(
    id: String,
    foodId: String = "f",
    foodName: String = "Egg",
    values: NutritionValues = NutritionValues(70.0, 6.0, 0.5, 5.0),
    savedAt: Long = 0L,
    meal: MealSlot? = null,
    dayKey: String? = null,
): MealEntry {
    val plain = MealEntry(id, foodId, foodName, 1.0, MeasureUnit.PIECE, 50.0, values, "1 piece", false, savedAt)
    return plain.copy(mealSlot = meal ?: plain.mealSlot, dayKey = dayKey ?: plain.dayKey)
}
