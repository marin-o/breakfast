package com.bosa.breakfast.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoticesTest {
    @Test fun oneNoticeAtATimeTheLatestWins() {
        val notices = Notices()
        notices.show("Saved to your food library.")
        notices.show("Could not undo.")
        assertEquals("Could not undo.", notices.message)
        notices.dismiss()
        assertNull(notices.message)
    }

    /** D15: the damaged-diary notice clears its marker only once the user dismissed it. */
    @Test fun aNoticesDismissActionRunsWhenItIsDismissedNotWhenReplaced() {
        val notices = Notices()
        var seen = 0
        notices.show("The diary could not be read. Import an export to restore it.", onDismiss = { seen++ })
        notices.show("Could not undo.")
        notices.dismiss()
        assertEquals("replaced, never seen", 0, seen)
        notices.show("The diary could not be read. Import an export to restore it.", onDismiss = { seen++ })
        notices.dismiss()
        notices.dismiss()
        assertEquals(1, seen)
    }
}
