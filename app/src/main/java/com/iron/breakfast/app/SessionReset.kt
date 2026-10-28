package com.iron.breakfast.app

import com.iron.breakfast.contribution.ContributionFlow
import com.iron.breakfast.entry.EntryEdit
import com.iron.breakfast.log.LogComposer
import com.iron.breakfast.resolve.ResolveFlow

/**
 * What happens to the session when the diary is replaced (Import diary, Replace diary) or deleted (Delete all data):
 * every owner drops the state that referred to the old diary, the same way for both. Registered as a [DiarySession]
 * listener, it runs [steps] in order on [DiaryChange.DiaryReplaced].
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
 * What the session drops with the old diary, in order: the Log's pins, a contribution candidate with its preflight and review, the
 * resolve queue and resolver, and the entry detail.
 */
internal fun sessionResetSteps(log: LogComposer, contribution: ContributionFlow, resolve: ResolveFlow, entryEdit: EntryEdit): List<() -> Unit> =
    listOf(log::clearPins, contribution::reset, resolve::abandon, entryEdit::closeDetail)
