package com.iron.breakfast.app

/**
 * What happens to the session when the diary is replaced (Import diary, Replace diary) or deleted (Delete all data):
 * every owner drops the state that referred to the old diary, the same way for both. Registered as a [DiarySession]
 * listener, it runs [steps] in order on [DiaryChange.DiaryReplaced].
 *
 * Undo and "Not right?" feedback end through that same change in their own listeners; the steps are the rest (log
 * pins, the resolve queue and resolver, the entry detail, a contribution candidate). Settings are not session state:
 * Delete all resets them only when asked, and import never touches them.
 */
internal class SessionReset(private val steps: List<() -> Unit>) {
    fun onChange(change: DiaryChange) {
        if (change == DiaryChange.DiaryReplaced) steps.forEach { it() }
    }
}
