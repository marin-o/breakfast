package com.iron.breakfast.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.settings.markDecimals
import com.iron.breakfast.settings.DisplayDecimal

internal fun MealSlot.label() = name.lowercase().replaceFirstChar { it.titlecase() }
/** A number as a row reads it: whole without ".0", else one decimal, with a point; [pretty] marks it. A field pre-fills with `domain/editableNumber`. */
internal fun Double.plain() = if (this % 1 == 0.0) "%.0f".format(Locale.ROOT, this) else "%.1f".format(Locale.ROOT, this)
/** [plain] as shown on screen: with the "Decimal mark" setting's mark. */
internal fun Double.pretty() = markDecimals(plain(), DisplayDecimal.mark)

/** "Wed 14 Oct", English: every day label in the app. */
internal fun shortDateLabel(dayKey: String): String =
    runCatching { LocalDate.parse(dayKey).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)) }.getOrDefault(dayKey)

/** "07:52", the time of a moment on this phone. */
internal fun clockLabel(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withZone(zone).format(Instant.ofEpochMilli(epochMs))

/** "1 food", "13 foods". */
internal fun countLabel(count: Int, one: String, many: String): String = if (count == 1) "1 $one" else "$count $many"

/** A macro's amount as shown: "—" when it is not known, never 0. */
internal fun macroText(grams: Double?): String = grams?.pretty() ?: "—"

/** "Protein 8 g, carbs 40 g, fat 4 g", under a kcal figure (M-Library-AmountSheet, M-Entry-FullEdit). */
internal fun macroLine(values: NutritionValues): String =
    "Protein ${macroText(values.protein)} g, carbs ${macroText(values.carbohydrates)} g, fat ${macroText(values.fat)} g"
