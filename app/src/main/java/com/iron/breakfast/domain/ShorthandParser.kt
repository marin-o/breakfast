package com.iron.breakfast.domain

import java.util.Locale

/** Asks the food list about a piece of the input. The parser never sees the matcher; the planner supplies this. */
interface FoodProbe {
    /** True when [text], amount included, resolves to a food. */
    fun resolves(text: String): Boolean

    /** True when a single food covers every word of [phrase]. An exact name or alias counts. */
    fun covers(phrase: String): Boolean

    /** True when [phrase] is exactly one food's name or alias, ignoring case, punctuation and stop words. */
    fun isExactName(phrase: String): Boolean

    /** True when [text] is a food on its own: an exact name, or a resolution whose words all match whole. */
    fun isWholeFood(text: String): Boolean

    /** True when some food names [word] as a whole word (not as part of a longer word). */
    fun matchesWholeWord(word: String): Boolean
}

/** One part of the typed input: its text and where it sits in the input. */
data class SegmentSpan(val text: String, val start: Int, val end: Int)

class ShorthandParser {
    fun split(input: String, probe: FoodProbe? = null): List<String> = parts(input, probe).map { it.text }

    /**
     * The [input] with the structural parts that [drop] selects removed. Each kept part keeps its own text and the
     * separator that followed it in the input, so nothing is re-segmented.
     */
    fun removingParts(input: String, drop: (SegmentSpan) -> Boolean): String {
        val spans = parts(input)
        val kept = spans.indices.filterNot { drop(spans[it]) }
        val out = StringBuilder()
        kept.forEachIndexed { n, index ->
            out.append(spans[index].text)
            if (n < kept.lastIndex) out.append(input.substring(spans[index].end, spans[index + 1].start))
        }
        return out.toString()
    }

    /** True when [text] has an explicit word separator ("and", "&", "+", "и") as a whole word. */
    fun hasWordSeparator(text: String): Boolean =
        WORD.findAll(text).any { it.value.lowercase(Locale.ROOT) in ShorthandLexicon.explicitSeparators }

    /** True when the input ends with a separator, so nothing is being typed after it yet. */
    fun endsOpen(input: String): Boolean {
        if (input.isEmpty()) return false
        // Any trailing space, comma, semicolon or newline ends the last part; so do + & and и.
        if (input.last().isWhitespace() || input.last() in OPEN_TAIL_CHARS) return true
        return END_SEPARATOR_WORD.containsMatchIn(input)
    }

    /**
     * The one segmentation of typed input. Commas, semicolons and newlines end a part, and only a comma can rejoin
     * across one, and only when the join is exactly one food's name ("Пинџур, домашен"). Inside a comma part, a new
     * quantity after a food word and before another starts a part ("2 egg 1 banana"). An explicit word separator
     * (`+`, `&`, ` and `, ` и `) starts a part only when both sides are a food's exact name or alias on their own, so
     * "Ben & Jerry's" and "Mac and cheese" stay whole. With a [probe], neighbouring pieces that together are one food
     * are rejoined, and a quantity split is kept only when some piece resolves to a food.
     */
    fun parts(input: String, probe: FoodProbe? = null): List<SegmentSpan> {
        val spans = mutableListOf<SegmentSpan>()
        var start = 0
        (HARD_BREAK.findAll(input).map { it.range.first } + input.length).forEach { end ->
            val segment = partsOfSegment(input, start, end, probe)
            val previous = spans.lastOrNull()
            val first = segment.firstOrNull()
            val breakBefore = if (start > 0) input[start - 1] else null
            if (probe != null && breakBefore == ',' && previous != null && first != null &&
                isWholeFoodName(input.substring(previous.start, first.end), probe)
            ) {
                spans[spans.lastIndex] = spanOf(input, previous.start, first.end)
                spans += segment.drop(1)
            } else {
                spans += segment
            }
            start = end + 1
        }
        return if (probe == null) spans else splitTrailingFragments(input, spans, probe)
    }

