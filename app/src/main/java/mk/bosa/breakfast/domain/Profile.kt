package mk.bosa.breakfast.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// The user's profile: pure values and rules. Everything is optional, and an empty profile changes nothing in the app.

/** What the profile holds beside the weight curve; null is not set. */
data class ProfileValues(
    val name: String = "",
    val proteinTarget: Double? = null,
    val carbsTarget: Double? = null,
    val fatTarget: Double? = null,
    val currentWeight: Double? = null,
    val targetWeight: Double? = null,
    val kcalTarget: Double? = null,
    /** "Several weights a day": each save adds a point, rather than replacing the day's latest. */
    val severalWeights: Boolean = false,
    val heightCm: Double? = null,
    val age: Int? = null,
    /** Null reads as Not saying. */
    val sex: Sex? = null,
    val activity: Activity? = null,
    /** Set by choosing a goal; typing Daily kcal by hand clears it, and the daily adjustment stops ([dailyAdjusted]). */
    val goal: Goal? = null,
    val paceKgPerWeek: Double? = null,
    /** Hides the goal and the targets on screen; they are kept. */
    val trackingOnly: Boolean = false,
    // Each macro's percent when it was entered by percent; null when it was not.
    val proteinPercent: Double? = null,
    val carbsPercent: Double? = null,
    val fatPercent: Double? = null,
    /** The macros last set by "Apply" and never entered: these follow when Daily kcal changes ([macrosAtKcal]). */
    val followingMacros: Set<Macro> = emptySet(),
    /** Grams of fibre a day; not part of the kcal split, the percents or Apply. */
    val fibreTarget: Double? = null,
    /** "Track fibre": fibre beside the macros ([fibreShown]); off keeps [fibreTarget]. */
    val trackFibre: Boolean = false,
)

/** One point of the weight curve. [id] tells apart points of one day; 0 until stored. */
data class WeightPoint(val dayKey: String, val kg: Double, val savedAtEpochMs: Long, val id: Long = 0)

/** The profile's fields as typed. */
@Serializable
data class ProfileDraft(
    val name: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val current: String = "",
    val target: String = "",
    val kcal: String = "",
    val severalWeights: Boolean = false,
    val height: String = "",
    val age: String = "",
    val sex: Sex? = null,
    val activity: Activity? = null,
    val goal: Goal? = null,
    val pace: String = "",
    /** The Custom choice of Kg a week is on. */
    val paceCustom: Boolean = false,
    val trackingOnly: Boolean = false,
    // Not blank: this macro is entered by percent. Otherwise its percent is only shown ([percentShown]).
    val proteinPercent: String = "",
    val carbsPercent: String = "",
    val fatPercent: String = "",
    val followingMacros: Set<Macro> = emptySet(),
    /** Typed in since the page opened: "Apply" leaves these as they are. Their numbers, and how each was entered, are the fields above. */
    val handEditedMacros: Set<Macro> = emptySet(),
    /** A macro was typed in since the last "Apply". */
    val macrosPending: Boolean = false,
    val fibre: String = "",
    val trackFibre: Boolean = false,
) {
    companion object {
        fun of(values: ProfileValues) = ProfileDraft(
            values.name,
            editableNumber(values.proteinTarget),
            editableNumber(values.carbsTarget),
            editableNumber(values.fatTarget),
            editableNumber(values.currentWeight),
            editableNumber(values.targetWeight),
            editableNumber(values.kcalTarget),
            values.severalWeights,
            editableNumber(values.heightCm),
            values.age?.toString().orEmpty(),
            values.sex,
            values.activity,
            values.goal,
            editableNumber(values.paceKgPerWeek),
            values.paceKgPerWeek != null && values.paceKgPerWeek !in PACE_CHOICES,
            values.trackingOnly,
            editableNumber(values.proteinPercent),
            editableNumber(values.carbsPercent),
            editableNumber(values.fatPercent),
            values.followingMacros,
            fibre = editableNumber(values.fibreTarget),
            trackFibre = values.trackFibre,
        )
    }
}

private val WEIGHT_KG = 1.0..500.0
private val MACRO_TARGET_G = 1.0..5000.0

const val KCAL_RANGE_MESSAGE = "Enter 1 to 20000 kcal."
internal val KCAL_TARGET = 1.0..20000.0

const val WEIGHT_RANGE_MESSAGE = "Enter 1 to 500 kg."
const val MACRO_TARGET_RANGE_MESSAGE = "Enter 1 to 5000 g."
const val HEIGHT_RANGE_MESSAGE = "Enter 50 to 300 cm."
const val AGE_RANGE_MESSAGE = "Enter 1 to 120."
const val PERCENT_RANGE_MESSAGE = "Enter 0 to 100%."

/** The Kg a week choices beside Custom. */
val PACE_CHOICES = listOf(0.25, 0.5, 1.0)

