package com.iron.breakfast.ui

import java.util.Locale
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.settings.markDecimals
import com.iron.breakfast.settings.DisplayDecimal

internal fun MealSlot.label() = name.lowercase().replaceFirstChar { it.titlecase() }
/** A number for a field the user edits: whole without ".0", else one decimal, always with a point (input reads both marks). */
internal fun Double.plain() = if (this % 1 == 0.0) "%.0f".format(Locale.ROOT, this) else "%.1f".format(Locale.ROOT, this)
/** [plain] as shown on screen: with the "Decimal mark" setting's mark. */
internal fun Double.pretty() = markDecimals(plain(), DisplayDecimal.mark)
