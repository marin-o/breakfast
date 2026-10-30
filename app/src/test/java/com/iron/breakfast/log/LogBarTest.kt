package com.iron.breakfast.log

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
    @Test fun aPartWithNothingToLogDisablesAdd() = assertEquals(LogBar.ADD_DISABLED, bar(hasNoValue = true))
}
