package com.bosa.breakfast.search

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

    @Test fun `the bump orders foods equal in their words, before the shorter name`() {
        val query = usdaQuery("egg", aliases)!!
        val common = UsdaCandidate(1, "Egg, whole, raw", bump = 1)
        val shorter = UsdaCandidate(2, "Egg, deviled")
        assertEquals(listOf(common, shorter), query.rank(listOf(shorter, common)))
    }

    @Test fun `the bump never lifts a food above one with more of the words in its name`() {
        val query = usdaQuery("egg", aliases)!!
        val eggplant = UsdaCandidate(1, "Eggplant", bump = 1)
        val egg = UsdaCandidate(2, "Egg, deviled, with a long name")
        assertEquals(listOf(egg, eggplant), query.rank(listOf(eggplant, egg)))
    }

    private val macedonian = mapOf(
        "јајце" to listOf("egg"), "пилешки" to listOf("chicken"), "гради" to listOf("breast"), "домат" to listOf("tomatoes"), "поховано" to listOf("breaded"),
        "пита" to listOf("pie", "pita"), "печено" to listOf("baked", "roasted"), "дроб" to listOf("liver"), "црн дроб" to listOf("liver"),
    )

    private fun asked(text: String) = usdaQuery(text, aliases, macedonian)?.matchExpressions()

    @Test fun `a Cyrillic word asks its English word, the last also as a prefix`() {
        assertEquals(listOf("\"egg*\""), asked("јајце"))
        assertEquals(listOf("\"chicken\" \"breast*\""), asked("пилешки гради"))
        assertEquals(listOf("\"egg*\""), asked("ЈАЈЦЕ"))
    }

    @Test fun `a translated word goes on through the plural and alias rules`() {
        assertEquals(listOf("\"tomatoes*\"", "\"tomato\"", "\"tomatoe\""), asked("домат"))
        assertEquals(listOf("\"breaded*\"", "\"coated\""), asked("поховано"))
    }

    @Test fun `a Cyrillic word with several English words finds any of them`() {
        assertEquals(listOf("\"pie*\"", "\"pita\""), asked("пита"))
        assertEquals(listOf("\"chicken\" \"baked*\"", "\"chicken\" \"roasted\""), asked("пилешки печено"))
        val query = usdaQuery("пита", aliases, macedonian)!!
        val found = listOf(UsdaCandidate(1, "Apple pie, frozen and long"), UsdaCandidate(2, "Bread, pita"))
        assertEquals(listOf("Bread, pita", "Apple pie, frozen and long"), query.rank(found).map { it.name })
    }

    @Test fun `two words the table holds together read as one, before each is read alone`() {
        assertEquals(listOf("\"liver*\""), asked("црн дроб"))
        assertEquals(listOf("\"liver*\""), asked("дроб"))
        assertEquals(listOf("\"chicken\" \"liver*\""), asked("пилешки црн дроб"))
        assertNull("the first word alone is not in the table", asked("црн"))
    }

    @Test fun `Cyrillic and Latin words may sit together, each in its own script`() {
        assertEquals(listOf("\"chicken\" \"breast*\""), asked("пилешки breast"))
    }

    @Test fun `a Cyrillic word the table lacks, or a word mixing scripts, finds nothing`() {
        assertNull(asked("млеко"))
        assertNull(asked("јајце млеко"))
        assertNull(asked("mилk"))
        assertNull(asked("јајцe"))
    }

    @Test fun `equal names keep one order`() {
        val query = usdaQuery("egg", aliases)!!
        val first = UsdaCandidate(1, "Egg")
        val second = UsdaCandidate(2, "Egg")
        assertEquals(listOf(first, second), query.rank(listOf(second, first)))
    }
}
