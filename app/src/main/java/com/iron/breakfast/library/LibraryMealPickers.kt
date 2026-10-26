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
import com.iron.breakfast.domain.FoodRecord

@Composable internal fun MixedMealPickerDialog(vm: BreakfastViewModel) {
    AlertDialog(
        onDismissRequest = vm::dismissMixedMealPicker,
        title = { Text("Which meal?") },
        text = {
            Column {
                Text("Choose where to add this batch.")
                MealSlot.entries.forEach { target ->
                    TextButton({ vm.chooseMixedMeal(target) }, Modifier.fillMaxWidth()) { Text(target.label()) }
                }
            }
        },
        confirmButton = { TextButton(vm::dismissMixedMealPicker) { Text("Cancel") } }
    )
}

@Composable internal fun LibraryMealPickerDialog(vm: BreakfastViewModel, food: FoodRecord) {
    AlertDialog(
        onDismissRequest = vm::cancelLibraryMealPicker,
        title = { Text("Add ${food.name} to which meal?") },
        text = { Column { Text("Choose a meal on ${vm.selectedDayKey}"); MealSlot.entries.forEach { target -> TextButton({ vm.chooseLibraryMeal(target) }, Modifier.fillMaxWidth()) { Text(target.label()) } } } },
        confirmButton = { TextButton(vm::cancelLibraryMealPicker) { Text("Cancel") } }
    )
}

@Composable internal fun LibraryBatchMealPickerDialog(vm: BreakfastViewModel) {
    AlertDialog(
        onDismissRequest = { vm.dismissLibraryBatchMealPicker() },
        title = { Text("Add foods to which meal?") },
        text = { Column { Text("Choose where to add this batch on the selected day."); MealSlot.entries.forEach { target -> TextButton({ vm.chooseLibraryBatchMeal(target) }, Modifier.fillMaxWidth()) { Text(target.label()) } } } },
        confirmButton = { TextButton(vm::dismissLibraryBatchMealPicker) { Text("Cancel") } }
    )
}
