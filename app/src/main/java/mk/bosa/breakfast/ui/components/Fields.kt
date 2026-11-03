package mk.bosa.breakfast.ui.components

import mk.bosa.breakfast.domain.MeasureUnit
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mk.bosa.breakfast.domain.decimalInput
import mk.bosa.breakfast.ui.label
import mk.bosa.breakfast.ui.theme.Macro
import mk.bosa.breakfast.ui.theme.BreakfastType
import mk.bosa.breakfast.ui.theme.FieldMinHeight
import mk.bosa.breakfast.ui.theme.FieldShape
import mk.bosa.breakfast.ui.theme.IconTarget
import mk.bosa.breakfast.ui.theme.LocalBreakfastColors

/** Labelled 1dp `line` box: focus is a 1.5dp ink outline, error a 1.5dp `bad` outline with the message below. */
@Composable
fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    labelVisible: Boolean = true,
    error: Boolean = false,
    errorMessage: String? = null,
    readOnly: Boolean = false,
    /** Centred 26/34 light amount, used by the quick-edit sheet. */
    amountStyle: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    /** Soft text at the end of the box, such as `from scan` or `≈30 g`. */
    hint: String? = null,
    /** Applied to the text field itself, for a FocusRequester. */
    fieldModifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    /** Marks the field as needed to save: "required" sits at the end of the label row. */
    required: Boolean = false,
    /** What TalkBack says for an [error] that shows only the red ring; a visible [errorMessage] is said instead. */
    errorDescription: String? = null,
    /** The macro this field holds: its dot goes before the label. */
    labelMacro: Macro? = null,
) {
    val colors = LocalBreakfastColors.current
    LabelledField(label, modifier, labelVisible, error, errorMessage, hint, fieldModifier, trailing, required, errorDescription, labelMacro) { textModifier ->
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = textModifier,
            singleLine = singleLine,
            minLines = minLines,
            readOnly = readOnly,
            textStyle = if (amountStyle) BreakfastType.LargeInput.copy(color = colors.ink, textAlign = TextAlign.Center) else BreakfastType.Body.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.ink),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            decorationBox = { inner -> FieldText(inner, value.isEmpty(), placeholder, centred = amountStyle) },
        )
    }
}

/**
 * The same [Field] over a [TextFieldValue], for text that also changes from outside the field (a tapped choice) and
 * needs its caret placed: the recipe's ingredients.
 */
@Composable
fun Field(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    fieldModifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalBreakfastColors.current
    LabelledField(label, modifier, labelVisible = true, error = false, errorMessage = null, hint = null, fieldModifier, trailing, required = false, errorDescription = null, labelMacro = null) { textModifier ->
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = textModifier,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = BreakfastType.Body.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.ink),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { inner -> FieldText(inner, empty = false, placeholder = null, centred = false) },
        )
    }
}

/** A field's label row, its box with [input] (given the modifier the text field takes), its hint, trailing and error. */
@Composable
private fun LabelledField(
    label: String,
    modifier: Modifier,
    labelVisible: Boolean,
    error: Boolean,
    errorMessage: String?,
    hint: String?,
    fieldModifier: Modifier,
    trailing: (@Composable () -> Unit)?,
    required: Boolean,
    errorDescription: String?,
    labelMacro: Macro?,
    input: @Composable (textModifier: Modifier) -> Unit,
) {
    val colors = LocalBreakfastColors.current
    val spokenError = errorMessage ?: errorDescription?.takeIf { error }
    Column(modifier.fillMaxWidth()) {
        if (labelVisible) Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            MacroLabel(label, labelMacro, colors.soft, BreakfastType.Meta, Modifier.weight(1f))
            if (required) Text("required", color = colors.soft, style = BreakfastType.Meta)
        }
        FieldFrame(error = error, modifier = Modifier.fillMaxWidth()) { onFocus ->
            input(
                Modifier
                    .weight(1f)
                    .then(fieldModifier)
                    .onFocusChanged(onFocus)
                    // The visible label names the field; the error is a semantic error, so TalkBack says it on the field itself.
                    .semantics {
                        contentDescription = if (required) "$label, required" else label
                        if (spokenError != null) error(spokenError)
                    },
            )
            if (hint != null) Text(hint, color = colors.soft, style = BreakfastType.Meta)
            trailing?.invoke()
        }
        if (errorMessage != null) Text(errorMessage, color = colors.bad, style = BreakfastType.Meta, modifier = Modifier.padding(top = 4.dp))
    }
}

