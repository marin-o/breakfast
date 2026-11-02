package com.bosa.breakfast.food

import com.bosa.breakfast.ui.components.AmountWithUnit
import com.bosa.breakfast.ui.components.FormFields
import com.bosa.breakfast.ui.components.SaveButton
import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.ui.components.NutrientFields
import com.bosa.breakfast.ui.components.NutrientField
import com.bosa.breakfast.ui.components.SubScreen
import com.bosa.breakfast.ui.theme.*
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
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bosa.breakfast.app.DiarySession
import com.bosa.breakfast.domain.parseNutritionShorthand
import com.bosa.breakfast.ui.components.DecimalField
import com.bosa.breakfast.ui.components.Field
import com.bosa.breakfast.ui.theme.LocalBreakfastColors
import com.bosa.breakfast.domain.editableNumber


/**
 * New personal food. The shorthand field fills the nutrition fields under it as it is typed; a field typed into
 * keeps its own value. Name and kcal are required, so Save waits for them.
 */
@Composable internal fun CreateFoodScreen(form: FoodForm, session: DiarySession) {
    val draft = form.draft
    val barcodeError = foodBarcodeError(draft.barcode, session.foods, editingId = null)
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
    SubScreen(
        "New food", form::leave,
        bottom = { SaveButton(form::save, state.canSave) },
    ) {
        FormFields {
            Field("Name", draft.name, { draft.name = it }, required = true, fieldModifier = Modifier.focusRequester(focus.getValue(FoodFormField.NAME)))
            Field("Brand", draft.brand, { draft.brand = it })
            FoodBarcodeField(form, barcodeError, Modifier.focusRequester(focus.getValue(FoodFormField.BARCODE)))
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
            AmountWithUnit("Basis", MeasureUnit.entries.firstOrNull { it.label == basisUnit }, { draft.basisEdited = true; draft.basisUnit = it.label }) { amountModifier ->
                DecimalField("Basis", basisAmount, { draft.basisEdited = true; draft.basisAmount = it }, amountModifier, labelVisible = false, fieldModifier = Modifier.focusRequester(focus.getValue(FoodFormField.BASIS)))
            }
            NutrientFields(
                listOf(
                    NutrientField(
                        "kcal", calories, { draft.caloriesEdited = true; draft.calories = it },
                        error = state.showRequired,
                        errorMessage = if (state.showRequired) "kcal is required" else null,
                        required = true,
                        fieldModifier = Modifier.focusRequester(focus.getValue(FoodFormField.KCAL)),
                    ),
                    NutrientField("Protein, g", protein, { draft.proteinEdited = true; draft.protein = it }, fieldModifier = Modifier.focusRequester(focus.getValue(FoodFormField.PROTEIN)), macro = Macro.PROTEIN),
                    NutrientField("Carbs, g", carbs, { draft.carbsEdited = true; draft.carbs = it }, fieldModifier = Modifier.focusRequester(focus.getValue(FoodFormField.CARBS)), macro = Macro.CARBS),
                    NutrientField("Fat, g", fat, { draft.fatEdited = true; draft.fat = it }, fieldModifier = Modifier.focusRequester(focus.getValue(FoodFormField.FAT)), macro = Macro.FAT),
                ),
                gap = 12.dp,
            )
            ServingsSection(draft)
            Field("Alias", draft.aliasInput, { draft.aliasInput = it })
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
