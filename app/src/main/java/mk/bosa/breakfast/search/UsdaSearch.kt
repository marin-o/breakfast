package mk.bosa.breakfast.search

import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.TextScript
import mk.bosa.breakfast.domain.matchWords
import mk.bosa.breakfast.domain.sameScript
import mk.bosa.breakfast.domain.scriptOf
import mk.bosa.breakfast.domain.singularForms

// The USDA catalogue's search, its pure decisions unit-tested: what a typed text asks the index, and the order of the
// answers (docs/usda-catalogue-spec.md).

/**
 * The USDA catalogue; `UsdaCatalogueReader` implements it. It runs off the main thread and is read-only. No catalogue
 * is no results, never an error. Dropping an answer for an older query is the caller's, as for the Open Food Facts
 * search ([shouldApplyOffSearchResult]).
 */
interface UsdaCatalogue {
    suspend fun search(text: String): List<FoodRecord>
}

/** At most this many foods answer a search. */
internal const val USDA_MAX_RESULTS = 10

/** The last word also counts as a prefix once it has this many letters. */
private const val PREFIX_MIN_LETTERS = 3

/** At most this many MATCH expressions answer a text, however many words have other forms: each one is a query. */
private const val MAX_EXPRESSIONS = 64

/** A food the index found, before it is ranked. [bump] is above 0 for a common food; it only orders results. */
internal data class UsdaCandidate(val fdcId: Long, val name: String, val bump: Int = 0)

/** A typed text as the index is asked it: its [words], and for a word the [aliases] and singular forms it also tries. */
internal data class UsdaQuery(val words: List<String>, val aliases: Map<String, List<String>>) {
    private val lastWord = words.last()
    private val lastIsPrefix = lastWord.count(Char::isLetter) >= PREFIX_MIN_LETTERS

    /**
     * The MATCH expressions to ask, one for each choice of a word's [otherForms], the words as typed first; a food is an
     * answer when any of them finds it. Each is only quoted words, so it reads the same with or without SQLite's enhanced
     * query syntax, which leaves `OR` and brackets to mean different things; which of the two a phone has depends on how
     * its SQLite was built.
     */
    fun matchExpressions(): List<String> = words.fold(listOf(emptyList<String>())) { expressions, word ->
        val typed = if (word == lastWord && lastIsPrefix) "\"$word*\"" else "\"$word\""
        val tries = listOf(typed) + otherForms(word).map { "\"$it\"" }
        expressions.flatMap { expression -> tries.map { expression + it } }.take(MAX_EXPRESSIONS)
    }.map { it.joinToString(" ") }

    /** The whole words that also count as [word]: its aliases, its singular forms ("breasts" as "breast") and an "-ies" plural a prefix can't reach ("cherry" as "cherries"). */
    private fun otherForms(word: String): List<String> = aliases[word].orEmpty() + (singularForms(word) - word) + listOfNotNull(iesPlural(word))

    /**
     * The order of the answers: more words whole in the name, then more words in the name at all (the rest are only in
     * the additional descriptions), then the bump, then the shorter name. "egg" reads "Egg, whole" before "Eggplant",
     * shorter or not.
     */
    fun rank(candidates: List<UsdaCandidate>): List<UsdaCandidate> = candidates.sortedWith(
        compareByDescending<UsdaCandidate> { candidate -> wholeInName(usdaWords(candidate.name).toSet()) }
            .thenByDescending { candidate -> usdaWords(candidate.name).toSet().let { wholeInName(it) + prefixInName(it) } }
            .thenByDescending { it.bump }
            .thenBy { it.name.length }
            .thenBy { it.fdcId }
    )

    private fun wholeInName(name: Set<String>): Int =
        words.count { word -> word in name || otherForms(word).any { it in name } || name.any { word in singularForms(it) } }

    /** 1 when only the last word's prefix is in the name; a whole last word is counted by [wholeInName]. */
    private fun prefixInName(name: Set<String>): Int {
        val whole = lastWord in name || otherForms(lastWord).any { it in name } || name.any { lastWord in singularForms(it) }
        return if (lastIsPrefix && !whole && name.any { it.startsWith(lastWord) }) 1 else 0
    }
}

/**
 * What [text] asks the catalogue, or null when it can't ask. The catalogue's text is Latin. A Cyrillic word is asked as
 * its English words in [translations], any of which may answer, hidden from the user: the one exception to the script
 * rule, and only for this catalogue. Two words the table holds together ("црн дроб") are read as one first. A Cyrillic
 * word the table lacks, a word that mixes scripts, or a word in another script finds nothing, and a text with no word
 * has nothing to ask. The words are [matchWords] cut to letters and digits, so nothing typed can reach the index as its
 * syntax.
 */
internal fun usdaQuery(text: String, aliases: Map<String, List<String>>, translations: Map<String, List<String>> = emptyMap()): UsdaQuery? {
    val typed = matchWords(text)
    val english = buildList {
        var at = 0
        while (at < typed.size) {
            val pair = typed.getOrNull(at + 1)?.let { translations["${typed[at]} $it"] }
            val word = typed[at]
            add(pair ?: if (scriptOf(word) == TextScript.CYRILLIC) translations[word] ?: return null else listOf(word))
            at += if (pair != null) 2 else 1
        }
    }
    if (english.any { !sameScript(scriptOf(it.first()), TextScript.LATIN) }) return null
    val words = english.flatMap { usdaWords(it.first()) }.distinct()
    if (words.isEmpty()) return null
    // The other English words of a translated word count as it does, as an alias does.
    val others = english.filter { it.size > 1 }.associate { it.first() to it.drop(1) + aliases[it.first()].orEmpty() }
    return UsdaQuery(words, aliases + others)
}

/** "cherry" as "cherries"; null for any word not ending in a consonant and "y" ("turkey"). */
private fun iesPlural(word: String): String? =
    if (word.length > 2 && word.endsWith("y") && word[word.length - 2] !in "aeiou") word.dropLast(1) + "ies" else null

/** The words of [text] as the index holds them: [matchWords], with the `.` and `%` it keeps as breaks ("2.8%" is 2, 8). */
private fun usdaWords(text: String): List<String> =
    matchWords(text).flatMap { it.split('.', '%') }.filter { it.isNotEmpty() }
