@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.iron.breakfast.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.label
import com.iron.breakfast.ui.theme.ReceiptFieldPillShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iron.breakfast.ui.theme.BoxGrey
import com.iron.breakfast.ui.theme.Ink
import com.iron.breakfast.ui.theme.InkSoft
import com.iron.breakfast.ui.theme.NavIndicator
import com.iron.breakfast.ui.theme.NavIndicatorInk
import com.iron.breakfast.ui.theme.OnGround
import com.iron.breakfast.ui.theme.Paper
import com.iron.breakfast.ui.theme.PenBlue
import com.iron.breakfast.ui.theme.PenOnDark
import com.iron.breakfast.ui.theme.PenOnDarkInk
import com.iron.breakfast.ui.theme.PenTonal
import com.iron.breakfast.ui.theme.PenTonalInk
import com.iron.breakfast.ui.theme.PrintRed
import com.iron.breakfast.ui.theme.TicketBandHeight
import com.iron.breakfast.ui.theme.TicketAddHeight
import com.iron.breakfast.ui.theme.TornEdgeHeight
import com.iron.breakfast.ui.theme.TornEdgeTooth
import com.iron.breakfast.ui.theme.QuickAddHeight
import com.iron.breakfast.ui.theme.ReceiptPaperShape
import com.iron.breakfast.ui.theme.ReceiptPillShape
import com.iron.breakfast.ui.theme.ReceiptStickerShape
import com.iron.breakfast.ui.theme.Rule
import com.iron.breakfast.ui.theme.Sticker
import com.iron.breakfast.ui.theme.Tray
import com.iron.breakfast.ui.theme.receiptHeadingStyle
import com.iron.breakfast.ui.theme.usesSingleWidthAt
import androidx.compose.ui.platform.LocalDensity

@Composable
fun ReceiptTornEdge(
    fill: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Canvas(modifier.fillMaxWidth().height(TornEdgeHeight)) {
        val tooth = with(density) { TornEdgeTooth.toPx() }
        val height = size.height
        val path = Path().apply {
            moveTo(0f, 0f)
            var x = 0f
            var down = true
            while (x <= size.width) {
                lineTo(x, if (down) height else 0f)
                x += tooth
                down = !down
            }
            lineTo(size.width, 0f)
            close()
        }
        drawPath(path, color = fill, style = Fill)
    }
}

@Composable
fun ReceiptTicket(
    mealName: String,
    total: String,
    time: String? = null,
    addFoodLabel: String? = null,
    onAddFood: (() -> Unit)? = null,
    onMealClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val stackTime = usesSingleWidthAt(LocalDensity.current.fontScale) && time != null
    val titleStyle = receiptHeadingStyle(mealName)
    Column(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .background(Paper),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = TicketBandHeight)
                    .background(PrintRed)
                    .clickable(enabled = onMealClick != null, role = Role.Button) { onMealClick?.invoke() }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (stackTime) {
                    Column(Modifier.weight(1f)) {
                        Text(mealName, style = titleStyle, color = OnGround, fontWeight = FontWeight.Bold)
                        Text(time!!, style = MaterialTheme.typography.bodySmall, color = com.iron.breakfast.ui.theme.OnRedSoft)
                    }
                } else {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(mealName, style = titleStyle, color = OnGround, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                        if (time != null) Text(time, style = MaterialTheme.typography.bodySmall, color = com.iron.breakfast.ui.theme.OnRedSoft)
                    }
                }
                Text(total, style = MaterialTheme.typography.titleLarge, color = OnGround, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Column(content = content)
            if (addFoodLabel != null && onAddFood != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = TicketAddHeight)
                        .background(PenTonal)
                        .clickable(role = Role.Button, onClick = onAddFood)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = PenTonalInk, modifier = Modifier.size(20.dp))
                    Text(addFoodLabel, color = PenTonalInk, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        ReceiptTornEdge(fill = if (addFoodLabel != null) PenTonal else Paper)
    }
}

@Composable
fun ReceiptSlip(
    label: String,
    total: String,
    unit: String = "kcal",
    macros: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    pending: String? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Surface(modifier = modifier, color = Paper, shape = ReceiptPaperShape) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = InkSoft, modifier = Modifier.weight(1f).padding(end = 8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(total, style = MaterialTheme.typography.displayLarge, color = Ink)
                    pending?.let { Text(" $it", style = MaterialTheme.typography.headlineLarge, color = PrintRed) }
                    Text(" $unit", style = MaterialTheme.typography.titleSmall, color = InkSoft)
                }
            }
            if (macros.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(Modifier.fillMaxWidth().receiptDashedRule().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    macros.forEach { Text(it, style = MaterialTheme.typography.labelMedium, color = Ink) }
                }
            }
            content?.invoke()
        }
    }
}

