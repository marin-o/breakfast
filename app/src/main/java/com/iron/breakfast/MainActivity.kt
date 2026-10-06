package com.iron.breakfast

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iron.breakfast.data.BreakfastStore
import com.iron.breakfast.domain.BreakfastEngine
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.RankedFood
import com.iron.breakfast.domain.ReplacementResult
import com.iron.breakfast.domain.ShorthandParser
import com.iron.breakfast.domain.displayLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BreakfastApp() }
    }
}

class BreakfastViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = BreakfastEngine(BreakfastStore(application))
    private val parser = ShorthandParser()

    var input by mutableStateOf("")
        private set
    var liveSuggestions by mutableStateOf(emptyList<RankedFood>())
        private set
    var savedMeal by mutableStateOf<com.iron.breakfast.domain.SavedMeal?>(null)
        private set
    var feedbackVisible by mutableStateOf(false)
        private set
    var searchOpen by mutableStateOf(false)
        private set
    var searchQuery by mutableStateOf("")
        private set
    var searchResults by mutableStateOf(emptyList<RankedFood>())
        private set
    var reinforcedEntryIds by mutableStateOf(emptySet<String>())
        private set
    var feedbackEntryIds by mutableStateOf(emptySet<String>())
        private set
    var savedAt by mutableStateOf(0L)
        private set
    var feedbackDeadlineMs by mutableStateOf(0L)
        private set
    var searchTargetEntryId by mutableStateOf<String?>(null)
        private set
    var replacementError by mutableStateOf<String?>(null)
        private set
    var preferenceRevision by mutableStateOf(0)
        private set
    var submissionInFlight by mutableStateOf(false)
        private set
    var correctionInFlightEntryIds by mutableStateOf(emptySet<String>())
        private set

    init {
        viewModelScope.launch {
            engine.initialize()
            val persisted = engine.persistedMeal()
            if (persisted.entries.isNotEmpty()) savedMeal = persisted
        }
    }

    fun onInputChanged(value: String) {
        input = value
        val currentSegment = value.substringAfterLast(',').trim()
        liveSuggestions = if (currentSegment.isBlank()) emptyList() else engine.suggestions(currentSegment)
    }

    fun done() {
        if (input.isBlank() || submissionInFlight) return
        val submittedInput = input
        submissionInFlight = true
        viewModelScope.launch {
            try {
                engine.initialize()
                val meal = engine.save(submittedInput)
                savedMeal = engine.persistedMeal().copy(unresolvedSegments = meal.unresolvedSegments)
                feedbackEntryIds = meal.entries.mapTo(mutableSetOf()) { it.id }
                savedAt = System.currentTimeMillis()
                feedbackDeadlineMs = savedAt + 30_000
                feedbackVisible = meal.entries.isNotEmpty()
                if (input == submittedInput) {
                    input = meal.unresolvedSegments.joinToString(", ")
                    liveSuggestions = emptyList()
                } else {
                    val currentSegment = input.substringAfterLast(',').trim()
                    liveSuggestions = if (currentSegment.isBlank()) emptyList() else engine.suggestions(currentSegment)
                }
                closeSearch()
            } catch (_: Throwable) {
                // Keep the captured draft available when persistence fails; the reservation is released below.
            } finally {
                submissionInFlight = false
            }
        }
    }

    fun showSearch(entryId: String) {
        if (!feedbackWindowOpen()) return
        searchTargetEntryId = entryId
        replacementError = null
        searchOpen = true
        searchQuery = ""
        searchResults = emptyList()
    }

    fun closeSearch() {
        searchOpen = false
        searchTargetEntryId = null
        replacementError = null
        searchQuery = ""
        searchResults = emptyList()
    }

    fun onSearchChanged(value: String) {
        if (!feedbackWindowOpen()) return
        searchQuery = value
        searchResults = if (value.isBlank()) emptyList() else engine.search(value)
    }

    fun selectFood(entry: MealEntry, food: FoodRecord) {
        if (!feedbackWindowOpen() || entry.id !in feedbackEntryIds || entry.id in reinforcedEntryIds ||
            entry.id in correctionInFlightEntryIds
        ) return
        correctionInFlightEntryIds = correctionInFlightEntryIds + entry.id
        viewModelScope.launch {
            try {
                applyFoodSelection(entry, food)
            } finally {
                correctionInFlightEntryIds = correctionInFlightEntryIds - entry.id
            }
        }
    }

    fun selectFromSearch(food: FoodRecord) {
        val entry = savedMeal?.entries?.firstOrNull { it.id == searchTargetEntryId } ?: return
        if (!feedbackWindowOpen() || entry.id in reinforcedEntryIds || entry.id in correctionInFlightEntryIds) return
        correctionInFlightEntryIds = correctionInFlightEntryIds + entry.id
        viewModelScope.launch {
            try {
                if (applyFoodSelection(entry, food) && searchTargetEntryId == entry.id) closeSearch()
            } finally {
                correctionInFlightEntryIds = correctionInFlightEntryIds - entry.id
            }
        }
    }

    private suspend fun applyFoodSelection(entry: MealEntry, food: FoodRecord): Boolean {
        val replacement = engine.replaceEntry(entry.id, food.id)
        val updated = when (replacement) {
            is ReplacementResult.Success -> replacement.entry
            is ReplacementResult.Failure -> {
                replacementError = replacement.reason
                return false
            }
        }
        val current = savedMeal ?: return false
        val updatedEntries = current.entries.map { if (it.id == updated.id) updated else it }
        savedMeal = current.copy(
            entries = updatedEntries,
            totals = updatedEntries.fold(NutritionValues.ZERO) { total, item -> total + item.nutritionSnapshot }
        )
        engine.reinforce(food.id)
        preferenceRevision++
        reinforcedEntryIds = reinforcedEntryIds + entry.id
        replacementError = null
        return true
    }

    fun confirm(entry: MealEntry) {
        engine.foods().firstOrNull { it.id == entry.foodId }?.let { selectFood(entry, it) }
    }

    fun alternatives(entry: MealEntry): List<RankedFood> =
        if (feedbackWindowOpen() && entry.id in feedbackEntryIds) engine.alternativesFor(entry) else emptyList()

    fun expireFeedback() {
        feedbackVisible = false
        feedbackEntryIds = emptySet()
        closeSearch()
        searchResults = emptyList()
    }

    private fun feedbackWindowOpen(): Boolean {
        if (!feedbackVisible || feedbackDeadlineMs <= 0L) return false
        if (System.currentTimeMillis() >= feedbackDeadlineMs) {
            expireFeedback()
            return false
        }
        return true
    }

    fun quantityHighlight(segment: String): AnnotatedString {
        val parsed = parser.parse(segment)
        val builder = AnnotatedString.Builder(segment)
        if (parsed.quantityStart != null && parsed.quantityEnd != null) {
            builder.addStyle(
                SpanStyle(background = Color(0xFFFFE8A1), fontWeight = FontWeight.Bold),
                parsed.quantityStart,
                parsed.quantityEnd.coerceAtMost(segment.length)
            )
        }
        return builder.toAnnotatedString()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakfastApp(vm: BreakfastViewModel = viewModel()) {
    LaunchedEffect(vm.feedbackDeadlineMs) {
        val remaining = (vm.feedbackDeadlineMs - System.currentTimeMillis()).coerceAtLeast(0L)
        if (remaining > 0L) {
            delay(remaining)
            vm.expireFeedback()
        } else if (vm.feedbackDeadlineMs > 0L) {
            vm.expireFeedback()
        }
    }
    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Breakfast") }) }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                item {
                    Text("Log a breakfast", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Use commas between foods. Examples: 2 tbsp oats, half a tbsp peanut butter, banana",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    OutlinedTextField(
                        value = vm.input,
                        onValueChange = vm::onInputChanged,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        label = { Text("Food shorthand") },
                        placeholder = { Text("2 tbsp oats, 100 ml milk") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { vm.done() })
                    )
                    val current = vm.input.substringAfterLast(',').trim()
                    if (current.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        val highlighted = remember(current) { vm.quantityHighlight(current) }
                        Text(highlighted, style = MaterialTheme.typography.bodyLarge)
                        Text("Quantity is highlighted while you type; spaces do not commit a food.", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (vm.liveSuggestions.isNotEmpty()) {
                    item {
                        Text("Suggestions", fontWeight = FontWeight.Medium)
                        vm.liveSuggestions.forEach { ranked ->
                            SuggestionRow(ranked)
                        }
                    }
                }
                item {
                    Button(
                        onClick = vm::done,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = vm.input.isNotBlank() && !vm.submissionInFlight
                    ) {
                        Text("Done — save breakfast")
                    }
                }
                if (vm.savedMeal != null) {
                    if (vm.savedMeal!!.entries.isNotEmpty()) {
                        item { SavedSummary(vm.savedMeal!!.totals) }
                        items(vm.savedMeal!!.entries, key = { it.id }) { entry ->
                            SavedEntryCard(entry, vm, entry.id in vm.feedbackEntryIds && vm.feedbackVisible)
                        }
                    }
                    if (vm.savedMeal!!.unresolvedSegments.isNotEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("Needs a match", fontWeight = FontWeight.SemiBold)
                                    Text("Still in the input: ${vm.savedMeal!!.unresolvedSegments.joinToString(", ")}")
                                    Text("Edit the text or use Search after saving a matching segment.", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    if (vm.feedbackVisible && vm.savedMeal!!.entries.isNotEmpty()) {
                        item { Text("Alternatives, Search, and Confirm disappear after 30 seconds or when the app restarts.", style = MaterialTheme.typography.labelSmall) }
                    }
                }
                if (vm.searchOpen && vm.feedbackVisible) {
                    item { SearchPanel(vm) }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SuggestionRow(ranked: RankedFood) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(ranked.food.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(ranked.reasons.firstOrNull().orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SavedSummary(totals: NutritionValues) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Saved", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${totals.calories.rounded()} kcal", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Protein ${totals.protein.displayMacro()}  •  " +
                    "Carbs ${totals.carbohydrates.displayMacro()}  •  " +
                    "Fat ${totals.fat.displayMacro()}"
            )
        }
    }
}

@Composable
private fun SavedEntryCard(entry: MealEntry, vm: BreakfastViewModel, feedbackEnabled: Boolean) {
    val alternatives = remember(feedbackEnabled, vm.preferenceRevision, entry.id, entry.foodId, entry.sourcePhrase) {
        if (feedbackEnabled) vm.alternatives(entry) else emptyList()
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(entry.foodName, fontWeight = FontWeight.SemiBold)
                Text("${entry.enteredAmount.pretty()} ${entry.enteredUnit.displayLabel()}  •  ${entry.nutritionSnapshot.calories.rounded()} kcal")
                Text(entry.conversionProvenance + if (entry.approximateConversion) " (approx.)" else "", style = MaterialTheme.typography.labelSmall)
            }
            if (feedbackEnabled) {
                HorizontalDivider()
                Text("Two quick alternatives", style = MaterialTheme.typography.labelMedium)
                alternatives.forEach { candidate ->
                    TextButton(
                        onClick = { vm.selectFood(entry, candidate.food) },
                        enabled = entry.id !in vm.reinforcedEntryIds && entry.id !in vm.correctionInFlightEntryIds
                    ) {
                        Text("Use ${candidate.food.name}")
                    }
                }
                OutlinedButton(
                    onClick = { vm.showSearch(entry.id) },
                    enabled = entry.id !in vm.reinforcedEntryIds && entry.id !in vm.correctionInFlightEntryIds
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Search catalogue")
                }
                TextButton(
                    onClick = { vm.confirm(entry) },
                    enabled = entry.id !in vm.reinforcedEntryIds && entry.id !in vm.correctionInFlightEntryIds
                ) {
                    Text(if (entry.id in vm.reinforcedEntryIds) "Preference saved" else "Confirm ${entry.foodName}")
                }
            }
        }
    }
}

@Composable
private fun SearchPanel(vm: BreakfastViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Local catalogue search", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = vm::closeSearch) { Text("×") }
            }
            vm.searchTargetEntryId?.let { targetId ->
                vm.savedMeal?.entries?.firstOrNull { it.id == targetId }?.let { target ->
                    Text("Replacing ${target.foodName}", style = MaterialTheme.typography.labelMedium)
                }
            }
            vm.replacementError?.let { reason ->
                Text(reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = vm.searchQuery,
                onValueChange = vm::onSearchChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("milk, овес, peanut...") }
            )
            vm.searchResults.forEach { result ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { vm.selectFromSearch(result.food) }.padding(vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(result.food.name)
                        Text(result.food.category, style = MaterialTheme.typography.labelSmall)
                    }
                    Text("Select", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private fun Double.rounded(): String = String.format(Locale.ROOT, "%.0f", this)

private fun Double?.displayMacro(): String = this?.let { "${it.rounded()} g" } ?: "—"

private fun Double.pretty(): String = if (this % 1.0 == 0.0) String.format(Locale.ROOT, "%.0f", this) else String.format(Locale.ROOT, "%.2f", this)
