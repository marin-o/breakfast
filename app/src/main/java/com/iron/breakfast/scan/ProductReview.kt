package com.iron.breakfast.scan

import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.ui.pretty
import com.iron.breakfast.domain.ShorthandParser
import com.iron.breakfast.domain.TypedPart
import com.iron.breakfast.domain.Calculation
import com.iron.breakfast.domain.toMeasureUnit

enum class ProductReviewOrigin { MEAL, LIBRARY, QUEUE }

internal data class ProductPrefill(val amount: Double, val unit: MeasureUnit)

internal fun productNutritionBasisLabel(food: FoodRecord): String =
    "Calories per ${food.nutrition.amount.pretty()} ${food.nutrition.unit.label}"

/** [typed] is [raw] as the Log read it; the view model passes the planner's reading, so a glued amount is kept. */
internal fun productPrefill(
    raw: String,
    food: FoodRecord,
    typed: TypedPart = ShorthandParser().parse(raw).let { TypedPart(it.foodPhrase, it.amount, it.unit) },
): ProductPrefill = ProductPrefill(
    amount = typed.amount ?: 100.0,
    unit = typed.unit ?: food.nutrition.unit
)

internal fun parseProductUnit(value: String): MeasureUnit? = value.trim().toMeasureUnit()

internal data class ProductDraft(
    val food: FoodRecord,
    val amount: Double,
    val unit: MeasureUnit,
    val calculation: Calculation
)
