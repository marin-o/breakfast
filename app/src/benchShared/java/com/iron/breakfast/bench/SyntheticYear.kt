package com.iron.breakfast.bench

import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionCalculator
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.Preference
import com.iron.breakfast.domain.ServingConversion
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

/**
 * A year-sized diary for the benchmarks (MOB-028, MOB-H07), the same every run: the bundled catalogue plus
 * [customFoods] personal foods (about a third with Cyrillic names, some with aliases, some archived), [entries]
 * entries over 365 days ending on [lastDay] with one busiest day, and preferences for the favourite foods.
 * Test code only; it never touches a stored diary.
 */
class SyntheticYear(
    val customFoods: Int = 450,
    val entries: Int = 3000,
    val lastDay: LocalDate = LocalDate.of(2026, 10, 26),
    seed: Int = 20261026,
) {
    private val random = Random(seed)
    private val calculator = NutritionCalculator()

    val custom: List<FoodRecord> = buildCustomFoods()
    val foods: List<FoodRecord> = FoodCatalog.fixtures + custom
    val diary: List<MealEntry> = buildEntries()
    val preferences: List<Preference> = diary.groupBy { it.foodId }.entries
        .sortedByDescending { it.value.size }.take(80)
        .map { (foodId, logged) -> Preference(foodId, explicitCount = 1 + logged.size % 9, lastReinforcedEpochMs = logged.maxOf { it.savedAtEpochMs }) }

    /** The day with the most entries. */
    val busiestDay: String = diary.groupingBy { it.dayKey }.eachCount().maxBy { it.value }.key

    private fun buildCustomFoods(): List<FoodRecord> {
        val latin = LATIN_ADJECTIVES.flatMap { adjective -> LATIN_NOUNS.map { "$adjective $it" } } +
            LATIN_ADJECTIVES.flatMap { adjective -> LATIN_NOUNS.map { "$adjective $it bites" } }
        val cyrillic = CYRILLIC_ADJECTIVES.flatMap { adjective -> CYRILLIC_NOUNS.map { "$adjective $it" } } +
            CYRILLIC_ADJECTIVES.flatMap { adjective -> CYRILLIC_NOUNS.map { "$adjective $it плус" } }
        val cyrillicCount = customFoods / 3
        val names = latin.shuffled(random).take(customFoods - cyrillicCount) + cyrillic.shuffled(random).take(cyrillicCount)
        return names.mapIndexed { index, name ->
            val liquid = LIQUIDS.any { name.contains(it) }
            val unit = if (liquid) MeasureUnit.MILLILITER else MeasureUnit.GRAM
            val kcal = 20.0 + random.nextInt(580)
            val values = NutritionValues(
                kcal,
                if (random.nextInt(10) == 0) null else random.nextInt(300) / 10.0,
                if (random.nextInt(10) == 0) null else random.nextInt(700) / 10.0,
                if (random.nextInt(10) == 0) null else random.nextInt(400) / 10.0,
            )
            val conversions = buildList {
                add(ServingConversion(unit, 1.0, "custom basis"))
                if (random.nextInt(3) == 0) add(ServingConversion(MeasureUnit.PIECE, 20.0 + random.nextInt(120), "1 piece", label = "piece"))
                if (random.nextInt(4) == 0) add(ServingConversion(MeasureUnit.TABLESPOON, 15.0, "15 ml household tablespoon"))
            }
            val aliases = when (random.nextInt(4)) {
                0 -> listOf(name.substringAfter(' '))
                1 -> listOf(name.lowercase() + " " + (if (index % 2 == 0) "mix" else "домашно"))
                else -> emptyList()
            }
            FoodRecord(
                id = "custom-bench-$index",
                name = name,
                aliases = aliases,
                category = "Personal food",
                origin = FoodOrigin.CUSTOM,
                nutrition = NutritionBasis(100.0, unit, values),
                conversions = conversions,
                defaultAmount = 100.0,
                defaultUnit = unit,
                brand = if (random.nextInt(3) == 0) "Brand ${random.nextInt(40)}" else null,
                barcode = if (random.nextInt(4) == 0) "38" + (1_000_000_000L + index).toString() + "0" else null,
                archived = index % 20 == 19,
                personalOverride = true,
            )
        }
    }

    private fun buildEntries(): List<MealEntry> {
        // Most logs use a few favourites, as a real diary does: a third of the foods take nearly every entry.
        val live = foods.filterNot { it.archived }
        val favourites = live.shuffled(random).take(live.size / 3)
        val busiest = 200
        val spread = entries - BUSIEST_EXTRA
        return (0 until entries).map { index ->
            val dayIndex = if (index < spread) (index.toLong() * 365 / spread).toInt() else busiest
            val day = lastDay.minusDays((364 - dayIndex).toLong())
            val food = if (random.nextInt(10) < 8) favourites[random.nextInt(favourites.size)] else live[random.nextInt(live.size)]
            val unit = food.nutrition.unit
            val amount = 20.0 + random.nextInt(280)
            val calculation = calculator.calculate(food, amount, unit)
            val slot = MealSlot.entries[random.nextInt(MealSlot.entries.size)]
            val savedAt = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + (7 + slot.ordinal * 4) * 3_600_000L + index
            MealEntry(
                id = "bench-entry-$index",
                foodId = food.id,
                foodName = food.name,
                enteredAmount = amount,
                enteredUnit = unit,
                resolvedBaseAmount = calculation.resolvedBaseAmount,
                nutritionSnapshot = calculation.nutrition,
                conversionProvenance = calculation.provenance,
                approximateConversion = calculation.approximate,
                savedAtEpochMs = savedAt,
                sourcePhrase = "${amount.toInt()} ${unit.label} ${food.name}",
                mealSlot = slot,
                dayKey = day.toString(),
                resolvedBaseUnit = unit,
            )
        }
    }

    companion object {
        private const val BUSIEST_EXTRA = 30
        private val LATIN_ADJECTIVES = listOf("Greek", "Smoked", "Roasted", "Whole", "Light", "Spicy", "Sweet", "Crispy", "Fresh", "Organic", "Baked", "Homemade")
        private val LATIN_NOUNS = listOf("yogurt", "chicken", "bread", "cheese", "salad", "soup", "bar", "cookie", "juice", "pasta", "rice", "beans", "sausage", "muesli", "granola", "kefir", "smoothie", "wrap")
        private val CYRILLIC_ADJECTIVES = listOf("Домашен", "Свеж", "Печен", "Посен", "Сладок", "Лут", "Димен", "Цел")
        private val CYRILLIC_NOUNS = listOf("јогурт", "леб", "сирење", "кифла", "супа", "пита", "ајвар", "кашкавал", "мусака", "пастрмајлија", "сок", "гравче", "бурек", "кефир")
        private val LIQUIDS = listOf("juice", "kefir", "smoothie", "soup", "сок", "кефир", "супа", "јогурт")

        /** Realistic Log drafts: a common English breakfast, a Cyrillic one, and a ten-part day. */
        val DRAFT_INPUTS = listOf(
            "2 eggs, 100 g rolled oats, 1 tbsp pb, 1 banana",
            "200 г домашен јогурт, 1 кифла, 50 г кашкавал, 1 јаболко",
            "2 eggs, 1 banana, 100 g rolled oats, 200 ml milk, 1 tbsp pb, 30 g cheese, 1 apple, 150 g greek yogurt, 2 slices bread, 1 tsp honey",
        )

        /** The four-part input the save-path benchmark plans. */
        const val SAVE_INPUT = "2 eggs, 100 g rolled oats, 1 tbsp pb, 1 banana"
    }
}

/** Every prefix of [text], one per keystroke, as the Log page sees them while it is typed. */
fun keystrokes(text: String): List<String> = (1..text.length).map { text.substring(0, it) }
