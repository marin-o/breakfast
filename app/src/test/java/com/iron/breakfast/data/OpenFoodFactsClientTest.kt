package com.iron.breakfast.data

import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class OpenFoodFactsClientTest {
    @Test fun contributionFormNormalizesBasisAndExcludesMealServingDetails() {
        val food = FoodRecord(
            id = "off-1", name = "Corrected drink", aliases = listOf("private alias"), category = "Open Food Facts",
            origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(250.0, MeasureUnit.MILLILITER, NutritionValues(50.0, 5.0, null, 1.25)),
            defaultAmount = 1.0, defaultUnit = MeasureUnit.TABLESPOON,
            sourceName = "Open Food Facts", barcode = "123", brand = "Brand"
        )
        val fields = requireNotNull(OffContributionDraft.fromFood(food)).asFormFields()
        val publicFields = requireNotNull(OffContributionDraft.fromFood(food)).publicFields()
        assertEquals("123", fields["code"])
        assertNull(fields["product_name"])
        assertNull(fields["brands"])
        assertEquals("100g", fields["nutrition_data_per"])
        assertTrue(publicFields.contains("Nutrition basis: per 100 ml"))
        assertTrue(publicFields.none { it.startsWith("Name:") || it.startsWith("Brand:") })
        assertEquals("20", fields["nutriment_energy-kcal"])
        assertEquals("2", fields["nutriment_proteins"])
        assertEquals("0.5", fields["nutriment_fat"])
        assertNull(fields["nutriment_carbohydrates"])
        assertNull(fields["entered_amount"])
        assertNull(fields["entered_unit"])
        assertNull(fields["private_alias"])
        assertTrue(fields.keys.none { it.contains("serving", ignoreCase = true) && it != "nutrition_data_per" })
    }

    @Test fun contributionResponseClassifiesAuthRejectionAndSuccess() {
        val client = OpenFoodFactsClient()
        assertTrue(client.classifyContributionResponseForTesting(401, "") is OffContributionResult.AuthFailed)
        assertTrue(client.classifyContributionResponseForTesting(422, "{\"status\":0,\"status_verbose\":\"invalid product\"}") is OffContributionResult.Rejected)
        assertTrue(client.classifyContributionResponseForTesting(200, "{\"status\":1,\"status_verbose\":\"fields saved\"}") is OffContributionResult.Success)
        assertTrue(client.classifyContributionResponseForTesting(200, "not-json") is OffContributionResult.Error)
    }

    @Test fun contributionUsesSessionCookieAndSendsOnlyPublicProductFields() {
        val connections = mutableListOf<FakeConnection>()
        val client = OpenFoodFactsClient { url ->
            FakeConnection(
                url = url,
                status = 200,
                body = when {
                    url.path.endsWith("session.pl") -> "logged in"
                    url.path.contains("/api/v3/product/") -> "{\"product\":{\"nutrition_data_per\":\"100g\",\"product_quantity_unit\":\"g\",\"nutriments\":{\"energy-kcal_100g\":100}}}"
                    else -> "{\"status\":1,\"status_verbose\":\"fields saved\"}"
                },
                setCookie = if (url.path.endsWith("session.pl")) "session=user&token=abc" else null
            ).also { connections += it }
        }
        val draft = OffContributionDraft("123", "Drink", "Brand", 50.0, MeasureUnit.GRAM, NutritionValues(100.0, 5.0, null, 2.0))

        assertTrue(client.contribute(draft, "user", "password") is OffContributionResult.Success)
        assertEquals(3, connections.size)
        val loginBody = connections[1].written.toString(Charsets.UTF_8)
        val productBody = connections[2].written.toString(Charsets.UTF_8)
        assertTrue(loginBody.contains("user_id=user"))
        assertTrue(loginBody.contains("password=password"))
        assertEquals("session=user&token=abc", connections[2].headersSeen["Cookie"])
        assertTrue(productBody.contains("code=123"))
        assertTrue(productBody.contains("nutriment_energy-kcal=200"))
        assertTrue(productBody.contains("nutrition_data_per=100g"))
        assertTrue(!productBody.contains("entered_amount"))
        assertTrue(!productBody.contains("entered_unit"))
        assertTrue(!productBody.contains("alias"))
    }

    @Test fun contributionRejectsServingBasisAndMissingProductsBeforeLogin() {
        val connections = mutableListOf<FakeConnection>()
        val client = OpenFoodFactsClient { url ->
            FakeConnection(
                url = url,
                status = if (url.path.contains("/api/v3/product/")) 200 else 200,
                body = if (url.path.contains("/api/v3/product/")) "{\"product\":{\"nutrition_data_per\":\"serving\",\"product_quantity_unit\":\"g\"}}" else "{\"status\":1}",
                setCookie = null
            ).also { connections += it }
        }
        val draft = OffContributionDraft("123", "Drink", null, 100.0, MeasureUnit.GRAM, NutritionValues(100.0, null, null, null))
        val result = client.contribute(draft, "user", "password")
        assertTrue(result is OffContributionResult.Ineligible)
        assertEquals(1, connections.size)
        assertTrue(connections.single().written.size() == 0)
    }

    @Test fun contributionLoginIoIsUnavailableRatherThanAuthFailure() {
        val client = OpenFoodFactsClient { url ->
            if (url.path.contains("/api/v3/product/")) {
                FakeConnection(url, 200, "{\"product\":{\"nutrition_data_per\":\"100g\",\"product_quantity_unit\":\"g\"}}", null)
            } else FakeConnection(url, 200, "", "session=x", failOnOutput = true)
        }
        val result = client.contribute(
            OffContributionDraft("123", "Drink", null, 100.0, MeasureUnit.GRAM, NutritionValues(100.0, null, null, null)),
            "user", "password"
        )
        assertTrue(result is OffContributionResult.Unavailable)
    }

    @Test fun contributionLoginRejectsOnlyAnExplicitAuthResponseAsAuthFailure() {
        val client = OpenFoodFactsClient { url ->
            when {
                url.path.contains("/api/v3/product/") -> FakeConnection(url, 200, "{\"product\":{\"nutrition_data_per\":\"100g\",\"product_quantity_unit\":\"g\"}}", null)
                url.path.endsWith("session.pl") -> FakeConnection(url, 401, "invalid credentials", null)
                else -> FakeConnection(url, 200, "{\"status\":1}", null)
            }
        }
        val result = client.contribute(
            OffContributionDraft("123", "Drink", null, 100.0, MeasureUnit.GRAM, NutritionValues(100.0, null, null, null)),
            "user", "password"
        )
        assertTrue(result is OffContributionResult.AuthFailed)
    }

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

    @Test fun searchAliciousHitsUseExplicitGramQuantityAndSkipAmbiguousUnits() {
        val raw = """
            {"hits":[
              {"code":"1","product_name":"Pesto","brands":["Brand"],"quantity":"200g","nutriments":{"energy-kcal_100g":400}},
              {"code":"2","product_name":"Pesto bites","quantity":"1pcs","nutriments":{"energy-kcal_100g":300}},
              {"code":"3","product_name":"Pesto drink","quantity":"250ml","nutriments":{"energy-kcal_100g":80}}
            ]}
        """.trimIndent()
        val result = OpenFoodFactsClient().parseSearchAliciousResponseForTesting(raw) as OffSearchResult.Success
        assertEquals(listOf("Pesto", "Pesto drink"), result.foods.map { it.name })
        assertEquals(MeasureUnit.GRAM, result.foods[0].nutrition.unit)
        assertEquals(MeasureUnit.MILLILITER, result.foods[1].nutrition.unit)
        assertEquals("Brand", result.foods[0].brand)
    }

    @Test fun searchAliciousQuantityRejectsMixedUnitsButAllowsRepeatedSameUnit() {
        val raw = """
            {"hits":[
              {"code":"1","product_name":"Mixed unit","quantity":"250 ml / 300 g","nutriments":{"energy-kcal_100g":100}},
              {"code":"2","product_name":"Repeated grams","quantity":"100g + 200 g","nutriments":{"energy-kcal_100g":200}}
            ]}
        """.trimIndent()
        val result = OpenFoodFactsClient().parseSearchAliciousResponseForTesting(raw) as OffSearchResult.Success
        assertEquals(listOf("Repeated grams"), result.foods.map { it.name })
        assertEquals(MeasureUnit.GRAM, result.foods.single().nutrition.unit)
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

private class FakeConnection(
    url: URL,
    private val status: Int,
    private val body: String,
    private val setCookie: String?,
    private val failOnOutput: Boolean = false
) : HttpURLConnection(url) {
    val written = ByteArrayOutputStream()
    val headersSeen = mutableMapOf<String, String>()

    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun usingProxy(): Boolean = false
    override fun getResponseCode(): Int = status
    override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
    override fun getErrorStream() = if (status in 200..299) null else ByteArrayInputStream(body.toByteArray())
    override fun getOutputStream() = if (failOnOutput) throw java.io.IOException("offline") else written
    override fun getHeaderFields(): MutableMap<String, MutableList<String>> = mutableMapOf<String, MutableList<String>>().apply {
        if (setCookie != null) put("Set-Cookie", mutableListOf(setCookie))
    }
    override fun setRequestProperty(key: String, value: String) { headersSeen[key] = value }
}
