package com.iron.breakfast.food

import com.iron.breakfast.ui.components.NutrientFields
import com.iron.breakfast.ui.components.NutrientField
import com.iron.breakfast.ui.components.GramMlChoices
import com.iron.breakfast.ui.components.SubScreen
import com.iron.breakfast.domain.POSITIVE_AMOUNT_MESSAGE
import com.iron.breakfast.domain.positiveAmountOf
import com.iron.breakfast.ui.theme.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iron.breakfast.app.DiarySession
import com.iron.breakfast.library.LibraryState
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.library.servingRowName
import com.iron.breakfast.ui.components.AddCircle
import com.iron.breakfast.ui.components.DecimalField
import com.iron.breakfast.ui.components.DestructiveButton
import com.iron.breakfast.ui.components.Field
import com.iron.breakfast.ui.components.PrimaryButton
import com.iron.breakfast.ui.components.SecondaryButton
import com.iron.breakfast.ui.components.SectionHeading
import com.iron.breakfast.ui.components.TertiaryButton
import com.iron.breakfast.ui.pretty
import com.iron.breakfast.ui.screens.entrySourceLabel

// Edit food: the same [FoodForm] as New food (CreateFoodScreen.kt), laid out as drawn for an existing food.

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditFoodScreen(form: FoodForm, session: DiarySession, library: LibraryState) {
    val colors = LocalBreakfastColors.current
    val food = session.foods.firstOrNull { it.id == form.editingFoodId } ?: return
    val draft = form.draft
    SubScreen("Edit food", form::leave) {
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Field("Name", draft.name, { draft.name = it }, required = true)
            Field("Brand", draft.brand, { draft.brand = it })
            FoodBarcodeField(form, foodBarcodeError(draft.barcode, session.foods, food.id))
            Text(entrySourceLabel(food), color = colors.soft, style = BreakfastType.Secondary)
            NutrientFields(
                listOf(
                    NutrientField("kcal", draft.calories, { draft.calories = it }, required = true),
                    NutrientField("Protein, g", draft.protein, { draft.protein = it }),
                    NutrientField("Carbs, g", draft.carbs, { draft.carbs = it }),
                    NutrientField("Fat, g", draft.fat, { draft.fat = it }),
                ),
                gap = 16.dp,
            )
            SectionHeading("Servings")
            draft.servings.forEach { conversion ->
                Row(Modifier.fillMaxWidth().heightIn(min = FieldMinHeight), verticalAlignment = Alignment.CenterVertically) {
                    Text("${servingRowName(conversion)} = ${conversion.baseUnitsPerServing.pretty()} ${food.nutrition.unit.label}", Modifier.weight(1f), color = colors.ink, style = BreakfastType.Body)
                    AddCircle("Remove serving", { draft.servings = draft.servings - conversion }, icon = Icons.Outlined.Close)
                }
            }
            if (draft.servingFormOpen) {
                val amountError = positiveAmountOf(draft.servingAmount) == null
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
                        GramMlChoices(draft.servingUnit, { draft.servingUnit = it }, Modifier.widthIn(min = 72.dp))
                    }
                    SecondaryButton("Add serving", form::commitServing)
                    TertiaryButton("Cancel", draft::cancelServing, Modifier.align(Alignment.CenterHorizontally), soft = true)
                }
            } else {
                TertiaryButton("Add serving", {
                    draft.openServing(food.nutrition.unit.takeIf { it == MeasureUnit.GRAM || it == MeasureUnit.MILLILITER } ?: MeasureUnit.GRAM)
                })
            }
            // Beside the servings: with it, g and ml both work for the food, and a serving may be in either.
            DecimalField(
                "100 ml =", draft.density, { draft.density = it },
                hint = "g",
                error = draft.densityError(),
                errorMessage = if (draft.densityError()) POSITIVE_AMOUNT_MESSAGE else null,
            )
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
            if (food.origin == FoodOrigin.CUSTOM) DestructiveButton("Delete food", { library.askDelete(food) }, Modifier.fillMaxWidth(), outlined = true)
        }
    }
}
