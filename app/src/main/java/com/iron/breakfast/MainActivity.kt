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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { IronApp() }
    }
}

enum class EntryEditMode { NONE, QUICK, FULL }

private enum class EntryGesturePhase { UP, HORIZONTAL, VERTICAL, CONSUMED }

class BreakfastViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val engine = BreakfastEngine(BreakfastStore(application))
    private val off = OpenFoodFactsClient()
    private val parser = ShorthandParser()
    private val calculator = NutritionCalculator()
    var input by mutableStateOf(""); private set
    var foods by mutableStateOf<List<FoodRecord>>(emptyList()); private set
    var entries by mutableStateOf<List<MealEntry>>(emptyList()); private set
    var selectedDayKey by mutableStateOf(savedState["selected_day"] ?: dayKeyForEpochMs(System.currentTimeMillis())); private set
    var slot by mutableStateOf(savedState.get<String>("meal_slot")?.let { runCatching { MealSlot.valueOf(it) }.getOrNull() }); private set
    var customOpen by mutableStateOf(false); private set
    var customSeedName by mutableStateOf(""); private set
    var customSeedBrand by mutableStateOf(""); private set
    var customSeedBarcode by mutableStateOf(""); private set
    var customSeedFromOff by mutableStateOf(false); private set
    var customSeedAliases by mutableStateOf(""); private set
    var editingLibraryFoodId by mutableStateOf<String?>(null); private set
    var customSeedCalories by mutableStateOf(""); private set
    var customSeedProtein by mutableStateOf(""); private set
    var customSeedCarbs by mutableStateOf(""); private set
    var customSeedFat by mutableStateOf(""); private set
    var customSeedBasisAmount by mutableStateOf("100"); private set
    var customSeedBasisUnit by mutableStateOf("g"); private set
    var searchOpen by mutableStateOf(false); private set
    var searchQuery by mutableStateOf(""); private set
    var offResults by mutableStateOf<List<FoodRecord>>(emptyList()); private set
    var searchBusy by mutableStateOf(false); private set
    var offSearchMessage by mutableStateOf<String?>(null); private set
    var scannerOpen by mutableStateOf(false); private set
    var selectedProduct by mutableStateOf<FoodRecord?>(null); private set
    var selectedProductFromScan by mutableStateOf(false); private set
    var productAmount by mutableStateOf("100"); private set
    var productUnit by mutableStateOf("g"); private set
    var productName by mutableStateOf(""); private set
    var productCalories by mutableStateOf(""); private set
    var productProtein by mutableStateOf(""); private set
    var productCarbs by mutableStateOf(""); private set
    var productFat by mutableStateOf(""); private set
    var notice by mutableStateOf<String?>(null); private set
    var pendingContribution by mutableStateOf<OffContributionDraft?>(null); private set
    var contributionReviewOpen by mutableStateOf(false); private set
    var contributionAuthOpen by mutableStateOf(false); private set
    var contributionFailure by mutableStateOf<String?>(null); private set
    var contributionUsername by mutableStateOf(""); private set
    var contributionPassword by mutableStateOf(""); private set
    var contributionBusy by mutableStateOf(false); private set
    var undoEntry by mutableStateOf<MealEntry?>(null); private set
    var mealDraftError by mutableStateOf<String?>(null); private set
    var mealPickerOpen by mutableStateOf(false); private set
    var libraryOpen by mutableStateOf(false); private set
    var libraryQuery by mutableStateOf(""); private set
    var libraryOnlyPersonal by mutableStateOf(false); private set
    var libraryIncludeArchived by mutableStateOf(false); private set
    var libraryFoods by mutableStateOf<List<FoodRecord>>(emptyList()); private set
    var selectedLibraryFood by mutableStateOf<FoodRecord?>(null); private set
    var libraryMealPickerFood by mutableStateOf<FoodRecord?>(null); private set
    var selectedEntry by mutableStateOf<MealEntry?>(null); private set
    private var selectedEntryId: String? = savedState["entry_id"]
    private var barcodeRequestToken = 0L
    private var barcodeOriginDay = selectedDayKey
    private var barcodeOriginMeal: MealSlot? = slot
    var ready by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var initializationError by mutableStateOf<String?>(null); private set
    var entryEditMode by mutableStateOf(EntryEditMode.NONE); private set
    var editingEntryId by mutableStateOf<String?>(null); private set
    var editingFoodName by mutableStateOf<String?>(null); private set
    var editInvalid by mutableStateOf(false); private set
    var editInvalidSignal by mutableStateOf(0); private set
    var quickEditHintVisible by mutableStateOf(false); private set
    private var editSession = 0L
    private var quickHintSession = 0L
    var unknownQueue by mutableStateOf<List<String>>(emptyList()); private set
    var unknownQueueTarget by mutableStateOf<MealSlot?>(null); private set
    var resolutionContinuation by mutableStateOf(false); private set
    var resolverOpen by mutableStateOf(false); private set
    var resolverQuery by mutableStateOf(""); private set
    var resolverResults by mutableStateOf<List<RankedFood>>(emptyList()); private set
    var feedbackUntil by mutableStateOf(0L); private set
    var feedbackIds by mutableStateOf<Set<String>>(emptySet()); private set

    init { viewModelScope.launch { runCatching { engine.initialize(); refresh() }.onSuccess { ready = true }.onFailure { initializationError = "Storage could not be opened: ${it.message}" } } }
    private fun refresh() {
        foods = engine.foods()
        entries = engine.persistedMeal().entries
        selectedEntry = selectedEntryId?.let { id -> entries.firstOrNull { it.id == id } }
    }
    fun reloadToday() { if (ready) refresh() }
    private fun selectDay(value: String) { selectedDayKey = value; savedState["selected_day"] = value }
    private fun selectMeal(value: MealSlot?) { slot = value; savedState["meal_slot"] = value?.name }
    fun shiftSelectedDay(delta: Long) { selectDay(runCatching { LocalDate.parse(selectedDayKey).plusDays(delta).toString() }.getOrDefault(selectedDayKey)); barcodeRequestToken++ }
    fun goToday() { selectDay(dayKeyForEpochMs(System.currentTimeMillis())); barcodeRequestToken++ }
    fun openLibrary() { libraryOpen = true; libraryQuery = ""; libraryOnlyPersonal = false; libraryIncludeArchived = false; refreshLibrary() }
    fun closeLibrary() { libraryOpen = false; selectedLibraryFood = null; libraryMealPickerFood = null }
    fun cancelLibraryMealPicker() { libraryMealPickerFood = null }
    fun onLibraryQuery(value: String) { libraryQuery = value; refreshLibrary() }
    fun toggleLibraryPersonal() { libraryOnlyPersonal = !libraryOnlyPersonal; refreshLibrary() }
    fun toggleLibraryArchived() { libraryIncludeArchived = !libraryIncludeArchived; refreshLibrary() }
    fun refreshLibrary() {
        if (!ready) return
        viewModelScope.launch {
            libraryFoods = engine.foodsMatching(libraryQuery, libraryIncludeArchived)
                .filter { !libraryOnlyPersonal || it.origin == com.iron.breakfast.domain.FoodOrigin.CUSTOM }
                .sortedBy { it.name.lowercase(Locale.ROOT) }
        }
    }
    fun openLibraryFood(food: FoodRecord) { selectedLibraryFood = food }
    fun closeLibraryFood() { selectedLibraryFood = null }
    fun logLibraryFood(food: FoodRecord) {
        if (!ready || busy || food.archived) return
        val target = slot
        if (target == null) { libraryMealPickerFood = food; return }
        busy = true
        viewModelScope.launch {
            runCatching { engine.cacheFoodAndLog(food, food.defaultAmount, food.defaultUnit, target, selectedDayKey) }
                .onSuccess { refresh(); notice = "Added to ${target.label()} on $selectedDayKey" }
                .onFailure { notice = it.message ?: "Could not log this food." }
                .also { busy = false }
        }
    }
    fun chooseLibraryMeal(target: MealSlot) {
        val food = libraryMealPickerFood ?: return
        libraryMealPickerFood = null
        if (!ready || busy) return
        busy = true
        viewModelScope.launch {
            runCatching { engine.cacheFoodAndLog(food, food.defaultAmount, food.defaultUnit, target, selectedDayKey) }
                .onSuccess { refresh(); notice = "Added to ${target.label()} on $selectedDayKey" }
                .onFailure { notice = it.message ?: "Could not log this food." }
                .also { busy = false }
        }
    }
    fun archiveLibraryFood(food: FoodRecord) {
        if (!ready || busy || food.origin != com.iron.breakfast.domain.FoodOrigin.CUSTOM) return
        busy = true
        viewModelScope.launch {
            runCatching { engine.archiveFood(food.id) }
                .onSuccess { selectedLibraryFood = null; refresh(); refreshLibrary(); notice = "Food archived; saved diary entries remain readable." }
                .onFailure { notice = it.message ?: "Could not archive this food." }
                .also { busy = false }
        }
    }
    fun openEntryDetail(entry: MealEntry) { selectedEntry = entry; selectedEntryId = entry.id; savedState["entry_id"] = entry.id }
    fun closeEntryDetail() { selectedEntry = null; selectedEntryId = null; savedState["entry_id"] = null }
    fun openContributionForEntry(entry: MealEntry) {
        openContributionForFood(foods.firstOrNull { it.id == entry.foodId })
    }
    fun openContributionForFood(food: FoodRecord?) {
        pendingContribution = food?.let { OffContributionDraft.fromFood(it) }
        if (pendingContribution == null) notice = "This food has no eligible Open Food Facts barcode provenance."
        else contributionReviewOpen = true
    }
    private fun abandonUnknownResolution(clearDraft: Boolean = false) {
        unknownQueue = emptyList()
        unknownQueueTarget = null
        resolutionContinuation = false
        resolverOpen = false
        if (clearDraft) {
            input = ""
            mealDraftError = null
        }
    }
    fun openMeal(s: MealSlot) { abandonUnknownResolution(); selectMeal(s); barcodeRequestToken++; cancelEdit(); mealDraftError = null }
    fun closeMeal() { abandonUnknownResolution(clearDraft = true); selectMeal(null); barcodeRequestToken++; cancelEdit() }
    fun onInput(v: String) { input = v }
    fun beginQuickEdit(entry: MealEntry) {
        if (busy) return
        editSession += 1
        editingEntryId = entry.id
        editingFoodName = entry.foodName
        entryEditMode = EntryEditMode.QUICK
        input = "${entry.enteredAmount.pretty()} ${entry.enteredUnit.displayLabel()}"
        mealDraftError = null
        editInvalid = false
        quickEditHintVisible = true
        val hintSession = ++quickHintSession
        viewModelScope.launch {
            delay(2_500)
            if (quickHintSession == hintSession && entryEditMode == EntryEditMode.QUICK) quickEditHintVisible = false
        }
    }
    /** Kept as the entry-point name for callers that predate the two edit modes. */
    fun beginEdit(entry: MealEntry) = beginQuickEdit(entry)
    fun beginFullEdit(entry: MealEntry) {
        if (busy) return
        editSession += 1
        editingEntryId = entry.id
        editingFoodName = null
        entryEditMode = EntryEditMode.FULL
        input = "${entry.enteredAmount.pretty()} ${entry.enteredUnit.displayLabel()} ${entry.foodName}"
        mealDraftError = null
        editInvalid = false
        quickHintSession += 1
        quickEditHintVisible = false
    }
    fun cancelEdit() {
        editSession += 1
        entryEditMode = EntryEditMode.NONE
        editingEntryId = null
        editingFoodName = null
        input = ""
        editInvalid = false
        quickHintSession += 1
        quickEditHintVisible = false
        mealDraftError = null
    }
    private fun markEditInvalid(message: String) {
        mealDraftError = message
        editInvalid = true
        val signal = editInvalidSignal + 1
        editInvalidSignal = signal
        viewModelScope.launch {
            delay(1_400)
            if (editInvalidSignal == signal) editInvalid = false
        }
    }
    fun submit() {
        if (!ready || busy) return
        if (entryEditMode != EntryEditMode.NONE) {
            if (input.isBlank()) {
                cancelEdit()
                return
            }
            val target = slot ?: return
            submitToMeal(target)
            return
        }
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
        val mode = entryEditMode
        if (editing != null && mode != EntryEditMode.NONE) {
            val session = editSession
            busy = true
            viewModelScope.launch {
                runCatching {
                    if (mode == EntryEditMode.QUICK) engine.replaceEntryAmountUnit(editing, input)
                    else engine.replaceEntryFromText(editing, input)
                }.onSuccess { result ->
                    if (entryEditMode != mode || editingEntryId != editing || editSession != session) return@onSuccess
                    when (result) {
                        is ReplacementResult.Success -> { refresh(); cancelEdit() }
                        is ReplacementResult.Failure -> markEditInvalid(result.reason)
                    }
                }.onFailure {
                    if (entryEditMode == mode && editingEntryId == editing && editSession == session) {
                        markEditInvalid(it.message ?: "Could not edit this entry.")
                    }
                }
                    .also { busy = false }
            }
            return
        }
        val cleaned = removeMealMarker(input, target)
        if (cleaned.isBlank()) { mealDraftError = "Add at least one food."; return }
        busy = true
        viewModelScope.launch {
            runCatching { engine.save(cleaned, mealSlot = target, dayKey = selectedDayKey) }.onSuccess { saved ->
                refresh(); if (input == captured) input = saved.unresolvedSegments.joinToString(", "); unknownQueue = saved.unresolvedSegments; unknownQueueTarget = target.takeIf { saved.unresolvedSegments.isNotEmpty() }; resolutionContinuation = saved.unresolvedSegments.isNotEmpty(); resolverOpen = saved.unresolvedSegments.isNotEmpty(); mealDraftError = if (saved.unresolvedSegments.isNotEmpty()) "Some foods need a match." else null
                feedbackIds = saved.entries.mapTo(mutableSetOf()) { it.id }; feedbackUntil = System.currentTimeMillis() + 30_000L
                if (slot == null) selectMeal(target)
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
    fun openCustom(name: String = "", brand: String = "", barcode: String = "", fromResolver: Boolean = false, fromOff: Boolean = false) {
        if (!fromResolver && unknownQueue.isNotEmpty()) abandonUnknownResolution()
        resolutionContinuation = fromResolver
        resetCustomSeeds()
        customSeedName = name
        customSeedBrand = brand
        customSeedBarcode = barcode
        customSeedFromOff = fromOff
        customOpen = true
        searchOpen = false
        resolverOpen = false
    }
    private fun resetCustomSeeds() {
        customSeedName = ""; customSeedBrand = ""; customSeedBarcode = ""; customSeedAliases = ""
        customSeedFromOff = false; customSeedCalories = ""; customSeedProtein = ""; customSeedCarbs = ""; customSeedFat = ""
        customSeedBasisAmount = "100"; customSeedBasisUnit = "g"
    }
    fun closeCustom() { customOpen = false; editingLibraryFoodId = null; resetCustomSeeds(); if (resolutionContinuation && unknownQueue.isNotEmpty()) resolverOpen = true else resolutionContinuation = false }
    fun openLibraryEdit(food: FoodRecord) {
        if (food.origin != com.iron.breakfast.domain.FoodOrigin.CUSTOM) return
        editingLibraryFoodId = food.id
        customSeedName = food.name; customSeedBrand = food.brand.orEmpty(); customSeedBarcode = food.barcode.orEmpty(); customSeedAliases = food.aliases.joinToString(", ")
        customSeedCalories = food.nutrition.values.calories.toString(); customSeedProtein = food.nutrition.values.protein?.toString().orEmpty()
        customSeedCarbs = food.nutrition.values.carbohydrates?.toString().orEmpty(); customSeedFat = food.nutrition.values.fat?.toString().orEmpty()
        customSeedBasisAmount = food.nutrition.amount.toString(); customSeedBasisUnit = food.nutrition.unit.label
        customOpen = true; selectedLibraryFood = null
    }
    fun closeResolver() { abandonUnknownResolution() }
    fun dismissNotice() { notice = null }
    fun reviewContribution() { if (pendingContribution != null) contributionReviewOpen = true }
    fun dismissContributionReview() { contributionReviewOpen = false }
    fun confirmContribution() {
        if (pendingContribution != null) {
            contributionReviewOpen = false
            contributionAuthOpen = true
        }
    }
    fun dismissContributionAuth() { contributionAuthOpen = false; contributionPassword = "" }
    fun dismissContributionFailure() { contributionFailure = null; pendingContribution = null }
    fun retryContribution() { contributionFailure = null; contributionAuthOpen = true }
    fun onContributionUsername(v: String) { contributionUsername = v }
    fun onContributionPassword(v: String) { contributionPassword = v }
    fun submitContribution() {
        val draft = pendingContribution ?: return
        if (contributionBusy) return
        if (contributionUsername.isBlank() || contributionPassword.isBlank()) {
            contributionFailure = "Enter your Open Food Facts username and password to submit."
            contributionAuthOpen = false
            return
        }
        val username = contributionUsername.trim()
        val password = contributionPassword
        contributionPassword = ""
        contributionAuthOpen = false
        contributionBusy = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = off.contribute(draft, username, password)
            withContext(Dispatchers.Main) {
                when (result) {
                    OffContributionResult.Success -> {
                        pendingContribution = null
                        contributionFailure = null
                        notice = "Contribution sent to Open Food Facts."
                    }
                    OffContributionResult.AuthFailed -> contributionFailure = "Open Food Facts could not authenticate that account. Check the username and password, then retry."
                    is OffContributionResult.Ineligible -> contributionFailure = result.message
                    is OffContributionResult.Rejected -> contributionFailure = result.message
                    OffContributionResult.Unavailable -> contributionFailure = "Open Food Facts was unavailable. Retry when you have a connection."
                    is OffContributionResult.Error -> contributionFailure = result.message
                }
                contributionBusy = false
            }
        }
    }
    fun clearSelectedProduct() { selectedProduct = null; selectedProductFromScan = false; if (resolutionContinuation && unknownQueue.isNotEmpty()) resolverOpen = true else resolutionContinuation = false }
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
    fun resolveUnknown(food: FoodRecord) { val raw = unknownQueue.firstOrNull() ?: return; val target = slot ?: return; if (unknownQueueTarget != target || !resolutionContinuation) return; val parsed = parser.parse(raw); val amount = parsed.amount ?: food.defaultAmount; val unit = parsed.unit ?: food.defaultUnit; if (!ready || busy) return; busy = true; viewModelScope.launch { runCatching { engine.cacheFoodAndLog(food, amount, unit, target, selectedDayKey) }.onSuccess { advanceUnknownQueue(); refresh() }.onFailure { notice = it.message ?: "Could not resolve this food." }.also { busy = false } } }
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
    fun chooseProduct(food: FoodRecord, fromScan: Boolean = false) {
        selectedProduct = food
        selectedProductFromScan = fromScan
        searchOpen = false
        val prefill = unknownQueue.firstOrNull()
            ?.takeIf { resolutionContinuation }
            ?.let { productPrefill(it, food) }
            ?: ProductPrefill(100.0, food.nutrition.unit)
        productAmount = prefill.amount.toString()
        productUnit = prefill.unit.displayLabel()
        productName = food.name
        productCalories = food.nutrition.values.calories.toString()
        productProtein = food.nutrition.values.protein?.toString().orEmpty()
        productCarbs = food.nutrition.values.carbohydrates?.toString().orEmpty()
        productFat = food.nutrition.values.fat?.toString().orEmpty()
    }
    fun productCalculation(food: FoodRecord): Calculation? = productDraft(food)?.calculation
    fun addSelected() {
        val food = selectedProduct ?: return
        if (!ready || busy) return
        val target = slot ?: run { notice = "Open a meal before adding this food."; return }
        val draft = productDraft(food) ?: run {
            notice = "Enter a positive amount, a supported unit, and valid calories."
            return
        }
        val resolvesQueue = resolutionContinuation && unknownQueueTarget == target
        val fromScan = selectedProductFromScan
        busy = true
        viewModelScope.launch {
            runCatching { engine.cacheFoodAndLog(draft.food, draft.amount, draft.unit, target, selectedDayKey) }
                .onSuccess {
                    selectedProduct = null
                    pendingContribution = contributionDraftAfterLocalSave(draft.food, fromScan)
                    selectedProductFromScan = false
                    if (resolvesQueue) advanceUnknownQueue() else resolutionContinuation = false
                    refresh()
                    notice = "Added to ${target.label()}"
                }
                .onFailure { notice = it.message ?: "This amount cannot be logged." }
                .also { busy = false }
        }
    }

    private fun productDraft(food: FoodRecord): ProductDraft? {
        val amount = productAmount.toDoubleOrNull()
        val unit = parseProductUnit(productUnit)
        val kcal = productCalories.toDoubleOrNull()
        if (amount == null || !amount.isFinite() || amount <= 0.0 || unit == null ||
            kcal == null || !kcal.isFinite() || kcal < 0.0
        ) return null
        val draftFood = food.copy(
            name = productName.trim().ifBlank { food.name },
            nutrition = NutritionBasis(
                food.nutrition.amount,
                food.nutrition.unit,
                NutritionValues(
                    kcal,
                    productProtein.toDoubleOrNull(),
                    productCarbs.toDoubleOrNull(),
                    productFat.toDoubleOrNull()
                )
            )
        )
        val calculation = calculator.calculate(draftFood, amount, unit)
        return ProductDraft(draftFood, amount, unit, calculation).takeIf { calculation.supported }
    }
    fun beginScan() { barcodeRequestToken++; barcodeOriginDay = selectedDayKey; barcodeOriginMeal = slot; scannerOpen = true }
    fun endScan() { scannerOpen = false }
    private fun barcodeRequestStillTargets(token: Long, day: String, meal: MealSlot?): Boolean = token == barcodeRequestToken && day == selectedDayKey && meal == slot
    fun onBarcode(code: String) {
        scannerOpen = false
        val token = barcodeRequestToken
        val originDay = barcodeOriginDay
        val originMeal = barcodeOriginMeal
        viewModelScope.launch(Dispatchers.IO) {
            val local = engine.foodByBarcode(code)
            if (local != null) {
                withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) chooseProduct(local, fromScan = true) }
                return@launch
            }
            when (val result = off.barcode(code)) {
                is OffResult.Found -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) chooseProduct(result.food, fromScan = true) }
                is OffResult.Ineligible -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { notice = "Nutrition is incomplete. Review it as a personal food."; openCustom(result.name.orEmpty(), result.brand.orEmpty(), result.barcode, fromOff = true) } }
                OffResult.Missing -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { notice = "Product not found. Add it manually with barcode $code"; openCustom(barcode = code) } }
                OffResult.Unavailable -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { notice = "No connection. Add the product manually."; openCustom(barcode = code) } }
                is OffResult.Error -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) notice = result.message }
            }
        }
    }
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
        val contribution = if (customSeedFromOff && barcode.isNotBlank()) OffContributionDraft(
            barcode = barcode.trim(), name = name.trim(), brand = brand.trim().ifBlank { null },
            basisAmount = basisAmount, basisUnit = basisUnit, nutrition = nutrition
        ) else null
        val offSource = customSeedFromOff.takeIf { it }?.let { "Open Food Facts" }
        val editingId = editingLibraryFoodId
        busy = true; viewModelScope.launch { runCatching {
            if (editingId != null) {
                val original = foods.firstOrNull { it.id == editingId } ?: error("That personal food is no longer available.")
                engine.updateFood(editedCustomFoodRecord(original, name, brand, barcode, alias, nutrition, basisAmount, basisUnit))
            } else {
                val target = slot
                if (target != null) engine.saveCustomAndLog(name, brand, barcode, alias, nutrition, basisAmount, basisUnit, target, basisAmount, basisUnit, dayKey = selectedDayKey, sourceName = offSource, sourceDescription = "Saved from an incomplete Open Food Facts barcode result")
                else engine.saveCustomFood(name, brand, barcode, alias, nutrition, basisAmount, basisUnit, sourceName = offSource, sourceDescription = "Saved from an incomplete Open Food Facts barcode result")
            }
        }.onSuccess {
            refresh()
            customOpen = false
            editingLibraryFoodId = null
            customSeedFromOff = false
            pendingContribution = contribution
            if (resolvesQueue && unknownQueue.isNotEmpty()) advanceUnknownQueue() else resolutionContinuation = false
            notice = if (editingId != null) "Personal food updated for future logs." else if (slot != null) "Saved to ${slot!!.label()} and your food library." else "Saved to your food library."
        }.onFailure { notice = it.message ?: "Could not save this food." }.also { busy = false } }
    }
}

