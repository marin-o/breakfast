package mk.bosa.breakfast.contribution

import mk.bosa.breakfast.domain.sameBarcode
import mk.bosa.breakfast.domain.normalizeBarcode
import mk.bosa.breakfast.domain.isOpenFoodFactsSource
import mk.bosa.breakfast.domain.scriptOf
import mk.bosa.breakfast.domain.TextScript
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.data.OffContributionDraft

/** The body of the contribution failure screen when the answer to a sent contribution was lost. */
internal const val CONTRIBUTION_UNCERTAIN = "Check Open Food Facts before sending again."

/** A name all in Cyrillic is Macedonian; a name in Latin, or mixing both, is English (as M-OFF-Contribute-New draws). */
internal fun contributionLanguageFor(name: String): String =
    if (scriptOf(name) == TextScript.CYRILLIC) "mk" else "en"

internal fun contributionDraftAfterLocalSave(food: FoodRecord, fromScan: Boolean): OffContributionDraft? =
    if (fromScan) OffContributionDraft.fromFood(food) else null

/**
 * Open Food Facts foods keep their source code in `sourceId`; once the local barcode no longer
 * matches it, edited numbers must not be submitted under a different product code.
 */
internal fun contributionAvailable(food: FoodRecord): Boolean {
    val barcode = normalizeBarcode(food.barcode ?: return false) ?: return false
    // A food that cannot become a draft (implausible per 100 g, no g or ml basis) offers no action that would only refuse.
    if (OffContributionDraft.fromFood(food) == null) return false
    return !isOpenFoodFactsSource(food) || sameBarcode(food.sourceId, barcode)
}

/** The Contribute to Open Food Facts action shows where it is [available], unless "Contribute" is Hide. */
internal fun contributionShown(show: Boolean, available: Boolean): Boolean = show && available
