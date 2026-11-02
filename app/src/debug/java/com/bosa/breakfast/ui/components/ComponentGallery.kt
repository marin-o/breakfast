package com.bosa.breakfast.ui.components

import com.bosa.breakfast.domain.WeightPoint
import com.bosa.breakfast.domain.WeightRange
import com.bosa.breakfast.domain.weightSeries
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
import com.bosa.breakfast.domain.MealSlot
import com.bosa.breakfast.ui.theme.BreakfastTheme
import com.bosa.breakfast.ui.theme.BreakfastType
import com.bosa.breakfast.ui.theme.LocalBreakfastColors
import com.bosa.breakfast.ui.theme.Macro

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
            DayTotal("1424", listOf(MacroStat(Macro.PROTEIN, "Protein", "95", "g", 0.27f), MacroStat(Macro.CARBS, "Carbs", "193", "g", 0.54f), MacroStat(Macro.FAT, "Fat", "32", "g", 0.2f)))
            DayTotal("1424", emptyList(), target = "/ 2000 kcal")
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
            DestructiveButton("Delete food", {}, outlined = true)
            Row2 {
                ChoiceOption("g", true, {})
                ChoiceOption("ml", false, {}, detail = "105 kcal")
            CheckboxRow("Add to curve", true, {})
            WeightChart(weightSeries(listOf(WeightPoint("2026-10-01", 82.4, 0L), WeightPoint("2026-10-12", 81.0, 0L), WeightPoint("2026-10-31", 80.2, 0L)), WeightRange.THREE_MONTHS, "2026-10-31"), 78.0)
            WeightChart(weightSeries(listOf(WeightPoint("2026-10-31", 80.2, 0L)), WeightRange.THREE_MONTHS, "2026-10-31"), null)
            StepDots(StepPosition(1, 5))
            }
            AddCircle("Add food", {})
            AddCircle("Remove serving", {}, icon = Icons.Outlined.Close)
            Field("Amount", "1.5", {}, errorMessage = "Enter more than 0 g.", error = true)
            Field("Name", "", {}, placeholder = "Name", hint = "from scan")
            SearchField("kiki", {}, {}, label = "Search foods")
            Composer(onScan = {}) { ComposerText("Log food", {}, Modifier.weight(1f)) }
            BottomNav(selected = NavTab.MEALS, onMeals = {}, onLibrary = {}, onRecipes = {}, onSettings = {}, onProfile = {})
            Toast("Saved meal", "Undo", {})
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
