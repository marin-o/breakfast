package com.bosa.breakfast.domain

import kotlin.math.roundToInt

// Macro percents and grams that share Daily kcal. See docs/profile-pages-spec.md, Macros.

/** How a macro got its number: typed in one of its fields, or left [FOLLOWS]ing the others (never entered, or not set). */
enum class MacroEntered { PERCENT, GRAMS, FOLLOWS }

/** One macro's part of Daily kcal; [percent] is its share of the kcal. */
data class MacroShare(val grams: Double, val percent: Double, val entered: MacroEntered)

fun kcalPerGram(macro: Macro): Double = when (macro) {
    Macro.PROTEIN -> KCAL_PER_GRAM_PROTEIN
    Macro.CARBS -> KCAL_PER_GRAM_CARBS
    Macro.FAT -> KCAL_PER_GRAM_FAT
}

private fun percentOf(grams: Double, macro: Macro, kcal: Double): Double = if (kcal > 0) grams * kcalPerGram(macro) / kcal * 100 else 0.0

/**
 * [restKcal] shared by [others] in the ratio of [weight], all zero meaning equal parts. Grams are whole, and the last
 * takes the remainder.
 */
private fun shared(others: List<Macro>, restKcal: Double, weight: (Macro) -> Double): Map<Macro, Double> {
    val total = others.sumOf(weight)
    var left = restKcal
    return others.associateWith { macro ->
        val share = if (total > 0) weight(macro) / total else 1.0 / others.size
        val kcal = if (macro == others.last()) left else restKcal * share
        val grams = (kcal.coerceAtLeast(0.0) / kcalPerGram(macro)).roundToInt().toDouble()
        left -= grams * kcalPerGram(macro)
        grams
    }
}

/**
 * [edited] set to [typed] (percent or grams, by [by]) within [kcal], capped at 100%; the other two share the rest in their
 * current ratio. Those keep how they were entered, unless all three were in grams, as targets from before this feature
 * are: then the two others follow from here on.
 */
fun rebalanceMacros(shares: Map<Macro, MacroShare>, kcal: Double, edited: Macro, typed: Double, by: MacroEntered): Map<Macro, MacroShare> {
    val factor = kcalPerGram(edited)
    val percent = if (by == MacroEntered.PERCENT) typed.coerceAtMost(100.0) else percentOf(typed, edited, kcal).coerceAtMost(100.0)
    val grams = if (by == MacroEntered.PERCENT) (kcal * percent / 100 / factor).roundToInt().toDouble() else typed.coerceAtMost(kcal / factor)
    val allInGrams = shares.values.all { it.entered == MacroEntered.GRAMS }
    val others = Macro.entries - edited
    val rest = shared(others, (kcal - grams * factor).coerceAtLeast(0.0)) { shares.getValue(it).percent }
    return Macro.entries.associateWith { macro ->
        if (macro == edited) MacroShare(grams, percent, by)
        else {
            val g = rest.getValue(macro)
            MacroShare(g, percentOf(g, macro, kcal), if (allInGrams) MacroEntered.FOLLOWS else shares.getValue(macro).entered)
        }
    }
}

/**
 * [shares] at a new [kcal]: a percent macro keeps its percent, a grams macro its grams, and those that follow share what
 * is left in their ratio. With none that follows the sum is left as it comes; followers with no grams stay unset.
 */
fun macrosAtKcal(shares: Map<Macro, MacroShare>, kcal: Double): Map<Macro, MacroShare> {
    val fixedGrams = shares.filterValues { it.entered != MacroEntered.FOLLOWS }.mapValues { (macro, share) ->
        if (share.entered == MacroEntered.PERCENT) (kcal * share.percent / 100 / kcalPerGram(macro)).roundToInt().toDouble() else share.grams
    }
    val followers = Macro.entries.filter { shares.getValue(it).entered == MacroEntered.FOLLOWS }
    val left = (kcal - fixedGrams.entries.sumOf { (macro, grams) -> grams * kcalPerGram(macro) }).coerceAtLeast(0.0)
    val weight = { macro: Macro -> shares.getValue(macro).grams * kcalPerGram(macro) }
    val followed = if (followers.sumOf(weight) <= 0.0) followers.associateWith { 0.0 } else shared(followers, left, weight)
    return Macro.entries.associateWith { macro ->
        val share = shares.getValue(macro)
        val grams = fixedGrams[macro] ?: followed[macro] ?: share.grams
        MacroShare(grams, if (share.entered == MacroEntered.PERCENT) share.percent else percentOf(grams, macro, kcal), share.entered)
    }
}

private fun sharesOf(grams: (Macro) -> Double?, percents: (Macro) -> Double?, following: Set<Macro>, kcal: Double): Map<Macro, MacroShare> =
    Macro.entries.associateWith { macro ->
        val g = grams(macro)
        val p = percents(macro)
        val entered = when {
            p != null -> MacroEntered.PERCENT
            g == null || macro in following -> MacroEntered.FOLLOWS
            else -> MacroEntered.GRAMS
        }
        MacroShare(g ?: 0.0, p ?: percentOf(g ?: 0.0, macro, kcal), entered)
    }

/** The macros stored as following; one with no grams is simply not set. */
private fun followingOf(shares: Map<Macro, MacroShare>): Set<Macro> =
    shares.filterValues { it.entered == MacroEntered.FOLLOWS && it.grams > 0 }.keys

private fun ProfileValues.gramsFor(macro: Macro): Double? = when (macro) {
    Macro.PROTEIN -> proteinTarget
    Macro.CARBS -> carbsTarget
    Macro.FAT -> fatTarget
}

private fun ProfileValues.percentFor(macro: Macro): Double? = when (macro) {
    Macro.PROTEIN -> proteinPercent
    Macro.CARBS -> carbsPercent
    Macro.FAT -> fatPercent
}

