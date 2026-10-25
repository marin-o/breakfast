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

    @Test fun aBareAmbiguousWordIsACountedSuggestionWithTheSavedFoodSelected() {
        // "milk" names Milk 2.8% and Milk 3.2%. Add saves the best-ranked one, so the bar counts it and it leads the
        // suggestions, selected; the group stays a suggestion group (no "which one?").
        val draft = planner.draft("milk")
        val saved = planner.plan("milk").entries.single()
        val ids = draft.suggestions.map { it.food.id }
        assertTrue(ids.toString(), "milk_28" in ids && "milk_32" in ids)
        assertEquals(saved.food.id, ids.first())
        assertTrue(draft.preselected)
        assertFalse(draft.choiceHint)
        assertTrue(draft.rows.isEmpty())
        assertEquals(listOf(saved.food.id), draft.logged.map { it.food.id })
    }
}
