package com.iron.breakfast

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage
import com.iron.breakfast.data.BreakfastStore
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
import com.iron.breakfast.domain.RankedFood
import com.iron.breakfast.domain.ReplacementResult
import com.iron.breakfast.domain.ShorthandParser
import com.iron.breakfast.domain.dayKeyForEpochMs
import com.iron.breakfast.domain.displayLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { IronApp() }
    }
}

class BreakfastViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = BreakfastEngine(BreakfastStore(application))
    private val off = OpenFoodFactsClient()
    private val parser = ShorthandParser()
    var input by mutableStateOf(""); private set
    var foods by mutableStateOf<List<FoodRecord>>(emptyList()); private set
    var entries by mutableStateOf<List<MealEntry>>(emptyList()); private set
    var slot by mutableStateOf<MealSlot?>(null); private set
    var customOpen by mutableStateOf(false); private set
    var customSeedName by mutableStateOf(""); private set
    var customSeedBrand by mutableStateOf(""); private set
    var customSeedBarcode by mutableStateOf(""); private set
    var searchOpen by mutableStateOf(false); private set
    var searchQuery by mutableStateOf(""); private set
    var offResults by mutableStateOf<List<FoodRecord>>(emptyList()); private set
    var searchBusy by mutableStateOf(false); private set
    var offSearchMessage by mutableStateOf<String?>(null); private set
    var scannerOpen by mutableStateOf(false); private set
    var selectedProduct by mutableStateOf<FoodRecord?>(null); private set
    var productAmount by mutableStateOf("100"); private set
    var productUnit by mutableStateOf("g"); private set
    var productName by mutableStateOf(""); private set
    var productCalories by mutableStateOf(""); private set
    var productProtein by mutableStateOf(""); private set
    var productCarbs by mutableStateOf(""); private set
    var productFat by mutableStateOf(""); private set
    var notice by mutableStateOf<String?>(null); private set
    var undoEntry by mutableStateOf<MealEntry?>(null); private set
    var mealDraftError by mutableStateOf<String?>(null); private set
    var mealPickerOpen by mutableStateOf(false); private set
    var ready by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var initializationError by mutableStateOf<String?>(null); private set
    var editingEntryId by mutableStateOf<String?>(null); private set
    var unknownQueue by mutableStateOf<List<String>>(emptyList()); private set
    var unknownQueueTarget by mutableStateOf<MealSlot?>(null); private set
    var resolutionContinuation by mutableStateOf(false); private set
    var resolverOpen by mutableStateOf(false); private set
    var resolverQuery by mutableStateOf(""); private set
    var resolverResults by mutableStateOf<List<RankedFood>>(emptyList()); private set
    var feedbackUntil by mutableStateOf(0L); private set
    var feedbackIds by mutableStateOf<Set<String>>(emptySet()); private set

    init { viewModelScope.launch { runCatching { engine.initialize(); refresh() }.onSuccess { ready = true }.onFailure { initializationError = "Storage could not be opened: ${it.message}" } } }
    private fun refresh() { foods = engine.foods(); entries = engine.persistedMeal().entries }
    fun reloadToday() { if (ready) refresh() }
    private fun abandonUnknownResolution(clearDraft: Boolean = false) {
        unknownQueue = emptyList()
        unknownQueueTarget = null
        resolutionContinuation = false
        resolverOpen = false
        if (clearDraft) input = ""
    }
    fun openMeal(s: MealSlot) { abandonUnknownResolution(); slot = s; input = ""; editingEntryId = null; mealDraftError = null }
    fun closeMeal() { abandonUnknownResolution(clearDraft = true); slot = null; input = ""; editingEntryId = null; mealDraftError = null }
    fun onInput(v: String) { input = v }
    fun beginEdit(entry: MealEntry) { editingEntryId = entry.id; input = "${entry.enteredAmount.pretty()} ${entry.enteredUnit.displayLabel()} ${entry.foodName}" }
    fun submit() {
        if (!ready || busy) return
        if (input.isBlank()) { mealDraftError = "Add at least one food."; return }
        val target = slot ?: run {
            val markers = mealMarkers(input)
            when {
                markers.isEmpty() -> { mealPickerOpen = true; mealDraftError = null; return }
                markers.size > 1 -> { mealDraftError = "Choose one meal marker, or remove the extra markers."; return }
                else -> markers.single()
            }
        }
        submitToMeal(target)
    }

    fun chooseMeal(target: MealSlot) {
        mealPickerOpen = false
        submitToMeal(target)
    }

    fun dismissMealPicker() { mealPickerOpen = false }

    private fun submitToMeal(target: MealSlot) {
        if (!ready || busy) return
        val captured = input
        val editing = editingEntryId
        if (editing != null) {
            busy = true
            viewModelScope.launch { runCatching { engine.replaceEntryFromText(editing, input) }.onSuccess { result -> if (result is ReplacementResult.Success) { refresh(); editingEntryId = null; input = ""; mealDraftError = null } }.onFailure { mealDraftError = it.message ?: "Could not edit this entry." }.also { busy = false } }
            return
        }
        val cleaned = removeMealMarker(input, target)
        if (cleaned.isBlank()) { mealDraftError = "Add at least one food."; return }
        busy = true
        viewModelScope.launch {
            runCatching { engine.save(cleaned, mealSlot = target) }.onSuccess { saved ->
                refresh(); if (input == captured) input = saved.unresolvedSegments.joinToString(", "); unknownQueue = saved.unresolvedSegments; unknownQueueTarget = target.takeIf { saved.unresolvedSegments.isNotEmpty() }; resolutionContinuation = saved.unresolvedSegments.isNotEmpty(); resolverOpen = saved.unresolvedSegments.isNotEmpty(); mealDraftError = if (saved.unresolvedSegments.isNotEmpty()) "Some foods need a match." else null
                feedbackIds = saved.entries.mapTo(mutableSetOf()) { it.id }; feedbackUntil = System.currentTimeMillis() + 30_000L
                if (slot == null) slot = target
            }.onFailure { mealDraftError = it.message ?: "Could not save this meal." }.also { busy = false }
        }
    }
    private fun parseMealMarker(value: String): MealSlot? {
        return mealMarkers(value).singleOrNull()
    }
    private fun mealMarkers(value: String): List<MealSlot> = mealMarkerSlots(value)
    private fun removeMealMarker(value: String, target: MealSlot): String = removeMealMarkerToken(value, target)
    fun delete(entry: MealEntry) { if (!ready || busy) return; busy = true; viewModelScope.launch { runCatching { engine.deleteEntry(entry.id) }.onSuccess { undoEntry = entry; refresh() }.also { busy = false } } }
    fun undo() { val e = undoEntry ?: return; if (!ready || busy) return; busy = true; viewModelScope.launch { runCatching { engine.restoreEntry(e) }.onSuccess { undoEntry = null; refresh() }.also { busy = false } } }
    fun openCustom(name: String = "", brand: String = "", barcode: String = "", fromResolver: Boolean = false) {
        if (!fromResolver && unknownQueue.isNotEmpty()) abandonUnknownResolution()
        resolutionContinuation = fromResolver
        customSeedName = name
        customSeedBrand = brand
        customSeedBarcode = barcode
        customOpen = true
        searchOpen = false
        resolverOpen = false
    }
    fun closeCustom() { customOpen = false; if (resolutionContinuation && unknownQueue.isNotEmpty()) resolverOpen = true else resolutionContinuation = false }
    fun closeResolver() { abandonUnknownResolution() }
    fun dismissNotice() { notice = null }
    fun clearSelectedProduct() { selectedProduct = null; if (resolutionContinuation && unknownQueue.isNotEmpty()) resolverOpen = true else resolutionContinuation = false }
    fun onProductAmount(v: String) { productAmount = v }
    fun onProductUnit(v: String) { productUnit = v }
    fun onProductName(v: String) { productName = v }
    fun onProductCalories(v: String) { productCalories = v }
    fun onProductProtein(v: String) { productProtein = v }
    fun onProductCarbs(v: String) { productCarbs = v }
    fun onProductFat(v: String) { productFat = v }
    fun editInput(v: String) { input = v }
    fun currentUnknownName(): String = unknownQueue.firstOrNull()?.let { parser.parse(it).foodPhrase.trim().ifBlank { it.trim() } }.orEmpty()
    fun openSearch(prefill: String? = null, fromResolver: Boolean = false) {
        if (!fromResolver && unknownQueue.isNotEmpty()) abandonUnknownResolution()
        resolutionContinuation = fromResolver
        searchOpen = true
        customOpen = false
        resolverOpen = false
        searchQuery = prefill ?: ""
        offResults = emptyList()
        offSearchMessage = null
    }
    fun closeSearch() { searchOpen = false; if (resolutionContinuation && unknownQueue.isNotEmpty()) resolverOpen = true else resolutionContinuation = false }
    fun onSearch(v: String) { searchQuery = v; offResults = emptyList(); offSearchMessage = null }
    fun onResolverQuery(v: String) { resolverQuery = v; resolverResults = if (v.isBlank()) emptyList() else engine.search(v) }
    private fun advanceUnknownQueue() {
        unknownQueue = unknownQueue.drop(1)
        input = unknownQueue.joinToString(", ")
        resolverQuery = ""
        resolverResults = emptyList()
        resolverOpen = unknownQueue.isNotEmpty()
        if (unknownQueue.isEmpty()) {
            unknownQueueTarget = null
            resolutionContinuation = false
        }
    }
    fun resolveUnknown(food: FoodRecord) { val raw = unknownQueue.firstOrNull() ?: return; val target = slot ?: return; if (unknownQueueTarget != target || !resolutionContinuation) return; val parsed = parser.parse(raw); val amount = parsed.amount ?: food.defaultAmount; val unit = parsed.unit ?: food.defaultUnit; if (!ready || busy) return; busy = true; viewModelScope.launch { runCatching { engine.cacheFoodAndLog(food, amount, unit, target) }.onSuccess { advanceUnknownQueue(); refresh() }.onFailure { notice = it.message ?: "Could not resolve this food." }.also { busy = false } } }
    fun discardUnknowns() { abandonUnknownResolution(clearDraft = true) }
    fun feedbackOpen(entry: MealEntry): Boolean = entry.id in feedbackIds && System.currentTimeMillis() < feedbackUntil
    fun alternatives(entry: MealEntry): List<RankedFood> = if (feedbackOpen(entry)) engine.alternativesFor(entry) else emptyList()
    fun confirm(entry: MealEntry) { if (!ready || busy || !feedbackOpen(entry)) return; busy = true; viewModelScope.launch { runCatching { engine.reinforce(entry.foodId) }.onSuccess { feedbackIds = feedbackIds - entry.id }.also { busy = false } } }
    fun chooseAlternative(entry: MealEntry, food: FoodRecord) { if (!ready || busy || !feedbackOpen(entry)) return; busy = true; viewModelScope.launch { runCatching { engine.replaceEntry(entry.id, food.id); engine.reinforce(food.id) }.onSuccess { feedbackIds = feedbackIds - entry.id; refresh() }.also { busy = false } } }
    fun expireFeedback() { feedbackUntil = 0L; feedbackIds = emptySet() }
    fun holdFeedback() { if (resolverOpen && feedbackUntil > 0L) feedbackUntil += 1000L }
    fun submitSearch() {
        if (!ready || searchQuery.isBlank() || searchBusy) return
        val captured = searchQuery
        searchBusy = true
        offSearchMessage = null
        viewModelScope.launch(Dispatchers.IO) {
            val result = off.search(captured)
            withContext(Dispatchers.Main) {
                if (searchQuery == captured) {
                    when (result) {
                        is OffSearchResult.Success -> {
                            offResults = result.foods
                            offSearchMessage = if (result.foods.isEmpty()) "No usable result for \"$captured\"." else null
                        }
                        OffSearchResult.Unavailable -> {
                            offResults = emptyList()
                            offSearchMessage = "Open Food Facts unavailable; try again or add manually."
                        }
                        is OffSearchResult.Error -> {
                            offResults = emptyList()
                            offSearchMessage = result.message
                        }
                    }
                }
                searchBusy = false
            }
        }
    }
    fun chooseProduct(food: FoodRecord) { selectedProduct = food; searchOpen = false; productAmount = "100"; productUnit = if (food.nutrition.unit == MeasureUnit.MILLILITER) "ml" else "g"; productName = food.name; productCalories = food.nutrition.values.calories.toString(); productProtein = food.nutrition.values.protein?.toString().orEmpty(); productCarbs = food.nutrition.values.carbohydrates?.toString().orEmpty(); productFat = food.nutrition.values.fat?.toString().orEmpty() }
    fun addSelected() { val food = selectedProduct ?: return; if (!ready || busy) return; val amount = productAmount.toDoubleOrNull(); val unit = if (productUnit.trim().lowercase() == "ml") MeasureUnit.MILLILITER else MeasureUnit.GRAM; val target = slot ?: run { notice = "Open a meal before adding this food."; return }; val resolvesQueue = resolutionContinuation && unknownQueueTarget == target; val kcal = productCalories.toDoubleOrNull(); if (amount == null || !amount.isFinite() || amount <= 0.0 || kcal == null || !kcal.isFinite() || kcal < 0.0) { notice = "Enter a positive amount and valid calories."; return }; val draft = food.copy(name = productName.trim().ifBlank { food.name }, nutrition = NutritionBasis(food.nutrition.amount, food.nutrition.unit, NutritionValues(kcal, productProtein.toDoubleOrNull(), productCarbs.toDoubleOrNull(), productFat.toDoubleOrNull()))); busy = true; viewModelScope.launch { runCatching { engine.cacheFoodAndLog(draft, amount, unit, target) }.onSuccess { selectedProduct = null; if (resolvesQueue) advanceUnknownQueue() else resolutionContinuation = false; refresh(); notice = "Added to ${target.label()}" }.onFailure { notice = it.message ?: "This amount cannot be logged." }.also { busy = false } } }
    fun beginScan() { scannerOpen = true }
    fun endScan() { scannerOpen = false }
    fun onBarcode(code: String) { scannerOpen = false; viewModelScope.launch(Dispatchers.IO) { val local = engine.foodByBarcode(code); if (local != null) { withContext(Dispatchers.Main) { chooseProduct(local) }; return@launch }; when (val result = off.barcode(code)) { is OffResult.Found -> withContext(Dispatchers.Main) { chooseProduct(result.food) }; is OffResult.Ineligible -> withContext(Dispatchers.Main) { notice = "Nutrition is incomplete. Review it as a personal food."; openCustom(result.name.orEmpty(), result.brand.orEmpty(), result.barcode) }; OffResult.Missing -> withContext(Dispatchers.Main) { notice = "Product not found. Add it manually with barcode $code"; openCustom(barcode = code) }; OffResult.Unavailable -> withContext(Dispatchers.Main) { notice = "No connection. Add the product manually."; openCustom(barcode = code) }; is OffResult.Error -> withContext(Dispatchers.Main) { notice = result.message } } } }
    fun saveCustom(name: String, brand: String, barcode: String, alias: String, shorthand: String, basisAmountText: String, basisUnitText: String, calories: String, protein: String, carbs: String, fat: String) {
        fun shorthandNumber(label: String) = Regex("(?i)([0-9]+(?:[.,][0-9]+)?)\\s*g?\\s*$label").find(shorthand)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
        val kcal = calories.toDoubleOrNull() ?: shorthandNumber("kcal"); val proteinValue = protein.toDoubleOrNull() ?: shorthandNumber("protein"); val carbsValue = carbs.toDoubleOrNull() ?: shorthandNumber("(?:carb|carbs)"); val fatValue = fat.toDoubleOrNull() ?: shorthandNumber("fat")
        if (name.isBlank() || kcal == null) { notice = "Name and calories are required."; return }
        val basisAmount = basisAmountText.toDoubleOrNull() ?: 100.0
        val basisUnit = if (basisUnitText.trim().lowercase() == "ml") MeasureUnit.MILLILITER else MeasureUnit.GRAM
        val nutrition = NutritionValues(kcal, proteinValue, carbsValue, fatValue)
        if (!nutrition.calories.isFinite() || nutrition.calories < 0.0 || nutrition.calories > 10000.0 || listOf(nutrition.protein, nutrition.carbohydrates, nutrition.fat).any { it != null && (!it.isFinite() || it < 0.0 || it > 1000.0) }) { notice = "Nutrition values must be finite and nonnegative."; return }
        if (!ready || busy || !basisAmount.isFinite() || basisAmount <= 0.0) { notice = "Enter a positive nutrition basis."; return }
        val resolvesQueue = resolutionContinuation && unknownQueueTarget == slot
        busy = true; viewModelScope.launch { runCatching { val target = slot; if (target != null) engine.saveCustomAndLog(name, brand, barcode, alias, nutrition, basisAmount, basisUnit, target, basisAmount, basisUnit) else engine.saveCustomFood(name, brand, barcode, alias, nutrition, basisAmount, basisUnit) }.onSuccess {
            refresh()
            customOpen = false
            if (resolvesQueue && unknownQueue.isNotEmpty()) advanceUnknownQueue() else resolutionContinuation = false
            notice = if (slot != null) "Saved to ${slot!!.label()} and your food library." else "Saved to your food library."
        }.onFailure { notice = it.message ?: "Could not save this food." }.also { busy = false } }
    }
}