private val AGE_YEARS = 1..120
private val HEIGHT_CM = 50.0..300.0

/** An age the app keeps, in whole years. */
fun ageValid(years: Int): Boolean = years in AGE_YEARS

/** A height the app keeps, in cm. */
fun heightValid(cm: Double): Boolean = cm in HEIGHT_CM

/** A macro's percent the app keeps; zero is a choice, unlike zero grams. */
fun macroPercentValid(percent: Double): Boolean = percent.isFinite() && percent in 0.0..100.0

/** A pace the app keeps, in kg a week. */
fun paceValid(kgPerWeek: Double): Boolean = isPositiveAmount(kgPerWeek)

/** A macro's percent as typed: empty is not set, otherwise in range with either decimal mark. */
fun macroPercentTextValid(text: String): Boolean = text.isBlank() || parseDecimal(text)?.let(::macroPercentValid) == true

/** Age as typed: empty is not set, otherwise whole years in range. */
fun ageTextValid(text: String): Boolean = text.isBlank() || ageOf(text) != null

/** Kg a week as typed: empty is not set, otherwise a positive number. */
fun paceTextValid(text: String): Boolean = text.isBlank() || positiveAmountOf(text)?.let(::paceValid) == true

/** Age as typed: whole years. */
fun ageOf(text: String): Int? = text.trim().toIntOrNull()?.takeIf(::ageValid)

/** A height as typed: empty is not set, otherwise in range with either decimal mark. */
fun heightTextValid(text: String): Boolean = text.isBlank() || positiveAmountOf(text)?.let(::heightValid) == true

/** A weight the app keeps, in kg. */
fun weightValid(kg: Double): Boolean = kg in WEIGHT_KG

/** A macro target the app keeps, in g. */
fun macroTargetValid(grams: Double): Boolean = grams in MACRO_TARGET_G

/** A kcal target the app keeps. */
fun kcalTargetValid(kcal: Double): Boolean = kcal in KCAL_TARGET

/** A weight as typed: empty is not set, otherwise in range with either decimal mark. */
fun weightTextValid(text: String): Boolean = text.isBlank() || positiveAmountOf(text)?.let(::weightValid) == true

/** A macro target as typed: empty is not set, otherwise in range with either decimal mark. */
fun macroTargetTextValid(text: String): Boolean = text.isBlank() || positiveAmountOf(text)?.let(::macroTargetValid) == true

/** A kcal target as typed: empty is not set, otherwise in range with either decimal mark. */
fun kcalTargetTextValid(text: String): Boolean = text.isBlank() || positiveAmountOf(text)?.let(::kcalTargetValid) == true

/** Kg a week shows: for Lose and Gain, with a body the burn can be worked out from, outside Tracking only. */
val ProfileDraft.paceShown: Boolean get() = !trackingOnly && burnBody() != null && (goal == Goal.LOSE || goal == Goal.GAIN)

/** The macros' % fields show: with a valid Daily kcal, outside Tracking only. */
val ProfileDraft.percentsShown: Boolean get() = !trackingOnly && validDailyKcal != null

private val ProfileDraft.percentsTextValid: Boolean get() = listOf(proteinPercent, carbsPercent, fatPercent).all(::macroPercentTextValid)

/** The fibre target field shows: with Track fibre on, outside Tracking only. */
val ProfileDraft.fibreTargetShown: Boolean get() = trackFibre && !trackingOnly

private val ProfileDraft.kcalAndGramsTextValid: Boolean get() =
    kcalTargetTextValid(kcal) && listOf(protein, carbs, fat).all(::macroTargetTextValid) && (!trackFibre || macroTargetTextValid(fibre))

/** A hidden field holding what is not valid reads as [exact]; one on show has already stopped [profileOf]. */
private fun shownOrSaved(text: String, valid: (String) -> Boolean, exact: Double?): Double? =
    if (valid(text)) fieldNumber(text, exact) else exact

/**
 * [draft] as values, a field left as it was keeping its exact number in [saved]; null while a number on show is not valid.
 * A hidden field that is not valid takes its saved value, and under Tracking only one such target keeps every saved target,
 * with the goal and pace they came from, as they belong together.
 */
