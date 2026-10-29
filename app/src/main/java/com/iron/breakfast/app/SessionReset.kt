package com.iron.breakfast.app

import com.iron.breakfast.contribution.ContributionFlow
import com.iron.breakfast.entry.EntryEdit
import com.iron.breakfast.food.FoodForm
import com.iron.breakfast.log.LogComposer
import com.iron.breakfast.resolve.ResolveFlow

/**
 * What happens to the session when the diary is replaced (Import diary, Replace diary, a fresh diary after damage)
 * or deleted (Delete all data): every owner drops the state that referred to the old diary, the same way for all.
 * Registered as a [DiarySession] listener, it runs [steps] in order on [DiaryChange.DiaryReplaced].
 *
 * Undo and "Not right?" feedback end through that same change in their own listeners; the steps are the rest
 * ([sessionResetSteps]). Settings are not session state:
 * Delete all resets them only when asked, and import never touches them.
 */
internal class SessionReset(private val steps: List<() -> Unit>) {
    fun onChange(change: DiaryChange) {
        if (change == DiaryChange.DiaryReplaced) steps.forEach { it() }
    }
}

/**
 * What the session drops with the old diary, in order: the Log's pins and its error (since B17: a "Some foods need a
 * match." about the old diary's foods no longer applies), a contribution candidate with its preflight and review, the
 * resolve queue and resolver, the entry detail, and an Edit food whose food the new diary does not have (since the
 * D15 fix: a fresh diary after damage resets the session too).
 */
internal fun sessionResetSteps(
    log: LogComposer, contribution: ContributionFlow, resolve: ResolveFlow, entryEdit: EntryEdit, foodForm: FoodForm,
): List<() -> Unit> =
    listOf(log::clearPins, log::clearError, contribution::reset, resolve::abandon, entryEdit::closeDetail, foodForm::closeIfFoodGone)
