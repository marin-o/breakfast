package mk.bosa.breakfast.domain


/** What the Log page shows for one draft, computed in one planner pass by the planner. */
internal data class LogDraftView(
    /** The draft's rows in typed order: entries, and parts shown as "Not in your foods". */
    val rows: List<DraftRow>,
    /** The group under the rows: the word being typed's suggestions, or the part's choices. */
    val choices: List<RankedFood>,
    /** The "which one?" hint, shown for real choices only. */
    val choiceHint: Boolean,
    /** True exactly when Add saves the part being typed; the first choice or suggestion is then the food it saves. */
    val preselected: Boolean,
    val activePhrase: String,
    /** What Add will save; the add bar's sum and count come from this. */
    val logged: List<SegmentResolution>,
    /** The kcal each of [choices] would log, in the same order. */
    val choiceCalculations: List<Calculation> = emptyList(),
    /** Add would log something. */
    val addLogs: Boolean = false,
    /** A part has no food, or local matches to choose from. */
    val needsMatch: Boolean = false,
    /** A part names its food in a unit that food has no value for. */
    val hasNoValue: Boolean = false,
    /** The first of [choices] is what the finished draft logs for the part being typed: it goes green. */
    val firstIsAddPick: Boolean = false,
    /** The parts that need a match, except one being typed that still has something to show. */
    val needsMatchParts: List<String> = emptyList(),
    /** What the USDA catalogue is asked for the part being typed; blank for nothing. */
    val catalogueQuery: String = "",
    /** The names the part being typed may be the rest of, for the catalogue to ask in turn ([DraftView.catalogueJoins]). */
    val catalogueJoins: List<CatalogueJoin> = emptyList(),
)

/**
 * What [planner] shows for [text] in one pass (nothing until storage is [ready]): rows, the choice group, suggestions
 * and the sum all come from the same resolution, and each choice's kcal from the same planner and text as the sum, so
 * the two never disagree. The Log page and a recipe's ingredients both read it.
 */
internal fun logDraftViewOf(planner: LogPlanner, text: String, committed: Boolean, ready: Boolean): LogDraftView {
    val view = if (ready) planner.draft(text, typing = !committed) else DraftView.EMPTY
    return LogDraftView(
        rows = view.rows,
        choices = view.choices,
        choiceHint = view.choiceHint,
        preselected = view.preselected,
        activePhrase = view.activePhrase,
        // The sum is the draft's own count: what Add would save.
        logged = view.logged,
        addLogs = view.addLogs,
        needsMatch = view.needsMatch,
        hasNoValue = view.hasNoValue,
        firstIsAddPick = view.addPick != null && view.choices.firstOrNull()?.food?.id == view.addPick.food.id,
        needsMatchParts = view.needsMatchParts,
        catalogueQuery = view.catalogueQuery,
        catalogueJoins = view.catalogueJoins,
        choiceCalculations = view.choices.map { planner.calculationFor(view.activeText, it.food) },
    )
}

/**
 * A "Not in your foods" row still names its text in [draft] (MOB-025): a draft's rows are computed off the main thread,
 * so after a keystroke a tapped row's span may hold other text, and that tap must do nothing.
 */
internal fun rowStillInDraft(draft: String, row: DraftRow.Unresolved): Boolean =
    row.start >= 0 && row.start < row.end && row.end <= draft.length && draft.substring(row.start, row.end) == row.text
