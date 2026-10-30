package com.iron.breakfast.search

import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.TextScript
import com.iron.breakfast.domain.matchWords
import com.iron.breakfast.domain.sameScript
import com.iron.breakfast.domain.scriptOf

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
