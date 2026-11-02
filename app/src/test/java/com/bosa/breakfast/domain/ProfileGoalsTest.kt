package com.bosa.breakfast.domain

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val UTC = ZoneOffset.UTC
private const val NOON_MS = 43_200_000L
private const val TODAY = "2026-11-02"
private val TODAY_DATE = LocalDate.parse(TODAY)
private const val DELTA = 0.01

private fun daysAgo(n: Int): String = TODAY_DATE.minusDays(n.toLong()).toString()

/** A weigh-in at noon on [day]. */
private fun weighIn(day: String, kg: Double) = WeightPoint(day, kg, LocalDate.parse(day).toEpochDay() * 86_400_000L + NOON_MS)

/** A weigh-in every day for [days] days to today, starting at [startKg] and moving by [perDay]. */
private fun line(days: Int, startKg: Double, perDay: Double) =
    (0 until days).map { i -> weighIn(daysAgo(days - 1 - i), startKg + perDay * i) }

private fun meal(day: String, kcal: Double, protein: Double? = null, carbs: Double? = null, fat: Double? = null) = MealEntry(
    id = "$day-$kcal", foodId = "f", foodName = "f", enteredAmount = 1.0, enteredUnit = MeasureUnit.GRAM, resolvedBaseAmount = 1.0,
    nutritionSnapshot = NutritionValues(kcal, protein, carbs, fat), conversionProvenance = "", approximateConversion = false, savedAtEpochMs = 0L, dayKey = day,
)

private val MAN = BurnBody(30, 180.0, 80.0, Sex.MALE, Activity.LOW_ACTIVE)

internal class DailyBurnTest {
    // 581.47 - 10.83 x 30 + 8.30 x 180 + 14.94 x 80 = 581.47 - 324.90 + 1494 + 1195.20
    @Test fun theMaleEquation() = assertEquals(2945.77, formulaBurn(MAN), DELTA)

    // 584.90 - 7.01 x 30 + 5.72 x 165 + 11.71 x 60 = 584.90 - 210.30 + 943.80 + 702.60
    @Test fun theFemaleEquation() = assertEquals(2021.0, formulaBurn(BurnBody(30, 165.0, 60.0, Sex.FEMALE, Activity.INACTIVE)), DELTA)

    // -517.88 - 324.9 + 2809.8 + 1528.8; 511.83 - 210.3 + 1632.6 + 1004.8
    @Test fun notSayingAndNoSexAreTheAverage() {
        val very = BurnBody(30, 180.0, 80.0, Sex.NOT_SAYING, Activity.VERY_ACTIVE)
        assertEquals((3495.82 + 2938.93) / 2, formulaBurn(very), DELTA)
        assertEquals(formulaBurn(very), formulaBurn(very.copy(sex = null)), DELTA)
    }

    @Test fun nothingLoggedOrWeighedIsTheFormula() {
        val burn = dailyBurn(MAN, emptyList(), emptyList(), TODAY, UTC)
        assertEquals(DailyBurn(formulaBurn(MAN), formulaBurn(MAN), 0.0, false), burn)
    }

    // slope -0.05 kg/day, SE 0, EI 2000: adaptive = 2000 + 442 = 2442. sigmaA^2 = 100^2. w = 339^2 / (339^2 + 100^2) = 114921 / 124921.
    @Test fun aPerfectLineLearns() {
        val entries = (1..27).map { meal(daysAgo(it), 2000.0) }
        val burn = dailyBurn(MAN, line(28, 82.0, -0.05), entries, TODAY, UTC)
        assertEquals(0.91995, burn.learned, 0.0001)
        assertEquals(2945.77 + 0.91995 * (2442 - 2945.77), burn.kcal, 0.1)
        assertFalse(burn.loggingMismatch)
    }

    @Test fun tooFewWeighInsOrASpanTooShortOrTooFewDaysLearnNothing() {
        val entries = (1..27).map { meal(daysAgo(it), 2000.0) }
        // 13 weigh-ins
        assertEquals(0.0, dailyBurn(MAN, line(28, 82.0, -0.05).takeLast(13), entries, TODAY, UTC).learned)
        // 21 weigh-ins span 20 days
        assertEquals(0.0, dailyBurn(MAN, line(21, 82.0, -0.05), entries, TODAY, UTC).learned)
        // 22 weigh-ins span 21 days: enough
        assertTrue(dailyBurn(MAN, line(22, 82.0, -0.05), entries, TODAY, UTC).learned > 0)
        // 20 eligible days
        assertEquals(0.0, dailyBurn(MAN, line(28, 82.0, -0.05), entries.take(20), TODAY, UTC).learned)
    }

    /** A low day under half the median is left out, so it neither counts nor lowers the mean. */
    @Test fun aDayUnderHalfTheMedianIsNotEligible() {
        val entries = (1..26).map { meal(daysAgo(it), 2000.0) } + meal(daysAgo(27), 900.0)
        assertEquals(dailyBurn(MAN, line(28, 82.0, -0.05), (1..26).map { meal(daysAgo(it), 2000.0) }, TODAY, UTC), dailyBurn(MAN, line(28, 82.0, -0.05), entries, TODAY, UTC))
    }

    /** Today is still being logged. */
    @Test fun todayDoesNotCountAsIntake() {
        val entries = (1..27).map { meal(daysAgo(it), 2000.0) }
        assertEquals(dailyBurn(MAN, line(28, 82.0, -0.05), entries, TODAY, UTC), dailyBurn(MAN, line(28, 82.0, -0.05), entries + meal(TODAY, 300.0), TODAY, UTC))
    }

    // adaptive 1000 + 0 under 0.75 x 2945.77 = 2209.
    @Test fun anAdaptiveOutOfRangeIsAMismatchAndCountsForNothing() {
        val burn = dailyBurn(MAN, line(28, 80.0, 0.0), (1..27).map { meal(daysAgo(it), 1000.0) }, TODAY, UTC)
        assertTrue(burn.loggingMismatch)
        assertEquals(0.0, burn.learned)
        assertEquals(formulaBurn(MAN), burn.kcal)
    }

    /** Several weigh-ins on identical days and times: no variance, so no slope and no division by zero. */
    @Test fun identicalMomentsGiveNoSlope() {
        assertNull(weightSlope(List(5) { weighIn(TODAY, 80.0 + it) }, UTC))
        assertNull(weightSlope(emptyList(), UTC))
        assertNull(weightSlope(line(2, 80.0, 0.1), UTC))
    }

