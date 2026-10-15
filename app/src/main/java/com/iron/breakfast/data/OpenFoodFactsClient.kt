package com.iron.breakfast.data

import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.normalizeBarcode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

private fun prettyNumber(value: Double): String =
    "%.2f".format(java.util.Locale.ROOT, value).trimEnd('0').trimEnd('.')

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

data class OffContributionDraft(
    val barcode: String,
    val name: String,
    val brand: String?,
    val basisAmount: Double,
    val basisUnit: MeasureUnit,
    val nutrition: NutritionValues,
    val createNewProduct: Boolean = false,
    val languageCode: String = "en",
    val packageQuantityMl: Double? = null
) {
    val normalizedNutrition: NutritionValues
        get() = nutrition * (100.0 / basisAmount)

    fun publicFields(): List<String> = buildList {
        add("Barcode: $barcode")
        if (createNewProduct) {
            add("New product name (${languageCode.lowercase()}): $name")
            brand?.let { add("Brand: $it") }
            if (basisUnit == MeasureUnit.MILLILITER) packageQuantityMl?.let { add("Package quantity: ${prettyNumber(it)} ml") }
        }
        add("Nutrition basis: per 100 ${basisUnit.label}")
        add("Calories: ${prettyNumber(normalizedNutrition.calories)} kcal")
        normalizedNutrition.protein?.let { add("Protein: ${prettyNumber(it)} g") }
        normalizedNutrition.carbohydrates?.let { add("Carbohydrates: ${prettyNumber(it)} g") }
        normalizedNutrition.fat?.let { add("Fat: ${prettyNumber(it)} g") }
    }

    fun asFormFields(): Map<String, String> = buildMap {
        put("code", barcode)
        // OFF's write API accepts only 100g or serving here. For liquids, the
        // normalized values still represent 100 ml, as documented by the
        // product nutrition schema; changing this token would reinterpret all
        // existing nutrients on the product.
        put("nutrition_data_per", "100g")
        put("nutriment_energy-kcal", prettyNumber(normalizedNutrition.calories))
        put("nutriment_energy-kcal_unit", "kcal")
        normalizedNutrition.protein?.let {
            put("nutriment_proteins", prettyNumber(it))
            put("nutriment_proteins_unit", "g")
        }
        normalizedNutrition.carbohydrates?.let {
            put("nutriment_carbohydrates", prettyNumber(it))
            put("nutriment_carbohydrates_unit", "g")
        }
        normalizedNutrition.fat?.let {
            put("nutriment_fat", prettyNumber(it))
            put("nutriment_fat_unit", "g")
        }
        if (createNewProduct) {
            val language = languageCode.trim().lowercase()
            require(language == "en" || language == "mk") { "Product language must be en or mk." }
            put("lc", language)
            put("lang", language)
            put("product_name_$language", name)
            put("product_type", "food")
            brand?.takeIf { it.isNotBlank() }?.let { put("brands", it) }
            packageQuantityMl?.takeIf { basisUnit == MeasureUnit.MILLILITER }?.let { put("quantity", "${prettyNumber(it)} ml") }
            put("comment", "New product contribution from Breakfast")
        } else {
            put("comment", "Nutrition correction from Breakfast")
        }
        put("app_name", "Breakfast")
        put("app_version", "0.1")
    }

    companion object {
        fun fromFood(food: FoodRecord): OffContributionDraft? {
            val barcode = normalizeBarcode(food.barcode ?: return null) ?: return null
            val basis = food.nutrition
            if (food.name.isBlank() || !basis.amount.isFinite() || basis.amount <= 0.0) return null
            if (basis.unit != MeasureUnit.GRAM && basis.unit != MeasureUnit.MILLILITER) return null
            fun valid(value: Double?) = value == null || (value.isFinite() && value >= 0.0)
            if (!valid(basis.values.calories) || !valid(basis.values.protein) || !valid(basis.values.carbohydrates) || !valid(basis.values.fat)) return null
            return OffContributionDraft(barcode, food.name.trim(), food.brand, basis.amount, basis.unit, basis.values)
        }
    }
}

