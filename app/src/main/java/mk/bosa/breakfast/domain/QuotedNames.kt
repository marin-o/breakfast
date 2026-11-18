package mk.bosa.breakfast.domain

/** Text between double quotes, quote characters included: [start] to [end] (exclusive). Open: no closing quote. */
internal data class QuotedRange(val start: Int, val end: Int, val closed: Boolean) {
    operator fun contains(index: Int): Boolean = index in start until end
}

/**
 * The one rule for a name typed in double quotes ("yogurt, greek"): the text between the quotes is one name, so no
 * comma or word separator inside it splits the part. Every reader of typed text (parts, endsOpen, parse, the pins'
 * boundaries) asks here.
 *
 * Openers are `"`, `“` and the Macedonian `„`; closers are `"`, `”` for `“`, and `“`, `”` or `"` for `„`. Apostrophes
 * are never quotes. A quote opens only at the start or after a space, "(" or a separator, and closes only before the
 * end, a space, a separator or punctuation. A quote mark straight after a digit is an inch mark ("13\" long",
 * "12\" pizza") and never opens a quote; inside one that is open it closes it all the same where a closer may stand
 * ("vitamin b12", "Omega 3"), and an inch mark followed by a letter or digit does not. A quote never spans a newline or
 * ";": left open, it ends there.
 */
internal object QuotedNames {
    private const val OPENERS = "\"“„"
    private const val BEFORE_OPENER = ",;+&("

    private fun closers(opener: Char): String = when (opener) {
        '“' -> "”\""
        '„' -> "“”\""
        else -> "\""
    }

    /** The quoted ranges of [input], in order. */
    fun ranges(input: String): List<QuotedRange> {
        if (input.none { it in OPENERS }) return emptyList()
        val out = mutableListOf<QuotedRange>()
        var open = -1
        for (i in input.indices) {
            val c = input[i]
            if (open >= 0) {
                if (c == '\n' || c == ';') {
                    out += QuotedRange(open, i, closed = false)
                    open = -1
                } else if (c in closers(input[open]) && closesAt(input, i)) {
                    out += QuotedRange(open, i + 1, closed = true)
                    open = -1
                }
            } else if (c in OPENERS && opensAt(input, i)) {
                open = i
            }
        }
        if (open >= 0) out += QuotedRange(open, input.length, closed = false)
        return out
    }

    /** The positions of the quote marks of [ranges]: each opener, and each closer that closed. */
    fun markers(ranges: List<QuotedRange>): Set<Int> =
        ranges.flatMapTo(HashSet()) { if (it.closed) listOf(it.start, it.end - 1) else listOf(it.start) }

    /** [text] without its quote marks ([markers]); the text between them stays. */
    fun strip(text: String, ranges: List<QuotedRange> = ranges(text)): String {
        val marks = markers(ranges)
        if (marks.isEmpty()) return text
        return buildString { text.forEachIndexed { i, c -> if (i !in marks) append(c) } }
    }

    /**
     * What the quote that ends [text] holds, so far (the quote is open, or [text] ends with its closing mark); null when
     * [text] does not end in a quote or the quote holds nothing.
     */
    fun trailingInner(text: String): String? {
        val last = ranges(text).lastOrNull() ?: return null
        if (last.end != text.trimEnd().length) return null
        return text.substring(last.start + 1, if (last.closed) last.end - 1 else last.end).takeIf { it.isNotBlank() }
    }

    /** [text] ends inside a quote that has not closed: the name is still being typed. */
    fun isOpenAtEnd(text: String): Boolean = ranges(text).lastOrNull()?.let { !it.closed && it.end == text.length } == true

    private fun opensAt(input: String, i: Int): Boolean {
        if (i > 0 && input[i - 1].isDigit()) return false
        val before = input.getOrNull(i - 1) ?: return true
        return before.isWhitespace() || before in BEFORE_OPENER
    }

    private fun closesAt(input: String, i: Int): Boolean {
        val after = input.getOrNull(i + 1) ?: return true
        return !after.isLetterOrDigit()
    }
}
