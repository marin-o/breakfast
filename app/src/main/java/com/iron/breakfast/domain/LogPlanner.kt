package com.iron.breakfast.domain

/**
 * One comma segment resolved the way the Log saves it: the best-ranked food, the typed or default amount and unit,
 * and the nutrition for them. [ranked] is every matching food, best first, so [food] is always `ranked.first()`.
 * [needsChoice] marks a weak typo or spelling match: it is offered as a choice and is not logged until picked.
 */
data class SegmentResolution(
    val raw: String,
    val ranked: List<RankedFood>,
    val food: FoodRecord,
    val amount: Double,
    val unit: MeasureUnit,
    val calculation: Calculation,
    val needsChoice: Boolean = false,
) {
    val supported: Boolean get() = calculation.supported
}

/** What Add logs for one input: the segments that save, in order, and the raw segments that do not. */
data class LogPlan(val entries: List<SegmentResolution>, val unresolved: List<String>)

/** A typed part as the library reads it: the food text, and the amount and unit typed (null when none). */
data class TypedPart(val foodPhrase: String, val amount: Double?, val unit: MeasureUnit?)

/**
 * The single rule for turning typed text into foods. [save] and the Log page both use it, so the preselected
 * suggestion, the add-bar sum and the saved entries always agree. Pure: it depends only on the matcher and prefs.
 * Results are cached per planner, so one Log draft resolves each part once.
 */
class LogPlanner(
    private val matcher: FoodMatcher,
    private val preferences: Map<String, Preference> = emptyMap(),
    private val calculator: NutritionCalculator = NutritionCalculator(),
    private val parser: ShorthandParser = ShorthandParser(),
) {
    /** How one part reads: what the food is matched on, and whether the amount came from the food's own name. */
    private class Reading(val parsed: ParsedSegment, val wholeName: Boolean) {
        val phrase: String get() = if (wholeName) parsed.raw else parsed.foodPhrase
    }

    private val resolved = HashMap<String, SegmentResolution?>()
    private val coverCache = HashMap<String, Boolean>()

    /** Resolves one segment. Null when no food matches. An unsupported unit still resolves; check [SegmentResolution.supported]. */
    fun resolve(raw: String): SegmentResolution? {
        if (resolved.containsKey(raw)) return resolved[raw]
        return computeResolution(raw).also { resolved[raw] = it }
    }

    private fun computeResolution(raw: String): SegmentResolution? {
        val reading = reading(raw)
        // A word separator left inside one part ("Ben & Jerry's") names one food only when one food covers all of it.
        if (parser.hasWordSeparator(raw) && !matcher.covers(reading.phrase)) return null
        val ranked = matcher.suggestions(reading.phrase, preferences, SUGGESTION_LIMIT)
        val top = ranked.firstOrNull() ?: return null
        val (amount, unit) = quantityFor(reading, top.food)
        val needsChoice = top.weakMatch && (ranked.size > 1 || top.firstLetterEdit)
        return SegmentResolution(raw, ranked, top.food, amount, unit, calculator.calculate(top.food, amount, unit), needsChoice)
    }

    private fun reading(raw: String): Reading {
        val parsed = parser.parse(raw)
        // A part that is exactly a food's name carries no amount: "Protein 2 bar" is one bar, not two. A number word or
        // article is part of the name when a food covers the whole part: "Ten grain bread" is one food.
        val wholeName = matcher.isExactName(raw) || (parsed.wordQuantity && matcher.covers(raw))
        return Reading(parsed, wholeName)
    }

    /** The amount and unit a matched [food] takes from [reading]: the typed ones, else the food's defaults. */
    private fun quantityFor(reading: Reading, food: FoodRecord): Pair<Double, MeasureUnit> {
        if (reading.wholeName) return food.defaultAmount to food.defaultUnit
        val parsed = reading.parsed
        // A bare "a" or "an" means one only when the food is counted by the piece ("a banana"); "a milk" is the default.
        val amount = parsed.amount
            ?: if (parsed.articleQuantity && food.defaultUnit == MeasureUnit.PIECE) 1.0 else food.defaultAmount
        return amount to (parsed.unit ?: food.defaultUnit)
    }

    /** The calories Add would log for [raw] matched to [food]. The Log's choices show exactly this. */
    fun calculationFor(raw: String, food: FoodRecord): Calculation {
        val reading = reading(raw)
        val (amount, unit) = quantityFor(reading, food)
        return calculator.calculate(food, amount, unit)
    }

    /** The food text the Log shows for [raw]: the whole part when it is a name, else the part without its amount. */
    fun phraseOf(raw: String): String = reading(raw).phrase

    /** A typed part as the library reads it. A name that carries its own number has no typed amount. */
    fun typed(raw: String): TypedPart {
        val reading = reading(raw)
        return if (reading.wholeName) TypedPart(reading.phrase, null, null)
        else TypedPart(reading.parsed.foodPhrase, reading.parsed.amount, reading.parsed.unit)
    }

    /**
     * The text that replaces a typed part when a choice is picked: the amount and unit Add would log, then the food's
     * name. Re-resolving it gives the same food, amount and unit the choice showed.
     */
    fun chosenText(raw: String, food: FoodRecord): String {
        val (amount, unit) = quantityFor(reading(raw), food)
        return listOfNotNull(formatAmount(amount), unit.displayLabel().ifBlank { null }, food.name).joinToString(" ")
    }

    private fun formatAmount(amount: Double): String =
        if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()

    private val probe = object : FoodProbe {
        override fun resolves(text: String): Boolean {
            val resolution = resolve(text)
            return resolution != null && !resolution.needsChoice
        }

        override fun covers(phrase: String): Boolean = coverCache.getOrPut(phrase) { matcher.covers(phrase) }
        override fun isExactName(phrase: String): Boolean = matcher.isExactName(phrase)
    }

    /** The parts of [input] the Log page works with, meal words included. Every caller segments through here. */
    fun segments(input: String): List<SegmentSpan> = parser.parts(input, probe)

    /** True when the input ends with a separator, so the last part is finished and nothing is being typed. */
    fun endsOpen(input: String): Boolean = parser.endsOpen(input)

    /**
     * Every part of [input], resolved; parts that match nothing, have no conversion or need a choice are unresolved.
     * Meal words are not foods. Add saves exactly this plan, so the rows, the sum and the save always agree.
     */
    fun plan(input: String): LogPlan {
        val entries = mutableListOf<SegmentResolution>()
        val unresolved = mutableListOf<String>()
        parser.split(input, probe).forEach { raw ->
            if (ShorthandLexicon.mealSlotOf(raw) != null) return@forEach
            val resolution = resolve(raw)
            if (resolution != null && resolution.supported && !resolution.needsChoice) entries += resolution else unresolved += raw
        }
        return LogPlan(entries, unresolved)
    }

    companion object {
        /** How many matches the Log page offers as choices. The preselected one is always the first. */
        const val SUGGESTION_LIMIT = 4
    }
}
