package mk.bosa.breakfast.domain

import java.util.Locale

class FoodMatcher(
    private val catalog: List<FoodRecord>,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private val abbreviations = mapOf(
        "pb" to "peanut butter",
        "oats" to "rolled oats",
        "tbsp" to "tablespoon",
        "tsp" to "teaspoon",
        "ml" to "milliliter",
        "g" to "gram"
    )

    /**
     * The words of a food's names and aliases in one script, read the way a typed token is (lowercase, apostrophes
     * joined, diacritics folded): [names] for a substring match, [words] for a whole word, a stem or a typo.
     */
    private class NameWords(val words: Set<String>) {
        val names: String = words.joinToString(" ")
    }

    /** Only a food's name and aliases are matched; its category, preparation and source text are not. */
    private class IndexedFood(
        val food: FoodRecord,
        /** Every name word, whatever its script: what an explicit modifier ("2.8") must be part of. */
        val all: NameWords,
        /** The name words no script mix spoils: what a token with no letters is matched against ([sameScript]). */
        val unmixed: NameWords,
        /** The name words by script. A token is matched only against the words in its own script. */
        val byScript: Map<TextScript, NameWords>,
        val normalizedNames: Set<String>,
        val normalizedAliases: Set<String>,
        /** The words of each name and alias in order, for the suggestions that follow a name's start. */
        val nameWords: List<List<String>>,
    ) {
        /** The name words [sameScript] lets [query] match, kept by script so one call per food stays cheap. */
        fun inScriptOf(query: QueryToken): NameWords? = when (query.script) {
            TextScript.NONE -> unmixed
            TextScript.MIXED -> null
            else -> byScript[query.script]
        }
    }

    private val indexedCatalog = catalog.map { food ->
        val words = food.allNames().flatMap(::tokens).toSet()
        IndexedFood(
            food = food,
            all = NameWords(words),
            unmixed = NameWords(words.filterTo(mutableSetOf()) { sameScript(TextScript.NONE, scriptOf(it)) }),
            byScript = words.groupBy(::scriptOf).mapValues { NameWords(it.value.toSet()) },
            normalizedNames = food.allNames().mapTo(mutableSetOf(), ::normalize),
            normalizedAliases = food.aliases.mapTo(mutableSetOf(), ::normalize),
            nameWords = food.allNames().map(::tokens),
        )
    }

    /** One input token with everything the tiers need, computed once per call rather than once per food. */
    private class QueryToken(val raw: String) {
        val script = scriptOf(raw)
        val stems = stems(raw)
        val typoAllowance = typoAllowance(raw)
        val isShortWord = raw.length < 3 && raw.all { it.isLetter() }
        val isModifier = raw in MODIFIERS || raw.endsWith("%") || raw.matches(NUMERIC_TOKEN_PATTERN)
    }

    /** How one input token matched one food: its weight, and whether a typo's edit is at the first letter. */
    private class TokenHit(val weight: Double, val firstLetterEdit: Boolean, val prefix: Boolean = false)

    fun suggestions(phrase: String, preferences: Map<String, Preference> = emptyMap(), limit: Int = 3): List<RankedFood> {
        val inputTokens = tokens(expandAbbreviations(phrase))
        if (inputTokens.isEmpty()) return emptyList()
        val queries = inputTokens.map(::QueryToken)
        val normalizedPhrase = normalize(phrase)
        // A plural phrase that is a food's whole name or alias once singular ("tomatoes" is "tomato").
        // Only when the typed phrase is not already an exact name or alias of some food, so exact forms still win.
        val stemmedPhrase = queries.joinToString(" ") { query -> query.stems.firstOrNull() ?: query.raw }
        val exactSomewhere = indexedCatalog.any { normalizedPhrase in it.normalizedNames || normalizedPhrase in it.normalizedAliases }
        val explicitModifiers = queries.filter { it.isModifier }.map { it.raw }
        val identityQueries = queries.filterNot { it.isModifier }
        val now = nowMs()

        return indexedCatalog.mapNotNull { indexed ->
            val food = indexed.food
            if (explicitModifiers.any { it !in indexed.all.names }) return@mapNotNull null
            val hits = hits(queries, indexed)
            val identityHits = hits.filterIndexed { index, _ -> !queries[index].isModifier }
            val exactName = normalizedPhrase in indexed.normalizedNames
            if (identityQueries.isNotEmpty() && identityHits.all { it == null }) return@mapNotNull null
            if (identityQueries.isEmpty() && !exactName) return@mapNotNull null

            val matched = hits.filterNotNull()
            if (matched.isEmpty()) return@mapNotNull null
            val aliasExact = normalizedPhrase in indexed.normalizedAliases
            val stemmedName = !exactSomewhere && (stemmedPhrase in indexed.normalizedNames || stemmedPhrase in indexed.normalizedAliases)
            val tokenScore = hits.sumOf { it?.weight ?: 0.0 } / inputTokens.size * 10.0
            val preferenceScore = (preferences[food.id]?.score(now) ?: 0.0) * 3.0
            val explicitScore = when {
                aliasExact -> 100.0
                stemmedName -> STEMMED_NAME_SCORE
                else -> 0.0
            }
            val originScore = if (food.origin == FoodOrigin.CUSTOM) 2.0 else 0.0
            RankedFood(
                food = food,
                score = explicitScore + tokenScore + originScore + preferenceScore,
                reasons = buildList {
                    if (aliasExact) add("explicit alias")
                    if (stemmedName) add("plural form of a name")
                    add("${matched.size}/${inputTokens.size} name tokens")
                    if (preferenceScore > 0) add("explicit preference")
                    if (originScore > 0) add("custom food")
                    if (explicitModifiers.isNotEmpty()) add("modifier constrained")
                },
                // Weak: every matched token is a typo, so the food is only a choice, not a resolution.
                weakMatch = matched.all { it.weight <= TYPO_WEIGHT },
                // A segment resolves on its own only when every identity token matches; a partial match is a choice.
                fullMatch = if (identityQueries.isEmpty()) exactName else identityHits.all { it != null },
                wholeMatch = if (identityQueries.isEmpty()) exactName else identityHits.all { it != null && !it.prefix },
                firstLetterEdit = matched.any { it.firstLetterEdit },
                exact = exactName || aliasExact || stemmedName,
            )
            // An exact name or alias ranks first; a learned preference only orders matches of equal standing.
        }.sortedWith(compareByDescending<RankedFood> { it.exact }.thenByDescending { it.score }.thenBy { it.food.name }).take(limit)
    }

    /**
     * How each of [queries] matches a food ([hit]). A token whose only match is as letters of a longer word is no match
     * when that longer word was typed too: "bar" and "can" name nothing in "bar candybar" and "can candybar", where
     * "candybar" already is the name word they are letters of. A whole word of the name always matches.
     */
    private fun hits(queries: List<QueryToken>, indexed: IndexedFood): List<TokenHit?> = queries.map { query ->
        hit(query, indexed)?.takeUnless { found -> found.prefix && queries.any { lettersOf(query.raw, it.raw) } }
    }

    /**
     * True when [word], singular or plural, is only letters of a longer word of [phrase] ("cans" and "can" in "candybar"),
     * so what matches [phrase] says nothing about [word]. A word some food names whole is a food of its own, never only
     * that ("apple" in "pineapple"). Under three letters a word never matches that way.
     */
    fun isLettersOfWordIn(word: String, phrase: String): Boolean {
        val typed = tokens(phrase)
        return tokens(word).any { token -> singularForms(token).any { form -> typed.any { lettersOf(form, it) } } } &&
            !matchesWholeWord(word)
    }

    /**
     * How one input token matches a food, within its own script: an exact form (a substring of the names, whole or a
     * prefix), a singular form that is a whole name word, or a typo of a name word. Null when no tier matches, and
     * always null for a token that mixes scripts ("bаnana" with a Cyrillic а): it is neither Latin nor Cyrillic.
     */
    private fun hit(query: QueryToken, indexed: IndexedFood): TokenHit? {
        val raw = query.raw
        val scope = indexed.inScriptOf(query) ?: return null
        // A short word (under three letters) names a food only as a whole word: "m" must not find "Grandma's".
        if (query.isShortWord) {
            return if (raw in scope.words) EXACT_HIT else null
        }
        if (raw in scope.names) {
            // A whole word of the name is exact; a part of a longer word ("ban" in banana) is a prefix match.
            return if (raw in scope.words) EXACT_HIT else PREFIX_HIT
        }
        if (query.stems.any { it in scope.words }) return STEM_HIT
        if (query.typoAllowance > 0) {
            scope.words.firstOrNull { withinEditDistance(raw, it, query.typoAllowance) }
                ?.let { return TokenHit(TYPO_WEIGHT, it.first() != raw.first()) }
        }
        return null
    }

    fun best(phrase: String, preferences: Map<String, Preference> = emptyMap()): RankedFood? =
        suggestions(phrase, preferences, 1).firstOrNull()

    /** True when [phrase] is exactly one food's name or alias, ignoring case and stop words. */
    fun isExactName(phrase: String): Boolean {
        val normalizedPhrase = normalize(phrase)
        return normalizedPhrase.isNotEmpty() &&
            indexedCatalog.any { normalizedPhrase in it.normalizedNames || normalizedPhrase in it.normalizedAliases }
    }

    /** True when [phrase] is one food's name or alias, or one food matches every word of it. Used to keep names whole. */
    fun covers(phrase: String): Boolean {
        if (isExactName(phrase)) return true
        val queries = tokens(expandAbbreviations(phrase)).map(::QueryToken)
        if (queries.isEmpty()) return false
        return indexedCatalog.any { indexed -> hits(queries, indexed).all { it != null } }
    }

    /**
     * True when some food has a serving named [word] ("cans" for a can), under the script rule: only the cheap first
     * check before the planner asks whether the food a part names has it.
     */
    fun hasServingNamed(word: String): Boolean = catalog.any { it.servingNamed(word) != null }

    /** True when some food names [word] as a whole word, through any tier except a prefix of a longer word. */
    fun matchesWholeWord(word: String): Boolean {
        val queries = tokens(expandAbbreviations(word)).map(::QueryToken)
        if (queries.isEmpty()) return false
        return indexedCatalog.any { indexed -> queries.all { hit(it, indexed)?.let { h -> !h.prefix } == true } }
    }

    /**
     * The foods a word being typed may complete. Suggestions never cross scripts: the fragment matches only the names
     * and aliases in the script it was typed (diacritics folded within that script), so "cev" does not find Чевап and
     * "bu" does not find a Cyrillic "бут". Ranked by the saved preference, then a name that starts with the fragment
     * before one where only a later word does, then name.
     */
    fun startingWith(fragment: String, preferences: Map<String, Preference> = emptyMap(), limit: Int = 4): List<RankedFood> {
        val word = tokens(expandAbbreviations(fragment)).lastOrNull() ?: return emptyList()
        val script = scriptOf(word)
        if (script == TextScript.MIXED) return emptyList()
        val folded = foldDiacritics(word)
        // Scripts never cross, word by word: a fragment matches only the name words in its own script, so "Протеин bar"
        // is found by "про" and by "bar", and a food with no word in that script is not suggested at all.
        val starts = { nameWord: String -> sameScript(script, scriptOf(nameWord)) && foldDiacritics(nameWord).startsWith(folded) }
        return rankedStarting("starts with $word", preferences, limit, { words -> words.any(starts) }) { words -> words.firstOrNull()?.let(starts) == true }
    }

    /**
     * The foods whose name starts with [phrase]: its words in order from the name's first word, each whole but the last,
     * which is the fragment still being typed ("yogurt, gre" finds "Yogurt, Greek, plain"). A word of the name that is the
     * plural or singular of a typed one counts as it. Scripts never cross, word by word, as in [startingWith].
     */
    fun startingAs(phrase: String, preferences: Map<String, Preference> = emptyMap(), limit: Int = 4): List<RankedFood> {
        val starts = runOn(phrase) ?: return emptyList()
        return rankedStarting("starts as ${phrase.trim()}", preferences, limit, starts, starts)
    }

    /** Whether a name (a USDA one, say) runs on [phrase] as [startingAs] reads it; null when no name can ([runOn]). */
    fun runsOn(phrase: String): ((String) -> Boolean)? {
        val starts = runOn(phrase) ?: return null
        return { name -> starts(tokens(name)) }
    }

    /**
     * The test of a name's words for [startingAs]: null when [phrase] has no words, mixes scripts, or ends in a stop word
     * (tokens drop those, so "yogurt, with" would otherwise ask for every "Yogurt" name).
     */
    private fun runOn(phrase: String): ((List<String>) -> Boolean)? {
        if (matchWords(phrase).lastOrNull() in STOP_WORDS) return null
        val words = tokens(expandAbbreviations(phrase)).map(::foldDiacritics)
        if (words.isEmpty()) return null
        val scripts = words.map(::scriptOf)
        if (TextScript.MIXED in scripts) return null
        val last = words.lastIndex
        val typedStems = words.map(::stems)
        return { name ->
            name.size >= words.size && words.indices.all { i ->
                sameScript(scripts[i], scriptOf(name[i])) && when {
                    i == last -> foldDiacritics(name[i]).startsWith(words[i])
                    else -> name[i] == words[i] || name[i] in typedStems[i] || words[i] in stems(name[i])
                }
            }
        }
    }

    /** The foods with a name that [qualifies] (as words), the saved preference first, then a name that [leads], then the name. */
    private fun rankedStarting(
        reason: String,
        preferences: Map<String, Preference>,
        limit: Int,
        qualifies: (List<String>) -> Boolean,
        leads: (List<String>) -> Boolean,
    ): List<RankedFood> {
        val now = nowMs()
        return indexedCatalog.mapNotNull { indexed ->
            val food = indexed.food
            val names = indexed.nameWords
            if (names.none(qualifies)) return@mapNotNull null
            Triple(
                RankedFood(food = food, score = (preferences[food.id]?.score(now) ?: 0.0) * 3.0, reasons = listOf(reason)),
                names.any(leads),
                food.name,
            )
        }.sortedWith(
            compareByDescending<Triple<RankedFood, Boolean, String>> { it.first.score }
                .thenByDescending { it.second }
                .thenBy { it.third }
        ).take(limit).map { it.first }
    }

    fun expandAbbreviations(phrase: String): String = phrase.lowercase(Locale.ROOT)
        .split(WHITESPACE)
        .joinToString(" ") { abbreviations[it] ?: it }

    private fun tokens(value: String): List<String> = matchWords(value).filter { it !in STOP_WORDS }

    private fun normalize(value: String): String = tokens(expandAbbreviations(value)).joinToString(" ")

    companion object {
        private val WHITESPACE = Regex("\\s+")
        private const val EXACT_WEIGHT = 1.0
        private const val STEM_WEIGHT = 0.95
        private const val TYPO_WEIGHT = 0.6
        private const val MIN_STEM_LENGTH = 3
        private const val STEMMED_NAME_SCORE = 50.0
        private const val MIN_LETTERS_OF_A_WORD = 3

        private fun lettersOf(part: String, word: String): Boolean =
            part.length >= MIN_LETTERS_OF_A_WORD && part.length < word.length && part in word

        private val EXACT_HIT = TokenHit(EXACT_WEIGHT, false)
        private val PREFIX_HIT = TokenHit(EXACT_WEIGHT, false, prefix = true)
        private val STEM_HIT = TokenHit(STEM_WEIGHT, false)
        private val STOP_WORDS = ShorthandLexicon.stopWords
        private val NUMERIC_TOKEN_PATTERN = Regex("\\d+(?:[.]\\d+)?")
        private val MODIFIERS = setOf(
            "oat", "oats", "small", "large", "smooth", "crunchy", "raw", "cooked", "roasted", "grilled",
            "boneless", "skinless", "drained", "canned", "yellow", "green", "red", "orange", "white",
            "roma", "grape", "baby", "mature", "light", "restaurant", "2.8", "3.2", "домашно", "домашен"
        )

        /** Singular forms to try for a token: "eggs" gives "egg", "berries" gives "berry". Never shorter than three letters. */
        private fun stems(token: String): List<String> {
            val candidates = buildList {
                if (token.endsWith("ies")) add(token.dropLast(3) + "y")
                if (token.endsWith("es")) add(token.dropLast(2))
                if (token.endsWith("s") && !token.endsWith("ss")) add(token.dropLast(1))
            }
            return candidates.filter { it.length >= MIN_STEM_LENGTH }.distinct()
        }

        /** Edits allowed for a typo: none under six letters, one for six to eight, two from nine on. Letters only. */
        private fun typoAllowance(token: String): Int = when {
            token.length < 6 || !token.all { it.isLetter() } -> 0
            token.length < 9 -> 1
            else -> 2
        }

        /** True when [a] and [b] are within [max] single-letter edits (insert, delete, substitute). Stops early when it cannot. */
        private fun withinEditDistance(a: String, b: String, max: Int): Boolean {
            if (kotlin.math.abs(a.length - b.length) > max) return false
            var previous = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val current = IntArray(b.length + 1)
                current[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                }
                if (current.min() > max) return false
                previous = current
            }
            return previous[b.length] <= max
        }
    }
}

