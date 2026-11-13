package mk.bosa.breakfast.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

/** [CaretField] driven the way a composition drives it: onChange, then reconcile and settle per frame. */
class CaretFieldTest {
    private fun CaretField.frame(text: String) { reconcile(text); settle() }

    @Test fun textSetFromCodeAfterATapEndsWithTheCaretAtItsEnd() {
        val field = CaretField("100")
        field.onChange(TextFieldValue("100", TextRange(0))) {}
        field.frame("100")
        assertEquals(TextRange(0), field.value.selection)
        field.frame("99.5")
        assertEquals(TextRange(4), field.value.selection)
    }

    @Test fun typingKeepsTheCaretAndAFilterKeepsItBesideTheText() {
        val field = CaretField("")
        var owner = ""
        field.onChange(TextFieldValue("12a3", TextRange(3))) { owner = it }
        field.frame("123")
        assertEquals("123", field.value.text)
        assertEquals(TextRange(2), field.value.selection)
        assertEquals("12a3", owner)
    }

    @Test fun theSameValueAgainLeavesNothingTypedForLaterCodeChanges() {
        val field = CaretField("ab")
        val tap = TextFieldValue("ab", TextRange(0))
        field.onChange(tap) {}
        field.frame("ab")
        field.onChange(tap) {}
        field.frame("abcd")
        assertEquals(TextRange(4), field.value.selection)
    }

    @Test fun twoEditsInOneFrameAreBothHeardByTheOwner() {
        val field = CaretField("")
        val heard = mutableListOf<String>()
        field.onChange(TextFieldValue("a", TextRange(1))) { heard += it }
        field.onChange(TextFieldValue("", TextRange(0))) { heard += it }
        assertEquals(listOf("a", ""), heard)
        field.frame("")
        assertEquals("", field.value.text)
    }
}