sealed interface OffContributionResult {
    data object Success : OffContributionResult
    data object AuthFailed : OffContributionResult
    data class Ineligible(val message: String) : OffContributionResult
    data class Rejected(val message: String) : OffContributionResult
    data class PreflightChanged(val message: String) : OffContributionResult
    data object Unavailable : OffContributionResult
    data class Error(val message: String) : OffContributionResult
}

private sealed interface OffLoginResult {
    data class Success(val cookie: String) : OffLoginResult
    data object AuthFailed : OffLoginResult
    data object Unavailable : OffLoginResult
    data class Error(val message: String) : OffLoginResult
}

sealed interface OffPreflightResult {
    data object Existing : OffPreflightResult
    data object NewProduct : OffPreflightResult
    data class Ineligible(val message: String) : OffPreflightResult
    data object Unavailable : OffPreflightResult
}

/** Explicit-submit adapter. It is deliberately independent of matching and Room. */
class OpenFoodFactsClient(
    private val connectionFactory: (URL) -> HttpURLConnection? = { url ->
        (url.openConnection() as? HttpURLConnection)
    }
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun search(query: String): OffSearchResult {
        val legacyUrl = URL("https://world.openfoodfacts.org/cgi/search.pl?search_terms=" + URLEncoder.encode(query, "UTF-8") + "&search_simple=1&action=process&json=1&page_size=20&fields=code,product_name,brands,nutriments,nutrition_data_per,product_quantity,product_quantity_unit,quantity,serving_size,serving_quantity_unit")
        return when (val legacy = requestSearch(legacyUrl, ::parseSearchResponse)) {
            OffSearchResult.Unavailable -> searchAlicious(query)
            else -> legacy
        }
    }

    private fun searchAlicious(query: String): OffSearchResult {
        val url = URL("https://search.openfoodfacts.org/search?q=" + URLEncoder.encode(query, "UTF-8") + "&page=1&page_size=20&fields=" + URLEncoder.encode("code,product_name,brands,nutriments,quantity,serving_size,serving_quantity_unit", "UTF-8"))
        return requestSearch(url, ::parseSearchAliciousResponse)
    }

    private fun requestSearch(url: URL, parser: (String) -> OffSearchResult): OffSearchResult {
        val connection = open(url) ?: return OffSearchResult.Unavailable
        return try {
            if (connection.responseCode !in 200..299) return OffSearchResult.Unavailable
            parser(connection.inputStream.bufferedReader().readText())
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

    fun contribute(draft: OffContributionDraft, username: String, password: String): OffContributionResult {
        if (username.isBlank() || password.isBlank()) return OffContributionResult.AuthFailed
        if (draft.createNewProduct && draft.languageCode.trim().lowercase() !in setOf("en", "mk")) {
            return OffContributionResult.Ineligible("Choose English (en) or Macedonian (mk) for a new product before submitting.")
        }
        val preflight = preflight(draft)
        when (preflight) {
            is OffPreflightResult.Ineligible -> return OffContributionResult.Ineligible(preflight.message)
            OffPreflightResult.Unavailable -> return OffContributionResult.Unavailable
            OffPreflightResult.Existing -> {
                if (draft.createNewProduct) return OffContributionResult.PreflightChanged("Open Food Facts changed this barcode while you were reviewing it. Review the current product state again before submitting.")
            }
            OffPreflightResult.NewProduct -> {
                if (!draft.createNewProduct) return OffContributionResult.PreflightChanged("Open Food Facts changed this barcode while you were reviewing it. Review the current product state again before submitting.")
                if (draft.basisUnit == MeasureUnit.MILLILITER &&
                    (draft.packageQuantityMl == null || !draft.packageQuantityMl.isFinite() || draft.packageQuantityMl <= 0.0)
                ) return OffContributionResult.Ineligible("New liquid products need the package quantity in ml before Breakfast can submit them.")
            }
        }
        val session = when (val login = login(username.trim(), password)) {
            is OffLoginResult.Success -> login.cookie
            OffLoginResult.AuthFailed -> return OffContributionResult.AuthFailed
            OffLoginResult.Unavailable -> return OffContributionResult.Unavailable
            is OffLoginResult.Error -> return OffContributionResult.Error(login.message)
        }
        // Re-read immediately before the write so a product that appeared after
        // the review cannot be treated as a new product (or vice versa).
        val latest = preflight(draft)
        when (latest) {
            is OffPreflightResult.Ineligible -> return OffContributionResult.Ineligible(latest.message)
            OffPreflightResult.Unavailable -> return OffContributionResult.Unavailable
            OffPreflightResult.Existing -> {
                if (draft.createNewProduct) return OffContributionResult.PreflightChanged("Open Food Facts changed this barcode while you were reviewing it. Review the current product state again before submitting.")
            }
            OffPreflightResult.NewProduct -> {
                if (!draft.createNewProduct) return OffContributionResult.PreflightChanged("Open Food Facts changed this barcode while you were reviewing it. Review the current product state again before submitting.")
                if (draft.basisUnit == MeasureUnit.MILLILITER &&
                    (draft.packageQuantityMl == null || !draft.packageQuantityMl.isFinite() || draft.packageQuantityMl <= 0.0)
                ) return OffContributionResult.Ineligible("New liquid products need the package quantity in ml before Breakfast can submit them.")
            }
        }
        if (latest is OffPreflightResult.NewProduct && draft.languageCode.trim().lowercase() !in setOf("en", "mk")) {
            return OffContributionResult.Ineligible("Choose English (en) or Macedonian (mk) for a new product before submitting.")
        }
        val remoteDraft = draft
        val connection = openPost(URL("https://world.openfoodfacts.org/cgi/product_jqm2.pl"))
            ?: return OffContributionResult.Unavailable
        return try {
            connection.setRequestProperty("Cookie", session)
            val response = postForm(connection, remoteDraft.asFormFields())
            classifyContributionResponse(response.first, response.second)
        } catch (_: java.io.IOException) {
            OffContributionResult.Unavailable
        } catch (_: Exception) {
            OffContributionResult.Error("Open Food Facts returned an unreadable contribution response.")
        } finally {
            connection.disconnect()
        }
    }

    fun preflight(draft: OffContributionDraft): OffPreflightResult {
        val connection = open(URL("https://world.openfoodfacts.org/api/v3/product/${URLEncoder.encode(draft.barcode, "UTF-8")}.json"))
            ?: return OffPreflightResult.Unavailable
        return try {
            if (connection.responseCode == 404) {
                return OffPreflightResult.NewProduct
            }
            if (connection.responseCode !in 200..299) return OffPreflightResult.Unavailable
            val product = json.parseToJsonElement(connection.inputStream.bufferedReader().readText()).jsonObject["product"]?.jsonObject
                ?: return OffPreflightResult.Unavailable
            val dataPer = product.string("nutrition_data_per")?.trim()?.lowercase()
            if (dataPer != "100g") {
                return OffPreflightResult.Ineligible("Open Food Facts stores this product's nutrition per serving or with an unclear basis, so Breakfast will not overwrite it with a partial correction.")
            }
            val sourceUnit = product.basisUnit()
                ?: return OffPreflightResult.Ineligible("Open Food Facts does not provide a clear gram or milliliter basis for this product.")
            if (sourceUnit != draft.basisUnit) {
                return OffPreflightResult.Ineligible("The local correction basis does not match the product's Open Food Facts basis, so it was not submitted.")
            }
            OffPreflightResult.Existing
        } catch (_: java.io.IOException) {
            OffPreflightResult.Unavailable
        } catch (_: Exception) {
            OffPreflightResult.Unavailable
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSearchResponse(raw: String): OffSearchResult = try {
        val root = json.parseToJsonElement(raw).jsonObject
        parseSearchArray(root, "products", "Open Food Facts returned no product list; try again or add manually.")
    } catch (_: Exception) {
        OffSearchResult.Error("Open Food Facts returned unreadable data; try again or add manually.")
    }

    private fun parseSearchAliciousResponse(raw: String): OffSearchResult = try {
        val root = json.parseToJsonElement(raw).jsonObject
        parseSearchArray(root, "hits", "Open Food Facts search returned no hit list; try again or add manually.")
    } catch (_: Exception) {
        OffSearchResult.Error("Open Food Facts returned unreadable data; try again or add manually.")
    }

    private fun parseSearchArray(root: kotlinx.serialization.json.JsonObject, key: String, missingMessage: String): OffSearchResult {
        val values = root[key]?.let { runCatching { it.jsonArray }.getOrNull() }
            ?: return OffSearchResult.Error(missingMessage)
        return OffSearchResult.Success(values.mapNotNull { element -> runCatching { parseProduct(element.jsonObject) }.getOrNull() })
    }

    internal fun parseSearchResponseForTesting(raw: String): OffSearchResult = parseSearchResponse(raw)
    internal fun parseSearchAliciousResponseForTesting(raw: String): OffSearchResult = parseSearchAliciousResponse(raw)

    private fun open(url: URL): HttpURLConnection? = try {
        connectionFactory(url)?.apply {
            connectTimeout = 4500; readTimeout = 4500; requestMethod = "GET"
            setRequestProperty("User-Agent", "Breakfast/0.1 (daily food logger)")
        }
    } catch (_: Exception) { null }

    private fun openPost(url: URL): HttpURLConnection? = try {
        connectionFactory(url)?.apply {
            connectTimeout = 4500; readTimeout = 4500; requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("User-Agent", "Breakfast/0.1 (daily food logger)")
        }
    } catch (_: Exception) { null }

    private fun login(username: String, password: String): OffLoginResult {
        val connection = openPost(URL("https://world.openfoodfacts.org/cgi/session.pl"))
            ?: return OffLoginResult.Unavailable
        return try {
            val (status, body) = postForm(connection, mapOf("user_id" to username, "password" to password, ".submit" to "Sign in"))
            if (status == 401 || status == 403 || body.contains("invalid", ignoreCase = true) && body.contains("password", ignoreCase = true)) {
                return OffLoginResult.AuthFailed
            }
            if (status !in 200..299) return OffLoginResult.Unavailable
            val cookie = connection.headerFields["Set-Cookie"]
                ?.mapNotNull { it.substringBefore(';').trim().takeIf(String::isNotBlank) }
                ?.joinToString("; ")
                ?.takeIf(String::isNotBlank)
            cookie?.let { OffLoginResult.Success(it) }
                ?: OffLoginResult.Error("Open Food Facts did not return a session for that account.")
        } catch (_: java.io.IOException) {
            OffLoginResult.Unavailable
        } catch (_: Exception) {
            OffLoginResult.Error("Open Food Facts returned an unreadable login response.")
        } finally { connection.disconnect() }
    }

    private fun postForm(connection: HttpURLConnection, fields: Map<String, String>): Pair<Int, String> {
        val body = fields.entries.joinToString("&") { (key, value) ->
            URLEncoder.encode(key, StandardCharsets.UTF_8.name()) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8.name())
        }
        connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        return connection.responseCode to (stream?.bufferedReader()?.readText().orEmpty())
    }

    internal fun classifyContributionResponseForTesting(status: Int, raw: String): OffContributionResult =
        classifyContributionResponse(status, raw)

    private fun classifyContributionResponse(status: Int, raw: String): OffContributionResult {
        if (status == 401 || status == 403) return OffContributionResult.AuthFailed
        if (status !in 200..299) return OffContributionResult.Rejected("Open Food Facts rejected the contribution (HTTP $status).")
        return runCatching {
            val root = json.parseToJsonElement(raw).jsonObject
            val ok = root["status"]?.jsonPrimitive?.content == "1"
            if (ok) OffContributionResult.Success
            else {
                val message = root["status_verbose"]?.jsonPrimitive?.contentOrNull ?: "Open Food Facts did not save the contribution."
                if (message.contains("auth", ignoreCase = true) || message.contains("password", ignoreCase = true) || message.contains("login", ignoreCase = true)) {
                    OffContributionResult.AuthFailed
                } else {
                    OffContributionResult.Rejected(message)
                }
            }
        }.getOrElse { OffContributionResult.Error("Open Food Facts returned an unreadable contribution response.") }
    }

    private fun parseProduct(p: kotlinx.serialization.json.JsonObject, fallbackBarcode: String? = null): FoodRecord? {
        val name = p.string("product_name")?.trim()?.ifBlank { null } ?: return null
        val n = p["nutriments"]?.jsonObject ?: return null
        // OFF normalizes both solids and liquids to *_100g. The package or
        // serving unit tells us whether those numbers are per 100 g or 100 ml.
        // nutrition_data_per describes the contributor's original input basis,
        // so it must not be used to choose our logging unit.
        val basisUnit = p.basisUnit() ?: return null
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

    private fun unitCandidates(value: String?): Set<MeasureUnit> {
        val units = Regex("(?i)(?<!\\p{L})(g|ml)(?!\\p{L})")
            .findAll(value.orEmpty())
            .mapNotNull { it.groupValues.getOrNull(1)?.lowercase() }
            .map { if (it == "g") MeasureUnit.GRAM else MeasureUnit.MILLILITER }
            .toSet()
        return units
    }

    private fun normalizedUnit(value: String?): MeasureUnit? = when (value?.trim()?.lowercase()) {
        "g" -> MeasureUnit.GRAM
        "ml" -> MeasureUnit.MILLILITER
        else -> null
    }

    /** Resolves a product's logging basis while rejecting conflicting evidence. */
    private fun kotlinx.serialization.json.JsonObject.basisUnit(): MeasureUnit? {
        val packageUnit = normalizedUnit(string("product_quantity_unit"))
        val quantityUnits = unitCandidates(string("quantity"))
        val servingUnit = normalizedUnit(string("serving_quantity_unit"))
        val servingSizeUnits = unitCandidates(string("serving_size"))

        // Mixed g/ml text is ambiguous even when another field happens to be
        // explicit; using that other field would silently guess the basis.
        if (quantityUnits.size > 1 || servingSizeUnits.size > 1) return null

        val quantityUnit = quantityUnits.singleOrNull()
        if (packageUnit != null && quantityUnit != null && packageUnit != quantityUnit) return null
        val packageBasis = packageUnit ?: quantityUnit

        val servingSizeUnit = servingSizeUnits.singleOrNull()
        if (servingUnit != null && servingSizeUnit != null && servingUnit != servingSizeUnit) return null
        val servingBasis = servingUnit ?: servingSizeUnit

        if (packageBasis != null && servingBasis != null && packageBasis != servingBasis) return null
        return packageBasis ?: servingBasis
    }

    private fun kotlinx.serialization.json.JsonObject.string(key: String): String? = when (val value = this[key]) {
        is kotlinx.serialization.json.JsonPrimitive -> value.contentOrNull
        is kotlinx.serialization.json.JsonArray -> value.mapNotNull { it.jsonPrimitive.contentOrNull }.joinToString(", ").ifBlank { null }
        else -> null
    }
    private fun kotlinx.serialization.json.JsonObject.number(key: String) = this[key]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

}
