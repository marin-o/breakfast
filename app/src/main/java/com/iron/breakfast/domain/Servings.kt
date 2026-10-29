package com.iron.breakfast.domain

// Measurements: a food's named servings, the weight and volume units, and its density (docs/measurements-spec.md).

/** The name a serving row in [unit] has without a label of its own: "piece", "tbsp", "tsp"; null for any other unit. */
fun defaultServingName(unit: MeasureUnit): String? = when (unit) {
    MeasureUnit.PIECE -> "piece"
    MeasureUnit.TABLESPOON -> "tbsp"
    MeasureUnit.TEASPOON -> "tsp"
    else -> null
}
