package com.iron.breakfast.ui.components

import com.iron.breakfast.ui.theme.Gutter
import androidx.compose.foundation.layout.fillMaxSize
import com.iron.breakfast.ui.theme.SnackbarGutter
import com.iron.breakfast.domain.MeasureUnit
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.heading
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.decimalInput
import com.iron.breakfast.ui.label
import com.iron.breakfast.ui.theme.AddCircleSize
import com.iron.breakfast.ui.theme.BreakfastType
import com.iron.breakfast.ui.theme.ChoiceMinHeight
import com.iron.breakfast.ui.theme.ChoiceShape
import com.iron.breakfast.ui.theme.ComposerHeight
import com.iron.breakfast.ui.theme.ComposerShape
import com.iron.breakfast.ui.theme.FieldMinHeight
import com.iron.breakfast.ui.theme.FieldShape
import com.iron.breakfast.ui.theme.HeaderHeight
import com.iron.breakfast.ui.theme.IconTarget
import com.iron.breakfast.ui.theme.LocalBreakfastColors
import com.iron.breakfast.ui.theme.PrimaryHeight
import com.iron.breakfast.ui.theme.PrimaryShape
import com.iron.breakfast.ui.theme.SecondaryHeight
import com.iron.breakfast.ui.theme.SecondaryShape
import com.iron.breakfast.ui.theme.SheetShape
import com.iron.breakfast.ui.theme.SnackbarShape
import com.iron.breakfast.ui.theme.TertiaryHeight

/** Food names and typed text use the Macedonian shaping locale. */
fun foodText(value: String): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(localeList = LocaleList("mk"))) { append(value) }
}

enum class ButtonKind { Constructive, Neutral }

/** One filled primary per screen. Constructive (ok) adds, saves, logs or restores; Neutral (ink) covers the rest. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    kind: ButtonKind = ButtonKind.Constructive,
    fillWidth: Boolean = true,
) {
    val colors = LocalBreakfastColors.current
    val fill = if (kind == ButtonKind.Constructive) colors.ok else colors.ink
    val content = if (kind == ButtonKind.Constructive) colors.onOk else colors.bg
    Box(
        modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = PrimaryHeight)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(PrimaryShape)
            .background(fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = content,
            style = BreakfastType.Button,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillWidth: Boolean = true,
    /** Over the camera ground: white outline and label instead of line and ink. */
    onDark: Boolean = false,
    /** A small icon after the label, such as the chevron on a picker. */
    trailing: ImageVector? = null,
) {
    val colors = LocalBreakfastColors.current
    val ink = if (onDark) colors.cameraMark else colors.ink
    Box(
        modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = SecondaryHeight)
            .alpha(if (enabled) 1f else 0.45f)
            .border(1.dp, if (onDark) colors.cameraMark else colors.line, SecondaryShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text, color = ink, style = BreakfastType.Button, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (trailing != null) Icon(trailing, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun TertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    soft: Boolean = false,
    /** Over the camera ground: white label. */
    onDark: Boolean = false,
) {
    val colors = LocalBreakfastColors.current
    Box(
        modifier
            .heightIn(min = TertiaryHeight)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (onDark) colors.cameraMark else if (soft) colors.soft else colors.ink,
            style = BreakfastType.Button,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Plain `bad` text by default; [outlined] gives the full-width 1dp outline used inside sheets and confirmations. */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalBreakfastColors.current
    if (outlined) {
        Box(
            modifier
                .fillMaxWidth()
                .heightIn(min = PrimaryHeight)
                .alpha(if (enabled) 1f else 0.45f)
                .border(1.dp, colors.bad, PrimaryShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Text(text, color = colors.bad, style = BreakfastType.Button, textAlign = TextAlign.Center) }
    } else {
        Box(
            modifier
                .heightIn(min = TertiaryHeight)
                .alpha(if (enabled) 1f else 0.45f)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text(text, color = colors.bad, style = BreakfastType.Button, textAlign = TextAlign.Center) }
    }
}

/** Unit, meal, ambiguous-food or alternative choice. [detail] is an optional second soft line, such as kcal. */
@Composable
fun ChoiceOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    /** Four-up meal row: 14sp, tighter padding, one line that never breaks mid-word (ellipsis as a last resort). */
    compact: Boolean = false,
    /** False for an action chip (Search, an alternative): a plain button to TalkBack, not a radio button. */
    selectable: Boolean = true,
) {
    val colors = LocalBreakfastColors.current
    Column(
        modifier
            .heightIn(min = ChoiceMinHeight)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) colors.ink else colors.line, ChoiceShape)
            .then(
                if (selectable) Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                else Modifier.clickable(role = Role.Button, onClick = onClick)
            )
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            foodText(label),
            color = if (selected) colors.ink else colors.soft,
            style = when {
                compact && selected -> BreakfastType.SecondaryMedium
                compact -> BreakfastType.Secondary
                selected -> BreakfastType.BodyMedium
                else -> BreakfastType.Body
            },
            maxLines = if (compact) 1 else 2,
            softWrap = !compact,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (compact) TextAlign.Center else TextAlign.Start,
        )
        if (detail != null) Text(detail, color = colors.soft, style = BreakfastType.Secondary)
    }
}

