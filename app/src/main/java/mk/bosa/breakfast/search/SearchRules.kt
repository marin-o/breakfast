package mk.bosa.breakfast.search

import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.TextScript
import mk.bosa.breakfast.domain.matchWords
import mk.bosa.breakfast.domain.sameScript
import mk.bosa.breakfast.domain.scriptOf
import mk.bosa.breakfast.domain.singularForms

// The Open Food Facts search's pure decision, unit-tested: which answer still applies.

internal fun shouldApplyOffSearchResult(
    responseGeneration: Long,
    currentGeneration: Long,
    responseQuery: String,
    currentQuery: String,
    searchOpen: Boolean,
): Boolean = searchOpen && responseGeneration == currentGeneration && responseQuery == currentQuery

/** Whether a name is worth asking Open Food Facts: it has a letter or a digit, and no word that mixes scripts. */
internal fun offQuerySendable(name: String): Boolean =
    name.any(Char::isLetterOrDigit) && matchWords(name).none { scriptOf(it) == TextScript.MIXED }

/** Whether a typed name may go to Open Food Facts with nothing pressed: [offQuerySendable], with three letters at least. */
internal fun offSearchesByItself(name: String): Boolean = offQuerySendable(name) && name.count(Char::isLetter) >= 3

/**
 * [foods] as Open Food Facts answered [query], most relevant first. It matches a word anywhere, an ingredient too, so
 * "olive oil" finds sprays and sauces among the oils. Stable, so within a group the order it gave stays (its most
 * scanned first). The groups: names with every word of [query] as a whole word (a plural counts) and the words side by
 * side in the typed order ("Extra Virgin Olive Oil"), then names with every word apart ("Oil, Olive Pomace"), then names
 * with some of the words ("Oil Spray"), then the rest. Nothing is dropped.
 */
internal fun rankOffResults(query: String, foods: List<FoodRecord>): List<FoodRecord> {
    val words = matchWords(query)
    if (words.isEmpty()) return foods
    fun isWord(typed: String, printed: String) = typed == printed || printed in singularForms(typed) || typed in singularForms(printed)
    fun tier(food: FoodRecord): Int {
        val name = matchWords(food.name)
        val found = words.count { word -> name.any { isWord(word, it) } }
        if (found == 0) return 3
        if (found < words.size) return 2
        val together = (0..name.size - words.size).any { at -> words.indices.all { isWord(words[it], name[at + it]) } }
        return if (together) 0 else 1
    }
    return foods.sortedBy(::tier)
}

/**
 * The [foods] a search or a local choice for [name] may show, by the script rule: a product is shown only with a name word in a
 * script [name] has letters in ("кисело млеко 2%" is Cyrillic), and a name with no letters shows them all. The one filter
 * of every list of foods offered for a typed name.
 */
internal fun foodsInScript(name: String, foods: List<FoodRecord>): List<FoodRecord> {
    val scripts = matchWords(name).map(::scriptOf).filter { it != TextScript.NONE }
    if (TextScript.MIXED in scripts) return emptyList()
    if (scripts.isEmpty()) return foods
    return foods.filter { food -> matchWords(food.name).any { word -> scripts.any { sameScript(it, scriptOf(word)) } } }
}
