package com.iron.breakfast.ui.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    private val quiet = Motion(reduced = false, density = Density(1f))
    private val reduced = Motion(reduced = true, density = Density(1f))

    @Test fun reducedMotionMakesEveryChangeInstant() {
        assertTrue(reduced.screenIn<Float>() is SnapSpec)
        assertTrue(reduced.screenOut<Float>() is SnapSpec)
        assertTrue(reduced.day<Float>() is SnapSpec)
        assertTrue(reduced.swipe<Float>() is SnapSpec)
        assertNull(reduced.listItem<Float>())
        assertEquals(EnterTransition.None, reduced.feedbackIn())
        assertEquals(ExitTransition.None, reduced.feedbackOut(resize = true))
        assertEquals(EnterTransition.None, reduced.rowIn())
        assertEquals(ExitTransition.None, reduced.rowOut())
    }

    @Test fun quietMotionIsShortTweens() {
        assertEquals(SCREEN_IN_MS, (quiet.screenIn<Float>() as TweenSpec).durationMillis)
        assertEquals(SCREEN_OUT_MS, (quiet.screenOut<Float>() as TweenSpec).durationMillis)
        assertEquals(DAY_MS, (quiet.day<Float>() as TweenSpec).durationMillis)
        assertEquals(SWIPE_MS, (quiet.swipe<Float>() as TweenSpec).durationMillis)
        assertEquals(LIST_MS, (quiet.listItem<Float>() as TweenSpec).durationMillis)
        assertTrue(quiet.feedbackIn() != EnterTransition.None)
        assertTrue(quiet.rowOut() != ExitTransition.None)
    }

    private fun rows(vararg keys: String) = keys.map { ShownRow(it, it, present = true, entering = false) }
    private fun List<ShownRow<String>>.drawn() = map { (if (it.present) "" else "-") + it.key + (if (it.entering) "+" else "") }

    @Test fun aRemovedRowStaysWhereItWasUntilItHasCollapsed() {
        assertEquals(listOf("a", "-b", "c"), mergeRows(rows("a", "b", "c"), listOf("a", "c")) { it }.drawn())
        assertEquals(listOf("-a", "b"), mergeRows(rows("a", "b"), listOf("b")) { it }.drawn())
        assertEquals(listOf("a", "-b"), mergeRows(rows("a", "b"), listOf("a")) { it }.drawn())
    }

    @Test fun aNewRowEntersInItsPlace() {
        assertEquals(listOf("a", "x+", "b"), mergeRows(rows("a", "b"), listOf("a", "x", "b")) { it }.drawn())
        assertEquals(listOf("x+", "a"), mergeRows(rows("a"), listOf("x", "a")) { it }.drawn())
        assertEquals(listOf("a", "x+"), mergeRows(rows("a"), listOf("a", "x")) { it }.drawn())
    }

    @Test fun aRowThatComesBackWhileItCollapsesEntersAgain() {
        val leaving = listOf(ShownRow("a", "a", present = true, entering = false), ShownRow("b", "b", present = false, entering = false))
        assertEquals(listOf("a", "b+"), mergeRows(leaving, listOf("a", "b")) { it }.drawn())
    }

    @Test fun reorderedRowsKeepEachRowOnce() {
        assertEquals(listOf("c", "a", "b"), mergeRows(rows("a", "b", "c"), listOf("c", "a", "b")) { it }.drawn())
    }
}
