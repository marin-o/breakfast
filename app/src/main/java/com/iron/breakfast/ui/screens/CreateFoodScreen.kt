package com.iron.breakfast.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iron.breakfast.BreakfastViewModel
import com.iron.breakfast.domain.asciiDigits
import com.iron.breakfast.domain.parseNutritionShorthand
import com.iron.breakfast.domain.validateBarcodeInput
import com.iron.breakfast.ui.components.ReceiptChoiceChip
import com.iron.breakfast.ui.components.ReceiptField
import com.iron.breakfast.ui.components.ReceiptRedTag
import com.iron.breakfast.ui.components.ReceiptTag
import com.iron.breakfast.ui.components.ReceiptTokenKind
import com.iron.breakfast.ui.components.ReceiptTokenRange
import com.iron.breakfast.ui.components.ReceiptTokenVisualTransformation
import com.iron.breakfast.ui.components.CharacterRule
import com.iron.breakfast.ui.components.ReceiptPrimaryButton
import com.iron.breakfast.ui.components.ReceiptTonalIconButton
import com.iron.breakfast.ui.components.ReceiptTopBar
import com.iron.breakfast.ui.components.receiptDashedRule
import com.iron.breakfast.ui.theme.ReceiptPaperShape
import com.iron.breakfast.ui.theme.LocalReceiptColors

