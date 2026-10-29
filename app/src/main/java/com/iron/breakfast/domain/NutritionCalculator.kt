package com.iron.breakfast.domain

class NutritionCalculator {
    /**
     * The nutrition of [amount] of [enteredUnit] (the food's default unit when null) of [food]. [servingName] names the
     * serving for [MeasureUnit.SERVING]; for any other unit it is only the label the amount is shown with.
     *
     * What one of the unit is in the food's basis units: a named serving's own amount; the basis itself; the food's own
     * conversion for that unit (its "piece", "tbsp"); else the unit's family: a volume unit on ml by its size, either
     * family through the food's density, and tsp, tbsp and cup on g without one as 1 ml ≈ 1 g, marked approximate.
     * Plain ml (and the units the shorthand reads as ml) on g without a density, and g on ml without one, are unsupported.
     */
    fun calculate(food: FoodRecord, amount: Double, enteredUnit: MeasureUnit?, servingName: String? = null): Calculation {
        val unit = enteredUnit ?: food.defaultUnit
        val per = perUnit(food, unit, servingName)
        // An amount of zero, below zero or not a number ("0 banana", "1/0 banana", "-1 banana") is never counted.
        val validAmount = amount.isFinite() && amount > 0.0
        if (per == null || !validAmount) {
            val shown = if (unit == MeasureUnit.SERVING) servingName.orEmpty() else unit.displayLabel()
            return Calculation(
                nutrition = NutritionValues.ZERO,
                resolvedBaseAmount = amount,
                provenance = if (!validAmount) "invalid amount" else "unsupported $shown conversion for ${food.name}",
                approximate = true,
                supported = false
            )
        }
        val resolvedAmount = amount * per.baseUnits
        return Calculation(
            nutrition = food.nutrition.values * (resolvedAmount / food.nutrition.amount),
            resolvedBaseAmount = resolvedAmount,
            provenance = per.provenance,
            approximate = per.approximate,
            supported = true
        )
    }

    /** One of a unit in the food's basis units, where that figure came from, and whether it is a guess. */
    private class PerUnit(val baseUnits: Double, val provenance: String, val approximate: Boolean)

    private fun perUnit(food: FoodRecord, unit: MeasureUnit, servingName: String?): PerUnit? {
        val basis = food.nutrition
        if (unit == MeasureUnit.SERVING) {
            val serving = servingName?.let(food::servingNamed) ?: return null
            return PerUnit(serving.baseUnitsPerServing, serving.provenance, serving.approximate)
        }
        if (unit == basis.unit) return PerUnit(1.0, "entered ${unit.displayLabel()} on ${basis.amount}${basis.unit.displayLabel()} basis", false)
        unitConversion(food, unit)?.let { return PerUnit(it.baseUnitsPerServing, it.provenance, it.approximate) }
        return familyConversion(food, unit)
    }

    /**
     * The food's own conversion for [unit]: one with no name of its own (or the unit's own, "piece", "tbsp"), so a named
     * serving in g ("slice = 30 g") never stands for "g". A piece or spoon row that only has another name is kept as
     * before, when nothing else is in that unit. g on ml and ml on g never come from a row: only from the density.
     */
    private fun unitConversion(food: FoodRecord, unit: MeasureUnit): ServingConversion? {
        if (crossesFamily(food, unit)) return null
        return food.conversions.firstOrNull { it.unit == unit && (it.name == null || it.name == defaultServingName(unit)) }
            ?: food.conversions.firstOrNull { it.unit == unit }.takeIf { defaultServingName(unit) != null }
    }

    private fun familyConversion(food: FoodRecord, unit: MeasureUnit): PerUnit? {
        val basis = food.nutrition
        val density = food.density?.takeIf { it.isFinite() && it > 0.0 }
        val ml = millilitresIn(unit)
        val basisLabel = "per-${basis.amount.prettyAmount()} ${basis.unit.displayLabel()}"
        return when (basis.unit) {
            MeasureUnit.MILLILITER -> when {
                ml != null -> PerUnit(ml, "1 ${unit.displayLabel()} = ${ml.prettyAmount()} ml; applied to $basisLabel nutrition basis", false)
                unit == MeasureUnit.GRAM && density != null ->
                    PerUnit(100.0 / density, "100 ml = ${density.prettyAmount()} g; applied to $basisLabel nutrition basis", false)
                else -> null
            }
            MeasureUnit.GRAM -> when {
                ml != null && density != null ->
                    PerUnit(ml * density / 100.0, "1 ${unit.displayLabel()} = ${ml.prettyAmount()} ml, 100 ml = ${density.prettyAmount()} g; applied to $basisLabel nutrition basis", false)
                ml != null && unit != MeasureUnit.MILLILITER -> PerUnit(
                    ml,
                    "Approximate fallback: 1 ${unit.displayLabel()} = ${ml.prettyAmount()} g rough estimate; density varies; applied to $basisLabel nutrition basis",
                    true,
                )
                else -> null
            }
            else -> null
        }
    }

    private fun Double.prettyAmount(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()
}