/**
 * [text] with a hyphen between two letters read as a word break, as food names and aliases are matched: "coca-cola" is
 * "coca cola", "кока-кола" "кока кола", and "Vitaminka-Витаминка" two words, each in one script (as the matcher's
 * tokens always split it), so the script rule holds word by word. A hyphen at either end of a word ("coca-", still being
 * typed) is a break too. A hyphen by a digit ("1-2") is left as it is.
 */
fun hyphensAsBreaks(text: String): String {
    if ('-' !in text) return text
    val out = StringBuilder(text.length)
    for (i in text.indices) {
        val c = text[i]
        val before = text.getOrNull(i - 1)
        val after = text.getOrNull(i + 1)
        val byLetter = before?.isLetter() == true || after?.isLetter() == true
        val byDigit = before?.isDigit() == true || after?.isDigit() == true
        out.append(if (c == '-' && byLetter && !byDigit) ' ' else c)
    }
    return out.toString()
}

/** Lowercase with the Latin diacritics folded (č and ć to c, š to s, ž to z, đ to d). */
internal fun foldDiacritics(text: String): String = text.lowercase(Locale.ROOT)
    .replace('č', 'c').replace('ć', 'c').replace('š', 's').replace('ž', 'z').replace('đ', 'd')

/** [text] as every match reads it: hyphens as breaks, lowercase, an apostrophe joining its word ("Jerry's" is one), folded. */
internal fun matchText(text: String): String = foldDiacritics(hyphensAsBreaks(text).replace("'", "").replace("’", ""))