/**
 * New personal food. The shorthand field is the main input; the slip shows what was understood, and
 * the "Full form" toggle opens the structured fields. Calories are required, so Save waits for them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun CreateFoodScreen(vm: BreakfastViewModel) {
    val draft = vm.draft
    val barcodeError = validateBarcodeInput(draft.barcode, vm.foods)
    val state = createFoodState(draft.nutritionInput(), draft.name, barcodeError)
    val slip = nutritionSlipValues(state.nutrition)
    val parsed = parseNutritionShorthand(draft.shorthand)
    val colors = LocalReceiptColors.current
    val highlight = remember(parsed, colors) { ReceiptTokenVisualTransformation(parsed.recognisedRanges.map { ReceiptTokenRange(it, ReceiptTokenKind.Quantity) }, redFill = colors.redFill, onRedFill = colors.onRedFill) }
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    BackHandler(onBack = vm::closeCustom)
    Surface(Modifier.fillMaxSize(), color = colors.paper) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(bottom = 12.dp)) {
            ReceiptTopBar("New food", vm::closeCustom, Modifier.padding(start = 4.dp))
            Column(Modifier.padding(horizontal = 14.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ReceiptField("Name", draft.name, { draft.name = it })
                ReceiptField("Brand", draft.brand, { draft.brand = it })
                Column {
                    ReceiptField(
                        label = "Barcode",
                        value = draft.barcode,
                        onValueChange = { draft.barcode = it.asciiDigits(); draft.barcodeFromScan = false },
                        error = barcodeError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailing = {
                            when {
                                draft.barcodeFromScan -> ReceiptTag("from scan")
                                else -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (draft.barcode.isNotBlank()) ReceiptTonalIconButton(Icons.Default.Close, "Clear barcode", { draft.barcode = "" })
                                    ReceiptTonalIconButton(Icons.Default.QrCodeScanner, "Scan barcode", vm::beginBarcodeRescan)
                                }
                            }
                        },
                    )
                    if (barcodeError != null) ReceiptRedTag(barcodeError, Modifier.padding(top = 6.dp))
                }
                ReceiptField(
                    label = "Nutrition",
                    value = draft.shorthand,
                    onValueChange = { draft.shorthand = it },
                    error = state.showRequired,
                    singleLine = false,
                    minLines = 2,
                    placeholder = "per 100g: 450 kcal, 12g protein, 20g carbs, 8g fat",
                    visualTransformation = highlight,
                    labelTrailing = if (state.showRequired) ({ ReceiptRedTag("Required: kcal") }) else null,
                )
            }
            Column(
                Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp).fillMaxWidth().background(colors.paper, ReceiptPaperShape).padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp)
                    .semantics(mergeDescendants = true) { },
            ) {
                Text(slip.label, color = colors.soft, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp).receiptDashedRule().padding(top = 8.dp)) {
                    SlipCell("kcal", slip.calories, Modifier.weight(1f))
                    SlipCell("protein", slip.protein, Modifier.weight(1f))
                    SlipCell("carbs", slip.carbs, Modifier.weight(1f))
                    SlipCell("fat", slip.fat, Modifier.weight(1f))
                }
            }
            TextButton(
                onClick = { draft.fullFormOpen = !draft.fullFormOpen },
                modifier = Modifier.padding(start = 10.dp, top = 6.dp).heightIn(min = 48.dp).semantics { stateDescription = if (draft.fullFormOpen) "Expanded" else "Collapsed" },
            ) {
                Text("Full form", color = colors.ink, fontWeight = FontWeight.Medium)
                Icon(if (draft.fullFormOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = colors.ink)
            }
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (draft.fullFormOpen) {
                    val basisAmount = if (draft.basisEdited) draft.basisAmount else editableNumber(state.nutrition.basisAmount)
                    val basisUnit = if (draft.basisEdited) draft.basisUnit else state.nutrition.basisUnit.label
                    val calories = if (draft.caloriesEdited) draft.calories else state.nutrition.calories?.let(::editableNumber).orEmpty()
                    val protein = if (draft.proteinEdited) draft.protein else state.nutrition.protein?.let(::editableNumber).orEmpty()
                    val carbs = if (draft.carbsEdited) draft.carbs else state.nutrition.carbohydrates?.let(::editableNumber).orEmpty()
                    val fat = if (draft.fatEdited) draft.fat else state.nutrition.fat?.let(::editableNumber).orEmpty()
                    Text("Shorthand fills these. Edits here win.", color = colors.soft, style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                        ReceiptField("Basis", basisAmount, { draft.basisEdited = true; draft.basisAmount = it.asciiDigits(allowDecimalPoint = true) }, Modifier.weight(1f), keyboardOptions = decimal)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("g", "ml").forEach { unit -> ReceiptChoiceChip(unit, basisUnit == unit, { draft.basisEdited = true; draft.basisUnit = unit }) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ReceiptField("kcal", calories, { draft.caloriesEdited = true; draft.calories = it.asciiDigits(allowDecimalPoint = true) }, Modifier.weight(1f), keyboardOptions = decimal, error = state.showRequired)
                        ReceiptField("Protein, g", protein, { draft.proteinEdited = true; draft.protein = it.asciiDigits(allowDecimalPoint = true) }, Modifier.weight(1f), keyboardOptions = decimal)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ReceiptField("Carbs, g", carbs, { draft.carbsEdited = true; draft.carbs = it.asciiDigits(allowDecimalPoint = true) }, Modifier.weight(1f), keyboardOptions = decimal)
                        ReceiptField("Fat, g", fat, { draft.fatEdited = true; draft.fat = it.asciiDigits(allowDecimalPoint = true) }, Modifier.weight(1f), keyboardOptions = decimal)
                    }
                }
                ReceiptField("Alias", draft.aliasInput, { draft.aliasInput = it })
            }
            }
            Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 14.dp, vertical = 8.dp)) {
                CharacterRule('=')
                ReceiptPrimaryButton("Save", vm::saveCustom, Modifier.padding(top = 8.dp), enabled = state.canSave)
            }
        }
    }
}

@Composable private fun SlipCell(label: String, value: String?, modifier: Modifier) {
    val colors = LocalReceiptColors.current
    Column(modifier) {
        Text(label, color = colors.soft, style = MaterialTheme.typography.labelSmall)
        if (value != null) Text(value, color = colors.ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        else Text("unknown", color = colors.soft, style = MaterialTheme.typography.labelMedium)
    }
}
