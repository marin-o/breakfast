package com.bosa.breakfast.domain

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
    GRAM("g"), MILLILITER("ml"), TABLESPOON("tbsp"), TEASPOON("tsp"), PIECE("piece"),
    /** A recipe's portion: only a recipe with portions has it (see `domain/Recipes.kt`). */
    PORTION("portion"),
    /** A US cup, 240 ml: a volume measure (see `domain/Servings.kt`). */
    CUP("cup"),
    /** One of a food's named servings ("slice", "can"): the name goes with it ([MealEntry.enteredUnitLabel]). */
    SERVING(""),
    UNKNOWN("")
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

/**
 * One macro summed over a day's entries: the sum of the known values, and how many entries knew it or did not.
 * Calories are always known; a macro may not be, so a sum that skips unknown parts says so.
 */
data class MacroTotal(val sum: Double, val known: Int, val unknown: Int) {
    /** Some entries have the macro and some do not: [sum] is only part of the day. */
    val partial: Boolean get() = known > 0 && unknown > 0

    /** No entry has the macro: there is no sum to show. An empty day is not unknown; it is zero. */
    val allUnknown: Boolean get() = known == 0 && unknown > 0
}

const val KCAL_PER_GRAM_PROTEIN = 4.0
const val KCAL_PER_GRAM_CARBS = 4.0
const val KCAL_PER_GRAM_FAT = 9.0

/** The day's protein, carbohydrates and fat, each as a [MacroTotal]. */
data class MacroTotals(val protein: MacroTotal, val carbohydrates: MacroTotal, val fat: MacroTotal) {
    /** At least one macro is only partly known. */
    val anyPartial: Boolean get() = listOf(protein, carbohydrates, fat).any { it.partial }
}

/** Folds one macro's values: null is unknown, never zero. */
internal fun macroTotalOf(values: List<Double?>): MacroTotal =
    MacroTotal(values.sumOf { it ?: 0.0 }, values.count { it != null }, values.count { it == null })

/** Folds the macros of [values] (one per entry) without turning an unknown macro into zero. */
internal fun macroTotalsOf(values: List<NutritionValues>): MacroTotals = MacroTotals(
    protein = macroTotalOf(values.map { it.protein }),
    carbohydrates = macroTotalOf(values.map { it.carbohydrates }),
    fat = macroTotalOf(values.map { it.fat }),
)

/**
 * The nutrition of [entries] together (a day, a meal, a save): their snapshots summed with [NutritionValues.plus], so
 * a macro no entry knows stays unknown. Daily, Meal and the engine read totals only through this.
 */
internal fun nutritionTotalOf(entries: List<MealEntry>): NutritionValues =
    entries.fold(NutritionValues.ZERO) { total, entry -> total + entry.nutritionSnapshot }

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
    /**
     * The serving's name, the key it is matched by ("bottle", "slice", "can"). A row without one is named by its unit
     * ([name]); a row in the basis unit without one is the basis itself, not a serving.
     */
    val label: String? = null
) {
    /** The name the serving is matched and shown by: its label, else "piece", "tbsp" or "tsp" ([defaultServingName]). */
    val name: String? get() = label?.trim()?.ifBlank { null } ?: defaultServingName(unit)
}

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
    /** Local edits take precedence over later imports of the same source record. */
    val personalOverride: Boolean = false,
    /** Grams in 100 ml ("100 ml = X g"), when known: with it both g and ml, and every unit of either, work. */
    val density: Double? = null,
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
    val resolvedBaseUnit: MeasureUnit = MeasureUnit.UNKNOWN,
    /**
     * What the amount was logged in, as shown: a serving's name for [MeasureUnit.SERVING] ("slice"), or a unit the
     * shorthand converted ("kg", "oz", "dl", with [enteredAmount] in g or ml). Null for a plain unit.
     */
    val enteredUnitLabel: String? = null,
)

/** The calendar date of [epochMs], for stored entries that lack a day. A new log takes its day from [dayKeyFor]. */
fun dayKeyForEpochMs(epochMs: Long): String = dayKeyFor(epochMs, ZoneId.systemDefault(), 0)

/**
 * The diary day [epochMs] belongs to when a day starts at [dayStartHour] (0 to 23) local time: before that hour it is
 * still the previous date. It reads the wall clock, so a daylight-saving day is still one day.
 */
fun dayKeyFor(epochMs: Long, zone: ZoneId, dayStartHour: Int): String {
    val local = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDateTime()
    val date = local.toLocalDate()
    return (if (local.hour < dayStartHour.coerceIn(0, 23)) date.minusDays(1) else date).toString()
}

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
    /** True when a number word or article was read as the amount, which a food's own name may contain instead. */
    val wordQuantity: Boolean = false,
    /** True for a bare "a" or "an" with no unit: one when the food is counted by the piece, else the food's default. */
    val articleQuantity: Boolean = false,
    /** [raw] as it reads: with a space before a quantity glued to a food word ("mleko200g" reads "mleko 200g"). */
    val spaced: String = raw,
    /** The unit word as typed ("slices", "kg", "pcs"): a food's serving of that name wins over [unit]. */
    val unitWord: String? = null,
    /** The label of a unit the amount was converted from ("kg", "oz", "dl"; [amount] is in g or ml); null otherwise. */
    val unitLabel: String? = null,
)

data class RankedFood(
    val food: FoodRecord,
    val score: Double,
    val reasons: List<String>,
    /** Every matched token is a typo: a choice to offer, not a resolution by itself. */
    val weakMatch: Boolean = false,
    /** Every identity token matches the food (exact, stem, typo or prefix), within one script. */
    val fullMatch: Boolean = false,
    /** Every identity token matches a whole word (no prefix): the food stands on its own in a split. */
    val wholeMatch: Boolean = false,
    /** A typo whose edit is at the first letter, which never resolves on its own. */
    val firstLetterEdit: Boolean = false,
    /** The typed phrase is the food's name or alias (or its singular): it ranks above every other match. */
    val exact: Boolean = false,
)

data class SavedMeal(
    val entries: List<MealEntry>,
    val totals: NutritionValues,
    val unresolvedSegments: List<String> = emptyList()
)
