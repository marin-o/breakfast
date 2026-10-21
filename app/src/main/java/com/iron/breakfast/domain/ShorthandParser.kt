package com.iron.breakfast.domain

import java.util.Locale

/** Asks the food list about a piece of the input. The parser never sees the matcher; the planner supplies this. */
interface FoodProbe {
    /** True when [text], amount included, resolves to a food. */
    fun resolves(text: String): Boolean

    /** True when a single food covers every word of [phrase]. An exact name or alias counts. */
    fun covers(phrase: String): Boolean
}

/** One part of the typed input: its text and where it sits in the input. */
data class SegmentSpan(val text: String, val start: Int, val end: Int)

class ShorthandParser {
    private val halfPattern = Regex(
        "(?i)\\bhalf\\s+a?\\s*(g|grams?|ml|milliliters?|tbsp|tablespoons?|tsp|teaspoons?|pieces?)?\\b"
    )
    private val numberPattern = Regex(
        "(?i)\\b(\\d+\\s+\\d+/\\d+|\\d+/\\d+|\\d+(?:[.,]\\d+)?)(?![.,\\d]|\\s*%)\\s*" +
            "(g|grams?|ml|milliliters?|tbsp|tablespoons?|tsp|teaspoons?|pieces?)?\\b"
    )

    fun split(input: String, probe: FoodProbe? = null): List<String> = parts(input, probe).map { it.text }

    /** True when the input ends with a separator, so nothing is being typed after it yet. */
    fun endsOpen(input: String): Boolean {
        val trimmed = input.trimEnd()
        return trimmed.isNotEmpty() && (trimmed.last() in OPEN_TAIL_CHARS || END_SEPARATOR_WORD.containsMatchIn(trimmed))
    }

    /**
     * The one segmentation of typed input. Commas, semicolons and newlines always end a part. Inside a part, explicit
     * separators (`+`, `&`, ` and `, ` и `) end one too, and so does a new quantity that follows at least one food word
     * and is followed by another. With a [probe], a split is kept only when it does not break a food name: the whole
     * part must not be one food, and at least one piece must resolve.
     */
    fun parts(input: String, probe: FoodProbe? = null): List<SegmentSpan> {
        val spans = mutableListOf<SegmentSpan>()
        var start = 0
        (HARD_BREAK.findAll(input).map { it.range.first } + input.length).forEach { end ->
            spans += partsOfSegment(input, start, end, probe)
            start = end + 1
        }
        return spans
    }

    private fun partsOfSegment(input: String, from: Int, to: Int, probe: FoodProbe?): List<SegmentSpan> {
        val words = WORD.findAll(input.substring(from, to)).map { from + it.range.first..from + it.range.last }.toList()
        if (words.isEmpty()) return emptyList()
        val texts = words.map { input.substring(it.first, it.last + 1) }
        val groups = mutableListOf<List<Int>>()
        var current = mutableListOf<Int>()
        fun flush() {
            if (current.isNotEmpty()) groups += current
            current = mutableListOf()
        }
        var i = 0
        while (i < texts.size) {
            val word = texts[i]
            if (word.lowercase(Locale.ROOT) in ShorthandLexicon.explicitSeparators) {
                flush()
                i++
                continue
            }
            val quantityEnd = quantityEnd(texts, i)
            if (quantityEnd != null) {
                if (current.any { isFoodWord(texts[it]) } && foodWordBeforeSeparator(texts, quantityEnd)) flush()
                current += i until quantityEnd
                i = quantityEnd
                continue
            }
            // A leading meal word before a quantity is its own part: "lunch 1 banana" is two parts.
            if (current.isEmpty() && ShorthandLexicon.mealSlotOf(word) != null && quantityEnd(texts, i + 1) != null) {
                current += i
                flush()
                i++
                continue
            }
            current += i
            i++
        }
        flush()

        val maximal = groups.map { indices -> spanOf(input, words[indices.first()].first, words[indices.last()].last + 1) }
        if (maximal.size <= 1 || probe == null) return maximal
        // Keep a name whole: join neighbouring parts whenever their join is one food, such as "Protein 2 bar".
        val joined = mutableListOf<SegmentSpan>()
        var index = 0
        while (index < maximal.size) {
            var last = index
            for (j in maximal.lastIndex downTo index + 1) {
                if (coversText(spanOf(input, maximal[index].start, maximal[j].end).text, probe)) {
                    last = j
                    break
                }
            }
            joined += spanOf(input, maximal[index].start, maximal[last].end)
            index = last + 1
        }
        if (joined.size == 1) return joined
        // Split only when some food part resolves; otherwise the whole segment stays as one unresolved part.
        val foodParts = joined.filter { ShorthandLexicon.mealSlotOf(it.text) == null }
        return if (foodParts.any { probe.resolves(it.text) }) joined else listOf(spanOf(input, words.first().first, words.last().last + 1))
    }

