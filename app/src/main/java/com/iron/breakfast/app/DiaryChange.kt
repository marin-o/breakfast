package com.iron.breakfast.app

import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.today.UndoOffer
import com.iron.breakfast.today.undoAfterLog
import com.iron.breakfast.today.undoEndsAt

/**
 * A committed diary write, published by [DiarySession] after it reloaded the diary. Listeners react to the kind, not
 * to the call site: Undo ends, keeps or replaces its offer by [undoAfterChange] (`today/UndoController`), "Not right?"
 * feedback follows a logged batch (`today/MatchFeedback`), the Library list is recomputed when [refreshesLibrary].
 */
internal sealed interface DiaryChange {
    /** The kind the Undo rule [undoEndsAt] reads. */
    val kind: DiaryChangeKind
    /** Stored rows changed, so the session reloads [DiarySession.foods] and [DiarySession.entries] first. */
    val rowsChanged: Boolean get() = true
    /** The Library list is recomputed: the write changed the foods it lists or was made from the Library side. */
    val refreshesLibrary: Boolean

    /**
     * Add on the Log page, or a Library mixed batch. [entries] are the rows it saved, as saved; they get their own Undo
     * (with [message] as its text), which replaces the pending one. None (the batch saved nothing) keeps the pending
     * one ([undoAfterLog]).
     */
    data class BatchLogged(val message: String, val entries: List<MealEntry>) : DiaryChange {
        override val kind get() = DiaryChangeKind.LOG
        override val refreshesLibrary get() = false
    }

    /** Foods logged from the Library, from product review, or as a new food with a meal. */
    data object FoodsLogged : DiaryChange {
        override val kind get() = DiaryChangeKind.LOG
        override val refreshesLibrary get() = true
    }

    /** One part of the resolve queue logged from the resolver. */
    data object QueuedPartLogged : DiaryChange {
        override val kind get() = DiaryChangeKind.LOG
        override val refreshesLibrary get() = false
    }

    /** An entry's amount, unit, food, meal or day changed (quick or full edit). */
    data object EntryEdited : DiaryChange {
        override val kind get() = DiaryChangeKind.EDIT
        override val refreshesLibrary get() = false
    }

    /**
     * An entry edit that committed after its edit had closed (the quick-edit sheet was left while it saved). The
     * diary is reloaded so it shows the stored row; nothing else follows, and the pending Undo stays, as it always
     * has for such a save ([undoEndsAt]).
     */
    data object EntryEditedAfterClose : DiaryChange {
        override val kind get() = DiaryChangeKind.EDIT_AFTER_CLOSE
        override val refreshesLibrary get() = false
    }

    /** [entry] deleted; its Undo, which puts it back, replaces the pending one. */
    data class EntryDeleted(val entry: MealEntry) : DiaryChange {
        override val kind get() = DiaryChangeKind.DELETE
        override val refreshesLibrary get() = false
    }

    /** An Undo applied: a deleted entry restored, or a logged batch removed. */
    data object UndoApplied : DiaryChange {
        override val kind get() = DiaryChangeKind.UNDO
        override val refreshesLibrary get() = false
    }

    /** A "Not right?" pick replaced an entry's food. */
    data object MatchCorrected : DiaryChange {
        override val kind get() = DiaryChangeKind.NOT_RIGHT_PICK
        override val refreshesLibrary get() = false
    }

    /** A "Not right?" confirmation: only the learned preference is written, no row on screen changes. */
    data object MatchConfirmed : DiaryChange {
        override val kind get() = DiaryChangeKind.CONFIRM_MATCH
        override val rowsChanged get() = false
        override val refreshesLibrary get() = false
    }

    /** A food saved, edited, deleted or cached without a log. */
    data object FoodsChanged : DiaryChange {
        override val kind get() = DiaryChangeKind.FOOD_EDIT
        override val refreshesLibrary get() = true
    }

    /** The whole diary replaced by an import, or deleted by Delete all data. */
    data object DiaryReplaced : DiaryChange {
        override val kind get() = DiaryChangeKind.REPLACE
        override val refreshesLibrary get() = true
    }
}

/**
 * The Undo offer after [change], published at [nowMs]: a write with its own Undo replaces [current] with an offer made
 * now; otherwise [undoEndsAt] decides.
 */
internal fun undoAfterChange(current: UndoOffer?, change: DiaryChange, nowMs: Long): UndoOffer? = when (change) {
    is DiaryChange.BatchLogged ->
        undoAfterLog(current, change.entries.takeIf { it.isNotEmpty() }?.let { UndoOffer.Logged(change.message, it, nowMs) })
    is DiaryChange.EntryDeleted -> UndoOffer.Deleted(change.entry, nowMs)
    else -> if (undoEndsAt(change.kind)) null else current
}

/**
 * The kind of a diary change, for [undoEndsAt]. [FOOD_EDIT] is a food saved, edited or deleted without a log;
 * [UNDO] an Undo applied; [REPLACE] the whole diary replaced (import) or deleted; [EDIT_AFTER_CLOSE] an entry edit
 * that committed after its edit had closed.
 */
internal enum class DiaryChangeKind { LOG, EDIT, EDIT_AFTER_CLOSE, DELETE, NOT_RIGHT_PICK, CONFIRM_MATCH, FOOD_EDIT, UNDO, REPLACE }