internal data class ProductPrefill(val amount: Double, val unit: MeasureUnit)

internal fun editedCustomFoodRecord(
    original: FoodRecord,
    name: String,
    brand: String,
    barcode: String,
    aliasText: String,
    nutrition: NutritionValues,
    basisAmount: Double,
    basisUnit: MeasureUnit
): FoodRecord {
    val aliases = aliasText.trim().takeIf { it.isNotBlank() }
        ?.split(',')?.map(String::trim)?.filter(String::isNotBlank)
        ?: original.aliases
    return original.copy(
        name = name.trim(), brand = brand.trim().ifBlank { null }, barcode = barcode.trim().ifBlank { null },
        aliases = aliases,
        nutrition = NutritionBasis(basisAmount, basisUnit, nutrition),
        conversions = listOf(com.iron.breakfast.domain.ServingConversion(basisUnit, basisAmount, "custom basis")),
        defaultAmount = basisAmount, defaultUnit = basisUnit
    )
}

internal fun productNutritionBasisLabel(food: FoodRecord): String =
    "Calories per ${food.nutrition.amount.pretty()} ${food.nutrition.unit.label}"

internal fun productPrefill(raw: String, food: FoodRecord): ProductPrefill {
    val parsed = ShorthandParser().parse(raw)
    return ProductPrefill(
        amount = parsed.amount ?: 100.0,
        unit = parsed.unit ?: food.nutrition.unit
    )
}

