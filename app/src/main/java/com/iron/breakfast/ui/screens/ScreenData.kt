package com.iron.breakfast.ui.screens

import com.iron.breakfast.domain.ProfileValues
import com.iron.breakfast.domain.SegmentResolution
import com.iron.breakfast.domain.KCAL_PER_GRAM_CARBS
import com.iron.breakfast.domain.KCAL_PER_GRAM_FAT
import com.iron.breakfast.domain.KCAL_PER_GRAM_PROTEIN
import com.iron.breakfast.domain.MacroTotal
import com.iron.breakfast.domain.MacroTotals
import com.iron.breakfast.domain.Recipe
import com.iron.breakfast.domain.yields
import com.iron.breakfast.domain.sourceLetter
import com.iron.breakfast.ui.clockLabel
import com.iron.breakfast.ui.components.MacroStat
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.Calculation
import com.iron.breakfast.domain.NutritionCalculator
import com.iron.breakfast.domain.ShorthandParser
import com.iron.breakfast.domain.TypedPart
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.domain.ShorthandLexicon
import com.iron.breakfast.domain.servingNameFor
import com.iron.breakfast.settings.DisplayDecimal
import com.iron.breakfast.settings.markDecimals
import com.iron.breakfast.ui.pretty
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

internal data class QuantityDisplay(
    val line: String,
    val approximateLabel: String? = null,
)

/** The quantity written plainly: `≈1 tbsp, 16 g` when approximate. */
internal fun QuantityDisplay.plainLine(): String = approximateLabel?.let { "≈$it, $line" } ?: line

/** Whole kcal, used the same way in every row, total and sheet. */
internal fun formatKcal(value: Double): String = round(value).toInt().toString()

/** "233 kcal". */
internal fun kcalLabel(value: Double): String = "${formatKcal(value)} kcal"

/** The kcal a Log choice shows. None when the choice cannot be counted, so it never reads "0 kcal". */
internal fun choiceKcalDetail(calculation: Calculation): String? =
    calculation.takeIf { it.supported }?.let { kcalLabel(it.nutrition.calories) }

internal fun quantityRateDisplay(
    entry: MealEntry,
    food: FoodRecord?,
): QuantityDisplay {
    // A counted piece or portion is the amount the user chose ("1 piece", "2 portions"). Its gram conversion and
    // kcal-per-gram rate are not a quantity to read, so they are not shown; the kcal sits in the row's own column.
    countedQuantity(entry.enteredAmount, entry.enteredUnit, entry.enteredUnitLabel)?.let { return QuantityDisplay(line = it) }
    val basisUnit = entry.resolvedBaseUnit.takeUnless { it == MeasureUnit.UNKNOWN }
        ?: provenanceBasisUnit(entry.conversionProvenance)
        ?: entry.enteredUnit
    val rate = if (entry.resolvedBaseAmount > 0.0) entry.nutritionSnapshot.calories / entry.resolvedBaseAmount else 0.0
    val entered = "${entry.enteredAmount.pretty()} ${entry.enteredUnit.labelFor(entry.enteredAmount)}".trim()
    val converted = basisUnit != entry.enteredUnit
    val approximate = entry.approximateConversion && converted
    val rawAmount = if (converted) entry.resolvedBaseAmount else entry.enteredAmount
    val printed = choosePrintedQuantity(rawAmount, rate, entry.nutritionSnapshot.calories)
    val base = "(${printed.amountText} ${basisUnit.displayLabel()})"
    val rateUnit = basisUnit.displayLabel()
    val multiply = "×"
    val line = when {
        converted && !approximate -> "$entered $base $multiply ${printed.rateText}"
        converted -> "$base $multiply ${printed.rateText}"
        else -> "${printed.amountText} ${entry.enteredUnit.displayLabel()} $multiply ${printed.rateText}".trim()
    }
    val mark = DisplayDecimal.mark
    return QuantityDisplay(
        line = markDecimals(withRateUnit(line, rateUnit, approximate), mark),
        approximateLabel = entered.takeIf { approximate },
    )
}

/**
 * A counted amount as it reads: "1 piece", "2 pieces", "1.5 portions", a serving by its name ("2 slices", [label]), or
 * an amount the shorthand converted, in the unit it was typed in ("1.5 kg", "8 oz"); null for a measured unit (g, ml,
 * tbsp, cup).
 */
internal fun countedQuantity(amount: Double, unit: MeasureUnit, label: String? = null): String? = when {
    unit == MeasureUnit.SERVING -> label?.let { "${amount.pretty()} ${servingNameFor(it, amount)}" }
    label != null -> ShorthandLexicon.factorOf(label)?.let { factor -> "${(amount / factor).pretty()} $label" }
    unit == MeasureUnit.PIECE || unit == MeasureUnit.PORTION -> "${amount.pretty()} ${unit.labelFor(amount)}"
    else -> null
}

/** A unit's label for [amount] of it, the one plural rule: "2 cups", "2 pieces"; an abbreviation stays ("2 tbsp"). */
internal fun MeasureUnit.labelFor(amount: Double): String = when (this) {
    MeasureUnit.CUP, MeasureUnit.PIECE, MeasureUnit.PORTION -> servingNameFor(displayLabel(), amount)
    else -> displayLabel()
}

/**
 * The one amount line of a row: counted pieces and portions read as the user typed them (`2 pieces`, `1 portion`), never
 * with ≈; other units show ≈ only when the amount was converted (1 tbsp as grams).
 */
internal fun previewQuantityLine(amount: Double, unit: MeasureUnit, approximate: Boolean = false, label: String? = null): String =
    countedQuantity(amount, unit, label) ?: "${if (approximate) "≈" else ""}${amount.pretty()} ${unit.labelFor(amount)}"

