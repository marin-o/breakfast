package com.bosa.breakfast.data

import com.bosa.breakfast.domain.FoodRecord
import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.domain.NutritionCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** An Open Food Facts product's printed serving and package become named servings, when they read clearly. */
class OffServingsTest {
    private fun product(fields: String): FoodRecord =
        OpenFoodFactsClient().parseProductForTesting("""{"code":"5310382009733","product_name":"Product",$fields,"nutriments":{"energy-kcal_100g":42}}""")!!

    private fun servings(food: FoodRecord) = food.conversions.mapNotNull { serving -> serving.name?.let { it to serving.baseUnitsPerServing } }

    @Test fun aCanAndItsPackage() {
        val coke = product(""""quantity":"330 ml","product_quantity":"330","product_quantity_unit":"ml","serving_size":"1 can (330 ml)"""")
        assertEquals(MeasureUnit.MILLILITER, coke.nutrition.unit)
        assertEquals(listOf("can" to 330.0, "package" to 330.0), servings(coke))
        assertEquals(138.6, NutritionCalculator().calculate(coke, 1.0, MeasureUnit.SERVING, "can").nutrition.calories, 1e-9)
    }

    @Test fun aCountedServingIsOneOfItsCount() {
        val biscuits = product(""""quantity":"200 g","serving_size":"2 biscuits (25 g)"""")
        assertEquals(listOf("biscuit" to 12.5, "package" to 200.0), servings(biscuits))
    }

    @Test fun aBareSizeIsAServing() {
        assertEquals(listOf("serving" to 30.0), servings(product(""""serving_size":"30 g"""")))
    }

    @Test fun anUnclearSizeIsSkippedNeverGuessed() {
        assertTrue(servings(product(""""serving_size":"1 portion (100 g) or 2 cups"""")).isEmpty())
        assertTrue(servings(product(""""quantity":"6 x 330 ml","serving_size":"one can"""")).isEmpty())
    }
}
