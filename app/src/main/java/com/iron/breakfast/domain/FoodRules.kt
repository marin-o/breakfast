package com.iron.breakfast.domain

import java.util.Locale

/**
 * Source boxes describe the local record's provenance. Personal overrides remain P even when
 * their underlying record came from OFF. A bundled marker is reserved for an actual catalogue
 * fixture; an otherwise unknown generic record has no trustworthy source and is marked "?".
 */
internal fun sourceLetter(food: FoodRecord?): String = when {
    food == null -> "?"
    food.origin == FoodOrigin.CUSTOM || food.personalOverride -> "P"
    FoodCatalog.fixtures.any { it.id == food.id } -> "B"
    isOpenFoodFactsSource(food) -> "O"
    else -> "?"
}

/** OFF provenance is independent from the source box: local overrides still may be contributed. */
internal fun isOpenFoodFactsSource(food: FoodRecord): Boolean =
    food.sourceName?.contains("open food facts", ignoreCase = true) == true ||
        food.sourceId?.startsWith("off", ignoreCase = true) == true

/** The start of the id of a food that came from the USDA catalogue, before its FDC ID. */
internal const val USDA_FOOD_ID_PREFIX = "fndds_"

internal fun isUsdaFoodId(id: String): Boolean = id.startsWith(USDA_FOOD_ID_PREFIX)

/** The entry [foodId] was last logged as: what a repeat of it starts from. */
internal fun lastLoggedEntry(entries: List<MealEntry>, foodId: String): MealEntry? =
    entries.asSequence().filter { it.foodId == foodId }.maxByOrNull { it.savedAtEpochMs }

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
    /** Grams in 100 ml ("100 ml = X g"); null when not known. */
    density: Double? = original.density,
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
        personalOverride = true,
        density = density,
    )
}

/** The calories of [food]'s default serving, as the Library row shows it. */
internal fun defaultServingCalculation(food: FoodRecord): Calculation =
    NutritionCalculator().calculate(food, food.defaultAmount, food.defaultUnit)