    // x 0,1,2; y 80,81,83: mean 81.33, Sxx 2, Sxy 3, slope 1.5; residuals 1/6,-1/3,1/6: SSE 1/6; SE sqrt((1/6)/1/2) = sqrt(1/12).
    @Test fun theRegressionByHand() {
        val slope = weightSlope(listOf(weighIn(daysAgo(2), 80.0), weighIn(daysAgo(1), 81.0), weighIn(TODAY, 83.0)), UTC)!!
        assertEquals(1.5, slope.kgPerDay, 0.0001)
        assertEquals(0.2887, slope.standardError, 0.0001)
    }

    @Test fun aBurnIsNeverBelowZero() = assertEquals(0.0, formulaBurn(BurnBody(120, 50.0, 1.0, Sex.MALE, Activity.VERY_ACTIVE)))

    @Test fun theMedianOfOddAndEven() {
        assertNull(median(emptyList()))
        assertEquals(2.0, median(listOf(3.0, 1.0, 2.0)))
        assertEquals(2.5, median(listOf(4.0, 1.0, 2.0, 3.0)))
    }
}

internal class GoalKcalTest {
    // 0.5 x 8840 / 7 = 631.43
    @Test fun loseGainAndMaintain() {
        assertEquals(GoalKcal(1869), goalKcal(2500.0, Goal.LOSE, 0.5, Sex.FEMALE))
        assertEquals(GoalKcal(3131), goalKcal(2500.0, Goal.GAIN, 0.5, Sex.FEMALE))
        assertEquals(GoalKcal(2500), goalKcal(2500.0, Goal.MAINTAIN, null, Sex.FEMALE))
        assertNull(goalKcal(2500.0, Goal.LOSE, null, Sex.FEMALE))
    }

    // (2000 - 1500) x 7 / 8840 = 0.396 kg a week.
    @Test fun theLowestSaysTheRealPace() {
        val result = goalKcal(2000.0, Goal.LOSE, 1.0, null)!!
        assertEquals(1500, result.kcal)
        assertEquals(1500, result.lowest)
        assertEquals(0.396, result.paceAtLowest!!, 0.001)
        assertEquals(GoalKcal(1369), goalKcal(2000.0, Goal.LOSE, 0.5, Sex.FEMALE))
        assertEquals(1200, goalKcal(1500.0, Goal.LOSE, 1.0, Sex.FEMALE)!!.kcal)
        assertEquals(1500, goalKcal(2000.0, Goal.LOSE, 1.0, Sex.MALE)!!.kcal)
        assertEquals(1500, goalKcal(2000.0, Goal.LOSE, 1.0, Sex.NOT_SAYING)!!.kcal)
    }

    @Test fun aBurnUnderTheLowestHasNoNegativePace() {
        assertEquals(GoalKcal(1500, 1500, null), goalKcal(1400.0, Goal.MAINTAIN, null, Sex.MALE))
        assertEquals(GoalKcal(1500, 1500, null), goalKcal(1400.0, Goal.GAIN, 0.01, Sex.MALE))
        assertEquals(GoalKcal(1500, 1500, null), goalKcal(1400.0, Goal.LOSE, 0.5, Sex.MALE))
    }

    @Test fun aPaceAboveOnePercentIsFast() {
        assertTrue(paceTooFast(Goal.LOSE, 1.0, 80.0))
        assertTrue(paceTooFast(Goal.GAIN, 1.0, 80.0))
        assertFalse(paceTooFast(Goal.LOSE, 0.8, 80.0))
        assertFalse(paceTooFast(Goal.MAINTAIN, 1.0, 80.0))
        assertFalse(paceTooFast(Goal.LOSE, null, 80.0))
        assertFalse(paceTooFast(Goal.LOSE, 1.0, null))
    }

    // 53 kg at 170 cm: BMI 18.34. 54 kg: 18.69.
    @Test fun loseWarnsOnBmiCurrentOrTargetAndOnAge() {
        assertEquals(setOf(LoseWarning.UNDERWEIGHT), loseWarnings(Goal.LOSE, 170.0, 30, 53.0, null))
        assertEquals(setOf(LoseWarning.UNDERWEIGHT), loseWarnings(Goal.LOSE, 170.0, 30, 60.0, 53.0))
        assertEquals(emptySet(), loseWarnings(Goal.LOSE, 170.0, 30, 54.0, 54.0))
        assertEquals(setOf(LoseWarning.UNDER_18), loseWarnings(Goal.LOSE, null, 17, 60.0, null))
        assertEquals(emptySet(), loseWarnings(Goal.LOSE, null, 18, 60.0, null))
        assertEquals(emptySet(), loseWarnings(Goal.GAIN, 170.0, 15, 40.0, null))
        assertEquals(emptySet(), loseWarnings(null, 170.0, 15, 40.0, null))
    }

    @Test fun theTargetOnADayIsTheLatestAtOrBeforeIt() {
        val history = listOf(TargetRow("2026-10-10", 1900.0, null, null, null), TargetRow("2026-10-01", 2000.0, null, null, null))
        assertEquals(2000.0, targetOn("2026-09-01", history)?.kcal)
        assertEquals(2000.0, targetOn("2026-10-09", history)?.kcal)
        assertEquals(1900.0, targetOn("2026-10-10", history)?.kcal)
        assertEquals(1900.0, targetOn("2026-12-01", history)?.kcal)
        assertNull(targetOn("2026-10-10", emptyList()))
    }
}

internal class LimitedKcalTest {
    private fun row(daysBefore: Int, kcal: Double) = TargetRow(daysAgo(daysBefore), kcal, null, null, null)

    @Test fun aMoveIsAtMost100() {
        assertEquals(1900.0, limitedKcal(1500, 2000.0, listOf(row(30, 2000.0)), TODAY))
        assertEquals(2100.0, limitedKcal(2500, 2000.0, listOf(row(30, 2000.0)), TODAY))
        assertEquals(1950.0, limitedKcal(1950, 2000.0, listOf(row(30, 2000.0)), TODAY))
    }

    @Test fun anEarlierMoveInTheWeekCounts() {
        // 2000 was in effect until 3 days ago, 1950 since. Down only to 1900, 100 under the highest seen.
        assertEquals(1900.0, limitedKcal(1500, 1950.0, listOf(row(30, 2000.0), row(3, 1950.0)), TODAY))
        // Up only to 2050, 100 over the lowest seen.
        assertEquals(2050.0, limitedKcal(2500, 1950.0, listOf(row(30, 2000.0), row(3, 1950.0)), TODAY))
    }

    @Test fun aMoveSevenDaysLaterIsFree() {
        // The 2000 ended 6 days ago: the window starts at that row, 1900.
        assertEquals(1800.0, limitedKcal(1500, 1900.0, listOf(row(30, 2000.0), row(6, 1900.0)), TODAY))
        // One day later, 2000 is still in the window.
        assertEquals(1900.0, limitedKcal(1500, 1900.0, listOf(row(30, 2000.0), row(5, 1900.0)), TODAY))
    }