/** 32dp circle with a `+` at the start of a section, in a 44dp box; the touch area is 48dp (see [IconTarget]). */
@Composable
fun AddCircle(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector = Icons.Outlined.Add,
) {
    val colors = LocalBreakfastColors.current
    Box(
        modifier
            .size(IconTarget)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(AddCircleSize).border(1.5.dp, colors.ink, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(16.dp))
        }
    }
}

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
) {
    val colors = LocalBreakfastColors.current
    LabelledField(label, modifier, labelVisible, error, errorMessage, hint, fieldModifier, trailing, required, errorDescription) { textModifier ->
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
    LabelledField(label, modifier, labelVisible = true, error = false, errorMessage = null, hint = null, fieldModifier, trailing, required = false, errorDescription = null) { textModifier ->
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
    input: @Composable (textModifier: Modifier) -> Unit,
) {
    val colors = LocalBreakfastColors.current
    val spokenError = errorMessage ?: errorDescription?.takeIf { error }
    Column(modifier.fillMaxWidth()) {
        if (labelVisible) Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text(label, color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.weight(1f))
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
) = Field(
    label, value, { onValueChange(it.decimalInput()) }, modifier,
    labelVisible = labelVisible, error = error, errorMessage = errorMessage, required = required, hint = hint, amountStyle = amountStyle,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
    keyboardActions = keyboardActions,
    fieldModifier = fieldModifier,
)

/** One of [NutrientFields]: a [DecimalField]'s label, value and, when it has them, error and focus. */
internal class NutrientField(
    val label: String,
    val value: String,
    val onValueChange: (String) -> Unit,
    val error: Boolean = false,
    val errorMessage: String? = null,
    val required: Boolean = false,
    val fieldModifier: Modifier = Modifier,
)

/**
 * The four nutrient fields of a food: two by two, [gap] apart as each mock draws them; with [oneRowWhenItFits], one row
 * while a label and a number with the field's padding fit a quarter (the scan review).
 */
@Composable
internal fun NutrientFields(fields: List<NutrientField>, gap: Dp, oneRowWhenItFits: Boolean = false) {
    val cell: @Composable (NutrientField, Modifier) -> Unit = { field, modifier ->
        DecimalField(field.label, field.value, field.onValueChange, modifier, error = field.error, errorMessage = field.errorMessage, required = field.required, fieldModifier = field.fieldModifier)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // The label sits above the box; the number inside it, within its 16dp padding.
        val fits = oneRowWhenItFits && fitsAQuarter(fields.map { it.label }, BreakfastType.Meta, 0.dp, maxWidth, gap) &&
            fitsAQuarter(listOf(NUTRIENT_SAMPLE), BreakfastType.Body, 32.dp, maxWidth, gap)
        if (fits) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                fields.forEach { cell(it, Modifier.weight(1f)) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                fields.chunked(2).forEach { pair ->
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
internal fun fitsQuarter(content: Dp, width: Dp, gap: Dp): Boolean = content <= (width - gap * 3) / 4

/** Whether the widest of [labels] in [style], with [padding], fits a quarter of [width] with [gap] between the four. */
@Composable
private fun fitsAQuarter(labels: List<String>, style: TextStyle, padding: Dp, width: Dp, gap: Dp): Boolean {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widest = labels.maxOf { measurer.measure(it, style).size.width }
    return fitsQuarter(with(density) { widest.toDp() } + padding, width, gap)
}

/** The g and ml of a basis or a serving, in the row they sit in; each takes [itemModifier]. */
@Composable
internal fun GramMlChoices(selected: MeasureUnit?, onSelect: (MeasureUnit) -> Unit, itemModifier: Modifier) {
    listOf(MeasureUnit.GRAM, MeasureUnit.MILLILITER).forEach { unit ->
        ChoiceOption(unit.label, selected == unit, { onSelect(unit) }, itemModifier)
    }
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

/** A food, meal or entry line: name, soft quantity line, kcal on the right (ink 500 for totals). */
@Composable
fun FoodRow(
    name: String,
    quantityLine: String,
    calories: String,
    modifier: Modifier = Modifier,
    total: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalBreakfastColors.current
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(top = 9.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(foodText(name), color = colors.ink, style = BreakfastType.Body)
            if (quantityLine.isNotBlank()) Text(quantityLine, color = colors.soft, style = BreakfastType.Secondary)
        }
        Text(
            calories,
            color = if (total) colors.ink else colors.soft,
            style = if (total) BreakfastType.BodyMedium else BreakfastType.Body,
            textAlign = TextAlign.End,
        )
    }
}

/** Section heading row: hairline, heading, time, total and the add circle. [emptyText] shows under an empty meal. */
@Composable
fun MealHeader(
    name: String,
    total: String,
    onAdd: () -> Unit,
    addDescription: String,
    modifier: Modifier = Modifier,
    time: String? = null,
    emptyText: String? = null,
) {
    val colors = LocalBreakfastColors.current
    val timeBelow = isLargeFont(LocalDensity.current.fontScale)
    Column(modifier.fillMaxWidth().padding(top = 30.dp)) {
        Hairline()
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (timeBelow) {
                Column(Modifier.weight(1f)) {
                    Text(name, color = colors.ink, style = BreakfastType.Section)
                    if (!time.isNullOrBlank()) Text(time, color = colors.soft, style = BreakfastType.Meta)
                }
            } else {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text(name, color = colors.ink, style = BreakfastType.Section)
                    if (!time.isNullOrBlank()) Text(time, color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Text(total, color = colors.ink, style = BreakfastType.Title, modifier = Modifier.padding(end = 8.dp))
            AddCircle(addDescription, onAdd)
        }
        if (emptyText != null) Text(emptyText, color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Date title with a previous/next pair; a "Go To Today" text button appears when [showToday] is set. */
@Composable
fun DayHeader(
    title: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    showToday: Boolean = false,
    onToday: () -> Unit = {},
    onTitleClick: (() -> Unit)? = null,
    /** False on today: Next is dimmed and does nothing. */
    nextEnabled: Boolean = true,
) {
    val colors = LocalBreakfastColors.current
    Row(modifier.fillMaxWidth().heightIn(min = HeaderHeight), verticalAlignment = Alignment.CenterVertically) {
        // The title and its chevron form one click target that opens the date picker.
        Row(
            Modifier
                .weight(1f)
                .then(if (onTitleClick != null) Modifier.clickable(role = Role.Button, onClick = onTitleClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = colors.ink, style = BreakfastType.Title)
            if (onTitleClick != null) Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = colors.soft, modifier = Modifier.padding(start = 4.dp).size(16.dp))
        }
        if (showToday) TertiaryButton("Go To Today", onToday)
        IconButton(onClick = onPrevious, modifier = Modifier.size(IconTarget)) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous day", tint = colors.ink, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onNext, enabled = nextEnabled, modifier = Modifier.size(IconTarget)) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Next day", tint = colors.ink.copy(alpha = if (nextEnabled) 1f else 0.4f), modifier = Modifier.size(22.dp))
        }
    }
}

/** One macro column: [share] is its fraction of the 2dp track; [value] is ink and [unit] soft. */
data class MacroStat(val label: String, val value: String, val unit: String, val share: Float)

/** The one big figure of a screen: 72sp kcal, its caption, and three macro columns (none when [macros] is empty). */
@Composable
fun DayTotal(
    kilocalories: String,
    macros: List<MacroStat>,
    modifier: Modifier = Modifier,
    caption: String = "kilocalories today",
    /** One soft line under the macros, or nothing. */
    note: String? = null,
) {
    val colors = LocalBreakfastColors.current
    val stacked = isLargeFont(LocalDensity.current.fontScale)
    Column(modifier.fillMaxWidth()) {
        Text(kilocalories, color = colors.ink, style = BreakfastType.Hero)
        Text(caption, color = colors.soft, style = BreakfastType.Secondary)
        if (macros.isEmpty()) {
            // Only the kcal: "Macros on today" is hidden.
        } else if (stacked) {
            // Large font: one macro per row, value, unit and label on one line, each with its own bar above.
            Column(Modifier.fillMaxWidth().padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                macros.forEach { macro ->
                    Column(Modifier.fillMaxWidth()) {
                        MacroTrack(macro.share)
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                            Text(macro.value, color = colors.ink, style = BreakfastType.MacroValue)
                            Text(" ${macro.unit}", color = colors.soft, style = BreakfastType.Secondary)
                            Text(macro.label, color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                macros.forEach { macro ->
                    Column(Modifier.weight(1f)) {
                        MacroTrack(macro.share)
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                            Text(macro.value, color = colors.ink, style = BreakfastType.MacroValue)
                            Text(" ${macro.unit}", color = colors.soft, style = BreakfastType.Secondary)
                        }
                        Text(macro.label, color = colors.soft, style = BreakfastType.Meta)
                    }
                }
            }
        }
        note?.let { Text(it, color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(top = 10.dp)) }
    }
}

/** 2dp `line` track with the macro's share as a 2dp ink fill. */
@Composable
internal fun MacroTrack(share: Float) {
    val colors = LocalBreakfastColors.current
    Box(Modifier.fillMaxWidth().height(2.dp).background(colors.line)) {
        Box(Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).height(2.dp).background(colors.ink))
    }
}

/** The bottom padding a base screen's list keeps, so its last row clears the [ComposerFade] floating over it. */
val BottomAreaClearance = 260.dp

/**
 * A `bg` fade with 16dp gutters, above the system navigation bar: the one bottom area of the base screens (Daily,
 * Meal, Library, Recipes; `app/BaseBottomArea.kt`), drawn once over whichever shows, so the bar and the tabs never
 * move between them. The list behind it keeps [BottomAreaClearance] at its end.
 */
@Composable
fun ComposerFade(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalBreakfastColors.current
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to colors.bg.copy(alpha = 0f), 0.22f to colors.bg))
            .navigationBarsPadding()
            .padding(start = SnackbarGutter, end = SnackbarGutter, top = 12.dp),
        content = content,
    )
}

/**
 * The base screens' ink bar (`app/BaseBottomArea.kt`): [text] (the Log opener, [ComposerText], or the Library's search)
 * then the scan icon.
 */
@Composable
fun Composer(onScan: () -> Unit, modifier: Modifier = Modifier, text: @Composable RowScope.() -> Unit) {
    InkBar(modifier) {
        text()
        IconButton(onClick = onScan, modifier = Modifier.size(IconTarget)) {
            Icon(ScanBarcodeIcon, contentDescription = "Scan barcode", tint = LocalBreakfastColors.current.bg)
        }
    }
}

/**
 * The [Composer]'s Log opener: the placeholder, or the typed draft at full strength ([draft]) cut as [longDraft] says;
 * a tap opens the Log page.
 */
@Composable
fun ComposerText(placeholder: String, onClick: () -> Unit, modifier: Modifier = Modifier, draft: Boolean = false, longDraft: LongDraft = LongDraft.START) {
    val colors = LocalBreakfastColors.current
    val text = placeholder.replace('\n', ' ')
    val color = if (draft) colors.bg else colors.bg.barPlaceholder
    val textModifier = modifier.clickable(role = Role.Button, onClick = onClick)
    if (draft && longDraft == LongDraft.END) {
        EndOfText(text, color, textModifier)
    } else {
        Text(
            text,
            color = color,
            style = BreakfastType.Body,
            maxLines = if (draft && longDraft == LongDraft.TWO_LINES) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = textModifier,
        )
    }
}

/** One line of [text] that keeps its end: cut at the start with "…" when it is wider than the space. */
@Composable
private fun EndOfText(text: String, color: Color, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val style = BreakfastType.Body
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth
        val fitted = remember(text, width, measurer) {
            if (!constraints.hasBoundedWidth) text
            else startEllipsized(text) { candidate -> measurer.measure(candidate, style, maxLines = 1, softWrap = false).size.width <= width }
        }
        // TalkBack reads the whole draft, not the shortened line.
        Text(
            fitted, color = color, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
            modifier = Modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
        )
    }
}

/**
 * The ink bar shared by [Composer] and the Library search: an ink fill, radius 16, min height 52.
 * Its content sits in a row with 16dp start padding and [endPadding] at the end.
 */
@Composable
fun InkBar(modifier: Modifier = Modifier, endPadding: Dp = 4.dp, content: @Composable RowScope.() -> Unit) {
    val colors = LocalBreakfastColors.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = ComposerHeight)
            .clip(ComposerShape)
            .background(colors.ink)
            .padding(start = 16.dp, end = endPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The bottom bar's tabs, in their order. */
enum class NavTab(val label: String) { MEALS("Meals"), LIBRARY("Library"), RECIPES("Recipes") }

/** The Settings gear as an item of the bar: after the tabs. */
private const val GEAR = 3

/**
 * The text tabs ([NavTab], in order), centred with a 40dp gap, and the Settings icon at the right end. The active item
 * (the gear while [settingsOpen]) is ink, the others soft, and a 2dp ink line sits under it; when it changes, the line
 * slides to the new item and the emphasis crosses over ([Motion.nav]; at once with reduced motion).
 */
@Composable
fun BottomNav(
    selected: NavTab,
    onMeals: () -> Unit,
    onLibrary: () -> Unit,
    onRecipes: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    settingsOpen: Boolean = false,
) {
    val colors = LocalBreakfastColors.current
    val motion = rememberMotion()
    val active = if (settingsOpen) GEAR else selected.ordinal
    // Where each item sits in the bar, in px: the line goes under the one that is active.
    val spans = remember { NavSpans() }
    val start = remember { Animatable(0f) }
    val width = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val target = spans.of(active)
    LaunchedEffect(active, target) {
        val (toStart, toWidth) = target ?: return@LaunchedEffect
        if (!placed) {
            // The first frame: right under the item; from then on the line slides.
            start.snapTo(toStart)
            width.snapTo(toWidth)
            placed = true
        }
        launch { start.animateTo(toStart, motion.nav()) }
        width.animateTo(toWidth, motion.nav())
    }
    val lineColor = colors.ink
    Row(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { spans.bar = it; spans.measure() }
            .drawBehind {
                if (placed && width.value > 0f) {
                    val y = size.height - 8.dp.toPx()
                    drawLine(lineColor, Offset(start.value, y), Offset(start.value + width.value, y), strokeWidth = 2.dp.toPx())
                }
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        fun Modifier.span(item: Int) = onGloballyPositioned { spans.items[item] = it; spans.measure() }
        // Equal sides keep the tabs centred; each side is at least the icon's width.
        Spacer(Modifier.weight(1f).widthIn(min = IconTarget))
        Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
            NavTab.entries.forEachIndexed { index, tab ->
                if (index > 0) Spacer(Modifier.width(40.dp))
                NavTabButton(tab.label, selected = index == active, motion = motion, labelModifier = Modifier.span(index), onClick = when (tab) {
                    NavTab.MEALS -> onMeals
                    NavTab.LIBRARY -> onLibrary
                    NavTab.RECIPES -> onRecipes
                })
            }
        }
        Box(Modifier.weight(1f).widthIn(min = IconTarget), contentAlignment = Alignment.CenterEnd) {
            val tint by animateColorAsState(if (active == GEAR) colors.ink else colors.soft, motion.nav(), label = "gear")
            IconButton(onClick = onSettings, modifier = Modifier.size(IconTarget)) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = tint, modifier = Modifier.size(22.dp).span(GEAR))
            }
        }
    }
}

/**
 * Where the bar's items sit, measured in the bar itself: whichever of the bar and an item is placed last measures them
 * all, so no inset between the bar and the window counts. A span is written only when it moved, so the bar riding the
 * keyboard (each frame of its animation) does not draw it again.
 */
private class NavSpans {
    var bar: LayoutCoordinates? = null
    val items = arrayOfNulls<LayoutCoordinates>(GEAR + 1)
    private val spans = mutableStateMapOf<Int, Pair<Float, Float>>()

    fun of(item: Int): Pair<Float, Float>? = spans[item]

    fun measure() {
        val bar = bar?.takeIf { it.isAttached } ?: return
        items.forEachIndexed { item, coords ->
            if (coords == null || !coords.isAttached) return@forEachIndexed
            val span = bar.localPositionOf(coords, Offset.Zero).x to coords.size.width.toFloat()
            if (spans[item] != span) spans[item] = span
        }
    }
}

@Composable
private fun NavTabButton(label: String, selected: Boolean, motion: Motion, labelModifier: Modifier, onClick: () -> Unit) {
    val colors = LocalBreakfastColors.current
    val color by animateColorAsState(if (selected) colors.ink else colors.soft, motion.nav(), label = "tab")
    Box(
        Modifier
            .heightIn(min = TertiaryHeight)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, style = BreakfastType.SecondaryMedium, modifier = labelModifier)
    }
}

/** Bottom sheet on `bg` with a 36x5 `line` grabber and 24dp padding over a `scrim`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalBreakfastColors.current
    // Material's own slide up is the sheet's motion (this is rememberModalBottomSheetState with its start chosen). With
    // reduced motion the sheet starts where it would stop, so it is simply there (a swipe or a tap outside still lets
    // Material slide it away). The start is read once: a sheet already open stays as it is when Motion changes.
    val reduced = rememberMotionReduced()
    val density = LocalDensity.current
    val sheetState = rememberSaveable(skipPartiallyExpanded, saver = SheetState.Saver(skipPartiallyExpanded, { true }, density, false)) {
        val start = if (!reduced) SheetValue.Hidden else if (skipPartiallyExpanded) SheetValue.Expanded else SheetValue.PartiallyExpanded
        SheetState(skipPartiallyExpanded, density, start, { true }, false)
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = colors.bg,
        contentColor = colors.ink,
        scrimColor = colors.scrim,
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 8.dp).size(width = 36.dp, height = 5.dp).background(colors.line, CircleShape))
        },
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp), content = content)
    }
}

/** Ink snackbar above the composer. The action is `bg` 500 on the right. */
@Composable
fun Snackbar(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBreakfastColors.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(SnackbarShape)
            .background(colors.ink)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = colors.bg, style = BreakfastType.Secondary, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                color = colors.bg,
                style = BreakfastType.Button,
                modifier = Modifier.clickable(role = Role.Button, onClick = onAction).padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * A confirmation with no action ("Logged!", `app/Confirmations`) as a [Snackbar], gliding in and out with [Reveal].
 * TalkBack reads each confirmation once from the root (`IronApp`), not from this node, which comes and goes.
 */
@Composable
internal fun ConfirmationSnackbar(message: String?, modifier: Modifier = Modifier) {
    // [modifier] places the whole reveal (an align in a Box must sit on it); the 8dp gap comes and goes with the bar.
    Reveal(message, modifier) { Snackbar(it, modifier = Modifier.padding(bottom = 8.dp)) }
}

/**
 * 2dp track with a 2dp ink bar sweeping across it, and a soft caption. No spinners. [onDark] draws it in white over the
 * camera ground. With reduced motion the bar stays still in the middle of the track: the same look, no sweep.
 */
@Composable
fun LinearProgress(caption: String, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val colors = LocalBreakfastColors.current
    val track = if (onDark) colors.cameraMark.copy(alpha = 0.24f) else colors.line
    val bar = if (onDark) colors.cameraMark else colors.ink
    val captionColor = if (onDark) colors.cameraMark.copy(alpha = 0.7f) else colors.soft
    val phase = if (rememberMotionReduced()) STILL_PROGRESS_PHASE else {
        val transition = rememberInfiniteTransition(label = "linear-progress")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1400, easing = LinearEasing)),
            label = "linear-progress-phase",
        ).value
    }
    Column(modifier.fillMaxWidth().semantics { contentDescription = caption }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(2.dp)) {
            val mid = size.height / 2
            drawLine(track, Offset(0f, mid), Offset(size.width, mid), strokeWidth = size.height)
            val barWidth = size.width * 0.25f
            val start = (size.width + barWidth) * phase - barWidth
            drawLine(bar, Offset(start, mid), Offset(start + barWidth, mid), strokeWidth = size.height)
        }
        Text(caption, color = captionColor, style = BreakfastType.Secondary)
    }
}

/** The sweep's phase that puts the bar in the middle of the track, where it stays when motion is reduced. */
private const val STILL_PROGRESS_PHASE = 0.5f

/**
 * A screen's header: back arrow (none on a tab, [onBack] null, whose title then starts at the gutter), a title
 * (optionally a soft second line) and a trailing slot.
 */
@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    val colors = LocalBreakfastColors.current
    Row(modifier.fillMaxWidth().heightIn(min = HeaderHeight), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.size(IconTarget)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = colors.ink, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = if (onBack != null) 4.dp else 0.dp)) {
            Text(title, color = colors.ink, style = BreakfastType.Title)
            if (subtitle != null) Text(subtitle, color = colors.soft, style = BreakfastType.Meta)
        }
        trailing()
    }
}

