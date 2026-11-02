package mk.bosa.breakfast.settings

import java.util.Locale
import mk.bosa.breakfast.today.UNDO_WINDOW_MS
import mk.bosa.breakfast.domain.MealSlot

/** The day start one step from [hour]; it wraps 23 to 00 and 00 to 23. */
internal fun shiftDayStartHour(hour: Int, delta: Int): Int = Math.floorMod(hour + delta, 24)

/** "04:00". */
internal fun dayStartLabel(hour: Int): String = String.format(Locale.ROOT, "%02d:00", hour)

/** The "Undo" choice. */
internal enum class UndoWindow { FIFTEEN_SECONDS, FIVE_SECONDS, THIRTY_SECONDS, UNTIL_NEXT_ACTION }

/** How long an offer of [window] stays, in ms; null until the next log, edit or delete replaces it. */
internal fun undoWindowMs(window: UndoWindow): Long? = when (window) {
    UndoWindow.FIVE_SECONDS -> 5_000L
    UndoWindow.FIFTEEN_SECONDS -> UNDO_WINDOW_MS
    UndoWindow.THIRTY_SECONDS -> 30_000L
    UndoWindow.UNTIL_NEXT_ACTION -> null
}

/** The "Meal" choice: what a log with no chosen, typed or preset meal goes to. */
internal enum class MealMode { ASK, BY_TIME, LAST_USED }

/** "By time": before [breakfastUntil] o'clock is Breakfast, then Lunch, then Dinner, then Snacks. Each is later than the one before. */
internal data class MealBounds(val breakfastUntil: Int = 12, val lunchUntil: Int = 17, val dinnerUntil: Int = 22) {
    /** True when every boundary is a whole hour from 01:00 to 23:00 and later than the one above it. */
    val valid: Boolean get() = breakfastUntil in 1..23 && lunchUntil in 1..23 && dinnerUntil in 1..23 && breakfastUntil < lunchUntil && lunchUntil < dinnerUntil
}

/** Stored boundaries, or the defaults when any is missing or they are not in order. */
internal fun mealBoundsOf(breakfastUntil: Int, lunchUntil: Int, dinnerUntil: Int): MealBounds =
    MealBounds(breakfastUntil, lunchUntil, dinnerUntil).takeIf { it.valid } ?: MealBounds()

/** The boundary that ends [meal]'s time; Snacks has none. */
internal fun MealBounds.untilOf(meal: MealSlot): Int? = when (meal) {
    MealSlot.BREAKFAST -> breakfastUntil
    MealSlot.LUNCH -> lunchUntil
    MealSlot.DINNER -> dinnerUntil
    MealSlot.SNACKS -> null
}

/** [bounds] with [meal]'s boundary moved [delta] hours; unchanged when that would break the order or leave 01:00 to 23:00. */
internal fun shiftMealBound(bounds: MealBounds, meal: MealSlot, delta: Int): MealBounds {
    val shifted = when (meal) {
        MealSlot.BREAKFAST -> bounds.copy(breakfastUntil = bounds.breakfastUntil + delta)
        MealSlot.LUNCH -> bounds.copy(lunchUntil = bounds.lunchUntil + delta)
        MealSlot.DINNER -> bounds.copy(dinnerUntil = bounds.dinnerUntil + delta)
        MealSlot.SNACKS -> bounds
    }
    return if (shifted.valid) shifted else bounds
}

/** True when [meal]'s boundary can move [delta] hours. */
internal fun canShiftMealBound(bounds: MealBounds, meal: MealSlot, delta: Int): Boolean = shiftMealBound(bounds, meal, delta) != bounds

/** "Before day start": the meal for a log made before "Day starts at" (it belongs to the previous diary day). */
internal enum class BeforeDayStart { SNACKS, DINNER, ASK }
