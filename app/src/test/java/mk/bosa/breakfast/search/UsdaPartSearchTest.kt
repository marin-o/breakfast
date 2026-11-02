package mk.bosa.breakfast.search

import mk.bosa.breakfast.domain.FoodOrigin
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.NutritionBasis
import mk.bosa.breakfast.domain.NutritionValues
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Test

class UsdaPartSearchTest {
    private fun food(name: String) = FoodRecord(
        "fndds_$name", name, emptyList(), "USDA", FoodOrigin.GENERIC,
        NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(100.0, 1.0, 1.0, 1.0)),
    )

    private val asked = mutableListOf<String>()
    private val answers = mutableMapOf<String, CompletableDeferred<List<FoodRecord>>>()
    private val rests = mutableListOf<CompletableDeferred<Unit>>()
    private val catalogue = object : UsdaCatalogue {
        override suspend fun search(text: String): List<FoodRecord> {
            asked += text
            return answers[text]?.await() ?: listOf(food(text))
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private fun search(rest: suspend (Long) -> Unit = {}) =
        UsdaPartSearch(catalogue, scope, Dispatchers.Unconfined, Dispatchers.Unconfined, rest)

    private val holdingRest: suspend (Long) -> Unit = { CompletableDeferred<Unit>().also { rests += it }.await() }

    @Test fun `only the text the typing rests on is asked`() {
        val search = search(holdingRest)
        search.typed("e")
        search.typed("eg")
        search.typed("egg")
        rests.last().complete(Unit)
        assertEquals(listOf("egg"), asked)
        assertEquals(listOf("egg"), search.rows.map { it.name })
    }

    @Test fun `an answer for an older text is dropped`() {
        answers["egg"] = CompletableDeferred()
        val search = search()
        search.typed("egg")
        search.typed("milk")
        answers.getValue("egg").complete(listOf(food("late egg")))
        assertEquals(listOf("milk"), search.rows.map { it.name })
    }

    @Test fun `old rows go at once and are drawn only for their own text`() {
        val search = search()
        search.typed("milk")
        assertEquals(listOf("milk"), search.rowsFor("milk").map { it.name })
        assertEquals("a text not asked yet has no rows", emptyList<FoodRecord>(), search.rowsFor("milk r"))
        answers["milk r"] = CompletableDeferred()
        search.typed("milk r")
        assertEquals(emptyList<FoodRecord>(), search.rows)
        answers.getValue("milk r").complete(listOf(food("Milk, reduced fat")))
        assertEquals(listOf("Milk, reduced fat"), search.rowsFor("milk r").map { it.name })
        search.typed("")
        assertEquals(emptyList<FoodRecord>(), search.rows)
    }

    @Test fun `a catalogue that fails is no rows`() {
        val failing = UsdaPartSearch(object : UsdaCatalogue {
            override suspend fun search(text: String): List<FoodRecord> = error("broken")
        }, scope, Dispatchers.Unconfined, Dispatchers.Unconfined) {}
        failing.typed("egg")
        assertEquals(emptyList<FoodRecord>(), failing.rows)
    }
}
