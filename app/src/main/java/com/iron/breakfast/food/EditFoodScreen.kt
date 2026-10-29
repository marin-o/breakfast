package com.iron.breakfast.food

import com.iron.breakfast.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iron.breakfast.app.BreakfastViewModel
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.asciiDigits
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.library.servingRowName
import com.iron.breakfast.ui.components.AddCircle
import com.iron.breakfast.ui.components.ChoiceOption
import com.iron.breakfast.ui.components.DecimalField
import com.iron.breakfast.ui.components.DestructiveButton
import com.iron.breakfast.ui.components.Field
import com.iron.breakfast.ui.components.PrimaryButton
import com.iron.breakfast.ui.components.ScanBarcodeIcon
import com.iron.breakfast.ui.components.SecondaryButton
import com.iron.breakfast.ui.components.SectionHeading
import com.iron.breakfast.ui.components.TertiaryButton
import com.iron.breakfast.ui.components.TopBar
import com.iron.breakfast.ui.pretty
import com.iron.breakfast.ui.screens.entrySourceLabel

// Edit food: the same [FoodForm] as New food (CreateFoodScreen.kt), laid out as drawn for an existing food.

@Composable
private fun FoodBarcodeField(form: FoodForm, label: String = "Barcode") {
    val draft = form.draft
    val colors = LocalBreakfastColors.current
    Field(
        label, draft.barcode, { draft.barcode = it.asciiDigits() }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        trailing = {
            if (draft.barcode.isNotBlank()) IconButton(onClick = { draft.barcode = "" }, modifier = Modifier.size(44.dp)) { Icon(Icons.Outlined.Close, "Clear barcode", tint = colors.soft) }
            IconButton(onClick = form::beginBarcodeRescan, modifier = Modifier.size(44.dp)) { Icon(ScanBarcodeIcon, "Scan barcode", tint = colors.ink) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditFoodScreen(vm: BreakfastViewModel) {
    val form = vm.foodForm
    val colors = LocalBreakfastColors.current
    val food = vm.foods.firstOrNull { it.id == form.editingFoodId } ?: return
    val draft = form.draft
    Column(Modifier.fillMaxSize().background(colors.bg).windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TopBar("Edit food", form::leave, Modifier.padding(top = 8.dp))
        Field("Name", draft.name, { draft.name = it }, required = true)
        Field("Brand", draft.brand, { draft.brand = it })
        FoodBarcodeField(form)
        Text(entrySourceLabel(food), color = colors.soft, style = BreakfastType.Secondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DecimalField("kcal", draft.calories, { draft.calories = it }, Modifier.weight(1f), required = true)
            DecimalField("Protein, g", draft.protein, { draft.protein = it }, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DecimalField("Carbs, g", draft.carbs, { draft.carbs = it }, Modifier.weight(1f))
            DecimalField("Fat, g", draft.fat, { draft.fat = it }, Modifier.weight(1f))
        }
        SectionHeading("Servings")
        draft.servings.forEach { conversion ->
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${servingRowName(conversion)} = ${conversion.baseUnitsPerServing.pretty()} ${food.nutrition.unit.label}", Modifier.weight(1f), color = colors.ink, style = BreakfastType.Body)
                AddCircle("Remove serving", { draft.servings = draft.servings - conversion }, icon = Icons.Outlined.Close)
            }
        }
        if (draft.servingFormOpen) {
            val amountError = draft.servingAmount.toDoubleOrNull()?.let { !it.isFinite() || it <= 0.0 } ?: true
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // "1 [name] = [amount]", then the unit choices, as drawn.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Text("1", color = colors.ink, style = BreakfastType.Body, modifier = Modifier.padding(top = 22.dp))
                    Field("Name", draft.servingLabel, { draft.servingLabel = it }, Modifier.weight(1f))
                    Text("=", color = colors.ink, style = BreakfastType.Body, modifier = Modifier.padding(top = 22.dp))
                    DecimalField(
                        "Amount", draft.servingAmount, { draft.servingAmount = it }, Modifier.weight(1f),
                        error = amountError,
                        errorMessage = if (amountError) "Enter more than 0 ${draft.servingUnit.label}." else null,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(MeasureUnit.GRAM, MeasureUnit.MILLILITER).forEach { unit ->
                        ChoiceOption(unit.displayLabel(), draft.servingUnit == unit, { draft.servingUnit = unit }, Modifier.widthIn(min = 72.dp))
                    }
                }
                SecondaryButton("Add serving", form::commitServing)
                TertiaryButton("Cancel", draft::cancelServing, Modifier.align(Alignment.CenterHorizontally), soft = true)
            }
        } else {
            TertiaryButton("Add serving", {
                draft.openServing(food.nutrition.unit.takeIf { it == MeasureUnit.GRAM || it == MeasureUnit.MILLILITER } ?: MeasureUnit.GRAM)
            })
        }
        SectionHeading("Aliases")
        draft.aliases.forEach { alias ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(alias, Modifier.weight(1f), color = colors.ink, style = BreakfastType.Body)
                AddCircle("Remove alias $alias", { draft.aliases = draft.aliases - alias }, icon = Icons.Outlined.Close)
            }
        }
        if (draft.aliasFieldOpen) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Field(
                    "New alias", draft.aliasInput, { draft.aliasInput = it }, Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { draft.commitAlias() }),
                )
                SecondaryButton("Add", { draft.commitAlias() }, fillWidth = false)
            }
        } else {
            TertiaryButton("Add alias", { draft.aliasFieldOpen = true })
        }
        PrimaryButton("Save", form::save, Modifier.padding(top = 8.dp))
        if (food.origin == FoodOrigin.CUSTOM) DestructiveButton("Delete food", { vm.library.askDelete(food) }, Modifier.fillMaxWidth(), outlined = true)
    }
}
