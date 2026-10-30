package com.iron.breakfast.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.ui.label

@Composable internal fun MixedMealPickerDialog(library: LibraryState) {
    MealPickerDialog("Which meal?", "Choose where to add this batch.", library::chooseMixedMeal, library::dismissMixedMealPicker)
}

@Composable internal fun LibraryBatchMealPickerDialog(library: LibraryState) {
    MealPickerDialog("Add foods to which meal?", "Choose where to add this batch on the selected day.", library::chooseBatchMeal, library::dismissBatchMealPicker)
}

@Composable private fun MealPickerDialog(title: String, text: String, onPick: (MealSlot) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(text)
                MealSlot.entries.forEach { target -> TextButton({ onPick(target) }, Modifier.fillMaxWidth()) { Text(target.label()) } }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
