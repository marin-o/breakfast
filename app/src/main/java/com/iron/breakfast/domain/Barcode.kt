package com.iron.breakfast.domain

private val BarcodeLengths = setOf(8, 12, 13, 14)

/** Keeps ASCII 0-9 only; Arabic-Indic and fullwidth digits are dropped. [allowDecimalPoint] also keeps '.'. */
fun String.asciiDigits(allowDecimalPoint: Boolean = false): String =
    filter { it in '0'..'9' || (allowDecimalPoint && it == '.') }

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
