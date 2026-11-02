package mk.bosa.breakfast.data

import mk.bosa.breakfast.testEntry
import mk.bosa.breakfast.domain.NutritionValues
import org.junit.Test
import kotlin.test.assertEquals

class BatchUndoSnapshotTest {
    private fun entry(id: String) = testEntry(id, "food-$id", "Food $id", savedAt = 1_000L, dayKey = "2026-10-25")

    @Test fun unchangedRowsAreUndone() {
        val logged = listOf(entry("a"), entry("b"))
        assertEquals(listOf("a", "b"), unchangedEntryIds(logged, logged.map { it.toEntity() }))
    }

    @Test fun anEntryEditedSinceItWasLoggedStays() {
        val logged = listOf(entry("a"), entry("b"), entry("c"))
        val stored = listOf(entry("a"), entry("b").copy(enteredAmount = 2.0, nutritionSnapshot = NutritionValues(140.0, 12.0, 1.0, 10.0)), entry("c"))
        assertEquals(listOf("a", "c"), unchangedEntryIds(logged, stored.map { it.toEntity() }))
    }

    @Test fun aMovedEntryStaysAndAGoneEntryIsSkipped() {
        val logged = listOf(entry("a"), entry("b"))
        val stored = listOf(entry("a").copy(dayKey = "2026-10-24"))
        assertEquals(emptyList(), unchangedEntryIds(logged, stored.map { it.toEntity() }))
    }
}
