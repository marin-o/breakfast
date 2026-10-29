package com.iron.breakfast.log

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iron.breakfast.app.DiaryChange
import com.iron.breakfast.app.LOGGED
import com.iron.breakfast.app.DiarySession
import com.iron.breakfast.app.SavedLogDraft
import com.iron.breakfast.domain.DraftRow
import com.iron.breakfast.domain.DraftView
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
    private val pins = mutableStateMapOf<String, FoodRecord>()

    /** The draft for `SavedStateHandle`. */
    fun savedDraft(): SavedLogDraft = SavedLogDraft(draft, committed)

    /** The pins as they are now, for a draft computed off the main thread. */
    fun pinsSnapshot(): Map<String, FoodRecord> = pins.toMap()

    /** The foods picked for the old diary are dropped with it (see `SessionReset`). */
    fun clearPins() { pins.clear() }

    /** A food was deleted: a part pinned to it is matched again, so Add cannot log (or bring back) that food. */
    fun dropPinsOf(foodId: String) { pins.entries.removeAll { it.value.id == foodId } }

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
        open = false
        mealMenuOpen = false
        submitPending = false
    }

    fun openMealMenu() { mealMenuOpen = true }
    fun dismissMealMenu() { mealMenuOpen = false; submitPending = false }

    /** Choosing a meal from the menu wins over everything else; it logs at once only if Add asked for it. */
    fun chooseMeal(target: MealSlot) {
        chosen = target
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
        draft = value
        committed = false
        if (value.isBlank()) pins.clear()
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

    fun draftView(text: String = draft, committed: Boolean = this.committed, pins: Map<String, FoodRecord> = pinsSnapshot()): LogDraftView {
        val planner = store.planner(pins)
        // One planner draft: rows, the choice group, suggestions and the add-bar sum all come from the same resolution.
        val view = if (session.ready) planner.draft(text, typing = !committed) else DraftView.EMPTY
        return LogDraftView(
            rows = view.rows,
            choices = view.choices,
            choiceHint = view.choiceHint,
            preselected = view.preselected,
            activePhrase = view.activePhrase,
            // The sum is the draft's own count: what Add would save.
            logged = view.logged,
            // Each choice's kcal from the same planner and text as the sum, so the two never disagree.
            choiceCalculations = view.choices.map { planner.calculationFor(view.activeText, it.food) },
        )
    }

    /**
     * Tapping a choice or a suggestion: the typed part becomes the amount, unit and name it would log, pinned to the
     * tapped food so it stays that food. Pins live as long as the draft (cleared on Add or an empty draft).
     */
    fun chooseSuggestion(food: FoodRecord) {
        val picked = store.planner(pins.toMap()).choose(draft, food, typing = !committed)
        picked.part?.let { pins[it] = food }
        onDraft(picked.input)
    }

    /** Add on the Log page. */
    fun submit() {
        // Add finishes the draft in both "Count what I'm typing" modes: it saves what the finished draft counts.
        committed = true
        if (!session.ready || session.busy) return
        when (val plan = logSubmitPlan(session.ready, session.busy, draft, mealMarkerSlots(draft), chosen, preset, automaticMeal())) {
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
        if (!session.ready || session.busy) return
        // Save takes the draft as typed: the planner skips meal words by span, so save and the rows share one segmentation.
        val captured = draft
        val capturedPins = pins.toMap()
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
                if (nothingSaved(saved)) {
                    error = NEEDS_A_MATCH
                    return@write
                }
                // Only the pins this save used: one added while it planned off the main thread belongs to the new text.
                capturedPins.forEach { (part, food) -> if (pins[part] == food) pins.remove(part) }
                close()
                afterSave(saved.unresolvedSegments, target, this@LogComposer, captured)
                error = if (saved.unresolvedSegments.isNotEmpty()) NEEDS_A_MATCH else null
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
        if (!session.ready || session.busy || !rowStillInDraft(draft, row)) return
        val part = draft.substring(row.start, row.end)
        val query = store.planner(pins.toMap()).libraryQueryFor(part)
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
        draft = if (pick == null) request.draftText else {
            val text = store.planner(pins.toMap()).pickedText(request.part, pick)
            pins[text] = pick
            replaceDraftPart(request.draftText, request.start, request.end, text)
        }
        committed = false
        error = null
        preset = request.preset
        chosen = request.chosen
        open = true
    }

    /** The draft as the resolve queue's source: what is left of its parts is written back here. */
    override val text: String get() = draft
    override fun putBack(left: String) { draft = left }
    /** Every part Add left was matched in the resolver: "Some foods need a match." no longer applies. A skipped part keeps it. */
    override fun allMatched() { if (error == NEEDS_A_MATCH) error = null }
}

/** The Log's error while parts of the draft are not matched. */
private const val NEEDS_A_MATCH = "Some foods need a match."