/**
 * The shell of a full screen over the base: `bg`, the safe area, the gutter and the [TopBar] 8dp down. With [scroll] the
 * header and [content] scroll together; without it [content] places its own scrolling part. [bottom] stays under them,
 * above the keyboard.
 */
@Composable
fun SubScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
    scroll: Boolean = true,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxSize(), color = LocalBreakfastColors.current.bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(
                Modifier
                    .weight(1f)
                    .then(if (scroll) Modifier.verticalScroll(rememberScrollState()).padding(bottom = 12.dp) else Modifier)
                    .padding(horizontal = Gutter),
            ) {
                TopBar(title, onBack, Modifier.padding(top = 8.dp), subtitle = subtitle, trailing = trailing)
                content()
            }
            if (bottom != null) Column(Modifier.fillMaxWidth().padding(horizontal = Gutter), content = bottom)
        }
    }
}

/** A food's or recipe's page: the [SubScreen] with Edit in its bar, a hairline, then [name]; [content] follows. */
@Composable
internal fun ItemPage(title: String, onBack: () -> Unit, name: String, onEdit: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    SubScreen(title, onBack, trailing = { if (onEdit != null) TertiaryButton("Edit", onEdit) }) {
        Hairline(Modifier.padding(top = 8.dp))
        Text(foodText(name), color = LocalBreakfastColors.current.ink, style = BreakfastType.Section, modifier = Modifier.padding(top = 8.dp))
        content()
    }
}

