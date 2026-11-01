package com.iron.breakfast.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.LatestSearch
import com.iron.breakfast.suspendRunCatching
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/** How long the typing rests before the catalogue is asked. */
internal const val USDA_TYPING_REST_MS = 150L

/**
 * The USDA foods under what is being typed: the Log's parts, the Library's search, the recipe form's. It only reads:
 * nothing here touches the draft or the diary, so rows arriving never change a selection and Add never logs one that
 * wasn't tapped.
 *
 * [rows] follow the text being typed ([typed]): asked once the typing rests, off the main thread; an answer for an older
 * text is dropped, and the previous rows go at once, so none can be tapped for the new text ([rowsFor]). A part Add left
 * unmatched asks once ([forPart]).
 */
internal class UsdaPartSearch(
    private val catalogue: UsdaCatalogue,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    main: CoroutineDispatcher,
    private val sleep: suspend (Long) -> Unit,
) {
    var rows by mutableStateOf(emptyList<FoodRecord>()); private set
    private var asked = ""
    private val latest = LatestSearch<List<FoodRecord>>(scope, io, main)

    /** The part being typed reads [phrase] now; blank for none. */
    fun typed(phrase: String) {
        if (phrase == asked) return
        asked = phrase
        rows = emptyList()
        if (phrase.isBlank()) { clear(); return }
        latest.launch(
            load = { sleep(USDA_TYPING_REST_MS); forPart(phrase) },
            wanted = { asked == phrase },
            land = { rows = it },
        )
    }

    /** The rows to draw while the text reads [phrase]: none until they are its own. */
    fun rowsFor(phrase: String): List<FoodRecord> = if (phrase == asked) rows else emptyList()

    /** The foods [phrase] finds; none when the catalogue fails. */
    suspend fun forPart(phrase: String): List<FoodRecord> = suspendRunCatching { catalogue.search(phrase) }.getOrDefault(emptyList())

    /** The Log closed: nothing runs and no row stays. */
    fun clear() {
        latest.cancel()
        asked = ""
        rows = emptyList()
    }
}
