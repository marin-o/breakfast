package com.iron.breakfast.ui.screens

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage
import com.iron.breakfast.data.BreakfastStore
import com.iron.breakfast.data.OffContributionDraft
import com.iron.breakfast.data.OffContributionResult
import com.iron.breakfast.data.OffPreflightResult
import com.iron.breakfast.data.OffResult
import com.iron.breakfast.data.OffSearchResult
import com.iron.breakfast.data.OpenFoodFactsClient
import com.iron.breakfast.domain.BreakfastEngine
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.Calculation
import com.iron.breakfast.domain.NutritionCalculator
import com.iron.breakfast.domain.RankedFood
import com.iron.breakfast.domain.ReplacementResult
import com.iron.breakfast.domain.ShorthandParser
import com.iron.breakfast.domain.toMeasureUnit
import com.iron.breakfast.domain.dayKeyForEpochMs
import com.iron.breakfast.domain.displayLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.hypot

import com.iron.breakfast.*

@Composable internal fun DailyScreen(vm: BreakfastViewModel, modifier: Modifier) {
    val selected = vm.entries.filter { it.dayKey == vm.selectedDayKey }
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val pageThreshold = with(LocalDensity.current) { 96.dp.toPx() }
    val dateGesture = Modifier.pointerInput(vm.selectedDayKey) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var dx = 0f
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                dx = change.position.x - down.position.x
                if (!change.pressed) break
                if (abs(dx) > touchSlop && abs(dx) > abs(change.position.y - down.position.y)) change.consume()
            }
            if (abs(dx) > pageThreshold) vm.shiftSelectedDay(if (dx < 0) 1 else -1)
        }
    }
    val dailyTotal = selected.fold(NutritionValues.ZERO) { total, entry -> total + entry.nutritionSnapshot }
    LazyColumn(modifier.fillMaxSize().then(dateGesture).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(vm.selectedDayKey, style = MaterialTheme.typography.headlineMedium)
                Row {
                    TextButton({ vm.shiftSelectedDay(-1) }) { Text("‹") }
                    TextButton(vm::goToday) { Text("Today") }
                    TextButton({ vm.shiftSelectedDay(1) }) { Text("›") }
                }
            }
            Text("${dailyTotal.calories.pretty()} kcal  ·  P ${dailyTotal.protein.macro()}  C ${dailyTotal.carbohydrates.macro()}  F ${dailyTotal.fat.macro()}", style = MaterialTheme.typography.titleMedium)
            Text("Swipe the diary to change date.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        vm.mealDraftError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        MealSlot.entries.forEach { s -> item { MealCard(s, selected.filter { it.mealSlot == s }, vm) } }
        item { OutlinedButton({ vm.openCustom() }, Modifier.fillMaxWidth()) { Text("Create a personal food") } }
        vm.pendingContribution?.let { item { ContributionAction(vm) } }
        vm.undoEntry?.let { item { TextButton({ vm.undo() }) { Text("Undo delete ${it.foodName}") } } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable internal fun DailyComposer(vm: BreakfastViewModel) {
    val focusRequester = remember { FocusRequester() }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BasicTextField(
            value = vm.input,
            onValueChange = vm::onInput,
            modifier = Modifier.weight(1f)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 11.dp).focusRequester(focusRequester)
                .onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) { vm.submit(); true } else false },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.submit() }),
            decorationBox = { inner -> Box { if (vm.input.isBlank()) Text("What did you eat?", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } }
        )
        Button(vm::submit, Modifier.height(46.dp)) { Text("Add") }
    }
}

@Composable internal fun MealCard(slot: MealSlot, entries: List<MealEntry>, vm: BreakfastViewModel) {
    val total = entries.fold(NutritionValues.ZERO) { a, e -> a + e.nutritionSnapshot }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(slot.label(), style = MaterialTheme.typography.titleLarge)
                    Text("${total.calories.pretty()} kcal  /  P ${total.protein.macro()}  C ${total.carbohydrates.macro()}  F ${total.fat.macro()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button({ vm.openFocusedLibrary(slot) }) { Text("Add food") }
            }
            entries.forEach { entry ->
                Row(Modifier.fillMaxWidth().clickable { vm.openEntryDetail(entry) }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${entry.foodName} · ${entry.enteredAmount.pretty()} ${entry.enteredUnit.displayLabel()}", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${entry.nutritionSnapshot.calories.pretty()} kcal", modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (entries.isEmpty()) Text("Nothing logged yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