/** One line of facts: the label, then the value in ink with its [unit] in soft, or, with no unit, the value in soft. */
@Composable
internal fun FactRow(label: String, value: String, unit: String? = null) {
    val colors = LocalBreakfastColors.current
    val shownValue: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = if (unit == null) colors.soft else colors.ink, style = if (unit == null) BreakfastType.Body else BreakfastType.BodyMedium, textAlign = TextAlign.End)
            if (unit != null) Text(" $unit", color = colors.soft, style = BreakfastType.Meta, maxLines = 1)
        }
    }
    if (unit != null && isLargeFont(LocalDensity.current.fontScale.coerceAtLeast(1f))) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(label, color = colors.ink, style = BreakfastType.Body)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { shownValue() }
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            Text(label, color = colors.ink, style = BreakfastType.Body, modifier = Modifier.weight(1f))
            shownValue()
        }
    }
}

/** The nutrition lines of a page, in the design's order: Energy, then the macros when there are any, each a value and unit. */
@Composable
internal fun NutritionFacts(energy: String, fat: Pair<String, String>? = null, carbs: Pair<String, String>? = null, protein: Pair<String, String>? = null) {
    FactRow("Energy", energy, "kcal")
    listOf("Fat" to fat, "Carbohydrate" to carbs, "Protein" to protein).forEach { (label, fact) ->
        if (fact != null) FactRow(label, fact.first, fact.second)
    }
}

