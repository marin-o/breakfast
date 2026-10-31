package com.iron.breakfast.domain

import com.iron.breakfast.ui.components.CurvePoint
import com.iron.breakfast.ui.components.curveLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileTest {
    @Test fun aWeightIsEmptyOrFrom1To500WithEitherDecimalMark() {
        listOf("", "  ", "82.5", "82,5", "1", "500").forEach { assertTrue(weightTextValid(it), it) }
        listOf("0", "-1", "abc", "1.2.3", "0.04", "0.5", "500.5", "8000").forEach { assertFalse(weightTextValid(it), it) }
    }

    @Test fun aMacroTargetIsEmptyOrFrom1To5000() {
        listOf("", "1", "120,5", "5000").forEach { assertTrue(macroTargetTextValid(it), it) }
        listOf("0", "0.04", "5000.5", "8000", "abc").forEach { assertFalse(macroTargetTextValid(it), it) }
    }

    @Test fun aDraftWithABadNumberHasNoValues() {
        assertNull(profileOf(ProfileDraft(current = "0"), ProfileValues()))
        assertNull(profileOf(ProfileDraft(fat = "x"), ProfileValues()))
    }

    @Test fun anEmptyDraftIsAnEmptyProfile() {
        assertEquals(ProfileValues(), profileOf(ProfileDraft(), ProfileValues()))
    }

    @Test fun anUneditedFieldKeepsItsExactNumber() {
        val saved = ProfileValues(currentWeight = 82.123456)
        assertEquals(saved, profileOf(ProfileDraft.of(saved), saved))
    }

    @Test fun typedNumbersAreRead() {
        val values = profileOf(ProfileDraft(name = " Ana ", protein = "120", current = "82,5"), ProfileValues())
        assertEquals(ProfileValues("Ana", proteinTarget = 120.0, currentWeight = 82.5), values)
    }

    @Test fun aPointComesFromAChangedWeightWithTheTickOn() {
        val before = ProfileValues(currentWeight = 80.0)
        val after = ProfileValues(currentWeight = 79.0)
        assertEquals(79.0, curveWeightOf(before, after, addToCurve = true, hasPointToday = true))
        assertNull(curveWeightOf(before, after, addToCurve = false, hasPointToday = true))
        assertNull(curveWeightOf(before, before, addToCurve = true, hasPointToday = true))
        assertNull(curveWeightOf(before, ProfileValues(), addToCurve = true, hasPointToday = false))
    }

    @Test fun theSameWeightMakesAPointOnADayWithoutOne() {
        val same = ProfileValues(currentWeight = 80.0)
        assertEquals(80.0, curveWeightOf(same, same, addToCurve = true, hasPointToday = false))
        assertNull(curveWeightOf(same, same, addToCurve = false, hasPointToday = false))
    }

    @Test fun theTickIsOfferedForAWeightChangedOrWithNoPointToday() {
        val saved = ProfileValues(currentWeight = 80.0)
        assertFalse(curveOffered(ProfileDraft.of(saved), saved, hasPointToday = true))
        assertTrue(curveOffered(ProfileDraft.of(saved), saved, hasPointToday = false))
        assertTrue(curveOffered(ProfileDraft(current = "79"), saved, hasPointToday = true))
        assertFalse(curveOffered(ProfileDraft(current = ""), saved, hasPointToday = false))
    }

    @Test fun pointsListNewestFirst() {
        val points = listOf(WeightPoint("2026-10-01", 80.0, 1L), WeightPoint("2026-10-03", 79.0, 2L), WeightPoint("2026-10-02", 81.0, 3L))
        assertEquals(listOf("2026-10-03", "2026-10-02", "2026-10-01"), newestFirst(points).map { it.dayKey })
    }

    @Test fun curveXIsDaysApart() {
        assertEquals(2.0, curveX("2026-10-03")!! - curveX("2026-10-01")!!)
        assertNull(curveX("not a day"))
    }

    @Test fun noPointsLayNothingEvenWithATarget() {
        val layout = curveLayout(emptyList(), 70.0, 100f, 100f, 0f)
        assertTrue(layout.points.isEmpty())
        assertNull(layout.targetY)
    }

    @Test fun onePointSitsInTheMiddle() {
        val point = curveLayout(listOf(CurvePoint(5.0, 80.0)), null, 200f, 100f, 10f).points.single()
        assertEquals(100f, point.x)
        assertEquals(50f, point.y)
    }

    @Test fun xIsProportionalToTimeAndLargerYIsHigher() {
        val points = curveLayout(listOf(CurvePoint(0.0, 80.0), CurvePoint(1.0, 90.0), CurvePoint(4.0, 85.0)), null, 120f, 120f, 10f).points
        assertEquals(listOf(10f, 35f, 110f), points.map { it.x })
        assertEquals(listOf(110f, 10f, 60f), points.map { it.y })
    }

    @Test fun theRangeIncludesTheTarget() {
        val layout = curveLayout(listOf(CurvePoint(0.0, 80.0), CurvePoint(1.0, 90.0)), 70.0, 100f, 100f, 0f)
        assertEquals(0f, layout.points[1].y)
        assertEquals(50f, layout.points[0].y)
        assertEquals(100f, layout.targetY)
    }

    @Test fun aFlatRangeSitsInTheMiddle() {
        val layout = curveLayout(listOf(CurvePoint(0.0, 80.0), CurvePoint(1.0, 80.0)), 80.0, 100f, 100f, 0f)
        assertEquals(listOf(50f, 50f), layout.points.map { it.y })
        assertEquals(50f, layout.targetY)
    }
}
