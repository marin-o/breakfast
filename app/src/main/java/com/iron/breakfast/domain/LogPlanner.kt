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

/** The Log draft after a choice is tapped: the new [input], and the [part] text that names the tapped food, if any. */
data class ChosenDraft(val input: String, val part: String?)

/** A typed part as the library reads it: the food text, and the amount and unit typed (null when none). */
data class TypedPart(val foodPhrase: String, val amount: Double?, val unit: MeasureUnit?)

/** One line of the Log draft, in the order the parts were typed. */
sealed interface DraftRow {
    /** A part that logs: shown with its food name, amount and kcal. */
    data class Entry(val resolution: SegmentResolution) : DraftRow

    /** A typed part that does not log: shown as "Not in your foods". [start] and [end] are its span in the input. */
    data class Unresolved(val text: String, val start: Int = -1, val end: Int = -1) : DraftRow
}

/**
 * What the Log page shows for one draft. [rows] are the entries and unresolved parts in typed order. The part being
 * typed may add a choice group ([choices], from [active]) or suggestions ([suggestions], the foods its word starts).
 * [logged] is exactly what Add saves. When Add saves the part being typed ([counted]), its food is the first of the
 * group and shows selected; when Add does not save it, nothing in the group is selected.
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
    /** The part being typed as Add saves it (the last of [logged]); null when Add does not save it. */
    val counted: SegmentResolution? = null,
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

    /** The first choice shows selected exactly when Add saves the part being typed, and it is the food Add saves. */
    val preselected: Boolean get() = counted != null && choices.firstOrNull()?.food?.id == counted.food.id

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
    /** Parts the user picked in the library, by the part text they now read as. Each one resolves to its food. */
    private val pins: Map<String, FoodRecord> = emptyMap(),
    /**
     * "Count what I'm typing" (P9). True: the part being typed counts like a finished one. False: the add bar counts it
     * only once it is finished (a separator after it, Done or Add) or picked (a pinned choice). Add always finishes the
     * draft first, so it saves [plan] of the whole input in both modes.
     */
    private val countTyping: Boolean = true,
) {
    /** How one part reads: what the food is matched on, and whether the amount came from the food's own name. */
    private class Reading(val parsed: ParsedSegment, val wholeName: Boolean) {
        // A glued part reads as its spaced form, so a name it carries is matched spaced ("cow milk3.2" as "cow milk 3.2").
        val phrase: String get() = if (wholeName) parsed.spaced else parsed.foodPhrase
    }

    // The Log page resolves on a background thread while taps resolve on the main one.
    private val resolved: MutableMap<String, SegmentResolution?> = java.util.Collections.synchronizedMap(HashMap())
    private val coverCache: MutableMap<String, Boolean> = java.util.Collections.synchronizedMap(HashMap())
    private val wholeWordCache: MutableMap<String, Boolean> = java.util.Collections.synchronizedMap(HashMap())

    /** Resolves one segment. Null when no food matches. An unsupported unit still resolves; check [SegmentResolution.supported]. */
    fun resolve(raw: String): SegmentResolution? {
        if (resolved.containsKey(raw)) return resolved[raw]
        return computeResolution(raw).also { resolved[raw] = it }
    }

    private fun computeResolution(raw: String): SegmentResolution? {
        pins[raw]?.let { picked ->
            val (amount, unit) = quantityFor(readingFor(raw, picked), picked)
            val ranked = RankedFood(picked, score = 1.0, reasons = listOf("picked"), fullMatch = true, wholeMatch = true)
            return SegmentResolution(raw, listOf(ranked), picked, amount, unit, calculator.calculate(picked, amount, unit))
        }
        val reading = reading(raw)
        // A word separator left inside one part ("Ben & Jerry's") names one food only when one food covers all of it.
        if (parser.hasWordSeparator(raw) && !matcher.covers(reading.phrase)) return null
        val plain = resolutionOf(raw, reading, matcher.suggestions(reading.phrase, preferences, SUGGESTION_LIMIT))
        if (plain != null && plain.supported && !plain.needsChoice) return plain
        // Only when the part does not resolve as it reads: "2 portions chili" may be portions of a recipe. A food's
        // phrase is never read this way, so "2 portions rice" logs exactly as it did before recipes.
        val portions = portionReading(reading) ?: return plain
        val recipes = matcher.suggestions(portions.phrase, preferences, SUGGESTION_LIMIT).filter { takesPortions(it.food) }
        val recipe = resolutionOf(raw, portions, recipes)
        return if (recipe != null && recipe.supported && !recipe.needsChoice) recipe else plain
    }

    private fun resolutionOf(raw: String, reading: Reading, ranked: List<RankedFood>): SegmentResolution? {
        val top = ranked.firstOrNull() ?: return null
        val (amount, unit) = quantityFor(reading, top.food)
        // A partial match (not every identity token matches) is only a choice, as is a weak typo or spelling match.
        val needsChoice = !top.fullMatch || (top.weakMatch && (ranked.size > 1 || top.firstLetterEdit))
        return SegmentResolution(raw, ranked, top.food, amount, unit, calculator.calculate(top.food, amount, unit), needsChoice)
    }

    /**
     * [reading] as portions of a recipe: a part whose food text starts with "portion" or "portions" (and an optional
     * "of"), with no unit typed, reads as that many portions of the rest ("half portion chili", "a portion of chili").
     * Null for any other part. Only a recipe that has portions ([takesPortions]) is ever matched this way.
     */
    private fun portionReading(reading: Reading): Reading? {
        if (reading.wholeName || reading.parsed.unit != null) return null
        val match = PORTION_WORDS.matchEntire(reading.parsed.foodPhrase.trim()) ?: return null
        return Reading(reading.parsed.copy(foodPhrase = match.groupValues[1], unit = MeasureUnit.PORTION), wholeName = false)
    }

    /** How [raw] reads for [food]: as portions when [food] is a recipe with portions and the part names them, else as it reads. */
    private fun readingFor(raw: String, food: FoodRecord): Reading {
        val reading = reading(raw)
        return if (takesPortions(food)) portionReading(reading) ?: reading else reading
    }

    /** A recipe with portions: the only thing a "portion" is ever counted for. */
    private fun takesPortions(food: FoodRecord): Boolean = isRecipeId(food.id) &&
        (food.nutrition.unit == MeasureUnit.PORTION || food.conversions.any { it.unit == MeasureUnit.PORTION })

    private fun reading(raw: String): Reading {
        val parsed = parser.parse(raw, probe)
        // A part that is exactly a food's name carries no amount: "Protein 2 bar" is one bar, not two. A number word or
        // article is part of the name when a food covers the whole part: "Ten grain bread" is one food.
        // A glued part ("cow milk3.2") reads exactly like its spaced form, its name included.
        val wholeName = matcher.isExactName(raw) || matcher.isExactName(parsed.spaced) ||
            (parsed.wordQuantity && (matcher.covers(raw) || matcher.covers(parsed.spaced)))
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
        val reading = readingFor(raw, food)
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
        val (amount, unit) = quantityFor(readingFor(raw, food), food)
        return partText(food, amount, unit)
    }

    /** A part that reads back as [amount] [unit] of [food]: "500 g Beef mince", "1 portion Chili". */
    fun partText(food: FoodRecord, amount: Double, unit: MeasureUnit): String =
        listOfNotNull(formatAmount(amount), unit.displayLabel().ifBlank { null }, food.name).joinToString(" ")

    /** [input] with [part] after its last part: after a comma, or as typed when the input already ends in a separator. */
    fun withPartAdded(input: String, part: String): String {
        val head = input.trimEnd()
        return when {
            head.isEmpty() -> part
            endsOpen(head) -> "$head $part"
            else -> "$head, $part"
        }
    }

    /** The query the library opens with for [raw]: the food text, without its amount ("kiselo mleko 200g" is "kiselo mleko"). */
    fun libraryQueryFor(raw: String): String = typed(raw).foodPhrase

    /**
     * The part text that replaces [raw] once the library returns [food]: the food's name, then the amount text the user
     * typed for the part ("kiselo mleko 200g" with Kiselo mleko reads "Kiselo mleko 200g"). Without a typed amount, the name.
     */
    fun pickedText(raw: String, food: FoodRecord): String {
        // A part that is a name carries no amount of its own, glued or not.
        if (reading(raw).wholeName) return food.name
        val query = libraryQueryFor(raw)
        val amountText = if (query.isBlank()) "" else raw.replaceFirst(query, "", ignoreCase = true).trim()
        return listOf(food.name, amountText).filter { it.isNotBlank() }.joinToString(" ")
    }

    /**
     * The input after the user taps [food] for the part being typed. The chosen text replaces that part; when text
     * comes before it, a comma keeps the chosen amount from joining the earlier food ("2 eggs, 100 g Buckwheat").
     */
    fun withChoice(input: String, food: FoodRecord, typing: Boolean = true): String = choose(input, food, typing).input

    /**
     * [withChoice], and the part text that now names [food] (null when the input is unchanged). Pinning that text to
     * [food] keeps the tapped food: re-resolving the chosen text alone may rank another food first.
     */
    fun choose(input: String, food: FoodRecord, typing: Boolean = true): ChosenDraft {
        if (!typing || endsOpen(input)) return ChosenDraft(input, null)
        val span = segments(input).lastOrNull() ?: return ChosenDraft(input, null)
        if (ShorthandLexicon.mealSlotOf(span.text) != null) return ChosenDraft(input, null)
        val text = chosenText(span.text, food)
        return ChosenDraft(joinBefore(input.substring(0, span.start), text), text)
    }

    private fun joinBefore(prefix: String, replacement: String): String {
        val head = prefix.trimEnd()
        if (head.isEmpty()) return replacement
        // The text before already ends in a separator (the parser's rule): the chosen text joins it as typed.
        if (endsOpen(head)) return joinDraft(prefix, replacement)
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

        override fun matchesWholeWord(word: String): Boolean = wholeWordCache.getOrPut(word) { matcher.matchesWholeWord(word) }
    }

    /** The parts of [input] the Log page works with, meal words included. Every caller segments through here. */
    fun segments(input: String): List<SegmentSpan> = parser.parts(input, probe)

    /** True when the input ends with a separator (or a space), so the last part is finished and nothing is being typed. */
    fun endsOpen(input: String): Boolean = parser.endsOpen(input)

    /**
     * Every part of [input], resolved; parts that match nothing, have no conversion or need a choice are unresolved.
     * Meal words are not foods. Add saves exactly this plan, so the rows, the sum and the save always agree.
     */
    fun plan(input: String): LogPlan = planOf(parser.split(input, probe))

    /**
     * What the add bar counts for [input] while it is [typing]: [plan] of [countedParts]. With [countTyping] on this is
     * [plan]; off, a part still being typed is left out (see [heldPart]). Not typing, it is [plan]: what Add saves.
     */
    fun plan(input: String, typing: Boolean): LogPlan = planOf(countedParts(input, typing))

    /** The parts Add plans for [input] while [typing]: every part, except one still being typed when it does not count. */
    fun countedParts(input: String, typing: Boolean): List<String> {
        val parts = parser.split(input, probe)
        return if (heldPartOf(input, typing, parts) != null) parts.dropLast(1) else parts
    }

    /** The part still being typed that the add bar leaves out ([countTyping] off and neither finished nor picked); else null. */
    fun heldPart(input: String, typing: Boolean): String? = heldPartOf(input, typing, null)

    private fun heldPartOf(input: String, typing: Boolean, known: List<String>?): String? {
        if (countTyping || !typing || input.isBlank() || endsOpen(input)) return null
        val last = (known ?: parser.split(input, probe)).lastOrNull() ?: return null
        return last.takeUnless { pins.containsKey(it) || ShorthandLexicon.mealSlotOf(it) != null }
    }

    private fun planOf(parts: List<String>): LogPlan {
        val entries = mutableListOf<SegmentResolution>()
        val unresolved = mutableListOf<String>()
        parts.forEach { raw ->
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
        val finished = (if (open) parts.dropLast(1) else parts).filter { ShorthandLexicon.mealSlotOf(it.text) == null }
        val rows = finished.map { rowFor(it) }.toMutableList()

        var active: SegmentResolution? = null
        var suggestions: List<RankedFood> = emptyList()
        var counted: SegmentResolution? = null
        if (activeText.isNotBlank() && ShorthandLexicon.mealSlotOf(activeText) == null) {
            val resolution = resolve(activeText)
            // Add saves the part being typed by the same rule as every other part (see [plan]), unless it is held back
            // until it is finished or picked ("Count what I'm typing" off).
            val counts = countTyping || pins.containsKey(activeText)
            counted = resolution?.takeIf { counts && it.supported && !it.needsChoice }
            // Each outcome renders the part exactly once: a suggestion group, a choice group, an entry row, or a
            // Not-in-your-foods row.
            when {
                // Nothing matches the word being typed, only a prefix of a longer word does, or a bare word names
                // several foods: suggest the foods it starts. When Add saves the word, the food it saves leads.
                resolution == null || (isBareWord(activeText) && (!resolution.ranked.first().wholeMatch || resolution.ranked.size > 1)) -> {
                    val starting = matcher.startingWith(activeText, preferences, SUGGESTION_LIMIT)
                    suggestions = counted?.let { saved ->
                        (listOf(saved.ranked.first()) + starting.filterNot { it.food.id == saved.food.id }).take(SUGGESTION_LIMIT)
                    } ?: starting
                }
                // A partial or weak match is a choice group only; the group is the part's rendering, not a second row.
                resolution.needsChoice -> active = resolution
                // A part with no usable conversion is a Not-in-your-foods row.
                !resolution.supported -> parts.last().let { rows += DraftRow.Unresolved(activeText, it.start, it.end) }
                // Not counted yet: the clean single candidate is offered to pick, shown unselected, never as a row.
                !counts && resolution.ranked.size == 1 -> suggestions = resolution.ranked
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
            logged = plan(input, typing).entries,
            activePhrase = if (activeText.isBlank()) "" else stripArticle(phraseOf(activeText)),
            suggestions = suggestions,
            counted = counted,
        )
    }

    private fun rowFor(span: SegmentSpan): DraftRow {
        val resolution = resolve(span.text)
        return if (resolution != null && resolution.supported && !resolution.needsChoice) DraftRow.Entry(resolution)
        else DraftRow.Unresolved(span.text, span.start, span.end)
    }

    /** One bare word with no quantity: the form a name is typed in, before it is finished. */
    private fun isBareWord(text: String): Boolean =
        text.trim().split(WHITESPACE).size == 1 && text.any { it.isLetter() } && parser.parse(text, probe).let { it.amount == null && !it.wordQuantity }

    /** A leading "a" or "an" is not part of the food the user named: "a bananna" reads "bananna". */
    private fun stripArticle(text: String): String = text.replace(LEADING_ARTICLE, "")

    companion object {
        /** How many matches the Log page offers as choices or suggestions. The preselected one is always the first. */
        const val SUGGESTION_LIMIT = 4
        private val WHITESPACE = Regex("\\s+")
        private val LEADING_ARTICLE = Regex("(?iu)^(?:a|an)\\s+")
        /** "portion chili", "portions of chili": the food text of a part that may name a recipe's portions. */
        private val PORTION_WORDS = Regex("(?iu)^portions?(?:\\s+of)?\\s+(.+)$")
    }
}

/** [input] with the part at [start] until [end] replaced by [text]; every other part keeps its text and place. */
fun replaceDraftPart(input: String, start: Int, end: Int, text: String): String =
    joinDraft(joinDraft(input.substring(0, start), text), input.substring(end))

/**
 * [left] then [right], with a space after a comma that would otherwise sit between two digits and read as a decimal:
 * "2," then "1 piece Banana" is "2, 1 piece Banana", never 2.1.
 */
internal fun joinDraft(left: String, right: String): String = when {
    left.endsWith(',') && left.getOrNull(left.length - 2)?.isDigit() == true && right.firstOrNull()?.isDigit() == true -> "$left $right"
    left.lastOrNull()?.isDigit() == true && right.startsWith(',') && right.getOrNull(1)?.isDigit() == true -> "$left, ${right.drop(1)}"
    else -> left + right
}

/** What Add saves for the Log's [input]: the same plan the draft's sum uses. Done and Add never open library search. */
internal fun draftLogPlan(input: String, planner: LogPlanner): LogPlan = planner.plan(input)
