package com.iron.breakfast.domain

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