    @Test fun aBigStepTheUserSavedHoldsTheTargetForTheWeek() {
        assertEquals(1500.0, limitedKcal(1400, 1500.0, listOf(row(30, 2000.0), row(2, 1500.0)), TODAY))
    }

    /** The value before a first move is not recorded yet, so a move now would let the week's range reach 200. */
    @Test fun noHistoryMovesFromCurrent() {
        assertEquals(2000.0, limitedKcal(1500, 2000.0, emptyList(), TODAY))
        assertEquals(2000.0, limitedKcal(1500, 2000.0, listOf(TargetRow(daysAgo(30), null, 100.0, null, null)), TODAY))
    }

    /** 2000 until yesterday, a goal saved at 1850 since: the band's low of 1900 must not lift it away from the goal. */
    @Test fun aTargetOutsideTheBandNeverMovesAwayFromWhatIsWanted() {
        val rows = listOf(row(30, 2000.0), row(1, 1850.0))
        assertEquals(1850.0, limitedKcal(1850, 1850.0, rows, TODAY))
        assertEquals(1850.0, limitedKcal(1800, 1850.0, rows, TODAY))
        assertEquals(1950.0, limitedKcal(2100, 1850.0, rows, TODAY))
    }

    @Test fun aMoveStaysInTheRangeTheAppKeeps() {
        assertEquals(20000.0, limitedKcal(30000, 19950.0, listOf(row(30, 19950.0)), TODAY))
        assertEquals(1.0, limitedKcal(-500, 50.0, listOf(row(30, 50.0)), TODAY))
    }

    @Test fun aRowDatedLaterDoesNotCount() {
        val later = TargetRow(TODAY_DATE.plusDays(5).toString(), 1000.0, null, null, null)
        assertEquals(1900.0, limitedKcal(1500, 2000.0, listOf(row(30, 2000.0), later), TODAY))
        // Only a later row: no day up to today has a row, and the window's start must not borrow it.
        assertEquals(2000.0, limitedKcal(1500, 2000.0, listOf(later), TODAY))
    }

    @Test fun aStartingRowIsNeededOnlyWithNoKcalBehindTheTargets() {
        val values = ProfileValues(kcalTarget = 2000.0)
        assertTrue(needsStartingRow(values, emptyList(), TODAY))
        assertTrue(needsStartingRow(values, listOf(TargetRow(TODAY_DATE.plusDays(1).toString(), 2000.0, null, null, null)), TODAY))
        assertTrue(needsStartingRow(values, listOf(TargetRow(daysAgo(3), null, 100.0, null, null)), TODAY))
        assertFalse(needsStartingRow(values, listOf(row(3, 2000.0)), TODAY))
        assertFalse(needsStartingRow(ProfileValues(), emptyList(), TODAY))
        assertFalse(needsStartingRow(ProfileValues(proteinTarget = 100.0), listOf(TargetRow(daysAgo(3), null, 100.0, null, null)), TODAY))
    }
}

/** What the daily adjustment does day after day, read back as the target in effect on each day. */
internal class DailyAdjustmentSimulationTest {
    // Burn 2945.77, less 631.43: the goal gives 2314.
    private val goal = ProfileValues(
        kcalTarget = 2946.0, currentWeight = 80.0, heightCm = 180.0, age = 30, sex = Sex.MALE, activity = Activity.LOW_ACTIVE, goal = Goal.LOSE, paceKgPerWeek = 0.5,
    )
    private val start: LocalDate = LocalDate.parse("2026-01-01")

    /** [days] days from [from], with [startRows] already stored; the days' targets in effect. */
    private fun run(startValues: ProfileValues, startRows: List<TargetRow>, from: LocalDate, days: Int): List<Double> {
        var values = startValues
        var rows = startRows
        return (0 until days).map { i ->
            val day = from.plusDays(i.toLong()).toString()
            val next = dailyAdjusted(values, emptyList(), emptyList(), rows, day, UTC)
            if (next != null) values = next
            if (next != null || needsStartingRow(values, rows, day)) rows = rows + targetsOf(values, day)
            targetOn(day, rows)!!.kcal!!
        }
    }

    private fun assertEveryWeekWithin100(kcal: List<Double>) = kcal.windowed(7).forEach { week ->
        assertTrue(week.max() - week.min() <= 100.0, "$week")
    }

    @Test fun fromNoHistory() {
        val kcal = run(goal, emptyList(), start, 90)
        assertEquals(2946.0, kcal[0])
        assertEquals(2846.0, kcal[1])
        assertEveryWeekWithin100(kcal)
        assertEquals(2314.0, kcal.last())
    }

    /** A step the user saved is theirs; from the day it took effect, every week the adjustment touches stays within 100. */
    @Test fun fromABigManualStep() {
        val stepDay = start.plusDays(10)
        val rows = listOf(TargetRow(start.toString(), 2946.0, null, null, null), TargetRow(stepDay.toString(), 1500.0, null, null, null))
        val kcal = run(goal.copy(kcalTarget = 1500.0), rows, stepDay, 90)
        assertEquals(List(6) { 1500.0 }, kcal.take(6))
        assertEquals(1600.0, kcal[6])
        assertEveryWeekWithin100(kcal)
        assertEquals(2314.0, kcal.last())
    }
}

internal class DailyAdjustedTest {
    private val goalValues = ProfileValues(
        kcalTarget = 2946.0, currentWeight = 80.0, heightCm = 180.0, age = 30, sex = Sex.MALE, activity = Activity.LOW_ACTIVE, goal = Goal.LOSE, paceKgPerWeek = 0.5,
    )
    private val old = listOf(TargetRow(daysAgo(30), 2946.0, null, null, null))

    // Burn 2945.77, less 631.43 is 2314: the first day moves 100.
    @Test fun aSavedGoalMovesAt100() {
        val next = dailyAdjusted(goalValues, emptyList(), emptyList(), old, TODAY, UTC)!!
        assertEquals(2846.0, next.kcalTarget)
    }

    @Test fun nothingWithoutAGoalAKcalABodyOrWhenTrackingOnly() {
        assertNull(dailyAdjusted(goalValues.copy(goal = null), emptyList(), emptyList(), old, TODAY, UTC))
        assertNull(dailyAdjusted(goalValues.copy(kcalTarget = null), emptyList(), emptyList(), old, TODAY, UTC))
        assertNull(dailyAdjusted(goalValues.copy(age = null), emptyList(), emptyList(), old, TODAY, UTC))
        assertNull(dailyAdjusted(goalValues.copy(trackingOnly = true), emptyList(), emptyList(), old, TODAY, UTC))
        assertNull(dailyAdjusted(goalValues.copy(paceKgPerWeek = null), emptyList(), emptyList(), old, TODAY, UTC))
    }

