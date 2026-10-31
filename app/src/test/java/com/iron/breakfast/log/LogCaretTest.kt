package com.iron.breakfast.log

import com.iron.breakfast.ui.components.syncedInputField
import com.iron.breakfast.ui.components.inputFieldFor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class LogCaretTest {
    @Test fun aTappedSuggestionMovesTheCaretToTheEnd() {
        val typed = TextFieldValue("2 eggs bu", TextRange(3))
        val after = syncedInputField(typed, "2 eggs, 100 g Buckwheat")
        assertEquals("2 eggs, 100 g Buckwheat", after.text)
        assertEquals(TextRange("2 eggs, 100 g Buckwheat".length), after.selection)
    }

    @Test fun typingKeepsTheCaretWhereTheUserPutIt() {
        val typed = TextFieldValue("2 eggs bu", TextRange(3))
        assertEquals(TextRange(3), syncedInputField(typed, "2 eggs bu").selection)
    }

    @Test fun aFieldThatOpensAgainStartsWithTheCaretAtTheEnd() {
        assertEquals(TextRange("2 eggs, Branded pesto 200ml".length), inputFieldFor("2 eggs, Branded pesto 200ml").selection)
    }
}
