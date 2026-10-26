package com.iron.breakfast.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** B9: Settings is a list of six groups, each its own page; Back goes from a page to the list, Licences to About. */
class SettingsPagesTest {
    @Test fun theListShowsTheSixGroupsInOrder() {
        assertEquals(listOf("Logging", "Display", "Accessibility", "Scanner", "Data", "About"), SETTINGS_GROUPS.map { it.title })
    }

    @Test fun backFromAGroupPageReturnsToTheList() {
        SETTINGS_GROUPS.forEach { assertNull(settingsPageBack(it)) }
    }

    @Test fun versionReadsNameThenCode() {
        assertEquals("0.1 (10)", versionLabel("0.1", 10))
    }

    @Test fun theLicencesPageShowsTheBundledFontLicence() {
        val licence = java.io.File("src/main/assets/$LICENCES_ASSET")
        assertTrue(licence.isFile)
        assertTrue(licence.readText().contains("SIL OPEN FONT LICENSE"))
    }

    @Test fun backFromLicencesReturnsToAbout() {
        assertEquals(SettingsPage.ABOUT, settingsPageBack(SettingsPage.LICENCES))
        assertEquals("Licences", SettingsPage.LICENCES.title)
    }
}
