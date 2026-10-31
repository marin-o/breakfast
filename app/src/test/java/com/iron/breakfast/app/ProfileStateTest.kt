package com.iron.breakfast.app

import androidx.lifecycle.SavedStateHandle
import androidx.compose.runtime.snapshots.Snapshot
import com.iron.breakfast.domain.ProfileDraft
import com.iron.breakfast.domain.ProfileValues
import com.iron.breakfast.domain.WeightPoint
import com.iron.breakfast.profile.ProfilePrefs
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
        assertEquals(listOf(WeightPoint("2026-10-28", 80.0, app.nowMs)), profile.points)
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
