package com.iron.breakfast.search

import com.iron.breakfast.ui.theme.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iron.breakfast.app.Surfaces
import com.iron.breakfast.food.FoodForm
import com.iron.breakfast.library.LibraryState
import com.iron.breakfast.resolve.ResolveFlow
import com.iron.breakfast.ui.pretty
import com.iron.breakfast.ui.components.FoodRow
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.LinearProgress
import com.iron.breakfast.ui.components.PrimaryButton
import com.iron.breakfast.ui.components.ButtonKind
import com.iron.breakfast.ui.components.SearchField
import com.iron.breakfast.ui.components.SecondaryButton
import com.iron.breakfast.ui.components.TertiaryButton
import com.iron.breakfast.ui.components.TopBar
import com.iron.breakfast.ui.theme.LocalBreakfastColors
import com.iron.breakfast.ui.screens.formatKcal


/** Local search that found nothing: the way on is Open Food Facts or a personal food. */
@Composable internal fun LibraryNoMatchScreen(library: LibraryState, surfaces: Surfaces, search: OffSearch, foodForm: FoodForm) {
    val colors = LocalBreakfastColors.current
    val query = library.query
    // Leaving a no-match result must keep the Daily quick-add draft intact.
    val clear = { library.onQuery("") }
    // The top-left arrow leaves the search as Back does ([Surfaces.leaveNoMatch]).
    val leave = surfaces::leaveNoMatch
    Surface(Modifier.fillMaxSize(), color = colors.bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = Gutter)) {
            TopBar("Search", leave, Modifier.padding(top = 8.dp))
            SearchCaption(Modifier.padding(top = 6.dp))
            SearchField(query, library::onQuery, onClear = clear, label = "Search library", modifier = Modifier.padding(top = 6.dp))
            Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No local match", color = colors.ink, style = BreakfastType.Title, modifier = Modifier.semantics { heading() })
                Text(
                    "Nothing in your foods matches \"$query\".",
                    color = colors.soft,
                    style = BreakfastType.Secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp).widthIn(max = 290.dp),
                )
            }
            Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("Search Open Food Facts", { search.open(query.trim()) }, kind = ButtonKind.Neutral)
                SecondaryButton("Create personal food", { foodForm.open(query.trim()) })
            }
        }
    }
}

/** Explicit Open Food Facts search: nothing is sent until Search (or the keyboard's search key) is used. */
@Composable internal fun OffSearchScreen(search: OffSearch, foodForm: FoodForm, resolve: ResolveFlow) {
    val colors = LocalBreakfastColors.current
    Surface(Modifier.fillMaxSize(), color = colors.bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = Gutter).verticalScroll(rememberScrollState())) {
            TopBar("Open Food Facts", search::close, Modifier.padding(top = 8.dp))
            SearchCaption(Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SearchField(search.query, search::onQuery, onClear = { search.onQuery("") }, label = "Search Open Food Facts", modifier = Modifier.weight(1f), onSearch = search::submit)
                if (search.busy) SecondaryButton("Search", search::submit, Modifier.widthIn(min = 104.dp), enabled = false, fillWidth = false)
                else PrimaryButton("Search", search::submit, Modifier.widthIn(min = 104.dp), kind = ButtonKind.Neutral, fillWidth = false)
            }
            Column(Modifier.padding(top = 16.dp)) {
                when {
                    search.busy -> Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgress("Searching Open Food Facts")
                        TertiaryButton("Cancel", search::close, soft = true)
                    }
                    search.results.isNotEmpty() -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${search.results.size} results from Open Food Facts", color = colors.soft, style = BreakfastType.Secondary)
                            Text("kcal per ${search.results.first().nutrition.amount.pretty()} ${search.results.first().nutrition.unit.label}", color = colors.soft, style = BreakfastType.Secondary)
                        }
                        Hairline(Modifier.padding(top = 10.dp))
                        search.results.forEach { food ->
                            FoodRow(food.name, food.brand.orEmpty(), formatKcal(food.nutrition.values.calories), onClick = { search.choose(food) })
                        }
                    }
                }
                search.message?.let { message ->
                    val createPersonal = { foodForm.open(search.query.trim(), fromResolver = resolve.continuation) }
                    when (offSearchStatus(search.unavailable, message)) {
                        OffSearchStatus.Offline -> SearchStatus(
                            title = "No connection",
                            body = "Open Food Facts needs internet. Local foods still work.",
                            retry = search::submit,
                            createPersonal = createPersonal,
                        )
                        OffSearchStatus.NoResults -> SearchStatus(title = "No results", body = null, retry = null, createPersonal = createPersonal)
                        OffSearchStatus.Failed -> SearchStatus(
                            title = "Search failed",
                            body = "Open Food Facts returned an error.",
                            retry = search::submit,
                            createPersonal = createPersonal,
                        )
                    }
                }
            }
        }
    }
}

/** Which status block the Open Food Facts search shows after a query. */
internal enum class OffSearchStatus { Offline, NoResults, Failed }

/** Offline wins; a "No usable result" message is an empty result; anything else is a failed search. */
internal fun offSearchStatus(unavailable: Boolean, message: String): OffSearchStatus = when {
    unavailable -> OffSearchStatus.Offline
    message.startsWith("No usable result") -> OffSearchStatus.NoResults
    else -> OffSearchStatus.Failed
}

/** The soft "Search" label above both search fields. */
@Composable private fun SearchCaption(modifier: Modifier = Modifier) {
    Text("Search", color = LocalBreakfastColors.current.soft, style = BreakfastType.Meta, modifier = modifier)
}

/** A centred status on the Open Food Facts search: title, optional soft body, a Neutral Retry when there is one, and a personal-food secondary. */
@Composable private fun SearchStatus(title: String, body: String?, retry: (() -> Unit)?, createPersonal: () -> Unit) {
    val colors = LocalBreakfastColors.current
    Column(Modifier.fillMaxWidth().padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = colors.ink, style = BreakfastType.Title, modifier = Modifier.semantics { heading() })
        if (body != null) Text(body, color = colors.soft, style = BreakfastType.Secondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp).widthIn(max = 290.dp))
        Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (retry != null) PrimaryButton("Retry", retry, kind = ButtonKind.Neutral)
            SecondaryButton("Create personal food", createPersonal)
        }
    }
}
