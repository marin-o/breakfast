package com.iron.breakfast.profile

import com.iron.breakfast.data.DiaryRows
import com.iron.breakfast.domain.ProfileValues
import com.iron.breakfast.settings.FakeSettingsStore
import com.iron.breakfast.transfer.EMPTY_ROWS
import com.iron.breakfast.transfer.FakeData
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The diary's export, replace and Delete all, with the profile that lives beside the database. */
internal class ProfileDiaryDataTest {
    private val events = mutableListOf<String>()
    private val store = FakeSettingsStore()
    private val prefs = ProfilePrefs(store)
    private val fake = FakeData(events)
    private val data = ProfileDiaryData(fake, prefs)
    private val saved = ProfileValues("Ana", proteinTarget = 120.0, currentWeight = 80.0, kcalTarget = 2100.5)

    @Test fun anExportCarriesTheProfile() = runBlocking {
        prefs.write(saved)
        assertEquals(saved, data.exportRows().profile)
    }

    @Test fun aFileWithAProfileReplacesItAndCommitsIt() = runBlocking {
        prefs.write(saved)
        val other = ProfileValues(name = "Mia")
        data.replaceDiary(EMPTY_ROWS.copy(profile = other))
        assertEquals(other, prefs.read())
        assertEquals(1, store.commits)
        assertEquals(listOf("replace"), events)
    }

    @Test fun aFileFromBeforeTheProfileKeepsIt() = runBlocking {
        prefs.write(saved)
        data.replaceDiary(EMPTY_ROWS)
        assertEquals(saved, prefs.read())
        assertEquals(0, store.commits)
    }

    @Test fun deleteAllClearsTheProfileAndCommits() = runBlocking {
        prefs.write(saved)
        data.deleteAllData(alsoPreferences = false)
        assertEquals(ProfileValues(), prefs.read())
        assertEquals(1, store.commits)
    }

    @Test fun aFailedReplaceOrDeleteLeavesTheProfile() = runBlocking {
        prefs.write(saved)
        fake.failure = IllegalStateException("no")
        assertFailsWith<IllegalStateException> { data.replaceDiary(EMPTY_ROWS.copy(profile = ProfileValues())) }
        assertFailsWith<IllegalStateException> { data.deleteAllData(alsoPreferences = false) }
        assertEquals(saved, prefs.read())
    }
}
