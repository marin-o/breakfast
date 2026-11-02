package com.bosa.breakfast.app

import androidx.lifecycle.SavedStateHandle
import androidx.compose.runtime.snapshots.Snapshot
import com.bosa.breakfast.domain.Activity
import com.bosa.breakfast.domain.Goal
import com.bosa.breakfast.domain.Macro
import com.bosa.breakfast.domain.Sex
import com.bosa.breakfast.domain.ProfileDraft
import com.bosa.breakfast.domain.ProfileValues
import com.bosa.breakfast.domain.TargetRow
import com.bosa.breakfast.domain.WeightPoint
import com.bosa.breakfast.domain.WeightProjection
import com.bosa.breakfast.domain.WeightRange
import com.bosa.breakfast.domain.macroApplyOffered
import com.bosa.breakfast.domain.percentShown
import com.bosa.breakfast.profile.ProfilePage
import com.bosa.breakfast.profile.ProfilePrefs
import com.bosa.breakfast.profile.profilePages
import com.bosa.breakfast.profile.weightPointLabel
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal class ProfileStateTest {
    private val app = AppKit()
    private val profile get() = app.owners.profile

    private fun saveCurrent(kg: String) {
        profile.edit(profile.draft.copy(current = kg))
        profile.save()
    }

    @Test fun theIconOpensThePageOverTheBaseAndBackReturns() {
        app.surfaces.openProfile()
        assertEquals(listOf("DAILY", "PROFILE"), app.shown())
        assertTrue(app.back())
        assertEquals(listOf("DAILY"), app.shown())
    }

    @Test fun backWithAChangeAsksAndKeepEditingKeepsIt() {
        app.surfaces.openProfile()
        profile.edit(ProfileDraft(name = "Ana"))
        app.back()
        assertEquals(listOf("DAILY", "PROFILE", "DISCARD_PROFILE"), app.shown())
        profile.keepEditing()
        assertEquals(listOf("DAILY", "PROFILE"), app.shown())
        assertEquals("Ana", profile.draft.name)
    }

    @Test fun discardDropsWhatWasTyped() {
        app.surfaces.openProfile()
        profile.edit(ProfileDraft(name = "Ana"))
        app.back()
        profile.discard()
        assertEquals(listOf("DAILY"), app.shown())
        app.surfaces.openProfile()
        assertEquals("", profile.draft.name)
        assertEquals(ProfileValues(), profile.values)
    }

    @Test fun theDraftAndTheOpenPageComeBackAfterTheProcessDies() {
        val saved = SavedStateHandle()
        val first = AppKit(saved)
        first.surfaces.openProfile()
        first.owners.profile.edit(ProfileDraft(current = "80"))
        first.owners.profile.toggleAddToCurve()
        Snapshot.sendApplyNotifications()
        val second = AppKit(saved)
        assertEquals(listOf("DAILY", "PROFILE"), second.shown())
        assertEquals("80", second.owners.profile.draft.current)
        assertFalse(second.owners.profile.addToCurve)
    }

    @Test fun aClosedPageLeavesNothingToComeBack() {
        val saved = SavedStateHandle()
        val first = AppKit(saved)
        first.surfaces.openProfile()
        first.owners.profile.edit(ProfileDraft(current = "80"))
        Snapshot.sendApplyNotifications()
        first.back()
        first.owners.profile.discard()
        Snapshot.sendApplyNotifications()
        assertEquals(listOf("DAILY"), AppKit(saved).shown())
    }

    @Test fun aTabChangeClosesThePage() {
        app.surfaces.openProfile()
        app.surfaces.openRecipes()
        assertFalse(app.surfaces.profileOpen)
    }

    @Test fun aSavedWeightWithNoPointTodayIsNotAnUnsavedEdit() {
        saveCurrent("80")
        app.nowMs += DAY_MS
        app.surfaces.openProfile()
        assertTrue(profile.curveOffered)
        assertTrue(profile.canSave)
        app.back()
        assertEquals(listOf("DAILY"), app.shown())
    }

    @Test fun openingThePageSavesNothing() {
        saveCurrent("80")
        app.nowMs += DAY_MS
        app.surfaces.openProfile()
        assertEquals(1, profile.points.size)
    }

    @Test fun theSameWeightOnTwoDaysMakesTwoPoints() {
        saveCurrent("80")
        app.nowMs += DAY_MS
        profile.save()
        assertEquals(listOf("2026-10-29" to 80.0, "2026-10-28" to 80.0), profile.points.map { it.dayKey to it.kg })
    }

    @Test fun aDeletedPointCanBeAddedAgainAtTheSameNumber() {
        saveCurrent("80")
        profile.askDelete(profile.points.single())
        profile.confirmDelete()
        assertTrue(profile.curveOffered)
        assertTrue(profile.canSave)
        profile.save()
        assertEquals(80.0, profile.points.single().kg)
    }

    @Test fun withTheTickOffAndNothingChangedSaveDoesNothing() {
        saveCurrent("80")
        app.nowMs += DAY_MS
        profile.toggleAddToCurve()
        assertFalse(profile.canSave)
        profile.save()
        assertEquals(1, profile.points.size)
    }

    @Test fun theDeleteQuestionIsASurface() {
        saveCurrent("80")
        app.surfaces.openProfile()
        profile.askDelete(profile.points.single())
        assertEquals(listOf("DAILY", "PROFILE", "DELETE_WEIGHT_POINT"), app.shown())
    }

    @Test fun anEmptyProfileHasNothingToSave() {
        assertFalse(profile.canSave)
    }

    @Test fun aKcalTargetIsStoredAndABadOneCannotBeSaved() {
        profile.edit(ProfileDraft(kcal = "20001"))
        assertFalse(profile.canSave)
        profile.edit(ProfileDraft(kcal = "2100"))
        profile.save()
        assertEquals(ProfileValues(kcalTarget = 2100.0), ProfilePrefs(app.profileStore).read())
    }

    private val goalValues = ProfileValues(
        kcalTarget = 2447.5, currentWeight = 80.0, heightCm = 180.0, age = 30, sex = Sex.MALE, activity = Activity.LOW_ACTIVE, goal = Goal.LOSE, paceKgPerWeek = 0.5,
    )

    @Test fun everyNewFieldIsKeptInPrefs() {
        val values = goalValues.copy(trackingOnly = true, proteinPercent = 30.0, carbsPercent = 0.0, followingMacros = setOf(Macro.FAT))
        val prefs = ProfilePrefs(app.profileStore)
        prefs.write(values)
        assertEquals(values, prefs.read())
    }

    @Test fun aSavedTargetWritesARowAndASecondSaveThatDayReplacesIt() {
        profile.edit(ProfileDraft(kcal = "2100"))
        profile.save()
        assertEquals(listOf(2100.0), app.diary.targets.map { it.kcal })
        profile.edit(profile.draft.copy(kcal = "2000"))
        profile.save()
        assertEquals(listOf(2000.0), app.diary.targets.map { it.kcal })
        assertEquals(2000.0, app.owners.session.targetHistory.single().kcal)
    }

    @Test fun aSaveThatChangesNoTargetWritesNoRow() {
        profile.edit(ProfileDraft(name = "Ana"))
        profile.save()
        assertEquals("Ana", profile.values.name)
        assertTrue(app.diary.targets.isEmpty())
    }

    @Test fun typingKcalClearsTheGoalAndPassingThroughSmallNumbersLosesNothing() {
        profile.edit(ProfileDraft(kcal = "2000"))
        profile.edit(profile.draft.copy(goal = Goal.LOSE))
        profile.editMacro(Macro.PROTEIN, "30", byPercent = true)
        profile.applyMacros()
        assertEquals(Goal.LOSE, profile.draft.goal)
        assertEquals("175", profile.draft.carbs)
        listOf("", "2", "24", "240", "2400").forEach(profile::editKcal)
        assertNull(profile.draft.goal)
        assertEquals("180", profile.draft.protein)
        // 2400 less 720: 1680, carbs at 700/702 of it.
        assertEquals("210", profile.draft.carbs)
    }

    @Test fun choosingAGoalFillsKcalFromTheBurn() {
        profile.edit(ProfileDraft(height = "180", age = "30", current = "80", sex = Sex.MALE, activity = Activity.LOW_ACTIVE))
        assertEquals(2447.5, profile.burn!!.kcal, 0.01)
        profile.chooseGoal(Goal.MAINTAIN)
        assertEquals("2448", profile.draft.kcal)
        profile.chooseGoal(Goal.LOSE)
        profile.choosePace("0.5", custom = false)
        assertEquals("1816", profile.draft.kcal)
        assertEquals(1816, profile.goalResult?.kcal)
    }

    /** The first run only records where the target stood, so the next day's move has it to count from. */
    @Test fun theDailyAdjustmentMovesASavedGoalAndWritesItsRowOnce() {
        ProfilePrefs(app.profileStore).write(goalValues)
        profile.reload()
        assertEquals(2447.5, profile.values.kcalTarget)
        assertEquals(listOf(2447.5), app.diary.targets.map { it.kcal })
        app.nowMs += DAY_MS
        profile.adjustDaily()
        assertEquals(2348.0, profile.values.kcalTarget)
        assertEquals(2348.0, ProfilePrefs(app.profileStore).read().kcalTarget)
        assertEquals(listOf(2447.5, 2348.0), app.diary.targets.map { it.kcal })
        profile.adjustDaily()
        assertEquals(2348.0, profile.values.kcalTarget)
        assertEquals(2, app.diary.targets.size)
    }

    @Test fun aKcalChangedThroughEditClearsTheGoal() {
        profile.edit(ProfileDraft(kcal = "2000"))
        profile.edit(profile.draft.copy(goal = Goal.LOSE))
        profile.edit(profile.draft.copy(kcal = "2100", name = "Ana"))
        assertNull(profile.draft.goal)
        assertEquals("2100", profile.draft.kcal)
        assertEquals("Ana", profile.draft.name)
        profile.edit(profile.draft.copy(goal = Goal.GAIN, kcal = "2200"))
        assertNull(profile.draft.goal)
    }

    @Test fun choosingAGoalKeepsItThoughItFillsKcal() {
        profile.edit(ProfileDraft(height = "180", age = "30", current = "80", sex = Sex.MALE, activity = Activity.LOW_ACTIVE))
        profile.chooseGoal(Goal.MAINTAIN)
        assertEquals(Goal.MAINTAIN, profile.draft.goal)
    }

    @Test fun whileAWriteRunsTheAdjustmentWritesNothing() {
        ProfilePrefs(app.profileStore).write(goalValues)
        val held = CompletableDeferred<Unit>()
        app.owners.session.write<Unit>(DiaryChange.WeightCurveChanged) { held.await() }
        profile.reload()
        assertTrue(app.diary.targets.isEmpty())
        held.complete(Unit)
        profile.adjustDaily()
        assertEquals(listOf(2447.5), app.diary.targets.map { it.kcal })
    }

    /** The row is written first: a process killed before the values were applied leaves it ahead of them. */
    @Test fun aRowTodayAheadOfTheValuesIsTrustedAndNothingSticks() {
        ProfilePrefs(app.profileStore).write(goalValues)
        app.diary.targets = listOf(TargetRow("2026-10-20", 2447.5, null, null, null), TargetRow("2026-10-28", 2348.0, null, null, null))
        app.owners.session.refresh()
        profile.reload()
        assertEquals(2348.0, profile.values.kcalTarget)
        assertEquals(2348.0, ProfilePrefs(app.profileStore).read().kcalTarget)
        assertEquals(Goal.LOSE, profile.values.goal)
        assertEquals(2, app.diary.targets.size)
    }

    @Test fun aRowDatedLaterGoesWithTodaysWriteAndPastDaysStay() {
        ProfilePrefs(app.profileStore).write(ProfileValues(kcalTarget = 2000.0))
        val past = TargetRow("2026-10-01", 2000.0, null, null, null)
        app.diary.targets = listOf(past, TargetRow("2026-12-01", 1500.0, null, null, null))
        app.owners.session.refresh()
        profile.reload()
        assertEquals(listOf(past, TargetRow("2026-10-28", 2000.0, null, null, null)), app.diary.targets)
        assertEquals(2000.0, profile.values.kcalTarget)
    }

    @Test fun onlyARowDatedLaterIsNoHistory() {
        ProfilePrefs(app.profileStore).write(goalValues)
        app.diary.targets = listOf(TargetRow("2026-12-01", 2000.0, null, null, null))
        app.owners.session.refresh()
        profile.reload()
        assertEquals(listOf(TargetRow("2026-10-28", 2447.5, null, null, null)), app.diary.targets)
        assertEquals(2447.5, profile.values.kcalTarget)
    }

    @Test fun theProjectionFollowsTheDayCheck() {
        ProfilePrefs(app.profileStore).write(ProfileValues(targetWeight = 70.0))
        profile.reload()
        app.diary.weights = listOf(WeightPoint("2026-10-28", 80.0, app.nowMs))
        app.owners.session.refresh()
        assertEquals(WeightProjection.NotEnoughData(13), profile.projection)
        app.nowMs += 40 * DAY_MS
        Snapshot.sendApplyNotifications()
        assertEquals(WeightProjection.NotEnoughData(13), profile.projection)
        app.day.check(returning = false)
        Snapshot.sendApplyNotifications()
        assertEquals(WeightProjection.NotEnoughData(14), profile.projection)
    }

    @Test fun warningsIgnoreWhatIsNotValid() {
        profile.edit(ProfileDraft(goal = Goal.LOSE, height = "1", age = "0", current = "9000", target = "x", pace = "1"))
        assertTrue(profile.loseWarnings.isEmpty())
        assertFalse(profile.paceTooFast)
        profile.edit(profile.draft.copy(height = "170", age = "17", current = "53"))
        assertEquals(setOf(com.bosa.breakfast.domain.LoseWarning.UNDERWEIGHT, com.bosa.breakfast.domain.LoseWarning.UNDER_18), profile.loseWarnings)
        assertTrue(profile.paceTooFast)
    }

    @Test fun typingLeavesTheOtherMacrosAloneUntilApply() {
        profile.edit(ProfileDraft(kcal = "2000", carbs = "300", fat = "40", followingMacros = setOf(Macro.CARBS, Macro.FAT)))
        val before = profile.draft
        listOf("5", "50", "500", "5000", "500", "50").forEach { profile.editMacro(Macro.PROTEIN, it, byPercent = false) }
        assertEquals("50", profile.draft.protein)
        assertEquals(before.carbs, profile.draft.carbs)
        assertEquals(before.fat, profile.draft.fat)
        assertTrue(profile.draft.macroApplyOffered)
    }

    // Protein 30%, 150 g, stays; carbs 200 g stays; 600 kcal left in fat: 66.7 g.
    @Test fun editApplyEditApplyLeavesBothEditsAndPutsTheRestInFat() {
        profile.edit(ProfileDraft(kcal = "2000"))
        profile.editMacro(Macro.PROTEIN, "30", byPercent = true)
        profile.applyMacros()
        assertFalse(profile.draft.macroApplyOffered)
        profile.editMacro(Macro.CARBS, "200", byPercent = false)
        assertEquals("78", profile.draft.fat)
        profile.applyMacros()
        assertEquals("150", profile.draft.protein)
        assertEquals("200", profile.draft.carbs)
        assertEquals("67", profile.draft.fat)
    }

    @Test fun applySavesNothing() {
        profile.edit(ProfileDraft(kcal = "2000"))
        profile.editMacro(Macro.PROTEIN, "30", byPercent = true)
        profile.applyMacros()
        assertEquals(ProfileValues(), profile.values)
        assertTrue(app.diary.targets.isEmpty())
        assertTrue(profile.canSave)
    }

    // 60% of 2000: 300 g each, 120%, with fat left at 67 g.
    @Test fun saveWithPendingEditsStoresWhatIsTypedAndClearsTheMarks() {
        ProfilePrefs(app.profileStore).write(ProfileValues(kcalTarget = 2000.0, proteinTarget = 150.0, carbsTarget = 200.0, fatTarget = 67.0))
        profile.reload()
        profile.editMacro(Macro.PROTEIN, "60", byPercent = true)
        profile.editMacro(Macro.CARBS, "60", byPercent = true)
        assertTrue(profile.canSave)
        profile.save()
        assertEquals(listOf(300.0, 300.0, 67.0), listOf(profile.values.proteinTarget, profile.values.carbsTarget, profile.values.fatTarget))
        assertEquals(60.0, profile.values.carbsPercent)
        assertEquals(emptySet(), profile.draft.handEditedMacros)
        assertFalse(profile.draft.macrosPending)
    }

    /** After a Save, carbs are no longer edited by hand: the next Apply moves them. */
    @Test fun theNextEditAfterSaveMarksOnlyWhatItTouches() {
        profile.edit(ProfileDraft(kcal = "2000", protein = "150", carbs = "200", fat = "67"))
        profile.editMacro(Macro.CARBS, "100", byPercent = false)
        profile.save()
        profile.editMacro(Macro.PROTEIN, "100", byPercent = false)
        assertEquals(setOf(Macro.PROTEIN), profile.draft.handEditedMacros)
        profile.applyMacros()
        assertNotEquals("100", profile.draft.carbs)
    }

    @Test fun leavingTheProfileClearsTheMarks() {
        app.surfaces.openProfile()
        profile.edit(ProfileDraft(kcal = "2000"))
        profile.editMacro(Macro.PROTEIN, "30", byPercent = true)
        app.back()
        profile.discard()
        app.surfaces.openProfile()
        assertEquals(emptySet(), profile.draft.handEditedMacros)
        assertFalse(profile.draft.macrosPending)
    }

    @Test fun theMarksComeBackAfterTheProcessDies() {
        val saved = SavedStateHandle()
        val first = AppKit(saved)
        first.surfaces.openProfile()
        first.owners.profile.edit(ProfileDraft(kcal = "2000"))
        first.owners.profile.editMacro(Macro.PROTEIN, "30", byPercent = true)
        Snapshot.sendApplyNotifications()
        assertEquals(setOf(Macro.PROTEIN), AppKit(saved).owners.profile.draft.handEditedMacros)
    }

    @Test fun aKcalInvalidThenTrackingOnlySavesAndKeepsTheTarget() {
        ProfilePrefs(app.profileStore).write(ProfileValues(kcalTarget = 2000.0))
        profile.reload()
        profile.editKcal("0")
        assertFalse(profile.canSave)
        profile.edit(profile.draft.copy(trackingOnly = true))
        assertTrue(profile.canSave)
        profile.save()
        assertEquals(ProfileValues(kcalTarget = 2000.0, trackingOnly = true), profile.values)
    }

    @Test fun aRefusedKeyLeavesTheGoal() {
        profile.edit(ProfileDraft(kcal = "2000", protein = "100"))
        profile.edit(profile.draft.copy(goal = Goal.LOSE))
        profile.editKcal("2000")
        assertEquals(Goal.LOSE, profile.draft.goal)
        val before = profile.draft
        profile.editMacro(Macro.PROTEIN, "100", byPercent = false)
        profile.editMacro(Macro.PROTEIN, "20", byPercent = true)
        assertEquals(before, profile.draft)
    }

    @Test fun tappingCustomAtASavedPaceAsksNothing() {
        ProfilePrefs(app.profileStore).write(goalValues.copy(kcalTarget = 1816.0))
        profile.reload()
        app.surfaces.openProfile()
        profile.choosePace("0.5", custom = true)
        assertTrue(profile.draft.paceCustom)
        app.back()
        assertEquals(listOf("DAILY"), app.shown())
    }

    @Test fun anInvalidNumberOnShowCountsAsAChange() {
        app.surfaces.openProfile()
        profile.editKcal("0")
        app.back()
        assertEquals(listOf("DAILY", "PROFILE", "DISCARD_PROFILE"), app.shown())
    }

    @Test fun typingANameDoesNotWorkTheBurnOutAgain() {
        profile.edit(ProfileDraft(height = "180", age = "30", current = "80", sex = Sex.MALE, activity = Activity.LOW_ACTIVE))
        val burn = profile.burn
        profile.edit(profile.draft.copy(name = "Ana"))
        assertSame(burn, profile.burn)
    }

    @Test fun theDailyAdjustmentWaitsWhileThePageIsOpen() {
        ProfilePrefs(app.profileStore).write(goalValues)
        profile.reload()
        app.diary.targets = emptyList()
        app.owners.session.refresh()
        app.surfaces.openProfile()
        profile.adjustDaily()
        assertTrue(app.diary.targets.isEmpty())
    }

    @Test fun targetsFromBeforeTheHistoryStartOne() {
        ProfilePrefs(app.profileStore).write(ProfileValues(kcalTarget = 2000.0, proteinTarget = 120.0))
        profile.reload()
        assertEquals(listOf(2000.0), app.diary.targets.map { it.kcal })
        assertEquals(2000.0, profile.values.kcalTarget)
    }

    @Test fun noTargetsStartNoHistory() {
        profile.adjustDaily()
        assertTrue(app.diary.targets.isEmpty())
    }

    @Test fun aBadNumberCannotBeSaved() {
        profile.edit(ProfileDraft(current = "0"))
        assertFalse(profile.canSave)
        profile.edit(ProfileDraft(fat = "8000"))
        assertFalse(profile.canSave)
    }

    @Test fun savedValuesAreStoredAcrossALaunch() {
        profile.edit(ProfileDraft(name = "Ana", protein = "120", target = "75,5"))
        profile.save()
        val expected = ProfileValues("Ana", proteinTarget = 120.0, targetWeight = 75.5)
        assertEquals(expected, profile.values)
        assertEquals(expected, ProfilePrefs(app.profileStore).read())
    }

    @Test fun theTickShowsForATypedWeightAndStartsOn() {
        assertFalse(profile.curveOffered)
        profile.edit(profile.draft.copy(current = "80"))
        assertTrue(profile.curveOffered)
        assertTrue(profile.addToCurve)
        profile.edit(profile.draft.copy(current = ""))
        assertFalse(profile.curveOffered)
    }

    @Test fun savingAChangedWeightAddsAPointOnTheDiaryDay() {
        saveCurrent("80")
        assertEquals(listOf(WeightPoint("2026-10-28", 80.0, app.nowMs, id = 1)), profile.points)
        assertEquals(80.0, profile.values.currentWeight)
    }

    @Test fun theDayFollowsDayStartsAt() {
        app.settings.shiftDayStart(13 - app.settings.dayStartHour)
        saveCurrent("80")
        assertEquals("2026-10-27", profile.points.single().dayKey)
    }

    /** Both saves name the same day; that the table keeps one row for it is proven by `BreakfastStoreTest` on a device. */
    @Test fun aSecondPointOnADayReplacesIt() {
        saveCurrent("80")
        saveCurrent("79.5")
        assertEquals(79.5, profile.points.single().kg)
    }

    @Test fun withTheTickOffOnlyTheCurrentWeightChanges() {
        saveCurrent("80")
        profile.edit(profile.draft.copy(current = "78"))
        profile.toggleAddToCurve()
        profile.save()
        assertEquals(78.0, profile.values.currentWeight)
        assertEquals(80.0, profile.points.single().kg)
    }

    @Test fun deletingAPointAsksFirstAndKeepsTheCurrentWeight() {
        saveCurrent("80")
        val point = profile.points.single()
        profile.askDelete(point)
        assertEquals(point, profile.deleteRequest)
        profile.cancelDelete()
        assertEquals(1, profile.points.size)
        profile.askDelete(point)
        profile.confirmDelete()
        assertNull(profile.deleteRequest)
        assertTrue(profile.points.isEmpty())
        assertEquals(80.0, profile.values.currentWeight)
    }

    private fun turnOnSeveral() {
        profile.edit(profile.draft.copy(severalWeights = true))
        profile.save()
    }

    /** Morning 80, a minute on evening 79, with the setting on. */
    private fun morningAndEvening() {
        profile.edit(profile.draft.copy(severalWeights = true, current = "80"))
        profile.save()
        app.nowMs += MINUTE_MS
        saveCurrent("79")
    }

    @Test fun severalWeightsIsOffUntilSavedAndStaysStored() {
        assertFalse(profile.values.severalWeights)
        turnOnSeveral()
        assertTrue(profile.values.severalWeights)
        assertTrue(ProfilePrefs(app.profileStore).read().severalWeights)
    }

    @Test fun tickingTheSettingIsAnUnsavedEdit() {
        app.surfaces.openProfile()
        profile.edit(profile.draft.copy(severalWeights = true))
        assertTrue(profile.canSave)
        app.back()
        assertEquals(listOf("DAILY", "PROFILE", "DISCARD_PROFILE"), app.shown())
    }

    @Test fun theSettingComesBackAfterTheProcessDies() {
        val saved = SavedStateHandle()
        val first = AppKit(saved)
        first.surfaces.openProfile()
        first.owners.profile.edit(ProfileDraft(severalWeights = true))
        Snapshot.sendApplyNotifications()
        assertTrue(AppKit(saved).owners.profile.draft.severalWeights)
    }

    @Test fun withSeveralOnTheSameNumberIsAddedByTickingIt() {
        morningAndEvening()
        app.nowMs += MINUTE_MS
        assertFalse(profile.addToCurve)
        profile.toggleAddToCurve()
        profile.save()
        assertEquals(listOf(79.0, 79.0, 80.0), profile.points.map { it.kg })
        assertEquals(setOf("2026-10-28"), profile.points.map { it.dayKey }.toSet())
    }

    @Test fun withSeveralOnTheTickShowsForAnyValidWeightAndIsNotAnUnsavedEdit() {
        morningAndEvening()
        app.surfaces.openProfile()
        assertTrue(profile.curveOffered)
        assertFalse(profile.canSave)
        app.back()
        assertEquals(listOf("DAILY"), app.shown())
    }

    @Test fun withSeveralOnADoubleTapOnSaveAddsOnePoint() {
        morningAndEvening()
        profile.edit(profile.draft.copy(current = "78"))
        profile.save()
        profile.save()
        assertEquals(listOf(78.0, 79.0, 80.0), profile.points.map { it.kg })
        profile.toggleAddToCurve()
        profile.save()
        profile.save()
        assertEquals(listOf(78.0, 78.0, 79.0, 80.0), profile.points.map { it.kg })
    }

    @Test fun withSeveralOnSavingAnythingElseAddsNoPoint() {
        morningAndEvening()
        profile.edit(profile.draft.copy(name = "Ana"))
        profile.save()
        assertEquals("Ana", profile.values.name)
        assertEquals(2, profile.points.size)
    }

    @Test fun turningTheSettingOnAddsNoPoint() {
        saveCurrent("80")
        turnOnSeveral()
        assertTrue(profile.values.severalWeights)
        assertEquals(1, profile.points.size)
    }

    @Test fun turningTheSettingOffWithANewWeightKeepsEveryPoint() {
        morningAndEvening()
        profile.edit(profile.draft.copy(severalWeights = false, current = "78"))
        profile.save()
        assertEquals(listOf(78.0, 79.0, 80.0), profile.points.map { it.kg })
    }

    @Test fun withSeveralOnABadWeightHidesTheTickAndAnEmptyOneToo() {
        morningAndEvening()
        profile.edit(profile.draft.copy(current = "0"))
        assertFalse(profile.curveOffered)
        profile.edit(profile.draft.copy(current = ""))
        assertFalse(profile.curveOffered)
    }

    @Test fun withSeveralOnAndTheTickOffOnlyTheCurrentWeightChanges() {
        morningAndEvening()
        profile.edit(profile.draft.copy(current = "78"))
        profile.toggleAddToCurve()
        profile.save()
        assertEquals(2, profile.points.size)
        assertEquals(78.0, profile.values.currentWeight)
    }

    @Test fun turningItOffKeepsThePointsAndTheNextSaveReplacesOnlyTheLatest() {
        morningAndEvening()
        profile.edit(profile.draft.copy(severalWeights = false))
        profile.save()
        assertEquals(listOf(79.0, 80.0), profile.points.map { it.kg })
        app.nowMs += MINUTE_MS
        saveCurrent("78")
        assertEquals(listOf(78.0, 80.0), profile.points.map { it.kg })
        assertEquals(app.nowMs, profile.points.first().savedAtEpochMs)
    }

    @Test fun deletingRemovesExactlyThePointTapped() {
        morningAndEvening()
        val (evening, morning) = profile.points
        profile.askDelete(morning)
        profile.confirmDelete()
        assertEquals(listOf(evening), profile.points)
    }

    @Test fun aSavedWeightStaysCurrentAfterItsPointIsDeleted() {
        morningAndEvening()
        profile.askDelete(profile.points.first())
        profile.confirmDelete()
        assertEquals(79.0, profile.values.currentWeight)
    }

    @Test fun aRowShowsTheTimeOnlyOnADayWithSeveral() {
        val zone = java.time.ZoneOffset.UTC
        val morning = WeightPoint("2026-10-31", 80.0, 1_793_423_520_000L, id = 1)
        val evening = WeightPoint("2026-10-31", 79.0, 1_793_466_720_000L, id = 2)
        val other = WeightPoint("2026-10-30", 81.0, 1_793_337_120_000L, id = 3)
        val points = listOf(evening, morning, other)
        assertEquals("Sat 31 Oct, 05:12", weightPointLabel(morning, points, zone))
        assertEquals("Sat 31 Oct, 17:12", weightPointLabel(evening, points, zone))
        assertEquals("Fri 30 Oct", weightPointLabel(other, points, zone))
    }

    @Test fun aReplacedDiaryReadsTheProfileAgain() {
        saveCurrent("80")
        ProfilePrefs(app.profileStore).clearNow()
        app.diary.weights = emptyList()
        app.owners.session.write(DiaryChange.DiaryReplaced) {}
        assertEquals(ProfileValues(), profile.values)
        assertEquals("", profile.draft.current)
        assertTrue(profile.points.isEmpty())
    }

    @Test fun backClosesTheLiftedChartBeforeAnythingElse() {
        app.surfaces.openProfile()
        profile.edit(ProfileDraft(name = "Ana"))
        profile.liftChart()
        assertTrue(app.back())
        assertFalse(profile.chartLifted)
        assertEquals(listOf("DAILY", "PROFILE"), app.shown())
        app.back()
        assertEquals(listOf("DAILY", "PROFILE", "DISCARD_PROFILE"), app.shown())
    }

    @Test fun aDraftSurvivesGoingBetweenPagesAndOpeningStartsOnTheFirst() {
        app.surfaces.openProfile()
        profile.edit(ProfileDraft(name = "Ana"))
        profile.goToPage(ProfilePage.NUTRITION)
        assertEquals(ProfilePage.NUTRITION, profile.page)
        profile.pageSettled(2, byUser = false)
        profile.pageSettled(1, byUser = true)
        assertEquals(ProfilePage.WEIGHT, profile.page)
        assertEquals("Ana", profile.draft.name)
        profile.discard()
        app.surfaces.openProfile()
        assertEquals(ProfilePage.PROFILE, profile.page)
    }

    @Test fun trackingOnlyTakesAwayTheWeightPageAndKeepsTheTargets() {
        assertEquals(ProfilePage.entries, profilePages(trackingOnly = false))
        assertEquals(listOf(ProfilePage.PROFILE, ProfilePage.NUTRITION), profilePages(trackingOnly = true))
        app.surfaces.openProfile()
        profile.edit(ProfileDraft(kcal = "2000", trackingOnly = true))
        assertEquals(2, profile.position.count)
        assertEquals("2000", profile.draft.kcal)
        profile.save()
        assertEquals(2000.0, profile.values.kcalTarget)
    }

    @Test fun anEmptiedPercentFieldUnsetsTheMacroAndTheNextKeyFillsIt() {
        app.surfaces.openProfile()
        profile.edit(ProfileDraft(kcal = "2000", protein = "150", carbs = "200", fat = "67"))
        profile.editMacro(Macro.PROTEIN, "3", byPercent = true)
        profile.editMacro(Macro.PROTEIN, "", byPercent = true)
        assertEquals("", profile.draft.percentShown(Macro.PROTEIN))
        assertEquals("", profile.draft.protein)
        assertEquals(setOf(Macro.PROTEIN), profile.draft.handEditedMacros)
        profile.editMacro(Macro.PROTEIN, "4", byPercent = true)
        assertEquals("4", profile.draft.proteinPercent)
        assertEquals("20", profile.draft.protein)
    }

    @Test fun weeksGoBackAndNoLaterThanTheCurrentOne() {
        app.surfaces.openProfile()
        val current = profile.week.start
        assertFalse(profile.hasNextWeek)
        profile.nextWeek()
        assertEquals(current, profile.week.start)
        profile.previousWeek()
        assertEquals(current.minusWeeks(1), profile.week.start)
        assertTrue(profile.hasNextWeek)
        profile.nextWeek()
        assertEquals(current, profile.week.start)
    }

    @Test fun aChosenRangeWinsOverTheDefaultUntilThePageCloses() {
        app.surfaces.openProfile()
        assertEquals(WeightRange.ALL, profile.weightRange)
        profile.chooseRange(WeightRange.ONE_MONTH)
        assertEquals(WeightRange.ONE_MONTH, profile.weightRange)
        profile.close()
        assertEquals(WeightRange.ALL, profile.weightRange)
    }
}

private const val DAY_MS = 86_400_000L
private const val MINUTE_MS = 60_000L
