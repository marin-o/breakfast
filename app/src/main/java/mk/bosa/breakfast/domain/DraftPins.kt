package mk.bosa.breakfast.domain

import androidx.compose.runtime.mutableStateListOf

/** Between a part's text and which of the parts with that text it is, in a pin's key: never typed. */
internal const val PIN_OCCURRENCE = '\u0001'

/**
 * The foods picked for the parts of a draft. Each pin belongs to its own part, by where the food's name sits in the
 * draft: [edited] moves it with the text, and retyping the name drops it, while editing the amount around it keeps it;
 * renaming the food changes the text with it ([refresh]). So two parts with one name keep their own foods, and a part
 * typed later is never taken by an earlier pin.
 *
 * The planner reads them as [keys]: a part's text to its food, the plain text unless parts with that text differ.
 */
internal class DraftPins {
    class Pin(val nameStart: Int, val food: FoodRecord, val madeFor: String) {
        /** What makes it this pin, whatever instance holds it and however its food was refreshed. */
        val key: Triple<Int, String, String> get() = Triple(nameStart, food.id, madeFor)
    }

    private val pins = mutableStateListOf<Pin>()

    /** Where a part begins and ends is the parser's say ([ShorthandParser.breakIndexes]): quoted names and decimal commas included. */
    private val parser = ShorthandParser()

    fun clear() = pins.clear()
    fun current(): List<Pin> = pins.toList()
    fun forget(used: List<Pin>) {
        val keys = used.mapTo(HashSet()) { it.key }
        pins.removeAll { it.key in keys }
    }
    fun dropFood(foodId: String) { pins.removeAll { it.food.id == foodId } }
    fun dropWhere(gone: (FoodRecord) -> Boolean) { pins.removeAll { gone(it.food) } }

    /**
     * Each pin holds its food as it is in [current]. A renamed food's new name takes the old one's place in [draft], for
     * every part pinned to it, and the other pins move with the text. Returns the draft as it now reads, with [shift]
     * given how a position in the old text moved. A pin whose name is no longer at its place goes.
     */
    fun refresh(draft: String, current: List<FoodRecord>, shift: ((Int) -> Int) -> Unit = {}): String {
        val byId = current.associateBy { it.id }
        val renames = pins.mapNotNull { pin ->
            val food = byId[pin.food.id]?.takeIf { it.name != pin.food.name && draft.startsWith(pin.food.name, pin.nameStart) }
            food?.let { Rename(pin.nameStart, pin.food.name.length, it.name) }
        }.distinctBy { it.start }.sortedBy { it.start }.fold(emptyList<Rename>()) { kept, rename ->
            if (kept.lastOrNull()?.let { it.start + it.oldLength > rename.start } == true) kept else kept + rename
        }
        val moved = { at: Int -> at + renames.filter { it.start + it.oldLength <= at }.sumOf { it.name.length - it.oldLength } }
        val text = buildString {
            var copied = 0
            renames.forEach { rename ->
                append(draft, copied, rename.start).append(rename.name)
                copied = rename.start + rename.oldLength
            }
            append(draft, copied, draft.length)
        }
        val next = pins.mapNotNull { pin ->
            val food = byId[pin.food.id] ?: pin.food
            val made = if (food.name == pin.food.name) pin.madeFor else pin.madeFor.replaceFirst(pin.food.name, food.name)
            Pin(moved(pin.nameStart), food, made).takeIf { text.startsWith(food.name, it.nameStart) }
        }
        pins.clear()
        pins.addAll(next)
        if (renames.isNotEmpty()) shift(moved)
        return text
    }

    /** A renamed food's name at [start] of the draft: [oldLength] long, now [name]. */
    private class Rename(val start: Int, val oldLength: Int, val name: String)

    /** Back from [keys] as they were saved, or as an edit types its parts back: each part with that text in [draft]. */
    fun restore(draft: String, keys: Map<String, FoodRecord>) {
        pins.clear()
        val breaks = parser.breakIndexes(draft)
        keys.forEach { (key, food) ->
            val text = key.substringBefore(PIN_OCCURRENCE)
            val only = key.substringAfter(PIN_OCCURRENCE, "").toIntOrNull()
            var from = draft.indexOf(text)
            var seen = 0
            while (from >= 0) {
                val end = from + text.length
                val starts = endsPart(draft, from, breaks)
                val ends = startsPart(draft, end, breaks)
                if (starts && ends) {
                    if (only == null || only == seen) add(draft, text, food, from)
                    seen++
                }
                from = draft.indexOf(text, from + 1)
            }
        }
    }

