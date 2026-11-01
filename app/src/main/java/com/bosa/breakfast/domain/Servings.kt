package com.bosa.breakfast.domain

import kotlinx.serialization.Serializable
import java.util.Locale

// Measurements: a food's named servings, the weight and volume units, and its density (docs/measurements-spec.md).

/** The name a serving row in [unit] has without a label of its own: "piece", "tbsp", "tsp"; null for any other unit. */
fun defaultServingName(unit: MeasureUnit): String? = when (unit) {
    MeasureUnit.PIECE -> "piece"
    MeasureUnit.TABLESPOON -> "tbsp"
    MeasureUnit.TEASPOON -> "tsp"
    else -> null
}

/**
 * What an amount counts: a unit, or one of a food's named servings ([MeasureUnit.SERVING] with its [name]). For a unit
 * the shorthand converted ("kg" as g) [name] is that unit's label, shown, never matched.
 */
@Serializable
data class Measure(val unit: MeasureUnit, val name: String? = null) {
    /** How a unit chip and a typed amount read it: the serving's name, else the unit's label. */
    val label: String get() = if (unit == MeasureUnit.SERVING) name.orEmpty() else unit.label
}

/** The ml one of [unit] is, for the volume units with a fixed size; null for a unit that is not one. */
internal fun millilitresIn(unit: MeasureUnit): Double? = when (unit) {
    MeasureUnit.MILLILITER -> 1.0
    MeasureUnit.TEASPOON -> 5.0
    MeasureUnit.TABLESPOON -> 15.0
    MeasureUnit.CUP -> 240.0
    else -> null
}

/** A serving that is the food itself on its basis, not a serving: in the basis unit and with no name. */
private fun ServingConversion.isServing(): Boolean = name != null

/**
 * True when [typed] names the serving [name]: the same word in any case, singular or plural, within one script (Latin
 * only matches Latin, Cyrillic only Cyrillic; a word that mixes them matches nothing).
 */
fun servingNameMatches(typed: String, name: String): Boolean {
    val a = typed.trim().lowercase(Locale.ROOT)
    val b = name.trim().lowercase(Locale.ROOT)
    if (a.isEmpty() || b.isEmpty()) return false
    val script = scriptOf(a)
    if (script == TextScript.NONE || !sameScript(script, scriptOf(b))) return false
    return singularForms(a).any { it in singularForms(b) }
}

/** A word and the singular forms it may be: "slices" is "slice", "berries" "berry", "glasses" "glass". Latin only. */
internal fun singularForms(word: String): Set<String> {
    if (scriptOf(word) != TextScript.LATIN) return setOf(word)
    return buildSet {
        add(word)
        if (word.endsWith("ies") && word.length > 4) add(word.dropLast(3) + "y")
        if (word.endsWith("es") && word.length > 3) add(word.dropLast(2))
        if (word.endsWith("s") && !word.endsWith("ss") && word.length > 2) add(word.dropLast(1))
    }
}

/**
 * The singular of a printed plural, chosen deliberately: "berries" is "berry" and "cookies" "cookie", "peaches" "peach",
 * "glasses" "glass", "slices" "slice", "bars" "bar". A word it cannot tell ("tomatoes", "candies") is kept as printed:
 * a spelling is never invented.
 */
internal fun printedSingular(word: String): String {
    if (scriptOf(word) != TextScript.LATIN) return word
    val lower = word.lowercase(Locale.ROOT)
    return when {
        lower.endsWith("ies") && lower.length > 4 -> {
            val stem = lower.dropLast(3)
            when {
                // A doubled consonant before it: "berries", "cherries", "patties".
                stem.length >= 2 && stem.last() == stem[stem.length - 2] && stem.last() !in VOWELS -> stem + "y"
                // Words that end in "ie": "cookies", "pies", "brownies".
                (stem + "ie") in IE_WORDS -> stem + "ie"
                else -> word
            }
        }
        listOf("ches", "shes", "xes", "sses").any(lower::endsWith) -> word.dropLast(2)
        lower.endsWith("oes") || lower.endsWith("ss") || lower.endsWith("us") || lower.endsWith("is") -> word
        lower.endsWith("s") && lower.length > 2 -> word.dropLast(1)
        else -> word
    }
}

private const val VOWELS = "aeiou"

/** Singulars that end in "ie", whose plural a rule cannot tell from a "-y" word's. */
private val IE_WORDS = setOf("cookie", "pie", "brownie", "smoothie", "veggie", "hoagie", "rotie", "tie", "pastie")

/**
 * [name] for [amount] of it: "slice", "2 slices", "1.5 cans"; a Cyrillic or already plural name is kept as it is. What
 * follows a comma or bracket only describes it and stays: "2 cups, cooked, diced", "2 bottles (8 fl oz)".
 */