internal fun mealMarkerSlots(value: String): List<MealSlot> = ShorthandParser().split(value).mapNotNull { token ->
    when (token.trim().lowercase(Locale.ROOT)) {
        "breakfast" -> MealSlot.BREAKFAST
        "lunch" -> MealSlot.LUNCH
        "dinner" -> MealSlot.DINNER
        "snacks", "snack" -> MealSlot.SNACKS
        else -> null
    }
}

internal fun removeMealMarkerToken(value: String, target: MealSlot): String = ShorthandParser().split(value)
    .filterNot { mealMarkerSlots(it).singleOrNull() == target }
    .joinToString(", ")

private fun MealSlot.label() = name.lowercase().replaceFirstChar { it.titlecase() }
private fun Double.pretty() = if (this % 1 == 0.0) "%.0f".format(Locale.ROOT, this) else "%.1f".format(Locale.ROOT, this)
private fun Double?.macro() = this?.let { "${it.pretty()} g" } ?: "—"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IronApp(vm: BreakfastViewModel = viewModel()) {
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); vm.reloadToday() } }
    LaunchedEffect(vm.feedbackUntil, vm.resolverOpen) { while (vm.feedbackUntil > 0L) { kotlinx.coroutines.delay(1000); if (vm.resolverOpen) vm.holdFeedback() else if (System.currentTimeMillis() >= vm.feedbackUntil) { vm.expireFeedback(); break } } }
    if (vm.scannerOpen) { BarcodeScanner(vm); return }
    Scaffold(
        topBar = { TopAppBar(title = { Text(vm.slot?.label() ?: "Iron") }) },
        bottomBar = { vm.slot?.let { MealComposer(vm, it) } }
    ) { pad ->
        if (vm.slot == null) Home(vm, Modifier.padding(pad)) else MealDetail(vm, Modifier.padding(pad))
    }
    if (vm.slot != null && !vm.customOpen && !vm.searchOpen && !vm.resolverOpen && vm.selectedProduct == null) {
        BackHandler { vm.closeMeal() }
    }
    if (vm.mealPickerOpen) MealPickerDialog(vm)
    if (vm.customOpen) CustomDialog(vm)
    if (vm.searchOpen) SearchDialog(vm)
    if (vm.resolverOpen) UnknownResolver(vm)
    vm.selectedProduct?.let { ProductDialog(vm, it) }
    vm.notice?.let { message -> AlertDialog(onDismissRequest = vm::dismissNotice, confirmButton = { TextButton(vm::dismissNotice) { Text("OK") } }, text = { Text(message) }) }
    vm.initializationError?.let { message -> AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("Storage unavailable") }, text = { Text(message) }) }
}

