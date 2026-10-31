package com.iron.breakfast.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.iron.breakfast.domain.MACRO_TARGET_RANGE_MESSAGE
import com.iron.breakfast.domain.WEIGHT_RANGE_MESSAGE
import com.iron.breakfast.domain.WeightPoint
import com.iron.breakfast.domain.curveX
import com.iron.breakfast.domain.macroTargetTextValid
import com.iron.breakfast.domain.weightTextValid
import com.iron.breakfast.ui.components.AddCircle
import com.iron.breakfast.ui.components.ButtonKind
import com.iron.breakfast.ui.components.CheckboxRow
import com.iron.breakfast.ui.components.CurvePoint
import com.iron.breakfast.ui.components.DecimalField
import com.iron.breakfast.ui.components.FactRow
import com.iron.breakfast.ui.components.Field
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.NutrientField
import com.iron.breakfast.ui.components.NutrientFields
import com.iron.breakfast.ui.components.PointCurve
import com.iron.breakfast.ui.components.PrimaryButton
import com.iron.breakfast.ui.components.SubScreen
import com.iron.breakfast.ui.components.isLargeFont
import com.iron.breakfast.ui.pretty
import com.iron.breakfast.ui.shortDateLabel

/** The Profile page: the name, the macro targets, the two weights, then the curve and its points, newest first. */
@Composable
internal fun ProfileScreen(profile: ProfileState) {
    val points = profile.points
    val curve = remember(points) { points.mapNotNull { point -> curveX(point.dayKey)?.let { CurvePoint(it, point.kg) } } }
    SubScreen(
        "Profile", profile::leave, scroll = false,
        bottom = { PrimaryButton("Save", profile::save, Modifier.padding(top = 16.dp, bottom = 8.dp), enabled = profile.canSave, kind = ButtonKind.Constructive) },
    ) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 12.dp)) {
            item(key = "fields") { ProfileFields(profile) }
            if (points.isNotEmpty()) item(key = "curve") { PointCurve(curve, profile.values.targetWeight, Modifier.padding(top = 16.dp)) }
            items(points, key = { it.dayKey }) { point -> PointRow(point) { profile.askDelete(point) } }
        }
    }
}

@Composable
private fun ProfileFields(profile: ProfileState) {
    val draft = profile.draft
    Column {
        Hairline(Modifier.padding(top = 8.dp))
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Field("Name", draft.name, { profile.edit(draft.copy(name = it)) })
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
            WeightField("Target weight, kg", draft.target) { profile.edit(draft.copy(target = it)) }
        }
    }
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
private fun PointRow(point: WeightPoint, onDelete: () -> Unit) {
    val date = shortDateLabel(point.dayKey)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // FactRow keeps 8dp above itself; the same below centres its text on the circle.
        Box(Modifier.weight(1f).padding(bottom = 8.dp)) { FactRow(date, point.kg.pretty(), "kg") }
        AddCircle("Delete $date", onDelete, icon = Icons.Outlined.Close)
    }
}
