package com.bosa.breakfast.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnouncementsTest {
    private val announcements = Announcements()

    @Test fun eachIsTakenOnceInOrderSoARecreationReadsNothingAgain() {
        assertEquals(emptyList<String>(), announcements.take())
        announcements.say("Logged! Undo")
        announcements.say("Logged!")
        assertEquals(2, announcements.serial)
        assertEquals(listOf("Logged! Undo", "Logged!"), announcements.take())
        assertEquals("a recreated screen takes again", emptyList<String>(), announcements.take())
    }

    /** Away from the screen nothing is kept, so the return is not a burst of old ones. */
    @Test fun whileTheAppIsAwayNothingIsKept() {
        announcements.say("Logged!")
        announcements.stop()
        announcements.say("Egg deleted. Undo")
        assertEquals(emptyList<String>(), announcements.take())
        announcements.start()
        announcements.say("Logged! Undo")
        assertEquals(listOf("Logged! Undo"), announcements.take())
    }

    @Test fun aRepeatOfTheSameTextIsANewOne() {
        announcements.say("Logged!")
        announcements.take()
        announcements.say("Logged!")
        assertEquals(2, announcements.serial)
        assertEquals(listOf("Logged!"), announcements.take())
    }
}
