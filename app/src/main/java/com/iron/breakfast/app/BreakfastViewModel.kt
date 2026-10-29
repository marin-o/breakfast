package com.iron.breakfast.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import java.time.ZoneId
import com.iron.breakfast.settings.AppSettings
import com.iron.breakfast.domain.BreakfastEngine
import com.iron.breakfast.data.BreakfastStore
import com.iron.breakfast.data.OpenFoodFactsClient
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.scan.BarcodeLookup
import com.iron.breakfast.scan.ProductStore
import com.iron.breakfast.food.FoodFormStore
import com.iron.breakfast.food.NewFood
import com.iron.breakfast.resolve.ResolveStore
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.entry.EntryEditStore
import com.iron.breakfast.log.LogStore
import com.iron.breakfast.library.LibraryStore
import com.iron.breakfast.settings.SettingsState
import com.iron.breakfast.today.MatchStore
import com.iron.breakfast.today.UndoStore
import com.iron.breakfast.transfer.ContentDiaryFiles
import com.iron.breakfast.transfer.DiaryData
import com.iron.breakfast.transfer.TransferLog
import com.iron.breakfast.data.DiaryRows

/**
 * The Android host of [AppOwners]: it adapts `BreakfastEngine`, `OpenFoodFactsClient` and the content resolver as the
 * owners' [AppStores], and carries the lifecycle (stop, start, the 60-second loop). The owners and their wiring are
 * [AppOwners]'s, shared with the JVM tests; the screens reach them through the fields below.
 */
