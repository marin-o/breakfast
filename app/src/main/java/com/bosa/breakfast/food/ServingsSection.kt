package com.bosa.breakfast.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bosa.breakfast.domain.POSITIVE_AMOUNT_MESSAGE
import com.bosa.breakfast.domain.positiveAmountOf
import com.bosa.breakfast.library.servingRowName
import com.bosa.breakfast.ui.components.AddCircle
import com.bosa.breakfast.ui.components.DecimalField
import com.bosa.breakfast.ui.components.Field
import com.bosa.breakfast.ui.components.GramMlChoices
import com.bosa.breakfast.ui.components.SecondaryButton
import com.bosa.breakfast.ui.components.SectionHeading
import com.bosa.breakfast.ui.components.TertiaryButton
import com.bosa.breakfast.ui.pretty
import com.bosa.breakfast.ui.theme.BreakfastType
import com.bosa.breakfast.ui.theme.FieldMinHeight
import com.bosa.breakfast.ui.theme.LocalBreakfastColors

/**
 * A food's servings and density over [draft]: the heading, the rows with their remove, the add-serving form and "100 ml =".
 * The one section of New food, Edit food and a new product's review details.
 */
@Composable
internal fun ServingsSection(draft: FoodDraft) {
    val colors = LocalBreakfastColors.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading("Servings")
        val unit = draft.shownServingUnit()
        draft.shownServings().forEachIndexed { index, conversion ->
            Row(Modifier.fillMaxWidth().heightIn(min = FieldMinHeight), verticalAlignment = Alignment.CenterVertically) {
                Text("${servingRowName(conversion)} = ${conversion.baseUnitsPerServing.pretty()} ${unit.label}", Modifier.weight(1f), color = colors.ink, style = BreakfastType.Body)
                AddCircle("Remove serving", { draft.removeServing(index) }, icon = Icons.Outlined.Close)
            }
        }
        if (draft.servingFormOpen) {
            val amountError = positiveAmountOf(draft.servingAmount) == null
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // "1 [name] = [amount]", then the unit choices, as drawn.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Text("1", color = colors.ink, style = BreakfastType.Body, modifier = Modifier.padding(top = 22.dp))
                    // An amount with no name is no serving: the ring says what is left to fill.
                    Field("Name", draft.servingLabel, { draft.servingLabel = it }, Modifier.weight(1f), error = !amountError && draft.servingLabel.isBlank())
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
                SecondaryButton("Add serving", { draft.commitServing() })
                TertiaryButton("Cancel", draft::cancelServing, Modifier.align(Alignment.CenterHorizontally), soft = true)
            }
        } else {
            TertiaryButton("Add serving", { draft.openServing(draft.basis()) })
        }
        // Beside the servings: with it, g and ml both work for the food, and a serving may be in either.
        DecimalField(
            "100 ml =", draft.density, { draft.density = it },
            hint = "g",
            error = draft.densityError(),
            errorMessage = if (draft.densityError()) POSITIVE_AMOUNT_MESSAGE else null,
        )
    }
}
