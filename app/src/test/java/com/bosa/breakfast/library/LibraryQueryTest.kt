package com.bosa.breakfast.library

import com.bosa.breakfast.domain.ShorthandParser
import org.junit.Assert.assertEquals
import org.junit.Test

/** The library list filters by what the search bar shows: a typed amount is not part of the food the list is searched for. */
class LibraryQueryTest {
    private val parser = ShorthandParser()

    @Test fun aPlainQueryIsTheQuery() {
        assertEquals("kiselo mleko", librarySearchText("kiselo mleko", parser))
    }

    @Test fun aTypedAmountIsLeftOutOfTheQuery() {
        assertEquals("banana", librarySearchText("2 banana", parser))
        assertEquals("kiselo mleko", librarySearchText("kiselo mleko 200g", parser))
    }
}
