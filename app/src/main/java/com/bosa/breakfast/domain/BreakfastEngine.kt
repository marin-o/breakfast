package com.bosa.breakfast.domain

import com.bosa.breakfast.data.BreakfastStore
import com.bosa.breakfast.data.DiaryRows
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class BreakfastEngine(
    private val store: BreakfastStore,
    /** Settings, "Count what I'm typing", read at each use (the planner is called off the main thread). */
    private val countTypingSetting: () -> Boolean = { true },
    /** Settings, "Learn from my picks", read at each use. */
    private val learnFromPicksSetting: () -> Boolean = { true },
    /** Where [save] plans, off the caller's (main) thread: the app's compute dispatcher (`AppThreads.compute`). */
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) {
    private val parser = ShorthandParser()
    private val calculator = NutritionCalculator()
    /** The Log's matcher: the foods and the recipes ([RecipeIndex.matchable]). */
    private val matchers = MatcherCache()
    /** The ingredients' matcher: the foods only, as a recipe is never an ingredient. */
    private val foodMatchers = MatcherCache()
    private val recipeIndex = RecipeIndex()

    suspend fun initialize() = store.initialize()

    fun weightPoints(): List<WeightPoint> = store.weightPoints()
    suspend fun addWeightPoint(point: WeightPoint) = store.addWeightPoint(point)
    suspend fun replaceLatestWeightPoint(point: WeightPoint) = store.replaceLatestWeightPoint(point)
    suspend fun deleteWeightPoint(id: Long) = store.deleteWeightPoint(id)
    fun targetHistory(): List<TargetRow> = store.targetHistory()
    suspend fun saveTargetRow(row: TargetRow) = store.saveTargetRow(row)

    /** Every stored row, for Export diary. */
    internal suspend fun exportRows(): DiaryRows = store.exportRows()

    /** Import diary: the stored diary becomes [rows], or stays as it was if the write fails. */
    internal suspend fun replaceDiary(rows: DiaryRows) = store.replaceDiary(rows)

    /** Delete all data, in one transaction; see [BreakfastStore.deleteAllData]. */
    internal suspend fun deleteAllData(alsoPreferences: Boolean) = store.deleteAllData(alsoPreferences)

    private val logPlanners = PlannerCache()
    private val ingredientPlanners = PlannerCache()

    /** "Count what I'm typing" (Settings): passed to every planner; see [LogPlanner]. */
    val countTyping: Boolean get() = countTypingSetting()

    /**
     * "Learn from my picks" (Settings). Off: ranking ignores the learned preferences and a pick is not learned; the
     * stored preferences stay as they are.
     */
    val learnFromPicks: Boolean get() = learnFromPicksSetting()

    /** The learned preferences ranking uses: none while [learnFromPicks] is off. */
    private fun preferences(): Map<String, Preference> = rankingPreferences(learnFromPicks, store.preferenceMap())

    /**
     * The matcher for the stored foods and the recipes, rebuilt only when [BreakfastStore.foods] or
     * [BreakfastStore.recipes] is a new list: the store keeps the same lists after an entry or preference write
     * (MOB-028), so only a food or recipe write re-indexes the catalog, the costly part of matching. Preferences are not
     * in the index; they are passed to each ranking call.
     */
    private fun matcher(): FoodMatcher = matchers.matcherFor(matchable())

    /** The foods and the recipes as foods, what the Log matches ([RecipeIndex]). */
    private fun matchable(): List<FoodRecord> = recipeIndex.matchable(store.foods(), store.recipes())

    /** The matcher for the foods alone, for ingredients; the Log's own while there are no recipes. */
    private fun ingredientMatcher(): FoodMatcher {
        val foods = store.foods()
        return if (matchable() === foods) matcher() else foodMatchers.matcherFor(foods)
    }

    /** The recipes, each ingredient following its food while it exists ([Recipe.current]). */
    fun recipes(): List<Recipe> = recipeIndex.recipes(store.foods(), store.recipes())

    /** The matcher in use now; tests check which writes keep it. */
    internal fun matcherForTesting(): FoodMatcher = matcher()

    /** Called after every write to the foods, so the next [matcher] call indexes the stored foods again. */
    private fun invalidateMatcher() { matchers.invalidate(); foodMatchers.invalidate() }

    fun parse(input: String): ParsedSegment = parser.parse(input)

    fun search(query: String): List<RankedFood> = matcher().suggestions(query, preferences(), 20)

    fun alternativesFor(entry: MealEntry): List<RankedFood> {
        val phrase = parser.parse(entry.sourcePhrase).foodPhrase.ifBlank { entry.foodName }
        return matcher().suggestions(phrase, preferences(), 3)
            .filterNot { it.food.id == entry.foodId }
            .take(2)
    }

    suspend fun save(
        input: String,
        nowMs: Long = System.currentTimeMillis(),
        mealSlot: MealSlot = MealSlot.BREAKFAST,
        dayKey: String,
        pins: Map<String, FoodRecord> = emptyMap(),
    ): SavedMeal {
        // Planning is CPU work (tens of ms on a phone with a year of foods): off the caller's (main) thread, before
        // the store's lock is taken.
        val plan = withContext(compute) { planner(pins).plan(input) }
        val entries = plan.entries.mapIndexed { index, resolved ->
            entryOf(resolved.food, resolved.amount, resolved.unit, resolved.calculation, mealSlot, dayKey, orderedSavedAt(nowMs, index), resolved.raw, resolved.label)
        }
        if (entries.isNotEmpty()) store.saveEntries(entries)
        return SavedMeal(
            entries = entries,
            totals = nutritionTotalOf(entries),
            unresolvedSegments = plan.unresolved
        )
    }

    /** The planner for the current foods, recipes and preferences. [save] and the Log page both resolve through it. */
    fun planner(pins: Map<String, FoodRecord> = emptyMap()): LogPlanner = logPlanners.plannerFor(matcher(), preferences(), pins, countTyping)

    /** The planner a recipe's ingredients are typed through: the Log's rules over the foods alone. */
    fun ingredientPlanner(pins: Map<String, FoodRecord> = emptyMap()): LogPlanner =
        ingredientPlanners.plannerFor(ingredientMatcher(), preferences(), pins, countTyping)

    /** One planner per matcher, preferences, pins and setting, so its per-part cache carries across keystrokes. */
    private inner class PlannerCache {
        private var planner: LogPlanner? = null
        private var matcher: FoodMatcher? = null
        private var preferences: Map<String, Preference>? = null
        private var pins: Map<String, FoodRecord>? = null
        private var counts: Boolean? = null

        @Synchronized
        fun plannerFor(matcher: FoodMatcher, preferences: Map<String, Preference>, pins: Map<String, FoodRecord>, counts: Boolean): LogPlanner {
            planner?.let { if (matcher === this.matcher && preferences === this.preferences && pins == this.pins && counts == this.counts) return it }
            return LogPlanner(matcher, preferences, calculator, parser, pins, counts).also {
                planner = it; this.matcher = matcher; this.preferences = preferences; this.pins = pins; this.counts = counts
            }
        }
    }

    /**
     * Applies [edit] to entry [entryId] as it is stored, inside the store's write (see [BreakfastStore.editEntry]), so an
     * edit never starts from a stale snapshot and never re-creates a deleted entry. [missing] when the entry is gone.
     */
    private suspend fun editEntry(entryId: String, missing: String, edit: (MealEntry) -> ReplacementResult): ReplacementResult {
        var result: ReplacementResult = ReplacementResult.Failure(missing)
        store.editEntry(entryId) { stored -> edit(stored).also { result = it }.let { (it as? ReplacementResult.Success)?.entry } }
        return result
    }

    suspend fun replaceEntry(entryId: String, foodId: String): ReplacementResult =
        editEntry(entryId, "That saved entry is no longer available.") { old ->
            // A "Not right?" pick may be a recipe: the Log matched it as a food.
            val food = matchable().firstOrNull { it.id == foodId }
                ?: return@editEntry ReplacementResult.Failure("That catalogue record is no longer available.")
            if (!isPositiveAmount(old.enteredAmount)) return@editEntry ReplacementResult.Failure(POSITIVE_AMOUNT_MESSAGE)
            val calculation = calculator.calculate(food, old.enteredAmount, old.enteredUnit, old.enteredUnitLabel)
            if (!calculation.supported) {
                return@editEntry ReplacementResult.Failure(
                    "${food.name} has no ${old.enteredUnit.displayLabel()} conversion. Re-enter this segment with a compatible amount such as ml or g."
                )
            }
            ReplacementResult.Success(
                old.copy(
                    foodId = food.id,
                    foodName = food.name,
                    resolvedBaseAmount = calculation.resolvedBaseAmount,
                    nutritionSnapshot = calculation.nutrition,
                    conversionProvenance = calculation.provenance,
                    approximateConversion = calculation.approximate,
                    resolvedBaseUnit = food.nutrition.unit
                )
            )
        }

    /**
     * Recalculates a saved entry for its original food record, changing only
     * the entered amount and unit. The parser is deliberately strict here:
     * quick edit must contain an amount and a supported unit, and it must not
     * be allowed to fall back to the food's defaults.
     */
    suspend fun replaceEntryAmountUnit(entryId: String, text: String): ReplacementResult =
        editEntry(entryId, "That entry is no longer available.") { old ->
            val parsed = parseEditAmount(text)
                ?: return@editEntry ReplacementResult.Failure("Enter a positive amount and a supported unit, such as 100 g or 1 tbsp.")
            // A recipe entry follows its recipe while it exists (portion and g both); a deleted one scales its snapshot.
            val food = matchable().firstOrNull { it.id == old.foodId }
                ?: return@editEntry snapshotScaled(old, parsed.amount, parsed.unit, parsed.word)?.let { ReplacementResult.Success(old.withScaled(parsed.amount, it)) }
                    ?: ReplacementResult.Failure("The original food for this entry is no longer available.")
            // A serving's name typed ("2 slice") counts that serving, as on the Log.
            val measure = food.typedMeasure(parsed.unit, parsed.word, parsed.label)
            val calculation = calculator.calculate(food, parsed.amount, measure.unit, measure.name)
            if (!calculation.supported) {
                return@editEntry ReplacementResult.Failure(
                    "That amount/unit is not supported for ${food.name}."
                )
            }
            ReplacementResult.Success(
                old.copy(
                    enteredAmount = parsed.amount,
                    enteredUnit = measure.unit,
                    enteredUnitLabel = measure.name,
                    resolvedBaseAmount = calculation.resolvedBaseAmount,
                    nutritionSnapshot = calculation.nutrition,
                    conversionProvenance = calculation.provenance,
                    approximateConversion = calculation.approximate,
                    resolvedBaseUnit = food.nutrition.unit
                )
            )
        }

    suspend fun replaceEntryFromText(entryId: String, text: String): ReplacementResult {
        // Matched before the write: indexing never runs inside the store's transaction.
        val parsed = parser.parse(text)
        val match = matcher().best(parsed.foodPhrase, preferences())
        return editEntry(entryId, "That entry is no longer available.") { old ->
            if (match == null) return@editEntry ReplacementResult.Failure("No matching food.")
            val amount = parsed.amount ?: old.enteredAmount
            val measure = if (parsed.unit != null) match.food.typedMeasure(parsed.unit, parsed.unitWord, parsed.unitLabel)
                else Measure(old.enteredUnit, old.enteredUnitLabel)
            val unit = measure.unit
            val calculation = calculator.calculate(match.food, amount, unit, measure.name)
            if (!calculation.supported) return@editEntry ReplacementResult.Failure("That amount/unit is not supported for ${match.food.name}.")
            ReplacementResult.Success(old.copy(foodId = match.food.id, foodName = match.food.name, enteredAmount = amount, enteredUnit = unit,
                enteredUnitLabel = measure.name,
                resolvedBaseAmount = calculation.resolvedBaseAmount, nutritionSnapshot = calculation.nutrition,
                conversionProvenance = calculation.provenance, approximateConversion = calculation.approximate, sourcePhrase = text,
                resolvedBaseUnit = match.food.nutrition.unit))
        }
    }

    /** Replaces every editable field in one storage transaction, retaining the entry id. */
    suspend fun replaceEntryDraft(
        entryId: String,
        foodId: String,
        amount: Double,
        unit: MeasureUnit,
        mealSlot: MealSlot,
        dayKey: String,
        /** The serving's name for [MeasureUnit.SERVING]. */
        servingName: String? = null,
    ): ReplacementResult = editEntry(entryId, "That saved entry is no longer available.") { old ->
        // A recipe that still exists is edited as a live recipe; a deleted one scales the entry's own snapshot.
        val food = matchable().firstOrNull { it.id == foodId }
        // Its food deleted, an entry kept on it can still change its amount (in its own unit), meal and day.
        if (food == null) {
            val scaled = snapshotScaled(old, amount, unit, servingName).takeIf { foodId == old.foodId }
                ?: return@editEntry ReplacementResult.Failure("That catalogue record is no longer available.")
            return@editEntry ReplacementResult.Success(old.withScaled(amount, scaled).copy(mealSlot = mealSlot, dayKey = dayKey))
        }
        if (!isPositiveAmount(amount)) return@editEntry ReplacementResult.Failure(POSITIVE_AMOUNT_MESSAGE)
        val calculation = calculator.calculate(food, amount, unit, servingName)
        if (!calculation.supported) return@editEntry ReplacementResult.Failure("That amount/unit is not supported for ${food.name}.")
        ReplacementResult.Success(
            old.copy(
                foodId = food.id,
                foodName = food.name,
                enteredAmount = amount,
                enteredUnit = unit,
                enteredUnitLabel = servingName.takeIf { unit == MeasureUnit.SERVING },
                resolvedBaseAmount = calculation.resolvedBaseAmount,
                nutritionSnapshot = calculation.nutrition,
                conversionProvenance = calculation.provenance,
                approximateConversion = calculation.approximate,
                sourcePhrase = food.name,
                mealSlot = mealSlot,
                dayKey = dayKey,
                resolvedBaseUnit = food.nutrition.unit,
            )
        )
    }

    /** Learns a pick; nothing while [learnFromPicks] is off. */
    suspend fun reinforce(foodId: String) { if (learnFromPicks) store.reinforce(foodId) }

    /** Reloads the stored diary when the read after a committed write failed; true when it reloaded. */
    suspend fun reloadIfStale(): Boolean = store.reloadIfStale()

    /** A damaged diary was set aside and the user has not seen the notice yet (see `data/DamagedDiaries`). */
    fun damagedNoticePending(): Boolean = store.damagedNoticePending()

    fun damagedNoticeSeen() = store.damagedNoticeSeen()

    /** True once after the store went on with a fresh diary during the session (see `BreakfastStore`). */
    fun takeDiaryReplaced(): Boolean = store.takeDiaryReplaced()

    fun foods(): List<FoodRecord> = store.foods()

    suspend fun foodByBarcode(barcode: String): FoodRecord? = store.foodByBarcode(barcode)

    suspend fun deleteEntry(id: String) = store.deleteEntry(id)

    suspend fun restoreEntry(entry: MealEntry) = store.saveEntries(listOf(entry))

    /** Undoes a logged batch in one transaction; an entry changed since [entries] were saved stays. Returns the ids deleted. */
    suspend fun deleteEntries(entries: List<MealEntry>): List<String> = store.deleteEntries(entries)

    /** Deletes a food for good; past entries keep their own name and numbers. */
    suspend fun deleteFood(foodId: String) {
        store.deleteFood(foodId)
        invalidateMatcher()
    }

    suspend fun updateFood(food: FoodRecord) {
        store.upsertFood(food.copy(personalOverride = true))
        invalidateMatcher()
    }

    suspend fun foodById(foodId: String): FoodRecord? = store.foodById(foodId)

    suspend fun foodsMatching(query: String = ""): List<FoodRecord> = store.foodsMatching(query)

    suspend fun saveCustomAndLog(
        name: String,
        brand: String?,
        barcode: String?,
        alias: String? = null,
        nutrition: NutritionValues,
        basisAmount: Double,
        basisUnit: MeasureUnit,
        mealSlot: MealSlot,
        amount: Double,
        amountUnit: MeasureUnit,
        nowMs: Long = System.currentTimeMillis(),
        dayKey: String,
        sourceName: String? = null,
        sourceDescription: String? = null,
        servings: List<ServingConversion> = emptyList(),
        density: Double? = null,
    ): MealEntry {
        require(name.isNotBlank()) { "Name is required" }
        require(basisAmount.isFinite() && basisAmount > 0.0) { "Nutrition basis must be positive" }
        requirePositive(amount)
        require(nutrition.calories.isFinite() && nutrition.calories >= 0.0) { "Calories are required" }
        val food = newCustomFoodRecord(
            "custom-" + UUID.randomUUID(), name, brand, barcode, alias, nutrition, basisAmount, basisUnit,
            amount, amountUnit, sourceName, sourceDescription, servings, density,
        )
        val calculation = calculator.calculate(food, amount, amountUnit)
        require(calculation.supported) { "Amount unit is incompatible with the nutrition basis" }
        val entry = entryOf(food, amount, amountUnit, calculation, mealSlot, dayKey, nowMs, name.trim())
        store.addFoodAndEntry(food, entry)
        invalidateMatcher()
        return entry
    }

    suspend fun saveCustomFood(
        name: String, brand: String?, barcode: String?, alias: String? = null, nutrition: NutritionValues,
        basisAmount: Double, basisUnit: MeasureUnit, sourceName: String? = null,
        sourceDescription: String? = null, servings: List<ServingConversion> = emptyList(), density: Double? = null,
    ): FoodRecord {
        require(name.isNotBlank()) { "Name is required" }
        require(basisAmount.isFinite() && basisAmount > 0.0) { "Nutrition basis must be positive" }
        require(nutrition.calories.isFinite() && nutrition.calories >= 0.0) { "Calories are required" }
        val food = newCustomFoodRecord(
            "custom-" + UUID.randomUUID(), name, brand, barcode, alias, nutrition, basisAmount, basisUnit,
            basisAmount, basisUnit, sourceName, sourceDescription, servings, density,
        )
        store.upsertFood(food)
        invalidateMatcher()
        return food
    }

    /**
     * Adds to the stored food [expected] the [servings] it has no serving by that name for, while it is stored exactly as
     * [expected] (an edit since leaves it as the user made it). Nothing else of it changes. True when it added one.
     */
    suspend fun addServings(expected: FoodRecord, servings: List<ServingConversion>): Boolean {
        val merged = withServingsAdded(store.foodById(expected.id), expected, servings) ?: return false
        store.upsertFood(merged)
        invalidateMatcher()
        return true
    }

    /** Stores a food for later logs without logging it: the food a Log part was picked as. Returns the food as it is stored. */
    suspend fun cacheFood(food: FoodRecord): FoodRecord {
        // A recipe is never stored as a food.
        if (isRecipeId(food.id)) return food
        val effectiveFood = cachedFoodForLogging(food, store.foodById(food.id), food.barcode?.let { store.foodByBarcode(it) })
        store.addFood(effectiveFood)
        invalidateMatcher()
        return effectiveFood
    }

    suspend fun cacheFoodAndLog(
        food: FoodRecord, amount: Double, unit: MeasureUnit, mealSlot: MealSlot, dayKey: String,
        /** The serving's name for [MeasureUnit.SERVING] ("slice"). */
        servingName: String? = null,
    ): MealEntry {
        if (isRecipeId(food.id)) return logRecipe(food, amount, unit, mealSlot, dayKey)
        val effectiveFood = cachedFoodForLogging(
            food,
            store.foodById(food.id),
            food.barcode?.let { store.foodByBarcode(it) }
        )
        requirePositive(amount)
        val calculation = calculator.calculate(effectiveFood, amount, unit, servingName)
        require(calculation.supported) { "Amount unit is incompatible with the nutrition basis" }
        val entry = entryOf(effectiveFood, amount, unit, calculation, mealSlot, dayKey, System.currentTimeMillis(), effectiveFood.name, servingName.takeIf { unit == MeasureUnit.SERVING })
        store.addFoodAndEntry(effectiveFood, entry)
        invalidateMatcher()
        return entry
    }

    private fun requirePositive(amount: Double) = require(isPositiveAmount(amount)) { POSITIVE_AMOUNT_MESSAGE }

    /** One new entry: [amount] [unit] of [food] with its own snapshot of [calculation]. */
    private fun entryOf(
        food: FoodRecord,
        amount: Double,
        unit: MeasureUnit,
        calculation: Calculation,
        meal: MealSlot,
        dayKey: String,
        savedAt: Long,
        phrase: String,
        label: String? = null,
    ) = MealEntry(
        id = UUID.randomUUID().toString(),
        foodId = food.id,
        foodName = food.name,
        enteredAmount = amount,
        enteredUnit = unit,
        resolvedBaseAmount = calculation.resolvedBaseAmount,
        nutritionSnapshot = calculation.nutrition,
        conversionProvenance = calculation.provenance,
        approximateConversion = calculation.approximate,
        savedAtEpochMs = savedAt,
        sourcePhrase = phrase,
        mealSlot = meal,
        dayKey = dayKey,
        resolvedBaseUnit = food.nutrition.unit,
        enteredUnitLabel = label,
    )

    /**
     * Logs [amount] [unit] of a recipe ([Recipe.asFood]) as one entry with its own snapshot, so editing or deleting the
     * recipe later never changes it. The recipe is not stored as a food.
     */
    suspend fun logRecipe(recipe: FoodRecord, amount: Double, unit: MeasureUnit, mealSlot: MealSlot, dayKey: String): MealEntry {
        requirePositive(amount)
        val calculation = calculator.calculate(recipe, amount, unit)
        require(calculation.supported) { "That amount/unit is not supported for ${recipe.name}." }
        val entry = entryOf(recipe, amount, unit, calculation, mealSlot, dayKey, System.currentTimeMillis(), recipe.name)
        store.saveEntries(listOf(entry))
        return entry
    }

    /** Saves a new recipe (a blank id gets one) or an edited one; returns it as stored. */
    suspend fun saveRecipe(recipe: Recipe): Recipe {
        require(recipe.name.isNotBlank()) { "Name is required" }
        require(recipe.yields().isNotEmpty()) { "Portions or a total weight is required" }
        val stored = recipe.copy(name = recipe.name.trim(), id = recipe.id.ifBlank { RECIPE_ID_PREFIX + UUID.randomUUID() })
        store.saveRecipe(stored)
        return stored
    }

    /** Deletes a recipe for good; past entries keep their own name and numbers. */
    suspend fun deleteRecipe(id: String) = store.deleteRecipe(id)

    fun persistedMeal(): SavedMeal {
        val entries = store.entries()
        return SavedMeal(
            entries = entries,
            totals = nutritionTotalOf(entries)
        )
    }

    fun totalsForToday(): NutritionValues = nutritionTotalOf(store.entries())
}

