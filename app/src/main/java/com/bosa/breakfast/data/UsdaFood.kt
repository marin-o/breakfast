package com.bosa.breakfast.data

import com.bosa.breakfast.domain.FoodOrigin
import com.bosa.breakfast.domain.FoodRecord
import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.domain.NutritionBasis
import com.bosa.breakfast.domain.NutritionValues
import com.bosa.breakfast.domain.ServingConversion
import com.bosa.breakfast.domain.USDA_FOOD_ID_PREFIX
import java.util.Locale

/** A kept portion of a USDA food: its printed label ("1 cup, cooked, diced") and its grams. */
internal data class UsdaPortionRow(val label: String, val grams: Double)

/** A food as the USDA catalogue file holds it, per 100 g. A null macro is unknown. */
internal data class UsdaFoodRow(
    val fdcId: Long,
    val foodCode: String,
    val description: String,
    val additional: String?,
    val kcal: Double,
    val protein: Double?,
    val fat: Double?,
    val carbs: Double?,
    val portions: List<UsdaPortionRow>,
)

/**
 * The food a USDA row becomes when it is offered, stored only if the user adds it: id `fndds_<fdcId>`, the original
 * description as its name. Each portion is a named, approximate serving in grams, "1 cup, cooked" being named
 * "cup, cooked"; a label that doesn't start with one ("1/2 bagel") keeps its words. The first portion of one cup,
 * tablespoon or teaspoon is also what that unit weighs for this food, and one labelled only that ("1 cup") is that and
 * no named serving, so the unit isn't offered twice. Null when the row fails the bounds every food read from outside
 * passes ([plausibleNutrition]).
 *
 * Once stored it is the user's: the catalogue is never written back to it.
 */
internal fun UsdaFoodRow.toFoodRecord(): FoodRecord? {
    val values = NutritionValues(kcal, protein, carbs, fat)
    if (!plausibleNutrition(values)) return null
    val units = USDA_PORTION_UNITS.mapNotNull { (word, unit) ->
        portions.firstOrNull { it.isOne(word) }?.let { ServingConversion(unit, it.grams, USDA_PORTION_PROVENANCE, approximate = true) }
    }
    val servings = portions.filterNot { portion -> USDA_PORTION_UNITS.keys.any { portion.label.equals("1 $it", ignoreCase = true) } }.map { portion ->
        ServingConversion(MeasureUnit.GRAM, portion.grams, USDA_PORTION_PROVENANCE, approximate = true, label = portion.label.removePrefix("1 "))
    }.distinctBy { it.label?.lowercase(Locale.ROOT) }
    return FoodRecord(
        id = "$USDA_FOOD_ID_PREFIX$fdcId",
        name = description,
        aliases = emptyList(),
        category = USDA_SOURCE_NAME,
        origin = FoodOrigin.GENERIC,
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, values),
        conversions = listOf(ServingConversion(MeasureUnit.GRAM, 1.0, "$USDA_SOURCE_NAME per 100 g")) + units + servings,
        defaultAmount = 100.0,
        defaultUnit = MeasureUnit.GRAM,
        sourceId = fdcId.toString(),
        sourceName = USDA_SOURCE_NAME,
        sourceRelease = USDA_SOURCE_RELEASE,
        sourceDescription = listOfNotNull("FNDDS $foodCode", additional).joinToString(": "),
    )
}

/** This portion is one of [word]: "1 cup", "1 cup, cooked", "1 cup (8 fl oz)"; not "1 cupcake" or "1/2 cup". */
private fun UsdaPortionRow.isOne(word: String): Boolean {
    val one = "1 $word"
    return label.startsWith(one, ignoreCase = true) && label.getOrNull(one.length)?.isLetter() != true
}

private val USDA_PORTION_UNITS = mapOf(
    "cup" to MeasureUnit.CUP,
    "tablespoon" to MeasureUnit.TABLESPOON,
    "teaspoon" to MeasureUnit.TEASPOON,
)

private const val USDA_SOURCE_NAME = "USDA FoodData Central"
private const val USDA_SOURCE_RELEASE = "FNDDS 2021-2023"
private const val USDA_PORTION_PROVENANCE = "USDA FNDDS 2021-2023 portion"
