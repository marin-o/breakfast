package com.iron.breakfast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** B9: Settings is a list of six groups, each its own page; Back goes from a page to the list, Licences to About. */
class SettingsPagesTest {
    @Test fun theListShowsTheSixGroupsInOrder() {
        assertEquals(listOf("Logging", "Display", "Accessibility", "Scanner", "Data", "About"), SETTINGS_GROUPS.map { it.title })
    }

    @Test fun backFromAGroupPageReturnsToTheList() {
        SETTINGS_GROUPS.forEach { assertNull(settingsPageBack(it)) }
    }

    @Test fun backFromLicencesReturnsToAbout() {
        assertEquals(SettingsPage.ABOUT, settingsPageBack(SettingsPage.LICENCES))
        assertEquals("Licences", SettingsPage.LICENCES.title)
    }
}