/** The preferences ranking reads: [stored] when picks are learned, else none (the same empty map every time). */
internal fun rankingPreferences(learn: Boolean, stored: Map<String, Preference>): Map<String, Preference> =
    if (learn) stored else NO_PREFERENCES

private val NO_PREFERENCES: Map<String, Preference> = emptyMap()

/**
 * An entry whose food was deleted, at [amount] of its own unit: its own snapshot scaled in proportion, the only honest
 * figure left. Null for another unit (nothing to convert with) or a stored amount that is not a positive number.
 */
internal fun snapshotScaled(entry: MealEntry, amount: Double, unit: MeasureUnit, servingName: String? = null): Calculation? {
    if (unit != entry.enteredUnit || !isPositiveAmount(amount) || !snapshotScalable(entry)) return null
    // A serving is its own unit: only the same name scales.
    if (unit == MeasureUnit.SERVING && (servingName == null || entry.enteredUnitLabel?.let { servingNameMatches(servingName, it) } != true)) return null
    val factor = amount / entry.enteredAmount
    return Calculation(entry.nutritionSnapshot * factor, entry.resolvedBaseAmount * factor, entry.conversionProvenance, entry.approximateConversion, supported = true)
}

/** Whether an entry's amount can change without its food: [snapshotScaled] needs a positive stored amount. */
internal fun snapshotScalable(entry: MealEntry): Boolean = isPositiveAmount(entry.enteredAmount)

