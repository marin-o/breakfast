package com.iron.breakfast.domain

import java.util.Locale

class ShorthandParser {
    private val halfPattern = Regex(
        "(?i)\\bhalf\\s+a?\\s*(g|grams?|ml|milliliters?|tbsp|tablespoons?|tsp|teaspoons?|pieces?)?\\b"
    )
    private val numberPattern = Regex(
        "(?i)\\b(\\d+\\s+\\d+/\\d+|\\d+/\\d+|\\d+(?:[.,]\\d+)?)(?![.,\\d]|\\s*%)\\s*" +
            "(g|grams?|ml|milliliters?|tbsp|tablespoons?|tsp|teaspoons?|pieces?)?\\b"
    )

    fun split(input: String): List<String> = input.split(',').map(String::trim).filter(String::isNotEmpty)

    fun parse(input: String): ParsedSegment {
        val raw = input.trim()
        if (raw.isEmpty()) return ParsedSegment(raw, "", null, null, null, null)

        val halfMatch = halfPattern.find(raw)
        val numericMatch = numberPattern.findAll(raw).firstOrNull { candidate ->
            val amountText = candidate.groupValues[1]
            val unitText = candidate.groupValues.getOrNull(2).orEmpty()
            !isCompositionRatio(candidate, raw) || unitText.isNotBlank()
        }
        val match = halfMatch ?: numericMatch
        if (match == null) {
            return ParsedSegment(raw, raw, null, null, null, null)
        }

        val amountText = if (halfMatch != null) "half" else match.groupValues[1]
        val unitText = if (halfMatch != null) {
            match.groupValues.getOrNull(1).orEmpty()
        } else {
            match.groupValues.getOrNull(2).orEmpty()
        }
        val amount = parseAmount(amountText)
        val unit = unitText.toMeasureUnit()
        val foodPhrase = (raw.removeRange(match.range)).replace(Regex("\\s+"), " ").trim()
        val modifiers = Regex("(?i)(?:\\b\\d+(?:[.,]\\d+)?%|\\b(?:small|large|oat|smooth|crunchy|raw|cooked|roasted|boneless|skinless|drained|canned)\\b)")
            .findAll(foodPhrase)
            .map { it.value.lowercase(Locale.ROOT) }
            .toList()
        return ParsedSegment(raw, foodPhrase, amount, unit, match.range.first, match.range.last + 1, modifiers)
    }

    private fun parseAmount(text: String): Double {
        val normalized = text.trim().lowercase(Locale.ROOT).replace(',', '.')
        if (normalized == "half") return 0.5
        val pieces = normalized.split(Regex("\\s+"))
        if (pieces.size == 2 && '/' in pieces[1]) {
            return (pieces[0].toDoubleOrNull() ?: 0.0) + fraction(pieces[1])
        }
        if ('/' in normalized) return fraction(normalized)
        return normalized.toDoubleOrNull() ?: 0.0
    }

    private fun fraction(text: String): Double {
        val parts = text.split('/')
        if (parts.size != 2) return 0.0
        val numerator = parts[0].toDoubleOrNull() ?: return 0.0
        val denominator = parts[1].toDoubleOrNull() ?: return 0.0
        return if (denominator == 0.0) 0.0 else numerator / denominator
    }

    private fun isCompositionRatio(match: MatchResult, raw: String): Boolean {
        val amountText = match.groupValues[1]
        if ('/' !in amountText || match.groupValues.getOrNull(2).orEmpty().isNotBlank()) return false
        val numerator = amountText.substringBefore('/').trim().toDoubleOrNull() ?: return false
        val following = raw.substring(match.range.last + 1).trim()
        return numerator >= 10.0 || following.matches(
            Regex("(?i)^(?:raw|cooked|roasted|grilled|boneless|skinless|lean|fat)\\b.*")
        )
    }
}

fun String?.toMeasureUnit(): MeasureUnit? = when (this?.lowercase(Locale.ROOT)) {
    "g", "gram", "grams" -> MeasureUnit.GRAM
    "ml", "milliliter", "milliliters" -> MeasureUnit.MILLILITER
    "tbsp", "tablespoon", "tablespoons" -> MeasureUnit.TABLESPOON
    "tsp", "teaspoon", "teaspoons" -> MeasureUnit.TEASPOON
    "piece", "pieces" -> MeasureUnit.PIECE
    else -> null
}

fun MeasureUnit.displayLabel(): String = when (this) {
    MeasureUnit.GRAM -> "g"
    MeasureUnit.MILLILITER -> "ml"
    MeasureUnit.TABLESPOON -> "tbsp"
    MeasureUnit.TEASPOON -> "tsp"
    MeasureUnit.PIECE -> "piece"
    MeasureUnit.UNKNOWN -> ""
}
