package com.iron.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A typed part renders once: as one row, or as one choice or suggestion group, never both. */
class DraftRenderOnceTest {
    private val planner = LogPlanner(FoodMatcher(FoodCatalog.fixtures))

    private fun rowTexts(draft: DraftView) = draft.rows.map {
        when (it) {
            is DraftRow.Entry -> it.resolution.raw
            is DraftRow.Unresolved -> it.text
        }
    }

    @Test fun aPartialMatchIsOnlyTheChoiceGroupNotAlsoANotInYourFoodsRow() {
        // "кисело млеко" matches Домашно млеко partly ("млеко"): one choice group, no Not-in-your-foods row.
        val draft = planner.draft("кисело млеко")
        assertTrue(draft.choices.isNotEmpty())
        assertFalse(rowTexts(draft).contains("кисело млеко"))
    }

    @Test fun aCommittedPartialMatchIsOnlyTheNotInYourFoodsRow() {
        val draft = planner.draft("кисело млеко", typing = false)
        assertEquals(listOf("кисело млеко"), rowTexts(draft))
        assertTrue(draft.choices.isEmpty())
    }

    @Test fun aBareAmbiguousWordIsAnUncountedSuggestionNotASelectedChoice() {
        // "milk" names Milk 2.8% and Milk 3.2%. While typed it is not logged, so nothing looks selected or counted.
        val draft = planner.draft("milk")
        val ids = draft.suggestions.map { it.food.id }
        assertTrue(ids.toString(), "milk_28" in ids && "milk_32" in ids)
        assertFalse(draft.preselected)
        assertFalse(draft.choiceHint)
        assertTrue(draft.rows.isEmpty())
        assertTrue(draft.logged.isEmpty())
    }
}
