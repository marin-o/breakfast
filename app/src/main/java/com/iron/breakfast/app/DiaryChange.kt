package com.iron.breakfast.app

import com.iron.breakfast.DiaryChangeKind
import com.iron.breakfast.today.UndoOffer
import com.iron.breakfast.undoAfterLog
import com.iron.breakfast.undoEndsAt

/**
 * A committed diary write, published by [DiarySession] after it reloaded the diary. Listeners react to the kind, not
 * to the call site: Undo ends or keeps by [undoAfterChange], the Library list is recomputed when [refreshesLibrary].
 */
internal sealed interface DiaryChange {
    /** The kind the Undo rule [undoEndsAt] reads. */
    val kind: DiaryChangeKind
    /** Stored rows changed, so the session reloads [DiarySession.foods] and [DiarySession.entries] first. */
    val rowsChanged: Boolean get() = true
    /** The Library list is recomputed: the write changed the foods it lists or was made from the Library side. */
    val refreshesLibrary: Boolean

    /**
     * Add on the Log page, or a Library mixed batch. [offer] is the batch's own Undo, which replaces the pending one;
     * null (the batch saved nothing) keeps it ([undoAfterLog]).
     */
    data class BatchLogged(val offer: UndoOffer.Logged?) : DiaryChange {
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

    /** An entry deleted; [offer] is the Undo that puts it back, which replaces the pending one. */
    data class EntryDeleted(val offer: UndoOffer.Deleted) : DiaryChange {
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

    /** A food saved, edited, archived, restored or cached without a log. */
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

/** The Undo offer after [change]: a write with its own Undo replaces [current]; otherwise [undoEndsAt] decides. */
internal fun undoAfterChange(current: UndoOffer?, change: DiaryChange): UndoOffer? = when (change) {
    is DiaryChange.BatchLogged -> undoAfterLog(current, change.offer)
    is DiaryChange.EntryDeleted -> change.offer
    else -> if (undoEndsAt(change.kind)) null else current
}
