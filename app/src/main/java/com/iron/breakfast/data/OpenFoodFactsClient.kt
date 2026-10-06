package com.iron.breakfast.data

import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

sealed interface OffResult {
    data class Found(val food: FoodRecord) : OffResult
    data object Missing : OffResult
    data class Ineligible(val name: String?, val brand: String?, val barcode: String) : OffResult
    data object Unavailable : OffResult
    data class Error(val message: String) : OffResult
}

sealed interface OffSearchResult {
    data class Success(val foods: List<FoodRecord>) : OffSearchResult
    data object Unavailable : OffSearchResult
    data class Error(val message: String) : OffSearchResult
}

/** Explicit-submit adapter. It is deliberately independent of matching and Room. */
class OpenFoodFactsClient {
    private val json = Json { ignoreUnknownKeys = true }

    fun search(query: String): OffSearchResult {
        val url = URL("https://world.openfoodfacts.org/cgi/search.pl?search_terms=" + URLEncoder.encode(query, "UTF-8") + "&search_simple=1&action=process&json=1&page_size=20&fields=code,product_name,brands,nutriments,nutrition_data_per,product_quantity,product_quantity_unit,quantity")
        val connection = open(url) ?: return OffSearchResult.Unavailable
        return try {
            if (connection.responseCode !in 200..299) return OffSearchResult.Unavailable
            parseSearchResponse(connection.inputStream.bufferedReader().readText())
        } catch (_: java.io.IOException) {
            OffSearchResult.Unavailable
        } catch (_: Exception) {
            OffSearchResult.Error("Open Food Facts returned unreadable data; try again or add manually.")
        } finally {
            connection.disconnect()
        }
    }

    fun barcode(code: String): OffResult {
        val connection = open(URL("https://world.openfoodfacts.org/api/v3/product/${URLEncoder.encode(code, "UTF-8")}.json")) ?: return OffResult.Unavailable
        return try {
            if (connection.responseCode == 404) return OffResult.Missing
            if (connection.responseCode !in 200..299) return OffResult.Unavailable
            val product = json.parseToJsonElement(connection.inputStream.bufferedReader().readText()).jsonObject["product"]?.jsonObject
                ?: return OffResult.Missing
            parseProduct(product, code)?.let { OffResult.Found(it) } ?: OffResult.Ineligible(product.string("product_name"), product.string("brands"), code)
        } catch (_: Exception) { OffResult.Unavailable } finally { connection.disconnect() }
    }

    private fun parseSearchResponse(raw: String): OffSearchResult = try {
        val root = json.parseToJsonElement(raw).jsonObject
        val products = root["products"]?.jsonArray
            ?: return OffSearchResult.Error("Open Food Facts returned no product list; try again or add manually.")
        OffSearchResult.Success(products.mapNotNull { parseProduct(it.jsonObject) })
    } catch (_: Exception) {
        OffSearchResult.Error("Open Food Facts returned unreadable data; try again or add manually.")
    }

    internal fun parseSearchResponseForTesting(raw: String): OffSearchResult = parseSearchResponse(raw)

    private fun open(url: URL): HttpURLConnection? = try {
        (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 4500; readTimeout = 4500; requestMethod = "GET"
            setRequestProperty("User-Agent", "IronBreakfast/0.1 (daily food logger)")
        }
    } catch (_: Exception) { null }

    private fun parseProduct(p: kotlinx.serialization.json.JsonObject, fallbackBarcode: String? = null): FoodRecord? {
        val name = p.string("product_name")?.trim()?.ifBlank { null } ?: return null
        val n = p["nutriments"]?.jsonObject ?: return null
        // OFF normalizes both solids and liquids to *_100g. The package's
        // quantity unit tells us whether those numbers are per 100 g or 100 ml.
        // nutrition_data_per describes the contributor's original input basis,
        // so it must not be used to choose our logging unit.
        val basisUnit = when (p.string("product_quantity_unit")?.trim()?.lowercase()) {
            "g" -> MeasureUnit.GRAM
            "ml" -> MeasureUnit.MILLILITER
            else -> return null
        }
        val calories = when {
            n.containsKey("energy-kcal_100g") -> n.number("energy-kcal_100g") ?: return null
            n.containsKey("energy-kj_100g") -> n.number("energy-kj_100g")?.div(4.184) ?: return null
            else -> return null
        }
        if (!calories.isFinite() || calories < 0 || calories > 1000) return null
        fun macro(key: String): Double? {
            if (!n.containsKey(key)) return null
            val value = n.number(key) ?: return Double.NaN
            return value.takeIf { it.isFinite() && it >= 0.0 && it <= 100.0 } ?: Double.NaN
        }
        val protein = macro("proteins_100g")
        val carbs = macro("carbohydrates_100g")
        val fat = macro("fat_100g")
        if (listOf(protein, carbs, fat).any { it?.isNaN() == true }) return null
        val barcode = p.string("code") ?: fallbackBarcode
        return FoodRecord(
            id = "off-" + (barcode ?: name.lowercase().replace(" ", "-")), name = name,
            aliases = emptyList(), category = "Open Food Facts",
            origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(100.0, basisUnit, NutritionValues(calories, protein, carbs, fat)),
            conversions = listOf(com.iron.breakfast.domain.ServingConversion(basisUnit, 1.0, "Open Food Facts per 100 ${basisUnit.label}")),
            defaultAmount = 100.0, defaultUnit = basisUnit,
            sourceId = barcode, sourceName = "Open Food Facts", sourceRelease = "v3",
            sourceDescription = "Selected on explicit search; cached only when added", brand = p.string("brands"), barcode = barcode
        )
    }

    internal fun parseProductForTesting(raw: String, fallbackBarcode: String? = null): FoodRecord? =
        json.parseToJsonElement(raw).jsonObject.let { parseProduct(it, fallbackBarcode) }

    private fun kotlinx.serialization.json.JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull
    private fun kotlinx.serialization.json.JsonObject.number(key: String) = this[key]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
}