private fun MealEntry.withScaled(amount: Double, scaled: Calculation) =
    copy(enteredAmount = amount, resolvedBaseAmount = scaled.resolvedBaseAmount, nutritionSnapshot = scaled.nutrition)

/**
 * The food a picked or scanned [candidate] is stored and logged as: a stored personal edit of it wins, and so does any
 * stored copy of a USDA food, which the catalogue is never written back to; else the candidate, which replaces a stored
 * copy [byId] but keeps the named servings that copy has and it lacks (a search result has none of its own, so
 * re-picking one never strips a serving its earlier pick brought).
 */
internal fun cachedFoodForLogging(candidate: FoodRecord, byId: FoodRecord?, byBarcode: FoodRecord?): FoodRecord {
    listOfNotNull(byId, byBarcode).firstOrNull { it.personalOverride }?.let { return it }
    if (isUsdaFoodId(candidate.id) && byId != null) return byId
    val kept = byId?.conversions.orEmpty().filter { stored ->
        val name = stored.name ?: return@filter false
        candidate.conversions.none { it.name?.equals(name, ignoreCase = true) == true }
    }
    return if (kept.isEmpty()) candidate else candidate.copy(conversions = candidate.conversions + kept)
}

/**
 * An amount and its unit as the quick edit types them. [word] is the unit word as typed (a serving's name for
 * [MeasureUnit.SERVING]); [label] the label of a unit the amount was converted from ("kg").
 */