    @Test fun aRowTodayMeansItAlreadyRan() =
        assertNull(dailyAdjusted(goalValues, emptyList(), emptyList(), old + TargetRow(TODAY, 2946.0, null, null, null), TODAY, UTC))

    @Test fun onTargetIsNoChange() = assertNull(dailyAdjusted(goalValues.copy(kcalTarget = 2314.0), emptyList(), emptyList(), listOf(TargetRow(daysAgo(30), 2314.0, null, null, null)), TODAY, UTC))

    @Test fun macrosFollowTheMove() {
        val next = dailyAdjusted(goalValues.copy(proteinTarget = 200.0, proteinPercent = 25.0, fatTarget = 50.0), emptyList(), emptyList(), old, TODAY, UTC)!!
        // 25% of 2846 / 4 = 177.875
        assertEquals(178.0, next.proteinTarget)
        assertEquals(25.0, next.proteinPercent)
        assertEquals(50.0, next.fatTarget)
    }
}

internal class MacroBalanceTest {
    private val unset = MacroShare(0.0, 0.0, MacroEntered.UNSET)
    private val noMacros = mapOf(Macro.PROTEIN to unset, Macro.CARBS to unset, Macro.FAT to unset)

    // 30% of 2000 = 600 kcal = 150 g. 1400 left, 50/50: carbs 700 / 4 = 175, fat 700 / 9 = 77.8.
    @Test fun aPercentWithBothOthersZeroSharesEvenly() {
        val out = rebalanceMacros(noMacros, 2000.0, Macro.PROTEIN, 30.0, MacroEntered.PERCENT)
        assertEquals(150.0, out.getValue(Macro.PROTEIN).grams)
        assertEquals(30.0, out.getValue(Macro.PROTEIN).percent)
        assertEquals(175.0, out.getValue(Macro.CARBS).grams)
        assertEquals(78.0, out.getValue(Macro.FAT).grams)
        assertEquals(MacroEntered.FOLLOWS, out.getValue(Macro.FAT).entered)
    }

    // Protein 100 g = 400 kcal. 1600 left. Carbs 200 g (800) and fat 40 g (360): carbs get 800/1160, 1103.4 kcal = 275.9 g, so 276. Fat: (1600 - 1104) / 9 = 55.1.
    @Test fun gramsKeepTheOthersRatio() {
        val current = mapOf(
            Macro.PROTEIN to MacroShare(50.0, 10.0, MacroEntered.FOLLOWS),
            Macro.CARBS to MacroShare(200.0, 40.0, MacroEntered.FOLLOWS),
            Macro.FAT to MacroShare(40.0, 18.0, MacroEntered.FOLLOWS),
        )
        val out = rebalanceMacros(current, 2000.0, Macro.PROTEIN, 100.0, MacroEntered.GRAMS)
        assertEquals(100.0, out.getValue(Macro.PROTEIN).grams)
        assertEquals(20.0, out.getValue(Macro.PROTEIN).percent, 0.001)
        assertEquals(276.0, out.getValue(Macro.CARBS).grams)
        assertEquals(55.0, out.getValue(Macro.FAT).grams)
    }

    @Test fun theEditedFatKeepsWhatWasTypedAndCarbsTakeTheRemainder() {
        val out = rebalanceMacros(noMacros, 2000.0, Macro.FAT, 50.5, MacroEntered.GRAMS)
        assertEquals(50.5, out.getValue(Macro.FAT).grams)
        // 2000 - 454.5 = 1545.5, half 772.75 = 193 g protein; carbs (1545.5 - 772) / 4 = 193.4
        assertEquals(193.0, out.getValue(Macro.PROTEIN).grams)
        assertEquals(193.0, out.getValue(Macro.CARBS).grams)
    }

    @Test fun capsAt100Percent() {
        val byPercent = rebalanceMacros(noMacros, 2000.0, Macro.PROTEIN, 150.0, MacroEntered.PERCENT)
        assertEquals(100.0, byPercent.getValue(Macro.PROTEIN).percent)
        assertEquals(500.0, byPercent.getValue(Macro.PROTEIN).grams)
        assertEquals(0.0, byPercent.getValue(Macro.FAT).grams)
        val byGrams = rebalanceMacros(noMacros, 2000.0, Macro.FAT, 900.0, MacroEntered.GRAMS)
        assertEquals(100.0, byGrams.getValue(Macro.FAT).percent, 0.001)
        assertEquals(2000.0 / 9, byGrams.getValue(Macro.FAT).grams, 0.001)
        assertEquals(0.0, byGrams.getValue(Macro.CARBS).grams)
    }

    @Test fun zeroPercentGivesTheOthersEverything() {
        val out = rebalanceMacros(noMacros, 1800.0, Macro.PROTEIN, 0.0, MacroEntered.PERCENT)
        assertEquals(0.0, out.getValue(Macro.PROTEIN).grams)
        assertEquals(225.0, out.getValue(Macro.CARBS).grams)
        assertEquals(100.0, out.getValue(Macro.FAT).grams)
    }

    @Test fun oldTargetsInGramsFollowAfterTheFirstEdit() {
        val old = mapOf(
            Macro.PROTEIN to MacroShare(150.0, 30.0, MacroEntered.GRAMS),
            Macro.CARBS to MacroShare(200.0, 40.0, MacroEntered.GRAMS),
            Macro.FAT to MacroShare(67.0, 30.0, MacroEntered.GRAMS),
        )
        val out = rebalanceMacros(old, 2000.0, Macro.PROTEIN, 100.0, MacroEntered.GRAMS)
        assertEquals(MacroEntered.GRAMS, out.getValue(Macro.PROTEIN).entered)
        assertEquals(MacroEntered.FOLLOWS, out.getValue(Macro.CARBS).entered)
    }

    // Protein 30% stays: 2400 x .3 / 4 = 180. Carbs 100 g stay: 400 kcal. Fat follows: 2400 - 720 - 400 = 1280 / 9 = 142.2.
    @Test fun atANewKcalEachKeepsHowItWasEntered() {
        val shares = mapOf(
            Macro.PROTEIN to MacroShare(150.0, 30.0, MacroEntered.PERCENT),
            Macro.CARBS to MacroShare(100.0, 20.0, MacroEntered.GRAMS),
            Macro.FAT to MacroShare(111.0, 50.0, MacroEntered.FOLLOWS),
        )
        val out = macrosAtKcal(shares, 2400.0)
        assertEquals(180.0, out.getValue(Macro.PROTEIN).grams)
        assertEquals(30.0, out.getValue(Macro.PROTEIN).percent)
        assertEquals(100.0, out.getValue(Macro.CARBS).grams)
        assertEquals(16.67, out.getValue(Macro.CARBS).percent, DELTA)
        assertEquals(142.0, out.getValue(Macro.FAT).grams)
    }

