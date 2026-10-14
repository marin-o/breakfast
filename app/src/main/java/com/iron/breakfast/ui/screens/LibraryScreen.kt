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

@Composable internal fun LibraryScreen(vm: BreakfastViewModel, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(if (vm.slot == null) "Food Library" else "Add to ${vm.slot!!.label()}", style = MaterialTheme.typography.headlineMedium)
            Text(if (vm.slot == null) "Used foods appear first. Everything here is available offline." else "Choose a local food, adjust its amount, and add it to this meal.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(vm::toggleLibraryPersonal) { Text(if (vm.libraryOnlyPersonal) "All foods" else "Foods") }
                OutlinedButton(vm::toggleLibraryArchived) { Text(if (vm.libraryIncludeArchived) "Hide archived" else "Archived") }
            }
            Button({ vm.openCustom() }, Modifier.fillMaxWidth()) { Text("Create personal food") }
        }
        if (vm.libraryFoods.isEmpty()) item { Text("No local foods match this search.") }
        if (vm.libraryFoods.isEmpty() && vm.libraryQuery.isNotBlank()) {
            item { TextButton({ vm.openSearch(vm.libraryQuery) }) { Text("Search Open Food Facts") } }
        }
        items(vm.libraryFoods, key = { it.id }) { food ->
            val last = vm.lastLoggedEntry(food)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { vm.openLibraryFood(food) }) {
                            Text(food.name, style = MaterialTheme.typography.titleMedium)
                            Text(listOfNotNull(food.brand, if (food.origin == com.iron.breakfast.domain.FoodOrigin.CUSTOM) "Personal food" else food.sourceName ?: "Bundled food").joinToString(" / "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (last != null) Text("Last logged: ${last.enteredAmount.pretty()} ${last.enteredUnit.displayLabel()} / ${last.nutritionSnapshot.calories.pretty()} kcal")
                            else {
                                val calculation = defaultServingCalculation(food)
                                val calories = if (calculation.supported) {
                                    "${calculation.nutrition.calories.pretty()} kcal"
                                } else {
                                    "${food.nutrition.values.calories.pretty()} kcal per ${food.nutrition.amount.pretty()} ${food.nutrition.unit.label} basis"
                                }
                                Text("Default serving: ${food.defaultAmount.pretty()} ${food.defaultUnit.displayLabel()} / $calories", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (vm.slot != null) {
                            TextButton({ vm.logLibraryFood(food, vm.slot!!, last?.enteredAmount ?: food.defaultAmount, last?.enteredUnit ?: food.defaultUnit) }, enabled = !food.archived) { Text("+") }
                        } else {
                            TextButton({ vm.toggleLibraryFoodPicker(food) }, enabled = !food.archived) { Text("+") }
                        }
                    }
                    if (vm.slot == null && vm.expandedLibraryFoodId == food.id) {
                        Text("Repeat ${last?.enteredAmount?.pretty() ?: food.defaultAmount.pretty()} ${last?.enteredUnit?.displayLabel() ?: food.defaultUnit.displayLabel()} on ${vm.selectedDayKey}", style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            MealSlot.entries.forEach { target ->
                                TextButton({
                                    vm.logLibraryFood(food, target, last?.enteredAmount ?: food.defaultAmount, last?.enteredUnit ?: food.defaultUnit)
                                    vm.collapseLibraryFoodPicker()
                                }) { Text(target.label()) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun LibraryComposer(vm: BreakfastViewModel) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(vm::beginScan, Modifier.height(46.dp)) { Text("Scan") }
        BasicTextField(
            value = vm.input,
            onValueChange = vm::onInput,
            modifier = Modifier.weight(1f)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 11.dp)
                .onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) { vm.submitLibraryInput(); true } else false },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.submitLibraryInput() }),
            decorationBox = { inner -> Box { if (vm.input.isBlank()) Text("Search or add a food", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } }
        )
        Button(vm::submitLibraryInput, Modifier.height(46.dp)) { Text("Go") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun LibraryFoodDetailDialog(vm: BreakfastViewModel, food: FoodRecord) {
    var amountText by remember(food.id) { mutableStateOf((vm.lastLoggedEntry(food)?.enteredAmount ?: food.defaultAmount).pretty()) }
    var unitText by remember(food.id) { mutableStateOf((vm.lastLoggedEntry(food)?.enteredUnit ?: food.defaultUnit).displayLabel()) }
    Dialog(onDismissRequest = vm::closeLibraryFood, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = { TopAppBar(title = { Text(food.name) }, navigationIcon = { TextButton(vm::closeLibraryFood) { Text("Back") } }) }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(listOfNotNull(food.brand, food.sourceName ?: if (food.origin == com.iron.breakfast.domain.FoodOrigin.CUSTOM) "Personal food" else "Bundled food").joinToString(" · "))
                    food.barcode?.let { Text("Barcode: $it") }
                    Text("Nutrition basis: ${food.nutrition.amount.pretty()} ${food.nutrition.unit.label}")
                    Text("${food.nutrition.values.calories.pretty()} kcal  ·  P ${food.nutrition.values.protein.macro()}  C ${food.nutrition.values.carbohydrates.macro()}  F ${food.nutrition.values.fat.macro()}")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(amountText, { amountText = it }, label = { Text("Amount") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(unitText, { unitText = it }, label = { Text("Unit") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Button({
                        val amount = amountText.toDoubleOrNull()
                        val unit = unitText.toMeasureUnit()
                        if (amount != null && amount > 0.0 && unit != null) { vm.logLibraryFood(food, amount, unit); vm.closeLibraryFood() } else vm.noticeForInvalidLibraryAmount()
                    }, enabled = !food.archived, modifier = Modifier.fillMaxWidth()) { Text("Add to ${vm.slot?.label() ?: "a meal"}") }
                    TextButton({ vm.closeLibraryFood(); vm.openLibraryEdit(food) }, Modifier.fillMaxWidth()) { Text("Edit local food data") }
                    if (food.barcode.isNullOrBlank()) Text("Open Food Facts submission requires a barcode.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else TextButton({ vm.closeLibraryFood(); vm.openContributionForFood(food) }, Modifier.fillMaxWidth()) { Text("Submit to Open Food Facts") }
                    if (food.origin == com.iron.breakfast.domain.FoodOrigin.CUSTOM && !food.archived) TextButton({ vm.archiveLibraryFood(food) }, Modifier.fillMaxWidth()) { Text("Archive personal food") }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable internal fun FocusedFoodAddDialog(vm: BreakfastViewModel, food: FoodRecord) {
    val target = vm.slot ?: return
    var amountText by remember(food.id, target) { mutableStateOf((vm.lastLoggedEntry(food)?.enteredAmount ?: food.defaultAmount).pretty()) }
    var unitText by remember(food.id, target) { mutableStateOf((vm.lastLoggedEntry(food)?.enteredUnit ?: food.defaultUnit).displayLabel()) }
    AlertDialog(
        onDismissRequest = vm::closeLibraryFood,
        title = { Text("Add ${food.name} to ${target.label()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${food.nutrition.values.calories.pretty()} kcal per ${food.nutrition.amount.pretty()} ${food.nutrition.unit.label}")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(amountText, { amountText = it }, label = { Text("Amount") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(unitText, { unitText = it }, label = { Text("Unit") }, modifier = Modifier.weight(1f), singleLine = true)
                }
            }
        },
        confirmButton = { Button({
            val amount = amountText.toDoubleOrNull()
            val unit = unitText.toMeasureUnit()
            if (amount != null && amount > 0.0 && unit != null) {
                vm.logLibraryFood(food, target, amount, unit)
                vm.closeLibraryFood()
            } else vm.noticeForInvalidLibraryAmount()
        }, enabled = !food.archived) { Text("Add to ${target.label()}") } },
        dismissButton = { TextButton(vm::closeLibraryFood) { Text("Back") } }
    )
}

