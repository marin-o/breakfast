package com.iron.breakfast.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.ui.theme.BreakfastTheme
import com.iron.breakfast.ui.theme.BreakfastType
import com.iron.breakfast.ui.theme.LocalBreakfastColors

/** Preview board showing every Monochrome shared component at least once. */
@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 1400)
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 1400, fontScale = 1.3f)
private fun ComponentGallery() {
    BreakfastTheme {
        val colors = LocalBreakfastColors.current
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TopBar("Entry", {}, subtitle = "Wed 14.10.2026")
            DayHeader("Wednesday 14.10.2026", {}, {}, showToday = true, onToday = {}, onTitleClick = {})
            DayTotal("1424", listOf(MacroStat("Protein", "95", "g", 0.27f), MacroStat("Carbs", "193", "g", 0.54f), MacroStat("Fat", "32", "g", 0.2f)))
            MealHeader("Breakfast", "546", onAdd = {}, addDescription = "Add food to Breakfast", time = "07:52")
            MealHeader("Dinner", "0", onAdd = {}, addDescription = "Add food to Dinner", emptyText = "Nothing yet")
            FoodRow("Rolled oats", "60 g", "233")
            FoodRow("Kikiriki puter", "≈1 tbsp, 16 g", "94", onClick = {})
            FoodRow("Total", "", "1424", total = true)
            PrimaryButton("Add to Lunch", {})
            PrimaryButton("Continue", {}, kind = ButtonKind.Neutral)
            PrimaryButton("Disabled", {}, enabled = false)
            SecondaryButton("Replace food", {})
            TertiaryButton("Open in Library", {})
            TertiaryButton("Soft", {}, soft = true)
            DestructiveButton("Delete entry", {})
            DestructiveButton("Archive food", {}, outlined = true)
            Row2 {
                ChoiceOption("g", true, {})
                ChoiceOption("ml", false, {}, detail = "105 kcal")
            }
            AddCircle("Add food", {})
            AddCircle("Remove serving", {}, icon = Icons.Outlined.Close)
            Field("Amount", "1.5", {}, errorMessage = "Enter more than 0 g.", error = true)
            Field("Name", "", {}, placeholder = "Name", hint = "from scan")
            SearchField("kiki", {}, {}, label = "Search foods")
            Composer("Log food", {}, onScan = {})
            BottomNav(selectedToday = true, onToday = {}, onLibrary = {})
            Snackbar("Saved meal", "Undo", {})
            LinearProgress("Searching Open Food Facts")
            SectionHeading("Servings")
            Hairline()
            MealChoices(MealSlot.LUNCH, {})
            Box(Modifier.padding(top = 4.dp)) { Text("Gallery", color = colors.soft, style = BreakfastType.Meta) }
        }
    }
}

@Composable
private fun Row2(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}
