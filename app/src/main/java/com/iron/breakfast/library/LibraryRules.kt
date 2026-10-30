package com.iron.breakfast.library

import com.iron.breakfast.domain.MealSlot

// The Library's pure decisions, unit-tested: when "No local match" shows, where leaving goes.

/** The "No local match" screen shows only once a finished library search for the current query is empty and nothing sits over it. */
internal fun isLibraryNoMatch(ready: Boolean, libraryOpen: Boolean, query: String, resultsQuery: String?, noResults: Boolean, otherScreenOpen: Boolean): Boolean =
    ready && libraryOpen && query.isNotBlank() && resultsQuery == query && noResults && !otherScreenOpen

/** Where leaving the Library goes. */
internal enum class LibraryExit { LOG, MEAL, DAILY }

/**
 * The Library's arrow and system Back both go here: a part being resolved back to the Log, a Library opened from a
 * meal ([origin]) back to that meal, and the Library tab to Daily.
 */
internal fun exitLibraryTarget(resolving: Boolean, origin: MealSlot?): LibraryExit = when {
    resolving -> LibraryExit.LOG
    origin != null -> LibraryExit.MEAL
    else -> LibraryExit.DAILY
}
