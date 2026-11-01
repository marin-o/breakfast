package com.bosa.breakfast.log

import org.junit.Assert.assertEquals
import org.junit.Test

class LogBarTest {
    private fun bar(addLogs: Boolean = false, needsMatch: Boolean = false, hasNoValue: Boolean = false, queue: Boolean = false, unqueued: Boolean = false) =
        logBarOf(addLogs, needsMatch, hasNoValue, queue, unqueued)

    @Test fun onlyUnmatchedFoodsFindMatches() = assertEquals(LogBar.FIND_MATCHES, bar(needsMatch = true))
    @Test fun aRunningQueueWithNothingToLogHasNoBar() = assertEquals(LogBar.HIDDEN, bar(needsMatch = true, queue = true))
    @Test fun aScannedPartDuringAQueueHasTheNormalBar() = assertEquals(LogBar.ADD, bar(addLogs = true, needsMatch = true, queue = true))
    @Test fun somethingToLogBesideUnmatchedHasTheNormalBar() = assertEquals(LogBar.ADD, bar(addLogs = true, needsMatch = true))
    @Test fun anEmptyDraftOrAMealWordIsAsToday() = assertEquals(LogBar.ADD, bar())
    @Test fun onlyNoValueLeavesAddDisabled() = assertEquals(LogBar.ADD_DISABLED, bar(hasNoValue = true))
    @Test fun aPartTypedDuringAQueueFindsMatches() = assertEquals(LogBar.FIND_MATCHES, bar(needsMatch = true, queue = true, unqueued = true))
    @Test fun findMoreMatchesIsBesideAddWhileAPartIsTyped() {
        assertEquals(true, logOffersMoreMatches(LogBar.ADD, "sarma"))
        assertEquals("nothing typed", false, logOffersMoreMatches(LogBar.ADD, " "))
        assertEquals("Find matches already searches", false, logOffersMoreMatches(LogBar.FIND_MATCHES, "sarma"))
        assertEquals(false, logOffersMoreMatches(LogBar.ADD_DISABLED, "sarma"))
        assertEquals(false, logOffersMoreMatches(LogBar.HIDDEN, "sarma"))
    }
    @Test fun aPressKeepsTheKeyboardDownUntilATapOnTheText() {
        assertEquals(LogFieldFocus.KEYBOARD_DOWN, logFieldFocusOf(matching = false, menuOpen = false, writing = false, keptDown = true))
        assertEquals("a closing menu does not bring it back", LogFieldFocus.KEYBOARD_DOWN, logFieldFocusOf(matching = false, menuOpen = true, writing = false, keptDown = true))
        assertEquals("nor a write", LogFieldFocus.KEYBOARD_DOWN, logFieldFocusOf(matching = false, menuOpen = false, writing = true, keptDown = true))
        assertEquals("after the tap, as ever", LogFieldFocus.FOCUS, logFieldFocusOf(matching = false, menuOpen = false, writing = false, keptDown = false))
    }
    @Test fun aRowBelowTheListScrollsUpJustEnough() = assertEquals(700, logScrollToShow(top = 900, height = 100, viewport = 300, scrolled = 0))
    @Test fun aRowAboveTheListScrollsDownToItsTop() = assertEquals(200, logScrollToShow(top = 200, height = 100, viewport = 300, scrolled = 500))
    @Test fun aRowThatShowsStaysPut() = assertEquals(150, logScrollToShow(top = 200, height = 100, viewport = 300, scrolled = 150))
    @Test fun aRowTooTallForTheListStartsAtItsTop() = assertEquals(900, logScrollToShow(top = 900, height = 500, viewport = 300, scrolled = 0))
    @Test fun onlyAnEditArmsThePauseAndItStaysArmed() {
        assertEquals("opening the Log", false, logDraftEdited(false, "kefir", "kefir"))
        assertEquals("a keystroke", true, logDraftEdited(false, "kefir x", "kefir"))
        assertEquals("back at the text it opened with", true, logDraftEdited(true, "kefir", "kefir"))
    }
    @Test fun aPartWithNothingToLogDisablesAdd() = assertEquals(LogBar.ADD_DISABLED, bar(hasNoValue = true))
}
