package mk.bosa.breakfast.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** MOB-028: a write reloads only the tables it touched, unless an earlier reload failed. */
class StoreReloadTest {
    @Test fun aWriteReloadsOnlyWhatItTouched() {
        assertEquals(setOf(StoreTable.ENTRIES), tablesToReload(setOf(StoreTable.ENTRIES), stale = false))
        assertEquals(setOf(StoreTable.PREFERENCES), tablesToReload(setOf(StoreTable.PREFERENCES), stale = false))
        assertEquals(setOf(StoreTable.FOODS, StoreTable.ENTRIES), tablesToReload(setOf(StoreTable.FOODS, StoreTable.ENTRIES), stale = false))
    }

    @Test fun aStaleSnapshotReloadsEverything() {
        assertEquals(ALL_TABLES, tablesToReload(setOf(StoreTable.ENTRIES), stale = true))
        assertEquals(StoreTable.entries.toSet(), ALL_TABLES)
    }
}