/** The 1.5dp dashed paper rule drawn along the top edge of a row. */
fun Modifier.receiptDashedRule(): Modifier = drawBehind {
    drawLine(
        color = Rule,
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(size.width, 0f),
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
    )
}

@Composable
fun ReceiptEntryRow(
    name: String,
    quantityLine: String,
    calories: String,
    source: String,
    approximate: String? = null,
    fresh: Boolean = false,
    first: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** Small grey line under the kcal figure, such as "/100 ml". */
    calorieCaption: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (fresh) Modifier.background(com.iron.breakfast.ui.theme.PaperFresh) else Modifier)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .then(if (first) Modifier else Modifier.receiptDashedRule())
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, color = Ink, style = MaterialTheme.typography.bodyLarge)
            if (approximate != null || quantityLine.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (approximate != null) ApproxTag(approximate)
                if (quantityLine.isNotBlank()) Text(quantityLine, color = InkSoft, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f, fill = false))
            }
        }
        Column(Modifier.padding(start = 10.dp), horizontalAlignment = Alignment.End) {
            Text(calories, color = Ink, style = MaterialTheme.typography.displaySmall)
            if (calorieCaption != null) Text(calorieCaption, color = InkSoft, style = MaterialTheme.typography.labelSmall)
        }
        SourceBox(source, Modifier.padding(start = 10.dp, top = 1.dp))
    }
}

/** Spoken and written name for a source letter. */
fun sourceLabel(letter: String): String = when (letter) {
    "B" -> "Bundled"
    "P" -> "Personal"
    "O" -> "Open Food Facts"
    else -> "Unknown source"
}

@Composable
fun SourceBox(source: String, modifier: Modifier = Modifier) {
    val description = "Source: ${sourceLabel(source).lowercase()}"
    Box(modifier.defaultMinSize(22.dp, 22.dp).border(1.5.dp, BoxGrey, RoundedCornerShape(3.dp)).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Text(source, color = InkSoft, style = MaterialTheme.typography.labelSmall)
    }
}

/** Round 48dp tonal button for repeated row actions. */
@Composable
fun ReceiptTonalIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp).background(if (enabled) PenTonal else PenTonal.copy(alpha = 0.4f), CircleShape),
    ) { Icon(icon, contentDescription = contentDescription, tint = PenTonalInk) }
}

/** Circular outlined barcode-scan button that sits beside a search pill. */
@Composable
fun ReceiptScanButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp).border(1.5.dp, PenOnDark, CircleShape)) {
        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan barcode", tint = PenOnDarkInk)
    }
}

@Composable
fun ApproxTag(text: String, modifier: Modifier = Modifier) = ReceiptRedTag("≈ $text", modifier)

/** Small print-red chip: the approximation mark and the "Required" marker share this one form. */
@Composable
fun ReceiptRedTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.background(PrintRed, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
        color = OnGround,
        style = MaterialTheme.typography.labelSmall,
    )
}

enum class ReceiptTokenKind { Meal, Quantity, Food }

data class ReceiptTokenRange(val range: IntRange, val kind: ReceiptTokenKind)

/** Applies the locked quick-add emphasis without changing cursor offsets. */
class ReceiptTokenVisualTransformation(
    private val tokens: List<ReceiptTokenRange>,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = AnnotatedString.Builder(text)
        tokens.forEach { token ->
            val start = token.range.first.coerceIn(0, text.length)
            val end = (token.range.last + 1).coerceIn(start, text.length)
            if (start < end) styled.addStyle(token.kind.style(), start, end)
        }
        return TransformedText(styled.toAnnotatedString(), OffsetMapping.Identity)
    }
}

private fun ReceiptTokenKind.style(): SpanStyle = when (this) {
    ReceiptTokenKind.Meal -> SpanStyle(color = OnGround, background = PrintRed, fontWeight = FontWeight.Bold)
    ReceiptTokenKind.Quantity -> SpanStyle(fontWeight = FontWeight.Bold)
    ReceiptTokenKind.Food -> SpanStyle(textDecoration = TextDecoration.Underline)
}

@Composable
fun ReceiptSticker(
    name: String,
    value: String,
    detail: String,
    fields: List<String> = emptyList(),
    approximateDetail: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .widthIn(max = 180.dp)
            .shadow(1.dp, ReceiptStickerShape)
            .background(Sticker, ReceiptStickerShape)
            .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 9.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, color = Ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(value, color = Ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (approximateDetail) ApproxTag(detail) else ReceiptTag(detail)
            fields.forEach { field ->
                ReceiptTag(field)
            }
        }
    }
}