/** The text inside a field's box, with its [placeholder] while [empty]. */
@Composable
private fun FieldText(inner: @Composable () -> Unit, empty: Boolean, placeholder: String?, centred: Boolean) {
    Box(Modifier.heightIn(min = 22.dp), contentAlignment = if (centred) Alignment.Center else Alignment.CenterStart) {
        if (empty && placeholder != null) Text(placeholder, color = LocalBreakfastColors.current.soft, style = BreakfastType.Body)
        inner()
    }
}

/**
 * A [Field] for a number in the food forms: the decimal keyboard, and the [decimalInput] filter, so "12,5" types the
 * same in New food and Edit food. [onValueChange] receives the filtered text.
 */
@Composable
fun DecimalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    labelVisible: Boolean = true,
    error: Boolean = false,
    errorMessage: String? = null,
    required: Boolean = false,
    /** Applied to the text field itself, for a FocusRequester. */
    fieldModifier: Modifier = Modifier,
    /** Soft text at the end of the box, such as a unit. */
    hint: String? = null,
    /** Centred 26/34 light amount, used by the quick-edit sheet. */
    amountStyle: Boolean = false,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    /** The macro this field holds: its dot goes before the label. */
    labelMacro: Macro? = null,
) = Field(
    label, value, { onValueChange(it.decimalInput()) }, modifier,
    labelVisible = labelVisible, error = error, errorMessage = errorMessage, required = required, hint = hint, amountStyle = amountStyle,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
    keyboardActions = keyboardActions,
    fieldModifier = fieldModifier,
    labelMacro = labelMacro,
)

/** One of [NutrientFields]: a [DecimalField]'s label, value and, when it has them, error, focus and macro dot. */
internal class NutrientField(
    val label: String,
    val value: String,
    val onValueChange: (String) -> Unit,
    val error: Boolean = false,
    val errorMessage: String? = null,
    val required: Boolean = false,
    val fieldModifier: Modifier = Modifier,
    val macro: Macro? = null,
)

/**
 * Number fields in [columns] per row (a food's four, two by two), [gap] apart as each mock draws them; with
 * [oneRowWhenItFits], one row while a label and a number with the field's padding fit a quarter (the scan review);
 * with [oneColumnWhenLabelsWrap], one per row when a label and its dot wouldn't fit a column (Profile).
 */
@Composable
internal fun NutrientFields(fields: List<NutrientField>, gap: Dp, oneRowWhenItFits: Boolean = false, columns: Int = 2, oneColumnWhenLabelsWrap: Boolean = false) {
    val cell: @Composable (NutrientField, Modifier) -> Unit = { field, modifier ->
        DecimalField(field.label, field.value, field.onValueChange, modifier, error = field.error, errorMessage = field.errorMessage, required = field.required, fieldModifier = field.fieldModifier, labelMacro = field.macro)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // The label sits above the box; the number inside it, within its 16dp padding.
        val labelMark = if (fields.any { it.macro != null }) MacroDotSize + MacroDotGap else 0.dp
        val fits = oneRowWhenItFits && fitsAQuarter(fields.map { it.label }, BreakfastType.Meta, labelMark, maxWidth, gap) &&
            fitsAQuarter(listOf(NUTRIENT_SAMPLE), BreakfastType.Body, 32.dp, maxWidth, gap)
        val labelsFit = !oneColumnWhenLabelsWrap || fitsAColumn(fields.map { it.label }, BreakfastType.Meta, labelMark, maxWidth, gap, columns)
        if (fits) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                fields.forEach { cell(it, Modifier.weight(1f)) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                fields.chunked(if (labelsFit) columns else 1).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        pair.forEach { cell(it, Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** A number as wide as a nutrient field must hold. */
private const val NUTRIENT_SAMPLE = "000.0"

/** Whether [content] fits a quarter of [width] with [gap] between the four. */
internal fun fitsQuarter(content: Dp, width: Dp, gap: Dp): Boolean = fitsColumn(content, width, gap, 4)

/** Whether [content] fits one of [columns] across [width] with [gap] between them. */
internal fun fitsColumn(content: Dp, width: Dp, gap: Dp, columns: Int): Boolean = content <= (width - gap * (columns - 1)) / columns

/** Whether the widest of [labels] in [style], with [padding], fits a quarter of [width] with [gap] between the four. */
@Composable
internal fun fitsAQuarter(labels: List<String>, style: TextStyle, padding: Dp, width: Dp, gap: Dp): Boolean =
    fitsAColumn(labels, style, padding, width, gap, 4)

/** Whether the widest of [labels] in [style], with [padding], fits one of [columns] across [width] with [gap] between them. */
@Composable
internal fun fitsAColumn(labels: List<String>, style: TextStyle, padding: Dp, width: Dp, gap: Dp, columns: Int): Boolean {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widest = labels.maxOf { measurer.measure(it, style).size.width }
    return fitsColumn(with(density) { widest.toDp() } + padding, width, gap, columns)
}

/** Field with a leading magnifier and a trailing clear button. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    /** Applied to the text field itself, such as a hardware Enter handler. */
    fieldModifier: Modifier = Modifier,
) {
    val colors = LocalBreakfastColors.current
    // Clearing keeps the bar selected so the next letters land in it.
    val focus = remember { FocusRequester() }
    FieldFrame(error = false, modifier = modifier.fillMaxWidth()) { onFocus ->
        Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.soft, modifier = Modifier.size(20.dp))
        SearchText(
            value, onValueChange, { onClear(); focus.requestFocus() }, label, onSearch,
            textColor = colors.ink, placeholderColor = colors.soft, clearTint = colors.soft,
            fieldModifier = fieldModifier.focusRequester(focus).onFocusChanged(onFocus),
        )
    }
}

/** A search's text and its Clear, in the colours of the box they sit in: [SearchField], or the Library's ink bar. */
@Composable
internal fun RowScope.SearchText(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    label: String,
    onSearch: (() -> Unit)?,
    textColor: Color,
    placeholderColor: Color,
    clearTint: Color,
    fieldModifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.weight(1f).then(fieldModifier).semantics { contentDescription = label },
        singleLine = true,
        textStyle = BreakfastType.Body.copy(color = textColor),
        cursorBrush = SolidColor(textColor),
        keyboardOptions = KeyboardOptions(imeAction = if (onSearch != null) ImeAction.Search else ImeAction.Done),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }, onDone = { onSearch?.invoke() }),
        decorationBox = { inner ->
            Box(Modifier.heightIn(min = 22.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(label, color = placeholderColor, style = BreakfastType.Body)
                inner()
            }
        },
    )
    if (value.isNotEmpty()) IconButton(onClick = onClear, modifier = Modifier.size(IconTarget)) {
        Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = clearTint, modifier = Modifier.size(20.dp))
    }
}

