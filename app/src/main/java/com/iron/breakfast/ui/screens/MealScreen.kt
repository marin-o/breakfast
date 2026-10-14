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

@Composable internal fun MealScreen(vm: BreakfastViewModel, modifier: Modifier) {
    val target = vm.slot ?: return; val entries = vm.entries.filter { it.dayKey == vm.selectedDayKey && it.mealSlot == target }; val total = entries.fold(NutritionValues.ZERO) { a, e -> a + e.nutritionSnapshot }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Spacer(Modifier.height(8.dp)); Text(target.label(), style = MaterialTheme.typography.headlineMedium); Text(vm.selectedDayKey, style = MaterialTheme.typography.labelMedium); Text("${total.calories.pretty()} kcal  •  P ${total.protein.macro()}  C ${total.carbohydrates.macro()}  F ${total.fat.macro()}") }
        vm.pendingContribution?.let { item { ContributionAction(vm) } }
        items(entries, key = { it.id }) { entry -> EntryRow(entry, vm) }
        item { vm.undoEntry?.let { TextButton({ vm.undo() }) { Text("Undo delete ${it.foodName}") } }; TextButton({ vm.closeMeal() }) { Text("Back to diary") }; Spacer(Modifier.height(16.dp)) }
    }
}

@Composable internal fun MealComposer(vm: BreakfastViewModel, target: MealSlot) {
    val editing = vm.entryEditMode != EntryEditMode.NONE
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val annotation = when {
        vm.editInvalid -> "Invalid input"
        editing -> "Editing"
        vm.input.isNotBlank() -> "Adding"
        else -> null
    }
    var shakeOffset by remember { mutableStateOf(0) }
    LaunchedEffect(vm.editInvalidSignal) {
        if (vm.editInvalidSignal == 0) return@LaunchedEffect
        repeat(6) { index ->
            shakeOffset = if (index % 2 == 0) 5 else -5
            delay(45)
        }
        shakeOffset = 0
    }
    LaunchedEffect(editing) {
        if (editing) focusRequester.requestFocus() else focusManager.clearFocus()
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider()
        if (!editing) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(vm::openLibrary) { Text("Library") }
                TextButton({ vm.openCustom() }) { Text("Custom") }
                TextButton(vm::openSearch) { Text("Search") }
            }
        } else {
            Spacer(Modifier.height(16.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .offset(x = shakeOffset.dp)
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = vm.input,
                    onValueChange = vm::onInput,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester).onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) { vm.submit(); true } else false
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.submit() }),
                    decorationBox = { innerTextField ->
                        Box {
                            if (vm.input.isBlank()) {
                                Text(
                                    if (editing) "Amount and unit" else "Add foods",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (vm.entryEditMode == EntryEditMode.QUICK) {
                    Text(
                        vm.editingFoodName.orEmpty(),
                        Modifier.weight(1f).padding(start = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            annotation?.let {
                Text(
                    it,
                    Modifier.align(Alignment.TopEnd)
                        .offset(x = (-12).dp, y = (-8).dp)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (vm.editInvalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
        if (vm.quickEditHintVisible) {
            Text("Hold a food to change it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        vm.mealDraftError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(vm::submit, Modifier.fillMaxWidth()) { Text(if (!editing) "Add to ${target.label()}" else "Save entry edit") }
        if (editing) TextButton(vm::cancelEdit, Modifier.fillMaxWidth()) { Text("Cancel edit") }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable internal fun EntryRow(entry: MealEntry, vm: BreakfastViewModel) {
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val deleteThreshold = with(LocalDensity.current) { 96.dp.toPx() }
    Card(
        Modifier
            .fillMaxWidth()
            .semantics {
                onClick(label = "Open details for ${entry.foodName}") { vm.openEntryDetail(entry); true }
                customActions = listOf(
                    CustomAccessibilityAction("Quick edit amount") { vm.beginQuickEdit(entry); true },
                    CustomAccessibilityAction("Edit food and amount") { vm.beginFullEdit(entry); true },
                    CustomAccessibilityAction("Delete ${entry.foodName}") { vm.delete(entry); true }
                )
            }
            .combinedClickable(
                onClick = { vm.openEntryDetail(entry) },
                onDoubleClick = { vm.beginQuickEdit(entry) },
                onLongClick = { vm.beginFullEdit(entry) }
            )
            .pointerInput(entry.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var horizontal = false
                    var finalDx = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        finalDx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        if (change.pressed && !horizontal && hypot(finalDx.toDouble(), dy.toDouble()).toFloat() > touchSlop && abs(finalDx) > abs(dy)) horizontal = true
                        if (horizontal) change.consume()
                        if (!change.pressed) break
                    }
                    if (horizontal && finalDx < -deleteThreshold) vm.delete(entry)
                }
            }
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(entry.foodName, style = MaterialTheme.typography.titleMedium)
            Text("${entry.enteredAmount.pretty()} ${entry.enteredUnit.displayLabel()}  ·  ${entry.nutritionSnapshot.calories.pretty()} kcal")
            Text("P ${entry.nutritionSnapshot.protein.macro()}  C ${entry.nutritionSnapshot.carbohydrates.macro()}  F ${entry.nutritionSnapshot.fat.macro()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (entry.approximateConversion) Text("Approximate: ${entry.conversionProvenance}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (vm.feedbackOpen(entry)) {
                vm.alternatives(entry).forEach { candidate -> TextButton({ vm.chooseAlternative(entry, candidate.food) }) { Text("Use ${candidate.food.name}") } }
                TextButton({ vm.confirm(entry) }) { Text("Confirm ${entry.foodName}") }
                Text("Correction controls expire after 30 seconds.", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

