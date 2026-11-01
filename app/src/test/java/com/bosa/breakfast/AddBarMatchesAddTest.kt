package com.bosa.breakfast

import com.bosa.breakfast.domain.DraftInvariantTest
import com.bosa.breakfast.domain.TestFoods
import com.bosa.breakfast.domain.FoodMatcher
import com.bosa.breakfast.domain.LogPlanner
import com.bosa.breakfast.domain.MeasureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the add bar totals is exactly what Add saves (MOB-015). While the last word is typed, Add saves its best-ranked
 * food, so the bar counts it too, and that food shows selected. Checked for every input and every prefix of it, so
 * each state a draft passes through while it is typed is covered. With "Count what I'm typing" off (B9) the bar leaves
 * out the word being typed; Add finishes the draft first, so the finished draft's bar is exactly what Add saves.
 */
class AddBarMatchesAddTest {
    private data class Saved(val foodId: String, val amount: Double, val unit: MeasureUnit, val kcal: Double)

    private fun fixturesPlanner(countTyping: Boolean = true) = LogPlanner(FoodMatcher(TestFoods.fixtures), countTyping = countTyping)
    private val fixtures = fixturesPlanner()
    private val extra = listOf("2 eggs, milk", "1 ban", "2 eggs, ban", "milk", "ban", "дом")

    /** Each input with the planner the Log page reads it with, and a factory for an independent planner. */
    private fun casesFor(countTyping: Boolean): List<Triple<LogPlanner, () -> LogPlanner, String>> {
        val fixtures = fixturesPlanner(countTyping)
        val messy = DraftInvariantTest.messyPlanner(countTyping)
        return (LogRegressionTest().inputs + extra).map { Triple(fixtures, { fixturesPlanner(countTyping) }, it) } +
            (DraftInvariantTest.MESSY + extra).map { Triple(messy, { DraftInvariantTest.messyPlanner(countTyping) }, it) }
    }
    private val cases = casesFor(countTyping = true)
    private val casesCountOff = casesFor(countTyping = false)

    private fun saved(entries: List<com.bosa.breakfast.domain.SegmentResolution>) =
        entries.map { Saved(it.food.id, it.amount, it.unit, it.calculation.nutrition.calories) }

    /** Every input as it is typed, letter by letter, then whole. */
    private fun typedStates(input: String) = (1..input.length).map { input.substring(0, it) }

    @Test fun theBarCountsExactlyTheEntriesAddSaves() = assertBarMatchesAdd(cases, typingStates = listOf(true, false))

    /** Count off: Add finishes the draft, so the committed draft's bar is what Add saves. */
    @Test fun withCountOffTheFinishedDraftCountsExactlyTheEntriesAddSaves() = assertBarMatchesAdd(casesCountOff, typingStates = listOf(false))

    private fun assertBarMatchesAdd(cases: List<Triple<LogPlanner, () -> LogPlanner, String>>, typingStates: List<Boolean>) {
        val failures = cases.flatMap { (planner, fresh, input) ->
            typedStates(input).flatMap { text ->
                typingStates.mapNotNull { typing ->
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
    @Test fun whatIsCountedShowsSelectedAndNothingElseDoes() = assertCountedShowsSelected(cases)

    @Test fun withCountOffWhatIsCountedShowsSelectedAndNothingElseDoes() = assertCountedShowsSelected(casesCountOff)

    /** With Count off and no pins, nothing being typed is ever counted, and every finished part still is. */
    @Test fun withCountOffAWordBeingTypedIsNeverCounted() {
        val failures = casesCountOff.flatMap { (planner, fresh, input) ->
            typedStates(input).mapNotNull { text ->
                val draft = planner.draft(text, typing = true)
                val finished = fresh().plan(text, typing = false).entries.size
                when {
                    draft.counted != null -> "[$text] ${draft.counted.food.id} is counted while it is typed"
                    planner.heldPart(text, typing = true) == null && draft.logged.size != finished -> "[$text] nothing held but the bar differs from Done"
                    else -> null
                }
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    private fun assertCountedShowsSelected(cases: List<Triple<LogPlanner, () -> LogPlanner, String>>) {
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