/**
 * A centred status under a header: the title, soft [lines], then its [actions], [top] down as each mock draws it.
 */
@Composable
internal fun StatusBlock(title: String, lines: List<String>, top: Dp, actions: @Composable ColumnScope.() -> Unit) {
    val colors = LocalBreakfastColors.current
    Column(Modifier.fillMaxWidth().padding(top = top), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = colors.ink, style = BreakfastType.Title, modifier = Modifier.semantics { heading() })
        lines.forEachIndexed { index, line ->
            Text(line, color = colors.soft, style = BreakfastType.Secondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = if (index == 0) 8.dp else 4.dp).widthIn(max = 290.dp))
        }
        Column(Modifier.fillMaxWidth().padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = actions)
    }
}

/** A two-way question over the screen: [confirm] or [dismiss]; [text] says what it does. */
@Composable
internal fun ConfirmDialog(confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, dismiss: String = "Cancel", text: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text(dismiss) } },
        text = text?.let { { Text(it) } },
    )
}

/** Section heading inside a screen (meal, form section). */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, color = LocalBreakfastColors.current.ink, style = BreakfastType.Section, modifier = modifier)
}

/** 1dp `line` hairline. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(LocalBreakfastColors.current.line))
}

/** Font scale from which the large-font layouts take over (2.0 is the reference; 1.3 keeps the normal layout). */
internal const val LargeFontScale = 1.8f

