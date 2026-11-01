package com.bosa.breakfast.ui.components

import java.text.BreakIterator

/** How the composer shows a draft too long for its bar (Settings, "Long draft"). */
enum class LongDraft {
    /** One line showing the end, where the user types: "…, 1 tbsp pb, 1 banana". */
    END,
    /** One line showing the start, cut at the end with "…". */
    START,
    /** Up to two lines, cut at the end with "…". */
    TWO_LINES,
}

/**
 * [text] as it fits when cut at the start: "…" and the longest end of [text] that [fits]. [fits] must hold for every
 * shorter end once it holds for a longer one, as a width check does. The cut is always at a character (grapheme)
 * boundary, so an emoji sequence, a flag or a letter with its accent is never split.
 */
internal fun startEllipsized(text: String, fits: (String) -> Boolean): String {
    if (text.isEmpty() || fits(text)) return text
    // The smallest start whose end, after "…", still fits.
    var low = 1
    var high = text.length
    while (low < high) {
        val mid = (low + high) ushr 1
        if (fits(ELLIPSIS + text.substring(mid))) high = mid else low = mid + 1
    }
    val start = nextGraphemeBoundary(text, low)
    return ELLIPSIS + text.substring(start)
}

/** [index] if a character starts there, else the start of the next one (or the end). */
private fun nextGraphemeBoundary(text: String, index: Int): Int {
    if (index >= text.length) return text.length
    val characters = BreakIterator.getCharacterInstance().apply { setText(text) }
    return if (characters.isBoundary(index)) index else characters.following(index).let { if (it == BreakIterator.DONE) text.length else it }
}

private const val ELLIPSIS = "…"
