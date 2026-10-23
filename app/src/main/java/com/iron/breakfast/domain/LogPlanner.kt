package com.iron.breakfast.domain

/**
 * One comma segment resolved the way the Log saves it: the best-ranked food, the typed or default amount and unit,
 * and the nutrition for them. [ranked] is every matching food, best first, so [food] is always `ranked.first()`.
 * [needsChoice] marks a partial or weak match: it is offered as a choice and is not logged until picked.
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

/** One line of the Log draft, in the order the parts were typed. */
sealed interface DraftRow {
    /** A part that logs: shown with its food name, amount and kcal. */
    data class Entry(val resolution: SegmentResolution) : DraftRow

    /** A typed part that does not log: shown as "Not in your foods". */
    data class Unresolved(val text: String) : DraftRow
}

/**
 * What the Log page shows for one draft. [rows] are the entries and unresolved parts in typed order. The part being
 * typed may add a choice group ([choices], from [active]) or suggestions ([suggestions], the foods its word starts).
 * Neither suggestions nor unpicked choices are in [logged], and nothing is preselected in a suggestion group.
 */
data class DraftView(
    val rows: List<DraftRow>,
    val activeText: String,
    val active: SegmentResolution?,
    val logged: List<SegmentResolution>,
    /** The active part's text as the choice label shows it. */
    val activePhrase: String = "",
    /** Foods the word being typed starts; shown in place of a Not-in-your-foods outcome for the active part. */
    val suggestions: List<RankedFood> = emptyList(),
) {
    val previews: List<SegmentResolution> get() = rows.filterIsInstance<DraftRow.Entry>().map { it.resolution }
    val unresolved: List<String> get() = rows.filterIsInstance<DraftRow.Unresolved>().map { it.text }

    /** The active part when it is a clean match with exactly one candidate: an entry row, never a choice. */
    val activeRow: SegmentResolution? get() = active?.takeIf { it.supported && !it.needsChoice && it.ranked.size == 1 }

    /** The group under the rows: the suggestions for a word being typed, else the active part's choices. */
    val choices: List<RankedFood> get() = when {
        suggestions.isNotEmpty() -> suggestions
        active == null || activeRow != null -> emptyList()
        !active.needsChoice && active.ranked.size == 1 -> emptyList()
        else -> active.ranked
    }

    /** The "which one?" hint shows for real choices, not for suggestions. */
    val choiceHint: Boolean get() = suggestions.isEmpty()

    /** The first choice is preselected only for a clean match among several candidates, which Add logs. */
    val preselected: Boolean get() =
        suggestions.isEmpty() && active != null && active.supported && !active.needsChoice && active.ranked.size > 1

    companion object {
        val EMPTY = DraftView(emptyList(), "", null, emptyList())
    }
}

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
        // A partial match (not every identity token matches) is only a choice, as is a weak typo or spelling match.
        val needsChoice = !top.fullMatch || (top.weakMatch && (ranked.size > 1 || top.firstLetterEdit))
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

    /**
     * The input after the user taps [food] for the part being typed. The chosen text replaces that part; when text
     * comes before it, a comma keeps the chosen amount from joining the earlier food ("2 eggs, 100 g Buckwheat").
     */
    fun withChoice(input: String, food: FoodRecord, typing: Boolean = true): String {
        if (!typing || endsOpen(input)) return input
        val span = segments(input).lastOrNull() ?: return input
        if (ShorthandLexicon.mealSlotOf(span.text) != null) return input
        return joinBefore(input.substring(0, span.start), chosenText(span.text, food))
    }

    private fun joinBefore(prefix: String, replacement: String): String {
        val head = prefix.trimEnd()
        if (head.isEmpty()) return replacement
        if (head.last() in ",;+&\n" || SEPARATOR_WORD_AT_END.containsMatchIn(head)) return prefix + replacement
        return "$head, $replacement"
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

        override fun isWholeFood(text: String): Boolean {
            val resolution = resolve(text) ?: return false
            return resolution.supported && !resolution.needsChoice && resolution.ranked.first().wholeMatch
        }

        override fun matchesWholeWord(word: String): Boolean = matcher.matchesWholeWord(word)
    }

    /** The parts of [input] the Log page works with, meal words included. Every caller segments through here. */
    fun segments(input: String): List<SegmentSpan> = parser.parts(input, probe)

    /** True when the input ends with a separator (or a space), so the last part is finished and nothing is being typed. */
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

    /**
     * The Log page's view of [input]. While [typing], the last part is the one being typed, unless the text ends in a
     * separator. Finished parts are rows in typed order. A part being typed that matches nothing yet shows the foods
     * its word starts (or nothing); a part that is not being typed and matches nothing is an unresolved row.
     */
    fun draft(input: String, typing: Boolean = true): DraftView {
        if (input.isBlank()) return DraftView.EMPTY
        val parts = segments(input)
        val open = typing && !endsOpen(input)
        val activeText = if (open) parts.lastOrNull()?.text.orEmpty() else ""
        val finished = (if (open) parts.dropLast(1) else parts).map { it.text }.filter { ShorthandLexicon.mealSlotOf(it) == null }
        val rows = finished.map { rowFor(it) }.toMutableList()

        var active: SegmentResolution? = null
        var suggestions: List<RankedFood> = emptyList()
        // While the word is typed and only suggested, it is not counted: nothing is counted until a suggestion is tapped.
        var suggesting = false
        if (activeText.isNotBlank() && ShorthandLexicon.mealSlotOf(activeText) == null) {
            val resolution = resolve(activeText)
            // Each outcome renders the part exactly once: a suggestion group, a choice group, an entry row, or a
            // Not-in-your-foods row.
            when {
                // Nothing matches the word being typed, only a prefix of a longer word does, or a bare word names
                // several foods (it is not logged until one is chosen): suggest the foods it starts.
                resolution == null || (isBareWord(activeText) && (!resolution.ranked.first().wholeMatch || resolution.ranked.size > 1)) -> {
                    suggesting = true
                    suggestions = matcher.startingWith(activeText, preferences, SUGGESTION_LIMIT)
                }
                // A partial or weak match is a choice group only; the group is the part's rendering, not a second row.
                resolution.needsChoice -> active = resolution
                // A part with no usable conversion is a Not-in-your-foods row.
                !resolution.supported -> rows += DraftRow.Unresolved(activeText)
                // A clean single candidate is an entry row, never a choice.
                resolution.ranked.size == 1 -> {
                    rows += DraftRow.Entry(resolution)
                    active = resolution
                }
                else -> active = resolution
            }
        }
        return DraftView(
            rows = rows,
            activeText = activeText,
            active = active,
            logged = plan(input).entries,
            activePhrase = if (activeText.isBlank()) "" else stripArticle(phraseOf(activeText)),
            suggestions = suggestions,
        ).let { view ->
            if (suggesting) view.copy(logged = view.logged.filterNot { it.raw == activeText }) else view
        }
    }

    private fun rowFor(text: String): DraftRow {
        val resolution = resolve(text)
        return if (resolution != null && resolution.supported && !resolution.needsChoice) DraftRow.Entry(resolution)
        else DraftRow.Unresolved(text)
    }

    /** One bare word with no quantity: the form a name is typed in, before it is finished. */
    private fun isBareWord(text: String): Boolean =
        text.trim().split(Regex("\\s+")).size == 1 && text.any { it.isLetter() } && parser.parse(text).let { it.amount == null && !it.wordQuantity }

    /** A leading "a" or "an" is not part of the food the user named: "a bananna" reads "bananna". */
    private fun stripArticle(text: String): String = text.replace(Regex("(?i)^(?:a|an)\\s+"), "")

    companion object {
        /** How many matches the Log page offers as choices. The preselected one is always the first. */
        const val SUGGESTION_LIMIT = 4
        private val SEPARATOR_WORD_AT_END = Regex("(?i)(?:^|\\s)(?:and|и)$")
    }
}
