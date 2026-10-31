package com.iron.breakfast.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsdaSearchTest {
    private val aliases = mapOf("breaded" to listOf("coated"))

    private fun expressions(text: String) = usdaQuery(text, aliases)!!.matchExpressions()

    private fun ranked(text: String, vararg names: String) =
        usdaQuery(text, aliases)!!.rank(names.mapIndexed { index, name -> UsdaCandidate(index.toLong(), name) }).map { it.name }

    @Test fun `every word must match, whole, and the last word also as a prefix`() {
        assertEquals(listOf("\"chicken\" \"breast*\""), expressions("chicken breast"))
    }

    @Test fun `a last word under three letters is whole only`() {
        assertEquals(listOf("\"eg\""), expressions("eg"))
        assertEquals(listOf("\"egg*\""), expressions("egg"))
        assertEquals(listOf("\"milk\" \"2\""), expressions("milk 2"))
    }

    @Test fun `an alias also tries its target and the typed word stays`() {
        assertEquals(
            listOf("\"breaded\" \"chicken\" \"breast*\"", "\"coated\" \"chicken\" \"breast*\""),
            expressions("breaded chicken breast"),
        )
    }

    @Test fun `an alias of the last word is whole`() {
        assertEquals(listOf("\"chicken\" \"breaded*\"", "\"chicken\" \"coated\""), expressions("chicken breaded"))
    }

    @Test fun `a repeated word is asked once`() {
        assertEquals(listOf("\"milk*\""), expressions("milk milk"))
    }

    @Test fun `case and an apostrophe read as the index reads them`() {
        assertEquals(listOf("\"farmers\" \"cheese*\"", "\"farmer\" \"cheese*\""), expressions("Farmer's CHEESE"))
    }

    @Test fun `a plural also tries its singular, whole`() {
        assertEquals(
            listOf(
                "\"breaded\" \"chicken\" \"breasts*\"", "\"breaded\" \"chicken\" \"breast\"",
                "\"coated\" \"chicken\" \"breasts*\"", "\"coated\" \"chicken\" \"breast\"",
            ),
            expressions("breaded chicken breasts"),
        )
        assertEquals(listOf("\"chicken\" \"breasts\" \"fried*\"", "\"chicken\" \"breast\" \"fried*\""), expressions("chicken breasts fried"))
    }

    @Test fun `many plurals ask a bounded number of expressions, as typed first`() {
        val asked = expressions("beans peas oats rolls buns chips nuts dates")
        assertEquals(64, asked.size)
        assertEquals("\"beans\" \"peas\" \"oats\" \"rolls\" \"buns\" \"chips\" \"nuts\" \"dates*\"", asked.first())
    }

    @Test fun `a plural counts its singular in the name as whole`() {
        assertEquals(
            listOf("Chicken breast, fried, coated", "Chicken"),
            ranked("chicken breasts", "Chicken", "Chicken breast, fried, coated"),
        )
    }

    @Test fun `eggs finds egg before a shorter eggplant`() {
        assertEquals(listOf("Egg, whole, cooked", "Eggplant"), ranked("eggs", "Eggplant", "Egg, whole, cooked"))
    }

    @Test fun `nothing typed reaches the index as its syntax`() {
        assertEquals(listOf("\"milk\" \"or\" \"egg*\""), expressions("milk OR egg"))
        assertEquals(listOf("\"milk\" \"not\" \"egg*\""), expressions("milk NOT egg"))
        assertEquals(listOf("\"name\" \"milk*\""), expressions("name:milk"))
        assertEquals(listOf("\"milk*\""), expressions("\"milk\"*"))
        assertEquals(listOf("\"milk\" \"egg*\""), expressions("(milk) -egg"))
        assertEquals(listOf("\"near\" \"3\" \"milk*\""), expressions("NEAR/3 milk"))
        assertEquals(listOf("\"milk\" \"2\" \"8\""), expressions("milk 2.8%"))
    }

    @Test fun `a text with no word asks nothing`() {
        assertNull(usdaQuery("", aliases))
        assertNull(usdaQuery("  %. *\"()", aliases))
    }

    @Test fun `the script rule, Cyrillic or mixed input asks nothing`() {
        assertNull(usdaQuery("млеко", aliases))
        assertNull(usdaQuery("milk млеко", aliases))
        assertNull(usdaQuery("mилk", aliases))
    }

    @Test fun `a number alone may ask`() {
        assertEquals(listOf("\"2\""), expressions("2"))
    }

    @Test fun `a word in the name ranks above a word only in the additional descriptions`() {
        assertEquals(
            listOf("Long name with the word chicken in it", "Short"),
            ranked("chicken", "Short", "Long name with the word chicken in it"),
        )
    }

    @Test fun `the shorter name ranks first`() {
        assertEquals(listOf("Egg, raw", "Egg, whole, raw"), ranked("egg", "Egg, whole, raw", "Egg, raw"))
    }

    @Test fun `egg finds egg before a shorter eggplant`() {
        assertEquals(
            listOf("Egg, whole, cooked, raw", "Eggplant", "Omelet"),
            ranked("egg", "Omelet", "Eggplant", "Egg, whole, cooked, raw"),
        )
    }

    @Test fun `an alias in the name counts as the typed word`() {
        assertEquals(
            listOf("Chicken breast, fried, coated", "Chicken breast"),
            ranked("breaded chicken breast", "Chicken breast", "Chicken breast, fried, coated"),
        )
    }

    @Test fun `equal names keep one order`() {
        val query = usdaQuery("egg", aliases)!!
        val first = UsdaCandidate(1, "Egg")
        val second = UsdaCandidate(2, "Egg")
        assertEquals(listOf(first, second), query.rank(listOf(second, first)))
    }
}