internal fun parseProductUnit(value: String): MeasureUnit? = value.trim().toMeasureUnit()

internal fun contributionDraftAfterLocalSave(food: FoodRecord, fromScan: Boolean): OffContributionDraft? =
    if (fromScan) OffContributionDraft.fromFood(food) else null

private data class ProductDraft(
    val food: FoodRecord,
    val amount: Double,
    val unit: MeasureUnit,
    val calculation: Calculation
)

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
        topBar = { TopAppBar(title = { Text(vm.slot?.let { "${it.label()} · ${vm.selectedDayKey}" } ?: "Breakfast · ${vm.selectedDayKey}") }) },
        bottomBar = { vm.slot?.let { MealComposer(vm, it) } }
    ) { pad ->
        when {
            vm.libraryOpen -> FoodLibrary(vm, Modifier.padding(pad))
            vm.slot == null -> Home(vm, Modifier.padding(pad))
            else -> MealDetail(vm, Modifier.padding(pad))
        }
    }
    if (vm.selectedEntry != null) EntryDetailDialog(vm, vm.selectedEntry!!)
    if (vm.selectedLibraryFood != null) LibraryFoodDetailDialog(vm, vm.selectedLibraryFood!!)
    if (vm.selectedEntry != null) {
        BackHandler { vm.closeEntryDetail() }
    } else if (vm.libraryOpen && vm.selectedLibraryFood == null && !vm.customOpen && !vm.searchOpen && !vm.resolverOpen && vm.selectedProduct == null) {
        BackHandler { vm.closeLibrary() }
    } else if (vm.slot != null && !vm.customOpen && !vm.searchOpen && !vm.resolverOpen && vm.selectedProduct == null && vm.selectedEntry == null) {
        BackHandler { vm.closeMeal() }
    }
    if (vm.mealPickerOpen) MealPickerDialog(vm)
    if (vm.libraryMealPickerFood != null) LibraryMealPickerDialog(vm, vm.libraryMealPickerFood!!)
    if (vm.customOpen) CustomDialog(vm)
    if (vm.searchOpen) SearchDialog(vm)
    if (vm.resolverOpen) UnknownResolver(vm)
    vm.selectedProduct?.let { ProductDialog(vm, it) }
    if (vm.contributionReviewOpen) vm.pendingContribution?.let { ContributionReviewDialog(vm, it) }
    if (vm.contributionAuthOpen) ContributionAuthDialog(vm)
    vm.contributionFailure?.let { ContributionFailureDialog(vm, it) }
    vm.notice?.let { message -> AlertDialog(onDismissRequest = vm::dismissNotice, confirmButton = { TextButton(vm::dismissNotice) { Text("OK") } }, text = { Text(message) }) }
    vm.initializationError?.let { message -> AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("Storage unavailable") }, text = { Text(message) }) }
}