fun servingNameFor(name: String, amount: Double): String {
    if (amount == 1.0 || scriptOf(name) != TextScript.LATIN) return name
    val head = name.takeWhile { it != ',' && it != '(' }.trimEnd()
    val description = name.removePrefix(head)
    val lower = head.lowercase(Locale.ROOT)
    val plural = when {
        lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("sh") || lower.endsWith("ch") -> if (lower.endsWith("s")) head else head + "es"
        lower.endsWith("y") && lower.length > 1 && lower[lower.length - 2] !in "aeiou" -> head.dropLast(1) + "ies"
        else -> head + "s"
    }
    return plural + description
}

/** The serving of [food] that [typed] names ([servingNameMatches]); null when it has none by that name. */
fun FoodRecord.servingNamed(typed: String): ServingConversion? =
    conversions.firstOrNull { it.isServing() && servingNameMatches(typed, it.name!!) }

/**
 * The serving a bare count of this food counts ("2 eggs", "2 chicken breast"), or null when none counts whole items: one
 * named by a word of [typed], else any; of several, the one that is only that word ("breast"), then a medium or regular
 * one ("medium", "medium breast"), then the first. A cup, a fl oz and the like measure an amount and count nothing.
 */
internal fun FoodRecord.countedServing(typed: String): ServingConversion? {
    val whole = conversions.filter { it.name?.let(::countsWholeItems) == true }
    val words = typed.split(NON_LETTER).filter { it.isNotEmpty() }
    val named = whole.filter { serving -> servingHead(serving.name!!).split(' ').any { head -> words.any { servingNameMatches(it, head) } } }
    return named.firstOrNull { serving -> words.any { servingNameMatches(it, servingHead(serving.name!!)) } }
        ?: named.ifEmpty { whole }.let { list -> list.firstOrNull { MIDDLE_SIZE.containsMatchIn(it.name!!) } ?: list.firstOrNull() }
}

/**
 * What a bare count of this food means, when its default would not do: a USDA food is on grams, so "2 eggs" as 2 g would
 * log nothing, silently. It counts [countedServing]; with none, pieces, which it has no value for, so the part says so
 * and doesn't log. Null for any other food, and for one whose default the user moved off its basis.
 */
internal fun FoodRecord.bareCountMeasure(typed: String): Measure? {
    if (!isUsdaFoodId(id) || defaultUnit != nutrition.unit) return null
    return articleMeasure(typed) ?: Measure(MeasureUnit.PIECE)
}

/** What an "a" or "an" before this food counts: as [bareCountMeasure], but with no serving to count "a milk" stays the default. */
internal fun FoodRecord.articleMeasure(typed: String): Measure? {
    if (!isUsdaFoodId(id) || defaultUnit != nutrition.unit) return null
    return countedServing(typed)?.let { typedMeasure(MeasureUnit.SERVING, it.name) }
}

private fun countsWholeItems(name: String): Boolean = name.firstOrNull()?.isDigit() != true && !AMOUNT_SERVING.containsMatchIn(name)

/** A serving's name without what describes it: "breast" of "breast, NS as to size", "can or bottle" of "can or bottle (12 fl oz)". */
private fun servingHead(name: String): String = name.takeWhile { it != ',' && it != '(' }.trim()

private val NON_LETTER = Regex("[^\\p{L}]+")
private val MIDDLE_SIZE = Regex("(?iu)^(?:medium|regular)\\b")
private val AMOUNT_SERVING = Regex("(?iu)^(?:cups?|fl oz|fluid ounces?|oz|ounces?|lbs?|pounds?|tablespoons?|tbsp|teaspoons?|tsp|g|grams?|kg|ml|liters?|litres?|pints?|quarts?|gallons?|(?:cubic|linear) inch(?:es)?)\\b")

/**
 * The amount and serving [text] is as `LogPlanner.partText` writes them, the serving's own commas kept: "2 cups, cooked,
 * diced" with a serving "cup, cooked, diced". Null when it is not that.
 */
internal fun FoodRecord.writtenServing(text: String): Pair<Double, String>? {
    val head = text.trim()
    val amount = head.substringBefore(' ').toDoubleOrNull()?.takeIf(::isPositiveAmount) ?: return null
    val written = head.substringAfter(' ', "").trim()
    val name = conversions.firstNotNullOfOrNull { serving ->
        serving.name?.takeIf { servingNameFor(it, amount).equals(written, ignoreCase = true) }
    } ?: return null
    return amount to name
}

/**
 * What [unit] (typed as [word], when typed) counts for this food: a serving named [word] when it has one, kept in its
 * own unit when that is the serving's unit's own name ("tbsp", "piece"); else [unit], labelled [label] when the
 * shorthand converted it ("kg"). A [MeasureUnit.SERVING] named [word] the food lacks counts for nothing.
 */
