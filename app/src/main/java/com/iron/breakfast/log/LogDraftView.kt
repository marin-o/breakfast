package com.iron.breakfast.log

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.iron.breakfast.domain.DraftRow
import com.iron.breakfast.domain.RankedFood
import com.iron.breakfast.domain.SegmentResolution
import com.iron.breakfast.domain.Calculation

/** What the Log page shows for one draft, computed in one planner pass by [BreakfastViewModel.logDraftView]. */
internal data class LogDraftView(
    /** The draft's rows in typed order: entries, and parts shown as "Not in your foods". */
    val rows: List<DraftRow>,
    /** The group under the rows: the word being typed's suggestions, or the part's choices. */
    val choices: List<RankedFood>,
    /** The "which one?" hint, shown for real choices only. */
    val choiceHint: Boolean,
    /** True exactly when Add saves the part being typed; the first choice or suggestion is then the food it saves. */
    val preselected: Boolean,
    val activePhrase: String,
    /** What Add will save; the add bar's sum and count come from this. */
    val logged: List<SegmentResolution>,
    /** The kcal each of [choices] would log, in the same order. */
    val choiceCalculations: List<Calculation> = emptyList(),
)
