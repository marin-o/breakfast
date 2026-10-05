package com.iron.breakfast.domain

import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.ln1p

@Serializable
enum class FoodOrigin { GENERIC, CUSTOM }

@Serializable
enum class MeasureUnit(val label: String) {
    GRAM("g"), MILLILITER("ml"), TABLESPOON("tbsp"), TEASPOON("tsp"), PIECE("piece"), UNKNOWN("")
}

@Serializable
data class NutritionValues(
    val calories: Double,
    val protein: Double,
    val carbohydrates: Double,
    val fat: Double
) {
    operator fun times(factor: Double) = NutritionValues(
        calories * factor,
        protein * factor,
        carbohydrates * factor,
        fat * factor
    )

    operator fun plus(other: NutritionValues) = NutritionValues(
        calories + other.calories,
        protein + other.protein,
        carbohydrates + other.carbohydrates,
        fat + other.fat
    )

    companion object {
        val ZERO = NutritionValues(0.0, 0.0, 0.0, 0.0)
    }
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
    val approximate: Boolean = false
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
    val sourceDescription: String? = null
) {
    fun allNames(): List<String> = listOf(name) + aliases
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
    val sourcePhrase: String = foodName
)

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
    val explicitModifiers: List<String> = emptyList()
)

data class RankedFood(
    val food: FoodRecord,
    val score: Double,
    val reasons: List<String>
)

data class SavedMeal(
    val entries: List<MealEntry>,
    val totals: NutritionValues,
    val unresolvedSegments: List<String> = emptyList()
)
