package com.iron.breakfast.ui.screens

import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.ShorthandParser
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.pretty
import com.iron.breakfast.ui.components.ReceiptTokenKind
import com.iron.breakfast.ui.components.ReceiptTokenRange
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/** B bundled (any generic food), P personal, O Open Food Facts. Only a missing food is "?". */
internal fun sourceLetter(food: FoodRecord?): String = when {
    food == null -> "?"
    food.origin == FoodOrigin.CUSTOM -> "P"
    food.sourceName?.contains("open food facts", ignoreCase = true) == true -> "O"
    food.sourceId?.startsWith("off", ignoreCase = true) == true -> "O"
    else -> "B"
}

internal data class ReceiptQuantityDisplay(
    val line: String,
    val approximateLabel: String? = null,
)

/** Printed receipt figures use whole kcal consistently across rows and slips. */
internal fun receiptKcal(value: Double): String = round(value).toInt().toString()

internal fun quantityRateDisplay(entry: MealEntry, food: FoodRecord?): ReceiptQuantityDisplay {
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
    return ReceiptQuantityDisplay(
        line = when {
            converted && !approximate -> "$entered $base × ${printed.rateText}"
            converted -> "$base × ${printed.rateText}"
            else -> "${printed.amountText} ${entry.enteredUnit.displayLabel()} × ${printed.rateText}".trim()
        },
        approximateLabel = entered.takeIf { approximate },
    )
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
    // Keep the normal one-decimal receipt treatment, adding precision only when
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

internal fun dayTitle(dayKey: String, zoneId: ZoneId = ZoneId.systemDefault()): Pair<String, String> {
    val date = runCatching { java.time.LocalDate.parse(dayKey) }.getOrNull() ?: return dayKey to dayKey
    val day = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
    return day to date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH))
}

internal fun tokenRangesForInput(value: String): List<ReceiptTokenRange> {
    val parser = ShorthandParser()
    val mealWords = setOf("breakfast", "lunch", "dinner", "snacks")
    var cursor = 0
    return value.splitToSequence(',').flatMap { original ->
        val leading = original.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
        val raw = original.trim()
        val offset = cursor + leading
        cursor += original.length + 1
        val parsed = parser.parse(raw)
        sequence {
            parsed.quantityStart?.let { start ->
                val end = parsed.quantityEnd ?: start
                yield(ReceiptTokenRange((offset + start)..(offset + end - 1), ReceiptTokenKind.Quantity))
            }
            val isMeal = parsed.amount == null && parsed.foodPhrase.trim().lowercase(Locale.ROOT) in mealWords
            if (isMeal) {
                yield(ReceiptTokenRange(offset..(offset + raw.length - 1), ReceiptTokenKind.Meal))
            } else {
                val foodStart = parsed.foodPhrase.takeIf { it.isNotBlank() }?.let { raw.indexOf(it) }
                if (foodStart != null && foodStart >= 0) {
                    yield(ReceiptTokenRange((offset + foodStart)..(offset + foodStart + parsed.foodPhrase.length - 1), ReceiptTokenKind.Food))
                }
            }
        }
    }.toList()
}

/** "Per 100 g", "Per 250 ml": always the food's real nutrition basis. */
internal fun basisLabel(amount: Double, unit: MeasureUnit): String = "Per ${amount.pretty()} ${unit.label}"

/**
 * Open Food Facts foods keep their source code in `sourceId`; once the local barcode no longer
 * matches it, edited numbers must not be submitted under a different product code.
 */
internal fun contributionAvailable(food: FoodRecord): Boolean {
    val barcode = food.barcode?.trim().orEmpty()
    if (barcode.isEmpty()) return false
    return sourceLetter(food) != "O" || food.sourceId?.trim() == barcode
}

internal fun foodCountLabel(count: Int): String = if (count == 1) "1 food" else "$count foods"
