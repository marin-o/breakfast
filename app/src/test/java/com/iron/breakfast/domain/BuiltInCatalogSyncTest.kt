package com.iron.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalogue sync for an existing install: a built-in food seeded from an older catalogue gets the current aliases,
 * and what the user owns is kept.
 */
class BuiltInCatalogSyncTest {
    private val homeMilk = FoodCatalog.fixtures.first { it.id == "custom_home_milk" }

    /** The row an install seeded from the old catalogue holds: the Latin alias "domashno mleko" is still there. */
    private val seededBefore = homeMilk.copy(aliases = listOf("домашно млеко", "domashno mleko", "млеко"))

    @Test fun anOlderInstallLosesTheRemovedLatinAlias() {
        val changed = BuiltInCatalogSync.changedRows(listOf(seededBefore), FoodCatalog.fixtures)
        assertEquals(1, changed.size)
        assertEquals(homeMilk.aliases, changed.single().aliases)
        assertFalse("domashno mleko" in changed.single().aliases)
    }

    @Test fun aRowThatMatchesTheCatalogueIsNotRewritten() {
        assertTrue(BuiltInCatalogSync.changedRows(listOf(homeMilk), FoodCatalog.fixtures).isEmpty())
    }

    @Test fun runningTheSyncTwiceChangesNothingTheSecondTime() {
        val once = BuiltInCatalogSync.changedRows(listOf(seededBefore), FoodCatalog.fixtures).single()
        assertTrue(BuiltInCatalogSync.changedRows(listOf(once), FoodCatalog.fixtures).isEmpty())
    }

    @Test fun aFoodTheUserEditedIsKept() {
        val edited = seededBefore.copy(name = "My home milk", personalOverride = true)
        assertTrue(BuiltInCatalogSync.changedRows(listOf(edited), FoodCatalog.fixtures).isEmpty())
    }

    @Test fun anArchivedBuiltInFoodStaysArchivedAndGetsTheCurrentContent() {
        val archived = seededBefore.copy(archived = true)
        val changed = BuiltInCatalogSync.changedRows(listOf(archived), FoodCatalog.fixtures).single()
        assertTrue(changed.archived)
        assertEquals(homeMilk.aliases, changed.aliases)
    }

    @Test fun sourceIdentityAndBarcodeAreKept() {
        val stored = seededBefore.copy(barcode = "4800000000001", sourceId = "off-1", sourceName = "Open Food Facts")
        val changed = BuiltInCatalogSync.changedRows(listOf(stored), FoodCatalog.fixtures).single()
        assertEquals("4800000000001", changed.barcode)
        assertEquals("off-1", changed.sourceId)
        assertEquals("Open Food Facts", changed.sourceName)
    }

    @Test fun aCustomFoodIsNotTouched() {
        val custom = seededBefore.copy(id = "custom-6f2c", name = "Гевеч", aliases = listOf("гевеч"), origin = FoodOrigin.CUSTOM)
        assertTrue(BuiltInCatalogSync.changedRows(listOf(custom), FoodCatalog.fixtures).isEmpty())
    }
}
