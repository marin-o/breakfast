package com.iron.breakfast.app

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

    @Test fun aRepeatOfTheSameTextIsANewOne() {
        announcements.say("Logged!")
        announcements.take()
        announcements.say("Logged!")
        assertEquals(2, announcements.serial)
        assertEquals(listOf("Logged!"), announcements.take())
    }
}