    private fun coversText(text: String, probe: FoodProbe): Boolean =
        probe.covers(text) || probe.covers(parse(text).foodPhrase)

    private fun spanOf(input: String, start: Int, end: Int) = SegmentSpan(input.substring(start, end), start, end)

    /** Exclusive end of the quantity that starts at [i], or null when no quantity starts there. */
    private fun quantityEnd(texts: List<String>, i: Int): Int? {
        val word = texts.getOrNull(i) ?: return null
        val next = texts.getOrNull(i + 1)
        return when {
            NUMBER.matches(word) -> {
                if (next?.startsWith("%") == true) return null
                var end = i + 1
                if (next != null && isQuantityFraction(next, texts.getOrNull(i + 2))) end++
                if (texts.getOrNull(end)?.toMeasureUnit() != null) end++
                end
            }
            isQuantityFraction(word, next) -> if (next?.toMeasureUnit() != null) i + 2 else i + 1
            ATTACHED_QUANTITY.matchEntire(word)?.let { it.groupValues[2].toMeasureUnit() != null } == true -> i + 1
            else -> null
        }
    }

    private fun isQuantityFraction(word: String, following: String?): Boolean {
        if (!FRACTION.matches(word)) return false
        val numerator = word.substringBefore('/').toIntOrNull() ?: return false
        return numerator < 10 && following?.lowercase(Locale.ROOT) !in COMPOSITION_WORDS
    }

    private fun foodWordBeforeSeparator(texts: List<String>, from: Int): Boolean {
        for (index in from until texts.size) {
            if (texts[index].lowercase(Locale.ROOT) in ShorthandLexicon.explicitSeparators) return false
            if (isFoodWord(texts[index])) return true
        }
        return false
    }

    /** A word that can name a food: it has a letter and is not structure (a stop word, a meal word or a unit). */
    private fun isFoodWord(word: String): Boolean {
        val lower = word.lowercase(Locale.ROOT)
        return word.any { it.isLetter() } && lower !in ShorthandLexicon.stopWords &&
            ShorthandLexicon.mealSlotOf(word) == null && lower.toMeasureUnit() == null
    }

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

    private companion object {
        val HARD_BREAK = Regex("[,;\n]")
        val WORD = Regex("\\S+")
        val NUMBER = Regex("\\d+(?:[.,]\\d+)?")
        val FRACTION = Regex("\\d+/\\d+")
        val ATTACHED_QUANTITY = Regex("(\\d+(?:[.,]\\d+)?)(\\p{L}+)")
        val COMPOSITION_WORDS = setOf("raw", "cooked", "roasted", "grilled", "boneless", "skinless", "lean", "fat")
        val OPEN_TAIL_CHARS = setOf(',', ';', '+', '&', '\n')
        val END_SEPARATOR_WORD = Regex("(?i)(?:^|\\s)(?:and|и)$")
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