@Composable private fun Home(vm: BreakfastViewModel, modifier: Modifier) {
    val today = vm.entries.filter { it.dayKey == dayKeyForEpochMs(System.currentTimeMillis()) }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Spacer(Modifier.height(8.dp)); Text("Today", style = MaterialTheme.typography.headlineMedium); Text("Log a food with a meal marker, or open a meal card.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { OutlinedTextField(
            vm.input,
            vm::onInput,
            Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) { vm.submit(); true } else false
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.submit() }),
            label = { Text("Food shorthand") },
            placeholder = { Text("100g oats (choose meal after submit)") }
        ) }
        item { Button({ vm.submit() }, Modifier.fillMaxWidth()) { Text("Add foods") } }
        vm.mealDraftError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        MealSlot.entries.forEach { s -> item { MealCard(s, today.filter { it.mealSlot == s }, vm) } }
        item { OutlinedButton({ vm.openCustom() }, Modifier.fillMaxWidth()) { Text("Create a personal food") } }
        vm.undoEntry?.let { item { TextButton({ vm.undo() }) { Text("Undo delete ${it.foodName}") } } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable private fun MealPickerDialog(vm: BreakfastViewModel) {
    AlertDialog(
        onDismissRequest = vm::dismissMealPicker,
        title = { Text("Which meal?") },
        text = {
            Column {
                Text("Choose where to add this batch.")
                MealSlot.entries.forEach { target ->
                    TextButton({ vm.chooseMeal(target) }, Modifier.fillMaxWidth()) { Text(target.label()) }
                }
            }
        },
        confirmButton = { TextButton(vm::dismissMealPicker) { Text("Cancel") } }
    )
}

@Composable private fun MealCard(slot: MealSlot, entries: List<MealEntry>, vm: BreakfastViewModel) {
    val total = entries.fold(NutritionValues.ZERO) { a, e -> a + e.nutritionSnapshot }
    Card(Modifier.fillMaxWidth().clickable { vm.openMeal(slot) }) { Column(Modifier.padding(14.dp)) {
        Text(slot.label(), style = MaterialTheme.typography.titleLarge); Text("${total.calories.pretty()} kcal  •  P ${total.protein.macro()}  C ${total.carbohydrates.macro()}  F ${total.fat.macro()}")
        entries.forEach { Text("${it.foodName}  ·  ${it.enteredAmount.pretty()} ${it.enteredUnit.displayLabel()}  ·  ${it.nutritionSnapshot.calories.pretty()} kcal") }
    } }
}

@Composable private fun MealDetail(vm: BreakfastViewModel, modifier: Modifier) {
    val target = vm.slot ?: return; val entries = vm.entries.filter { it.dayKey == dayKeyForEpochMs(System.currentTimeMillis()) && it.mealSlot == target }; val total = entries.fold(NutritionValues.ZERO) { a, e -> a + e.nutritionSnapshot }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Spacer(Modifier.height(8.dp)); Text(target.label(), style = MaterialTheme.typography.headlineMedium); Text("${total.calories.pretty()} kcal  •  P ${total.protein.macro()}  C ${total.carbohydrates.macro()}  F ${total.fat.macro()}") }
        items(entries, key = { it.id }) { entry -> EntryRow(entry, vm) }
        item { vm.undoEntry?.let { TextButton({ vm.undo() }) { Text("Undo delete ${it.foodName}") } }; TextButton({ vm.closeMeal() }) { Text("Back to today") }; Spacer(Modifier.height(16.dp)) }
    }
}

