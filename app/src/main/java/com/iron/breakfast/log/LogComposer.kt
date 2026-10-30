package com.iron.breakfast.log

import com.iron.breakfast.domain.DraftPins
import com.iron.breakfast.domain.MeasureUnit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iron.breakfast.app.DiaryChange
import com.iron.breakfast.app.LOGGED
import com.iron.breakfast.app.DiarySession
import com.iron.breakfast.app.SavedLogDraft
import com.iron.breakfast.domain.DraftRow
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.LogPlanner
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.SavedMeal
import com.iron.breakfast.domain.ShorthandLexicon
import com.iron.breakfast.domain.mealMarkerSlots
import com.iron.breakfast.domain.replaceDraftPart
import com.iron.breakfast.resolve.LogResolveRequest
import com.iron.breakfast.resolve.ResolveSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** What the Log reads and writes; the view model adapts `BreakfastEngine`. */
internal interface LogStore {
    /** The planner over the current foods and preferences, with [pins] resolving their parts. */
    fun planner(pins: Map<String, FoodRecord> = emptyMap()): LogPlanner
    /** Saves [text] to [meal] on [dayKey]: what resolves is logged, the rest comes back unresolved. */
    suspend fun save(text: String, meal: MealSlot, dayKey: String, pins: Map<String, FoodRecord>): SavedMeal
}

/**
 * The Log: the one draft Daily and Meal share, and the full-screen Log page that edits it.
 *
 * - **Draft:** [draft] and [committed] (Done or Add finished it, so every part follows the finished rules), restored
 *   from [initial] and mirrored by the coordinator ([savedDraft]); only Add or clearing it removes it. [pins] are the
 *   foods picked for it (a choice, a suggestion, a part resolved in the Library), by the part text they now read as;
 *   the planner resolves those parts to them, and they live as long as the draft.
 * - **Page:** [open], with the meal it was opened for ([preset]) and the one chosen from its menu ([chosen]);
 *   [targetMeal] by [logTargetMealOf]. [error] is the Log's own (Add's refusals and failures, "Some foods need a
 *   match."), shown on the Log page and on Daily; other features clear it through [clearError].
 * - **Submit:** Add saves the draft through [session]; what it could not match goes to the post-save handling
 *   ([afterSave], the resolve queue), with the draft as the queue's [ResolveSource].
 * - **Resolve:** a "Not in your foods" part goes to the Library through [resolvePart]; [resume] brings the Log back.
 */
