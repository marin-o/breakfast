package com.iron.breakfast.app

import com.iron.breakfast.profile.WeightStore
import com.iron.breakfast.profile.ProfilePrefs
import com.iron.breakfast.domain.WeightPoint
import com.iron.breakfast.domain.newestFirst
import androidx.lifecycle.SavedStateHandle
import com.iron.breakfast.QueueDispatcher
import com.iron.breakfast.contribution.OffContributions
import com.iron.breakfast.data.OffContributionDraft
import com.iron.breakfast.data.OffContributionResult
import com.iron.breakfast.data.OffPreflightResult
import com.iron.breakfast.data.OffResult
import com.iron.breakfast.data.OffSearchResult
import com.iron.breakfast.domain.TestFoods
import com.iron.breakfast.domain.FoodMatcher
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.LogPlanner
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.RankedFood
import com.iron.breakfast.domain.ReplacementResult
import com.iron.breakfast.domain.Recipe
import com.iron.breakfast.recipes.RecipeStore
import com.iron.breakfast.domain.SavedMeal
import com.iron.breakfast.entry.EntryEditStore
import com.iron.breakfast.food.FoodFormStore
import com.iron.breakfast.food.NewFood
import com.iron.breakfast.library.LibraryStore
import com.iron.breakfast.log.LogStore
import com.iron.breakfast.resolve.ResolveStore
import com.iron.breakfast.resolve.food
import com.iron.breakfast.scan.BarcodeLookup
import com.iron.breakfast.scan.ProductStore
import com.iron.breakfast.search.OffSearchService
import com.iron.breakfast.settings.AppSettings
import com.iron.breakfast.settings.FakeSettingsStore
import com.iron.breakfast.settings.SettingsState
import com.iron.breakfast.today.MatchStore
import com.iron.breakfast.today.UndoStore
import com.iron.breakfast.transfer.FakeData
import com.iron.breakfast.transfer.FakeFiles
import com.iron.breakfast.transfer.RecordingLog
import com.iron.breakfast.transfer.exportReminderOnScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import java.time.Instant
import java.time.ZoneOffset

/**
 * The app as the view model builds it, over fakes: the same [AppOwners] (every feature owner, [Surfaces], the session
 * listeners, the settings observers, the saved state and what runs when storage opens), with these [AppStores] in
 * place of the engine and Open Food Facts. What is drawn ([shown]), what takes Back ([back]) and whether anything is
 * over the base screen, read from it.
 *
 * The clock stands at noon on 2026-10-28 (UTC); settings start at their defaults; the Undo and "Not right?" timers never
 * run out. IO runs at once in the caller unless [io] is set [QueueDispatcher.holding]; the main and compute work is
 * unconfined. [savedState] can be handed to a second kit, as the process would hand it after being killed; [foods]
 * are in storage when it opens.
 *
 * Characterisation: [SurfacesTest] was first written against the app's three encodings as they were before
 * `Surfaces` (the view model's coordination, `IronApp`'s composition order and BackHandler order, `topOverlay()`,
 * transcribed here at 6da279d) and passed unchanged against `Surfaces` (B17).
 */