@Composable private fun MealComposer(vm: BreakfastViewModel, target: MealSlot) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton({ vm.openCustom() }) { Text("Custom") }
            TextButton(vm::openSearch) { Text("Search") }
            TextButton(vm::beginScan) { Text("Scan") }
        }
        OutlinedTextField(
            vm.input,
            vm::onInput,
            Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) { vm.submit(); true } else false
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.submit() }),
            label = { Text("Add foods") },
            placeholder = { Text("100g chicken, 1 tomato") }
        )
        vm.mealDraftError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(vm::submit, Modifier.fillMaxWidth()) { Text(if (vm.editingEntryId == null) "Add to ${target.label()}" else "Save entry edit") }
    }
}

@Composable private fun EntryRow(entry: MealEntry, vm: BreakfastViewModel) {
    var offset by remember { mutableStateOf(0f) }
    Card(Modifier.fillMaxWidth().pointerInput(entry.id) { detectHorizontalDragGestures(onDragEnd = { if (offset < -120f) vm.delete(entry); offset = 0f }) { _, drag -> offset += drag } }.clickable { vm.beginEdit(entry) }) { Column(Modifier.padding(12.dp)) { Text(entry.foodName, style = MaterialTheme.typography.titleMedium); Text("${entry.enteredAmount.pretty()} ${entry.enteredUnit.displayLabel()}  ·  ${entry.nutritionSnapshot.calories.pretty()} kcal"); Text("P ${entry.nutritionSnapshot.protein.macro()}  C ${entry.nutritionSnapshot.carbohydrates.macro()}  F ${entry.nutritionSnapshot.fat.macro()}", color = MaterialTheme.colorScheme.onSurfaceVariant); if (vm.feedbackOpen(entry)) { vm.alternatives(entry).forEach { candidate -> TextButton({ vm.chooseAlternative(entry, candidate.food) }) { Text("Use ${candidate.food.name}") } }; TextButton({ vm.confirm(entry) }) { Text("Confirm ${entry.foodName}") }; Text("Correction controls expire after 30 seconds.", style = MaterialTheme.typography.labelSmall) } } }
}