fun profileOf(draft: ProfileDraft, saved: ProfileValues): ProfileValues? {
    if (!listOf(draft.current, draft.target).all(::weightTextValid)) return null
    if (!heightTextValid(draft.height)) return null
    if (!ageTextValid(draft.age)) return null
    if (draft.paceShown && !paceTextValid(draft.pace)) return null
    if (!draft.trackingOnly && !draft.kcalAndGramsTextValid) return null
    if (draft.percentsShown && !draft.percentsTextValid) return null
    val values = ProfileValues(
        draft.name.trim(),
        fieldNumber(draft.protein, saved.proteinTarget),
        fieldNumber(draft.carbs, saved.carbsTarget),
        fieldNumber(draft.fat, saved.fatTarget),
        fieldNumber(draft.current, saved.currentWeight),
        fieldNumber(draft.target, saved.targetWeight),
        fieldNumber(draft.kcal, saved.kcalTarget),
        draft.severalWeights,
        fieldNumber(draft.height, saved.heightCm),
        ageOf(draft.age),
        draft.sex,
        draft.activity,
        draft.goal,
        shownOrSaved(draft.pace, ::paceTextValid, saved.paceKgPerWeek),
        draft.trackingOnly,
        shownOrSaved(draft.proteinPercent, ::macroPercentTextValid, saved.proteinPercent),
        shownOrSaved(draft.carbsPercent, ::macroPercentTextValid, saved.carbsPercent),
        shownOrSaved(draft.fatPercent, ::macroPercentTextValid, saved.fatPercent),
        draft.followingMacros,
        fibreTarget = if (draft.trackFibre) fieldNumber(draft.fibre, saved.fibreTarget) else shownOrSaved(draft.fibre, ::macroTargetTextValid, saved.fibreTarget),
        trackFibre = draft.trackFibre,
    )
    val keepTargets = draft.trackingOnly && !(draft.kcalAndGramsTextValid && draft.percentsTextValid)
    return if (keepTargets) values.withSavedTargets(saved) else values
}

private fun ProfileValues.withSavedTargets(saved: ProfileValues) = copy(
    kcalTarget = saved.kcalTarget,
    proteinTarget = saved.proteinTarget,
    carbsTarget = saved.carbsTarget,
    fatTarget = saved.fatTarget,
    fibreTarget = saved.fibreTarget,
    proteinPercent = saved.proteinPercent,
    carbsPercent = saved.carbsPercent,
    fatPercent = saved.fatPercent,
    followingMacros = saved.followingMacros,
    goal = saved.goal,
    paceKgPerWeek = saved.paceKgPerWeek,
)

/** "Add to curve" shows: with several weights a day, for any valid weight; else a weight is typed, and it is another one or today has no point yet, so a steady weight can be recorded. */
fun curveOffered(draft: ProfileDraft, saved: ProfileValues, hasPointToday: Boolean): Boolean = when {
    draft.current.isBlank() -> false
    draft.severalWeights -> weightTextValid(draft.current)
    else -> !hasPointToday || draft.current != ProfileDraft.of(saved).current
}

/** Where "Add to curve" starts: on for a weight not yet recorded, off for one already there, so a save of anything else adds no point. */
fun curveTickStart(draft: ProfileDraft, saved: ProfileValues, hasPointToday: Boolean): Boolean =
    !hasPointToday || draft.current != ProfileDraft.of(saved).current

/** The weight a save puts on the curve today: with the tick on, any weight with several a day, a changed weight, or any weight on a day without a point; else none. */
fun curveWeightOf(before: ProfileValues, after: ProfileValues, addToCurve: Boolean, hasPointToday: Boolean): Double? {
    val kg = after.currentWeight ?: return null
    return if (addToCurve && (after.severalWeights || kg != before.currentWeight || !hasPointToday)) kg else null
}

/** By day, then time; a repeated time keeps the order stored. */
fun oldestFirst(points: List<WeightPoint>): List<WeightPoint> =
    points.sortedWith(compareBy({ it.dayKey }, { it.savedAtEpochMs }, { it.id }))

/** The curve as a list reads it: the newest day first, and within a day the latest. */
fun newestFirst(points: List<WeightPoint>): List<WeightPoint> = oldestFirst(points).asReversed()

const val MINUTES_PER_DAY = 1440L

/** A diary day's start as a position along the curve's x axis, in minutes; null for a key that is no date. */
fun dayStartMinute(dayKey: String): Double? = runCatching { (LocalDate.parse(dayKey).toEpochDay() * MINUTES_PER_DAY).toDouble() }.getOrNull()

/**
 * Where [point] sits along the curve, in whole minutes by the phone's clock, so a day is always 1440 even when the clocks
 * change. A diary day can run past midnight, up to the next one's "Day starts at". A time outside that (imported or seeded)
 * says nothing about the day, and the point sits at the day's start. Null for a key that is no date.
 */
fun curveMinute(point: WeightPoint, zone: ZoneId = ZoneId.systemDefault()): Double? {
    val start = dayStartMinute(point.dayKey) ?: return null
    val day = LocalDate.parse(point.dayKey)
    val at = Instant.ofEpochMilli(point.savedAtEpochMs).atZone(zone).toLocalDateTime()
    val onItsDay = at.toLocalDate() == day || at.toLocalDate() == day.plusDays(1)
    return if (onItsDay) start + ChronoUnit.MINUTES.between(day.atStartOfDay(), at) else start
}
