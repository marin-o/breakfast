package com.iron.breakfast.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iron.breakfast.BreakfastViewModel
import com.iron.breakfast.dayStartLabel
import com.iron.breakfast.ui.components.AddCircle
import com.iron.breakfast.ui.components.ChoiceOption
import com.iron.breakfast.ui.components.LongDraft
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.TopBar
import com.iron.breakfast.ui.theme.BreakfastType
import com.iron.breakfast.ui.theme.Gutter
import com.iron.breakfast.ui.theme.LocalBreakfastColors

/** General settings, opened from the Library menu: a plain list of rows. The arrow and Back return to the Library. */
@Composable
internal fun SettingsScreen(vm: BreakfastViewModel) {
    val colors = LocalBreakfastColors.current
    BackHandler { vm.closeSettings() }
    Column(Modifier.fillMaxSize().background(colors.bg).windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 4.dp)) {
        TopBar("Settings", vm::closeSettings, Modifier.padding(top = 8.dp))
        Hairline(Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Day starts at", Modifier.weight(1f), color = colors.ink, style = BreakfastType.Body)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AddCircle("Decrease", { vm.shiftDayStart(-1) }, icon = Icons.Outlined.Remove)
                Text(dayStartLabel(vm.dayStartHour), Modifier.widthIn(min = 56.dp), color = colors.ink, style = BreakfastType.BodyMedium, textAlign = TextAlign.Center)
                AddCircle("Increase", { vm.shiftDayStart(1) })
            }
        }
        Hairline()
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Long draft", color = colors.ink, style = BreakfastType.Body)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LONG_DRAFT_CHOICES.forEach { (mode, label) -> ChoiceOption(label, vm.longDraft == mode, { vm.chooseLongDraft(mode) }) }
            }
        }
        Hairline()
    }
}

private val LONG_DRAFT_CHOICES = listOf(LongDraft.END to "End", LongDraft.START to "Start", LongDraft.TWO_LINES to "Two lines")
