package com.iron.breakfast.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iron.breakfast.ui.theme.BreakfastTheme
import com.iron.breakfast.ui.theme.LocalBreakfastColors

/** Preview board for the Monochrome shared components. */
@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 844)
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 844, fontScale = 1.3f)
private fun ComponentGallery() {
    BreakfastTheme {
        val colors = LocalBreakfastColors.current
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DayTotal("1424", listOf(MacroStat("Protein", "95", "g", 0.27f), MacroStat("Carbs", "193", "g", 0.54f), MacroStat("Fat", "32", "g", 0.2f)))
            MealHeader("Breakfast", "546", onAdd = {}, addDescription = "Add food to Breakfast", time = "07:52")
            FoodRow("Rolled oats", "60 g", "233")
            FoodRow("Kikiriki puter", "≈1 tbsp, 16 g", "94")
            PrimaryButton("Add to Lunch", {})
            PrimaryButton("Continue", {}, kind = ButtonKind.Neutral)
            SecondaryButton("Replace food", {})
            ChoiceOption("g", true, {})
            ChoiceOption("Kikiriki puter", false, {}, detail = "105 kcal")
            Field("Amount", "1.5", {}, errorMessage = "Enter more than 0 g.", error = true)
            Snackbar("Saved meal", "Undo", {})
            LinearProgress("Searching Open Food Facts")
            BottomNav(selectedToday = true, onToday = {}, onLibrary = {})
        }
    }
}