/** True at the large-font layout: stacked macros and meal times under headings, and stacked add bars. */
internal fun isLargeFont(fontScale: Float): Boolean = fontScale >= LargeFontScale

/** "Meal" over the [MealChoices], one row when it fits (M-Library-AmountSheet, M-Entry-FullEdit, M-Scan-Review). */
@Composable
internal fun MealField(selected: MealSlot, onSelect: (MealSlot) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    Column(modifier.fillMaxWidth()) {
        Text("Meal", color = LocalBreakfastColors.current.soft, style = BreakfastType.Meta, modifier = Modifier.padding(bottom = 4.dp))
        MealChoices(selected, onSelect, singleRow = true, compact = compact)
    }
}

/** The meal choices: a 2x2 grid, or one row of four when [singleRow] (Library repeat and amount sheets). */
@Composable
fun MealChoices(selected: MealSlot?, onSelect: (MealSlot) -> Unit, modifier: Modifier = Modifier, singleRow: Boolean = false, compact: Boolean = false) {
    if (singleRow) {
        // One row only when the longest meal name fits on one line in a quarter; otherwise two by two, never a broken word.
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val style = if (compact) BreakfastType.SecondaryMedium else BreakfastType.BodyMedium
            // 6dp apart in a sheet's row, 8dp in the scan review's compact one, as the mocks draw them.
            val gap = if (compact) 8.dp else 6.dp
            if (fitsAQuarter(MealSlot.entries.map { it.label() }, style, (if (compact) 16.dp else 24.dp) + 2.dp, maxWidth, gap)) {
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    MealSlot.entries.forEach { meal -> ChoiceOption(meal.label(), selected == meal, { onSelect(meal) }, Modifier.weight(1f), compact = compact) }
                }
            } else MealChoices(selected, onSelect, Modifier.fillMaxWidth(), singleRow = false, compact = compact)
        }
        return
    }
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MealSlot.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { meal ->
                    ChoiceOption(meal.label(), selected == meal, { onSelect(meal) }, Modifier.weight(1f), compact = compact)
                }
            }
        }
    }
}
