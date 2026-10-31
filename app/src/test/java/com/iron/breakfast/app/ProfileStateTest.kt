package com.iron.breakfast.app

import androidx.lifecycle.SavedStateHandle
import androidx.compose.runtime.snapshots.Snapshot
import com.iron.breakfast.domain.ProfileDraft
import com.iron.breakfast.domain.MacroPreset
import com.iron.breakfast.domain.ProfileValues
import com.iron.breakfast.domain.WeightPoint
import com.iron.breakfast.profile.ProfilePrefs
import com.iron.breakfast.profile.weightPointLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test fun aPresetFillsTheFieldsAndSavesNothing() {
        profile.edit(ProfileDraft(kcal = "2000"))
        profile.applyPreset(MacroPreset.LOW_CARB)
        assertEquals(ProfileDraft(kcal = "2000", protein = "150", carbs = "100", fat = "111"), profile.draft)
        assertEquals(ProfileValues(), profile.values)
        assertEquals(ProfileValues(), ProfilePrefs(app.profileStore).read())
        assertFalse(profile.kcalAsked)
    }

    @Test fun aPresetWithoutKcalAsksForItAndChangesNothing() {
        val typed = ProfileDraft(protein = "90")
        profile.edit(typed)
        profile.applyPreset(MacroPreset.BALANCED)
        assertEquals(typed, profile.draft)
        assertTrue(profile.kcalAsked)
    }

    @Test fun typingKcalEndsTheAskingButTypingElsewhereDoesNot() {
        profile.applyPreset(MacroPreset.BALANCED)
        profile.edit(profile.draft.copy(protein = "90"))
        assertTrue(profile.kcalAsked)
        profile.edit(profile.draft.copy(kcal = "2"))
        assertFalse(profile.kcalAsked)
    }

    @Test fun aKcalTargetIsStoredAndABadOneCannotBeSaved() {
        profile.edit(ProfileDraft(kcal = "20001"))
        assertFalse(profile.canSave)
        profile.edit(ProfileDraft(kcal = "2100"))
        profile.save()
        assertEquals(ProfileValues(kcalTarget = 2100.0), ProfilePrefs(app.profileStore).read())
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
        profile.edit(profile.draft.copy(name = "Marino"))
        profile.save()
        assertEquals("Marino", profile.values.name)
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
}

private const val DAY_MS = 86_400_000L
private const val MINUTE_MS = 60_000L