    @Test fun atANewKcalNothingFollowsWithoutAFollower() {
        val old = mapOf(
            Macro.PROTEIN to MacroShare(150.0, 30.0, MacroEntered.GRAMS),
            Macro.CARBS to MacroShare(200.0, 40.0, MacroEntered.GRAMS),
            Macro.FAT to MacroShare(67.0, 30.0, MacroEntered.GRAMS),
        )
        val out = macrosAtKcal(old, 2500.0)
        assertEquals(listOf(150.0, 200.0, 67.0), Macro.entries.map { out.getValue(it).grams })
    }

    @Test fun atANewKcalUnsetStaysUnsetAndTooLittleLeavesZero() {
        val partly = noMacros + (Macro.PROTEIN to MacroShare(120.0, 24.0, MacroEntered.GRAMS))
        assertEquals(listOf(120.0, 0.0, 0.0), Macro.entries.map { macrosAtKcal(partly, 2000.0).getValue(it).grams })
        val over = mapOf(
            Macro.PROTEIN to MacroShare(500.0, 100.0, MacroEntered.GRAMS),
            Macro.CARBS to MacroShare(50.0, 10.0, MacroEntered.FOLLOWS),
            Macro.FAT to MacroShare(50.0, 20.0, MacroEntered.FOLLOWS),
        )
        assertEquals(listOf(500.0, 0.0, 0.0), Macro.entries.map { macrosAtKcal(over, 1000.0).getValue(it).grams })
    }

    /** Pushed to nothing by a kcal too low, followers come back when it rises: equally, their ratio being gone. */
    @Test fun aFollowerAtZeroKeepsFollowing() {
        val values = ProfileValues(kcalTarget = 2000.0, proteinTarget = 500.0, carbsTarget = 0.5, fatTarget = 0.5, followingMacros = setOf(Macro.CARBS, Macro.FAT))
        val low = values.withKcalFollowing(1000.0)
        assertNull(low.carbsTarget)
        assertNull(low.fatTarget)
        assertEquals(setOf(Macro.CARBS, Macro.FAT), low.followingMacros)
        // 3000 less 2000 from protein: 500 kcal each, 125 g carbs and 55.6 g fat.
        val back = low.withKcalFollowing(3000.0)
        assertEquals(125.0, back.carbsTarget)
        assertEquals(56.0, back.fatTarget)
    }

    /** Rounded to nothing beside another follower, it counts as under a gram: it takes a little, not its old share. */
    @Test fun aFollowerRoundedAwayBesideAnotherComesBack() {
        val shares = mapOf(
            Macro.PROTEIN to MacroShare(100.0, 20.0, MacroEntered.GRAMS),
            Macro.CARBS to MacroShare(200.0, 40.0, MacroEntered.FOLLOWS),
            Macro.FAT to MacroShare(0.0, 0.0, MacroEntered.FOLLOWS),
        )
        val out = macrosAtKcal(shares, 2000.0)
        assertTrue(out.getValue(Macro.FAT).grams > 0)
        assertTrue(out.getValue(Macro.CARBS).grams > out.getValue(Macro.FAT).grams)
    }

    @Test fun aMacroClearedStaysOutOfALaterEdit() {
        val shares = mapOf(
            Macro.PROTEIN to MacroShare(100.0, 20.0, MacroEntered.GRAMS),
            Macro.CARBS to MacroShare(200.0, 40.0, MacroEntered.FOLLOWS),
            Macro.FAT to unset,
        )
        assertEquals(MacroEntered.UNSET, rebalanceMacros(shares, 2000.0, Macro.PROTEIN, 150.0, MacroEntered.GRAMS).getValue(Macro.FAT).entered)
    }

    @Test fun zeroMacrosAtZeroKcalDoNotDivideByZero() {
        val out = macrosAtKcal(noMacros, 0.0)
        assertEquals(listOf(0.0, 0.0, 0.0), Macro.entries.map { out.getValue(it).percent })
    }
}

internal class MacroDraftTest {
    @Test fun typingAPercentFillsTheOthersAndKeepsWhatWasTyped() {
        val out = ProfileDraft(kcal = "2000").withMacroTyped(Macro.PROTEIN, "30", byPercent = true)
        assertEquals("30", out.proteinPercent)
        assertEquals("150", out.protein)
        assertEquals("175", out.carbs)
        assertEquals("78", out.fat)
        assertEquals(setOf(Macro.CARBS, Macro.FAT), out.followingMacros)
        assertEquals("35", out.percentShown(Macro.CARBS))
    }

    @Test fun aHundredPercentLeavesTheOthersNotSetSoTheDraftSaves() {
        val out = ProfileDraft(kcal = "2000").withMacroTyped(Macro.PROTEIN, "150", byPercent = true)
        assertEquals("100", out.proteinPercent)
        assertEquals("", out.carbs)
        assertEquals("", out.fat)
        assertEquals(500.0, profileOf(out, ProfileValues())?.proteinTarget)
        assertNull(profileOf(out, ProfileValues())?.fatTarget)
    }

    @Test fun withoutAValidKcalOnlyTheFieldChanges() {
        val out = ProfileDraft(carbs = "200").withMacroTyped(Macro.PROTEIN, "100", byPercent = false)
        assertEquals("100", out.protein)
        assertEquals("200", out.carbs)
        assertEquals("", out.percentShown(Macro.PROTEIN))
    }

    @Test fun aPartlyTypedNumberDoesNotRebalance() {
        val draft = ProfileDraft(kcal = "2000", carbs = "200")
        assertEquals("200", draft.withMacroTyped(Macro.PROTEIN, "", byPercent = false).carbs)
        assertEquals("200", draft.withMacroTyped(Macro.PROTEIN, "x", byPercent = false).carbs)
    }

    @Test fun anOldProfileIsNotRebalancedUntilAnEdit() {
        val saved = ProfileValues(kcalTarget = 2000.0, proteinTarget = 120.0, carbsTarget = 100.0, fatTarget = 10.0)
        val draft = ProfileDraft.of(saved)
        assertEquals(saved, profileOf(draft, saved))
        assertEquals(draft.copy(kcal = "2100"), draft.withDailyKcal("2100"))
    }

