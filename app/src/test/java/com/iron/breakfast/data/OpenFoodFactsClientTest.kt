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
            sourceName = "Open Food Facts", barcode = "4006381333931", brand = "Brand"
        )
        val fields = requireNotNull(OffContributionDraft.fromFood(food)).asFormFields()
        val publicFields = requireNotNull(OffContributionDraft.fromFood(food)).publicFields()
        assertEquals("4006381333931", fields["code"])
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

    @Test fun newProductFormIncludesDocumentedIdentityFieldsButCorrectionDoesNot() {
        val create = OffContributionDraft(
            "999", "Македонски напиток", "Бренд", 100.0, MeasureUnit.MILLILITER,
            NutritionValues(42.0, 1.0, null, 0.5), createNewProduct = true,
            languageCode = "mk", packageQuantityMl = 500.0
        ).asFormFields()
        assertEquals("mk", create["lc"])
        assertEquals("Македонски напиток", create["product_name_mk"])
        assertEquals("food", create["product_type"])
        assertEquals("Бренд", create["brands"])
        assertEquals("500 ml", create["quantity"])

        val existing = OffContributionDraft("999", "Name", "Brand", 100.0, MeasureUnit.GRAM, NutritionValues(42.0, null, null, null)).asFormFields()
        assertNull(existing["product_name_en"])
        assertNull(existing["brands"])
        assertNull(existing["lc"])
    }

    @Test fun customBarcodeFoodsAreEligibleForExplicitSubmission() {
        val food = FoodRecord(
            id = "custom", name = "My drink", aliases = emptyList(), category = "Personal food",
            origin = FoodOrigin.CUSTOM,
            nutrition = NutritionBasis(100.0, MeasureUnit.MILLILITER, NutritionValues(42.0, null, null, null)),
            sourceName = null, barcode = "4006381333931"
        )
        assertEquals("4006381333931", OffContributionDraft.fromFood(food)?.barcode)
    }

    @Test fun contributionDraftRejectsInvalidBarcodeLengthAndNonAsciiDigits() {
        val base = FoodRecord(
            id = "food", name = "Food", aliases = emptyList(), category = "Personal food",
            origin = FoodOrigin.CUSTOM,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(42.0, null, null, null)),
        )
        assertNull(OffContributionDraft.fromFood(base.copy(barcode = "1234567")))
        assertNull(OffContributionDraft.fromFood(base.copy(barcode = "１２３４５６７８")))
        assertNull(OffContributionDraft.fromFood(base.copy(barcode = "١٢٣٤٥٦٧٨")))
        assertEquals("4006381333931", OffContributionDraft.fromFood(base.copy(barcode = " 4006381333931 "))?.barcode)
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
        assertEquals(4, connections.size)
        val loginBody = connections[1].written.toString(Charsets.UTF_8)
        val productBody = connections[3].written.toString(Charsets.UTF_8)
        assertTrue(loginBody.contains("user_id=user"))
        assertTrue(loginBody.contains("password=password"))
        assertEquals("session=user&token=abc", connections[3].headersSeen["Cookie"])
        assertTrue(productBody.contains("code=123"))
        assertTrue(productBody.contains("nutriment_energy-kcal=200"))
        assertTrue(productBody.contains("nutrition_data_per=100g"))
        assertTrue(!productBody.contains("entered_amount"))
        assertTrue(!productBody.contains("entered_unit"))
        assertTrue(!productBody.contains("alias"))
    }

    @Test fun missingBarcodePreflightCreatesNewProductWithLanguageAndLiquidQuantity() {
        val connections = mutableListOf<FakeConnection>()
        val client = OpenFoodFactsClient { url ->
            FakeConnection(
                url = url,
                status = if (url.path.contains("/api/v3/product/")) 404 else 200,
                body = if (url.path.endsWith("session.pl")) "logged in" else "{\"status\":1}",
                setCookie = if (url.path.endsWith("session.pl")) "session=user&token=abc" else null
            ).also { connections += it }
        }
        val result = client.contribute(
            OffContributionDraft("999", "Македонски напиток", "Бренд", 100.0, MeasureUnit.MILLILITER,
                NutritionValues(42.0, null, null, null), createNewProduct = true, languageCode = "mk", packageQuantityMl = 500.0),
            "user", "password"
        )
        assertTrue(result is OffContributionResult.Success)
        val body = connections.last().written.toString(Charsets.UTF_8)
        assertTrue(body.contains("product_name_mk=%D0%9C%D0%B0%D0%BA%D0%B5%D0%B4%D0%BE%D0%BD%D1%81%D0%BA%D0%B8"))
        assertTrue(body.contains("quantity=500+ml"))
        assertTrue(body.contains("product_type=food"))
    }

    @Test fun newLiquidProductRequiresPackageQuantityBeforeLogin() {
        val connections = mutableListOf<FakeConnection>()
        val client = OpenFoodFactsClient { url ->
            FakeConnection(url, if (url.path.contains("/api/v3/product/")) 404 else 200, "", null).also { connections += it }
        }
        val result = client.contribute(
            OffContributionDraft("999", "Drink", null, 100.0, MeasureUnit.MILLILITER, NutritionValues(42.0, null, null, null), createNewProduct = true),
            "user", "password"
        )
        assertTrue(result is OffContributionResult.Ineligible)
        assertEquals(1, connections.size)
    }

    @Test fun preflightModeChangeFromMissingToExistingAbortsBeforeWrite() {
        val connections = mutableListOf<FakeConnection>()
        var productReads = 0
        val client = OpenFoodFactsClient { url ->
            val isProduct = url.path.contains("/api/v3/product/")
            val status = if (isProduct) {
                productReads++
                if (productReads == 1) 404 else 200
            } else 200
            FakeConnection(url, status, if (isProduct) "{\"product\":{\"nutrition_data_per\":\"100g\",\"product_quantity_unit\":\"g\",\"nutriments\":{\"energy-kcal_100g\":100}}}" else "{\"status\":1}", if (url.path.endsWith("session.pl")) "session=x" else null).also { connections += it }
        }
        val result = client.contribute(OffContributionDraft("123", "Food", null, 100.0, MeasureUnit.GRAM, NutritionValues(100.0, null, null, null), createNewProduct = true, languageCode = "en"), "user", "password")
        assertTrue(result is OffContributionResult.PreflightChanged)
        assertEquals(3, connections.size)
    }

    @Test fun preflightModeChangeFromExistingToMissingAbortsBeforeWrite() {
        val connections = mutableListOf<FakeConnection>()
        var productReads = 0
        val client = OpenFoodFactsClient { url ->
            val isProduct = url.path.contains("/api/v3/product/")
            val status = if (isProduct) {
                productReads++
                if (productReads == 1) 200 else 404
            } else 200
            FakeConnection(url, status, if (isProduct) "{\"product\":{\"nutrition_data_per\":\"100g\",\"product_quantity_unit\":\"g\",\"nutriments\":{\"energy-kcal_100g\":100}}}" else "{\"status\":1}", if (url.path.endsWith("session.pl")) "session=x" else null).also { connections += it }
        }
        val result = client.contribute(OffContributionDraft("123", "Food", null, 100.0, MeasureUnit.GRAM, NutritionValues(100.0, null, null, null)), "user", "password")
        assertTrue(result is OffContributionResult.PreflightChanged)
        assertEquals(3, connections.size)
    }

    @Test fun reviewedExistingModeAbortsIfFirstPreflightFindsNewProduct() {
        val connections = mutableListOf<FakeConnection>()
        val client = OpenFoodFactsClient { url ->
            FakeConnection(url, if (url.path.contains("/api/v3/product/")) 404 else 200, "", null).also { connections += it }
        }
        val result = client.contribute(OffContributionDraft("123", "Food", null, 100.0, MeasureUnit.GRAM, NutritionValues(100.0, null, null, null), createNewProduct = false), "user", "password")
        assertTrue(result is OffContributionResult.PreflightChanged)
        assertEquals(1, connections.size)
    }

    @Test fun reviewedNewModeAbortsIfFirstPreflightFindsExistingProduct() {
        val connections = mutableListOf<FakeConnection>()
        val client = OpenFoodFactsClient { url ->
            FakeConnection(url, 200, if (url.path.contains("/api/v3/product/")) "{\"product\":{\"nutrition_data_per\":\"100g\",\"product_quantity_unit\":\"g\",\"nutriments\":{\"energy-kcal_100g\":100}}}" else "", null).also { connections += it }
        }
        val result = client.contribute(OffContributionDraft("123", "Food", null, 100.0, MeasureUnit.GRAM, NutritionValues(100.0, null, null, null), createNewProduct = true, languageCode = "en"), "user", "password")
        assertTrue(result is OffContributionResult.PreflightChanged)
        assertEquals(1, connections.size)
    }

    @Test fun unsupportedCreationLanguageIsRejectedWithoutNetworkWrite() {
        val connections = mutableListOf<FakeConnection>()
        val client = OpenFoodFactsClient { url -> FakeConnection(url, 404, "", null).also { connections += it } }
        val result = client.contribute(
            OffContributionDraft("123", "Food", null, 100.0, MeasureUnit.GRAM, NutritionValues(100.0, null, null, null), createNewProduct = true, languageCode = "sr"),
            "user", "password"
        )
        assertTrue(result is OffContributionResult.Ineligible)
        assertEquals(0, connections.size)
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

    @Test fun legacySearchRequestsServingBasisFieldsAndParsesServingOnlyProducts() {
        val urls = mutableListOf<String>()
        val client = OpenFoodFactsClient { url ->
            urls += url.toExternalForm()
            FakeConnection(
                url, 200,
                """{"products":[{"code":"legacy-1","product_name":"Serving yogurt","nutrition_data_per":"100g","serving_size":"1 portion (100 g)","nutriments":{"energy-kcal_100g":42}}]}""",
                null
            )
        }

        val result = client.search("yogurt") as OffSearchResult.Success
        assertEquals(listOf("Serving yogurt"), result.foods.map { it.name })
        assertEquals(MeasureUnit.GRAM, result.foods.single().nutrition.unit)
        assertTrue(urls.single().contains("serving_size"))
        assertTrue(urls.single().contains("serving_quantity_unit"))
    }

    @Test fun searchFallbackRequestsServingBasisFieldsAndParsesServingOnlyProducts() {
        val urls = mutableListOf<String>()
        val client = OpenFoodFactsClient { url ->
            urls += url.toExternalForm()
            val legacy = url.host == "world.openfoodfacts.org"
            FakeConnection(
                url,
                if (legacy) 503 else 200,
                if (legacy) "" else """{"hits":[{"code":"fallback-1","product_name":"Serving yogurt","nutrition_data_per":"100g","serving_size":"1 portion (100 g)","nutriments":{"energy-kcal_100g":42}}]}""",
                null
            )
        }

        val result = client.search("yogurt") as OffSearchResult.Success
        assertEquals(listOf("Serving yogurt"), result.foods.map { it.name })
        assertEquals(MeasureUnit.GRAM, result.foods.single().nutrition.unit)
        assertTrue(urls.last().contains("serving_size"))
        assertTrue(urls.last().contains("serving_quantity_unit"))
    }

    @Test fun aBlankCodeIsNoCodeSoProductsDoNotShareOneId() {
        val raw = """
            {"products":[
              {"code":"","product_name":"Pesto","product_quantity_unit":"g","nutriments":{"energy-kcal_100g":400}},
              {"code":" ","product_name":"Ajvar","product_quantity_unit":"g","nutriments":{"energy-kcal_100g":80}}
            ]}
        """.trimIndent()
        val foods = (OpenFoodFactsClient().parseSearchResponseForTesting(raw) as OffSearchResult.Success).foods
        assertEquals(listOf("off-pesto", "off-ajvar"), foods.map { it.id })
        assertEquals(listOf(null, null), foods.map { it.barcode })
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

    @Test fun searchFallbackHitsUseExplicitGramQuantityAndSkipAmbiguousUnits() {
        val raw = """
            {"hits":[
              {"code":"1","product_name":"Pesto","brands":["Brand"],"quantity":"200g","nutriments":{"energy-kcal_100g":400}},
              {"code":"2","product_name":"Pesto bites","quantity":"1pcs","nutriments":{"energy-kcal_100g":300}},
              {"code":"3","product_name":"Pesto drink","quantity":"250ml","nutriments":{"energy-kcal_100g":80}}
            ]}
        """.trimIndent()
        val result = OpenFoodFactsClient().parseFallbackSearchResponseForTesting(raw) as OffSearchResult.Success
        assertEquals(listOf("Pesto", "Pesto drink"), result.foods.map { it.name })
        assertEquals(MeasureUnit.GRAM, result.foods[0].nutrition.unit)
        assertEquals(MeasureUnit.MILLILITER, result.foods[1].nutrition.unit)
        assertEquals("Brand", result.foods[0].brand)
    }

    @Test fun searchFallbackQuantityRejectsMixedUnitsButAllowsRepeatedSameUnit() {
        val raw = """
            {"hits":[
              {"code":"1","product_name":"Mixed unit","quantity":"250 ml / 300 g","nutriments":{"energy-kcal_100g":100}},
              {"code":"2","product_name":"Repeated grams","quantity":"100g + 200 g","nutriments":{"energy-kcal_100g":200}}
            ]}
        """.trimIndent()
        val result = OpenFoodFactsClient().parseFallbackSearchResponseForTesting(raw) as OffSearchResult.Success
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

    @Test fun servingBasisParsesBellaFarmRecordAndPreflightAcceptsIt() {
        val product = """
            {
              "code":"5310153028451",
              "product_name":"Greek Yogurt",
              "nutrition_data_per":"100g",
              "serving_quantity":100,
              "serving_quantity_unit":"g",
              "serving_size":"1 portion (100 g)",
              "nutriments":{"energy-kcal_100g":77,"proteins_100g":4.5,"fat_100g":3.8}
            }
        """.trimIndent()
        val client = OpenFoodFactsClient { url ->
            FakeConnection(url, 200, "{\"product\":$product}", null)
        }

        val food = requireNotNull(client.parseProductForTesting(product))
        assertEquals(MeasureUnit.GRAM, food.nutrition.unit)
        assertEquals("5310153028451", food.barcode)
        assertTrue(
            client.preflight(
                OffContributionDraft(
                    barcode = "5310153028451", name = "Greek Yogurt", brand = null,
                    basisAmount = 100.0, basisUnit = MeasureUnit.GRAM,
                    nutrition = NutritionValues(77.0, 4.5, null, 3.8)
                )
            ) is OffPreflightResult.Existing
        )
        assertTrue(
            client.preflight(
                OffContributionDraft(
                    barcode = "5310153028451", name = "Greek Yogurt", brand = null,
                    basisAmount = 100.0, basisUnit = MeasureUnit.MILLILITER,
                    nutrition = NutritionValues(77.0, 4.5, null, 3.8)
                )
            ) is OffPreflightResult.Ineligible
        )
    }

    @Test fun parenthesizedServingTextProvidesBasisWithoutNormalizedServingUnit() {
        val food = OpenFoodFactsClient().parseProductForTesting(
            """{"product_name":"Text-only yogurt","nutrition_data_per":"100g","serving_size":"1 portion (100 g)","nutriments":{"energy-kcal_100g":42}}"""
        )
        assertEquals(MeasureUnit.GRAM, requireNotNull(food).nutrition.unit)
    }

    @Test fun rejectsConflictingPackageAndServingBasis() {
        val raw = """
            {"product_name":"Conflicting product","product_quantity":"500","product_quantity_unit":"g",
             "serving_size":"1 portion (100 ml)","nutrition_data_per":"100g",
             "nutriments":{"energy-kcal_100g":42}}
        """.trimIndent()
        assertNull(OpenFoodFactsClient().parseProductForTesting(raw))

        val client = OpenFoodFactsClient { url -> FakeConnection(url, 200, "{\"product\":$raw}", null) }
        val result = client.preflight(
            OffContributionDraft("123", "Conflicting product", null, 100.0, MeasureUnit.GRAM, NutritionValues(42.0, null, null, null))
        )
        assertTrue(result is OffPreflightResult.Ineligible)
    }

    @Test fun rejectsAmbiguousOrNonBasisServingText() {
        val client = OpenFoodFactsClient()
        assertNull(client.parseProductForTesting("""{"product_name":"Mixed","serving_size":"100 g / 100 ml","nutrition_data_per":"100g","nutriments":{"energy-kcal_100g":42}}"""))
        assertNull(client.parseProductForTesting("""{"product_name":"Supplement","serving_size":"30 mg","nutrition_data_per":"100g","nutriments":{"energy-kcal_100g":42}}"""))
        assertNull(client.parseProductForTesting("""{"product_name":"Unknown","serving_size":"one portion","nutrition_data_per":"100g","nutriments":{"energy-kcal_100g":42}}"""))
    }

    @Test fun rejectsSlashSeparatedMixedUnitsInEitherOrderDuringParseAndPreflight() {
        listOf("100 g/100 ml", "100 ml/100 g").forEach { servingSize ->
            val raw = """
                {"product_name":"Mixed","serving_size":"$servingSize","nutrition_data_per":"100g",
                 "nutriments":{"energy-kcal_100g":42}}
            """.trimIndent()
            assertNull(OpenFoodFactsClient().parseProductForTesting(raw))

            val client = OpenFoodFactsClient { url -> FakeConnection(url, 200, "{\"product\":$raw}", null) }
            val result = client.preflight(
                OffContributionDraft("123", "Mixed", null, 100.0, MeasureUnit.GRAM, NutritionValues(42.0, null, null, null))
            )
            assertTrue(result is OffPreflightResult.Ineligible)
        }
    }

    @Test fun rejectsUnsuffixedEnergyAndImpossibleCalories() {
        assertNull(OpenFoodFactsClient().parseProductForTesting("""{"product_name":"Ambiguous","nutriments":{"energy-kcal":42}}"""))
        assertNull(OpenFoodFactsClient().parseProductForTesting("""{"product_name":"Bad","product_quantity_unit":"g","nutriments":{"energy-kcal_100g":2000}}"""))
        assertNull(OpenFoodFactsClient().parseProductForTesting("""{"product_name":"Bad macro","product_quantity_unit":"g","nutriments":{"energy-kcal_100g":42,"fat_100g":-1}}"""))
    }

    @Test fun barcodeServerErrorsAreErrorsAndOnlyNoConnectionIsUnavailable() {
        fun lookup(connection: (URL) -> HttpURLConnection?) = OpenFoodFactsClient(connection).barcode("3017620422003")
        listOf(503, 429, 500).forEach { status ->
            assertEquals(OffResult.Error(OFF_NOT_RESPONDING), lookup { url -> FakeConnection(url, status, "", null) })
        }
        assertEquals(OffResult.Error(OFF_NOT_RESPONDING), lookup { url -> FakeConnection(url, 200, "<html>busy</html>", null) })
        assertEquals(OffResult.Error(OFF_NOT_RESPONDING), lookup { url -> FakeConnection(url, 200, "{\"product\":[1]}", null) })
        assertEquals(OffResult.Unavailable, lookup { url -> FakeConnection(url, 200, "", null, failOnResponse = true) })
        assertEquals(OffResult.Unavailable, lookup { throw java.io.IOException("no route") })
        assertEquals(OffResult.Missing, lookup { url -> FakeConnection(url, 404, "{\"status\":\"failure\"}", null) })
    }

    @Test fun aCodeThatIsNotAProductBarcodeIsNeverSent() {
        val connections = mutableListOf<FakeConnection>()
        val client = OpenFoodFactsClient { url -> FakeConnection(url, 200, "{}", null).also { connections += it } }
        assertEquals(OffResult.Missing, client.barcode("WIFI:S:x;;"))
        assertEquals(0, connections.size)
    }

    @Test fun searchServerErrorsAreErrorsAndOnlyNoConnectionIsUnavailable() {
        fun search(connection: (URL) -> HttpURLConnection?) = OpenFoodFactsClient(connection).search("yogurt")
        listOf(503, 429).forEach { status ->
            assertTrue(search { url -> FakeConnection(url, status, "", null) } is OffSearchResult.Error)
        }
        assertTrue(search { url -> FakeConnection(url, 200, "not-json", null) } is OffSearchResult.Error)
        assertEquals(OffSearchResult.Unavailable, search { url -> FakeConnection(url, 200, "", null, failOnResponse = true) })
        // search.pl answers 503 and the fallback cannot connect: the server error is reported, not "no connection".
        assertTrue(search { url ->
            if (url.host == "world.openfoodfacts.org") FakeConnection(url, 503, "", null) else FakeConnection(url, 200, "", null, failOnResponse = true)
        } is OffSearchResult.Error)
        // search.pl cannot connect and the fallback answers 429: an error too.
        assertTrue(search { url ->
            if (url.host == "world.openfoodfacts.org") FakeConnection(url, 200, "", null, failOnResponse = true) else FakeConnection(url, 429, "", null)
        } is OffSearchResult.Error)
    }

    @Test fun aRecordWithNoUnitEvidenceKeepsTheLocalBasisInPreflight() {
        // 5310382009733 (Apetini peanut butter) as Open Food Facts holds it: per 100g, and no quantity or serving field.
        val product = """{"code":"5310382009733","product_name":"Peanut butter","nutrition_data_per":"100g","nutriments":{"energy-kcal_100g":600}}"""
        val client = OpenFoodFactsClient { url -> FakeConnection(url, 200, "{\"product\":$product}", null) }
        val draft = OffContributionDraft("5310382009733", "Peanut butter", "Apetini", 100.0, MeasureUnit.GRAM, NutritionValues(600.0, 25.0, 15.0, 50.0))
        assertEquals(OffPreflightResult.Existing, client.preflight(draft))
        assertEquals(OffPreflightResult.Existing, client.preflight(draft.copy(basisUnit = MeasureUnit.MILLILITER)))
        // Reading it for logging still needs a unit: nothing is guessed there.
        assertNull(client.parseProductForTesting(product))
    }

    @Test fun quantityTextInKgLitresCentilitresAndDecilitresIsUnitEvidence() {
        fun unitOf(quantity: String) = OpenFoodFactsClient().parseProductForTesting(
            """{"product_name":"Item","quantity":"$quantity","nutrition_data_per":"100g","nutriments":{"energy-kcal_100g":42}}"""
        )?.nutrition?.unit
        assertEquals(MeasureUnit.GRAM, unitOf("1 kg"))
        assertEquals(MeasureUnit.GRAM, unitOf("0,5KG"))
        assertEquals(MeasureUnit.GRAM, unitOf("250 G"))
        assertEquals(MeasureUnit.MILLILITER, unitOf("1L"))
        assertEquals(MeasureUnit.MILLILITER, unitOf("0,5 l"))
        assertEquals(MeasureUnit.MILLILITER, unitOf("33 cl"))
        assertEquals(MeasureUnit.MILLILITER, unitOf("2 dl"))
        assertEquals(MeasureUnit.MILLILITER, unitOf("500ML"))
        assertNull(unitOf("330 ml (340 g)"))
        assertNull(unitOf("1 l / 1 kg"))
        assertNull(unitOf("500 mg"))
        assertNull(unitOf("2 lb"))
    }

    @Test fun preflightStillRefusesMixedOrDisagreeingUnits() {
        fun preflight(fields: String, unit: MeasureUnit = MeasureUnit.GRAM): OffPreflightResult {
            val client = OpenFoodFactsClient { url -> FakeConnection(url, 200, "{\"product\":{\"nutrition_data_per\":\"100g\",$fields}}", null) }
            return client.preflight(OffContributionDraft("5310382009733", "Item", null, 100.0, unit, NutritionValues(100.0, null, null, null)))
        }
        assertTrue(preflight("\"quantity\":\"330 ml (340 g)\"") is OffPreflightResult.Ineligible)
        assertTrue(preflight("\"product_quantity_unit\":\"g\",\"quantity\":\"1 l\"") is OffPreflightResult.Ineligible)
        assertTrue(preflight("\"quantity\":\"1 kg\"", MeasureUnit.MILLILITER) is OffPreflightResult.Ineligible)
        assertEquals(OffPreflightResult.Existing, preflight("\"quantity\":\"1 kg\""))
        assertEquals(OffPreflightResult.Existing, preflight("\"quantity\":\"33 cl\"", MeasureUnit.MILLILITER))
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
    private val failOnOutput: Boolean = false,
    /** The request goes out, then reading the response fails: a connection lost before the answer arrived. */
    private val failOnResponse: Boolean = false,
) : HttpURLConnection(url) {
    val written = ByteArrayOutputStream()
    val headersSeen = mutableMapOf<String, String>()

    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun usingProxy(): Boolean = false
    override fun getResponseCode(): Int = if (failOnResponse) throw java.io.IOException("connection reset") else status
    override fun getInputStream() = if (failOnResponse) throw java.io.IOException("connection reset") else ByteArrayInputStream(body.toByteArray())
    override fun getErrorStream() = if (status in 200..299) null else ByteArrayInputStream(body.toByteArray())
    override fun getOutputStream() = if (failOnOutput) throw java.io.IOException("offline") else written
    override fun getHeaderFields(): MutableMap<String, MutableList<String>> = mutableMapOf<String, MutableList<String>>().apply {
        if (setCookie != null) put("Set-Cookie", mutableListOf(setCookie))
    }
    override fun setRequestProperty(key: String, value: String) { headersSeen[key] = value }
}
