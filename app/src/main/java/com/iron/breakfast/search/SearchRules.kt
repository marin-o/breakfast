package com.iron.breakfast.search

// The Open Food Facts search's pure decision, unit-tested: which answer still applies.

internal fun shouldApplyOffSearchResult(
    responseGeneration: Long,
    currentGeneration: Long,
    responseQuery: String,
    currentQuery: String,
    searchOpen: Boolean,
): Boolean = searchOpen && responseGeneration == currentGeneration && responseQuery == currentQuery
