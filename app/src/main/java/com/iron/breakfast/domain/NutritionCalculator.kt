package com.iron.breakfast.domain

class NutritionCalculator {
    fun calculate(food: FoodRecord, amount: Double, enteredUnit: MeasureUnit?): Calculation {
        val unit = enteredUnit ?: food.defaultUnit
        val conversion = food.conversions.firstOrNull { it.unit == unit }
        val resolvedAmount = when {
            unit == food.nutrition.unit -> amount
            conversion != null -> amount * conversion.baseUnitsPerServing
            unit == MeasureUnit.PIECE && food.nutrition.unit == MeasureUnit.MILLILITER -> amount * 100.0
            unit == MeasureUnit.PIECE && food.nutrition.unit == MeasureUnit.GRAM -> amount * 100.0
            else -> amount
        }
        val ratio = resolvedAmount / food.nutrition.amount
        return Calculation(
            nutrition = food.nutrition.values * ratio,
            resolvedBaseAmount = resolvedAmount,
            provenance = when {
                unit == food.nutrition.unit -> "entered ${unit.displayLabel()} on ${food.nutrition.amount}${food.nutrition.unit.displayLabel()} basis"
                conversion != null -> conversion.provenance
                else -> "same-unit fallback; review conversion"
            },
            approximate = conversion?.approximate ?: unit != food.nutrition.unit
        )
    }
}
