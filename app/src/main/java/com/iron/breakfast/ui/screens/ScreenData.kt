package com.iron.breakfast.ui.screens

import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.Calculation
import com.iron.breakfast.domain.NutritionCalculator
import com.iron.breakfast.domain.NutritionInput
import com.iron.breakfast.domain.ParsedAmountUnit
import com.iron.breakfast.domain.ResolvedNutrition
import com.iron.breakfast.domain.ShorthandParser
import com.iron.breakfast.domain.TypedPart
import com.iron.breakfast.domain.parseStrictAmountUnit
import com.iron.breakfast.domain.parseDecimal
import com.iron.breakfast.domain.resolveNutrition
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.settings.DisplayDecimal
import com.iron.breakfast.settings.markDecimals
import com.iron.breakfast.ui.pretty
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

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
private fun isOpenFoodFactsSource(food: FoodRecord): Boolean =
    food.sourceName?.contains("open food facts", ignoreCase = true) == true ||
        food.sourceId?.startsWith("off", ignoreCase = true) == true

internal data class QuantityDisplay(
    val line: String,
    val approximateLabel: String? = null,
)

/** The quantity written plainly for a row: `≈1 tbsp, 16 g` when approximate. */
internal fun QuantityDisplay.plainLine(): String = approximateLabel?.let { "≈$it, $line" } ?: line

/** Whole kcal, used the same way in every row, total and sheet. */
internal fun formatKcal(value: Double): String = round(value).toInt().toString()

/** The kcal a Log choice shows. None when the choice cannot be counted, so it never reads "0 kcal". */
internal fun choiceKcalDetail(calculation: Calculation): String? =
    calculation.takeIf { it.supported }?.let { "${formatKcal(it.nutrition.calories)} kcal" }

internal fun quantityRateDisplay(
    entry: MealEntry,
    food: FoodRecord?,
    includeRateUnit: Boolean = true,
    asciiMultiply: Boolean = false,
): QuantityDisplay {
    // A counted piece is the amount the user chose ("1 piece"). Its gram conversion and kcal-per-gram rate are not a
    // quantity to read, so they are not shown; the kcal sits in the row's own column.
    if (entry.enteredUnit == MeasureUnit.PIECE) {
        val count = entry.enteredAmount.pretty()
        return QuantityDisplay(line = "$count ${if (entry.enteredAmount == 1.0) "piece" else "pieces"}")
    }
    val basisUnit = entry.resolvedBaseUnit.takeUnless { it == MeasureUnit.UNKNOWN }
        ?: provenanceBasisUnit(entry.conversionProvenance)
        ?: entry.enteredUnit
    val rate = if (entry.resolvedBaseAmount > 0.0) entry.nutritionSnapshot.calories / entry.resolvedBaseAmount else 0.0
    val entered = "${entry.enteredAmount.pretty()} ${entry.enteredUnit.displayLabel()}".trim()
    val converted = basisUnit != entry.enteredUnit
    val approximate = entry.approximateConversion && converted
    val rawAmount = if (converted) entry.resolvedBaseAmount else entry.enteredAmount
    val printed = choosePrintedQuantity(rawAmount, rate, entry.nutritionSnapshot.calories)
    val base = "(${printed.amountText} ${basisUnit.displayLabel()})"
    val rateUnit = basisUnit.displayLabel()
    val multiply = if (asciiMultiply) "x" else "×"
    val line = when {
        converted && !approximate -> "$entered $base $multiply ${printed.rateText}"
        converted -> "$base $multiply ${printed.rateText}"
        else -> "${printed.amountText} ${entry.enteredUnit.displayLabel()} $multiply ${printed.rateText}".trim()
    }
    val mark = DisplayDecimal.mark
    return QuantityDisplay(
        line = markDecimals(if (includeRateUnit) withRateUnit(line, rateUnit, approximate) else line, mark),
        approximateLabel = entered.takeIf { approximate },
    )
}

private const val MAX_RATE_UNIT_LINE = 28

/** Plain rows name the rate unit ("kcal/g") when the line still fits on one row; approximate rows never do. */
internal fun withRateUnit(line: String, rateUnit: String, approximate: Boolean): String {
    if (approximate || rateUnit.isBlank()) return line
    val withUnit = "$line kcal/$rateUnit"
    return if (withUnit.length <= MAX_RATE_UNIT_LINE) withUnit else line
}