@Composable private fun CustomDialog(vm: BreakfastViewModel) {
    var name by remember { mutableStateOf(vm.customSeedName) }; var brand by remember { mutableStateOf(vm.customSeedBrand) }; var barcode by remember { mutableStateOf(vm.customSeedBarcode) }; var alias by remember { mutableStateOf("") }; var shorthand by remember { mutableStateOf("") }; var basisAmount by remember { mutableStateOf("100") }; var basisUnit by remember { mutableStateOf("g") }; var calories by remember { mutableStateOf("") }; var protein by remember { mutableStateOf("") }; var carbs by remember { mutableStateOf("") }; var fat by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = vm::closeCustom, confirmButton = { Button({ vm.saveCustom(name, brand, barcode, alias, shorthand, basisAmount, basisUnit, calories, protein, carbs, fat) }) { Text("Save food") } }, dismissButton = { TextButton(vm::closeCustom) { Text("Cancel") } }, title = { Text("Create personal food") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedTextField(name, { name = it }, label = { Text("Name") }); OutlinedTextField(brand, { brand = it }, label = { Text("Brand (optional)") }); OutlinedTextField(alias, { alias = it }, label = { Text("Alias (optional)") }); OutlinedTextField(barcode, { barcode = it }, label = { Text("Barcode (optional)") }); OutlinedTextField(shorthand, { shorthand = it }, label = { Text("Nutrition shorthand (optional)") }, placeholder = { Text("450 kcal, 12g protein, 20g carbs") }); Text("Nutrition basis (per 100 g by default)", style = MaterialTheme.typography.labelMedium); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedTextField(basisAmount, { basisAmount = it }, label = { Text("Amount") }, modifier = Modifier.weight(1f)); OutlinedTextField(basisUnit, { basisUnit = it }, label = { Text("Unit g/ml") }, modifier = Modifier.weight(1f)) }; OutlinedTextField(calories, { calories = it }, label = { Text("Calories *") }); OutlinedTextField(protein, { protein = it }, label = { Text("Protein g (optional)") }); OutlinedTextField(carbs, { carbs = it }, label = { Text("Carbs g (optional)") }); OutlinedTextField(fat, { fat = it }, label = { Text("Fat g (optional)") }) } })
}

