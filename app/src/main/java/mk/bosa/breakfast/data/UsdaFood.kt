package mk.bosa.breakfast.data

import mk.bosa.breakfast.domain.FoodOrigin
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.NutritionBasis
import mk.bosa.breakfast.domain.NutritionValues
import mk.bosa.breakfast.domain.ServingConversion
import mk.bosa.breakfast.domain.USDA_FOOD_ID_PREFIX
import java.util.Locale

/** A kept portion of a USDA food: its printed label ("1 cup, cooked, diced") and its grams. */
internal data class UsdaPortionRow(val label: String, val grams: Double)

/** A food as the USDA catalogue file holds it, per 100 g. A null macro or nutrient is unknown; salt is in g, from sodium. */
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
    val saturatedFat: Double? = null,
    val sugars: Double? = null,
    val fibre: Double? = null,
    val salt: Double? = null,
    /** The release the row comes from: "FNDDS 2021-2023" or "SR Legacy 2018-04". */
    val release: String = USDA_FNDDS_RELEASE,
)

/**
 * The food a USDA row becomes when it is offered, stored only if the user adds it: id `fndds_<fdcId>` whichever release it comes from (the id of foods users already stored), the original
 * description as its name. Each portion is a named, approximate serving in grams, "1 cup, cooked" being named
 * "cup, cooked"; a label that doesn't start with one ("1/2 bagel") keeps its words. The first portion of one cup,
 * tablespoon or teaspoon is also what that unit weighs for this food, and one labelled only that ("1 cup") is that and
 * no named serving, so the unit isn't offered twice. Null when the row fails the bounds every food read from outside
 * passes ([plausibleNutrition]).
 *
 * Once stored it is the user's: the catalogue is never written back to it.
 */
internal fun UsdaFoodRow.toFoodRecord(): FoodRecord? {
    val macros = NutritionValues(kcal, protein, carbs, fat)
    if (!plausibleNutrition(macros)) return null
    val values = macros.copy(saturatedFat = plausibleExtra(saturatedFat), sugars = plausibleExtra(sugars), fibre = plausibleExtra(fibre), salt = plausibleExtra(salt))
    val units = USDA_PORTION_UNITS.mapNotNull { (word, unit) ->
        portions.firstOrNull { it.isOne(word) }?.let { ServingConversion(unit, it.grams, "USDA $release portion", approximate = true) }
    }
    val servings = portions.filterNot { portion -> USDA_PORTION_UNITS.keys.any { portion.label.equals("1 $it", ignoreCase = true) } }.map { portion ->
        ServingConversion(MeasureUnit.GRAM, portion.grams, "USDA $release portion", approximate = true, label = portion.label.removePrefix("1 "))
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
        sourceRelease = release,
        sourceDescription = listOfNotNull("${release.substringBeforeLast(' ')} $foodCode", additional).joinToString(": "),
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
internal const val USDA_FNDDS_RELEASE = "FNDDS 2021-2023"
