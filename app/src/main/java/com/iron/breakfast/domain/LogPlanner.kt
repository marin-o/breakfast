package com.iron.breakfast.domain

/**
 * One comma segment resolved the way the Log saves it: the best-ranked food, the typed or default amount and unit,
 * and the nutrition for them. [ranked] is every matching food, best first, so [food] is always `ranked.first()`.
 */
data class SegmentResolution(
    val raw: String,
    val ranked: List<RankedFood>,
    val food: FoodRecord,
    val amount: Double,
    val unit: MeasureUnit,
    val calculation: Calculation,
) {
    val supported: Boolean get() = calculation.supported
}

/** What Add logs for one input: the segments that save, in order, and the raw segments that do not. */
data class LogPlan(val entries: List<SegmentResolution>, val unresolved: List<String>)

/**
 * The single rule for turning typed text into foods. [save] and the Log page both use it, so the preselected
 * suggestion, the add-bar sum and the saved entries always agree. Pure: it depends only on the matcher and prefs.
 */
class LogPlanner(
    private val matcher: FoodMatcher,
    private val preferences: Map<String, Preference> = emptyMap(),
    private val calculator: NutritionCalculator = NutritionCalculator(),
    private val parser: ShorthandParser = ShorthandParser(),
) {
    /** Resolves one segment. Null when no food matches. An unsupported unit still resolves; check [SegmentResolution.supported]. */
    fun resolve(raw: String): SegmentResolution? {
        val parsed = parser.parse(raw)
        val ranked = matcher.suggestions(parsed.foodPhrase, preferences, SUGGESTION_LIMIT)
        val food = ranked.firstOrNull()?.food ?: return null
        val amount = parsed.amount ?: food.defaultAmount
        val unit = parsed.unit ?: food.defaultUnit
        return SegmentResolution(raw, ranked, food, amount, unit, calculator.calculate(food, amount, unit))
    }

    /** Every comma segment of [input], resolved; segments that match nothing or have no conversion are unresolved. */
    fun plan(input: String): LogPlan {
        val entries = mutableListOf<SegmentResolution>()
        val unresolved = mutableListOf<String>()
        parser.split(input).forEach { raw ->
            val resolution = resolve(raw)
            if (resolution != null && resolution.supported) entries += resolution else unresolved += raw
        }
        return LogPlan(entries, unresolved)
    }

    companion object {
        /** How many matches the Log page offers as choices. The preselected one is always the first. */
        const val SUGGESTION_LIMIT = 4
    }
}
