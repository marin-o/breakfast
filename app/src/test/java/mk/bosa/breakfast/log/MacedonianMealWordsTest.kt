package mk.bosa.breakfast.log

import mk.bosa.breakfast.domain.TestFoods
import mk.bosa.breakfast.domain.FoodMatcher
import mk.bosa.breakfast.domain.LogPlanner
import mk.bosa.breakfast.domain.MealSlot
import mk.bosa.breakfast.domain.ShorthandLexicon
import mk.bosa.breakfast.domain.ShorthandParser
import org.junit.Assert.assertEquals
import org.junit.Test
import mk.bosa.breakfast.domain.mealMarkerSlots

/** Meal words in Macedonian, Cyrillic and Latin, select the meal the way the English ones do. */
class MacedonianMealWordsTest {
    private val planner = LogPlanner(FoodMatcher(TestFoods.fixtures))

    @Test fun everyMacedonianMealWordNamesItsSlot() {
        mapOf(
            "појадок" to MealSlot.BREAKFAST, "pojadok" to MealSlot.BREAKFAST,
            "доручек" to MealSlot.BREAKFAST, "dorucek" to MealSlot.BREAKFAST, "doruček" to MealSlot.BREAKFAST,
            "ручек" to MealSlot.LUNCH, "rucek" to MealSlot.LUNCH, "ruček" to MealSlot.LUNCH,
            "вечера" to MealSlot.DINNER, "vecera" to MealSlot.DINNER, "Večera" to MealSlot.DINNER,
            "ужина" to MealSlot.SNACKS, "uzina" to MealSlot.SNACKS, "užina" to MealSlot.SNACKS,
            "закуска" to MealSlot.SNACKS, "zakuska" to MealSlot.SNACKS,
        ).forEach { (word, slot) -> assertEquals(word, slot, ShorthandLexicon.mealSlotOf(word)) }
    }

    @Test fun aLeadingMacedonianMealWordTargetsThatMeal() {
        assertEquals(listOf("доручек", "1 банана"), ShorthandParser().split("доручек 1 банана"))
        assertEquals(listOf(MealSlot.BREAKFAST), mealMarkerSlots("доручек 1 банана"))
        assertEquals(MealSlot.BREAKFAST, logTargetMealOf(null, mealMarkerSlots("доручек 1 банана"), null))
        assertEquals(listOf("banana"), planner.plan("доручек 1 банана").entries.map { it.food.id })
    }

    @Test fun aCapitalisedMealWordAsAKeyboardTypesIt() {
        assertEquals(listOf(MealSlot.DINNER), mealMarkerSlots("Вечера, 2 eggs"))
    }
}