class BreakfastViewModel(application: Application, savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val clock: () -> Long = System::currentTimeMillis
    private val settingsState = SettingsState(AppSettings(application), clock)
    private val engine = BreakfastEngine(
        BreakfastStore(application),
        countTypingSetting = { settingsState.countTyping },
        learnFromPicksSetting = { settingsState.learnFromPicks },
    )
    private val off = OpenFoodFactsClient()
    private val app = AppOwners(
        stores = AppStores(
            diary = object : DiarySource {
                override suspend fun open() = engine.initialize()
                override fun foods() = engine.foods()
                override fun entries() = engine.persistedMeal().entries
                override suspend fun reloadIfStale() = engine.reloadIfStale()
            },
            undo = object : UndoStore {
                override suspend fun deleteEntry(id: String) = engine.deleteEntry(id)
                override suspend fun restoreEntry(entry: MealEntry) { engine.restoreEntry(entry) }
                override suspend fun deleteEntries(entries: List<MealEntry>) { engine.deleteEntries(entries) }
            },
            match = object : MatchStore {
                override fun alternativesFor(entry: MealEntry) = engine.alternativesFor(entry)
                override suspend fun replaceEntry(entryId: String, foodId: String) = engine.replaceEntry(entryId, foodId)
                override suspend fun reinforce(foodId: String) = engine.reinforce(foodId)
            },
            resolve = object : ResolveStore {
                override fun typed(raw: String) = engine.planner().typed(raw)
                override fun search(query: String) = engine.search(query)
                override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String) {
                    engine.cacheFoodAndLog(food, amount, unit, meal, dayKey)
                }
            },
            log = object : LogStore {
                override fun planner(pins: Map<String, FoodRecord>) = engine.planner(pins)
                override suspend fun save(text: String, meal: MealSlot, dayKey: String, pins: Map<String, FoodRecord>) =
                    engine.save(text, mealSlot = meal, dayKey = dayKey, pins = pins)
            },
            library = object : LibraryStore {
                override suspend fun foodsMatching(query: String) = engine.foodsMatching(query)
                override fun planner() = engine.planner()
                override fun search(phrase: String) = engine.search(phrase)
                override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String) {
                    engine.cacheFoodAndLog(food, amount, unit, meal, dayKey)
                }
                override suspend fun save(text: String, meal: MealSlot, dayKey: String) = engine.save(text, mealSlot = meal, dayKey = dayKey)
                override suspend fun deleteFood(id: String) { engine.deleteFood(id) }
            },
            entryEdit = object : EntryEditStore {
                override suspend fun replaceAmountUnit(entryId: String, text: String) = engine.replaceEntryAmountUnit(entryId, text)
                override suspend fun replaceEntry(entryId: String, foodId: String, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String) =
                    engine.replaceEntryDraft(entryId, foodId, amount, unit, meal, dayKey)
            },
            foodForm = object : FoodFormStore {
                override suspend fun updateFood(food: FoodRecord) = engine.updateFood(food)
                override suspend fun saveFood(food: NewFood) = engine.saveCustomFood(
                    food.name, food.brand, food.barcode, food.aliases, food.nutrition, food.basisAmount, food.basisUnit,
                    sourceName = food.sourceName, sourceDescription = food.sourceDescription,
                )
                override suspend fun saveFoodAndLog(food: NewFood, meal: MealSlot, dayKey: String): FoodRecord {
                    val entry = engine.saveCustomAndLog(
                        food.name, food.brand, food.barcode, food.aliases, food.nutrition, food.basisAmount, food.basisUnit, meal,
                        food.basisAmount, food.basisUnit, dayKey = dayKey, sourceName = food.sourceName, sourceDescription = food.sourceDescription,
                    )
                    return engine.foodById(entry.foodId) ?: error("Saved food could not be reloaded.")
                }
            },
            products = object : ProductStore {
                override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String) {
                    engine.cacheFoodAndLog(food, amount, unit, meal, dayKey)
                }
                override suspend fun updateFood(food: FoodRecord) = engine.updateFood(food)
                override suspend fun cacheFood(food: FoodRecord) = engine.cacheFood(food)
            },
            barcodes = object : BarcodeLookup {
                override suspend fun localFood(barcode: String) = engine.foodByBarcode(barcode)
                override suspend fun openFoodFacts(barcode: String) = off.barcode(barcode)
            },
            diaryData = object : DiaryData {
                override suspend fun exportRows() = engine.exportRows()
                override suspend fun replaceDiary(rows: DiaryRows) { engine.replaceDiary(rows) }
                override suspend fun deleteAllData(alsoPreferences: Boolean) { engine.deleteAllData(alsoPreferences) }
            },
            diaryFiles = ContentDiaryFiles(application.contentResolver, application.noBackupFilesDir),
            transferLog = object : TransferLog {
                override fun error(message: String, cause: Throwable?) { Log.e(LOG_TAG, message, cause) }
                override fun warn(message: String) { Log.w(LOG_TAG, message) }
            },
            contributions = off,
            offSearch = off,
        ),
        settings = settingsState,
        savedState = savedState,
        threads = AppThreads(viewModelScope, io = Dispatchers.IO, main = Dispatchers.Main, compute = Dispatchers.Default),
        clock = clock,
        zone = { ZoneId.systemDefault() },
        // The "Storage unavailable" dialog offers [retryStorage]; the cause goes to the log only.
        onOpenFailed = { Log.e(LOG_TAG, "Storage could not be opened", it) },
    )

    // The owners, as [AppOwners] describes them.
    internal val settings = app.settings
    internal val day = app.day
    internal val notices = app.notices
    internal val undo = app.undo
    internal val feedback = app.feedback
    internal val contribution = app.contribution
    internal val search = app.search
    internal val transfer = app.transfer
    internal val reminders = app.reminders
    internal val resolve = app.resolve
    internal val log = app.log
    internal val library = app.library
    internal val entryEdit = app.entryEdit
    internal val foodForm = app.foodForm
    internal val scan = app.scan
    internal val review = app.review
    internal val surfaces = app.surfaces

    private val session = app.session
    val foods: List<FoodRecord> get() = session.foods
    val entries: List<MealEntry> get() = session.entries
    val ready: Boolean get() = session.ready
    /** A diary write is running ([DiarySession.write]); user actions that write are refused meanwhile. */
    val busy: Boolean get() = session.busy
    /** Storage failed to open; the dialog stays until a retry opens it. */
    val storageUnavailable: Boolean get() = session.storageUnavailable

    /** "Try again" on the storage dialog: runs the same open again. */
    fun retryStorage() = app.openStore()
    /** The 60-second loop: today is recomputed, while the selected day stays where it is. */
    fun reloadToday() { day.check(returning = false); if (ready) session.refresh() }
    private var stopped = false
    /** The activity left the screen (backgrounded or closed). */
    fun onAppStopped() { stopped = true; reminders.hide() }
    /** The activity is on screen again after a stop: a user following today moves to the new today. */
    fun onAppStarted() {
        if (!stopped) return
        stopped = false
        day.check(returning = true)
        if (ready) session.refresh()
        reminders.check()
    }
    /** The meal the "Meal" setting gives a log now ([AppOwners.automaticMealNow]); null for Ask. */
    internal fun automaticMealNow(): MealSlot? = app.automaticMealNow()
}

internal const val LOG_TAG = "Breakfast"
