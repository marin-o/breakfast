package com.bosa.breakfast.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

// Typed numbers and the comma that separates items: one rule for every field.

/**
 * The single decimal-input rule for number fields: ASCII digits and one separator. A comma is the decimal separator
 * as much as a point ("12,5" is "12.5"); any separator after the first is dropped.
 */
fun String.decimalInput(): String {
    val out = StringBuilder()
    var separator = false
    for (c in this) {
        when {
            c in '0'..'9' -> out.append(c)
            (c == '.' || c == ',') && !separator -> { out.append('.'); separator = true }
        }
    }
    return out.toString()
}

/** A number typed in a field, by the same rule as [decimalInput]: "12,5" is 12.5. Null when blank or not a number. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.')
    .takeIf { DecimalText.matches(it) }?.toDoubleOrNull()?.takeIf { it.isFinite() }

private val DecimalText = Regex("\\d+(?:\\.\\d*)?|\\.\\d+")

/** A comma that separates items: every comma except one between two digits, which is a decimal ("mleko 2,8%"). */
internal val ItemComma = Regex("(?<!\\d),|,(?!\\d)")

/** Aliases typed as one comma list, split by the [ItemComma] rule. */
fun splitAliases(text: String): List<String> = text.split(ItemComma).map(String::trim).filter(String::isNotBlank)

/** The one rule for an amount: a finite number above zero. */
fun isPositiveAmount(amount: Double): Boolean = amount.isFinite() && amount > 0.0

/** A typed amount the app can use ([parseDecimal]): null when blank, not a number, or not more than zero. */
fun positiveAmountOf(text: String): Double? = parseDecimal(text)?.takeIf(::isPositiveAmount)

/** What a field or a refused save says about an amount that is not positive. */
const val POSITIVE_AMOUNT_MESSAGE = "Enter a positive amount."

/**
 * A number as a field shows it: never in exponent form, whole values without ".0", at most three decimals, trimmed
 * (1/3 of a cup is 0.333). A value too small for three keeps its first digit (0.0005), so it never becomes 0. This is
 * display only: a field left as it was stands for the exact stored value ([fieldNumber], [fieldText]).
 */
fun editableNumber(value: Double?): String {
    if (value == null || !value.isFinite()) return ""
    val exact = BigDecimal.valueOf(value)
    val tiny = exact.signum() != 0 && exact.abs() < BigDecimal("0.001")
    val shown = if (tiny) exact.round(MathContext(1, RoundingMode.HALF_UP)) else exact.setScale(3, RoundingMode.HALF_UP)
    return shown.stripTrailingZeros().toPlainString()
}

/** The number a pre-filled field stands for: [exact] while it still reads as it was filled, else what was typed. */
fun fieldNumber(text: String, exact: Double?): Double? =
    if (exact != null && text == editableNumber(exact)) exact else parseDecimal(text)

/** [text] to hand on as typed: the whole of [exact] while the field still reads as it was filled. */
fun fieldText(text: String, exact: Double?): String =
    if (exact != null && exact.isFinite() && text == editableNumber(exact)) BigDecimal.valueOf(exact).stripTrailingZeros().toPlainString() else text
