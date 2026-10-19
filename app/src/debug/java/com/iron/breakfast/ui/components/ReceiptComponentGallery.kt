package com.iron.breakfast.ui.components

import com.iron.breakfast.ui.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iron.breakfast.ui.theme.BreakfastTheme
import com.iron.breakfast.ui.theme.LocalReceiptColors

/** Ground level preview board for Receipt v5 shared components. */
@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 844)
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 844, fontScale = 1.3f)
private fun ReceiptComponentGallery() {
    BreakfastTheme {
        val colors = LocalReceiptColors.current
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.paper)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReceiptSlip("Today", "1424", "kcal", listOf("P 95 g", "C 193 g", "F 32 g"))
            ReceiptTicket("Breakfast", "327", "07:52", "Add food", {}) {
                ReceiptEntryRow("Rolled oats", "60 g × 3.89", "233", "B", first = true)
                ReceiptEntryRow("Kikiriki puter", "(16 g) × 5.88", "94", "P", approximate = "1 tbsp")
            }
            ReceiptQuickAddField(
                value = "1 tsp pb",
                onValueChange = {},
                onLog = {},
                expanded = true,
                visualTransformation = ReceiptTokenVisualTransformation(
                    listOf(
                        ReceiptTokenRange(0..0, ReceiptTokenKind.Quantity),
                        ReceiptTokenRange(2..7, ReceiptTokenKind.Food),
                    ),
                ),
            )
            ReceiptBottomTray(
                content = { Text("1,424 kcal  ·  P 95 g  ·  C 193 g  ·  F 32 g", color = colors.ink) },
                nav = { ReceiptNavigationBar(selectedDaily = true, onDaily = {}, onLibrary = {}) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReceiptChoiceChip("g", true, {})
                ReceiptActionChip("Search", {})
            }
            ReceiptSnackbar("Saved meal", "UNDO", {})
            ReceiptSticker("Kikiriki puter", "31", "1 tsp", fields = listOf("1 tbsp", "16 g"))
            Text("Receipt v5 component gallery", color = colors.ink)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 844)
private fun ReceiptSheetGallery() {
    BreakfastTheme {
        val colors = LocalReceiptColors.current
        ReceiptBottomSheet(onDismissRequest = {}) {
            Text("Add food", color = colors.ink, modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp))
        }
    }
}
