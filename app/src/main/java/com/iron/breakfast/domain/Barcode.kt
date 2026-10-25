package com.iron.breakfast.domain

private val BarcodeLengths = setOf(8, 12, 13, 14)

/** Keeps ASCII 0-9 only; Arabic-Indic and fullwidth digits are dropped. */
fun String.asciiDigits(): String = filter { it in '0'..'9' }

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

/**
 * The single barcode rule: trimmed, ASCII digits only, 8, 12, 13 or 14 long
 * (EAN-8, UPC-A, EAN-13, GTIN-14). Check digits are not validated.
 * Returns the normalised code, or null when [raw] is not a product barcode.
 */
fun normalizeBarcode(raw: String): String? {
    val trimmed = raw.trim()
    return trimmed.takeIf { it.length in BarcodeLengths && it.all { c -> c in '0'..'9' } }
}

/**
 * Validates a barcode typed or scanned into a food form. Blank means "no barcode" and is valid.
 * A code held by any other food, archived ones included, is rejected by naming that food.
 */
fun validateBarcodeInput(value: String, foods: List<FoodRecord>, editingId: String? = null): String? {
    if (value.isBlank()) return null
    val barcode = normalizeBarcode(value) ?: return "Enter an 8, 12, 13 or 14 digit barcode or clear the field."
    val holder = foods.firstOrNull { it.id != editingId && it.barcode?.trim() == barcode } ?: return null
    return "That barcode already belongs to ${holder.name}."
}
