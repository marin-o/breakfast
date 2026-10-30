package com.iron.breakfast.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iron.breakfast.domain.asciiDigits
import com.iron.breakfast.ui.components.Field
import com.iron.breakfast.ui.components.ScanBarcodeIcon
import com.iron.breakfast.ui.theme.IconTarget
import com.iron.breakfast.ui.theme.LocalBreakfastColors

/**
 * The barcode of New food and Edit food: digits only, its [error] as it is typed. New food marks a code from a scan and
 * then has no Clear or rescan; Edit food always has both and no mark, as drawn.
 */
@Composable
internal fun FoodBarcodeField(form: FoodForm, error: String?, fieldModifier: Modifier = Modifier) {
    val draft = form.draft
    val colors = LocalBreakfastColors.current
    val markedFromScan = barcodeMarkedFromScan(draft.barcodeFromScan, form.editingFoodId)
    Field(
        label = "Barcode",
        value = draft.barcode,
        onValueChange = { draft.barcode = it.asciiDigits(); draft.barcodeFromScan = false },
        error = error != null,
        errorMessage = error,
        hint = if (markedFromScan) "from scan" else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        fieldModifier = fieldModifier,
        trailing = if (markedFromScan) null else ({
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (draft.barcode.isNotBlank()) IconButton(onClick = { draft.barcode = "" }, modifier = Modifier.size(IconTarget)) { Icon(Icons.Outlined.Close, "Clear barcode", tint = colors.soft) }
                IconButton(onClick = form::beginBarcodeRescan, modifier = Modifier.size(IconTarget)) { Icon(ScanBarcodeIcon, "Scan barcode", tint = colors.ink) }
            }
        }),
    )
}
