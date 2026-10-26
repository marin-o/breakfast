package com.iron.breakfast.contribution

import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.data.OffContributionDraft

/** The body of the contribution failure screen when the answer to a sent contribution was lost. */
internal const val CONTRIBUTION_UNCERTAIN = "Check Open Food Facts before sending again."

internal fun contributionLanguageFor(name: String): String =
    if (name.any { it in '\u0400'..'\u04ff' }) "mk" else "en"

internal fun contributionDraftAfterLocalSave(food: FoodRecord, fromScan: Boolean): OffContributionDraft? =
    if (fromScan) OffContributionDraft.fromFood(food) else null

/** The Contribute to Open Food Facts action shows where it is [available], unless "Contribute" is Hide. */
internal fun contributionShown(show: Boolean, available: Boolean): Boolean = show && available
