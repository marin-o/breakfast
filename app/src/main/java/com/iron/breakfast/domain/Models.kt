package com.iron.breakfast.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.ln1p

@Serializable
enum class FoodOrigin { GENERIC, CUSTOM }

@Serializable
enum class MealSlot { BREAKFAST, LUNCH, DINNER, SNACKS }

@Serializable
enum class MeasureUnit(val label: String) {
    GRAM("g"), MILLILITER("ml"), TABLESPOON("tbsp"), TEASPOON("tsp"), PIECE("piece"), UNKNOWN("")
}

@Serializable
data class NutritionValues(
    val calories: Double,
    val protein: Double?,
    val carbohydrates: Double?,
    val fat: Double?
) {
    operator fun times(factor: Double) = NutritionValues(
        calories * factor,
        protein?.times(factor),
        carbohydrates?.times(factor),
        fat?.times(factor)
    )

    operator fun plus(other: NutritionValues) = NutritionValues(
        calories + other.calories,
        protein.sumIfSupplied(other.protein),
        carbohydrates.sumIfSupplied(other.carbohydrates),
        fat.sumIfSupplied(other.fat)
    )

    companion object {
        val ZERO = NutritionValues(0.0, 0.0, 0.0, 0.0)
    }
}

private fun Double?.sumIfSupplied(other: Double?): Double? = when {
    this == null -> other
    other == null -> this
    else -> this + other
}

@Serializable
data class NutritionBasis(
    val amount: Double,
    val unit: MeasureUnit,
    val values: NutritionValues
)

@Serializable
data class ServingConversion(
    val unit: MeasureUnit,
    val baseUnitsPerServing: Double,
    val provenance: String,
    val approximate: Boolean = false,
    /** Optional printed name for a personal serving such as "bottle" or "jar". */
    val label: String? = null
)

@Serializable
data class FoodRecord(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val category: String,
    val origin: FoodOrigin,
    val nutrition: NutritionBasis,
    val conversions: List<ServingConversion> = emptyList(),
    val defaultAmount: Double = 1.0,
    val defaultUnit: MeasureUnit = MeasureUnit.PIECE,
    val sourceId: String? = null,
    val sourceName: String? = null,
    val sourceRelease: String? = null,
    val preparation: String? = null,
    val sourceDescription: String? = null,
    val brand: String? = null,
    val barcode: String? = null,
    /** Archived personal foods stay available to historical diary snapshots. */
    val archived: Boolean = false,
    /** Local edits take precedence over later imports of the same source record. */
    val personalOverride: Boolean = false
) {
    fun allNames(): List<String> = listOf(name) + aliases

    /** Serving conversions worth showing: the nutrition basis unit itself is implicit. */
    fun householdServings(): List<ServingConversion> = conversions.filter {
        (it.unit != nutrition.unit || !it.label.isNullOrBlank()) && it.unit != MeasureUnit.UNKNOWN
    }
}

@Serializable
data class Preference(
    val foodId: String,
    val explicitCount: Int = 0,
    val lastReinforcedEpochMs: Long = 0L
) {
    fun score(nowMs: Long): Double {
        if (explicitCount == 0) return 0.0
        val ageDays = ((nowMs - lastReinforcedEpochMs).coerceAtLeast(0L) / 86_400_000.0)
        return ln1p(explicitCount.toDouble()) * exp(-ageDays / 30.0)
    }
}

@Serializable
data class MealEntry(
    val id: String,
    val foodId: String,
    val foodName: String,
    val enteredAmount: Double,
    val enteredUnit: MeasureUnit,
    val resolvedBaseAmount: Double,
    val nutritionSnapshot: NutritionValues,
    val conversionProvenance: String,
    val approximateConversion: Boolean,
    val savedAtEpochMs: Long,
    val sourcePhrase: String = foodName,
    val mealSlot: MealSlot = MealSlot.BREAKFAST,
    val dayKey: String = dayKeyForEpochMs(savedAtEpochMs),
    /** Immutable nutrition basis captured when this entry was saved. */
    val resolvedBaseUnit: MeasureUnit = MeasureUnit.UNKNOWN
)

fun dayKeyForEpochMs(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate().toString()

data class Calculation(
    val nutrition: NutritionValues,
    val resolvedBaseAmount: Double,
    val provenance: String,
    val approximate: Boolean,
    val supported: Boolean
)

sealed class ReplacementResult {
    data class Success(val entry: MealEntry) : ReplacementResult()
    data class Failure(val reason: String) : ReplacementResult()
}

data class ParsedSegment(
    val raw: String,
    val foodPhrase: String,
    val amount: Double?,
    val unit: MeasureUnit?,
    val quantityStart: Int?,
    val quantityEnd: Int?,
    val explicitModifiers: List<String> = emptyList(),
    /** True when a number word or article was read as the amount, which a food's own name may contain instead. */
    val wordQuantity: Boolean = false,
    /** True for a bare "a" or "an" with no unit: one when the food is counted by the piece, else the food's default. */
    val articleQuantity: Boolean = false,
)

data class RankedFood(
    val food: FoodRecord,
    val score: Double,
    val reasons: List<String>,
    /** Every matched token is a typo or a loose spelling: a choice to offer, not a resolution by itself. */
    val weakMatch: Boolean = false,
    /** Every identity token matches the food (exact, stem, transliteration, typo or prefix). */
    val fullMatch: Boolean = false,
    /** Every identity token matches a whole word (no prefix): the food stands on its own in a split. */
    val wholeMatch: Boolean = false,
    /** A typo whose edit is at the first letter, which never resolves on its own. */
    val firstLetterEdit: Boolean = false,
)

data class SavedMeal(
    val entries: List<MealEntry>,
    val totals: NutritionValues,
    val unresolvedSegments: List<String> = emptyList()
)