internal class AppKit(
    val savedState: SavedStateHandle = SavedStateHandle(),
    foods: List<FoodRecord> = emptyList(),
    damagedDiary: Boolean = false,
    /** The stored settings; the tutorial is done, unless a test passes an empty store: a first launch. */
    val settingsStore: FakeSettingsStore = FakeSettingsStore().also { AppSettings(it).tutorialDone = true },
) {
    val diary = KitDiary().also { it.foods = foods; it.damaged = damagedDiary }
    /** Writes the fakes received, in order. */
    val calls = mutableListOf<String>()
    /** What a tap taught, in order. */
    val reinforced = mutableListOf<String>()
    /** What replacing an entry's food answers. */
    var replacement: ReplacementResult = ReplacementResult.Failure("not in these tests")
    /** What Open Food Facts answers, whatever it is asked. */
    var offFoods: List<FoodRecord> = emptyList()
    /** What the USDA catalogue answers, whatever it is asked. */
    var usdaFoods: List<FoodRecord> = emptyList()
    /** What Open Food Facts was asked, in order. */
    val offSearched = mutableListOf<String>()
    /** What the queue logged: amount, unit and food. */
    val queueLogged = mutableListOf<String>()
    /** What the Log's Add and the Library's mixed batch leave unmatched. */
    var unmatched: List<String> = emptyList()
    /** Barcodes the Library has. */
    val barcodes = mutableMapOf<String, FoodRecord>()
    /** Foods the Library's list finds for any query; empty gives "No local match". */
    var libraryFoods: List<FoodRecord> = emptyList()
    /** The foods the Log's planner matches. */
    var plannerFoods: List<FoodRecord> = TestFoods.fixtures
    private val plannerFor = { pins: Map<String, FoodRecord> -> LogPlanner(FoodMatcher(plannerFoods), pins = pins) }
    private val transferEvents = mutableListOf<String>()
    /** Picked documents for an import, by name. */
    val documents = FakeFiles(transferEvents)
    /** The diary's replace and delete; set [FakeData.failure] to make them fail. */
    val data = FakeData(transferEvents)
    /** File and Open Food Facts work; set [QueueDispatcher.holding] to keep it "in flight" until [QueueDispatcher.runAll]. */
    val io = QueueDispatcher(holding = false)
    /** The clock; a test moves it to reach another diary day. */
    var nowMs = Instant.parse("2026-10-28T12:00:00Z").toEpochMilli()
    private val now get() = nowMs
    /** Where the profile's values are kept. */
    val profileStore = FakeSettingsStore()

    val owners = AppOwners(
        stores = AppStores(
            diary = diary.source,
            undo = object : UndoStore {
                override suspend fun deleteEntry(id: String) { calls += "delete entry $id" }
                override suspend fun restoreEntry(entry: MealEntry) { calls += "restore entry ${entry.id}" }
                override suspend fun deleteEntries(entries: List<MealEntry>) { calls += "delete entries ${entries.map { it.id }}" }
            },
            match = object : MatchStore {
                override fun alternativesFor(entry: MealEntry) = emptyList<RankedFood>()
                override suspend fun replaceEntry(entryId: String, foodId: String): ReplacementResult { calls += "replace $entryId with $foodId"; return replacement }
                override suspend fun replaceEntryWith(entryId: String, foodId: String, amount: Double, unit: MeasureUnit, servingName: String?, meal: MealSlot, dayKey: String): ReplacementResult {
                    calls += "replace $entryId with $foodId $amount ${servingName ?: unit.label} $meal $dayKey"
                    return replacement
                }
                override suspend fun reinforce(foodId: String) { calls += "reinforce $foodId" }
            },
            resolve = object : ResolveStore {
                override fun typed(raw: String) = plannerFor(emptyMap()).typed(raw)
                override fun search(query: String) = emptyList<RankedFood>()
                override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String, servingName: String?) {
                    calls += "queue log ${food.id} $meal"
                    queueLogged += "$amount ${unit.label} ${food.id}"
                }
            },
            log = object : LogStore {
                override fun planner(pins: Map<String, FoodRecord>) = plannerFor(pins)
                override suspend fun save(text: String, meal: MealSlot, dayKey: String, pins: Map<String, FoodRecord>): SavedMeal {
                    calls += "log save \"$text\" $meal"
                    return SavedMeal(listOf(com.iron.breakfast.today.entry("logged")), NutritionValues.ZERO, unmatched)
                }
                override suspend fun reinforce(foodId: String) { reinforced += foodId }
            },
            library = object : LibraryStore {
                override suspend fun foodsMatching(query: String) = libraryFoods
                override fun planner() = plannerFor(emptyMap())
                override fun search(phrase: String) = emptyList<RankedFood>()
                override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String, servingName: String?) {
                    calls += "library log ${food.id} $meal"
                }
                override suspend fun save(text: String, meal: MealSlot, dayKey: String): SavedMeal {
                    calls += "library save \"$text\" $meal"
                    return SavedMeal(listOf(com.iron.breakfast.today.entry("batch")), NutritionValues.ZERO, unmatched)
                }
                override suspend fun deleteFood(id: String) { calls += "delete food $id" }
            },
            entryEdit = object : EntryEditStore {
                override suspend fun replaceAmountUnit(entryId: String, text: String) = ReplacementResult.Failure("not in these tests")
                override suspend fun replaceEntry(entryId: String, foodId: String, amount: Double, unit: MeasureUnit, servingName: String?, meal: MealSlot, dayKey: String) =
                    ReplacementResult.Failure("not in these tests")
            },
            foodForm = object : FoodFormStore {
                override suspend fun updateFood(food: FoodRecord) { calls += "update ${food.id}" }
                override suspend fun saveFood(food: NewFood): FoodRecord { calls += "save food ${food.name}"; return food(food.name.lowercase(), food.name).copy(barcode = food.barcode.ifBlank { null }) }
                override suspend fun saveFoodAndLog(food: NewFood, meal: MealSlot, dayKey: String): FoodRecord {
                    calls += "save and log ${food.name} $meal"
                    return food(food.name.lowercase(), food.name)
                }
            },
            products = object : ProductStore {
                override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String, servingName: String?) {
                    calls += "review log ${food.id} $meal"
                    // As the engine stores it: over a stored food with its id.
                    diary.foods = diary.foods.map { if (it.id == food.id) food else it }
                }
                override suspend fun updateFood(food: FoodRecord) {
                    calls += "review save ${food.id}"
                    diary.foods = diary.foods.map { if (it.id == food.id) food else it }
                }
                override suspend fun cacheFood(food: FoodRecord): FoodRecord { calls += "review store ${food.id}"; return food }
                override suspend fun fullProduct(barcode: String): FoodRecord? { calls += "fetch $barcode"; return null }
            },
            barcodes = object : BarcodeLookup {
                override suspend fun localFood(barcode: String) = barcodes[barcode]
                override suspend fun openFoodFacts(barcode: String) = OffResult.Missing
            },
            diaryData = data,
            diaryFiles = documents,
            transferLog = RecordingLog(),
            contributions = object : OffContributions {
                override fun preflight(draft: OffContributionDraft) = OffPreflightResult.Existing
                override fun contribute(draft: OffContributionDraft, username: String, password: String) = OffContributionResult.Success
            },
            offSearch = object : OffSearchService { override suspend fun search(query: String): OffSearchResult { offSearched += query; return OffSearchResult.Success(offFoods) } },
            usda = object : com.iron.breakfast.search.UsdaCatalogue { override suspend fun search(text: String) = usdaFoods },
            licence = { "SIL Open Font License" },
            weights = object : WeightStore {
                private var nextId = 1L
                private fun stored(point: WeightPoint) = point.copy(id = nextId++)
                override suspend fun add(point: WeightPoint) { diary.weights = diary.weights + stored(point) }
                override suspend fun replaceLatest(point: WeightPoint) {
                    val latest = newestFirst(diary.weights).firstOrNull { it.dayKey == point.dayKey }
                    diary.weights = diary.weights.filterNot { it == latest } + stored(point)
                }
                override suspend fun delete(id: Long) { diary.weights = diary.weights.filterNot { it.id == id } }
            },
            profilePrefs = ProfilePrefs(profileStore),
            recipes = object : RecipeStore {
                override fun ingredientPlanner(pins: Map<String, FoodRecord>) = plannerFor(pins)
                override suspend fun foodsMatching(query: String) = libraryFoods
                override suspend fun cacheFood(food: FoodRecord): FoodRecord { calls += "recipe store ${food.id}"; return food }
                override suspend fun saveRecipe(recipe: Recipe): Recipe {
                    val stored = recipe.copy(id = recipe.id.ifBlank { "recipe-${recipe.name.lowercase()}" })
                    calls += "save recipe ${stored.id}"
                    diary.recipes = diary.recipes.filterNot { it.id == stored.id } + stored
                    return stored
                }
                override suspend fun deleteRecipe(id: String) { calls += "delete recipe $id"; diary.recipes = diary.recipes.filterNot { it.id == id } }
                override suspend fun logRecipe(recipe: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String) {
                    calls += "log recipe ${recipe.id} $amount ${unit.name} $meal"
                }
            },
        ),
        settings = SettingsState(AppSettings(settingsStore)) { now },
        savedState = savedState,
        threads = AppThreads(diary.scope, io = io, main = Dispatchers.Unconfined, compute = Dispatchers.Unconfined, sleep = { awaitCancellation() }),
        clock = { now },
        zone = { ZoneOffset.UTC },
        onOpenFailed = { throw it },
    )

    // ---- The owners, by the names the view model gives them. ----

    val settings = owners.settings
    val day = owners.day
    val notices = owners.notices
    val confirmations = owners.confirmations
    val undo = owners.undo
    val feedback = owners.feedback
    val contribution = owners.contribution
    val search = owners.search
    val transfer = owners.transfer
    val reminders = owners.reminders
    val resolve = owners.resolve
    val log = owners.log
    val library = owners.library
    val entryEdit = owners.entryEdit
    val foodForm = owners.foodForm
    val scan = owners.scan
    val review = owners.review
    val surfaces = owners.surfaces
    val recipes = owners.recipes
    val recipeForm = owners.recipeForm

    /** The day picked, as the date picker picks it (`DayState.goTo`). */
    fun selectDay(value: String) = day.goTo(value)

    /** Puts [entries] in the diary, as a reload would. */
    fun diaryHas(vararg entries: MealEntry) {
        diary.rows = entries.toList()
        owners.session.refresh()
    }

    /** Puts [recipes] in the diary, as a reload would. */
    fun recipesAre(vararg recipes: Recipe) {
        diary.recipes = recipes.toList()
        owners.session.refresh()
    }

    // ---- What the tests drive, by the names they were written with. ----

    val meal: MealSlot? get() = surfaces.meal
    val settingsOpen: Boolean get() = surfaces.settingsOpen
    fun mealOnScreen(): MealSlot? = surfaces.mealOnScreen()
    fun openMeal(s: MealSlot) = surfaces.openMeal(s)
    fun closeMeal() = surfaces.closeMeal()
    fun openLibrary() = surfaces.openLibrary()
    fun openFocusedLibrary(target: MealSlot, query: String = "") = surfaces.openFocusedLibrary(target, query)
    fun openLibraryFoodDetail(entry: MealEntry) = surfaces.openLibraryFoodDetail(entry)
    fun closeLibrary() = surfaces.closeLibrary()
    fun exitLibrary() = surfaces.exitLibrary()
    fun openSettings() = surfaces.openSettings()

    /** What the root composable draws, bottom first. */
    fun shown(): List<String> = surfaces.shown().map { it.name }

    /**
     * System Back as the activity's dispatcher delivers it (a dialog or sheet takes Back in its own window first). False
     * when nothing takes it, so the system handles it (the app goes back).
     */
    fun back(): Boolean = surfaces.back()

    /** A due export reminder is drawn on Daily. */
    fun reminderShown(): Boolean = exportReminderOnScreen(due = true, covered = surfaces.baseCovered())
}

/** The diary the kit's storage holds ([rows], [foods]), read by the session through [source], on an unconfined [scope]. */
internal class KitDiary {
    /** A damaged diary was set aside and its notice waits (D15). */
    var damaged = false
    var rows: List<MealEntry> = emptyList()
    var foods: List<FoodRecord> = emptyList()
    var recipes: List<Recipe> = emptyList()
    var weights: List<WeightPoint> = emptyList()
    val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
    val source = object : DiarySource {
        override suspend fun open() {}
        override fun foods(): List<FoodRecord> = foods
        override fun entries(): List<MealEntry> = rows
        override fun recipes(): List<Recipe> = recipes
        override fun weightPoints(): List<WeightPoint> = weights
        override suspend fun reloadIfStale() = false
        override fun damagedNoticePending() = damaged
        override fun damagedNoticeSeen() { damaged = false }
    }
}
