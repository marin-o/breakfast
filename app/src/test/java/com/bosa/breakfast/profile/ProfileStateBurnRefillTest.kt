package com.bosa.breakfast.profile

import com.bosa.breakfast.app.Notices
import com.bosa.breakfast.domain.Activity
import com.bosa.breakfast.domain.Goal
import com.bosa.breakfast.domain.Macro
import com.bosa.breakfast.domain.ProfileDraft
import com.bosa.breakfast.domain.Sex
import com.bosa.breakfast.domain.TargetRow
import com.bosa.breakfast.domain.WeightPoint
import com.bosa.breakfast.domain.gramsText
import com.bosa.breakfast.domain.percentShown
import com.bosa.breakfast.settings.FakeSettingsStore
import com.bosa.breakfast.today.OpenDiary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ProfileStateBurnRefillTest {
    private val diary = OpenDiary()
    private val profile = ProfileState(
        session = diary.session,
        store = object : WeightStore {
            override suspend fun add(point: WeightPoint) {}
            override suspend fun replaceLatest(point: WeightPoint) {}
            override suspend fun delete(id: Long) {}
            override suspend fun saveTarget(row: TargetRow) {}
        },
        prefs = ProfilePrefs(FakeSettingsStore()),
        notices = Notices(diary.scope, {}),
        today = { "2026-11-02" },
        shownDay = { "2026-11-02" },
        clock = { 0L },
    ).apply {
        edit(draft.copy(height = "180", age = "30", current = "80", activity = Activity.LOW_ACTIVE, sex = Sex.MALE))
        chooseGoal(Goal.MAINTAIN)
    }

    private fun assertRefilledBy(change: (ProfileDraft) -> ProfileDraft) {
        val before = profile.draft.kcal
        profile.edit(change(profile.draft))
        assertNotEquals(before, profile.draft.kcal)
        assertEquals(profile.burn?.kcal?.let { Math.round(it).toString() }, profile.draft.kcal)
        assertEquals(Goal.MAINTAIN, profile.draft.goal)
    }

    @Test fun activityRefills() = assertRefilledBy { it.copy(activity = Activity.VERY_ACTIVE) }
    @Test fun heightRefills() = assertRefilledBy { it.copy(height = "170") }
    @Test fun ageRefills() = assertRefilledBy { it.copy(age = "50") }
    @Test fun currentWeightRefills() = assertRefilledBy { it.copy(current = "90") }
    @Test fun sexRefills() = assertRefilledBy { it.copy(sex = Sex.FEMALE) }

    @Test fun macrosFollowAsForAKcalChange() {
        profile.editMacro(Macro.PROTEIN, "30", byPercent = true)
        val grams = profile.draft.gramsText(Macro.PROTEIN)
        profile.edit(profile.draft.copy(activity = Activity.VERY_ACTIVE))
        assertEquals("30", profile.draft.percentShown(Macro.PROTEIN))
        assertNotEquals(grams, profile.draft.gramsText(Macro.PROTEIN))
    }

    @Test fun aRefillKeepsAMacroEditedInGramsAndItsMark() {
        profile.editMacro(Macro.PROTEIN, "150", byPercent = false)
        profile.edit(profile.draft.copy(activity = Activity.VERY_ACTIVE))
        assertEquals("150", profile.draft.gramsText(Macro.PROTEIN))
        assertEquals(setOf(Macro.PROTEIN), profile.draft.handEditedMacros)
    }

    @Test fun anInvalidBodyKeepsTheKcalAndTheGoal() {
        val before = profile.draft.kcal
        profile.edit(profile.draft.copy(height = "1"))
        assertEquals(before, profile.draft.kcal)
        assertEquals(Goal.MAINTAIN, profile.draft.goal)
    }

    @Test fun anIncompleteBodyKeepsTheKcalThenTheFinishedOneRefills() {
        val before = profile.draft.kcal
        profile.edit(profile.draft.copy(age = ""))
        assertEquals(before, profile.draft.kcal)
        profile.edit(profile.draft.copy(age = "60"))
        assertNotEquals(before, profile.draft.kcal)
        assertEquals(Goal.MAINTAIN, profile.draft.goal)
    }

    @Test fun anUnrelatedEditLeavesATypedOverKcalAlone() {
        val before = profile.draft.kcal
        profile.edit(profile.draft.copy(name = "Ana"))
        profile.edit(profile.draft.copy(target = "75"))
        assertEquals(before, profile.draft.kcal)
        assertEquals(Goal.MAINTAIN, profile.draft.goal)
    }

    @Test fun withoutAGoalNothingRefills() {
        profile.editKcal("1800")
        assertNull(profile.draft.goal)
        profile.edit(profile.draft.copy(activity = Activity.VERY_ACTIVE))
        assertEquals("1800", profile.draft.kcal)
    }

    @Test fun trackingOnlyKeepsTheTargets() {
        val before = profile.draft.kcal
        profile.edit(profile.draft.copy(trackingOnly = true))
        profile.edit(profile.draft.copy(activity = Activity.VERY_ACTIVE))
        assertEquals(before, profile.draft.kcal)
    }

    @Test fun kcalTypedByHandStillClearsTheGoal() {
        profile.edit(profile.draft.copy(kcal = "1900"))
        assertEquals("1900", profile.draft.kcal)
        assertNull(profile.draft.goal)
    }
}
