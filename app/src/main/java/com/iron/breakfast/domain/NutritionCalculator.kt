package com.iron.breakfast.domain

class NutritionCalculator {
    fun calculate(food: FoodRecord, amount: Double, enteredUnit: MeasureUnit?): Calculation {
        val unit = enteredUnit ?: food.defaultUnit
        val conversion = food.conversions.firstOrNull { it.unit == unit }
        val fallback = conversion ?: fallbackConversion(food.nutrition, unit)
        val supported = unit == food.nutrition.unit || fallback != null
        // An amount of zero, below zero or not a number ("0 banana", "1/0 banana", "-1 banana") is never counted.
        val validAmount = amount.isFinite() && amount > 0.0
        if (!supported || !validAmount) {
            return Calculation(
                nutrition = NutritionValues.ZERO,
                resolvedBaseAmount = amount,
                provenance = if (!validAmount) "invalid amount" else "unsupported ${unit.displayLabel()} conversion for ${food.name}",
                approximate = true,
                supported = false
            )
        }
        val resolvedAmount = if (unit == food.nutrition.unit) amount else amount * fallback!!.baseUnitsPerServing
        val ratio = resolvedAmount / food.nutrition.amount
        return Calculation(
            nutrition = food.nutrition.values * ratio,
            resolvedBaseAmount = resolvedAmount,
            provenance = when {
                unit == food.nutrition.unit -> "entered ${unit.displayLabel()} on ${food.nutrition.amount}${food.nutrition.unit.displayLabel()} basis"
                fallback != null -> fallback.provenance
                else -> "unsupported conversion"
            },
            approximate = fallback?.approximate ?: false,
            supported = true
        )
    }

    private fun fallbackConversion(basis: NutritionBasis, enteredUnit: MeasureUnit): ServingConversion? {
        val baseUnits = when (enteredUnit) {
            MeasureUnit.TABLESPOON -> 15.0
            MeasureUnit.TEASPOON -> 5.0
            else -> return null
        }
        val basisLabel = "per-${basis.amount.prettyAmount()} ${basis.unit.displayLabel()}"
        return when (basis.unit) {
            MeasureUnit.MILLILITER -> ServingConversion(
                enteredUnit,
                baseUnits,
                "Approximate fallback: 1 ${enteredUnit.displayLabel()} = ${baseUnits.prettyAmount()} ml household measure (NIST); applied to $basisLabel nutrition basis",
                approximate = true
            )
            MeasureUnit.GRAM -> ServingConversion(
                enteredUnit,
                baseUnits,
                "Approximate fallback: 1 ${enteredUnit.displayLabel()} = ${baseUnits.prettyAmount()} g rough estimate; density varies; applied to $basisLabel nutrition basis",
                approximate = true
            )
            else -> null
        }
    }

    private fun Double.prettyAmount(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()
}
