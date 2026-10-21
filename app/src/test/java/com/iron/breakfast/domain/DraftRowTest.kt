package com.iron.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A part with one clean candidate is an entry row, never a single-chip choice; a choice needs at least two candidates or a weak match. */
class DraftRowTest {
    private val planner = LogPlanner(FoodMatcher(FoodCatalog.fixtures))
    private val homeMilk = FoodCatalog.fixtures.first { it.id == "custom_home_milk" }

    @Test fun aCleanSingleCandidateAfterATrailingQuantityIsAnEntryRow() {
        val draft = planner.draft("kiselo mleko 200g 1 kg rolled oats")
        val oats = draft.previews.single { it.food.id == "rolled_oats" }
        assertEquals(1000.0, oats.amount, 0.0)
        assertEquals(MeasureUnit.GRAM, oats.unit)
        assertTrue(draft.choices.isEmpty())
        assertFalse(draft.preselected)
        assertTrue(draft.logged.any { it.food.id == "rolled_oats" })
    }

    @Test fun aTypoSoleCandidateIsAnEntryRowWithoutTheArticle() {
        val draft = planner.draft("two eggs and a bananna")
        val banana = draft.previews.single { it.food.id == "banana" }
        assertEquals(1.0, banana.amount, 0.0)
        assertEquals(MeasureUnit.PIECE, banana.unit)
        assertTrue(draft.choices.isEmpty())
        assertEquals(planner.plan("banana").entries.single().calculation.nutrition.calories, banana.calculation.nutrition.calories, 1e-9)
        assertEquals("bananna", draft.activePhrase)
    }

    @Test fun aSingleCandidatePartialMatchIsAChoiceNotPreselectedAndNotCounted() {
        val only = LogPlanner(FoodMatcher(listOf(homeMilk)))
        val draft = only.draft("kiselo mleko")
        assertEquals(1, draft.choices.size)
        assertFalse(draft.preselected)
        assertEquals(listOf("kiselo mleko"), draft.unresolved)
        assertTrue(draft.logged.isEmpty())
    }

    @Test fun aLeadingArticleIsNotPartOfTheChoiceLabel() {
        assertEquals("bananna", planner.draft("a bananna").activePhrase)
        assertEquals("egg", planner.draft("an egg").activePhrase)
        assertEquals("kiselo mleko", planner.draft("kiselo mleko").activePhrase)
    }

    @Test fun aMultiCandidateChoiceStaysAChoiceWithItsTopPreselected() {
        val draft = planner.draft("milk")
        assertTrue(draft.choices.size > 1)
        assertTrue(draft.preselected)
        assertTrue(draft.previews.isEmpty())
    }
}
