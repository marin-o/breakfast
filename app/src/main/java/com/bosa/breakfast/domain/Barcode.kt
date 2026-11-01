package com.bosa.breakfast.domain

private val BarcodeLengths = setOf(8, 12, 13, 14)

/** Keeps ASCII 0-9 only; Arabic-Indic and fullwidth digits are dropped. */
fun String.asciiDigits(): String = filter { it in '0'..'9' }

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
 * The one form product codes are compared and stored in. A 12-digit UPC-A and its 13-digit EAN (a leading 0) are one
 * code, and so is a GTIN-14 that only adds a leading 0: UPC-A becomes "0" + code, a GTIN-14 starting with 0 drops it.
 * Other EAN-13, EAN-8 and other GTIN-14 codes stay as they are. Null when [raw] is not a product barcode.
 */
fun canonicalBarcode(raw: String): String? {
    val code = normalizeBarcode(raw) ?: return null
    return when {
        code.length == 12 -> "0$code"
        code.length == 14 && code[0] == '0' -> code.substring(1)
        else -> code
    }
}

/**
 * True when [a] and [b] are the same product code in any of their forms; false when either is not a barcode.
 * An 8-digit code that is a valid UPC-E (number system 0 or 1, matching check digit) is also its UPC-A, because
 * scans before UPC-E expansion stored the raw 8 digits; an EAN-8 that is not a valid UPC-E is only itself.
 */
fun sameBarcode(a: String?, b: String?): Boolean {
    val left = a?.let(::canonicalForms).orEmpty()
    return left.isNotEmpty() && b?.let(::canonicalForms).orEmpty().any { it in left }
}

/**
 * Every spelling a stored row may hold for the code [raw]: for "036000291452", that, "0036000291452",
 * "00036000291452" and, when the UPC-A compresses, its 8-digit UPC-E. For a valid 8-digit UPC-E, its UPC-A
 * spellings too. [raw]'s own form comes first. Empty when [raw] is not a product barcode.
 */
fun barcodeForms(raw: String): List<String> {
    val own = normalizeBarcode(raw) ?: return emptyList()
    return (listOf(own) + canonicalForms(own).flatMap(::spellings)).distinct()
}

/** The canonical codes [raw] stands for: its own, and for a valid 8-digit UPC-E also its UPC-A's. */
private fun canonicalForms(raw: String): List<String> {
    val own = canonicalBarcode(raw) ?: return emptyList()
    val upcA = own.takeIf { it.length == 8 }?.let(::expandUpcE)?.let(::canonicalBarcode)
    return listOfNotNull(own, upcA).distinct()
}

/** The stored spellings of one canonical code. */
private fun spellings(canonical: String): List<String> = when (canonical.length) {
    13 -> {
        val upcA = canonical.substring(1).takeIf { canonical[0] == '0' }
        listOfNotNull(canonical, upcA, "0$canonical", upcA?.let(::compressUpcE))
    }
    else -> listOf(canonical)
}

/**
 * The 8-digit UPC-E (number system, 6 digits, check digit) for a 12-digit UPC-A, by the GS1 zero-suppression rules
 * in their order; null when [upcA] is not number system 0 or 1 or does not compress. [expandUpcE] is its inverse.
 */
fun compressUpcE(upcA: String): String? {
    if (upcA.length != 12 || !upcA.all { it in '0'..'9' }) return null
    val system = upcA[0]
    if (system != '0' && system != '1') return null
    val m = upcA.substring(1, 6)
    val p = upcA.substring(6, 11)
    val body = when {
        m[2] in '0'..'2' && m.endsWith("00") && p.startsWith("00") -> "${m[0]}${m[1]}${p[2]}${p[3]}${p[4]}${m[2]}"
        m.endsWith("00") && p.startsWith("000") -> "${m[0]}${m[1]}${m[2]}${p[3]}${p[4]}3"
        m.endsWith("0") && p.startsWith("0000") -> "${m[0]}${m[1]}${m[2]}${m[3]}${p[4]}4"
        p.startsWith("0000") && p[4] in '5'..'9' -> "$m${p[4]}"
        else -> return null
    }
    val code = "$system$body${upcA[11]}"
    return code.takeIf { expandUpcE(it) == upcA }
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
 * A code held by any other food is rejected by naming that food.
 */
fun validateBarcodeInput(value: String, foods: List<FoodRecord>, editingId: String? = null): String? {
    if (value.isBlank()) return null
    val barcode = normalizeBarcode(value) ?: return "Enter an 8, 12, 13 or 14 digit barcode or clear the field."
    val holder = foods.firstOrNull { it.id != editingId && sameBarcode(it.barcode, barcode) } ?: return null
    return "That barcode already belongs to ${holder.name}."
}
