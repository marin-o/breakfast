package mk.bosa.breakfast.food

import mk.bosa.breakfast.domain.NutritionInput
import mk.bosa.breakfast.domain.resolveNutrition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoodSaveRefusalTest {
    private fun refusal(name: String = "Ajvar", input: NutritionInput, barcodeError: String? = null) =
        foodSaveRefusal(name, input, resolveNutrition(input), barcodeError)

    @Test fun aValidFoodMaySave() {
        assertNull(refusal(input = NutritionInput(calories = "120", protein = "2", fat = "8")))
    }

    @Test fun eachRefusalPointsAtTheFieldToFix() {
        assertEquals(FoodFormField.PROTEIN, refusal(input = NutritionInput(calories = "120", protein = "."))?.field)
        assertEquals(FoodFormField.BASIS, refusal(input = NutritionInput(calories = "120", basisAmount = "."))?.field)
        assertEquals(FoodFormField.NAME, refusal(name = " ", input = NutritionInput(calories = "120"))?.field)
        assertEquals(FoodFormField.KCAL, refusal(input = NutritionInput())?.field)
        assertEquals(FoodFormField.BARCODE, refusal(input = NutritionInput(calories = "120"), barcodeError = "That barcode already belongs to Milk.")?.field)
        assertEquals(FoodFormField.KCAL, refusal(input = NutritionInput(calories = "10001"))?.field)
        assertEquals(FoodFormField.FAT, refusal(input = NutritionInput(calories = "120", fat = "1001"))?.field)
        assertEquals(FoodFormField.BASIS, refusal(input = NutritionInput(calories = "120", basisAmount = "0"))?.field)
    }

    @Test fun theNoticesAreTheExistingOnes() {
        assertEquals("Nutrition values must be finite and nonnegative.", refusal(input = NutritionInput(calories = "120", carbs = "2000"))?.message)
        assertEquals("Name and calories are required.", refusal(name = "", input = NutritionInput(calories = "1"))?.message)
        assertEquals("Enter a positive nutrition basis.", refusal(input = NutritionInput(calories = "1", basisAmount = "0"))?.message)
        assertEquals("That barcode already belongs to Milk.", refusal(input = NutritionInput(calories = "1"), barcodeError = "That barcode already belongs to Milk.")?.message)
    }

    @Test fun anInvalidNumberIsReportedBeforeAMissingName() {
        assertEquals(FoodFormField.KCAL, refusal(name = "", input = NutritionInput(calories = "."))?.field)
    }
}