fun FoodRecord.typedMeasure(unit: MeasureUnit, word: String?, label: String? = null): Measure {
    word?.let(::servingNamed)?.let { serving ->
        val name = serving.name!!
        return if (name == defaultServingName(serving.unit)) Measure(serving.unit) else Measure(MeasureUnit.SERVING, name)
    }
    if (unit == MeasureUnit.SERVING) return Measure(MeasureUnit.SERVING, word)
    return Measure(unit, label)
}

/**
 * The measures an amount sheet and an entry edit offer for this food: its default unit and basis, the units its own
 * conversions are in, the other of g and ml when it has a density, then its named servings. A food with nothing extra
 * offers what it always did.
 */
fun FoodRecord.measures(): List<Measure> {
    val units = listOf(defaultUnit, nutrition.unit) +
        conversions.filter { (it.name == null || it.name == defaultServingName(it.unit)) && !crossesFamily(this, it.unit) }.map { it.unit } +
        listOfNotNull(density?.let { otherFamily(nutrition.unit) })
    val named = conversions.mapNotNull { serving -> serving.name?.takeIf { it != defaultServingName(serving.unit) } }
        .distinctBy { it.lowercase(Locale.ROOT) }
    return units.filter { it != MeasureUnit.UNKNOWN && it != MeasureUnit.SERVING }.distinct().map { Measure(it) } +
        named.map { Measure(MeasureUnit.SERVING, it) }
}

/** [unit] is g on a food on ml, or ml on one on g: only its density crosses that, never a serving row. */
internal fun crossesFamily(food: FoodRecord, unit: MeasureUnit): Boolean = otherFamily(food.nutrition.unit) == unit

/** g for a food on ml and ml for one on g: the family a density opens; null for another basis. */
private fun otherFamily(basis: MeasureUnit): MeasureUnit? = when (basis) {
    MeasureUnit.GRAM -> MeasureUnit.MILLILITER
    MeasureUnit.MILLILITER -> MeasureUnit.GRAM
    else -> null
}

/**
 * The serving a product's printed size reads as, in its basis unit [basis] (g or ml), or null when it does not read
 * clearly: "1 can (330 ml)" is can = 330 ml, "2 biscuits (25 g)" biscuit = 12.5 g, a bare "30 g" [fallbackName] = 30 g.
 * The bracketed or bare unit must be the basis; nothing is guessed.
 */
fun servingFromLabel(text: String?, basis: MeasureUnit, fallbackName: String): ServingConversion? {
    val printed = text?.trim()?.replace(',', '.') ?: return null
    NAMED_SIZE.matchEntire(printed)?.let { match ->
        val count = match.groupValues[1].toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
        val word = match.groupValues[2].trim().lowercase(Locale.ROOT)
        val amount = match.groupValues[3].toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
        if (sizeUnit(match.groupValues[4]) != basis) return null
        val script = scriptOf(word)
        if (script == TextScript.MIXED || script == TextScript.NONE || word.split(' ').size > 2) return null
        val name = if (count == 1.0) word else printedSingular(word)
        return ServingConversion(basis, amount / count, "Open Food Facts serving size", label = name)
    }
    BARE_SIZE.matchEntire(printed)?.let { match ->
        val amount = match.groupValues[1].toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
        if (sizeUnit(match.groupValues[2]) != basis) return null
        return ServingConversion(basis, amount, "Open Food Facts serving size", label = fallbackName)
    }
    return null
}

private fun sizeUnit(text: String): MeasureUnit? = when (text.lowercase(Locale.ROOT)) {
    "g" -> MeasureUnit.GRAM
    "ml" -> MeasureUnit.MILLILITER
    else -> null
}

/** "1 can (330 ml)", "2 biscuits (25 g)": a count, a name, and its size in brackets. */
private val NAMED_SIZE = Regex("(?iu)^(\\d+(?:\\.\\d+)?)\\s+(\\p{L}[\\p{L} ]*?)\\s*\\(\\s*(\\d+(?:\\.\\d+)?)\\s*(g|ml)\\s*\\)$")

/** "30 g", "250ml": a bare size. */
private val BARE_SIZE = Regex("(?iu)^(\\d+(?:\\.\\d+)?)\\s*(g|ml)$")

/**
 * [stored] with the [servings] it has no serving by that name for, or null when there is nothing to add or [stored] is
 * not exactly [expected] any more (gone, or edited since): a fetched serving never overwrites what the user made.
 */
internal fun withServingsAdded(stored: FoodRecord?, expected: FoodRecord, servings: List<ServingConversion>): FoodRecord? {
    if (stored == null || stored != expected) return null
    val missing = servings.filter { serving ->
        val name = serving.name ?: return@filter false
        stored.conversions.none { it.name?.equals(name, ignoreCase = true) == true }
    }
    return if (missing.isEmpty()) null else stored.copy(conversions = stored.conversions + missing)
}