    @Test fun aSavedDraftKeepsItsPercentAndFollowers() {
        val typed = ProfileDraft(kcal = "2000").withMacroTyped(Macro.PROTEIN, "30", byPercent = true)
        val saved = profileOf(typed, ProfileValues())!!
        assertEquals(30.0, saved.proteinPercent)
        assertEquals(setOf(Macro.CARBS, Macro.FAT), saved.followingMacros)
        assertEquals(typed, ProfileDraft.of(saved))
    }

    @Test fun aPercentOutOfRangeDoesNotSave() = assertNull(profileOf(ProfileDraft(fatPercent = "101"), ProfileValues()))

    @Test fun ageAndHeightRanges() {
        assertNull(profileOf(ProfileDraft(age = "0"), ProfileValues()))
        assertNull(profileOf(ProfileDraft(age = "121"), ProfileValues()))
        assertNull(profileOf(ProfileDraft(height = "49"), ProfileValues()))
        assertEquals(120, profileOf(ProfileDraft(age = "120", height = "300"), ProfileValues())?.age)
    }

    @Test fun choosingAGoalFillsKcalButNotWithoutAPace() {
        val draft = ProfileDraft(sex = Sex.FEMALE)
        assertEquals("", draft.withGoal(Goal.LOSE, 2500.0).kcal)
        assertEquals("2500", draft.withGoal(Goal.MAINTAIN, 2500.0).kcal)
        assertEquals("1869", draft.withGoal(Goal.LOSE, 2500.0).withPace("0.5", false, 2500.0).kcal)
        assertEquals(Goal.LOSE, draft.withGoal(Goal.LOSE, 2500.0).withPace("0.5", false, 2500.0).goal)
    }

    @Test fun theBurnBodyNeedsEveryValidValue() {
        val whole = ProfileDraft(height = "180", age = "30", current = "80", activity = Activity.ACTIVE)
        assertEquals(BurnBody(30, 180.0, 80.0, null, Activity.ACTIVE), whole.burnBody())
        assertNull(whole.copy(height = "20").burnBody())
        assertNull(whole.copy(age = "").burnBody())
        assertNull(whole.copy(current = "x").burnBody())
        assertNull(whole.copy(activity = null).burnBody())
    }
}

internal class WeightProjectionTest {
    private val target = 78.0

    @Test fun noTargetOrReachedIsNothing() {
        assertEquals(WeightProjection.Nothing, weightProjection(line(28, 82.0, -0.1), null, null, TODAY, UTC))
        assertEquals(WeightProjection.Nothing, weightProjection(listOf(weighIn(TODAY, 77.0)), target, Goal.LOSE, TODAY, UTC))
        assertEquals(WeightProjection.Nothing, weightProjection(listOf(weighIn(TODAY, 79.0)), target, Goal.GAIN, TODAY, UTC))
        assertEquals(WeightProjection.Nothing, weightProjection(listOf(weighIn(TODAY, 78.0)), target, null, TODAY, UTC))
    }

    @Test fun withNoDirectionTheEarliestWeighInSaysWhenTheTargetIsCrossed() {
        val down = listOf(weighIn(daysAgo(10), 80.0), weighIn(daysAgo(5), 79.0), weighIn(TODAY, 77.0))
        assertEquals(WeightProjection.Nothing, weightProjection(down, target, null, TODAY, UTC))
        assertEquals(WeightProjection.Nothing, weightProjection(down, target, Goal.MAINTAIN, TODAY, UTC))
        val up = listOf(weighIn(daysAgo(10), 76.0), weighIn(daysAgo(5), 77.0), weighIn(TODAY, 79.0))
        assertEquals(WeightProjection.Nothing, weightProjection(up, target, null, TODAY, UTC))
        assertIs<WeightProjection.Dated>(weightProjection(line(10, 80.0, -0.1), target, null, TODAY, UTC))
        // Started on it and moved away: not crossed.
        assertTrue(weightProjection(listOf(weighIn(daysAgo(10), 78.0), weighIn(daysAgo(5), 79.0), weighIn(TODAY, 80.0)), target, null, TODAY, UTC) != WeightProjection.Nothing)
    }

    @Test fun noPointsAndFewDaysAreNotEnoughData() {
        assertEquals(WeightProjection.NotEnoughData(14), weightProjection(emptyList(), target, null, TODAY, UTC))
        assertEquals(WeightProjection.NotEnoughData(12), weightProjection(line(2, 82.0, -0.1), target, null, TODAY, UTC))
    }

    /** Three on one day, identical or not, are one weigh-in day and no variance. */
    @Test fun identicalDaysAreNotEnoughData() =
        assertEquals(WeightProjection.NotEnoughData(13), weightProjection(List(3) { weighIn(TODAY, 80.0) }, target, null, TODAY, UTC))

    @Test fun weighInsOutOfTheWindowDoNotCount() {
        val old = (30..50).map { weighIn(daysAgo(it), 90.0) }
        assertEquals(WeightProjection.NotEnoughData(14), weightProjection(old, target, null, TODAY, UTC))
    }

    // Slope -0.1, SE 0; the line ends at 82 - 2.7 = 79.3, so D = 1.3. Earliest 1.3 / 0.1 = 13 days. Latest 343 ln(34.3 / 33) = 13.25, so 13.
    @Test fun aSteadyLossHasDates() {
        val result = assertIs<WeightProjection.Dated>(weightProjection(line(28, 82.0, -0.1), target, Goal.LOSE, TODAY, UTC))
        assertEquals(0.7, result.kgPerWeek, 0.001)
        assertEquals(78.0, result.targetKg)
        assertEquals(TODAY_DATE.plusDays(13), result.earliest)
        assertEquals(TODAY_DATE.plusDays(13), result.latest)
        assertNull(result.comeBackInDays)
    }

    @Test fun datesUnder14DaysCarryTheComeBackLine() {
        val result = assertIs<WeightProjection.Dated>(weightProjection(line(10, 80.0, -0.1), target, Goal.LOSE, TODAY, UTC))
        assertEquals(4, result.comeBackInDays)
    }

    @Test fun movingAwayIsNoDate() {
        assertEquals(WeightProjection.NoDate, weightProjection(line(28, 80.0, 0.1), target, Goal.LOSE, TODAY, UTC))
    }

    /** 0.04 kg a week on a clean line: the latest date is defined only if near, so a target 0.01 kg off gets past that test. */
    @Test fun underTheSlowestIsNoDate() {
        val slow = line(28, 80.0, -0.04 / 7)
        assertEquals(WeightProjection.NoDate, weightProjection(slow, slow.last().kg - 0.01, Goal.LOSE, TODAY, UTC))
    }