data class ParsedAmountUnit(val amount: Double, val unit: MeasureUnit, val word: String? = null, val label: String? = null)

private val PORTION_UNIT = Regex("(?iu)portions?")

private val SERVING_WORD = Regex("\\p{L}+")

/** Strict amount/unit parsing used by saved-entry quick edit. */
fun parseStrictAmountUnit(text: String): ParsedAmountUnit? {
    val parsed = ShorthandParser().parse(text)
    val amount = parsed.amount?.takeIf(::isPositiveAmount) ?: return null
    val rest = parsed.foodPhrase.trim()
    return when {
        // The word is kept only when it may be a serving's name ("slice"), not the unit's own label ("g").
        parsed.unit != null -> if (rest.isNotBlank()) null
            else ParsedAmountUnit(amount, parsed.unit, parsed.unitWord?.takeUnless { it.equals(parsed.unit.label, ignoreCase = true) }, parsed.unitLabel)
        // "portion" is no unit word of the shorthand (a food's phrase keeps it): here, alone after the amount, it is one.
        PORTION_UNIT.matches(rest) -> ParsedAmountUnit(amount, MeasureUnit.PORTION)
        else -> null
    }
}

/**
 * The quick edit's amount and unit ([parseStrictAmountUnit]), where the unit may also be a serving's name alone after
 * the amount ("2 slice", "1 can"), which the entry's food has or does not.
 */