    /**
     * A trailing word that no food names as a whole word ("zzq", "bu", "ban") is split off the part it ends when the
     * words before it are a food on their own: "2 eggs zzq" is "2 eggs" and "zzq". The word is then a fragment the
     * user may still be typing, and the head stays a food row.
     */
    private fun splitTrailingFragments(input: String, spans: List<SegmentSpan>, probe: FoodProbe): List<SegmentSpan> {
        val out = mutableListOf<SegmentSpan>()
        for (span in spans) {
            val words = WORD.findAll(span.text).toList()
            val last = words.lastOrNull()
            if (words.size >= 2 && last != null && ShorthandLexicon.mealSlotOf(span.text) == null) {
                val word = last.value
                val head = spanOf(input, span.start, span.start + words[words.size - 2].range.last + 1)
                // Only a plain word is a fragment: "150ml" and "60g" are quantities, never a word being typed.
                val plainWord = word.all { it.isLetter() }
                if (plainWord && isFoodWord(word) && !probe.matchesWholeWord(word) && probe.isWholeFood(head.text)) {
                    out += head
                    out += spanOf(input, span.start + last.range.first, span.end)
                    continue
                }
            }
            out += span
        }
        return out
    }

    /** True when [text], with or without its amount, is exactly one food's name or alias. */
    private fun isWholeFoodName(text: String, probe: FoodProbe): Boolean =
        probe.isExactName(text) || probe.isExactName(parse(text).foodPhrase)

    private fun partsOfSegment(input: String, from: Int, to: Int, probe: FoodProbe?): List<SegmentSpan> {
        val words = WORD.findAll(input.substring(from, to)).map { from + it.range.first..from + it.range.last }.toList()
        if (words.isEmpty()) return emptyList()
        val texts = words.map { input.substring(it.first, it.last + 1) }
        val groups = mutableListOf<List<Int>>()
        // For each group: whether the break before it is a word separator (true) or a quantity boundary (false).
        val wordBreaks = mutableListOf<Boolean>()
        var current = mutableListOf<Int>()
        var currentWordBreak = false
        // Within a part, a quantity after food words belongs to that food (a trailing count such as "60g" or "200ml").
        // A quantity that follows the food of a part already carrying a quantity, or a bare number before food, leads
        // the next food. So "2 egg 1 banana" is two foods, "kiselo mleko 200g 1 kg rolled oats" cuts between 200g and 1 kg.
        var leadingQuantity = false
        var trailingQuantity = false
        fun flush(nextWordBreak: Boolean) {
            if (current.isNotEmpty()) {
                groups += current
                wordBreaks += currentWordBreak
            }
            current = mutableListOf()
            currentWordBreak = nextWordBreak
            leadingQuantity = false
            trailingQuantity = false
        }
        var i = 0
        while (i < texts.size) {
            val word = texts[i]
            if (word.lowercase(Locale.ROOT) in ShorthandLexicon.explicitSeparators) {
                flush(nextWordBreak = true)
                i++
                continue
            }
            val quantityEnd = quantityEnd(texts, i)
            if (quantityEnd != null) {
                val hasFood = current.any { isFoodWord(texts[it]) }
                val foodFollows = foodWordBeforeSeparator(texts, quantityEnd)
                if (hasFood && foodFollows && (leadingQuantity || trailingQuantity || !hasUnit(texts, i, quantityEnd))) {
                    flush(nextWordBreak = false)
                    leadingQuantity = true
                } else if (hasFood) {
                    trailingQuantity = true
                } else {
                    leadingQuantity = true
                }
                current += i until quantityEnd
                i = quantityEnd
                continue
            }
            // A food word after a trailing quantity starts the next food: "oats 60g milk" is two parts.
            if (trailingQuantity && isFoodWord(word)) flush(nextWordBreak = false)
            // A leading meal word before a quantity is its own part: "lunch 1 banana" is two parts.
            if (current.isEmpty() && ShorthandLexicon.mealSlotOf(word) != null && quantityEnd(texts, i + 1) != null) {
                current += i
                flush(nextWordBreak = false)
                i++
                continue
            }
            current += i
            i++
        }
        flush(nextWordBreak = false)

        val spans = groups.map { indices -> spanOf(input, words[indices.first()].first, words[indices.last()].last + 1) }
        if (spans.size <= 1 || probe == null) return spans

        // Rejoin neighbours whose join is one food ("Protein 2 bar", or "Mac and cheese" with a Macaroni food).
        // A piece that was joined this way is a food by construction, so it counts as one on its own below.
        val joined = mutableListOf<SegmentSpan>()
        val joinedWordBreaks = mutableListOf<Boolean>()
        val joinedCovered = mutableListOf<Boolean>()
        var index = 0
        while (index < spans.size) {
            var last = index
            for (j in spans.lastIndex downTo index + 1) {
                if (coversText(spanOf(input, spans[index].start, spans[j].end).text, probe)) {
                    last = j
                    break
                }
            }
            joined += spanOf(input, spans[index].start, spans[last].end)
            joinedWordBreaks += wordBreaks[index]
            joinedCovered += last > index
            index = last + 1
        }
        if (joined.size == 1) return joined

        // A word separator splits only when both sides are a food on their own. Otherwise the sides stay one part.
        val parts = mutableListOf(joined.first())
        val partIsFood = mutableListOf(joinedCovered.first())
        for (k in 1 until joined.size) {
            val left = parts.last()
            val right = joined[k]
            val leftIsFood = partIsFood.last() || isStrictPiece(left.text, probe)
            val rightIsFood = joinedCovered[k] || isStrictPiece(right.text, probe)
            val split = !joinedWordBreaks[k] || (leftIsFood && rightIsFood)
            if (split) {
                parts += right
                partIsFood += joinedCovered[k]
            } else {
                parts[parts.lastIndex] = spanOf(input, left.start, right.end)
                partIsFood[partIsFood.lastIndex] = false
            }
        }
        if (parts.size == 1) return parts

        // A quantity split is kept only when some food part resolves; otherwise the whole segment stays one part.
        val foodParts = parts.filter { ShorthandLexicon.mealSlotOf(it.text) == null }
        return if (foodParts.any { probe.resolves(it.text) }) parts
        else listOf(spanOf(input, words.first().first, words.last().last + 1))
    }

