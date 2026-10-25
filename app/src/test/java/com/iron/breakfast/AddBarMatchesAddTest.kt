package com.iron.breakfast

import com.iron.breakfast.domain.DraftInvariantTest
import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.FoodMatcher
import com.iron.breakfast.domain.LogPlanner
import com.iron.breakfast.domain.MeasureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the add bar totals is exactly what Add saves (MOB-015). While the last word is typed, Add saves its best-ranked
 * food, so the bar counts it too, and that food shows selected. Checked for every input and every prefix of it, so
 * each state a draft passes through while it is typed is covered.
 */
class AddBarMatchesAddTest {
    private data class Saved(val foodId: String, val amount: Double, val unit: MeasureUnit, val kcal: Double)

    private fun fixturesPlanner() = LogPlanner(FoodMatcher(FoodCatalog.fixtures))
    private val fixtures = fixturesPlanner()
    private val messy = DraftInvariantTest.messyPlanner()
    private val extra = listOf("2 eggs, milk", "1 ban", "2 eggs, ban", "milk", "ban", "дом")

    /** Each input with the planner the Log page reads it with, and a factory for an independent planner. */
    private val cases: List<Triple<LogPlanner, () -> LogPlanner, String>> =
        (LogRegressionTest().inputs + extra).map { Triple(fixtures, ::fixturesPlanner, it) } +
            (DraftInvariantTest.MESSY + extra).map { Triple(messy, DraftInvariantTest::messyPlanner, it) }

    private fun saved(entries: List<com.iron.breakfast.domain.SegmentResolution>) =
        entries.map { Saved(it.food.id, it.amount, it.unit, it.calculation.nutrition.calories) }

    /** Every input as it is typed, letter by letter, then whole. */
    private fun typedStates(input: String) = (1..input.length).map { input.substring(0, it) }

    @Test fun theBarCountsExactlyTheEntriesAddSaves() {
        val failures = cases.flatMap { (planner, fresh, input) ->
            typedStates(input).flatMap { text ->
                listOf(true, false).mapNotNull { typing ->
                    val logged = planner.draft(text, typing).logged
                    // Add's save() plans the committed text with a planner of its own: a fresh one, so no cached
                    // resolution is shared with the draft. Compared by value.
                    val saved = fresh().plan(text).entries
                    val barKcal = logged.sumOf { it.calculation.nutrition.calories }
                    // save() snapshots each entry's calculation; this is the total it stores.
                    val savedKcal = saved.sumOf { it.calculation.nutrition.calories }
                    when {
                        saved(logged) != saved(saved) ->
                            "[$text typing=$typing] bar ${logged.map { it.food.id }}, Add ${saved.map { it.food.id }}"
                        barKcal != savedKcal -> "[$text typing=$typing] bar $barKcal kcal, Add $savedKcal kcal"
                        else -> null
                    }
                }
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    /** The part being typed is counted exactly when it is shown as counted: an entry row or the selected first choice. */
    @Test fun whatIsCountedShowsSelectedAndNothingElseDoes() {
        val failures = cases.flatMap { (planner, _, input) ->
            typedStates(input).mapNotNull { text ->
                val draft = planner.draft(text, typing = true)
                val counted = draft.counted
                when {
                    counted != null && draft.logged.lastOrNull() !== counted -> "[$text] counted ${counted.food.id} is not the last saved entry"
                    counted != null && draft.activeRow !== counted && !draft.preselected -> "[$text] ${counted.food.id} is counted but not shown selected"
                    counted == null && draft.preselected -> "[$text] a choice shows selected but nothing is counted"
                    draft.preselected && draft.choices.first().food.id != counted?.food?.id -> "[$text] the selected choice is not the saved food"
                    else -> null
                }
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test fun twoEggsMilkCountsTheMilkAddSaves() {
        val draft = fixtures.draft("2 eggs, milk")
        val saved = fixtures.plan("2 eggs, milk").entries
        assertEquals(listOf("egg", saved.last().food.id), draft.logged.map { it.food.id })
        assertTrue(saved.last().food.id.startsWith("milk"))
        assertTrue(draft.preselected)
        assertEquals(saved.last().food.id, draft.choices.first().food.id)
    }

    @Test fun twoEggsBanCountsTheBananaAddSaves() {
        val draft = fixtures.draft("2 eggs, ban")
        assertEquals(fixtures.plan("2 eggs, ban").entries.map { it.food.id }, draft.logged.map { it.food.id })
        assertEquals("banana", draft.logged.last().food.id)
        assertEquals("banana", draft.choices.first().food.id)
        assertTrue(draft.preselected)
    }
}
