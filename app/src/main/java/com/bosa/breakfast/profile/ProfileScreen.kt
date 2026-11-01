package com.bosa.breakfast.profile

import com.bosa.breakfast.ui.components.FormFields
import com.bosa.breakfast.ui.components.SaveButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bosa.breakfast.domain.KCAL_FIRST_MESSAGE
import com.bosa.breakfast.domain.KCAL_RANGE_MESSAGE
import com.bosa.breakfast.domain.MACRO_TARGET_RANGE_MESSAGE
import com.bosa.breakfast.domain.MacroPreset
import com.bosa.breakfast.domain.WEIGHT_RANGE_MESSAGE
import com.bosa.breakfast.domain.WeightPoint
import com.bosa.breakfast.domain.kcalTargetTextValid
import com.bosa.breakfast.domain.macroTargetTextValid
import com.bosa.breakfast.domain.weightTextValid
import com.bosa.breakfast.ui.components.AddCircle
import com.bosa.breakfast.ui.components.CheckboxRow
import com.bosa.breakfast.ui.components.ChoiceOption
import com.bosa.breakfast.ui.components.DecimalField
import com.bosa.breakfast.ui.components.FactRow
import com.bosa.breakfast.ui.components.Field
import com.bosa.breakfast.ui.components.Hairline
import com.bosa.breakfast.ui.components.NutrientField
import com.bosa.breakfast.ui.components.NutrientFields
import com.bosa.breakfast.ui.components.SubScreen
import com.bosa.breakfast.ui.components.WeightChart
import com.bosa.breakfast.ui.components.isLargeFont
import com.bosa.breakfast.ui.pretty

/** The Profile page: the name, the kcal and macro targets, the two weights, then the curve and its points, newest first. */
@Composable
internal fun ProfileScreen(profile: ProfileState) {
    val points = profile.points
    SubScreen(
        "Profile", profile::leave, scroll = false,
        bottom = { SaveButton(profile::save, profile.canSave) },
    ) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 12.dp)) {
            item(key = "fields") { ProfileFields(profile) }
            item(key = "curve") { WeightChart(points, profile.values.targetWeight, Modifier.padding(top = 16.dp)) }
            items(points, key = { it.id }) { point -> PointRow(point, profile) }
        }
    }
}

private val PRESET_CHOICES = listOf(
    MacroPreset.KCAL_AND_PROTEIN to "Kcal and protein",
    MacroPreset.BALANCED to "Balanced",
    MacroPreset.HIGH_PROTEIN to "High protein",
    MacroPreset.LOW_CARB to "Low carb",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileFields(profile: ProfileState) {
    val draft = profile.draft
    Column {
        Hairline(Modifier.padding(top = 8.dp))
        FormFields {
            Field("Name", draft.name, { profile.edit(draft.copy(name = it)) })
            KcalField(profile)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESET_CHOICES.forEach { (preset, label) -> ChoiceOption(label, false, { profile.applyPreset(preset) }, selectable = false) }
            }
            NutrientFields(
                listOf(
                    targetField("Protein, g", draft.protein) { profile.edit(draft.copy(protein = it)) },
                    targetField("Carbs, g", draft.carbs) { profile.edit(draft.copy(carbs = it)) },
                    targetField("Fat, g", draft.fat) { profile.edit(draft.copy(fat = it)) },
                ),
                gap = 12.dp,
                columns = if (isLargeFont(LocalDensity.current.fontScale)) 1 else 3,
            )
            WeightField("Current weight, kg", draft.current) { profile.edit(draft.copy(current = it)) }
            if (profile.curveOffered) CheckboxRow("Add to curve", profile.addToCurve, profile::toggleAddToCurve)
            CheckboxRow("Several weights a day", draft.severalWeights, { profile.edit(draft.copy(severalWeights = !draft.severalWeights)) })
            WeightField("Target weight, kg", draft.target) { profile.edit(draft.copy(target = it)) }
        }
    }
}

/** Out of range shows the red ring alone; "Enter your daily kcal first." comes from a preset that needed it. */
@Composable
private fun KcalField(profile: ProfileState) {
    val draft = profile.draft
    val valid = kcalTargetTextValid(draft.kcal)
    DecimalField(
        "Daily kcal", draft.kcal, { profile.edit(draft.copy(kcal = it)) },
        error = profile.kcalAsked || !valid,
        errorMessage = if (!valid) KCAL_RANGE_MESSAGE else KCAL_FIRST_MESSAGE.takeIf { profile.kcalAsked },
    )
}

private fun targetField(label: String, value: String, onValue: (String) -> Unit): NutrientField {
    val valid = macroTargetTextValid(value)
    return NutrientField(label, value, onValue, error = !valid, errorMessage = MACRO_TARGET_RANGE_MESSAGE.takeUnless { valid })
}

@Composable
private fun WeightField(label: String, value: String, onValue: (String) -> Unit) {
    val valid = weightTextValid(value)
    DecimalField(label, value, onValue, error = !valid, errorMessage = WEIGHT_RANGE_MESSAGE.takeUnless { valid })
}

@Composable
private fun PointRow(point: WeightPoint, profile: ProfileState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // FactRow keeps 8dp above itself; the same below centres its text on the circle.
        Box(Modifier.weight(1f).padding(bottom = 8.dp)) { FactRow(profile.pointLabel(point), point.kg.pretty(), "kg") }
        AddCircle("Delete ${profile.pointSummary(point)}", { profile.askDelete(point) }, icon = Icons.Outlined.Close)
    }
}
