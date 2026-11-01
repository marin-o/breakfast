package com.bosa.breakfast.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.bosa.breakfast.domain.ShorthandParser
import com.bosa.breakfast.domain.FoodRecord
import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.domain.MealSlot
import com.bosa.breakfast.domain.LogPlanner
import com.bosa.breakfast.domain.ShorthandLexicon
import com.bosa.breakfast.domain.TypedPart

/** The library list's query for what the bar shows: a typed amount is not part of the food name the list filters by. */
internal fun librarySearchText(text: String, parser: ShorthandParser): String =
    parser.parse(text).let { if (it.amount == null) text else it.foodPhrase }

internal data class LibraryLogDraft(
    val food: FoodRecord,
    val amount: Double,
    val unit: MeasureUnit,
    val target: MealSlot? = null,
    /** The serving's name for [MeasureUnit.SERVING]. */
    val servingName: String? = null,
)

internal fun libraryLogDraft(food: FoodRecord, target: MealSlot, amount: Double, unit: MeasureUnit, servingName: String? = null): LibraryLogDraft =
    LibraryLogDraft(food, amount, unit, target, servingName)

internal data class LibraryInputSegment(
    val raw: String,
    val foodPhrase: String,
    val amount: Double?,
    val unit: MeasureUnit?
)

/** The library's view of a draft: the parts without meal words, each with the amount and unit it types. */
internal fun libraryInputSegments(value: String, planner: LogPlanner? = null): List<LibraryInputSegment> {
    val parser = ShorthandParser()
    return (planner?.segments(value)?.map { it.text } ?: parser.split(value))
        .filter { ShorthandLexicon.mealSlotOf(it) == null }
        .map { raw ->
            val typed = planner?.typed(raw) ?: parser.parse(raw).let { TypedPart(it.foodPhrase, it.amount, it.unit) }
            LibraryInputSegment(raw, typed.foodPhrase, typed.amount, typed.unit)
        }
}
