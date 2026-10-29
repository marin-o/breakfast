package com.iron.breakfast.domain

/**
 * Brings each stored copy of a built-in catalogue food up to the current catalogue, matched by id. Catalogue edits
 * reach an existing install this way; a fresh install already has them.
 *
 * Kept as the user has it: a food the user edited (`personalOverride`), its source identity and
 * barcode, custom foods (not in the catalogue), and logged entries (which keep their own nutrition snapshot). Running
 * it again changes nothing once the rows match, so it runs at every start.
 */
object BuiltInCatalogSync {
    /** The stored foods whose catalogue content differs from [catalogue]: the rows to rewrite. */
    fun changedRows(stored: List<FoodRecord>, catalogue: List<FoodRecord>): List<FoodRecord> {
        val byId = catalogue.associateBy { it.id }
        return stored.mapNotNull { row ->
            val built = byId[row.id] ?: return@mapNotNull null
            if (row.personalOverride) return@mapNotNull null
            val synced = row.copy(
                name = built.name,
                category = built.category,
                aliases = built.aliases,
                nutrition = built.nutrition,
                defaultAmount = built.defaultAmount,
                defaultUnit = built.defaultUnit,
                conversions = built.conversions,
            )
            synced.takeIf { storedForm(it) != storedForm(row) }
        }
    }

    /**
     * A food as storage keeps it: aliases are a set (read back in table order, not typed order) and a serving is kept
     * by its name ("piece" for a piece row with no label, as storage writes it). Comparing in this form keeps a row that only came back from the database differently
     * ordered from counting as changed, which rewrote (and moved to the end of the table) catalogue rows at every start.
     */
    private fun storedForm(food: FoodRecord): FoodRecord = food.copy(
        aliases = food.aliases.distinct().sorted(),
        conversions = food.conversions.map { it.copy(label = it.name) }
            .sortedWith(compareBy({ it.unit }, { it.label.orEmpty() })),
    )
}
