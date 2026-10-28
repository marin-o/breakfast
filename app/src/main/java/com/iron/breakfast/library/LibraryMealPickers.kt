package com.iron.breakfast.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.iron.breakfast.app.BreakfastViewModel
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.ui.label

@Composable internal fun MixedMealPickerDialog(vm: BreakfastViewModel) {
    AlertDialog(
        onDismissRequest = vm.library::dismissMixedMealPicker,
        title = { Text("Which meal?") },
        text = {
            Column {
                Text("Choose where to add this batch.")
                MealSlot.entries.forEach { target ->
                    TextButton({ vm.library.chooseMixedMeal(target) }, Modifier.fillMaxWidth()) { Text(target.label()) }
                }
            }
        },
        confirmButton = { TextButton(vm.library::dismissMixedMealPicker) { Text("Cancel") } }
    )
}

@Composable internal fun LibraryBatchMealPickerDialog(vm: BreakfastViewModel) {
    AlertDialog(
        onDismissRequest = vm.library::dismissBatchMealPicker,
        title = { Text("Add foods to which meal?") },
        text = { Column { Text("Choose where to add this batch on the selected day."); MealSlot.entries.forEach { target -> TextButton({ vm.library.chooseBatchMeal(target) }, Modifier.fillMaxWidth()) { Text(target.label()) } } } },
        confirmButton = { TextButton(vm.library::dismissBatchMealPicker) { Text("Cancel") } }
    )
}