    @Test fun aFlatLineIsNoDateWithEnoughDaysAndNotEnoughDataWithFew() {
        assertEquals(WeightProjection.NoDate, weightProjection(line(14, 80.0, 0.0), target, Goal.LOSE, TODAY, UTC))
        assertEquals(WeightProjection.NotEnoughData(1), weightProjection(line(13, 80.0, 0.0), target, Goal.LOSE, TODAY, UTC))
    }

    // 0.1 kg/day but tau x r = 34.3 < D = 50.
    @Test fun farBeyondWhatThePaceCanReachIsNoDate() {
        assertEquals(WeightProjection.NoDate, weightProjection(line(28, 150.0, -0.1), 100.0, Goal.LOSE, TODAY, UTC))
    }
}

internal class WeightSeriesTest {
    @Test fun theDefaultRange() {
        assertEquals(WeightRange.ALL, defaultWeightRange(emptyList(), TODAY))
        assertEquals(WeightRange.ALL, defaultWeightRange(listOf(weighIn(daysAgo(30), 80.0)), TODAY))
        assertEquals(WeightRange.THREE_MONTHS, defaultWeightRange(listOf(weighIn(daysAgo(120), 80.0)), TODAY))
    }

    @Test fun noPointsDrawNothing() = assertEquals(WeightSeries(emptyList(), emptyList()), weightSeries(emptyList(), WeightRange.ALL, TODAY, UTC))

    // 80, then 0.1 x 90 + 0.9 x 80 = 81, then 0.1 x 70 + 0.9 x 81 = 79.9.
    @Test fun theTrendByHand() {
        val series = weightSeries(listOf(weighIn(daysAgo(2), 80.0), weighIn(daysAgo(1), 90.0), weighIn(TODAY, 70.0)), WeightRange.THREE_MONTHS, TODAY, UTC)
        assertEquals(listOf(80.0, 90.0, 70.0), series.dots.map { it.kg })
        assertEquals(listOf(80.0, 81.0, 79.9), series.trend.single().map { it.kg }.map { Math.round(it * 100) / 100.0 })
    }

    @Test fun theTrendBreaksOverAGapOfMoreThan14Days() {
        val at14 = weightSeries(listOf(weighIn(daysAgo(14), 80.0), weighIn(TODAY, 70.0)), WeightRange.THREE_MONTHS, TODAY, UTC)
        assertEquals(1, at14.trend.size)
        val at15 = weightSeries(listOf(weighIn(daysAgo(15), 80.0), weighIn(TODAY, 70.0)), WeightRange.THREE_MONTHS, TODAY, UTC)
        assertEquals(listOf(listOf(80.0), listOf(70.0)), at15.trend.map { line -> line.map { it.kg } })
    }

    /** The range cuts what is drawn, not the smoothing: the first point shown keeps what came before it. */
    @Test fun aRangeDoesNotRestartTheTrend() {
        val series = weightSeries(listOf(weighIn(daysAgo(40), 80.0), weighIn(daysAgo(30), 90.0), weighIn(TODAY, 90.0)), WeightRange.ONE_MONTH, TODAY, UTC)
        assertEquals(listOf(90.0, 90.0), series.dots.map { it.kg })
        assertEquals(81.0, series.trend.flatten().first().kg, DELTA)
    }

    // 2026-11-02 is a Monday; so 2026-10-26 starts the week before. Weeks: (80 + 82) / 2 and 70.
    @Test fun oneYearAndAllAreWeeklyAverages() {
        val points = listOf(weighIn("2026-10-26", 80.0), weighIn("2026-11-01", 82.0), weighIn("2026-11-02", 70.0))
        val series = weightSeries(points, WeightRange.ONE_YEAR, TODAY, UTC)
        assertEquals(listOf(81.0, 70.0), series.dots.map { it.kg })
        assertEquals(2, series.trend.single().size)
        assertEquals(3, weightSeries(points, WeightRange.SIX_MONTHS, TODAY, UTC).dots.size)
        assertEquals(2, weightSeries(points, WeightRange.ALL, TODAY, UTC).dots.size)
    }
}

internal class NutritionWeekTest {
    private val monday: LocalDate = LocalDate.parse("2026-10-26")

    @Test fun theWeekStartsMondayAndStopsAtTheCurrentOne() {
        assertEquals(monday, weekStartOf("2026-11-01"))
        assertEquals(LocalDate.parse("2026-11-02"), weekStartOf("2026-11-02"))
        assertNull(weekStartOf("nope"))
        assertTrue(hasNextWeek(monday, TODAY))
        assertFalse(hasNextWeek(LocalDate.parse("2026-11-02"), TODAY))
    }

    @Test fun anEmptyWeekHasEmptySlotsAndNoAverages() {
        val week = nutritionWeek(monday, emptyList(), emptyList())
        assertEquals(7, week.days.size)
        assertEquals(LocalDate.parse("2026-11-01"), week.end)
        assertEquals(0, week.loggedDays)
        assertNull(week.averageKcal)
        assertNull(week.averageFat)
        assertTrue(week.days.none { it.logged })
    }

    // 10 g protein, 10 g carbs, 10 g fat = 40 + 40 + 90 = 170. An entry of 340 kcal leaves 170 soft.
    @Test fun segmentsAreTheKnownGramsAndTheRestIsSoft() {
        val bar = nutritionWeek(monday, listOf(meal("2026-10-26", 340.0, 10.0, 10.0, 10.0)), emptyList()).days.first()
        assertEquals(40.0, bar.proteinKcal, DELTA)
        assertEquals(40.0, bar.carbsKcal, DELTA)
        assertEquals(90.0, bar.fatKcal, DELTA)
        assertEquals(170.0, bar.unknownKcal, DELTA)
        assertEquals(340.0, bar.kcal)
    }

    @Test fun aPartlyKnownEntryDrawsItsGramsAndTheRestSoft() {
        val bar = nutritionWeek(monday, listOf(meal("2026-10-26", 500.0, 5.0)), emptyList()).days.first()
        assertEquals(20.0, bar.proteinKcal, DELTA)
        assertEquals(480.0, bar.unknownKcal, DELTA)
    }

    // 170 kcal of macros in a 100 kcal entry: scaled by 100/170.
    @Test fun macrosOverTheEntrysKcalScaleDownToIt() {
        val bar = nutritionWeek(monday, listOf(meal("2026-10-26", 100.0, 10.0, 10.0, 10.0)), emptyList()).days.first()
        assertEquals(23.53, bar.proteinKcal, DELTA)
        assertEquals(23.53, bar.carbsKcal, DELTA)
        assertEquals(52.94, bar.fatKcal, DELTA)
        assertEquals(0.0, bar.unknownKcal)
    }

