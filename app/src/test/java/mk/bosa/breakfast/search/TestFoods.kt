package mk.bosa.breakfast.search

import mk.bosa.breakfast.domain.FoodOrigin
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.NutritionBasis
import mk.bosa.breakfast.domain.NutritionValues

/** An Open Food Facts product of this [name], for the search tests. */
internal fun product(name: String) =
    FoodRecord(name, name, emptyList(), "off", FoodOrigin.GENERIC, NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(50.0, null, null, null)))
