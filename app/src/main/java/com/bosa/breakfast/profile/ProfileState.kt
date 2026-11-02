package com.bosa.breakfast.profile

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bosa.breakfast.app.DiaryChange
import com.bosa.breakfast.app.DiarySession
import com.bosa.breakfast.app.Notices
import com.bosa.breakfast.domain.DailyBurn
import com.bosa.breakfast.domain.Goal
import com.bosa.breakfast.domain.GoalKcal
import com.bosa.breakfast.domain.LoseWarning
import com.bosa.breakfast.domain.Macro
import com.bosa.breakfast.domain.MacroPreset
import com.bosa.breakfast.domain.TargetRow
import com.bosa.breakfast.domain.WeightProjection
import com.bosa.breakfast.domain.burnBody
import com.bosa.breakfast.domain.dailyAdjusted
import com.bosa.breakfast.domain.dailyBurn
import com.bosa.breakfast.domain.goalKcal
import com.bosa.breakfast.domain.loseWarnings
import com.bosa.breakfast.domain.paceTooFast
import com.bosa.breakfast.domain.parseDecimal
import com.bosa.breakfast.domain.positiveAmountOf
import com.bosa.breakfast.domain.targetsOf
import com.bosa.breakfast.domain.weightProjection
import com.bosa.breakfast.domain.withDailyKcal
import com.bosa.breakfast.domain.withGoal
import com.bosa.breakfast.domain.withMacroTyped
import com.bosa.breakfast.domain.withPace
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

/** The writes the profile makes to the diary, the curve and the target history; the view model adapts `BreakfastEngine`. */
internal interface WeightStore {
    /** Stores [point] beside any on its day. */
    suspend fun add(point: WeightPoint)
    /** Stores [point] in place of its day's latest, if there is one. */
    suspend fun replaceLatest(point: WeightPoint)
    suspend fun delete(id: Long)
    /** Stores [row] in place of any on its day. */
    suspend fun saveTarget(row: TargetRow)
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
 * dropping a changed draft, by the food form's rule. [reload] follows a diary replaced or deleted. [adjustDaily] is the
 * daily automatic adjustment. Every change of targets, manual or automatic, is stored through [writeTargets].
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

    /** Daily burn from the draft's body; null until height, age, current weight and activity are valid. */
    val burn: DailyBurn? by derivedStateOf {
        draft.burnBody()?.let { dailyBurn(it, session.weightPoints, session.entries, today()) }
    }

    /** What the draft's goal and pace give, with whether the lowest applied; null without both. */
    val goalResult: GoalKcal? by derivedStateOf {
        val goal = draft.goal
        val estimate = burn
        if (goal == null || estimate == null) null else goalKcal(estimate.kcal, goal, positiveAmountOf(draft.pace), draft.sex)
    }

    val paceTooFast: Boolean by derivedStateOf {
        paceTooFast(draft.goal, positiveAmountOf(draft.pace), positiveAmountOf(draft.current))
    }

    val loseWarnings: Set<LoseWarning> by derivedStateOf {
        loseWarnings(draft.goal, parseDecimal(draft.height), draft.age.toIntOrNull(), parseDecimal(draft.current), parseDecimal(draft.target))
    }

    /** What the Weight page says below its chart, from the saved target. */
    val projection: WeightProjection by derivedStateOf {
        weightProjection(session.weightPoints, values.targetWeight, values.goal, today())
    }

    /** The draft as it was when the kcal field's edit began, so a target passing through 2, 20, 200 on its way to 2000 loses nothing. */
    private var kcalBase: ProfileDraft? = null

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
        kcalBase = null
        draft = next
    }

    /** Daily kcal typed by hand: the goal is cleared, and the macros follow by how each was entered. */
    fun editKcal(text: String) {
        val base = kcalBase ?: draft
        kcalAsked = false
        draft = base.withDailyKcal(text).copy(goal = null)
        kcalBase = base
    }

    /** One macro field typed: the other two share the rest. */
    fun editMacro(macro: Macro, text: String, byPercent: Boolean) = edit(draft.withMacroTyped(macro, text, byPercent))

    /** A goal chosen: Daily kcal fills in when the pace allows it. */
    fun chooseGoal(goal: Goal) = edit(draft.withGoal(goal, burn?.kcal))

    /** Kg a week chosen or typed: Daily kcal fills in by the goal. */
    fun choosePace(text: String, custom: Boolean) = edit(draft.withPace(text, custom, burn?.kcal))

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
        adjustDaily()
    }

    /**
     * Once a day, called when storage opens and on each return to the app: a saved goal's Daily kcal is recalculated
     * ([dailyAdjusted]), and targets that have no history yet (saved before it existed, or from an older file) start one.
     * Not the user's write: it takes no gate and refuses no tap, and while the page is open it waits, so a draft never
     * moves under the user. Its row today is what makes a second call that day do nothing.
     */
    fun adjustDaily() {
        if (!session.ready || open) return
        val before = values
        val history = session.targetHistory
        val after = dailyAdjusted(before, session.weightPoints, session.entries, history, today()) ?: before
        val starts = history.isEmpty() && targetsOf(before, today()).hasAny
        if (after == before && !starts) return
        session.background(DiaryChange.WeightCurveChanged) {
            writeTargets(after)
            prefs.write(after)
            if (values == before) { values = after; if (!open) resetDraft() }
            true
        }
    }

    /** The one way targets are stored: today's row of the history, for [next], manual or automatic. */
    private suspend fun writeTargets(next: ProfileValues) = store.saveTarget(targetsOf(next, today()))

    fun save() {
        if (!canSave) return
        val next = profileOf(draft, values) ?: return
        val kg = curveWeightOf(values, next, addToCurve, hasPointToday)
        val targetsChanged = targetsOf(next, "") != targetsOf(values, "")
        if (kg == null && !targetsChanged) { commit(next); return }
        val point = kg?.let { WeightPoint(today(), it, clock()) }
        // A save that turns the setting off still adds: turning it off keeps every point there.
        val adds = next.severalWeights || values.severalWeights
        write(onSuccess = { commit(next) }) {
            if (point != null) { if (adds) store.add(point) else store.replaceLatest(point) }
            if (targetsChanged) writeTargets(next)
        }
    }

    private fun commit(next: ProfileValues) {
        prefs.write(next)
        values = next
        resetDraft()
    }

    private fun resetDraft() {
        draft = ProfileDraft.of(values)
        kcalBase = null
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