/** A logged entry's amount in a meal's rows: as it was typed ("8 g", "2 pieces", "≈1 tbsp"), the kcal in the row's own column. */
internal fun entryQuantityLine(entry: MealEntry): String = markDecimals(
    previewQuantityLine(entry.enteredAmount, entry.enteredUnit, entry.approximateConversion, entry.enteredUnitLabel),
    DisplayDecimal.mark,
)

/** Why a part that names a food cannot log: "No value for tbsp", when the food has no number for the unit or serving typed; else its amount. */
internal fun noValueLine(part: SegmentResolution): String =
    if (part.noValue) "No value for ${part.label ?: part.unit.labelFor(part.amount)}" else "Amount must be above 0"

private const val MAX_RATE_UNIT_LINE = 28

/** Plain rows name the rate unit ("kcal/g") when the line still fits on one row; approximate rows never do. */
internal fun withRateUnit(line: String, rateUnit: String, approximate: Boolean): String {
    if (approximate || rateUnit.isBlank()) return line
    val withUnit = "$line kcal/$rateUnit"
    return if (withUnit.length <= MAX_RATE_UNIT_LINE) withUnit else line
}

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
    return clockLabel(savedAt, zoneId)
}


/** "Per 100 g", "Per 250 ml": always the food's real nutrition basis. */
internal fun basisLabel(amount: Double, unit: MeasureUnit): String = "Per ${amount.pretty()} ${unit.label}"

/** The unresolved quote of the resolve queue: its kept quantity ("2 tbsp", when typed) and the food words. */
internal data class UnknownDisplay(val quantity: String?, val name: String)

/** [typed] is [raw] as the Log read it; the resolver passes the planner's reading, so a glued amount shows apart. */
internal fun unknownDisplay(
    raw: String,
    typed: TypedPart = ShorthandParser().parse(raw).let { TypedPart(it.foodPhrase, it.amount, it.unit) },
): UnknownDisplay {
    val quantity = typed.amount?.let { amount -> listOfNotNull(amount.pretty(), typed.unit?.labelFor(amount)?.takeIf { it.isNotBlank() }).joinToString(" ") }
    return UnknownDisplay(quantity, typed.foodPhrase.trim().ifBlank { raw.trim() })
}

/** One local match for the kept quantity: whole kcal, the ≈ chip text when the conversion is a guess, and the plain detail. */
internal data class MatchDisplay(val kcal: String, val approximateLabel: String?, val detail: String)

internal fun matchDisplay(food: FoodRecord, raw: String, calculator: NutritionCalculator = NutritionCalculator()): MatchDisplay {
    val parsed = ShorthandParser().parse(raw)
    val amount = parsed.amount ?: food.defaultAmount
    val unit = parsed.unit ?: food.defaultUnit
    val calculation = calculator.calculate(food, amount, unit)
    val entered = previewQuantityLine(amount, unit).trim()
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

/** "30 g" for the red chip beside a household-measure amount; null for exact amounts. */
internal fun productApproxLabel(calculation: Calculation?, enteredUnit: MeasureUnit?, basisUnit: MeasureUnit): String? =
    calculation?.takeIf { it.supported && it.approximate && enteredUnit != null && enteredUnit != basisUnit }
        ?.let { "${it.resolvedBaseAmount.pretty()} ${basisUnit.label}" }

/**
 * A macro's value and unit as shown: "—" with none known, "12" and "g ?" with some unknown, else "12" and "g". With
 * nothing counted it is 0 on a day ([noneIsUnknown] false), and unknown on what has no ingredients to say.
 */
internal fun macroFact(total: MacroTotal, noneIsUnknown: Boolean = false): Pair<String, String> = when {
    total.allUnknown || (noneIsUnknown && total.known == 0) -> "—" to "g"
    total.partial -> total.sum.pretty() to "g ?"
    else -> total.sum.pretty() to "g"
}

/**
 * The three macro columns under a kcal figure ([macros] of [kcal]), each with its known share of the kcal: a day's total
 * and one entry's. A macro nothing has reads "—"; a sum that leaves some out reads "12 g ?".
 */
internal fun macroStats(kcal: Double, macros: MacroTotals, targets: ProfileValues = ProfileValues()): List<MacroStat> {
    fun stat(label: String, total: MacroTotal, kcalPerGram: Double, target: Double?): MacroStat {
        val (value, unit) = macroFact(total)
        // With a target the bar fills toward it, full at most: going over is neither coloured nor scolded.
        if (target != null) return MacroStat(label, value, unit, (total.sum / target).toFloat().coerceIn(0f, 1f), "/ ${target.pretty()} g")
        return MacroStat(label, value, unit, if (kcal > 0.0) (total.sum * kcalPerGram / kcal).toFloat() else 0f)
    }
    return listOf(
        stat("Protein", macros.protein, KCAL_PER_GRAM_PROTEIN, targets.proteinTarget),
        stat("Carbs", macros.carbohydrates, KCAL_PER_GRAM_CARBS, targets.carbsTarget),
        stat("Fat", macros.fat, KCAL_PER_GRAM_FAT, targets.fatTarget),
    )
}

/**
 * The one line under the macros when any of them shows "?". Never drawn when the macros are hidden ("Macros on today"
 * Hide) or the note is ("Missing values note" Hide).
 */
internal fun dailyMacroNote(macros: MacroTotals, showMacros: Boolean = true, showNote: Boolean = true): String? =
    if (showMacros && showNote && macros.anyPartial) "? some foods have no value" else null

/** What a recipe makes: "4 portions", "1600 g", "1500 ml", or what it has of them, "4 portions, 1600 g, 1500 ml". */
internal fun yieldLine(recipe: Recipe): String = recipe.yields().joinToString(", ") { (unit, amount) -> previewQuantityLine(amount, unit) }