    /** Pins [food] to the part [text] of [draft], the one at or after [from]. */
    fun add(draft: String, text: String, food: FoodRecord, from: Int = 0) {
        val partAt = draft.indexOf(text, from).takeIf { it >= 0 } ?: return
        val nameAt = draft.indexOf(food.name, partAt).takeIf { it >= 0 && it < partAt + text.length } ?: return
        pins += Pin(nameAt, food, text)
    }

    /** The draft went from [old] to [new]: a pin moves with its text, and goes when its name was touched. */
    fun edited(old: String, new: String) {
        if (pins.isEmpty() || old == new) return
        val prefix = old.commonPrefixWith(new).length
        val suffix = old.substring(prefix).commonSuffixWith(new.substring(prefix)).length
        val delta = new.length - old.length
        val moved = pins.mapNotNull { pin ->
            val end = pin.nameStart + pin.food.name.length
            val start = when {
                end <= prefix -> pin.nameStart
                pin.nameStart >= old.length - suffix -> pin.nameStart + delta
                else -> return@mapNotNull null
            }
            Pin(start, pin.food, pin.madeFor).takeIf { new.startsWith(pin.food.name, start) }
        }
        pins.clear()
        pins.addAll(moved)
    }

    /**
     * The pins as the planner takes them, for [draft] now. A pin stands while its part is the name with an amount (or as
     * it was made); a part that has become something else ("Milk 3.2", another food's name) reads as typed.
     */
    fun keys(draft: String, planner: (Map<String, FoodRecord>) -> LogPlanner): Map<String, FoodRecord> {
        if (pins.isEmpty()) return emptyMap()
        val checking = planner(emptyMap())
        val breaks = parser.breakIndexes(draft)
        val standing = pins.mapNotNull { pin ->
            val part = partAround(draft, pin.nameStart, pin.food, breaks)
            (part to pin).takeIf { part.text == pin.madeFor || checking.pinFits(part.text, part.left, part.right, pin.food) }
        }
        val plain = standing.associate { (part, pin) -> part.text to pin.food }
        // Parts with one text: only then is it worth reading the parts, to say which of them each pin is.
        val repeated = standing.filter { (part, _) -> draft.indexOf(part.text) != draft.lastIndexOf(part.text) }
        if (repeated.isEmpty()) return plain
        val spans = planner(plain).segments(draft)
        return standing.associate { (part, pin) ->
            val same = spans.filter { it.text == part.text }
            val which = same.indexOfFirst { it.start <= pin.nameStart && pin.nameStart < it.end }
            (if (same.size > 1 && which >= 0) "${part.text}$PIN_OCCURRENCE$which" else part.text) to pin.food
        }
    }

    /** Whether the text of [draft] ahead of [from] ends at a break of [breaks]: only blanks sit between the two. */
    private fun endsPart(draft: String, from: Int, breaks: List<Int>): Boolean {
        val head = draft.substring(0, from).trimEnd().length
        return head == 0 || breaks.any { it in head - 1 until from }
    }

    /** Whether the text of [draft] from [end] on begins at a break of [breaks], as [endsPart]. */
    private fun startsPart(draft: String, end: Int, breaks: List<Int>): Boolean {
        val tail = end + (draft.length - end - draft.substring(end).trimStart().length)
        return tail == draft.length || breaks.any { it in end..tail }
    }

    private class Part(val text: String, val left: String, val right: String)

    /**
     * The part around a food's name: back to the separator before it, or past the commas of a serving of that food
     * written before it ("2 cups, cooked Rice"), on to the separator after it.
     */
    private fun partAround(draft: String, nameStart: Int, food: FoodRecord, breaks: List<Int>): Part {
        val nameEnd = nameStart + food.name.length
        val before = draft.substring(0, nameStart)
        val left = servingBefore(before, food, breaks) ?: before.substring((breaks.lastOrNull { it < nameStart } ?: -1) + 1)
        val right = draft.substring(nameEnd, parser.partEndFrom(draft, nameEnd))
        val text = (left + draft.substring(nameStart, nameEnd) + right).trim()
        return Part(text, left.trim(), right.trim())
    }

    /** The amount and comma-holding serving of [food] that [before] ends in, or null. A cut at each comma, nearest last. */
    private fun servingBefore(before: String, food: FoodRecord, breaks: List<Int>): String? {
        if (food.conversions.none { it.name?.contains(',') == true }) return null
        val starts = breaks.filter { it < before.length }.map { it + 1 }
        return (listOf(0) + starts).map(before::substring).firstOrNull { food.writtenServing(it) != null }
    }
}
