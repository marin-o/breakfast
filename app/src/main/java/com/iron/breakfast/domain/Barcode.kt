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
 * The scanner's guard: what a decoded symbol becomes before any lookup, or null when it is not a product barcode
 * (a QR code's text, a URL), so the frame is ignored and scanning goes on. A UPC-E symbol ([upcE]) is expanded to
 * its 12-digit UPC-A first, so it is not read as an EAN-8.
 */
fun scannedProductBarcode(raw: String, upcE: Boolean = false): String? {
    val trimmed = raw.trim()
    return if (upcE) expandUpcE(trimmed) ?: normalizeBarcode(trimmed) else normalizeBarcode(trimmed)
}

/**
 * The 12-digit UPC-A that a UPC-E code stands for. [code] is the 6 compressed digits, the number system (0 or 1)
 * and those 6, or all 8 with the check digit, which must match. Null when [code] is not UPC-E.
 */
fun expandUpcE(code: String): String? {
    if (!code.all { it in '0'..'9' }) return null
    val (system, body, check) = when (code.length) {
        6 -> Triple('0', code, null)
        7 -> Triple(code[0], code.substring(1), null)
        8 -> Triple(code[0], code.substring(1, 7), code[7])
        else -> return null
    }
    if (system != '0' && system != '1') return null
    val d = body
    val digits11 = system + when (d[5]) {
        '0', '1', '2' -> "${d[0]}${d[1]}${d[5]}0000${d[2]}${d[3]}${d[4]}"
        '3' -> "${d[0]}${d[1]}${d[2]}00000${d[3]}${d[4]}"
        '4' -> "${d[0]}${d[1]}${d[2]}${d[3]}00000${d[4]}"
        else -> "${d[0]}${d[1]}${d[2]}${d[3]}${d[4]}0000${d[5]}"
    }
    val sum = digits11.mapIndexed { index, c -> (c - '0') * if (index % 2 == 0) 3 else 1 }.sum()
    val computed = '0' + (10 - sum % 10) % 10
    if (check != null && check != computed) return null
    return digits11 + computed
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
