package com.iron.breakfast.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptComponentsTest {
    @Test
    fun largeTextStacksTimeOnlyWhenTicketHasAMealName() {
        assertTrue(shouldStackTicketTime(1.3f, "Lunch", "12:30"))
        assertFalse(shouldStackTicketTime(1.3f, "", "12:30"))
        assertFalse(shouldStackTicketTime(1.3f, "Lunch", null))
        assertFalse(shouldStackTicketTime(1.0f, "Lunch", "12:30"))
    }
}
