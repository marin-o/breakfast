package com.iron.breakfast

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iron.breakfast.data.BreakfastStore
import com.iron.breakfast.domain.BreakfastEngine
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.RankedFood
import com.iron.breakfast.domain.ShorthandParser
import com.iron.breakfast.domain.displayLabel
import kotlinx.coroutines.delay
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
    var savedAt by mutableStateOf(0L)
        private set

    fun onInputChanged(value: String) {
        input = value
        val currentSegment = value.substringAfterLast(',').trim()
        liveSuggestions = if (currentSegment.isBlank()) emptyList() else engine.suggestions(currentSegment)
    }

    fun done() {
        if (input.isBlank()) return
        savedMeal = engine.save(input)
        savedAt = System.currentTimeMillis()
        feedbackVisible = true
        input = ""
        liveSuggestions = emptyList()
    }

    fun showSearch() {
        searchOpen = true
        searchQuery = ""
        searchResults = emptyList()
    }

    fun closeSearch() {
        searchOpen = false
        searchQuery = ""
        searchResults = emptyList()
    }

    fun onSearchChanged(value: String) {
        searchQuery = value
        searchResults = if (value.isBlank()) emptyList() else engine.search(value)
    }

    fun reinforce(entry: MealEntry, food: FoodRecord? = null) {
        if (entry.id in reinforcedEntryIds) return
        engine.reinforce(food?.id ?: entry.foodId)
        reinforcedEntryIds = reinforcedEntryIds + entry.id
    }

    fun reinforceFromSearch(food: FoodRecord) {
        engine.reinforce(food.id)
    }

    fun alternatives(entry: MealEntry): List<RankedFood> =
        engine.search(entry.foodName).filterNot { it.food.id == entry.foodId }.take(2)

    fun expireFeedback() {
        feedbackVisible = false
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
    LaunchedEffect(vm.savedAt) {
        if (vm.savedAt > 0) {
            delay(30_000)
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
                        Text(vm.quantityHighlight(current), style = MaterialTheme.typography.bodyLarge)
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
                    Button(onClick = vm::done, modifier = Modifier.fillMaxWidth(), enabled = vm.input.isNotBlank()) {
                        Text("Done — save breakfast")
                    }
                }
                if (vm.feedbackVisible && vm.savedMeal != null) {
                    item { SavedSummary(vm.savedMeal!!.totals) }
                    items(vm.savedMeal!!.entries, key = { it.id }) { entry ->
                        SavedEntryCard(entry, vm)
                    }
                    item {
                        OutlinedButton(onClick = vm::showSearch, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Search catalogue")
                        }
                        Text("Alternatives and Confirm disappear after 30 seconds or when the app restarts.", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (vm.searchOpen) {
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
            Text("Protein ${totals.protein.rounded()} g  •  Carbs ${totals.carbohydrates.rounded()} g  •  Fat ${totals.fat.rounded()} g")
        }
    }
}

@Composable
private fun SavedEntryCard(entry: MealEntry, vm: BreakfastViewModel) {
    val alternatives = vm.alternatives(entry)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(entry.foodName, fontWeight = FontWeight.SemiBold)
            Text("${entry.enteredAmount.pretty()} ${entry.enteredUnit.displayLabel()}  •  ${entry.nutritionSnapshot.calories.rounded()} kcal")
            Text(entry.conversionProvenance + if (entry.approximateConversion) " (approx.)" else "", style = MaterialTheme.typography.labelSmall)
            Divider()
            Text("Two quick alternatives", style = MaterialTheme.typography.labelMedium)
            alternatives.forEach { candidate ->
                TextButton(onClick = { vm.reinforce(entry, candidate.food) }, enabled = entry.id !in vm.reinforcedEntryIds) {
                    Text("Use ${candidate.food.name}")
                }
            }
            TextButton(onClick = { vm.reinforce(entry) }, enabled = entry.id !in vm.reinforcedEntryIds) {
                Text(if (entry.id in vm.reinforcedEntryIds) "Preference saved" else "Confirm ${entry.foodName}")
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
                    modifier = Modifier.fillMaxWidth().clickable { vm.reinforceFromSearch(result.food) }.padding(vertical = 8.dp),
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

private fun Double.pretty(): String = if (this % 1.0 == 0.0) String.format(Locale.ROOT, "%.0f", this) else String.format(Locale.ROOT, "%.2f", this)