@Composable private fun SearchDialog(vm: BreakfastViewModel) {
    AlertDialog(
        onDismissRequest = vm::closeSearch,
        confirmButton = { Button(vm::submitSearch, enabled = !vm.searchBusy) { Text(if (vm.searchBusy) "Searching…" else "Search") } },
        dismissButton = { TextButton(vm::closeSearch) { Text("Cancel") } },
        title = { Text("Open Food Facts") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(vm.searchQuery, vm::onSearch, label = { Text("Search (submitted only)") })
                vm.offResults.forEach { food ->
                    TextButton({ vm.chooseProduct(food) }) { Text("${food.name}${food.brand?.let { " · $it" } ?: ""}") }
                }
                vm.offSearchMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (vm.offSearchMessage != null && vm.searchQuery.isNotBlank() && !vm.searchBusy) {
                    TextButton({ vm.openCustom(vm.searchQuery.trim(), fromResolver = vm.resolutionContinuation) }) {
                        Text(if (vm.offSearchMessage?.startsWith("No usable result") == true) "No usable result — create personal food" else "Add manually")
                    }
                }
            }
        }
    )
}

@Composable private fun UnknownResolver(vm: BreakfastViewModel) {
    val current = vm.unknownQueue.firstOrNull().orEmpty()
    AlertDialog(onDismissRequest = vm::closeResolver, confirmButton = { TextButton(vm::discardUnknowns) { Text("Discard remaining") } }, title = { Text("Match food (${vm.unknownQueue.size} left)") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(current); OutlinedTextField(vm.resolverQuery, vm::onResolverQuery, label = { Text("Search your foods") }); vm.resolverResults.forEach { result -> TextButton({ vm.resolveUnknown(result.food) }) { Text("Use ${result.food.name}") } }; TextButton({ vm.openSearch(vm.currentUnknownName(), fromResolver = true) }) { Text("Search Open Food Facts") }; TextButton({ vm.openCustom(vm.currentUnknownName(), fromResolver = true) }) { Text("Create personal food") } } })
}

