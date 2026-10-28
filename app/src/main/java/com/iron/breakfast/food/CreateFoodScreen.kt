package com.iron.breakfast.food

import com.iron.breakfast.ui.theme.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.iron.breakfast.app.BreakfastViewModel
import com.iron.breakfast.FoodFormField
import com.iron.breakfast.domain.asciiDigits
import com.iron.breakfast.domain.parseNutritionShorthand
import com.iron.breakfast.domain.validateBarcodeInput
import com.iron.breakfast.ui.components.ButtonKind
import com.iron.breakfast.ui.components.ChoiceOption
import com.iron.breakfast.ui.components.DecimalField
import com.iron.breakfast.ui.components.Field
import com.iron.breakfast.ui.components.PrimaryButton
import com.iron.breakfast.ui.components.ScanBarcodeIcon
import com.iron.breakfast.ui.components.TopBar
import com.iron.breakfast.ui.theme.LocalBreakfastColors
import com.iron.breakfast.ui.screens.createFoodState
import com.iron.breakfast.ui.screens.editableNumber


/**
 * New personal food. The shorthand field fills the nutrition fields under it as it is typed; a field typed into
 * keeps its own value. Name and kcal are required, so Save waits for them.
 */
@Composable internal fun CreateFoodScreen(vm: BreakfastViewModel) {
    val form = vm.foodForm
    val draft = form.draft
    val barcodeError = validateBarcodeInput(draft.barcode, vm.foods)
    val state = createFoodState(draft.nutritionInput(), draft.name, barcodeError)
    val colors = LocalBreakfastColors.current
    // A save the view model refuses (Save was enabled, but a number is out of range) moves focus to the field to fix,
    // which TalkBack then reads; nothing new is shown.
    val focus = remember { FoodFormField.entries.associateWith { FocusRequester() } }
    val focusRequest = form.focus
    LaunchedEffect(focusRequest) {
        if (focusRequest == null) return@LaunchedEffect
        runCatching { focus.getValue(focusRequest.field).requestFocus() }
        form.focusHandled()
    }
    Surface(Modifier.fillMaxSize(), color = colors.bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(bottom = 12.dp)) {
                TopBar("New food", form::leave, Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                Column(Modifier.padding(horizontal = Gutter).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Field("Name", draft.name, { draft.name = it }, required = true, fieldModifier = Modifier.focusRequester(focus.getValue(FoodFormField.NAME)))
                    Field("Brand", draft.brand, { draft.brand = it })
                    Field(
                        label = "Barcode",
                        value = draft.barcode,
                        onValueChange = { draft.barcode = it.asciiDigits(); draft.barcodeFromScan = false },
                        error = barcodeError != null,
                        errorMessage = barcodeError,
                        hint = if (draft.barcodeFromScan) "from scan" else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        fieldModifier = Modifier.focusRequester(focus.getValue(FoodFormField.BARCODE)),
                        trailing = if (draft.barcodeFromScan) null else ({
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (draft.barcode.isNotBlank()) IconButton(onClick = { draft.barcode = "" }, modifier = Modifier.size(44.dp)) { Icon(Icons.Outlined.Close, "Clear barcode", tint = colors.soft) }
                                IconButton(onClick = form::beginBarcodeRescan, modifier = Modifier.size(44.dp)) { Icon(ScanBarcodeIcon, "Scan barcode", tint = colors.ink) }
                            }
                        }),
                    )
                    // The shorthand fills the fields below as it is typed; the numbers it read print in ink.
                    Field(
                        label = "Nutrition",
                        value = draft.shorthand,
                        onValueChange = { draft.shorthand = it },
                        singleLine = false,
                        minLines = 2,
                        placeholder = "per 100g: 450 kcal, 12g protein, 20g carbs, 8g fat",
                        visualTransformation = recognisedNumbers(colors.ink, colors.soft),
                    )
                    // A field typed into keeps its own value; the others follow the shorthand.
                    val basisAmount = if (draft.basisEdited) draft.basisAmount else editableNumber(state.nutrition.basisAmount)
                    val basisUnit = if (draft.basisEdited) draft.basisUnit else state.nutrition.basisUnit.label
                    val calories = if (draft.caloriesEdited) draft.calories else state.nutrition.calories?.let(::editableNumber).orEmpty()
                    val protein = if (draft.proteinEdited) draft.protein else state.nutrition.protein?.let(::editableNumber).orEmpty()
                    val carbs = if (draft.carbsEdited) draft.carbs else state.nutrition.carbohydrates?.let(::editableNumber).orEmpty()
                    val fat = if (draft.fatEdited) draft.fat else state.nutrition.fat?.let(::editableNumber).orEmpty()
                    Column {
                        Text("Basis", color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(bottom = 4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DecimalField("Basis", basisAmount, { draft.basisEdited = true; draft.basisAmount = it }, Modifier.weight(1.6f), labelVisible = false, fieldModifier = Modifier.focusRequester(focus.getValue(FoodFormField.BASIS)))
                            listOf("g", "ml").forEach { unit -> ChoiceOption(unit, basisUnit == unit, { draft.basisEdited = true; draft.basisUnit = unit }, Modifier.weight(1f)) }
                        }
                    }
                    DecimalField(
                        "kcal", calories, { draft.caloriesEdited = true; draft.calories = it },
                        error = state.showRequired,
                        errorMessage = if (state.showRequired) "kcal is required" else null,
                        required = true,
                        fieldModifier = Modifier.focusRequester(focus.getValue(FoodFormField.KCAL)),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DecimalField("Protein, g", protein, { draft.proteinEdited = true; draft.protein = it }, Modifier.weight(1f), fieldModifier = Modifier.focusRequester(focus.getValue(FoodFormField.PROTEIN)))
                        DecimalField("Carbs, g", carbs, { draft.carbsEdited = true; draft.carbs = it }, Modifier.weight(1f), fieldModifier = Modifier.focusRequester(focus.getValue(FoodFormField.CARBS)))
                        DecimalField("Fat, g", fat, { draft.fatEdited = true; draft.fat = it }, Modifier.weight(1f), fieldModifier = Modifier.focusRequester(focus.getValue(FoodFormField.FAT)))
                    }
                    Field("Alias", draft.aliasInput, { draft.aliasInput = it })
                }
            }
            Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = Gutter, vertical = 8.dp)) {
                PrimaryButton("Save", form::save, Modifier.padding(top = 8.dp), enabled = state.canSave, kind = ButtonKind.Constructive)
            }
        }
    }
}

/** The shorthand in [soft] with the numbers it recognised in [ink], so what fills the fields below is visible. */
private fun recognisedNumbers(ink: Color, soft: Color) = VisualTransformation { text ->
    val styled = buildAnnotatedString {
        append(text)
        addStyle(SpanStyle(color = soft), 0, text.length)
        parseNutritionShorthand(text.text).recognisedRanges.forEach { addStyle(SpanStyle(color = ink, fontWeight = FontWeight.Medium), it.first, it.last + 1) }
    }
    TransformedText(styled, OffsetMapping.Identity)
}