/** A target at zero grams is not set: the app keeps macro targets from 1 g. */
private fun Map<Macro, MacroShare>.gramsSet(macro: Macro): Double? = getValue(macro).grams.takeIf { it > 0 }

private fun Map<Macro, MacroShare>.percentSet(macro: Macro): Double? = getValue(macro).takeIf { it.entered == MacroEntered.PERCENT }?.percent

/** These values with Daily kcal at [kcal], the macros following by how each was entered. */
fun ProfileValues.withKcalFollowing(kcal: Double): ProfileValues {
    val next = macrosAtKcal(sharesOf({ gramsFor(it) }, { percentFor(it) }, followingMacros, kcal), kcal)
    return copy(
        kcalTarget = kcal,
        proteinTarget = next.gramsSet(Macro.PROTEIN),
        carbsTarget = next.gramsSet(Macro.CARBS),
        fatTarget = next.gramsSet(Macro.FAT),
        proteinPercent = next.percentSet(Macro.PROTEIN),
        carbsPercent = next.percentSet(Macro.CARBS),
        fatPercent = next.percentSet(Macro.FAT),
        followingMacros = followingOf(next),
    )
}

fun ProfileDraft.gramsText(macro: Macro): String = when (macro) {
    Macro.PROTEIN -> protein
    Macro.CARBS -> carbs
    Macro.FAT -> fat
}

private fun ProfileDraft.percentText(macro: Macro): String = when (macro) {
    Macro.PROTEIN -> proteinPercent
    Macro.CARBS -> carbsPercent
    Macro.FAT -> fatPercent
}

private fun ProfileDraft.withMacroTexts(macro: Macro, grams: String, percent: String): ProfileDraft = when (macro) {
    Macro.PROTEIN -> copy(protein = grams, proteinPercent = percent)
    Macro.CARBS -> copy(carbs = grams, carbsPercent = percent)
    Macro.FAT -> copy(fat = grams, fatPercent = percent)
}

private fun validKcal(text: String): Double? = positiveAmountOf(text)?.takeIf(::kcalTargetValid)

/** One decimal, as a percent field shows a number it was not typed with. */
private fun percentField(percent: Double): String = editableNumber((percent * 10).roundToInt() / 10.0)

private fun gramsField(grams: Double): String = if (grams > 0) editableNumber(grams) else ""

/** The fields as shares at [kcal]; null while a macro field holds something that is no number. */
private fun ProfileDraft.macroShares(kcal: Double): Map<Macro, MacroShare>? {
    if (Macro.entries.any { macro -> gramsText(macro).let { it.isNotBlank() && positiveAmountOf(it) == null } }) return null
    if (Macro.entries.any { macro -> percentText(macro).let { it.isNotBlank() && parseDecimal(it) == null } }) return null
    return sharesOf({ positiveAmountOf(gramsText(it)) }, { parseDecimal(percentText(it)) }, followingMacros, kcal)
}

private fun ProfileDraft.withShares(shares: Map<Macro, MacroShare>): ProfileDraft =
    Macro.entries.fold(this) { draft, macro ->
        val share = shares.getValue(macro)
        val percent = when {
            share.entered != MacroEntered.PERCENT -> ""
            // A percent that stayed as it was reads as it was typed.
            parseDecimal(percentText(macro)) == share.percent -> percentText(macro)
            else -> percentField(share.percent)
        }
        draft.withMacroTexts(macro, gramsField(share.grams), percent)
    }.copy(followingMacros = followingOf(shares))

/** The percent a macro's field shows: as entered, else its share of a valid Daily kcal, whole; blank without one. */
fun ProfileDraft.percentShown(macro: Macro): String {
    val own = percentText(macro)
    if (own.isNotBlank()) return own
    val kcal = validKcal(this.kcal) ?: return ""
    val grams = positiveAmountOf(gramsText(macro)) ?: return ""
    return percentOf(grams, macro, kcal).roundToInt().toString()
}

/**
 * A macro's field typed. Its text stays as typed, capped at 100%; with a valid Daily kcal and a number, the other two
 * share the rest. With neither, only this field changes: there is nothing to share yet.
 */
fun ProfileDraft.withMacroTyped(macro: Macro, text: String, byPercent: Boolean): ProfileDraft {
    val asTyped = withMacroTexts(macro, if (byPercent) gramsText(macro) else text, if (byPercent) text else "")
        .copy(followingMacros = followingMacros - macro)
    val kcal = validKcal(this.kcal) ?: return asTyped
    val shares = asTyped.macroShares(kcal) ?: return asTyped
    val number = (if (byPercent) parseDecimal(text) else positiveAmountOf(text)) ?: return asTyped
    val by = if (byPercent) MacroEntered.PERCENT else MacroEntered.GRAMS
    val next = rebalanceMacros(shares, kcal, macro, number, by)
    val edited = next.getValue(macro)
    val capped = if (byPercent) number > edited.percent else number > edited.grams
    val shownGrams = when {
        !byPercent -> if (capped) gramsField(edited.grams) else text
        else -> gramsField(edited.grams)
    }
    val shownPercent = when {
        !byPercent -> ""
        capped -> percentField(edited.percent)
        else -> text
    }
    return asTyped.withShares(next).withMacroTexts(macro, shownGrams, shownPercent)
}

/** Daily kcal set to [text]; with a valid number the macros follow by how each was entered. */
fun ProfileDraft.withDailyKcal(text: String): ProfileDraft {
    val typed = copy(kcal = text)
    val kcal = validKcal(text) ?: return typed
    val shares = macroShares(kcal) ?: return typed
    return typed.withShares(macrosAtKcal(shares, kcal))
}