/** A number for an editable field: whole values without ".0", others as the shortest plain decimal. */
internal fun editableNumber(value: Double?): String {
    if (value == null || !value.isFinite()) return ""
    return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}

/** "Wed 14 October", the date part of the entry detail line. */
internal fun entryDateLabel(dayKey: String): String =
    runCatching { java.time.LocalDate.parse(dayKey) }.getOrNull()
        ?.format(DateTimeFormatter.ofPattern("EEE d MMMM", Locale.ENGLISH)) ?: dayKey

/** The last moment a date picker may offer: the end of [todayKey] in [zone], so no day after today can be picked. */
internal fun datePickerMaxMs(todayKey: String, zone: ZoneId): Long =
    java.time.LocalDate.parse(todayKey).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

/** Source label beside the O/B/P box on the entry detail. */
internal fun entrySourceLabel(food: FoodRecord?): String = sourceLabel(sourceLetter(food))

/** Spoken and written name for a source letter. */
internal fun sourceLabel(letter: String): String = when (letter) {
    "B" -> "Bundled"
    "P" -> "Personal"
    "O" -> "Open Food Facts"
    else -> "Unknown source"
}

private fun provenanceBasisUnit(provenance: String): MeasureUnit? {
    val match = Regex("(?:applied to per-[0-9.]+|on [0-9.]+)\\s*(g|ml)\\b", RegexOption.IGNORE_CASE)
        .find(provenance) ?: return null
    return when (match.groupValues[1].lowercase(Locale.ROOT)) {
        "g" -> MeasureUnit.GRAM
        "ml" -> MeasureUnit.MILLILITER
        else -> null
    }
}

private data class PrintedQuantity(val amountText: String, val rateText: String)

private fun choosePrintedQuantity(amount: Double, rate: Double, calories: Double): PrintedQuantity {
    if (!amount.isFinite() || amount <= 0.0 || !rate.isFinite()) return PrintedQuantity("0", "0.00")
    val targetKcal = round(calories)
    // Keep the normal one-decimal treatment, adding precision only when
    // the actual snapshot-derived rate would otherwise print the wrong kcal.
    for (amountDigits in 0..4) {
        val amountText = formatAmount(amount, amountDigits)
        val displayedAmount = amountText.toDoubleOrNull() ?: amount
        for (rateDigits in 2..4) {
            val rateText = String.format(Locale.ROOT, "%.${rateDigits}f", rate)
            val displayedRate = rateText.toDoubleOrNull() ?: rate
            if (round(displayedAmount * displayedRate) == targetKcal) {
                return PrintedQuantity(amountText, rateText)
            }
        }
    }
    return PrintedQuantity(formatAmount(amount, 4), String.format(Locale.ROOT, "%.4f", rate))
}

private fun formatAmount(value: Double, digits: Int): String {
    if (abs(value - value.toInt()) < 0.0001) return value.toInt().toString()
    val raw = String.format(Locale.ROOT, "%.${digits.coerceAtLeast(1)}f", value)
    return raw.trimEnd('0').trimEnd('.')
}

internal fun earliestMealTime(entries: List<MealEntry>, zoneId: ZoneId = ZoneId.systemDefault()): String? {
    val savedAt = entries.minOfOrNull { it.savedAtEpochMs } ?: return null
    return DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
        .withZone(zoneId).format(Instant.ofEpochMilli(savedAt))
}


/** "Per 100 g", "Per 250 ml": always the food's real nutrition basis. */
internal fun basisLabel(amount: Double, unit: MeasureUnit): String = "Per ${amount.pretty()} ${unit.label}"

/**
 * Open Food Facts foods keep their source code in `sourceId`; once the local barcode no longer
 * matches it, edited numbers must not be submitted under a different product code.
 */
internal fun contributionAvailable(food: FoodRecord): Boolean {
    val barcode = com.iron.breakfast.domain.normalizeBarcode(food.barcode ?: return false) ?: return false
    // A food that cannot become a draft (implausible per 100 g, no g or ml basis) offers no action that would only refuse.
    if (com.iron.breakfast.data.OffContributionDraft.fromFood(food) == null) return false
    return !isOpenFoodFactsSource(food) || com.iron.breakfast.domain.sameBarcode(food.sourceId, barcode)
}