internal class LogComposer(
    private val session: DiarySession,
    private val store: LogStore,
    private val selectedDay: () -> String,
    /** The Meal setting's meal at this moment, null for Ask (see [automaticMeal]). */
    private val automaticMeal: () -> MealSlot?,
    /** The meal on screen beneath the Log, where a part sent to the Library comes back to. */
    private val openMeal: () -> MealSlot?,
    /** An entry edit is open: the Log does not open over it. */
    private val editing: () -> Boolean,
    /** Post-save handling: the parts a save could not match, for [meal] (see `ResolveFlow.afterSave`). */
    private val afterSave: (unresolved: List<String>, meal: MealSlot, source: ResolveSource, typedAtSave: String) -> Unit,
    /** A part sent to the Library, opened on [meal] and searching [query] (see `ResolveFlow.beginPart`). */
    private val resolvePart: (request: LogResolveRequest, meal: MealSlot, query: String) -> Unit,
    /** Where the Log page's draft is planned ([computeDraftView]): off the main thread in the app. */
    private val compute: CoroutineDispatcher,
    /** The Log closed: its queue and searches end. */
    private val onQueueLeft: (source: ResolveSource) -> Unit = {},
    /** The draft changed and now holds these parts: those still queued stay. */
    private val onDraftEdited: (source: ResolveSource, parts: List<String>) -> Unit = { _, _ -> },
    /** The meal menu chose this: what is queued logs there. */
    private val onMealChosen: (source: ResolveSource, meal: MealSlot) -> Unit = { _, _ -> },
    /** The parts the queue holds now. */
    private val queuedParts: () -> List<String> = { emptyList() },
    initial: SavedLogDraft = SavedLogDraft("", false),
) : ResolveSource {
    var draft by mutableStateOf(initial.text); private set
    var committed by mutableStateOf(initial.committed); private set
    var error by mutableStateOf<String?>(null); private set
    var open by mutableStateOf(false); private set
    var preset by mutableStateOf<MealSlot?>(null); private set
    var chosen by mutableStateOf<MealSlot?>(null); private set
    var mealMenuOpen by mutableStateOf(false); private set
    /** True when the meal menu was opened by Add with no meal known, so choosing a meal logs straight away. */
    private var submitPending = false
    // Observable, so a pin added without a text change still recomputes the Log page's draft.
    private val pins = DraftPins()

    /** The draft for `SavedStateHandle`. */
    fun savedDraft(): SavedLogDraft = SavedLogDraft(draft, committed)

    /** The pins as they are now, for a draft computed off the main thread. */
    fun pinsSnapshot(): Map<String, FoodRecord> = pins.keys(draft, store::planner)

    /** The foods picked for the old diary are dropped with it (see `SessionReset`). */
    fun clearPins() { pins.clear() }

    /** A food was deleted: a part pinned to it is matched again, so Add cannot log (or bring back) that food. */
    fun dropPinsOf(foodId: String) { pins.dropFood(foodId) }

    /** A meal, the Library or an entry edit opened, or the diary was replaced: the Log's error no longer applies. */
    fun clearError() { error = null }

    /** Opens the Log page. [preset] is the meal of the `+` or composer that opened it; null for the Daily composer. */
    fun open(preset: MealSlot?) {
        if (editing()) return
        this.preset = preset
        chosen = null
        mealMenuOpen = false
        submitPending = false
        error = null
        open = true
    }

    /** Closes the Log page. The draft stays. */
    fun close() {
        onQueueLeft(this)
        open = false
        mealMenuOpen = false
        submitPending = false
    }

    fun openMealMenu() { mealMenuOpen = true }
    fun dismissMealMenu() { mealMenuOpen = false; submitPending = false }

    /** Choosing a meal from the menu wins over everything else; it logs at once only if Add asked for it. */
    fun chooseMeal(target: MealSlot) {
        chosen = target
        onMealChosen(this, target)
        mealMenuOpen = false
        if (submitPending) {
            submitPending = false
            submit()
        }
    }

    /** The keyboard's Done: the draft is committed, so its last word is no longer being typed. */
    fun commit() { committed = true }

    /** The Log page's typing. Clearing the draft also drops the foods picked for it. */
    fun onDraft(value: String) {
        // Add's write is taking this text: what it logs must not stay here to be logged again.
        if (session.busy) return
        applyDraft(value)
    }

    private fun applyDraft(value: String) {
        pins.edited(draft, value)
        draft = value
        if (value.isBlank()) pins.clear()
        val parts = queuedParts().takeIf { it.isNotEmpty() }?.let { store.planner(pinsSnapshot()).segments(value).map { span -> span.text } }
        if (parts != null) onDraftEdited(this, parts)
        // A queued part left last is finished, not being typed: it keeps its row.
        committed = committed && parts?.lastOrNull()?.let { it in queuedParts() } == true
    }

    /** The meal the Log page writes to, by [logTargetMealOf] precedence. Also drives the picker label. */
    fun targetMeal(): MealSlot? = logTargetMealOf(chosen, mealMarkerSlots(draft), preset, automaticMeal())

    /**
     * Everything the Log page shows for the current draft, computed in one pass from one planner, so the rows,
     * the choices and the add-bar sum all come from the same resolution.
     */
    /** [draftView] on [compute], so typing never waits on matching; the Log page shows the last one until the next is ready. */
    suspend fun computeDraftView(text: String, committed: Boolean, pins: Map<String, FoodRecord>): LogDraftView =
        withContext(compute) { draftView(text, committed, pins) }

    fun draftView(text: String = draft, committed: Boolean = this.committed, pins: Map<String, FoodRecord> = pinsSnapshot()): LogDraftView =
        logDraftViewOf(store.planner(pins), text, committed, session.ready)

    /**
     * Tapping a choice or a suggestion: the typed part becomes the amount, unit and name it would log, pinned to the
     * tapped food so it stays that food. Pins live as long as the draft (cleared on Add or an empty draft).
     */
    fun chooseSuggestion(food: FoodRecord) {
        val picked = store.planner(pinsSnapshot()).choose(draft, food, typing = !committed)
        val before = draft
        applyDraft(picked.input)
        // The tapped part is the last one: from where the text before it ends.
        picked.part?.let { pins.add(picked.input, it, food, picked.input.commonPrefixWith(before).length.coerceAtMost(picked.input.lastIndexOf(it))) }
    }

    /**
     * A food scanned from the Log page and stored: [amount] [unit] of it after the parts there are, pinned so it reads
     * as exactly that food. The Log's meal and the rest of the draft stay.
     */
    fun addScannedPart(food: FoodRecord, amount: Double, unit: MeasureUnit) {
        val planner = store.planner(pinsSnapshot())
        val part = planner.partText(food, amount, unit)
        val next = planner.withPartAdded(draft, part)
        applyDraft(next)
        pins.add(next, part, food, next.length - part.length)
    }

    /** Add on the Log page. */
    fun submit() {
        // Add finishes the draft in both "Count what I'm typing" modes: it saves what the finished draft counts.
        committed = true
        when (val plan = logSubmitPlan(session.canWrite, draft, mealMarkerSlots(draft), chosen, preset, automaticMeal())) {
            LogSubmitPlan.Ignore -> Unit
            is LogSubmitPlan.Error -> error = plan.message
            LogSubmitPlan.OpenMenu -> {
                error = null
                submitPending = true
                mealMenuOpen = true
            }
            is LogSubmitPlan.Save -> save(plan.target)
        }
    }

    private fun save(target: MealSlot) {
        if (!session.canWrite) return
        // Save takes the draft as typed: the planner skips meal words by span, so save and the rows share one segmentation.
        val captured = draft
        val capturedPins = pinsSnapshot()
        val usedPins = pins.current()
        if (store.planner(capturedPins).segments(captured).none { ShorthandLexicon.mealSlotOf(it.text) == null }) {
            error = "Add at least one food."
            return
        }
        // Nothing resolved: nothing was saved, so the Log stays open with the typed text and says what to fix.
        fun nothingSaved(saved: SavedMeal) = saved.entries.isEmpty() && saved.unresolvedSegments.isNotEmpty()
        session.write(
            change = { saved ->
                if (nothingSaved(saved)) null
                else DiaryChange.BatchLogged(LOGGED, saved.entries)
            },
            onSuccess = { saved ->
                // Only the pins this save used: one added while it planned off the main thread belongs to the new text.
                pins.forget(usedPins)
                val unmatched = saved.unresolvedSegments
                val planner = store.planner(pinsSnapshot())
                if (draft == captured) {
                    pins.edited(draft, unmatched.joinToString(", "))
                    draft = unmatched.joinToString(", ")
                }
                error = if (unmatched.isNotEmpty()) NEEDS_A_MATCH else null
                // Back during the write: what is left stays as text, and no queue runs behind a closed Log.
                if (!open) return@write
                if (unmatched.isEmpty()) close() else chosen = target
                // What no food matches stays on the Log, under its rows, for the same meal. A part with a local choice
                // or a unit its food lacks is not Open Food Facts' to answer: it stays a plain row.
                afterSave(unmatched.filter(planner::hasNoLocalMatch), target, this@LogComposer, captured)
            },
            onFailure = { error = it.message ?: "Could not save this meal." },
        ) { store.save(captured, target, selectedDay(), capturedPins) }
    }

    /**
     * A "Not in your foods" row: the part goes to the Library search, prefilled with its food text, on the meal the
     * Log writes to (Breakfast when none is known), and the Log closes until [resume]. A row drawn from an older draft
     * (a keystroke since) names a span that may no longer hold its text, and does nothing.
     */
    fun resolveNotInFoods(row: DraftRow.Unresolved) {
        if (!session.canWrite || !rowStillInDraft(draft, row)) return
        val part = draft.substring(row.start, row.end)
        val query = store.planner(pinsSnapshot()).libraryQueryFor(part)
        val request = LogResolveRequest(draft, row.start, row.end, part, preset, chosen, openMeal())
        open = false
        error = null
        resolvePart(request, targetMeal() ?: MealSlot.BREAKFAST, query)
    }

    /**
     * The Library is left for the Log ([request] ended): the Log opens where it was, with the draft as it was, or
     * with [pick] in place of the part, pinned so the part resolves to it.
     */
    fun resume(request: LogResolveRequest, pick: FoodRecord?) {
        if (pick == null) {
            applyDraft(request.draftText)
        } else {
            val text = store.planner(pinsSnapshot()).pickedText(request.part, pick)
            val next = replaceDraftPart(request.draftText, request.start, request.end, text)
            applyDraft(next)
            pins.add(next, text, pick, request.start)
        }
        // Back with its rows in place, as Add left them, when a queue is still waiting.
        committed = queuedParts().isNotEmpty()
        error = if (queuedParts().isNotEmpty()) error else null
        preset = request.preset
        chosen = request.chosen
        open = true
    }

    /** The draft as the resolve queue's source: what is left of its parts is written back here. */
    override val text: String get() = draft
    override fun putBack(left: String) { pins.edited(draft, left); draft = left }
    /** Every part Add left was matched in the resolver: "Some foods need a match." no longer applies. A skipped part keeps it. */
    override fun allMatched() { if (error == NEEDS_A_MATCH) error = null }
    override val showsItsParts: Boolean get() = true
    override fun removePart(raw: String) {
        val next = store.planner(pinsSnapshot()).withoutPart(draft, raw)
        pins.edited(draft, next)
        draft = next
    }
    // Whatever else is in the draft (a scanned part, a local choice) is still the Log's to add.
    override fun queueEnded() { if (draft.isBlank()) close() }
}

/** The Log's error while parts of the draft are not matched. */
private const val NEEDS_A_MATCH = "Some foods need a match."
