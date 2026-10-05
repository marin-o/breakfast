package com.iron.breakfast.domain

class NutritionCalculator {
    fun calculate(food: FoodRecord, amount: Double, enteredUnit: MeasureUnit?): Calculation {
        val unit = enteredUnit ?: food.defaultUnit
        val conversion = food.conversions.firstOrNull { it.unit == unit }
        val supported = unit == food.nutrition.unit || conversion != null
        if (!supported) {
            return Calculation(
                nutrition = NutritionValues.ZERO,
                resolvedBaseAmount = amount,
                provenance = "unsupported ${unit.displayLabel()} conversion for ${food.name}",
                approximate = true,
                supported = false
            )
        }
        val resolvedAmount = if (unit == food.nutrition.unit) amount else amount * conversion!!.baseUnitsPerServing
        val ratio = resolvedAmount / food.nutrition.amount
        return Calculation(
            nutrition = food.nutrition.values * ratio,
            resolvedBaseAmount = resolvedAmount,
            provenance = when {
                unit == food.nutrition.unit -> "entered ${unit.displayLabel()} on ${food.nutrition.amount}${food.nutrition.unit.displayLabel()} basis"
                conversion != null -> conversion.provenance
                else -> "unsupported conversion"
            },
            approximate = conversion?.approximate ?: false,
            supported = true
        )
    }
}
