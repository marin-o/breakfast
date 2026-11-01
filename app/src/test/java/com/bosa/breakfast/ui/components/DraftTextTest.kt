package com.bosa.breakfast.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftTextTest {
    /** A bar [width] characters wide. */
    private fun fitsIn(width: Int): (String) -> Boolean = { it.length <= width }

    @Test fun aDraftThatFitsIsShownWhole() {
        assertEquals("2 eggs, 1 banana", startEllipsized("2 eggs, 1 banana", fitsIn(16)))
        assertEquals("", startEllipsized("", fitsIn(0)))
    }

    @Test fun aLongDraftKeepsItsEnd() {
        val draft = "2 eggs, 1 slice bread, 1 tbsp pb, 1 banana"
        assertEquals("…, 1 tbsp pb, 1 banana", startEllipsized(draft, fitsIn(22)))
        assertEquals("…a", startEllipsized(draft, fitsIn(2)))
    }

    @Test fun theLongestEndThatFitsIsChosen() {
        val draft = "abcdefghij"
        for (width in 1..9) {
            val shown = startEllipsized(draft, fitsIn(width))
            assertEquals(width, shown.length)
            assertEquals(draft.takeLast(width - 1), shown.drop(1))
        }
    }

    @Test fun nothingFitsShowsTheEllipsisAlone() {
        assertEquals("…", startEllipsized("abc", fitsIn(0)))
    }

    @Test fun aSurrogatePairIsNotSplit() {
        val draft = "1 egg \uD83E\uDD5A"
        // Width 3 would start at the low half of the emoji; the cut moves past the whole pair.
        val shown = startEllipsized(draft, fitsIn(2))
        assertEquals("…", shown)
        assertEquals("…\uD83E\uDD5A", startEllipsized(draft, fitsIn(3)))
    }

    @Test fun aCutNeverSplitsACharacter() {
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67" // man, ZWJ, woman, ZWJ, girl
        val flag = "\uD83C\uDDF2\uD83C\uDDF0" // MK: two regional indicators
        val accent = "e\u0301" // e and a combining acute
        for (whole in listOf(family, flag, accent)) {
            val draft = "1 egg, $whole"
            for (width in 1..draft.length + 1) {
                val end = startEllipsized(draft, fitsIn(width)).removePrefix("…")
                val cut = draft.length - end.length
                // The last character is shown whole or not at all.
                assertTrue("$whole at $width: $end", cut <= draft.length - whole.length || cut == draft.length)
            }
            assertEquals("…", startEllipsized(draft, fitsIn(whole.length)))
            assertEquals("…$whole", startEllipsized(draft, fitsIn(whole.length + 1)))
        }
    }
}
