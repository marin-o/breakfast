package com.iron.breakfast.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepPositionTest {
    private val first = StepPosition(0, 3)

    @Test fun nextGoesForwardAndStopsAtTheLast() {
        assertEquals(1, first.next().page)
        assertEquals(2, first.next().next().page)
        assertEquals(2, first.next().next().next().page)
    }

    @Test fun backGoesBackAndStopsAtTheFirst() {
        assertEquals(1, StepPosition(2, 3).back().page)
        assertEquals(first, first.back())
        assertFalse(first.canGoBack)
        assertTrue(StepPosition(1, 3).canGoBack)
    }

    /** A second Next while the first still moves counts from where it is going. */
    @Test fun aSecondNextIsNotLost() {
        val twice = first.next().next()
        assertEquals(2, twice.page)
        assertEquals(0, twice.settled)
    }

    /** The pager stopping on the way, as a new move replaces the last, does not take the position back. */
    @Test fun aSettleOnTheWayDoesNotOverrideANewerPosition() {
        val moving = first.next().next().settledAt(1, byUser = false)
        assertEquals(2, moving.page)
        assertEquals(1, moving.settled)
        assertEquals(2, moving.settledAt(2, byUser = false).settled)
    }

    /** A swipe is the user's: it wins, also over a move still going. */
    @Test fun aSwipeSetsThePosition() {
        assertEquals(1, first.settledAt(1, byUser = true).page)
        val moving = first.next().next()
        val swiped = moving.settledAt(1, byUser = true)
        assertEquals(1, swiped.page)
        assertEquals(1, swiped.settled)
    }

    /** With no move going, a page reached any other way (TalkBack's page scroll) is still where it is. */
    @Test fun aSettleWithNoMoveGoingSetsThePosition() {
        assertEquals(StepPosition(2, 3), first.settledAt(2, byUser = false))
    }

    /** The last page's foot waits for the pager, so a second tap on Next cannot land on what replaces it. */
    @Test fun arrivingAtTheLastNeedsThePagerThere() {
        val going = StepPosition(1, 3).next()
        assertTrue(going.isLast)
        assertFalse(going.arrivedAtLast)
        assertFalse(going.next().arrivedAtLast)
        assertTrue(going.settledAt(2, byUser = false).arrivedAtLast)
        assertFalse(StepPosition(1, 3).arrivedAtLast)
    }

    @Test fun aRestoreLandsOnAPageThatExists() {
        assertEquals(StepPosition(2, 3), first.at(2))
        assertEquals(StepPosition(2, 3), first.at(9))
        assertEquals(first, StepPosition(1, 3).at(-1))
    }
}