@Composable private fun ProductDialog(vm: BreakfastViewModel, food: FoodRecord) {
    val liveCalories = vm.productCalories.toDoubleOrNull()?.let { calories -> vm.productAmount.toDoubleOrNull()?.let { amount -> calories * amount / 100.0 } }
    AlertDialog(onDismissRequest = vm::clearSelectedProduct, confirmButton = { Button(vm::addSelected) { Text("Add to ${vm.slot?.label() ?: "meal"}") } }, dismissButton = { Row { TextButton({ vm.clearSelectedProduct(); vm.openSearch(fromResolver = vm.resolutionContinuation) }) { Text("Find better match") }; TextButton({ vm.clearSelectedProduct(); vm.beginScan() }) { Text("Rescan") }; TextButton(vm::clearSelectedProduct) { Text("Back") } } }, title = { Text("Product details") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { OutlinedTextField(vm.productName, vm::onProductName, label = { Text("Name") }); food.brand?.let { Text(it) }; OutlinedTextField(vm.productCalories, vm::onProductCalories, label = { Text("Calories per 100 ${food.nutrition.unit.label}") }); OutlinedTextField(vm.productProtein, vm::onProductProtein, label = { Text("Protein g (blank = unknown)") }); OutlinedTextField(vm.productCarbs, vm::onProductCarbs, label = { Text("Carbs g (blank = unknown)") }); OutlinedTextField(vm.productFat, vm::onProductFat, label = { Text("Fat g (blank = unknown)") }); OutlinedTextField(vm.productAmount, vm::onProductAmount, label = { Text("Amount") }); OutlinedTextField(vm.productUnit, vm::onProductUnit, label = { Text("Unit: g or ml") }); Text("Preview: ${liveCalories?.pretty() ?: "—"} kcal", style = MaterialTheme.typography.titleMedium) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun BarcodeScanner(vm: BreakfastViewModel) {
    val context = LocalContext.current
    val session = remember { ScannerSession() }
    DisposableEffect(Unit) { onDispose { session.dispose() } }
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasPermission = granted; if (!granted) { vm.endScan(); vm.openCustom() } }
    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.CAMERA) }
    Scaffold(topBar = { TopAppBar(title = { Text("Scan barcode") }) }) { pad ->
        if (hasPermission) AndroidView(factory = { ctx -> PreviewView(ctx).also { view ->
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({ runCatching {
                if (session.disposed.get()) return@runCatching
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                val scanner = BarcodeScanning.getClient(); var delivered = false
                session.provider = provider; session.preview = preview; session.analysis = analysis; session.scanner = scanner
                analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { proxy ->
                    val image = proxy.image
                    if (image == null || delivered || session.disposed.get()) { proxy.close(); return@setAnalyzer }
                    scanner.process(InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees)).addOnSuccessListener { codes ->
                        codes.firstOrNull()?.rawValue?.let { code -> if (!delivered && !session.disposed.get()) { delivered = true; vm.onBarcode(code) } }
                    }.addOnCompleteListener { proxy.close() }
                }
                provider.bindToLifecycle(context as ComponentActivity, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }.onFailure { if (!session.disposed.get()) { session.dispose(); vm.endScan(); vm.openCustom() } } }, ContextCompat.getMainExecutor(ctx))
        } }, Modifier.fillMaxSize().padding(pad))
        else Box(Modifier.fillMaxSize().padding(pad)) { Text("Camera permission is required to scan.") }
    }
}

private class ScannerSession {
    val disposed = AtomicBoolean(false)
    @Volatile var provider: ProcessCameraProvider? = null
    @Volatile var preview: Preview? = null
    @Volatile var analysis: ImageAnalysis? = null
    @Volatile var scanner: BarcodeScanner? = null

    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        analysis?.clearAnalyzer()
        scanner?.close()
        val currentPreview = preview
        val currentAnalysis = analysis
        if (currentPreview != null && currentAnalysis != null) provider?.unbind(currentPreview, currentAnalysis)
    }
}
