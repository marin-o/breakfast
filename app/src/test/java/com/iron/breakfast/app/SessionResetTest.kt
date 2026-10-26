package com.iron.breakfast.app

import com.iron.breakfast.contribution.ContributionFlow
import com.iron.breakfast.contribution.OffContributions
import com.iron.breakfast.data.OffContributionDraft
import com.iron.breakfast.data.OffContributionResult
import com.iron.breakfast.data.OffPreflightResult
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.settings.AppSettings
import com.iron.breakfast.settings.FakeSettingsStore
import com.iron.breakfast.settings.SettingsState
import com.iron.breakfast.today.OpenDiary
import com.iron.breakfast.transfer.DiaryTransfer
import com.iron.breakfast.transfer.FakeData
import com.iron.breakfast.transfer.FakeFiles
import com.iron.breakfast.transfer.RecordingLog
import com.iron.breakfast.transfer.validDiaryText
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * B14: Import diary and Delete all data reset the session the same way, through one [SessionReset] on the
 * [DiaryChange.DiaryReplaced] they both publish. Before, import kept the resolve queue, the entry detail and a
 * contribution candidate, which referred to the old diary.
 */
class SessionResetTest {
    private val diary = OpenDiary()
    private val events = mutableListOf<String>()
    private val files = FakeFiles(events)
    private val data = FakeData(events)
    private val settings = SettingsState(AppSettings(FakeSettingsStore())) { 0L }
    private val contribution = ContributionFlow(
        object : OffContributions {
            override fun preflight(draft: OffContributionDraft) = OffPreflightResult.Existing
            override fun contribute(draft: OffContributionDraft, username: String, password: String) = OffContributionResult.Success
        },
        Notices(), diary.scope, Dispatchers.Unconfined,
    )
    private val transfer = DiaryTransfer(diary.session, data, files, settings, Notices(), { 0L }, diary.scope, Dispatchers.Unconfined, RecordingLog(), onExported = {})
    private val resets = mutableListOf<String>()

    init {
        // As the view model registers it: its own steps, and each owner's reset operation.
        diary.session.onChange(
            SessionReset(listOf({ resets += "log pins" }, contribution::reset, { resets += "resolve queue" }, { resets += "entry detail" }))::onChange,
        )
    }

    private fun candidate() {
        contribution.offerAfterLocalSave(OffContributionDraft("4006381333931", "Kefir", null, 100.0, MeasureUnit.GRAM, NutritionValues(60.0, 3.0, 4.0, 3.0)))
        assertNotNull(contribution.pending)
    }

    @Test fun replaceDiaryResetsTheWholeSession() {
        candidate()
        files.documents["in.json"] = validDiaryText()
        transfer.import("in.json")
        transfer.confirmImport()
        assertEquals(listOf("log pins", "resolve queue", "entry detail"), resets)
        assertNull(contribution.pending)
    }

    @Test fun deleteAllResetsTheSessionExactlyTheSameWay() {
        candidate()
        transfer.openDeleteAll()
        transfer.confirmDeleteAll()
        assertEquals(listOf("log pins", "resolve queue", "entry detail"), resets)
        assertNull(contribution.pending)
    }

    @Test fun aReplaceOrDeleteThatFailedResetsNothing() {
        candidate()
        data.failure = IllegalStateException("locked")
        files.documents["in.json"] = validDiaryText()
        transfer.import("in.json")
        transfer.confirmImport()
        transfer.openDeleteAll()
        transfer.confirmDeleteAll()
        assertEquals(emptyList<String>(), resets)
        assertNotNull(contribution.pending)
    }

    @Test fun otherWritesDoNotReset() {
        candidate()
        diary.commit(DiaryChange.FoodsLogged)
        diary.commit(DiaryChange.EntryEdited)
        assertEquals(emptyList<String>(), resets)
        assertNotNull(contribution.pending)
    }
}