    @Test fun noOrZeroMacrosAreOneSoftSegmentAndAddUp() {
        val bar = nutritionWeek(monday, listOf(meal("2026-10-26", 100.0), meal("2026-10-26", 50.0, 0.0, 0.0, 0.0), meal("2026-10-26", 40.0, 10.0)), emptyList()).days.first()
        assertEquals(150.0, bar.unknownKcal)
        assertEquals(40.0, bar.proteinKcal, DELTA)
        assertEquals(190.0, bar.kcal)
    }

    @Test fun aZeroKcalDayIsStillLogged() {
        val week = nutritionWeek(monday, listOf(meal("2026-10-26", 0.0)), emptyList())
        assertTrue(week.days.first().logged)
        assertEquals(1, week.loggedDays)
    }

    @Test fun averagesCountLoggedDaysOnlyAndEntriesOutsideAreLeftOut() {
        val week = nutritionWeek(monday, listOf(meal("2026-10-26", 1000.0, 50.0), meal("2026-10-28", 2000.0, 100.0, 30.0), meal("2026-11-02", 9000.0)), emptyList())
        assertEquals(2, week.loggedDays)
        assertEquals(1500.0, week.averageKcal)
        assertEquals(75.0, week.averageProtein)
        assertEquals(15.0, week.averageCarbs)
        assertEquals(0.0, week.averageFat)
    }

    @Test fun eachDayHasItsTarget() {
        val history = listOf(TargetRow("2026-10-28", 1800.0, null, null, null), TargetRow("2026-10-01", 2000.0, null, null, null))
        val days = nutritionWeek(monday, emptyList(), history).days
        assertEquals(2000.0, days[1].targetKcal)
        assertEquals(1800.0, days[2].targetKcal)
        assertEquals(1800.0, days[6].targetKcal)
        assertNull(nutritionWeek(monday, emptyList(), emptyList()).days[0].targetKcal)
    }

    @Test fun oldGramTargetsEditedByPercentLetTheOtherTwoFollow() {
        val edited = ProfileDraft(kcal = "2000", protein = "150", carbs = "200", fat = "67").withMacroTyped(Macro.PROTEIN, "30", byPercent = true)
        assertEquals(setOf(Macro.CARBS, Macro.FAT), edited.followingMacros)
        val more = edited.withDailyKcal("3000")
        assertEquals("225", more.protein)
        assertTrue(positiveAmountOf(more.carbs)!! > 200.0, more.carbs)
        assertTrue(positiveAmountOf(more.fat)!! > 67.0, more.fat)
    }

    @Test fun aPercentMacroEditedBesideGramMacrosLeavesThemGrams() {
        val draft = ProfileDraft(kcal = "2000", protein = "150", proteinPercent = "30", carbs = "200", fat = "67")
        assertEquals(emptySet(), draft.withMacroTyped(Macro.PROTEIN, "40", byPercent = true).followingMacros)
    }

    @Test fun aPercentMacroEditedInGramsLeavesTheOthersGrams() {
        val draft = ProfileDraft(kcal = "2000", protein = "150", proteinPercent = "30", carbs = "200", fat = "67")
        assertEquals(emptySet(), draft.withMacroTyped(Macro.PROTEIN, "100", byPercent = false).followingMacros)
    }

    @Test fun trackingOnlyHidesTheTargetsFromMealsAndKeepsThem() {
        val saved = ProfileValues(kcalTarget = 2000.0, proteinTarget = 100.0, carbsTarget = 200.0, fatTarget = 50.0, trackingOnly = true)
        val shown = saved.targetsShown()
        assertNull(shown.kcalTarget)
        assertNull(shown.proteinTarget)
        assertNull(shown.carbsTarget)
        assertNull(shown.fatTarget)
        assertEquals(2000.0, saved.kcalTarget)
        val tracking = saved.copy(trackingOnly = false)
        assertEquals(tracking, tracking.targetsShown())
    }

    @Test fun theLowestLineNeedsARealPace() {
        assertEquals("1500 kcal is the lowest here. That's about 0.4 kg a week.", lowestLine(GoalKcal(1500, 1500, 0.42)))
        assertNull(lowestLine(GoalKcal(1500, 1500, null)))
        assertNull(lowestLine(GoalKcal(2100)))
    }

    private val projectionDay: LocalDate = LocalDate.parse("2026-11-02")
    private fun dated(earliest: String, latest: String, comeBack: Int? = null, kg: Double = 0.42, target: Double = 78.0) =
        WeightProjection.Dated(kg, target, LocalDate.parse(earliest), LocalDate.parse(latest), comeBack)

    @Test fun projectionBetweenTwoMonths() {
        assertEquals(
            listOf("At about 0.4 kg a week, you reach 78 kg between February and April."),
            projectionLines(dated("2027-02-10", "2027-04-20"), projectionDay),
        )
    }

    @Test fun projectionInOneMonth() {
        assertEquals(listOf("At about 0.4 kg a week, you reach 78.5 kg in March."), projectionLines(dated("2027-03-01", "2027-03-30", target = 78.5), projectionDay))
    }

    @Test fun aYearFollowsOnlyAMonthPastTheNextTwelve() {
        assertEquals(
            listOf("At about 1 kg a week, you reach 78 kg between October and November 2027."),
            projectionLines(dated("2027-10-10", "2027-11-02", kg = 1.04), projectionDay),
        )
        assertEquals(
            listOf("At about 0.4 kg a week, you reach 78 kg in January 2028."),
            projectionLines(dated("2028-01-02", "2028-01-20"), projectionDay),
        )
    }

    @Test fun underFourteenDaysTheComeBackLineFollows() {
        assertEquals(
            listOf("At about 0.4 kg a week, you reach 78 kg in March.", "Not enough data yet. Come back in 9 days."),
            projectionLines(dated("2027-03-01", "2027-03-30", comeBack = 9), projectionDay),
        )
    }

    @Test fun theOtherProjections() {
        assertEquals(emptyList(), projectionLines(WeightProjection.Nothing, projectionDay))
        assertEquals(listOf("Not enough data yet. Come back in 9 days."), projectionLines(WeightProjection.NotEnoughData(9), projectionDay))
        assertEquals(listOf("Not enough data yet. Come back in 1 day."), projectionLines(WeightProjection.NotEnoughData(1), projectionDay))
        assertEquals(listOf("No date yet. Your weight isn't moving toward your target."), projectionLines(WeightProjection.NoDate, projectionDay))
    }

    @Test fun aPaceIsWrittenPlainly() {
        assertEquals("0.4", kgAWeekText(0.42))
        assertEquals("1", kgAWeekText(1.04))
        assertEquals("0.06", kgAWeekText(0.06))
        assertEquals("0.1", kgAWeekText(0.095))
        assertEquals("10", kgAWeekText(10.0))
    }
}
