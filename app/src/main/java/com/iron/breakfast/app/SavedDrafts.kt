package com.iron.breakfast.app

import androidx.lifecycle.SavedStateHandle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.iron.breakfast.food.FoodDraftState
import com.iron.breakfast.food.FoodDraftFields

// Typed work that survives the process being killed in the background: the log draft and a half-filled food form,
// and which day the diary shows. Only these keys are written here. The Open Food Facts login is never among them.

internal const val LOG_DRAFT_KEY = "log_draft"
internal const val LOG_DRAFT_COMMITTED_KEY = "log_draft_committed"
internal const val FOOD_FORM_KEY = "food_form"
internal const val SELECTED_DAY_KEY = "selected_day"
internal const val FOLLOWING_TODAY_KEY = "following_today"

/** The day the diary shows: [followingToday], or the past day the user picked ([dayKey]). */
internal data class SavedDay(val dayKey: String?, val followingToday: Boolean)

/** Following today stores no date, so a restore never brings back a stale today. */
internal fun saveSelectedDay(state: SavedStateHandle, day: SavedDay) {
    state[FOLLOWING_TODAY_KEY] = day.followingToday
    if (day.followingToday || day.dayKey == null) state.remove<String>(SELECTED_DAY_KEY) else state[SELECTED_DAY_KEY] = day.dayKey
}

/** Nothing saved means today. A date saved by an older build, with no flag, stays the picked day. */
internal fun restoreSelectedDay(state: SavedStateHandle): SavedDay {
    val dayKey = state.get<String>(SELECTED_DAY_KEY)
    return SavedDay(dayKey, state.get<Boolean>(FOLLOWING_TODAY_KEY) ?: (dayKey == null))
}

internal data class SavedLogDraft(val text: String, val committed: Boolean)

/** Mirrors the log draft. An empty draft (added or cleared) removes both keys, so it never comes back. */
internal fun saveLogDraft(state: SavedStateHandle, draft: SavedLogDraft) {
    if (draft.text.isEmpty()) {
        state.remove<String>(LOG_DRAFT_KEY)
        state.remove<Boolean>(LOG_DRAFT_COMMITTED_KEY)
    } else {
        state[LOG_DRAFT_KEY] = draft.text
        state[LOG_DRAFT_COMMITTED_KEY] = draft.committed
    }
}

internal fun restoreLogDraft(state: SavedStateHandle): SavedLogDraft =
    SavedLogDraft(state.get<String>(LOG_DRAFT_KEY).orEmpty(), state.get<Boolean>(LOG_DRAFT_COMMITTED_KEY) ?: false)

/** An open New food ([editingFoodId] null) or Edit food form, with [baseline], the form as it opened, for the Back question. */
@Serializable
internal data class SavedFoodForm(
    val editingFoodId: String?,
    val draft: FoodDraftState,
    val baseline: FoodDraftFields,
)

private val formJson = Json { ignoreUnknownKeys = true }

/** Mirrors the open food form; null (closed or saved) removes the key. */
internal fun saveFoodForm(state: SavedStateHandle, form: SavedFoodForm?) {
    if (form == null) state.remove<String>(FOOD_FORM_KEY)
    else state[FOOD_FORM_KEY] = formJson.encodeToString(SavedFoodForm.serializer(), form)
}

/** The saved food form, or null when there is none or it cannot be read. */
internal fun restoreFoodForm(state: SavedStateHandle): SavedFoodForm? =
    state.get<String>(FOOD_FORM_KEY)?.let { runCatching { formJson.decodeFromString(SavedFoodForm.serializer(), it) }.getOrNull() }
