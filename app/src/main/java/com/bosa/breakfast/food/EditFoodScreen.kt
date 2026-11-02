package com.bosa.breakfast.food

import com.bosa.breakfast.ui.components.FormFields
import com.bosa.breakfast.ui.components.NutrientFields
import com.bosa.breakfast.ui.components.NutrientField
import com.bosa.breakfast.ui.components.SubScreen
import com.bosa.breakfast.ui.theme.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bosa.breakfast.app.DiarySession
import com.bosa.breakfast.library.LibraryState
import com.bosa.breakfast.ui.components.AddCircle
import com.bosa.breakfast.ui.components.DestructiveButton
import com.bosa.breakfast.ui.components.Field
import com.bosa.breakfast.ui.components.PrimaryButton
import com.bosa.breakfast.ui.components.SecondaryButton
import com.bosa.breakfast.ui.components.SectionHeading
import com.bosa.breakfast.ui.components.TertiaryButton
import com.bosa.breakfast.ui.screens.entrySourceLabel

// Edit food: the same [FoodForm] as New food (CreateFoodScreen.kt), laid out as drawn for an existing food.

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditFoodScreen(form: FoodForm, session: DiarySession, library: LibraryState) {
    val colors = LocalBreakfastColors.current
    val food = session.foods.firstOrNull { it.id == form.editingFoodId } ?: return
    val draft = form.draft
    SubScreen("Edit food", form::leave) {
        FormFields {
            Field("Name", draft.name, { draft.name = it }, required = true)
            Field("Brand", draft.brand, { draft.brand = it })
            FoodBarcodeField(form, foodBarcodeError(draft.barcode, session.foods, food.id))
            Text(entrySourceLabel(food), color = colors.soft, style = BreakfastType.Secondary)
            NutrientFields(
                listOf(
                    NutrientField("kcal", draft.calories, { draft.calories = it }, required = true),
                    NutrientField("Protein, g", draft.protein, { draft.protein = it }, macro = Macro.PROTEIN),
                    NutrientField("Carbs, g", draft.carbs, { draft.carbs = it }, macro = Macro.CARBS),
                    NutrientField("Fat, g", draft.fat, { draft.fat = it }, macro = Macro.FAT),
                ),
                gap = 16.dp,
            )
            ServingsSection(draft)
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
            DestructiveButton("Delete food", { library.askDelete(food) }, Modifier.fillMaxWidth(), outlined = true)
        }
    }
}
