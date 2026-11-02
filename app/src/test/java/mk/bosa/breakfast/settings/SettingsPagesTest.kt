package mk.bosa.breakfast.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun theLicencesPageEndsOnTheBundledFontLicence() {
        assertEquals(listOf("NOTICE.txt", "OFL.txt"), LICENCE_ASSETS)
        val licence = java.io.File("src/main/assets/${LICENCE_ASSETS.last()}")
        assertTrue(licence.isFile)
        assertTrue(licence.readText().contains("SIL OPEN FONT LICENSE"))
    }

    /** The build copies the repository's NOTICE in as the page's first asset, so there is no second copy to keep. */
    @Test fun theNoticeIsNotKeptAsASecondCopyInTheAssets() {
        assertFalse(java.io.File("src/main/assets/${LICENCE_ASSETS.first()}").exists())
        assertTrue(java.io.File("build.gradle.kts").readText().contains("""file("NOTICE")"""))
    }

    @Test fun theNoticeListsTheDataTheFontAndEveryLicence() {
        val notice = java.io.File("../NOTICE").readText()
        listOf(
            "Geologica", "SIL Open Font License", "FoodData Central", "CC0", "Open Food Facts", "ODbL", "Apache License 2.0",
            "zxing-cpp", "Vico", "AndroidX", "Kotlin", "Checker Framework", "MIT License", "camera-core", "BSD 3-Clause",
        ).forEach { assertTrue(it, notice.contains(it)) }
    }

    @Test fun theNoticeNamesEveryDirectDependency() {
        val notice = java.io.File("../NOTICE").readText()
        val dependency = Regex("""^\s*\w*[iI]mplementation\((?:platform\()?"([^":]+):([^":]+)""", RegexOption.MULTILINE)
        val coordinates = dependency.findAll(java.io.File("build.gradle.kts").readText()).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertTrue(coordinates.size > 10)
        coordinates.forEach { (group, artifact) -> assertTrue("$group:$artifact", notice.contains(noticeNameOf(group))) }
    }

    /** What NOTICE calls a dependency's group; a group added to the build and not here fails, so it gets listed. */
    private fun noticeNameOf(group: String): String = when {
        group == "androidx.test" || group == "androidx.test.ext" -> "AndroidX Test"
        group == "androidx.room" -> "Room"
        group == "androidx.camera" -> "CameraX"
        group.startsWith("androidx.compose") -> "Jetpack Compose"
        group.startsWith("androidx.") -> "AndroidX"
        group == "io.github.zxing-cpp" -> "zxing-cpp"
        group == "org.jetbrains.kotlinx" -> "kotlinx.serialization"
        group == "org.jetbrains.kotlin" -> "Kotlin"
        group == "com.patrykandpatrick.vico" -> "Vico"
        group == "junit" -> "JUnit"
        else -> throw AssertionError("$group: add it to NOTICE and here")
    }

    @Test fun aPageLiesDeeperThanTheListAndLicencesDeeperThanAbout() {
        assertEquals(0, pageDepth(null))
        SETTINGS_GROUPS.forEach { assertEquals(1, pageDepth(it)) }
        assertEquals(2, pageDepth(SettingsPage.LICENCES))
    }

    @Test fun backFromLicencesReturnsToAbout() {
        assertEquals(SettingsPage.ABOUT, settingsPageBack(SettingsPage.LICENCES))
        assertEquals("Licences", SettingsPage.LICENCES.title)
    }
}
