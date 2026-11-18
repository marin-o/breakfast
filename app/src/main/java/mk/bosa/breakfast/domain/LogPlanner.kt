package mk.bosa.breakfast.domain

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
    /** The serving's name for [MeasureUnit.SERVING], or the label of a unit the amount was converted from ("kg"). */
    val label: String? = null,
) {
    val supported: Boolean get() = calculation.supported

    /** Its food is known and the amount is real; only the unit has no number for that food. */
    val noValue: Boolean get() = !supported && !needsChoice && isPositiveAmount(amount)

    /** Its food is known but it cannot log: the unit has no number for that food ([noValue]), or the amount is not above 0. */
    val namedButCannotLog: Boolean get() = !supported && !needsChoice
}

/** What Add logs for one input: the segments that save, in order, and the raw segments that do not. */
data class LogPlan(val entries: List<SegmentResolution>, val unresolved: List<String>)

/** The Log draft after a choice is tapped: the new [input], and the [part] text that names the tapped food, if any. */
data class ChosenDraft(val input: String, val part: String?)

/** A typed part as the library reads it: the food text, and the amount and unit typed (null when none). */
data class TypedPart(
    val foodPhrase: String,
    val amount: Double?,
    val unit: MeasureUnit?,
    /** The word typed as [unit], which may be a serving's name ("slices"). */
    val unitWord: String? = null,
    /** The amount is a leading "a" or "an", which counts one only for a counted food. */
    val article: Boolean = false,
)

/** One line of the Log draft, in the order the parts were typed. */
sealed interface DraftRow {
    /** A part that logs: shown with its food name, amount and kcal. */
    data class Entry(val resolution: SegmentResolution) : DraftRow

    /** A typed part that does not log: shown as "Not in your foods". [start] and [end] are its span in the input. */
    data class Unresolved(
        val text: String,
        val start: Int = -1,
        val end: Int = -1,
        /** The food this part names when only its unit or amount stops it logging: neither ever changes the food. */
        val noValue: SegmentResolution? = null,
    ) : DraftRow
}