fun parseEditAmount(text: String): ParsedAmountUnit? {
    parseStrictAmountUnit(text)?.let { return it }
    val parsed = ShorthandParser().parse(text)
    val amount = parsed.amount?.takeIf(::isPositiveAmount) ?: return null
    val word = parsed.foodPhrase.trim()
    return if (parsed.unit == null && SERVING_WORD.matches(word)) ParsedAmountUnit(amount, MeasureUnit.SERVING, word) else null
}

/**
 * The saved time of the [index]th entry of one save. Entries saved together share a moment, so each gets its own
 * millisecond in typed order: the diary lists them by saved time, and the typed order is then kept.
 */
internal fun orderedSavedAt(nowMs: Long, index: Int): Long = nowMs + index

/** One [FoodMatcher] per foods list object, built by [build] the first time that list is asked for. */
internal class MatcherCache(
    private val build: (List<FoodRecord>) -> FoodMatcher = { FoodMatcher(it) },
) {
    private var source: List<FoodRecord>? = null
    private var matcher: FoodMatcher? = null

    @Synchronized
    fun matcherFor(foods: List<FoodRecord>): FoodMatcher {
        val current = matcher
        if (current != null && foods === source) return current
        return build(foods).also { matcher = it; source = foods }
    }

    /** The next [matcherFor] builds again, whatever list it gets. */
    @Synchronized
    fun invalidate() {
        source = null
    }
}
