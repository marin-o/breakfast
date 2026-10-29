package com.iron.breakfast.domain

import java.util.Locale

/** Library order: the most recently logged food first, then by name. */
internal fun libraryRecencyOrder(foods: List<FoodRecord>, entries: List<MealEntry>): List<FoodRecord> =
    recencyOrder(foods, entries, FoodRecord::id, FoodRecord::name)

/** [items] with the one logged most recently first (by the entries naming its [id]), then by [name]. */
internal fun <T> recencyOrder(items: List<T>, entries: List<MealEntry>, id: (T) -> String, name: (T) -> String): List<T> {
    val latestByFood = entries.groupBy { it.foodId }.mapValues { (_, values) -> values.maxOfOrNull { it.savedAtEpochMs } ?: Long.MIN_VALUE }
    return items.sortedWith(compareByDescending<T> { latestByFood[id(it)] ?: Long.MIN_VALUE }.thenBy { name(it).lowercase(Locale.ROOT) })
}

/**
 * Applies form edits to a stored food. [aliases] and [servings], when given, replace the stored lists
 * outright (so the last alias or serving can be removed); otherwise [aliasText] keeps its older
 * "blank leaves aliases alone" meaning and imported servings are preserved.
 * `sourceId` stays untouched for non-custom foods so an Open Food Facts food keeps its original code.
 */
internal fun editedCustomFoodRecord(
    original: FoodRecord,
    name: String,
    brand: String,
    barcode: String,
    aliasText: String,
    nutrition: NutritionValues,
    basisAmount: Double,
    basisUnit: MeasureUnit,
    aliases: List<String>? = null,
    servings: List<ServingConversion>? = null,
): FoodRecord {
    val editedAliases = aliases ?: aliasText.trim().takeIf { it.isNotBlank() }
        ?.let(::splitAliases)
        ?: original.aliases
    val isCustom = original.origin == FoodOrigin.CUSTOM
    val preserveImportedServings = !isCustom && original.nutrition.unit == basisUnit
    val basisConversion = ServingConversion(basisUnit, basisAmount, "custom basis")
    val extraServings = servings.orEmpty().filterNot { it.unit == basisUnit && it.label.isNullOrBlank() }
    val importedBasis = original.conversions.filter { it.unit == basisUnit && it.label.isNullOrBlank() }
    val conversions = when {
        servings == null -> if (preserveImportedServings) original.conversions else listOf(basisConversion)
        preserveImportedServings -> importedBasis + extraServings
        else -> listOf(basisConversion) + extraServings
    }
    val keepDefault = preserveImportedServings && (servings == null || original.defaultUnit == basisUnit || extraServings.any { it.unit == original.defaultUnit })
    return original.copy(
        name = name.trim(), brand = brand.trim().ifBlank { null }, barcode = barcode.trim().ifBlank { null },
        sourceId = if (isCustom) barcode.trim().ifBlank { null } else original.sourceId,
        aliases = editedAliases,
        nutrition = NutritionBasis(basisAmount, basisUnit, nutrition),
        conversions = conversions,
        defaultAmount = if (keepDefault) original.defaultAmount else basisAmount,
        defaultUnit = if (keepDefault) original.defaultUnit else basisUnit,
        personalOverride = true
    )
}

/** The calories of [food]'s default serving, as the Library row shows it. */
internal fun defaultServingCalculation(food: FoodRecord): Calculation =
    NutritionCalculator().calculate(food, food.defaultAmount, food.defaultUnit)