/** The one tokeniser: the words of [text] as every match reads them ([matchText]; punctuation separates them). */
internal fun matchWords(text: String): List<String> =
    matchText(text).replace(TOKEN_CHARACTER_PATTERN, " ").split(' ').filter { it.isNotBlank() }

private val TOKEN_CHARACTER_PATTERN = Regex("[^\\p{L}\\p{N}.%]+")

/** [text] as a substring search reads it: its [matchWords], one space apart. */
internal fun searchKey(text: String): String = matchWords(text).joinToString(" ")

/**
 * True when [query] is part of [text], by their [matchWords] and within one script: a query word that mixes scripts finds
 * nothing, and a name word that mixes them matches nothing and joins nothing ("Foo Bаr Baz" is not "foo baz").
 */
internal fun searchFinds(text: String, query: String): Boolean {
    val queryWords = matchWords(query)
    if (queryWords.any { scriptOf(it) == TextScript.MIXED }) return false
    val key = queryWords.joinToString(" ")
    val runs = mutableListOf(StringBuilder())
    for (word in matchWords(text)) {
        if (scriptOf(word) == TextScript.MIXED) { runs += StringBuilder(); continue }
        if (runs.last().isNotEmpty()) runs.last().append(' ')
        runs.last().append(word)
    }
    return runs.any { it.contains(key) }
}

/**
 * Whether a word in script [query] may match a word in script [word]: never from or to a mix of scripts, only its own
 * script otherwise; a query with no letters (a number) matches any word without a mix.
 */
internal fun sameScript(query: TextScript, word: TextScript): Boolean = when {
    query == TextScript.MIXED || word == TextScript.MIXED -> false
    query == TextScript.NONE -> true
    else -> query == word
}

/** The script a word is written in. Matching never crosses scripts, and a word that mixes them matches nothing. */
internal enum class TextScript { NONE, LATIN, CYRILLIC, MIXED }

/** The script of [text]'s letters: [TextScript.NONE] with no Latin or Cyrillic letter, [TextScript.MIXED] with both. */
internal fun scriptOf(text: String): TextScript {
    var latin = false
    var cyrillic = false
    for (c in text) {
        if (!c.isLetter()) continue
        when (Character.UnicodeScript.of(c.code)) {
            Character.UnicodeScript.LATIN -> latin = true
            Character.UnicodeScript.CYRILLIC -> cyrillic = true
            else -> Unit
        }
    }
    return when {
        latin && cyrillic -> TextScript.MIXED
        latin -> TextScript.LATIN
        cyrillic -> TextScript.CYRILLIC
        else -> TextScript.NONE
    }
}