/** A [phrase] ("yogurt gre") for the catalogue, from the draft's parts at [start] until [end], which a pick of it replaces. */
data class CatalogueJoin(val phrase: String, val start: Int, val end: Int)


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
    /** What the USDA catalogue is asked while typing ([LogPlanner.partBeingTyped] without its amount); blank for nothing. */
    val catalogueQuery: String = "",
    /**
     * The names the part being typed may be the rest of, longest first, when no food of the library is: the USDA catalogue
     * is asked each, and the first with names that run on ([LogPlanner.runsOn]) replaces its span.
     */
    val catalogueJoins: List<CatalogueJoin> = emptyList(),
    /** The part being typed as Add saves it (the last of [logged]); null when Add does not save it. */
    val counted: SegmentResolution? = null,
    /** What the finished draft logs for the part being typed, whatever the add bar counts: the chip that goes green. */
    val addPick: SegmentResolution? = null,
    /** The parts that need a match, except one being typed that still has something to show. */
    val needsMatchParts: List<String> = emptyList(),
    /** Add would log something: the finished draft's plan, whatever the add bar counts while typing. */
    val addLogs: Boolean = false,
    /** A part has no food behind it, or local matches to choose from. */
    val needsMatch: Boolean = false,
    /** A part names its food but cannot log: a unit it has no value for, or an amount that is no amount. */
    val hasNoValue: Boolean = false,
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
    /**
     * How one part reads: what the food is matched on, whether the amount came from the food's own name, and a word read
     * as a serving's name where a unit goes ("cans" in "2 cans coke"), when it is read that way.
     */
    private class Reading(val parsed: ParsedSegment, val wholeName: Boolean, val servingWord: String? = null) {
        // A glued part reads as its spaced form, so a name it carries is matched spaced ("cow milk3.2" as "cow milk 3.2").
        val phrase: String get() = if (wholeName) parsed.spaced else parsed.foodPhrase
    }

    // The Log page resolves on a background thread while taps resolve on the main one.
    private val resolved: MutableMap<String, SegmentResolution?> = java.util.Collections.synchronizedMap(HashMap())
    private val coverCache: MutableMap<String, Boolean> = java.util.Collections.synchronizedMap(HashMap())
    private val wholeWordCache: MutableMap<String, Boolean> = java.util.Collections.synchronizedMap(HashMap())
    private val servingWordCache: MutableMap<String, Boolean> = java.util.Collections.synchronizedMap(HashMap())
    private val headServingCache: MutableMap<String, Boolean> = java.util.Collections.synchronizedMap(HashMap())

    /** Resolves one segment. Null when no food matches. An unsupported unit still resolves; check [SegmentResolution.supported]. */
    fun resolve(raw: String): SegmentResolution? {
        if (resolved.containsKey(raw)) return resolved[raw]
        return computeResolution(raw).also { resolved[raw] = it }
    }

    private fun computeResolution(raw: String): SegmentResolution? {
        pins[raw]?.let { picked -> return pickedResolution(raw, picked) }
        val reading = reading(raw)
        // A word separator left inside one part ("Ben & Jerry's") names one food only when one food covers all of it.
        if (parser.hasWordSeparator(raw) && !matcher.covers(reading.phrase)) return null
        val ranked = matcher.suggestions(reading.phrase, preferences, SUGGESTION_LIMIT)
        // The food the part names counts a serving of its own named where a unit goes, even a word of its name ("1 bar
        // Protein bar"), as every other path to a food reads it ([readingFor]). An exact name carries no amount.
        val plain = resolutionOf(raw, ranked.firstOrNull()?.let { servingReadingOf(reading, it.food) } ?: reading, ranked)
        if (plain != null && plain.supported && !plain.needsChoice) return plain
        // Only when the part does not resolve as it reads: a word where a unit goes may be a serving's name of the food
        // it names ("2 cans coke", "coke 2 cans"). A word that is only letters of the rest ("1 can Candybar") is no part
        // of the name, so it can only be a measure, and one the food lacks is not dropped.
        for (serving in servingReadings(reading)) {
            val word = serving.servingWord!!
            sizeTheFoodLacks(raw, reading, serving)?.let { return it }
            val flagged = matcher.hasServingNamed(word) || matcher.isLettersOfWordIn(word, serving.phrase)
            onTopFood(raw, serving, flagged) { it.servingNamed(word) != null }?.let { return it }
        }
        // Then "2 portions chili" may be portions of a recipe; a food the plain reading resolves is never read this way.
        val portions = portionReading(reading) ?: return plain
        return onTopFood(raw, portions, flagged = true, ::takesPortions) ?: plain
    }

    /**
     * A size word on a counted food ("1 large tomato") logs what a tap on that food logs: its ordinary unit, shown in the
     * row, or a serving that holds the word ("1 large bread"). Null for any other part.
     */
    private fun sizeTheFoodLacks(raw: String, reading: Reading, serving: Reading): SegmentResolution? {
        val word = serving.servingWord!!
        if (!isSizeWord(word)) return null
        val ranked = matcher.suggestions(serving.phrase, preferences, SUGGESTION_LIMIT)
        val top = ranked.firstOrNull()?.food ?: return null
        if (top.servingNamed(word) != null) return null
        return resolutionOf(raw, reading, ranked)?.takeIf { it.supported && !it.needsChoice && it.unit == MeasureUnit.SERVING }
    }

    /**
     * [reading], a measure word on the rest of the part, for the food ranked first: the word never chooses the food. A
     * food with that measure ([has]) logs by it. With [flagged] one without it is that food, unsupported: it does not log
     * and the word is not dropped ("No value for cans"). Null: this is no such reading.
     */
    private fun onTopFood(raw: String, reading: Reading, flagged: Boolean, has: (FoodRecord) -> Boolean): SegmentResolution? {
        val ranked = matcher.suggestions(reading.phrase, preferences, SUGGESTION_LIMIT)
        val top = ranked.firstOrNull()?.food ?: return null
        val resolution = resolutionOf(raw, reading, ranked)?.takeIf { !it.needsChoice } ?: return null
        return when {
            has(top) -> resolution.takeIf { it.supported }
            flagged -> resolution.takeIf { it.noValue }
            else -> null
        }
    }

    private fun pickedResolution(raw: String, picked: FoodRecord): SegmentResolution {
        val (amount, measure) = quantityFor(servingBeforeName(raw, picked) ?: amountAfterName(raw, picked) ?: readingFor(raw, picked), picked)
        val ranked = RankedFood(picked, score = 1.0, reasons = listOf("picked"), fullMatch = true, wholeMatch = true)
        return SegmentResolution(raw, listOf(ranked), picked, amount, measure.unit, calculate(picked, amount, measure), label = measure.name)
    }

    /** [raw] as [partText] writes a named serving of a picked food, whose commas are the serving's: "2 cups, cooked Rice, white". */
    private fun servingBeforeName(raw: String, picked: FoodRecord): Reading? {
        if (!raw.endsWith(" ${picked.name}")) return null
        val (amount, serving) = picked.writtenServing(raw.removeSuffix(picked.name)) ?: return null
        return Reading(ParsedSegment(raw, picked.name, amount, null, null, null), wholeName = false, servingWord = serving)
    }

    /**
     * [raw] as a picked food's name with the amount typed after it ("Extra virgin olive oil 200 g", "Bread 2 slices"),
     * whatever the words of its name would read as ("Protein 2 bar 200 g"). Null when it is not that.
     */
    private fun amountAfterName(raw: String, picked: FoodRecord): Reading? {
        if (!raw.startsWith("${picked.name} ")) return null
        val tail = parser.parse(raw.substring(picked.name.length + 1).trim())
        if (tail.amount == null) return null
        val word = tail.foodPhrase.trim()
        if (word.isNotEmpty() && (tail.unit != null || picked.servingNamed(word) == null)) return null
        return Reading(tail.copy(foodPhrase = picked.name), wholeName = false, servingWord = word.ifEmpty { null })
    }

    /**
     * Whether a pin on [food] still names [part], which is its name with [left] and [right] around it: an amount on one
     * side, and not the whole name of another food ("Milk 3.2" beside a pinned Milk).
     */
    internal fun pinFits(part: String, left: String, right: String, food: FoodRecord): Boolean {
        if (left.isEmpty() && right.isEmpty()) return true
        if (left.isNotEmpty() && right.isNotEmpty()) return false
        if (matcher.isExactName(part)) return false
        if (right.isEmpty() && food.writtenServing(left) != null) return true
        val typed = parser.parse(left + right)
        if (typed.amount == null) return false
        val word = typed.foodPhrase.trim()
        return word.isEmpty() || food.servingNamed(word) != null || (takesPortions(food) && PORTION_WORD.matches(word))
    }

    /** The food picked for this [occurrence] of the part [raw]: its own pin, else one for every part with that text. */
    private fun pinOf(raw: String, occurrence: Int): FoodRecord? = pins["$raw$PIN_OCCURRENCE$occurrence"] ?: pins[raw]

    /** The part [raw], the [occurrence]-th with that text: parts with one text may be pinned to different foods. */
    private fun resolvePart(raw: String, occurrence: Int): SegmentResolution? =
        pins["$raw$PIN_OCCURRENCE$occurrence"]?.let { pickedResolution(raw, it) } ?: resolve(raw)

    /** Each of [parts] with how many before it had the same text. */
    private fun <T> withOccurrences(parts: List<T>, text: (T) -> String): List<Pair<T, Int>> {
        val seen = HashMap<String, Int>()
        return parts.map { part -> part to seen.getOrDefault(text(part), 0).also { seen[text(part)] = it + 1 } }
    }

    private fun isPinned(raw: String): Boolean =
        pins.containsKey(raw) || pins.keys.any { it.startsWith("$raw$PIN_OCCURRENCE") }

    private fun resolutionOf(raw: String, reading: Reading, ranked: List<RankedFood>): SegmentResolution? {
        val top = ranked.firstOrNull() ?: return null
        val (amount, measure) = quantityFor(reading, top.food)
        // A partial match (not every identity token matches) is only a choice, as is a weak typo or spelling match.
        val needsChoice = !top.fullMatch || (top.weakMatch && (ranked.size > 1 || top.firstLetterEdit))
        return SegmentResolution(raw, ranked, top.food, amount, measure.unit, calculate(top.food, amount, measure), needsChoice, measure.name)
    }

    private fun calculate(food: FoodRecord, amount: Double, measure: Measure): Calculation =
        calculator.calculate(food, amount, measure.unit, measure.name)

    /**
     * [reading] with a serving's name where a unit goes: the first word of its food text ("2 cans coke", "a can of
     * coke") or the last ("coke 2 cans"), the rest the food. Only for a part that states an amount and no unit.
     */
    private fun servingReadings(reading: Reading): List<Reading> {
        val parsed = reading.parsed
        if (reading.wholeName || parsed.unit != null || (parsed.amount == null && !parsed.articleQuantity)) return emptyList()
        val words = parsed.foodPhrase.trim().split(WHITESPACE).filter { it.isNotBlank() }
        if (words.size < 2) return emptyList()
        fun servingWord(word: String) = word.any(Char::isLetter) && word.lowercase() !in ShorthandLexicon.stopWords
        val first = words.first().takeIf(::servingWord)?.let { word ->
            val rest = words.drop(1).dropWhile { it.equals("of", ignoreCase = true) }
            rest.takeIf { it.isNotEmpty() }?.let { Reading(parsed.copy(foodPhrase = it.joinToString(" ")), wholeName = false, servingWord = word) }
        }
        val last = words.last().takeIf(::servingWord)?.let { word ->
            Reading(parsed.copy(foodPhrase = words.dropLast(1).joinToString(" ")), wholeName = false, servingWord = word)
        }
        return listOfNotNull(first, last)
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

    /**
     * How [raw] reads for [food]: with a serving's name [food] has where a unit goes, as portions when [food] is a recipe
     * with portions and the part names them, else as it reads.
     */
    private fun readingFor(raw: String, food: FoodRecord): Reading {
        val reading = reading(raw)
        servingReadingOf(reading, food)?.let { return it }
        return if (takesPortions(food)) portionReading(reading) ?: reading else reading
    }

    /** [reading] with a word that is a serving of [food] where a unit goes; null when it has no such word. */
    private fun servingReadingOf(reading: Reading, food: FoodRecord): Reading? {
        // The food's own word is no measure when the part asks for another serving: "1 chunk mango" is not "1 mango".
        val asked = food.asksForServing(reading.parsed.foodPhrase)
        return servingReadings(reading).firstOrNull { food.servingNamed(it.servingWord!!) != null && !(asked && food.namesWord(it.servingWord)) }
    }

    /** A recipe with portions: the only thing a "portion" is ever counted for. */
    private fun takesPortions(food: FoodRecord): Boolean = isRecipeId(food.id) &&
        (food.nutrition.unit == MeasureUnit.PORTION || food.conversions.any { it.unit == MeasureUnit.PORTION })

    private fun reading(raw: String): Reading {
        val parsed = parser.parse(raw, probe)
        // A part that is exactly a food's name carries no amount: "Protein 2 bar" is one bar, not two. A number word
        // is part of the name when a food covers the whole part: "Ten grain bread" is one food. A leading article isn't:
        // matching skips it as a stop word, so "a beer" would be the name Beer and lose its count.
        // A glued part ("cow milk3.2") reads exactly like its spaced form, its name included.
        val wholeName = !parsed.articleQuantity && (matcher.isExactName(raw) || matcher.isExactName(parsed.spaced) ||
            (parsed.wordQuantity && (matcher.covers(raw) || matcher.covers(parsed.spaced))))
        return Reading(keepingNameUnit(parsed), wholeName)
    }

    /**
     * A unit word that came with measurements (cup, oz, lb) stays part of a food's name when a food covers it with the
     * rest ("1 cup noodles" with a food Cup noodles): such a part reads as it did before that word was a unit.
     */
    private fun keepingNameUnit(parsed: ParsedSegment): ParsedSegment {
        val word = parsed.unitWord ?: return parsed
        if (word.lowercase().replace(WHITESPACE, " ") !in ShorthandLexicon.nameUnitWords) return parsed
        val phrase = "$word ${parsed.foodPhrase}".trim()
        if (!matcher.covers(phrase)) return parsed
        val factor = ShorthandLexicon.unitSpec(word)?.factor ?: 1.0
        return parsed.copy(foodPhrase = phrase, amount = parsed.amount?.div(factor), unit = null, unitWord = null, unitLabel = null)
    }

    /**
     * The amount and measure a matched [food] takes from [reading]: the typed ones, else the food's defaults. A unit word
     * that is one of the food's serving names counts that serving ("2 slices bread" with a slice); "slices" without one
     * is a piece, as before. A bare count may have its own meaning for the food ([bareCountMeasure]).
     */
    private fun quantityFor(reading: Reading, food: FoodRecord): Pair<Double, Measure> {
        if (reading.wholeName) return food.defaultAmount to Measure(food.defaultUnit)
        val parsed = reading.parsed
        val serving = reading.servingWord
        val amount = parsed.amount ?: if (articleMeansOne(reading, food)) 1.0 else food.defaultAmount
        if (serving != null) return amount to food.typedMeasure(MeasureUnit.SERVING, serving)
        val counted = if (parsed.amount != null) food.bareCountMeasure(parsed.foodPhrase) else food.articleMeasure(parsed.foodPhrase).takeIf { parsed.articleQuantity }
        val unit = parsed.unit ?: return amount to (counted ?: Measure(food.defaultUnit))
        return amount to food.typedMeasure(unit, parsed.unitWord, parsed.unitLabel)
    }

    /** A bare "a" or "an" means one only when the food is counted ("a banana", "a can of coke", a USDA "an egg"); "a milk" is the default. */
    private fun articleMeansOne(reading: Reading, food: FoodRecord): Boolean = reading.parsed.articleQuantity &&
        (food.defaultUnit == MeasureUnit.PIECE || reading.servingWord != null || food.articleMeasure(reading.parsed.foodPhrase) != null)

    /** The calories Add would log for [raw] matched to [food]. The Log's choices show exactly this. */
    fun calculationFor(raw: String, food: FoodRecord): Calculation {
        val reading = readingFor(raw, food)
        val (amount, measure) = quantityFor(reading, food)
        return calculate(food, amount, measure)
    }

    /**
     * The amount and measure [raw] logs as for a [food] picked for it (in the resolver, or the Library bar's batch): a
     * serving of [food] named where a unit goes ("2 cans coke" with a can), else as the part reads.
     */
    fun quantityOf(raw: String, food: FoodRecord): Pair<Double, Measure> = quantityFor(readingFor(raw, food), food)

    /** See [newFoodQuantity]; this planner knows only [food]. */
    internal fun typedQuantity(raw: String, food: FoodRecord): Pair<Double, Measure>? {
        val reading = readingFor(raw, food)
        if (reading.wholeName || (reading.parsed.amount == null && !articleMeansOne(reading, food))) return null
        // A unit counts a food of any name; without one, the rest of the part has to read as this food.
        val part = if (reading.parsed.unit != null) pickedResolution(raw, food) else resolve(raw)
        return part?.takeIf { it.supported && !it.needsChoice }?.let { it.amount to Measure(it.unit, it.label) }
    }

    /** The food text the Log shows for [raw]: the whole part when it is a name, else the part without its amount. */
    fun phraseOf(raw: String): String = reading(raw).phrase

    /** A typed part as the library reads it. A name that carries its own number has no typed amount. */
    fun typed(raw: String): TypedPart {
        val reading = reading(raw)
        return if (reading.wholeName) TypedPart(reading.phrase, null, null)
        else TypedPart(reading.parsed.foodPhrase, reading.parsed.amount, reading.parsed.unit, reading.parsed.unitWord, reading.parsed.articleQuantity)
    }

    /**
     * The text that replaces a typed part when a choice is picked: the amount and unit Add would log, then the food's
     * name. Re-resolving it gives the same food, amount and unit the choice showed.
     */
    fun chosenText(raw: String, food: FoodRecord): String {
        val (amount, measure) = quantityFor(readingFor(raw, food), food)
        return partText(food, amount, measure)
    }

    /** A part that reads back as [amount] [unit] of [food]: "500 g Beef mince", "1 portion Chili". */
    fun partText(food: FoodRecord, amount: Double, unit: MeasureUnit): String = partText(food, amount, Measure(unit))

    /** A part that reads back as [amount] of [measure] of [food]: "500 g Beef mince", "2 slices Bread". */
    fun partText(food: FoodRecord, amount: Double, measure: Measure): String {
        val unit = if (measure.unit == MeasureUnit.SERVING) servingNameFor(measure.label, amount) else measure.label
        return listOfNotNull(formatAmount(amount), unit.ifBlank { null }, food.name).joinToString(" ")
    }

    /** [input] with [part] after its last part: after a comma, or as typed when the input already ends in a separator. */
    fun withPartAdded(input: String, part: String): String {
        val head = input.trimEnd()
        return when {
            head.isEmpty() -> part
            endsOpen(head) -> "$head $part"
            else -> "$head, $part"
        }
    }

    /** [input] without its first part [raw]; the other parts keep their text and the separators between them. */
    fun withoutPart(input: String, raw: String): String {
        val spans = segments(input)
        val at = spans.indexOfFirst { it.text == raw }
        if (at < 0) return input
        val kept = spans.filterIndexed { index, _ -> index != at }
        return buildString {
            kept.forEachIndexed { n, span ->
                append(span.text)
                if (n < kept.lastIndex) append(input.substring(span.end, kept[n + 1].start).ifBlank { ", " })
            }
        }
    }

    /** Whether Add left [raw] for the user to settle: no food behind it, or local matches to choose from. Not a unit its food lacks. */
    fun needsAttention(raw: String): Boolean = resolve(raw).let { it == null || it.needsChoice }

    /** The query the library opens with for [raw]: the food text, without its amount ("kiselo mleko 200g" is "kiselo mleko"). */
    fun libraryQueryFor(raw: String): String = typed(raw).foodPhrase

    /**
     * The part text that replaces [raw] once the library returns [food]: the food's name, then the amount text the user
     * typed for the part ("kiselo mleko 200g" with Kiselo mleko reads "Kiselo mleko 200g"). Without a typed amount, the name.
     */
    fun pickedText(raw: String, food: FoodRecord): String {
        // A part that is a name carries no amount of its own, glued or not.
        // Nor does a leading article: "a bananna" picked as Banana is Banana.
        val reading = reading(raw)
        if (reading.wholeName || reading.parsed.articleQuantity) return food.name
        // The amount as typed, by where the parser found it: the food text may drop what the raw keeps (a name's quotes).
        val parsed = reading.parsed
        val amountText = if (libraryQueryFor(raw).isBlank() || parsed.quantityStart == null || parsed.quantityEnd == null) ""
        else parsed.raw.substring(parsed.quantityStart, parsed.quantityEnd).trim()
        return listOf(food.name, amountText).filter { it.isNotBlank() }.joinToString(" ")
    }

    /**
     * The input after the user taps [food] for the part being typed: its typed amount and the food's name ("200 g Extra
     * virgin olive oil"), or with no amount typed just the name and a space. When text comes before it, a comma keeps it
     * from joining the earlier food ("2 eggs, 100 g Buckwheat").
     */
    fun withChoice(input: String, food: FoodRecord, typing: Boolean = true): String = choose(input, food, typing).input

    /**
     * [withChoice], and the part text that now names [food] (null when the input is unchanged). Pinning that text to
     * [food] keeps the tapped food: re-resolving the chosen text alone may rank another food first.
     */
    fun choose(input: String, food: FoodRecord, typing: Boolean = true): ChosenDraft {
        if (!typing || endsOpen(input)) return ChosenDraft(input, null)
        val parts = segments(input)
        val last = parts.lastOrNull() ?: return ChosenDraft(input, null)
        if (ShorthandLexicon.mealSlotOf(last.text) != null) return ChosenDraft(input, null)
        // A food that runs on across the comma before the part replaces both, keeping the amount typed before the comma.
        val span = acrossComma(input, parts)?.takeIf { across -> across.matches.any { it.food.id == food.id } }?.local?.let { spanOf(input, it) } ?: last
        val before = input.substring(0, span.start)
        if (statesAmount(span.text, food)) chosenText(span.text, food).let { return ChosenDraft(joinBefore(before, it), it) }
        // No amount was typed, so none is written for the user to go back and edit: the name, then room to type one.
        // Add logs the food's default if they do not; the row shows it.
        return ChosenDraft(joinBefore(if (before.endsWith(',')) "$before " else before, food.name) + " ", food.name)
    }

    /** Whether [raw] says how much of [food]: a number, or an "a" that means one ([quantityFor]). */
    private fun statesAmount(raw: String, food: FoodRecord): Boolean {
        val reading = readingFor(raw, food)
        if (reading.wholeName) return false
        val parsed = reading.parsed
        return parsed.amount != null || articleMeansOne(reading, food)
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
        override fun isPinned(text: String): Boolean = this@LogPlanner.pins.isNotEmpty() && this@LogPlanner.isPinned(text)

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

        // First the cheap question, whether any food has a serving by that name (most input has none), then the head's food.
        // Letters of the head's own name are no food either ("Candybar 1 can"): the word stays with its amount, flagged.
        override fun isServingWord(word: String, head: String): Boolean = word.any(Char::isLetter) && (
            matcher.isLettersOfWordIn(word, head) ||
                servingWordCache.getOrPut(word) { matcher.hasServingNamed(word) } &&
                headServingCache.getOrPut("$word\u0000$head") {
                    // The food the words before the amount name, as they read on their own: does it have that serving?
                    resolve(head)?.takeIf { !it.needsChoice }?.food?.servingNamed(word) != null
                }
            )
    }

    /** The parts of [input] the Log page works with, meal words included. Every caller segments through here. */
    fun segments(input: String): List<SegmentSpan> = parser.parts(input, probe)

    /**
     * The last part while it is still the one being typed, for a search that follows the typing: a space after it doesn't
     * finish it, as the user may be typing its next word; a separator does. A meal word is no such part.
     */
    fun partBeingTyped(input: String): SegmentSpan? {
        val typed = input.trimEnd()
        if (typed.isEmpty() || endsOpen(typed)) return null
        val parts = segments(typed)
        return typedSpan(typed, parts, acrossComma(typed, parts))
    }

    /** The span of the part being typed: with a food of the library running on across the comma, the span of that whole name. */
    private fun typedSpan(typed: String, parts: List<SegmentSpan>, across: Across?): SegmentSpan? {
        val last = parts.lastOrNull()?.takeUnless { ShorthandLexicon.mealSlotOf(it.text) != null } ?: return null
        return across?.local?.let { spanOf(typed, it) } ?: last
    }

    private fun spanOf(input: String, join: Join) = SegmentSpan(input.substring(join.start, join.end), join.start, join.end)

    /** A name that may run on across commas: the [phrase] it starts with (the head parts without their amount, then the fragment) and the span it would replace. */
    private class Join(val start: Int, val end: Int, val phrase: String)

    /**
     * What the part being typed may continue: every join the parts before allow, longest first, and for the first of them
     * a food of the library runs on across ([local], with its [matches]).
     */
    private class Across(val joins: List<Join>, val local: Join?, val matches: List<RankedFood>)

    /** Whether a name (a USDA one, say) runs on [phrase], a [CatalogueJoin]'s, as the library's foods are read. */
    fun runsOn(phrase: String): (String) -> Boolean = matcher.runsOn(phrase) ?: { false }

    /**
     * The foods a part typed after a comma may be the rest of a name with ("200g yogurt, gre" and "Yogurt, Greek, plain"):
     * only a part with no amount of its own, joined to the part before it by a comma alone (not ";" or a line break), where
     * the parts before are not picked foods and only the first of them states an amount. Null when no part can be joined;
     * [Across.local] is null when no food of the library runs on (the USDA catalogue may, see [DraftView.catalogueJoins]).
     * Nothing is joined for the user: "2 eggs, banana" stays two foods.
     */
    private fun acrossComma(input: String, parts: List<SegmentSpan>): Across? {
        val fragment = parts.lastOrNull() ?: return null
        if (parts.size < 2 || !isPlainPart(fragment.text) || hasOwnAmount(fragment.text)) return null
        val fragmentPhrase = parser.parse(fragment.text, probe).foodPhrase
        if (fragmentPhrase.isBlank() || matcher.runsOn(fragmentPhrase) == null) return null
        // How far back the name may begin: each part back to it follows a lone comma, and all but the first have no amount.
        val starts = mutableListOf<Int>()
        var at = parts.lastIndex - 1
        while (at >= 0 && parts.lastIndex - at <= MAX_PARTS_IN_A_NAME) {
            val head = parts[at]
            if (input.substring(head.end, parts[at + 1].start).trim() != "," || !isPlainPart(head.text)) break
            starts += at
            if (hasOwnAmount(head.text)) break
            at--
        }
        val joins = starts.asReversed().map { first ->
            val head = parser.parse(input.substring(parts[first].start, parts[parts.lastIndex - 1].end), probe).foodPhrase
            Join(parts[first].start, fragment.end, "$head $fragmentPhrase")
        }
        if (joins.isEmpty()) return null
        // One library scan for each head, until a food runs on.
        joins.forEach { join ->
            val matches = matcher.startingAs(join.phrase, preferences, SUGGESTION_LIMIT)
            if (matches.isNotEmpty()) return Across(joins, join, matches)
        }
        return Across(joins, null, emptyList())
    }

    /** A part that is not a meal word and not a picked food: one that can be the start or the rest of a name. */
    private fun isPlainPart(text: String): Boolean = ShorthandLexicon.mealSlotOf(text) == null && !isPinned(text)

    /** The part states an amount of its own: a number, a number word or an article. */
    private fun hasOwnAmount(text: String): Boolean = parser.parse(text, probe).let { it.amount != null || it.articleQuantity }

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
        val parts = known ?: parser.split(input, probe)
        val last = parts.lastOrNull() ?: return null
        return last.takeUnless { pinOf(it, parts.count { part -> part == it } - 1) != null || ShorthandLexicon.mealSlotOf(it) != null }
    }

    private fun planOf(parts: List<String>): LogPlan {
        val entries = mutableListOf<SegmentResolution>()
        val unresolved = mutableListOf<String>()
        withOccurrences(parts) { it }.forEach { (raw, occurrence) ->
            if (ShorthandLexicon.mealSlotOf(raw) != null) return@forEach
            val resolution = resolvePart(raw, occurrence)
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
        val counted0 = withOccurrences(parts) { it.text }
        val finished = (if (open) counted0.dropLast(1) else counted0).filter { ShorthandLexicon.mealSlotOf(it.first.text) == null }
        val rows = finished.map { (span, occurrence) -> rowFor(span, occurrence) }.toMutableList()
        val activeOccurrence = counted0.lastOrNull()?.second ?: 0
        // The catalogue follows the part a space does not finish ([partBeingTyped]); the suggestions, only one still open.
        val searching = typing && !endsOpen(input.trimEnd())
        val across = if (searching) acrossComma(input.trimEnd(), parts) else null

        var active: SegmentResolution? = null
        var suggestions: List<RankedFood> = emptyList()
        var counted: SegmentResolution? = null
        var addPick: SegmentResolution? = null
        // A quoted name is searched by the text inside the quotes, from the start of a name; one still open leads whatever
        // the part resolves to, as its commas are no break.
        var quotedStarting: List<RankedFood>? = null
        if (activeText.isNotBlank() && ShorthandLexicon.mealSlotOf(activeText) == null) {
            quotedStarting = QuotedNames.trailingInner(activeText)?.let { inner ->
                matcher.startingAs(inner, preferences, SUGGESTION_LIMIT).ifEmpty { matcher.startingWith(inner, preferences, SUGGESTION_LIMIT) }
            }
            val resolution = resolvePart(activeText, activeOccurrence)
            // Add saves the part being typed by the same rule as every other part (see [plan]), unless it is held back
            // until it is finished or picked ("Count what I'm typing" off).
            val counts = countTyping || pinOf(activeText, activeOccurrence) != null
            addPick = resolution?.takeIf { it.supported && !it.needsChoice }
            counted = addPick.takeIf { counts }
            // Each outcome renders the part exactly once: a suggestion group, a choice group, an entry row, or a
            // Not-in-your-foods row.
            when {
                // Nothing matches the word being typed, only a prefix of a longer word does, or a bare word names
                // several foods: suggest the foods it starts. When Add saves the word, the food it saves leads.
                resolution == null || (isBareWord(activeText) && (!resolution.ranked.first().wholeMatch || resolution.ranked.size > 1)) -> {
                    val starting = quotedStarting ?: matcher.startingWith(activeText, preferences, SUGGESTION_LIMIT)
                    suggestions = addPick?.let { saved ->
                        (listOf(saved.ranked.first()) + starting.filterNot { it.food.id == saved.food.id }).take(SUGGESTION_LIMIT)
                    } ?: starting
                }
                // A partial or weak match is a choice group only; the group is the part's rendering, not a second row.
                resolution.needsChoice -> active = resolution
                // A part with no usable conversion is its food's row, flagged: it does not log.
                !resolution.supported -> parts.last().let { span ->
                    rows += DraftRow.Unresolved(activeText, span.start, span.end, noValue = resolution.takeIf { it.namedButCannotLog })
                    // The other foods the words name that do have the unit: one tap picks one.
                    if (resolution.noValue) suggestions = resolution.ranked.drop(1).filter { calculationFor(activeText, it.food).supported }
                }
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
        // The foods that run on across the comma lead: the part is, or may be, the rest of one name.
        if (quotedStarting != null && QuotedNames.isOpenAtEnd(activeText)) suggestions = (quotedStarting + suggestions).distinctBy { it.food.id }.take(SUGGESTION_LIMIT)
        if (open && across?.local != null) suggestions = (across.matches + suggestions).distinctBy { it.food.id }.take(SUGGESTION_LIMIT)
        val attention = counted0.filter { ShorthandLexicon.mealSlotOf(it.first.text) == null }.map { (span, occurrence) -> span.text to resolvePart(span.text, occurrence) }
        // The part being typed asks for nothing while it still has something to show.
        val activeShows = suggestions.isNotEmpty() || active != null
        val needing = attention.filter { (text, resolution) -> (resolution == null || resolution.needsChoice) && !(activeShows && text == activeText) }.map { it.first }
        return DraftView(
            rows = rows,
            activeText = activeText,
            active = active,
            logged = plan(input, typing).entries,
            activePhrase = if (activeText.isBlank()) "" else stripArticle(phraseOf(activeText)),
            suggestions = suggestions,
            catalogueQuery = (if (searching) typedSpan(input.trimEnd(), parts, across) else null)?.takeUnless { isPinned(it.text) }?.let { stripArticle(phraseOf(it.text)) }.orEmpty(),
            catalogueJoins = if (searching && across?.local == null) across?.joins?.map { CatalogueJoin(it.phrase, it.start, it.end) }.orEmpty() else emptyList(),
            counted = counted,
            addLogs = plan(input).entries.isNotEmpty(),
            addPick = addPick,
            needsMatchParts = needing,
            needsMatch = needing.isNotEmpty(),
            hasNoValue = attention.any { (_, resolution) -> resolution != null && !resolution.needsChoice && !resolution.supported },
        )
    }

    private fun rowFor(span: SegmentSpan, occurrence: Int): DraftRow {
        val resolution = resolvePart(span.text, occurrence)
        return if (resolution != null && resolution.supported && !resolution.needsChoice) DraftRow.Entry(resolution)
        else DraftRow.Unresolved(span.text, span.start, span.end, noValue = resolution?.takeIf { it.namedButCannotLog })
    }

    /** One bare word with no quantity: the form a name is typed in, before it is finished. */
    private fun isBareWord(text: String): Boolean =
        text.trim().split(WHITESPACE).size == 1 && text.any { it.isLetter() } && parser.parse(text, probe).let { it.amount == null && !it.wordQuantity }

    /** A leading "a" or "an" is not part of the food the user named: "a bananna" reads "bananna". */
    private fun stripArticle(text: String): String = text.replace(LEADING_ARTICLE, "")

    companion object {
        /** How many matches the Log page offers as choices or suggestions. The preselected one is always the first. */
        const val SUGGESTION_LIMIT = 4
        /** How many parts at most a name that runs on across commas is joined from: the fragment and the heads before it. */
        private const val MAX_PARTS_IN_A_NAME = 6
        private val WHITESPACE = Regex("\\s+")
        private val LEADING_ARTICLE = Regex("(?iu)^(?:a|an)\\s+")
        /** "portion chili", "portions of chili": the food text of a part that may name a recipe's portions. */
        private val PORTION_WORDS = Regex("(?iu)^portions?(?:\\s+of)?\\s+(.+)$")
        private val PORTION_WORD = Regex("(?iu)^portions?$")
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

/**
 * What typed part [raw] logs of [food], a food made for it just now: the amount and measure typed, a serving of [food] counted
 * ("1 bar Candybar" with a bar). Null when no amount was typed, or the food can't count it ("1 can Candybar" with no can,
 * ml with no density), so nothing logs without the user seeing it.
 */
internal fun newFoodQuantity(raw: String, food: FoodRecord): Pair<Double, Measure>? =
    LogPlanner(FoodMatcher(listOf(food))).typedQuantity(raw, food)
