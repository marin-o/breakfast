package com.bosa.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A part with one clean candidate is an entry row, never a single-chip choice; a choice needs at least two candidates or a weak match. */
class DraftRowTest {
    private val planner = LogPlanner(FoodMatcher(TestFoods.fixtures))
    private val homeMilk = TestFoods.fixtures.first { it.id == "custom_home_milk" }

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
        val draft = only.draft("кисело млеко")
        // A partial match is the choice group alone: no separate Not-in-your-foods row for the same text.
        assertEquals(1, draft.choices.size)
        assertFalse(draft.preselected)
        assertTrue(draft.unresolved.isEmpty())
        assertTrue(draft.logged.isEmpty())
    }

    @Test fun aLeadingArticleIsNotPartOfTheChoiceLabel() {
        assertEquals("bananna", planner.draft("a bananna").activePhrase)
        assertEquals("egg", planner.draft("an egg").activePhrase)
        assertEquals("kiselo mleko", planner.draft("kiselo mleko").activePhrase)
    }

    @Test fun aMultiCandidateChoiceStaysAChoiceWithItsTopPreselected() {
        // With a quantity the typed part is logged, so several candidates are a choice with the top preselected and counted.
        // A bare "milk" is different: it is not logged until chosen, so it is a suggestion (see DraftRenderOnceTest).
        val draft = planner.draft("100 ml milk")
        assertTrue(draft.choices.size > 1)
        assertTrue(draft.preselected)
        assertTrue(draft.previews.isEmpty())
        assertTrue(draft.logged.isNotEmpty())
    }
}
