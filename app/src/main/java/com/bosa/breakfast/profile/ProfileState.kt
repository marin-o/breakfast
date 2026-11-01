package com.bosa.breakfast.profile

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bosa.breakfast.app.DiaryChange
import com.bosa.breakfast.app.DiarySession
import com.bosa.breakfast.app.Notices
import com.bosa.breakfast.domain.MacroPreset
import com.bosa.breakfast.domain.ProfileDraft
import com.bosa.breakfast.domain.ProfileValues
import com.bosa.breakfast.domain.WeightPoint
import com.bosa.breakfast.domain.curveOffered
import com.bosa.breakfast.domain.curveTickStart
import com.bosa.breakfast.domain.curveWeightOf
import com.bosa.breakfast.domain.newestFirst
import com.bosa.breakfast.domain.profileOf
import com.bosa.breakfast.domain.withPreset
import com.bosa.breakfast.domain.FormBack
import com.bosa.breakfast.domain.formBack
import com.bosa.breakfast.ui.clockLabel
import com.bosa.breakfast.ui.pretty
import com.bosa.breakfast.ui.shortDateLabel
import java.time.ZoneId
import kotlinx.serialization.Serializable

/** The writes the weight curve makes; the view model adapts `BreakfastEngine`. */
internal interface WeightStore {
    /** Stores [point] beside any on its day. */
    suspend fun add(point: WeightPoint)
    /** Stores [point] in place of its day's latest, if there is one. */
    suspend fun replaceLatest(point: WeightPoint)
    suspend fun delete(id: Long)
}

/** "Sat 31 Oct", and on a day with several of [points] its time: "Sat 31 Oct, 07:52". */
internal fun weightPointLabel(point: WeightPoint, points: List<WeightPoint>, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = shortDateLabel(point.dayKey)
    return if (points.count { it.dayKey == point.dayKey } > 1) "$date, ${clockLabel(point.savedAtEpochMs, zone)}" else date
}

/** The open Profile page as typed, for coming back after the process dies. */
@Serializable
internal data class SavedProfile(val draft: ProfileDraft, val addToCurve: Boolean)

/**
 * The Profile page's owner: whether it is [open], the saved [values] (Meals reads the targets from them), the
 * [draft] being typed, and the curve as the session reads it ([points]).
 *
 * Save writes through [session]: a point when the rules ask for one ([curveWeightOf]), then the values. Back asks before
 * dropping a changed draft, by the food form's rule. [reload] follows a diary replaced or deleted.
 */
internal class ProfileState(
    private val session: DiarySession,
    private val store: WeightStore,
    private val prefs: ProfilePrefs,
    private val notices: Notices,
    /** The diary day now, by the "Day starts at" setting. */
    private val today: () -> String,
    private val clock: () -> Long,
) {
    var open by mutableStateOf(false); private set
    var values by mutableStateOf(prefs.read()); private set
    var draft by mutableStateOf(ProfileDraft.of(values)); private set
    /** The user's own tick; until they touch it, [addToCurve] follows [curveTickStart]. */
    private var curveTick by mutableStateOf<Boolean?>(null)
    val addToCurve: Boolean get() = curveTick ?: curveTickStart(draft, values, hasPointToday)
    /** The kcal field asks to be filled, after a preset that needed it. */
    var kcalAsked by mutableStateOf(false); private set
    var discardOpen by mutableStateOf(false); private set
    /** The point the delete dialog asks about. */
    var deleteRequest by mutableStateOf<WeightPoint?>(null); private set

    /** Newest first; sorted again only when the curve changes, not on each key typed. */
    val points: List<WeightPoint> by derivedStateOf { newestFirst(session.weightPoints) }

    fun pointLabel(point: WeightPoint): String = weightPointLabel(point, session.weightPoints)

    /** What tells [point] from any other: the delete question and its button's TalkBack label. */
    fun pointSummary(point: WeightPoint): String = "${pointLabel(point)}, ${point.kg.pretty()} kg"

    private val hasPointToday: Boolean get() = today().let { day -> session.weightPoints.any { it.dayKey == day } }

    /** The "Add to curve" tick shows. An unchanged weight offered this way is not an unsaved edit. */
    val curveOffered: Boolean get() = curveOffered(draft, values, hasPointToday)

    /** There is something valid to save: a changed value, or a point the tick asks for. */
    val canSave: Boolean get() {
        val next = profileOf(draft, values) ?: return false
        return !session.busy && (next != values || curveWeightOf(values, next, addToCurve, hasPointToday) != null)
    }

    /** The page, from what is saved. */
    fun show() {
        resetDraft()
        open = true
    }

    /** The page as it was when the process died. */
    fun restore(saved: SavedProfile) {
        draft = saved.draft
        curveTick = saved.addToCurve
        open = true
    }

    fun savedPage(): SavedProfile? = if (open) SavedProfile(draft, addToCurve) else null

    fun edit(next: ProfileDraft) {
        if (next.kcal != draft.kcal) kcalAsked = false
        draft = next
    }

    /** A preset that needs the kcal target has none: the field says so until it is typed in. */
    fun applyPreset(preset: MacroPreset) {
        val next = draft.withPreset(preset)
        kcalAsked = next == null
        if (next != null) draft = next
    }

    fun toggleAddToCurve() { curveTick = !addToCurve }

    /** Back or the arrow: a changed draft asks before it is dropped; while a save runs, nothing happens. */
    fun leave() {
        when (formBack(saving = session.busy, dirty = draft != ProfileDraft.of(values))) {
            FormBack.IGNORE -> Unit
            FormBack.ASK -> discardOpen = true
            FormBack.CLOSE -> close()
        }
    }

    fun keepEditing() { discardOpen = false }
    fun discard() { if (session.busy) return; close() }

    fun close() {
        open = false
        resetDraft()
    }

    /** The stored values again, after the diary was replaced or deleted. */
    fun reload() {
        values = prefs.read()
        resetDraft()
    }

    fun save() {
        if (!canSave) return
        val next = profileOf(draft, values) ?: return
        val kg = curveWeightOf(values, next, addToCurve, hasPointToday)
        if (kg == null) { commit(next); return }
        val point = WeightPoint(today(), kg, clock())
        // A save that turns the setting off still adds: turning it off keeps every point there.
        val adds = next.severalWeights || values.severalWeights
        write(onSuccess = { commit(next) }) { if (adds) store.add(point) else store.replaceLatest(point) }
    }

    private fun commit(next: ProfileValues) {
        prefs.write(next)
        values = next
        resetDraft()
    }

    private fun resetDraft() {
        draft = ProfileDraft.of(values)
        curveTick = null
        kcalAsked = false
        discardOpen = false
        deleteRequest = null
    }

    fun askDelete(point: WeightPoint) { deleteRequest = point }
    fun cancelDelete() { deleteRequest = null }

    fun confirmDelete() {
        val point = deleteRequest ?: return
        deleteRequest = null
        write { store.delete(point.id) }
    }

    private fun write(onSuccess: () -> Unit = {}, run: suspend () -> Unit) {
        session.write(
            change = DiaryChange.WeightCurveChanged,
            onFailure = { notices.show(it.message ?: COULD_NOT_SAVE) },
            onSuccess = { _: Unit -> onSuccess() },
            run = run,
        )
    }
}

private const val COULD_NOT_SAVE = "Could not save."
