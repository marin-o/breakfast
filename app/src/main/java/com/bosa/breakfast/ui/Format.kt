package com.bosa.breakfast.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.bosa.breakfast.domain.MealSlot
import com.bosa.breakfast.domain.NutritionValues
import com.bosa.breakfast.settings.markDecimals
import com.bosa.breakfast.settings.DisplayDecimal
import com.bosa.breakfast.ui.theme.Macro

internal fun MealSlot.label() = name.lowercase().replaceFirstChar { it.titlecase() }
/** A number as a row reads it: whole without ".0", else one decimal, with a point; [pretty] marks it. A field pre-fills with `domain/editableNumber`. */
internal fun Double.plain() = if (this % 1 == 0.0) "%.0f".format(Locale.ROOT, this) else "%.1f".format(Locale.ROOT, this)
/** [plain] as shown on screen: with the "Decimal mark" setting's mark. */
internal fun Double.pretty() = markDecimals(plain(), DisplayDecimal.mark)

/** "Wed 14 Oct", English: every day label in the app. */
internal fun shortDateLabel(dayKey: String): String =
    runCatching { LocalDate.parse(dayKey).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)) }.getOrDefault(dayKey)

/** "12 Oct", or "12 Oct 26" [withYear]: where the same day could otherwise read twice. */
internal fun dayMonthLabel(date: LocalDate, withYear: Boolean = false): String =
    date.format(DateTimeFormatter.ofPattern(if (withYear) "d MMM yy" else "d MMM", Locale.ENGLISH))

/** "07:52", the time of a moment on this phone. */
internal fun clockLabel(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withZone(zone).format(Instant.ofEpochMilli(epochMs))

/** "1 food", "13 foods". */
internal fun countLabel(count: Int, one: String, many: String): String = if (count == 1) "1 $one" else "$count $many"

/** A macro's amount as shown: "—" when it is not known, never 0. */
internal fun macroText(grams: Double?): String = grams?.pretty() ?: "—"

/** One piece of a line of macros: its [text] and the [macro] its dot marks, if it has one. */
internal data class MacroPart(val text: String, val macro: Macro? = null)

/** The parts as one string, as TalkBack says them; on screen they have no commas. */
internal fun List<MacroPart>.lineText(): String = joinToString(", ") { it.text }

/** "Protein 8 g", "carbs 40 g", "fat 4 g", under a kcal figure (M-Library-AmountSheet, M-Entry-FullEdit). */
internal fun macroLineParts(values: NutritionValues): List<MacroPart> = macroParts(values, "Protein", "carbs", "fat", " g")

/** "P 2", "C 9", "F 8", inside the scan review's line. */
internal fun macroLetterParts(values: NutritionValues): List<MacroPart> = macroParts(values, "P", "C", "F", "")

private fun macroParts(values: NutritionValues, protein: String, carbs: String, fat: String, unit: String): List<MacroPart> = listOf(
    MacroPart("$protein ${macroText(values.protein)}$unit", Macro.PROTEIN),
    MacroPart("$carbs ${macroText(values.carbohydrates)}$unit", Macro.CARBS),
    MacroPart("$fat ${macroText(values.fat)}$unit", Macro.FAT),
)
