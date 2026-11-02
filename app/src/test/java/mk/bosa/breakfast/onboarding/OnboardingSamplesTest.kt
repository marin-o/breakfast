package mk.bosa.breakfast.onboarding

import mk.bosa.breakfast.domain.DraftRow
import mk.bosa.breakfast.domain.TestFoods
import mk.bosa.breakfast.domain.FoodMatcher
import mk.bosa.breakfast.domain.LogDraftView
import mk.bosa.breakfast.domain.LogPlanner
import mk.bosa.breakfast.domain.logDraftViewOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the tutorial shows is what the app shows for that text over its catalogue: the real planner, the fixed samples. */
class OnboardingSamplesTest {
    private fun real(text: String, committed: Boolean) = logDraftViewOf(LogPlanner(FoodMatcher(TestFoods.fixtures)), text, committed, ready = true)

    private fun rowsOf(view: LogDraftView) = view.rows.map { row ->
        when (row) {
            is DraftRow.Entry -> listOf(row.resolution.food.name, row.resolution.amount, row.resolution.unit, row.resolution.label, row.resolution.calculation.approximate, row.resolution.calculation.nutrition.calories)
            is DraftRow.Unresolved -> listOf(row.text)
        }
    }

    @Test fun theTypedLineShowsWhatTheAppShows() {
        val sample = sampleLineView()
        assertTrue(sample.rows.isNotEmpty())
        assertEquals(rowsOf(real(SAMPLE_LINE, committed = true)), rowsOf(sample))
    }

    @Test fun theGreenChipIsWhatTheAppMakesGreen() {
        val sample = samplePartialView()
        val real = real(SAMPLE_PARTIAL, committed = false)
        assertTrue(sample.firstIsAddPick)
        assertEquals(real.firstIsAddPick, sample.firstIsAddPick)
        assertEquals(real.activePhrase, sample.activePhrase)
        assertEquals(real.choiceHint, sample.choiceHint)
        assertEquals(real.preselected, sample.preselected)
        assertEquals(real.choices.first().food.name, sample.choices.single().food.name)
        assertEquals(real.choiceCalculations.first().nutrition.calories, sample.choiceCalculations.single().nutrition.calories, 0.0)
        assertEquals(rowsOf(real), rowsOf(sample))
    }

    @Test fun theUnknownPartIsOneTheAppDoesNotKnow() {
        val sample = sampleUnknownView()
        assertTrue(sample.rows.isNotEmpty())
        assertEquals(rowsOf(real(SAMPLE_UNKNOWN, committed = true)), rowsOf(sample))
    }
}
