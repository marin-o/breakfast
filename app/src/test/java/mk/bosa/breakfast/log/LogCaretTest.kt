package mk.bosa.breakfast.log

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import mk.bosa.breakfast.domain.asciiDigits
import mk.bosa.breakfast.domain.decimalInput
import mk.bosa.breakfast.ui.components.caretValueFor
import mk.bosa.breakfast.ui.components.filteredField
import mk.bosa.breakfast.ui.components.inputFieldFor
import org.junit.Assert.assertEquals
import org.junit.Test

/** The one caret rule of every text field: text set from code ends with the caret at its end, typing keeps the user's. */
class LogCaretTest {
    @Test fun aTappedSuggestionMovesTheCaretToTheEnd() {
        val held = TextFieldValue("2 eggs bu", TextRange(3))
        val after = caretValueFor(held, typed = null, text = "2 eggs, 100 g Buckwheat")
        assertEquals("2 eggs, 100 g Buckwheat", after.text)
        assertEquals(TextRange("2 eggs, 100 g Buckwheat".length), after.selection)
    }

    @Test fun typingKeepsTheCaretWhereTheUserPutIt() {
        // A letter typed in the middle of "2 eggs b": the field holds the typed value, and the owner answers with the same text.
        val typed = TextFieldValue("2 eggs bu", TextRange(5))
        assertEquals(TextRange(5), caretValueFor(typed, typed = typed, text = "2 eggs bu").selection)
        // A tap that only moved the caret: nothing was typed, the text is the same.
        val tapped = TextFieldValue("2 eggs bu", TextRange(2))
        assertEquals(TextRange(2), caretValueFor(tapped, typed = null, text = "2 eggs bu").selection)
    }

    @Test fun aFieldThatOpensAgainStartsWithTheCaretAtTheEnd() {
        assertEquals(TextRange("2 eggs, Branded pesto 200ml".length), inputFieldFor("2 eggs, Branded pesto 200ml").selection)
    }

    @Test fun anAmountSetFromCodeEndsWithTheCaretAtItsEnd() {
        // A unit switch, a plus button or a prefill changes the text while the caret sat at the start.
        val held = TextFieldValue("100", TextRange(0))
        assertEquals(TextRange(1), caretValueFor(held, typed = null, text = "1").selection)
        assertEquals(TextRange(4), caretValueFor(held, typed = null, text = "99.5").selection)
    }

    @Test fun aFilteredCommaKeepsTheCaretWhereItWas() {
        val typed = TextFieldValue("1,25", TextRange(2))
        val after = filteredField(typed, "1,25".decimalInput())
        assertEquals("1.25", after.text)
        assertEquals(TextRange(2), after.selection)
    }

    @Test fun aDroppedLetterKeepsTheCaretBesideTheTextItSatNextTo() {
        // In the middle: "12a|3" typed, "123" kept, the caret stays between 2 and 3.
        assertEquals(TextRange(2), filteredField(TextFieldValue("12a3", TextRange(3)), "12a3".asciiDigits()).selection)
        // At the end.
        assertEquals(TextRange(2), filteredField(TextFieldValue("12a", TextRange(3)), "12a".asciiDigits()).selection)
        // At the start.
        assertEquals(TextRange(0), filteredField(TextFieldValue("a12", TextRange(1)), "a12".asciiDigits()).selection)
    }

    @Test fun aSecondSeparatorIsDroppedWithoutMovingTheCaretOffTheText() {
        val after = filteredField(TextFieldValue("1.2.5", TextRange(4)), "1.2.5".decimalInput())
        assertEquals("1.25", after.text)
        assertEquals(TextRange(3), after.selection)
    }

    @Test fun aFilterIsOnlyAnAnswerToTyping() {
        // Typed text the owner changed keeps the caret; the same change with nothing typed came from code, so the end.
        val held = TextFieldValue("12a3", TextRange(3))
        assertEquals(TextRange(2), caretValueFor(held, typed = held, text = "123").selection)
        assertEquals(TextRange(3), caretValueFor(held, typed = null, text = "123").selection)
    }
}
