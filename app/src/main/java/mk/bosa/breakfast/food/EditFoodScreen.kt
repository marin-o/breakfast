package mk.bosa.breakfast.food

import mk.bosa.breakfast.ui.components.FormFields
import mk.bosa.breakfast.ui.components.NutrientFields
import mk.bosa.breakfast.ui.components.NutrientField
import mk.bosa.breakfast.ui.components.otherNutrientFields
import mk.bosa.breakfast.ui.components.SubScreen
import mk.bosa.breakfast.ui.theme.*
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
import mk.bosa.breakfast.app.DiarySession
import mk.bosa.breakfast.library.LibraryState
import mk.bosa.breakfast.ui.components.AddCircle
import mk.bosa.breakfast.ui.components.DestructiveButton
import mk.bosa.breakfast.ui.components.Field
import mk.bosa.breakfast.ui.components.PrimaryButton
import mk.bosa.breakfast.ui.components.SecondaryButton
import mk.bosa.breakfast.ui.components.SectionHeading
import mk.bosa.breakfast.ui.components.TertiaryButton
import mk.bosa.breakfast.ui.screens.entrySourceLabel

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
                    NutrientField("Protein, g", draft.protein, { draft.protein = it }, mark = Macro.PROTEIN.mark),
                    NutrientField("Carbs, g", draft.carbs, { draft.carbs = it }, mark = Macro.CARBS.mark),
                    NutrientField("Fat, g", draft.fat, { draft.fat = it }, mark = Macro.FAT.mark),
                ) + otherNutrientFields(
                    draft.saturatedFat to { draft.saturatedFat = it }, draft.sugars to { draft.sugars = it },
                    draft.fibre to { draft.fibre = it }, draft.salt to { draft.salt = it },
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
