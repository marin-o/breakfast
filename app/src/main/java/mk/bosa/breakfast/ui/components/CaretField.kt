package mk.bosa.breakfast.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

// One caret rule for every text field: text set from code ends with the caret at its end; typing and tapping keep the user's caret.

/** [text] with the caret at its end: what a field shows for text it did not type itself, or opens with. */
internal fun inputFieldFor(text: String): TextFieldValue = TextFieldValue(text, TextRange(text.length))

/**
 * What a field shows for the owner's [text]. [held] is what it showed last; [typed] the user's latest change, when it has
 * not been answered yet. Text the field already shows (the user typed it) keeps [held]'s caret. Text the owner changed
 * while answering a change ([typed]: a filter or a rewrite) keeps the user's caret place ([filteredField]). Any other
 * change came from code, and its caret goes to the end.
 */
internal fun caretValueFor(held: TextFieldValue, typed: TextFieldValue?, text: String): TextFieldValue = when {
    held.text == text -> held
    typed != null -> filteredField(typed, text)
    else -> inputFieldFor(text)
}

/**
 * [typed] after its text was changed to [text] by a filter ("12,5" to "12.5", a dropped letter): the caret stays at the same
 * place in the text that is left. A change of characters keeps it as it was; for a dropped or added run, the caret stays
 * with the text it sits next to, counted from the start or from the end, whichever side of the change it is on.
 */
internal fun filteredField(typed: TextFieldValue, text: String): TextFieldValue {
    val raw = typed.text
    val caret = typed.selection.end.coerceIn(0, raw.length)
    if (text.length == raw.length) return TextFieldValue(text, TextRange(caret))
    val prefix = raw.commonPrefixWith(text).length
    val suffix = raw.commonSuffixWith(text).length.coerceAtMost(minOf(raw.length, text.length) - prefix)
    val place = when {
        caret <= prefix -> caret
        raw.length - caret <= suffix -> text.length - (raw.length - caret)
        else -> caret.coerceAtMost(text.length)
    }
    return TextFieldValue(text, TextRange(place))
}

/**
 * A text field's [TextFieldValue] over its owner's plain text, with the one caret rule ([caretValueFor]). Decided while
 * composing, so a change from code shows with its caret in the same frame.
 */
internal class CaretField(text: String) {
    private var held by mutableStateOf(inputFieldFor(text))
    private var typed: TextFieldValue? = null

    /** What the field shows now. */
    var value: TextFieldValue = held
        private set

    fun reconcile(text: String) { value = caretValueFor(held, typed, text) }

    fun settle() {
        typed = null
        if (held != value) held = value
    }

    /** The field changed ([next], a typed change, a tap or a selection): the owner hears the text only when it changed. */
    fun onChange(next: TextFieldValue, onText: (String) -> Unit) {
        if (next == held) return
        val textChanged = next.text != held.text
        typed = next
        held = next
        if (textChanged) onText(next.text)
    }
}

/** The [CaretField] for the owner's [text]. */
@Composable
internal fun rememberCaretField(text: String): CaretField {
    val field = remember { CaretField(text) }
    field.reconcile(text)
    SideEffect { field.settle() }
    return field
}

/** Takes focus when it first shows, with the caret at the end of its text: a sheet or page that opens on a field. */
@Composable
internal fun Modifier.focusOnOpen(): Modifier {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    return focusRequester(requester)
}
