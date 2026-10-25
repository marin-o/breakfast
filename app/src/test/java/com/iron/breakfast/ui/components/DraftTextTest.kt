package com.iron.breakfast.ui.components

import org.junit.Assert.assertEquals
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
}