@Composable private fun Home(vm: BreakfastViewModel, modifier: Modifier) {
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
    LazyColumn(modifier.fillMaxSize().then(dateGesture).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Spacer(Modifier.height(8.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(vm.selectedDayKey, style = MaterialTheme.typography.headlineMedium); TextButton(vm::goToday) { Text("Today") } }; Text("Swipe left/right to change diary date. Logging targets ${vm.selectedDayKey}.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
        MealSlot.entries.forEach { s -> item { MealCard(s, selected.filter { it.mealSlot == s }, vm) } }
        item { OutlinedButton(vm::openLibrary, Modifier.fillMaxWidth()) { Text("Food Library") } }
        item { OutlinedButton({ vm.openCustom() }, Modifier.fillMaxWidth()) { Text("Create a personal food") } }
        vm.pendingContribution?.let { item { ContributionAction(vm) } }
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

@Composable private fun LibraryMealPickerDialog(vm: BreakfastViewModel, food: FoodRecord) {
    AlertDialog(
        onDismissRequest = vm::cancelLibraryMealPicker,
        title = { Text("Add ${food.name} to which meal?") },
        text = { Column { Text("${vm.selectedDayKey} · choose a meal"); MealSlot.entries.forEach { target -> TextButton({ vm.chooseLibraryMeal(target) }, Modifier.fillMaxWidth()) { Text(target.label()) } } } },
        confirmButton = { TextButton(vm::cancelLibraryMealPicker) { Text("Cancel") } }
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable private fun FoodLibrary(vm: BreakfastViewModel, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("Food Library", style = MaterialTheme.typography.headlineMedium)
            Text("Local foods stay available offline. Open Food Facts search is an explicit remote fallback.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(vm.libraryQuery, vm::onLibraryQuery, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search local foods") })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(vm::toggleLibraryPersonal) { Text(if (vm.libraryOnlyPersonal) "All foods" else "My Foods") }
                OutlinedButton(vm::toggleLibraryArchived) { Text(if (vm.libraryIncludeArchived) "Hide archived" else "Archived") }
            }
            Button({ vm.openCustom() }, Modifier.fillMaxWidth()) { Text("Create personal food") }
        }
        if (vm.libraryFoods.isEmpty()) item { Text("No local foods match this search.") }
        items(vm.libraryFoods, key = { it.id }) { food ->
            Card(Modifier.fillMaxWidth().clickable { vm.openLibraryFood(food) }) {
                Column(Modifier.padding(12.dp)) {
                    Text(food.name, style = MaterialTheme.typography.titleMedium)
                    Text(listOfNotNull(food.brand, if (food.origin == com.iron.breakfast.domain.FoodOrigin.CUSTOM) "Personal food" else food.sourceName ?: "Bundled food").joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${food.nutrition.values.calories.pretty()} kcal per ${food.nutrition.amount.pretty()} ${food.nutrition.unit.label}")
                }
            }
        }
    }
}

@Composable private fun LibraryFoodDetailDialog(vm: BreakfastViewModel, food: FoodRecord) {
    AlertDialog(
        onDismissRequest = vm::closeLibraryFood,
        title = { Text(food.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(listOfNotNull(food.brand, food.sourceName ?: if (food.origin == com.iron.breakfast.domain.FoodOrigin.CUSTOM) "Personal food" else "Bundled food").joinToString(" · "))
                food.barcode?.let { Text("Barcode: $it") }
                Text("Nutrition basis: ${food.nutrition.amount.pretty()} ${food.nutrition.unit.label}")
                Text("${food.nutrition.values.calories.pretty()} kcal  ·  P ${food.nutrition.values.protein.macro()}  C ${food.nutrition.values.carbohydrates.macro()}  F ${food.nutrition.values.fat.macro()}")
                food.conversions.forEach { conversion -> Text("${conversion.unit.label}: ${conversion.provenance}${if (conversion.approximate) " (approximate)" else ""}", style = MaterialTheme.typography.bodySmall) }
                if (food.archived) Text("Archived personal food; historical entries are preserved.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (OffContributionDraft.fromFood(food) != null) TextButton({ vm.closeLibraryFood(); vm.openContributionForFood(food) }, Modifier.fillMaxWidth()) { Text("Contribute to Open Food Facts") }
                if (food.origin == com.iron.breakfast.domain.FoodOrigin.CUSTOM) TextButton({ vm.openLibraryEdit(food) }, Modifier.fillMaxWidth()) { Text("Edit personal food") }
                if (food.origin == com.iron.breakfast.domain.FoodOrigin.CUSTOM && !food.archived) TextButton({ vm.archiveLibraryFood(food) }, Modifier.fillMaxWidth()) { Text("Archive personal food") }
            }
        },
        confirmButton = { Button({ vm.logLibraryFood(food); vm.closeLibraryFood() }, enabled = !food.archived) { Text("Add to ${vm.slot?.label() ?: "a meal"} on ${vm.selectedDayKey}") } },
        dismissButton = { TextButton(vm::closeLibraryFood) { Text("Close") } }
    )
}

@Composable private fun EntryDetailDialog(vm: BreakfastViewModel, entry: MealEntry) {
    val food = vm.foods.firstOrNull { it.id == entry.foodId }
    AlertDialog(
        onDismissRequest = vm::closeEntryDetail,
        title = { Text(entry.foodName) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                food?.let { Text(listOfNotNull(it.brand, it.sourceName ?: it.category, it.barcode?.let { code -> "Barcode $code" }).joinToString(" · ")) }
                Text("Logged nutrition snapshot: ${entry.nutritionSnapshot.calories.pretty()} kcal  ·  P ${entry.nutritionSnapshot.protein.macro()}  C ${entry.nutritionSnapshot.carbohydrates.macro()}  F ${entry.nutritionSnapshot.fat.macro()}")
                food?.let { Text("Current catalogue metadata (may have changed): ${it.nutrition.amount.pretty()} ${it.nutrition.unit.label} basis", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("Logged amount: ${entry.enteredAmount.pretty()} ${entry.enteredUnit.displayLabel()}")
                Text("${entry.dayKey} · ${entry.mealSlot.label()}")
                if (entry.approximateConversion) Text("Approximate conversion: ${entry.conversionProvenance}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Single tap opens details · double-tap edits amount · hold edits food", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton({ vm.closeEntryDetail(); vm.beginFullEdit(entry) }, Modifier.fillMaxWidth()) { Text("Replace food") }
                if (food != null && OffContributionDraft.fromFood(food) != null) TextButton({ vm.openContributionForEntry(entry) }, Modifier.fillMaxWidth()) { Text("Contribute to Open Food Facts") }
                TextButton({ vm.closeEntryDetail(); vm.delete(entry) }, Modifier.fillMaxWidth()) { Text("Delete entry") }
            }
        },
        confirmButton = { Button({ vm.closeEntryDetail(); vm.beginQuickEdit(entry) }) { Text("Edit amount") } },
        dismissButton = { TextButton(vm::closeEntryDetail) { Text("Close") } }
    )
}

@Composable private fun MealCard(slot: MealSlot, entries: List<MealEntry>, vm: BreakfastViewModel) {
    val total = entries.fold(NutritionValues.ZERO) { a, e -> a + e.nutritionSnapshot }
    Card(Modifier.fillMaxWidth().clickable { vm.openMeal(slot) }) { Column(Modifier.padding(14.dp)) {
        Text(slot.label(), style = MaterialTheme.typography.titleLarge); Text(vm.selectedDayKey, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${total.calories.pretty()} kcal  •  P ${total.protein.macro()}  C ${total.carbohydrates.macro()}  F ${total.fat.macro()}")
        entries.forEach { Text("${it.foodName}  ·  ${it.enteredAmount.pretty()} ${it.enteredUnit.displayLabel()}  ·  ${it.nutritionSnapshot.calories.pretty()} kcal") }
    } }
}

@Composable private fun MealDetail(vm: BreakfastViewModel, modifier: Modifier) {
    val target = vm.slot ?: return; val entries = vm.entries.filter { it.dayKey == vm.selectedDayKey && it.mealSlot == target }; val total = entries.fold(NutritionValues.ZERO) { a, e -> a + e.nutritionSnapshot }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Spacer(Modifier.height(8.dp)); Text(target.label(), style = MaterialTheme.typography.headlineMedium); Text(vm.selectedDayKey, style = MaterialTheme.typography.labelMedium); Text("${total.calories.pretty()} kcal  •  P ${total.protein.macro()}  C ${total.carbohydrates.macro()}  F ${total.fat.macro()}") }
        vm.pendingContribution?.let { item { ContributionAction(vm) } }
        items(entries, key = { it.id }) { entry -> EntryRow(entry, vm) }
        item { vm.undoEntry?.let { TextButton({ vm.undo() }) { Text("Undo delete ${it.foodName}") } }; TextButton({ vm.closeMeal() }) { Text("Back to diary") }; Spacer(Modifier.height(16.dp)) }
    }
}

@Composable private fun MealComposer(vm: BreakfastViewModel, target: MealSlot) {
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
                TextButton(vm::beginScan) { Text("Scan") }
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
@Composable private fun EntryRow(entry: MealEntry, vm: BreakfastViewModel) {
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

@Composable private fun ContributionAction(vm: BreakfastViewModel) {
    OutlinedButton(vm::reviewContribution, Modifier.fillMaxWidth()) {
        Text("Contribute to Open Food Facts")
    }
}

@Composable private fun ContributionReviewDialog(vm: BreakfastViewModel, draft: OffContributionDraft) {
    AlertDialog(
        onDismissRequest = vm::dismissContributionReview,
        title = { Text("Contribute correction?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("These public product fields will be sent to Open Food Facts:")
                draft.publicFields().forEach { Text(it) }
                Text("Your meal amount, aliases, preferences, and serving estimates are not included.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(vm::confirmContribution) { Text("Continue to sign in") } },
        dismissButton = { TextButton(vm::dismissContributionReview) { Text("Decline") } }
    )
}

@Composable private fun ContributionAuthDialog(vm: BreakfastViewModel) {
    AlertDialog(
        onDismissRequest = vm::dismissContributionAuth,
        title = { Text("Open Food Facts sign in") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Use your own Open Food Facts account. Breakfast does not save your password.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(vm.contributionUsername, vm::onContributionUsername, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(
                    vm.contributionPassword,
                    vm::onContributionPassword,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.submitContribution() })
                )
            }
        },
        confirmButton = { Button(vm::submitContribution, enabled = !vm.contributionBusy) { Text(if (vm.contributionBusy) "Submitting…" else "Submit contribution") } },
        dismissButton = { TextButton(vm::dismissContributionAuth) { Text("Cancel") } }
    )
}

@Composable private fun ContributionFailureDialog(vm: BreakfastViewModel, message: String) {
    AlertDialog(
        onDismissRequest = vm::dismissContributionFailure,
        title = { Text("Contribution not sent") },
        text = { Text(message) },
        confirmButton = { TextButton(vm::retryContribution) { Text("Retry") } },
        dismissButton = { TextButton(vm::dismissContributionFailure) { Text("Dismiss") } }
    )
}

@Composable private fun CustomDialog(vm: BreakfastViewModel) {
    var name by remember(vm.editingLibraryFoodId, vm.customSeedName) { mutableStateOf(vm.customSeedName) }; var brand by remember(vm.editingLibraryFoodId, vm.customSeedBrand) { mutableStateOf(vm.customSeedBrand) }; var barcode by remember(vm.editingLibraryFoodId, vm.customSeedBarcode) { mutableStateOf(vm.customSeedBarcode) }; var alias by remember(vm.editingLibraryFoodId, vm.customSeedAliases) { mutableStateOf(vm.customSeedAliases) }; var shorthand by remember(vm.editingLibraryFoodId) { mutableStateOf("") }; var basisAmount by remember(vm.editingLibraryFoodId, vm.customSeedBasisAmount) { mutableStateOf(vm.customSeedBasisAmount) }; var basisUnit by remember(vm.editingLibraryFoodId, vm.customSeedBasisUnit) { mutableStateOf(vm.customSeedBasisUnit) }; var calories by remember(vm.editingLibraryFoodId, vm.customSeedCalories) { mutableStateOf(vm.customSeedCalories) }; var protein by remember(vm.editingLibraryFoodId, vm.customSeedProtein) { mutableStateOf(vm.customSeedProtein) }; var carbs by remember(vm.editingLibraryFoodId, vm.customSeedCarbs) { mutableStateOf(vm.customSeedCarbs) }; var fat by remember(vm.editingLibraryFoodId, vm.customSeedFat) { mutableStateOf(vm.customSeedFat) }
    AlertDialog(onDismissRequest = vm::closeCustom, confirmButton = { Button({ vm.saveCustom(name, brand, barcode, alias, shorthand, basisAmount, basisUnit, calories, protein, carbs, fat) }) { Text(if (vm.editingLibraryFoodId != null) "Save changes" else "Save food") } }, dismissButton = { TextButton(vm::closeCustom) { Text("Cancel") } }, title = { Text(if (vm.editingLibraryFoodId != null) "Edit personal food" else "Create personal food") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedTextField(name, { name = it }, label = { Text("Name") }); OutlinedTextField(brand, { brand = it }, label = { Text("Brand (optional)") }); OutlinedTextField(alias, { alias = it }, label = { Text("Alias (optional)") }); OutlinedTextField(barcode, { barcode = it }, label = { Text("Barcode (optional)") }); OutlinedTextField(shorthand, { shorthand = it }, label = { Text("Nutrition shorthand (optional)") }, placeholder = { Text("450 kcal, 12g protein, 20g carbs") }); Text("Nutrition basis", style = MaterialTheme.typography.labelMedium); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedTextField(basisAmount, { basisAmount = it }, label = { Text("Amount") }, modifier = Modifier.weight(1f)); OutlinedTextField(basisUnit, { basisUnit = it }, label = { Text("Unit g/ml") }, modifier = Modifier.weight(1f)) }; OutlinedTextField(calories, { calories = it }, label = { Text("Calories *") }); OutlinedTextField(protein, { protein = it }, label = { Text("Protein g (optional)") }); OutlinedTextField(carbs, { carbs = it }, label = { Text("Carbs g (optional)") }); OutlinedTextField(fat, { fat = it }, label = { Text("Fat g (optional)") }) } })
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
    val calculation = vm.productCalculation(food)
    AlertDialog(onDismissRequest = vm::clearSelectedProduct, confirmButton = { Button(vm::addSelected, enabled = !food.archived) { Text("Add to ${vm.slot?.label() ?: "meal"}") } }, dismissButton = { Row { TextButton({ vm.clearSelectedProduct(); vm.openSearch(fromResolver = vm.resolutionContinuation) }) { Text("Find better match") }; TextButton({ vm.clearSelectedProduct(); vm.beginScan() }) { Text("Rescan") }; TextButton(vm::clearSelectedProduct) { Text("Back") } } }, title = { Text("Product details") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { OutlinedTextField(vm.productName, vm::onProductName, label = { Text("Name") }); food.brand?.let { Text(it) }; OutlinedTextField(vm.productCalories, vm::onProductCalories, label = { Text(productNutritionBasisLabel(food)) }); OutlinedTextField(vm.productProtein, vm::onProductProtein, label = { Text("Protein g (blank = unknown)") }); OutlinedTextField(vm.productCarbs, vm::onProductCarbs, label = { Text("Carbs g (blank = unknown)") }); OutlinedTextField(vm.productFat, vm::onProductFat, label = { Text("Fat g (blank = unknown)") }); OutlinedTextField(vm.productAmount, vm::onProductAmount, label = { Text("Amount") }); OutlinedTextField(vm.productUnit, vm::onProductUnit, label = { Text("Unit: g, ml, tbsp, tsp, or piece") }); Text("Preview: ${calculation?.nutrition?.calories?.pretty() ?: "—"} kcal", style = MaterialTheme.typography.titleMedium); calculation?.let { Text("${if (it.approximate) "Approximate conversion: " else "Conversion: "}${it.provenance}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } })
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