internal fun foodCountLabel(count: Int): String = if (count == 1) "1 food" else "$count foods"

/** "/100 g" or "/100 ml": the caption under an Open Food Facts kcal figure, by the product's own basis unit. */
internal fun basisCaption(amount: Double, unit: MeasureUnit): String = "/${amount.pretty()} ${unit.label}"

/** The unresolved quote of the resolve queue: its kept quantity ("2 tbsp", when typed) and the food words. */
internal data class UnknownDisplay(val quantity: String?, val name: String)

/** [typed] is [raw] as the Log read it; the resolver passes the planner's reading, so a glued amount shows apart. */
internal fun unknownDisplay(
    raw: String,
    typed: TypedPart = ShorthandParser().parse(raw).let { TypedPart(it.foodPhrase, it.amount, it.unit) },
): UnknownDisplay {
    val quantity = typed.amount?.let { amount -> listOfNotNull(amount.pretty(), typed.unit?.displayLabel()?.takeIf { it.isNotBlank() }).joinToString(" ") }
    return UnknownDisplay(quantity, typed.foodPhrase.trim().ifBlank { raw.trim() })
}

/** One local match for the kept quantity: whole kcal, the ≈ chip text when the conversion is a guess, and the plain detail. */
internal data class MatchDisplay(val kcal: String, val approximateLabel: String?, val detail: String)

internal fun matchDisplay(food: FoodRecord, raw: String, calculator: NutritionCalculator = NutritionCalculator()): MatchDisplay {
    val parsed = ShorthandParser().parse(raw)
    val amount = parsed.amount ?: food.defaultAmount
    val unit = parsed.unit ?: food.defaultUnit
    val calculation = calculator.calculate(food, amount, unit)
    val entered = "${amount.pretty()} ${unit.displayLabel()}".trim()
    if (!calculation.supported) return MatchDisplay("—", null, entered)
    val converted = unit != food.nutrition.unit
    val base = "(${calculation.resolvedBaseAmount.pretty()} ${food.nutrition.unit.label})"
    val approximate = calculation.approximate && converted
    return MatchDisplay(
        kcal = formatKcal(calculation.nutrition.calories),
        approximateLabel = entered.takeIf { approximate },
        detail = when {
            approximate -> base
            converted -> "$entered $base"
            else -> entered
        },
    )
}

/** Review amount field: "2 tbsp", or a bare number meaning the product's own basis unit. Null while it cannot be logged. */
internal fun parseProductAmount(text: String, basisUnit: MeasureUnit): ParsedAmountUnit? {
    val bare = parseDecimal(text)
    if (bare != null) return if (bare > 0.0) ParsedAmountUnit(bare, basisUnit) else null
    return parseStrictAmountUnit(text)
}

/** "30 g" for the red chip beside a household-measure amount; null for exact amounts. */
internal fun productApproxLabel(calculation: Calculation?, enteredUnit: MeasureUnit?, basisUnit: MeasureUnit): String? =
    calculation?.takeIf { it.supported && it.approximate && enteredUnit != null && enteredUnit != basisUnit }
        ?.let { "${it.resolvedBaseAmount.pretty()} ${basisUnit.label}" }

/** What the create form resolves to and whether it can be saved. Calories are required. */
internal data class CreateFoodState(
    val nutrition: ResolvedNutrition,
    val caloriesMissing: Boolean,
    /** The red ring and "Required: kcal" tag: shown once the user has started on nutrition. */
    val showRequired: Boolean,
    val canSave: Boolean,
)

internal fun createFoodState(input: NutritionInput, name: String, barcodeError: String?): CreateFoodState {
    val nutrition = resolveNutrition(input)
    val missing = nutrition.calories == null
    val touched = listOf(input.shorthand, input.calories, input.protein, input.carbs, input.fat).any { it.isNotBlank() }
    return CreateFoodState(nutrition, missing, showRequired = missing && touched, canSave = !missing && name.isNotBlank() && barcodeError == null)
}


