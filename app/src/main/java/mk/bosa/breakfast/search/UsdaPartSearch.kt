package mk.bosa.breakfast.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import mk.bosa.breakfast.domain.CatalogueJoin
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.LatestSearch
import mk.bosa.breakfast.suspendRunCatching
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
 *
 * A part typed after a comma may be the rest of a name ("200g yogurt, gre" and "Yogurt, Greek, plain"): [typed] is given
 * the joined phrases too, and the rows are those of the first whose names run on, with that join ([joinFor]); with none
 * they are the text's own.
 */
internal class UsdaPartSearch(
    private val catalogue: UsdaCatalogue,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    main: CoroutineDispatcher,
    private val sleep: suspend (Long) -> Unit,
) {
    var rows by mutableStateOf(emptyList<FoodRecord>()); private set
    private var joined by mutableStateOf<CatalogueJoin?>(null)
    // State, as [rowsFor] reads it first: a screen that read only this must still draw when it changes, and then read [rows].
    private var asked by mutableStateOf(Ask())
    private val latest = LatestSearch<Answer>(scope, io, main)

    private data class Ask(val phrase: String = "", val joins: List<CatalogueJoin> = emptyList())
    private class Answer(val rows: List<FoodRecord>, val join: CatalogueJoin?)

    /**
     * The part being typed reads [phrase] now; blank for none. Of [joins] (what it may be the rest of, longest first) the
     * first with names that [runsOn] keeps, one test for each, answers instead.
     */
    fun typed(phrase: String, joins: List<CatalogueJoin> = emptyList(), runsOn: List<(String) -> Boolean> = emptyList()) {
        val ask = Ask(phrase, joins)
        if (ask == asked) return
        asked = ask
        rows = emptyList()
        joined = null
        if (phrase.isBlank()) { clear(); return }
        latest.launch(
            load = {
                sleep(USDA_TYPING_REST_MS)
                joins.zip(runsOn).firstNotNullOfOrNull { (join, runs) ->
                    forPart(join.phrase).filter { runs(it.name) }.takeIf { it.isNotEmpty() }?.let { Answer(it, join) }
                } ?: Answer(forPart(phrase), null)
            },
            wanted = { asked == ask },
            land = { rows = it.rows; joined = it.join },
        )
    }

    /** The rows to draw while the text reads [phrase] (and may be the rest of [joins]): none until they are its own. */
    fun rowsFor(phrase: String, joins: List<CatalogueJoin> = emptyList()): List<FoodRecord> = if (Ask(phrase, joins) == asked) rows else emptyList()

    /** The join the rows of [rowsFor] run on, or null: they are the text's own. */
    fun joinFor(phrase: String, joins: List<CatalogueJoin>): CatalogueJoin? = if (Ask(phrase, joins) == asked) joined else null

    /** The foods [phrase] finds; none when the catalogue fails. */
    suspend fun forPart(phrase: String): List<FoodRecord> = suspendRunCatching { catalogue.search(phrase) }.getOrDefault(emptyList())

    /** The Log closed: nothing runs and no row stays. */
    fun clear() {
        latest.cancel()
        asked = Ask()
        rows = emptyList()
        joined = null
    }
}