    /** A piece that stands alone as a food (its exact name or alias) or as a meal word. */
    private fun isStrictPiece(text: String, probe: FoodProbe): Boolean =
        ShorthandLexicon.mealSlotOf(text) != null || isWholeFoodName(text, probe) || probe.isWholeFood(text)

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
            MULTIPLIER.matches(word) -> i + 1
            else -> wordQuantityEnd(texts, i)
        }
    }

    /** End of a number word at [i] ("two", "half", "a couple of", "a half") with its optional unit, or null. */
    private fun wordQuantityEnd(texts: List<String>, i: Int): Int? {
        val lower = texts.getOrNull(i)?.lowercase(Locale.ROOT) ?: return null
        val next = texts.getOrNull(i + 1)?.lowercase(Locale.ROOT)
        var end = when {
            lower in setOf("a", "an") && next != null && (ShorthandLexicon.isFractionWord(next) || next == "couple") -> i + 2
            ShorthandLexicon.wordAmount(lower) != null -> i + 1
            else -> return null
        }
        if (next == "couple" && texts.getOrNull(end)?.lowercase(Locale.ROOT) == "of") end++
        // "half a tbsp" takes its article after the word.
        if (ShorthandLexicon.isFractionWord(lower) && texts.getOrNull(end)?.lowercase(Locale.ROOT) == "a") end++
        if (texts.getOrNull(end)?.toMeasureUnit() != null) end++
        return end
    }

    private fun isQuantityFraction(word: String, following: String?): Boolean {
        if (!FRACTION.matches(word)) return false
        val numerator = word.substringBefore('/').toIntOrNull() ?: return false
        return numerator < 10 && following?.lowercase(Locale.ROOT) !in COMPOSITION_WORDS
    }

    /** True when the quantity words in [from, to) carry a unit ("60g", "1 kg", "2 tbsp"); a bare number does not. */
    private fun hasUnit(texts: List<String>, from: Int, to: Int): Boolean = (from until to).any { index ->
        texts[index].toMeasureUnit() != null ||
            ATTACHED_QUANTITY.matchEntire(texts[index])?.let { it.groupValues[2].toMeasureUnit() != null } == true
    }

    private fun foodWordBeforeSeparator(texts: List<String>, from: Int): Boolean {
        for (index in from until texts.size) {
            if (texts[index].lowercase(Locale.ROOT) in ShorthandLexicon.explicitSeparators) return false
            if (isFoodWord(texts[index])) return true
        }
        return false
    }

    /** A word that can name a food: it has a letter and is not structure (a stop word, meal word, unit or number word). */
    private fun isFoodWord(word: String): Boolean {
        val lower = word.lowercase(Locale.ROOT)
        return word.any { it.isLetter() } && lower !in ShorthandLexicon.stopWords &&
            ShorthandLexicon.mealSlotOf(word) == null && lower.toMeasureUnit() == null &&
            ShorthandLexicon.wordAmount(lower) == null && !MULTIPLIER.matches(word)
    }

    fun parse(input: String): ParsedSegment {
        val raw = input.trim()
        if (raw.isEmpty()) return ParsedSegment(raw, "", null, null, null, null)

        val quantity = findQuantity(raw) ?: return ParsedSegment(raw, raw, null, null, null, null)
        val foodPhrase = (raw.removeRange(quantity.range)).replace(WHITESPACE, " ").trim()
        return ParsedSegment(
            raw, foodPhrase, quantity.amount, quantity.unit, quantity.range.first, quantity.range.last + 1,
            wordQuantity = quantity.fromWord, articleQuantity = quantity.article,
        )
    }

    /**
     * A stated quantity. [amount] is null for a bare article ("a milk"): the food's default unit then decides whether
     * it means one. [fromWord] is true for a number word or an article, which may be part of a food's name.
     */
    private class Quantity(
        val amount: Double?,
        val unit: MeasureUnit?,
        val range: IntRange,
        val fromWord: Boolean = false,
        val article: Boolean = false,
    )

    /** The amount a segment states. Priority: half words, then digits, then number words, then "2x" or "x2". */
    private fun findQuantity(raw: String): Quantity? {
        halfPattern.find(raw)?.let { match ->
            return quantityWithUnit(ShorthandLexicon.wordAmount(match.groupValues[1]) ?: 0.5, match.groupValues[2], match.range, fromWord = true)
        }
        numberPattern.findAll(raw).firstOrNull { candidate ->
            !isCompositionRatio(candidate, raw) || candidate.groupValues.getOrNull(2).orEmpty().isNotBlank()
        }?.let { match ->
            val amount = parseAmount(match.groupValues[1])
            return quantityWithUnit(if (isNegated(raw, match.range.first)) -amount else amount, match.groupValues[2], match.range)
        }
        wordPattern.find(raw)?.let { match ->
            val unitText = match.groupValues[2]
            if (match.groupValues[1].lowercase(Locale.ROOT) in ARTICLES && unitText.isBlank()) {
                return Quantity(null, null, match.range, fromWord = true, article = true)
            }
            return quantityWithUnit(ShorthandLexicon.wordAmount(match.groupValues[1]) ?: 0.0, unitText, match.range, fromWord = true)
        }
        return multiplierPattern.find(raw)?.let { match ->
            val count = match.groupValues[1].ifEmpty { match.groupValues[2] }.toDoubleOrNull() ?: 0.0
            Quantity(count, null, match.range)
        }
    }

    /** A stated amount in the unit it names, converted to the stored unit: "1.5 kg" is 1500 g. */
    private fun quantityWithUnit(amount: Double, unitText: String, range: IntRange, fromWord: Boolean = false): Quantity {
        val spec = ShorthandLexicon.unitSpec(unitText) ?: return Quantity(amount, null, range, fromWord)
        return Quantity(amount * spec.factor, spec.unit, range, fromWord)
    }

    private fun parseAmount(text: String): Double {
        val normalized = text.trim().lowercase(Locale.ROOT).replace(',', '.')
        if (normalized == "half") return 0.5
        val pieces = normalized.split(WHITESPACE)
        if (pieces.size == 2 && '/' in pieces[1]) {
            return (pieces[0].toDoubleOrNull() ?: 0.0) + fraction(pieces[1])
        }
        if ('/' in normalized) return fraction(normalized)
        return normalized.toDoubleOrNull() ?: 0.0
    }

    /** True for a minus sign straight before the number at [index] that starts a word: "-1 banana", not "2-3". */
    private fun isNegated(raw: String, index: Int): Boolean =
        index > 0 && raw[index - 1] == '-' && (index == 1 || raw[index - 2].isWhitespace())

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
        return numerator >= 10.0 || following.matches(COMPOSITION_TAIL)
    }

    // Built once: compiling these patterns per parser made every keystroke slow.
    private companion object {
        val unit = ShorthandLexicon.unitAlternation()
        val halfPattern = Regex(
            "(?iu)(?<![\\p{L}\\p{N}])(?:a\\s+)?(half|quarter|половина)(?![\\p{L}\\p{N}])(?:\\s+a(?![\\p{L}\\p{N}]))?\\s*" +
                "($unit)?(?![\\p{L}\\p{N}])"
        )
        /** Number words other than the half family, longest first so "a couple of" wins over "a". */
        val wordPattern = Regex(
            "(?iu)(?<![\\p{L}\\p{N}])(${ShorthandLexicon.wordAlternation()})(?![\\p{L}\\p{N}])\\s*" +
                "($unit)?(?![\\p{L}\\p{N}])"
        )
        /** "2x egg" and "egg x2". */
        val multiplierPattern = Regex("(?iu)(?<![\\p{L}\\p{N}])(?:(\\d+)\\s*x|x(\\d+))(?![\\p{L}\\p{N}.,])")
        val numberPattern = Regex(
            "(?iu)\\b(\\d+\\s+\\d+/\\d+|\\d+/\\d+|\\d+(?:[.,]\\d+)?)(?![.,\\d]|\\s*%)\\s*" +
                "($unit)?(?![\\p{L}\\p{N}])"
        )
        val COMPOSITION_TAIL = Regex("(?iu)^(?:raw|cooked|roasted|grilled|boneless|skinless|lean|fat)\\b.*")
        /** Characters that always end a part. A comma does too, unless it sits between two digits ([ItemComma]). */
        const val BREAK_CHARS = ";\n"
        /** A comma between two digits is a decimal separator ("1,5 kg"), never a break; "oats,1 banana" still breaks. */
        val HARD_BREAK = Regex("${ItemComma.pattern}|[$BREAK_CHARS]")
        val WORD = Regex("\\S+")
        val WHITESPACE = Regex("\\s+")
        val NUMBER = Regex("\\d+(?:[.,]\\d+)?")
        val FRACTION = Regex("\\d+/\\d+")
        val MULTIPLIER = Regex("(?iu)\\d+x|x\\d+")
        val ATTACHED_QUANTITY = Regex("(\\d+(?:[.,]\\d+)?)(\\p{L}+)")
        val ARTICLES = setOf("a", "an")
        val COMPOSITION_WORDS = setOf("raw", "cooked", "roasted", "grilled", "boneless", "skinless", "lean", "fat")
        // The tail rules come from the break characters and the lexicon's separators, so the sets cannot drift apart.
        private val SEPARATOR_SYMBOLS = ShorthandLexicon.explicitSeparators.filterNot { word -> word.any(Char::isLetter) }
        private val SEPARATOR_WORDS = ShorthandLexicon.explicitSeparators - SEPARATOR_SYMBOLS.toSet()
        val OPEN_TAIL_CHARS = setOf(',') + BREAK_CHARS.toSet() + SEPARATOR_SYMBOLS.map { it.single() }
        val END_SEPARATOR_WORD = Regex("(?iu)(?:^|\\s)(?:${SEPARATOR_WORDS.joinToString("|") { Regex.escape(it) }})$")
    }
}

fun String?.toMeasureUnit(): MeasureUnit? = ShorthandLexicon.unitSpec(this)?.unit

/** The short name a unit is shown with ("g", "tbsp"). [MeasureUnit.label] is the one definition. */
fun MeasureUnit.displayLabel(): String = label