/** Small outlined grey tag: "2 tbsp" on a sticker, "from scan" on a field. */
@Composable
fun ReceiptTag(value: String, modifier: Modifier = Modifier) {
    Text(
        value,
        color = InkSoft,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier.border(1.5.dp, Rule, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp),
    )
}

@Composable
fun ReceiptChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingIcon = if (selected) ({ Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp)) }) else null,
        modifier = modifier.heightIn(min = 48.dp),
        shape = ReceiptPillShape,
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = Rule, selectedBorderColor = PenBlue, borderWidth = 1.5.dp, selectedBorderWidth = 2.dp),
        colors = FilterChipDefaults.filterChipColors(containerColor = Color.Transparent, labelColor = PenTonalInk, selectedContainerColor = PenTonal, selectedLabelColor = PenTonalInk),
    )
}

@Composable
fun ReceiptActionChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, trailingIcon: ImageVector? = null) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingIcon = trailingIcon?.let { icon -> { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) } },
        modifier = modifier.heightIn(min = 48.dp),
        shape = ReceiptPillShape,
        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = PenOnDark, borderWidth = 1.5.dp),
        colors = AssistChipDefaults.assistChipColors(labelColor = PenOnDarkInk, trailingIconContentColor = PenOnDarkInk),
    )
}

@Composable
fun ReceiptTonalChip(
    label: String,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingIcon = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) } },
        modifier = modifier.heightIn(min = 48.dp),
        shape = ReceiptPillShape,
        border = null,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = PenTonal,
            labelColor = PenTonalInk,
            leadingIconContentColor = PenTonalInk,
        ),
    )
}

@Composable
fun ReceiptQuickAddField(
    value: String,
    onValueChange: (String) -> Unit,
    onLog: () -> Unit,
    placeholder: String = "2 eggs, 1 banana",
    expanded: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onImeDone: () -> Unit = onLog,
    logContentDescription: String = "Log",
    modifier: Modifier = Modifier,
) {
    var focused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (expanded) 76.dp else QuickAddHeight)
            .background(Paper, RoundedCornerShape(if (expanded) 26.dp else 27.dp))
            .border(if (focused) 2.dp else 0.dp, PenOnDark, RoundedCornerShape(if (expanded) 26.dp else 27.dp))
            .padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
            singleLine = !expanded,
            minLines = if (expanded) 2 else 1,
            maxLines = if (expanded) 2 else 1,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onImeDone() }),
            decorationBox = { inner -> Box { if (value.isBlank()) Text(placeholder, color = InkSoft, style = MaterialTheme.typography.bodyMedium); inner() } },
        )
        IconButton(onClick = onLog, enabled = value.isNotBlank(), modifier = Modifier.size(46.dp)) {
            if (value.isBlank()) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = logContentDescription, tint = BoxGrey) }
            } else {
                Surface(color = PenBlue, shape = RoundedCornerShape(23.dp), modifier = Modifier.size(46.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = logContentDescription, tint = Color.White) }
                }
            }
        }
    }
}

@Composable
fun ReceiptSnackbar(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 48.dp),
        color = Paper,
        contentColor = Ink,
        shape = ReceiptPaperShape,
        shadowElevation = 1.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel, color = PenTonalInk, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = Paper,
        contentColor = Ink,
        scrimColor = com.iron.breakfast.ui.theme.Scrim,
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(width = 32.dp, height = 4.dp).background(BoxGrey, RoundedCornerShape(2.dp)))
        },
        content = content,
    )
}

@Composable
fun ReceiptBottomTray(content: @Composable () -> Unit, nav: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.background(Tray)) {
        Box(Modifier.padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 8.dp)) { content() }
        nav()
    }
}

