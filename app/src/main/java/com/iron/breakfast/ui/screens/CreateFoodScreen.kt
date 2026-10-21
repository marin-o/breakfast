package com.iron.breakfast.ui.screens

import com.iron.breakfast.ui.theme.*

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iron.breakfast.BreakfastViewModel
import com.iron.breakfast.domain.asciiDigits
import com.iron.breakfast.domain.parseNutritionShorthand
import com.iron.breakfast.domain.validateBarcodeInput
import com.iron.breakfast.ui.components.ButtonKind
import com.iron.breakfast.ui.components.ChoiceOption
import com.iron.breakfast.ui.components.Field
import com.iron.breakfast.ui.components.PrimaryButton
import com.iron.breakfast.ui.components.TopBar
import com.iron.breakfast.ui.theme.LocalBreakfastColors

/**
 * New personal food. The shorthand field is the main input; the summary shows what was understood, and
 * the "Full form" toggle opens the structured fields. Calories are required, so Save waits for them.
 */
@Composable internal fun CreateFoodScreen(vm: BreakfastViewModel) {
    val draft = vm.draft
    val barcodeError = validateBarcodeInput(draft.barcode, vm.foods)
    val state = createFoodState(draft.nutritionInput(), draft.name, barcodeError)
    val slip = nutritionSlipValues(state.nutrition)
    val colors = LocalBreakfastColors.current
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    BackHandler(onBack = vm::closeCustom)
    Surface(Modifier.fillMaxSize(), color = colors.bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(bottom = 12.dp)) {
                TopBar("New food", vm::closeCustom, Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                Column(Modifier.padding(horizontal = Gutter).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Field("Name", draft.name, { draft.name = it })
                    Field("Brand", draft.brand, { draft.brand = it })
                    Field(
                        label = "Barcode",
                        value = draft.barcode,
                        onValueChange = { draft.barcode = it.asciiDigits(); draft.barcodeFromScan = false },
                        error = barcodeError != null,
                        errorMessage = barcodeError,
                        hint = if (draft.barcodeFromScan) "from scan" else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailing = if (draft.barcodeFromScan) null else ({
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (draft.barcode.isNotBlank()) IconButton(onClick = { draft.barcode = "" }, modifier = Modifier.size(44.dp)) { Icon(Icons.Outlined.Close, "Clear barcode", tint = colors.soft) }
                                IconButton(onClick = vm::beginBarcodeRescan, modifier = Modifier.size(44.dp)) { Icon(Icons.Outlined.QrCodeScanner, "Scan barcode", tint = colors.ink) }
                            }
                        }),
                    )
                    Field(
                        label = "Nutrition",
                        value = draft.shorthand,
                        onValueChange = { draft.shorthand = it },
                        error = state.showRequired,
                        errorMessage = if (state.showRequired) "kcal is required" else null,
                        singleLine = false,
                        minLines = 2,
                        placeholder = "per 100g: 450 kcal, 12g protein, 20g carbs, 8g fat",
                    )
                }
                Column(Modifier.padding(horizontal = Gutter).padding(top = 24.dp).fillMaxWidth().semantics(mergeDescendants = true) { }) {
                    Text(slip.label, color = colors.soft, style = BreakfastType.Meta)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SlipCell("kcal", slip.calories)
                            SlipCell("carbs", slip.carbs)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SlipCell("protein", slip.protein)
                            SlipCell("fat", slip.fat)
                        }
                    }
                }
                FullFormToggle(
                    open = draft.fullFormOpen,
                    onClick = { draft.fullFormOpen = !draft.fullFormOpen },
                    modifier = Modifier.padding(start = Gutter, top = 16.dp),
                )
                Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (draft.fullFormOpen) {
                        val basisAmount = if (draft.basisEdited) draft.basisAmount else editableNumber(state.nutrition.basisAmount)
                        val basisUnit = if (draft.basisEdited) draft.basisUnit else state.nutrition.basisUnit.label
                        val calories = if (draft.caloriesEdited) draft.calories else state.nutrition.calories?.let(::editableNumber).orEmpty()
                        val protein = if (draft.proteinEdited) draft.protein else state.nutrition.protein?.let(::editableNumber).orEmpty()
                        val carbs = if (draft.carbsEdited) draft.carbs else state.nutrition.carbohydrates?.let(::editableNumber).orEmpty()
                        val fat = if (draft.fatEdited) draft.fat else state.nutrition.fat?.let(::editableNumber).orEmpty()
                        Text("Shorthand fills these. Edits here win.", color = colors.soft, style = BreakfastType.Secondary)
                        Column {
                            Text("Basis", color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(bottom = 4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Field("Basis", basisAmount, { draft.basisEdited = true; draft.basisAmount = it.asciiDigits(allowDecimalPoint = true) }, Modifier.weight(1.6f), labelVisible = false, keyboardOptions = decimal)
                                listOf("g", "ml").forEach { unit -> ChoiceOption(unit, basisUnit == unit, { draft.basisEdited = true; draft.basisUnit = unit }, Modifier.weight(1f)) }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Field("kcal", calories, { draft.caloriesEdited = true; draft.calories = it.asciiDigits(allowDecimalPoint = true) }, Modifier.weight(1f), keyboardOptions = decimal, error = state.showRequired)
                            Field("Protein, g", protein, { draft.proteinEdited = true; draft.protein = it.asciiDigits(allowDecimalPoint = true) }, Modifier.weight(1f), keyboardOptions = decimal)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Field("Carbs, g", carbs, { draft.carbsEdited = true; draft.carbs = it.asciiDigits(allowDecimalPoint = true) }, Modifier.weight(1f), keyboardOptions = decimal)
                            Field("Fat, g", fat, { draft.fatEdited = true; draft.fat = it.asciiDigits(allowDecimalPoint = true) }, Modifier.weight(1f), keyboardOptions = decimal)
                        }
                    }
                    Field("Alias", draft.aliasInput, { draft.aliasInput = it })
                }
            }
            Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = Gutter, vertical = 8.dp)) {
                PrimaryButton("Save", vm::saveCustom, Modifier.padding(top = 8.dp), enabled = state.canSave, kind = ButtonKind.Constructive)
            }
        }
    }
}

/** "Full form" with a chevron that points down when closed and up when open. */
@Composable private fun FullFormToggle(open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalBreakfastColors.current
    Row(
        modifier
            .heightIn(min = TertiaryHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Full form", color = colors.ink, style = BreakfastType.Button)
        Icon(if (open) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
    }
}

/** One preview cell: a soft label on the left, the value ink on the right; "unknown" in soft when empty. */
@Composable private fun SlipCell(label: String, value: String?) {
    val colors = LocalBreakfastColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(label, color = colors.soft, style = BreakfastType.Secondary)
        if (value == null) Text("unknown", color = colors.soft, style = BreakfastType.Secondary)
        else Row(verticalAlignment = Alignment.Bottom) {
            val number = value.removeSuffix(" g")
            Text(number, color = colors.ink, style = BreakfastType.BodyMedium)
            if (number != value) Text(" g", color = colors.soft, style = BreakfastType.Secondary)
        }
    }
}
