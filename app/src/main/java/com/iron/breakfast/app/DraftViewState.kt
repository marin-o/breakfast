package com.iron.breakfast.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.settings.SettingsState

/**
 * What a draft shows, one planner pass per change, off the main thread ([compute]) so typing never waits on matching;
 * the last view shows until the next is ready. The keys here are everything the planner reads.
 */
@Composable
internal fun <V> rememberDraftView(
    text: String,
    committed: Boolean,
    pins: Map<String, FoodRecord>,
    session: DiarySession,
    settings: SettingsState,
    first: () -> V,
    compute: suspend (text: String, committed: Boolean, pins: Map<String, FoodRecord>) -> V,
): V {
    var view by remember { mutableStateOf(first()) }
    LaunchedEffect(text, committed, pins, session.foods, session.ready, settings.countTyping, settings.learnFromPicks) {
        view = compute(text, committed, pins)
    }
    return view
}