@Composable
fun ReceiptNavigationBar(selectedDaily: Boolean, onDaily: () -> Unit, onLibrary: () -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(
        modifier = modifier,
        containerColor = Tray,
        contentColor = OnGround,
    ) {
        NavigationBarItem(
            selected = selectedDaily,
            onClick = onDaily,
            icon = { Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = "Daily") },
            label = { Text("Daily", style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (selectedDaily) FontWeight.Bold else FontWeight.Normal)) },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = NavIndicatorInk, selectedTextColor = OnGround, indicatorColor = NavIndicator, unselectedIconColor = com.iron.breakfast.ui.theme.OnGroundSoft, unselectedTextColor = com.iron.breakfast.ui.theme.OnGroundSoft),
        )
        NavigationBarItem(
            selected = !selectedDaily,
            onClick = onLibrary,
            icon = { Icon(Icons.Outlined.Straighten, contentDescription = "Library") },
            label = { Text("Library", style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (!selectedDaily) FontWeight.Bold else FontWeight.Normal)) },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = NavIndicatorInk, selectedTextColor = OnGround, indicatorColor = NavIndicator, unselectedIconColor = com.iron.breakfast.ui.theme.OnGroundSoft, unselectedTextColor = com.iron.breakfast.ui.theme.OnGroundSoft),
        )
    }
}


/** Sub-screen header: arrow_back, a bold title (optionally a soft second line) and a trailing slot. */
@Composable
fun ReceiptTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OnGround)
        }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(title, color = OnGround, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = com.iron.breakfast.ui.theme.OnGroundSoft, style = MaterialTheme.typography.bodySmall)
        }
        trailing()
    }
}

/** Rounded paper text field with a clear button, used for the search screens. */
@Composable
fun ReceiptSearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
) {
    Row(
        modifier
            .heightIn(min = 54.dp)
            .background(Paper, ReceiptFieldPillShape)
            .border(2.dp, PenOnDark, ReceiptFieldPillShape)
            .padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Ink),
            keyboardOptions = KeyboardOptions(imeAction = if (onSearch != null) ImeAction.Search else ImeAction.Done),
            keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }, onDone = { onSearch?.invoke() }),
        )
        if (value.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.size(46.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Clear", tint = InkSoft)
            }
        }
    }
}

/**
 * Labelled paper field. The label sits above on the dark ground (or on paper when [labelColor] says so);
 * a 2dp ring shows focus, or print-red when [error] is set. [trailing] holds a tag or a small button.
 */
@Composable
fun ReceiptField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    labelColor: Color = com.iron.breakfast.ui.theme.OnGroundSoft,
    container: Color = Paper,
    outline: Color? = null,
    error: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    labelTrailing: (@Composable () -> Unit)? = null,
    /** Applied to the text field itself, for a FocusRequester. */
    fieldModifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    var focused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val ring = when {
        error -> PrintRed
        focused -> PenOnDark
        else -> null
    }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = labelColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clearAndSetSemantics { })
            labelTrailing?.invoke()
        }
        Row(
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .background(container, ReceiptPaperShape)
                .then(if (ring != null) Modifier.border(2.dp, ring, ReceiptPaperShape) else if (outline != null) Modifier.border(1.5.dp, outline, ReceiptPaperShape) else Modifier)
                .padding(horizontal = 14.dp, vertical = if (singleLine) 0.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .then(fieldModifier)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = label },
                singleLine = singleLine,
                minLines = minLines,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Ink),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
                decorationBox = { inner ->
                    Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) Text(placeholder, color = InkSoft, style = MaterialTheme.typography.bodyMedium)
                        inner()
                    }
                },
            )
            trailing?.invoke()
        }
    }
}

/** Full-width 48dp pill buttons. Solid primary is the one pen-blue action; tonal and outlined are secondary. */
@Composable
fun ReceiptPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, fillWidth: Boolean = true) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier).heightIn(min = 48.dp),
        shape = ReceiptPillShape,
    ) { Text(text, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
}

@Composable
fun ReceiptTonalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = ReceiptPillShape,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = PenTonal, contentColor = PenTonalInk),
    ) { Text(text, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
}

@Composable
fun ReceiptOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = ReceiptPillShape,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, PenOnDark),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = PenOnDarkInk),
    ) { Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
}

/** A plain paper slip: padded paper with the 6dp radius and no band. */
@Composable
fun ReceiptPlainSlip(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(Paper, ReceiptPaperShape).padding(16.dp), content = content)
}

/** One paper ticket without a red band, closed by the torn edge: the results and matches lists. */
@Composable
fun ReceiptPaperTicket(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(Paper), content = content)
        ReceiptTornEdge(fill = Paper)
    }
}

/** Bold subhead row of a paper ticket ("5 results", "Local matches"). */
@Composable
fun ReceiptSubhead(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
        Text(text, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
    }
}

/** The 2x2 meal choice. Selected = tonal + 2dp primary border + check. */
@Composable
fun ReceiptMealChips(selected: MealSlot, onSelect: (MealSlot) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MealSlot.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { meal -> ReceiptChoiceChip(meal.label(), selected == meal, { onSelect(meal) }, Modifier.weight(1f)) }
            }
        }
    }
}