/** An ink bar's placeholder: `bg` at 60%. */
internal val Color.barPlaceholder: Color get() = copy(alpha = 0.6f)

/** A [Field]'s frame around a value picked elsewhere: a tap opens the picker. */
@Composable
internal fun ValueField(label: String, value: String, onClick: () -> Unit, icon: ImageVector, iconDescription: String, modifier: Modifier = Modifier) {
    val colors = LocalBreakfastColors.current
    Column(modifier.fillMaxWidth()) {
        Text(label, color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(bottom = 4.dp))
        FieldFrame(error = false, modifier = Modifier.fillMaxWidth(), onClick = onClick) { _ ->
            Text(value, Modifier.weight(1f), color = colors.ink, style = BreakfastType.Body)
            Icon(icon, iconDescription, tint = colors.ink)
        }
    }
}

/** The shared box for [Field] and [SearchField]: 1dp line, 1.5dp ink on focus, 1.5dp bad on error. */
@Composable
private fun FieldFrame(
    error: Boolean,
    modifier: Modifier = Modifier,
    /** A frame that is itself the control: tapped, and focused as a whole. */
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.(onFocus: (FocusState) -> Unit) -> Unit,
) {
    val colors = LocalBreakfastColors.current
    var focused by remember { mutableStateOf(false) }
    val ring = when {
        error -> colors.bad
        focused -> colors.ink
        else -> colors.line
    }
    Row(
        modifier
            .then(if (onClick != null) Modifier.onFocusChanged { focused = it.isFocused }.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = FieldMinHeight)
            .border(if (error || focused) 1.5.dp else 1.dp, ring, FieldShape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content { focusState -> focused = focusState.isFocused }
    }
}

/** A form's fields, in the one spacing every form has. */
@Composable
internal fun FormFields(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

/** [label] over an amount's [field] (it takes the modifier, and keeps its own label hidden), then g and ml. */
@Composable
internal fun AmountWithUnit(label: String, unit: MeasureUnit?, onUnit: (MeasureUnit) -> Unit, field: @Composable (Modifier) -> Unit) {
    Column {
        Text(label, color = LocalBreakfastColors.current.soft, style = BreakfastType.Meta, modifier = Modifier.padding(bottom = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            field(Modifier.weight(1.6f))
            GramMlChoices(unit, onUnit, Modifier.weight(1f))
        }
    }
}
