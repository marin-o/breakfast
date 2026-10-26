package com.iron.breakfast.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoticesTest {
    @Test fun oneNoticeAtATimeTheLatestWins() {
        val notices = Notices()
        notices.show("Added to Lunch")
        notices.show("Could not undo.")
        assertEquals("Could not undo.", notices.message)
        notices.dismiss()
        assertNull(notices.message)
    }
}
