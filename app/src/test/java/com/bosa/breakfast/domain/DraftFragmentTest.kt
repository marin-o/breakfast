package com.bosa.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row order follows the typed order; the word being typed gets suggestions, not Not-in-your-foods. */
class DraftFragmentTest {
    private val buckwheat = FoodRecord(
        id = "buckwheat", name = "Buckwheat", aliases = emptyList(), category = "Grain", origin = FoodOrigin.CUSTOM,
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(340.0, 12.0, 70.0, 3.0)),
        defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM,
    )
    private val planner = LogPlanner(FoodMatcher(TestFoods.fixtures + buckwheat))

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

    @Test fun twoEggsBuWhileTypingGivesSuggestionsAndCountsOnlyTheEggs() {
        val draft = planner.draft("2 eggs bu")
        assertEquals(listOf("2 eggs"), rowTexts(draft))
        assertEquals(listOf("egg"), draft.logged.map { it.food.id })
        assertEquals(2.0, draft.logged.single().amount, 0.0)
        assertTrue(draft.unresolved.isEmpty())
        val suggested = draft.suggestions.map { it.food.id }
        assertTrue(suggested.toString(), "buckwheat" in suggested)
        assertTrue(suggested.toString(), suggested.any { it.startsWith("peanut") || it.contains("butter") })
        assertEquals("bu", draft.activePhrase)
        assertFalse(draft.preselected)
        assertFalse(draft.choiceHint)
    }

    @Test fun twoEggsZzqWhileTypingGivesTheEggRowOnly() {
        val draft = planner.draft("2 eggs zzq")
        assertEquals(listOf("2 eggs"), rowTexts(draft))
        assertTrue(draft.suggestions.isEmpty())
        assertTrue(draft.unresolved.isEmpty())
        assertEquals(listOf("egg"), draft.logged.map { it.food.id })
    }

    @Test fun aTrailingSpaceOrDoneMakesZzqNotInYourFoods() {
        val spaced = planner.draft("2 eggs zzq ")
        assertEquals(listOf("zzq"), spaced.unresolved)
        assertEquals(listOf("egg"), spaced.logged.map { it.food.id })
        val done = planner.draft("2 eggs zzq", typing = false)
        assertEquals(listOf("zzq"), done.unresolved)
        assertTrue(done.suggestions.isEmpty())
        assertEquals(listOf("egg"), done.logged.map { it.food.id })
    }

    @Test fun cyrillicDomGivesTheHomemadeMilkSuggestionAndCountsWhatAddSaves() {
        // "дом" starts Домашно млеко and Домашен ајвар. Add saves the best-ranked one: the bar counts it, and it leads
        // the suggestions, selected.
        val draft = planner.draft("дом")
        val saved = planner.plan("дом").entries.single()
        assertTrue(draft.suggestions.map { it.food.id }.toString(), "custom_home_milk" in draft.suggestions.map { it.food.id })
        assertEquals(saved.food.id, draft.suggestions.first().food.id)
        assertEquals(listOf(saved.food.id), draft.logged.map { it.food.id })
        assertTrue(draft.preselected)
        assertEquals("дом", draft.activePhrase)
    }

    @Test fun aFullyTypedSingleFoodStaysAnEntryWhileTyping() {
        // "1 ban" is a quantity and a name: it is still the preselected Banana row, as before.
        val draft = planner.draft("1 ban")
        assertTrue(draft.activeRow != null)
        assertEquals("banana", draft.logged.single().food.id)
        assertTrue(draft.suggestions.isEmpty())
    }

    @Test fun suggestionsFollowTheTypedFragmentNotTheWholeLine() {
        val draft = planner.draft("1 tsp pb, bu")
        assertEquals(listOf("1 tsp pb"), rowTexts(draft))
        assertEquals("bu", draft.activePhrase)
        assertTrue(draft.suggestions.isNotEmpty())
    }
}

class DraftTrailingSpaceTest {
    private val planner = LogPlanner(FoodMatcher(TestFoods.fixtures))

    /** A space after a part finishes it: the part is no longer typed, so a clean match is an entry row, not a choice. */
    @Test fun aTrailingSpaceFinishesTheLastPart() {
        val draft = planner.draft("milk ")
        assertTrue(draft.previews.isNotEmpty())
        assertTrue(draft.choices.isEmpty())
        assertEquals(draft.previews.map { it.food.id }, draft.logged.map { it.food.id })
    }
}
