package com.iron.breakfast.search

import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.TextScript
import com.iron.breakfast.domain.matchWords
import com.iron.breakfast.domain.sameScript
import com.iron.breakfast.domain.scriptOf
import com.iron.breakfast.domain.singularForms

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

/** A food the index found, before it is ranked. */
internal data class UsdaCandidate(val fdcId: Long, val name: String)

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

    /** The whole words that also count as [word]: its aliases and its singular forms ([singularForms]), "breasts" as "breast". */
    private fun otherForms(word: String): List<String> = aliases[word].orEmpty() + (singularForms(word) - word)

    /**
     * The order of the answers: more words whole in the name, then more words in the name at all (the rest are only in
     * the additional descriptions), then the shorter name. "egg" reads "Egg, whole" before "Eggplant", shorter or not.
     */
    fun rank(candidates: List<UsdaCandidate>): List<UsdaCandidate> = candidates.sortedWith(
        compareByDescending<UsdaCandidate> { candidate -> wholeInName(usdaWords(candidate.name).toSet()) }
            .thenByDescending { candidate -> usdaWords(candidate.name).toSet().let { wholeInName(it) + prefixInName(it) } }
            .thenBy { it.name.length }
            .thenBy { it.fdcId }
    )

    private fun wholeInName(name: Set<String>): Int =
        words.count { word -> word in name || otherForms(word).any { it in name } }

    /** 1 when only the last word's prefix is in the name; a whole last word is counted by [wholeInName]. */
    private fun prefixInName(name: Set<String>): Int {
        val whole = lastWord in name || otherForms(lastWord).any { it in name }
        return if (lastIsPrefix && !whole && name.any { it.startsWith(lastWord) }) 1 else 0
    }
}

/**
 * What [text] asks the catalogue, or null when it can't ask: the catalogue's text is Latin, so a word in another script
 * or a mix of them finds nothing (the script rule, [sameScript]), and a text with no word has nothing to ask. The words
 * are [matchWords] cut to letters and digits, so nothing typed can reach the index as its syntax.
 */
internal fun usdaQuery(text: String, aliases: Map<String, List<String>>): UsdaQuery? {
    if (matchWords(text).any { !sameScript(scriptOf(it), TextScript.LATIN) }) return null
    val words = usdaWords(text).distinct()
    return if (words.isEmpty()) null else UsdaQuery(words, aliases)
}

/** The words of [text] as the index holds them: [matchWords], with the `.` and `%` it keeps as breaks ("2.8%" is 2, 8). */
private fun usdaWords(text: String): List<String> =
    matchWords(text).flatMap { it.split('.', '%') }.filter { it.isNotEmpty() }
