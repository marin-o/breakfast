package com.iron.breakfast.data

import com.iron.breakfast.domain.MeasureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsClientTest {
    @Test fun searchParsingKeepsSuccessfulEmptyDistinctFromMalformedResponse() {
        val client = OpenFoodFactsClient()
        assertTrue(client.parseSearchResponseForTesting("{\"products\":[]}") is OffSearchResult.Success)
        assertEquals(0, (client.parseSearchResponseForTesting("{\"products\":[]}") as OffSearchResult.Success).foods.size)
        assertTrue(client.parseSearchResponseForTesting("{\"error\":\"backend unavailable\"}") is OffSearchResult.Error)
        assertTrue(client.parseSearchResponseForTesting("not-json") is OffSearchResult.Error)
    }

    @Test fun searchParsingRetainsOnlyEligibleNutritionRecords() {
        val raw = """
            {"products":[
              {"code":"1","product_name":"Pesto","product_quantity_unit":"g","nutriments":{"energy-kcal_100g":400}},
              {"code":"2","product_name":"Unknown","product_quantity_unit":"g","nutriments":{"energy-kcal":42}}
            ]}
        """.trimIndent()
        val result = OpenFoodFactsClient().parseSearchResponseForTesting(raw) as OffSearchResult.Success
        assertEquals(listOf("Pesto"), result.foods.map { it.name })
    }

    @Test fun usesLiquidQuantityUnitForNormalizedPer100gValuesAndKeepsMissingMacrosUnknown() {
        val food = OpenFoodFactsClient().parseProductForTesting("""{"product_name":"Drink","product_quantity":"1000","product_quantity_unit":"ml","nutrition_data_per":"100g","nutriments":{"energy-kcal_100g":42,"fat_100g":1.5}}""", "123")
        requireNotNull(food)
        assertEquals(MeasureUnit.MILLILITER, food.nutrition.unit)
        assertEquals(42.0, food.nutrition.values.calories, 0.001)
        assertNull(food.nutrition.values.protein)
        assertEquals("123", food.barcode)
    }

    @Test fun rejectsUnsuffixedEnergyAndImpossibleCalories() {
        assertNull(OpenFoodFactsClient().parseProductForTesting("""{"product_name":"Ambiguous","nutriments":{"energy-kcal":42}}"""))
        assertNull(OpenFoodFactsClient().parseProductForTesting("""{"product_name":"Bad","product_quantity_unit":"g","nutriments":{"energy-kcal_100g":2000}}"""))
        assertNull(OpenFoodFactsClient().parseProductForTesting("""{"product_name":"Bad macro","product_quantity_unit":"g","nutriments":{"energy-kcal_100g":42,"fat_100g":-1}}"""))
    }

    @Test fun rejectsAmbiguousBasisAndPresentInvalidNutrients() {
        val client = OpenFoodFactsClient()
        assertNull(client.parseProductForTesting("""{"product_name":"Unknown package","nutrition_data_per":"100g","nutriments":{"energy-kcal_100g":42}}"""))
        assertNull(client.parseProductForTesting("""{"product_name":"Invalid protein","product_quantity_unit":"ml","nutriments":{"energy-kcal_100g":42,"proteins_100g":"oops"}}"""))
        assertNull(client.parseProductForTesting("""{"product_name":"Invalid energy","product_quantity_unit":"g","nutriments":{"energy-kcal_100g":"oops","energy-kj_100g":175}}"""))
    }
}
