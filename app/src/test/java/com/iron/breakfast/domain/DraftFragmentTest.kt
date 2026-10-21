package com.iron.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row order follows the typed order. */
class DraftFragmentTest {
    private val planner = LogPlanner(FoodMatcher(FoodCatalog.fixtures))

    private fun rowTexts(draft: DraftView) = draft.rows.map {
        when (it) {
            is DraftRow.Entry -> it.resolution.raw
            is DraftRow.Unresolved -> it.text
        }
    }

    @Test fun rowsFollowTheTypedOrder() {
        val draft = planner.draft("kiselo mleko 200g 1 kg rolled oats")
        assertEquals(listOf("kiselo mleko 200g", "1 kg rolled oats"), rowTexts(draft))
        assertTrue(draft.rows[0] is DraftRow.Unresolved)
        assertTrue(draft.rows[1] is DraftRow.Entry)
    }
}
