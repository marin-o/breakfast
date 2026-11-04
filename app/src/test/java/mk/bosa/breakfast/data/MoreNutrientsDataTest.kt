package mk.bosa.breakfast.data

import mk.bosa.breakfast.domain.FoodOrigin
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.MealEntry
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.NutritionBasis
import mk.bosa.breakfast.domain.NutritionValues
import mk.bosa.breakfast.domain.ProfileValues
import mk.bosa.breakfast.domain.RecipeIngredient
import mk.bosa.breakfast.profile.ProfilePrefs
import mk.bosa.breakfast.settings.FakeSettingsStore
import mk.bosa.breakfast.ui.components.LongDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Saturated fat, sugars, fibre and salt in storage, the diary file, Open Food Facts and the USDA catalogue. */
class MoreNutrientsDataTest {
    private val values = NutritionValues(389.0, 16.9, 66.3, 6.9, saturatedFat = 1.2, sugars = 1.0, fibre = 10.6, salt = 0.02)
    private val food = FoodRecord("oats", "Oats", emptyList(), "Custom", FoodOrigin.CUSTOM, NutritionBasis(100.0, MeasureUnit.GRAM, values), barcode = "5310382009733")
    private val entry = MealEntry("e", "oats", "Oats", 40.0, MeasureUnit.GRAM, 40.0, values * 0.4, "40 g", false, 1_000L, dayKey = "2026-11-04")
    private val ingredient = RecipeIngredient("oats", "Oats", 80.0, MeasureUnit.GRAM, 80.0, MeasureUnit.GRAM, values * 0.8)

    @Test fun roomRowsKeepTheFour() {
        assertEquals(values, food.toEntity().toDomain(emptyList(), emptyList()).nutrition.values)
        assertEquals(entry.nutritionSnapshot, entry.toEntity().toDomain().nutritionSnapshot)
        assertEquals(ingredient.nutritionSnapshot, ingredient.toEntity("recipe-p", 0).toDomain().nutritionSnapshot)
        val unknown = food.copy(nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(100.0, null, null, null)))
        val read = unknown.toEntity().toDomain(emptyList(), emptyList()).nutrition.values
        assertNull(read.saturatedFat); assertNull(read.sugars); assertNull(read.fibre); assertNull(read.salt)
    }

    private val settings = DiarySettings(dayStartHour = 4, longDraft = LongDraft.TWO_LINES)
    private fun rows(profile: ProfileValues? = null) = DiaryRows(
        foods = listOf(food.toEntity()), aliases = emptyList(), conversions = emptyList(), preferences = emptyList(),
        entries = listOf(entry.toEntity()), recipes = listOf(RecipeEntity("recipe-p", "Porridge", 2.0, null)),
        recipeIngredients = listOf(ingredient.toEntity("recipe-p", 0)), profile = profile, weightPoints = emptyList(),
    )

    @Test fun theExportCarriesTheFourAndTheFibreTarget() {
        val profile = ProfileValues(kcalTarget = 2000.0, fibreTarget = 30.0, trackFibre = true)
        val imported = decodeDiary(encodeDiary(diaryFileOf(rows(profile), settings, 5_000L))) as DiaryImport.Valid
        assertEquals(listOf(food.toEntity()), imported.rows.foods)
        assertEquals(listOf(entry.toEntity()), imported.rows.entries)
        assertEquals(listOf(ingredient.toEntity("recipe-p", 0)), imported.rows.recipeIngredients)
        assertEquals(profile, imported.rows.profile)
    }

    /** A file from before has none of the fields: it imports, and they read as unknown and as no target. */
    @Test fun anOlderFileWithoutThemStillImports() {
        val encoded = encodeDiary(diaryFileOf(rows(ProfileValues(kcalTarget = 2000.0)), settings, 5_000L))
        val old = encoded
            .replace("\"formatVersion\":$DIARY_FORMAT_VERSION,\"schemaVersion\":$DIARY_SCHEMA_VERSION,", "\"formatVersion\":5,\"schemaVersion\":12,")
            .replace(Regex(",\"saturatedFat\":[^,]*,\"sugars\":[^,]*,\"fibre\":[^,]*,\"salt\":[^,}]*"), "")
            .replace(Regex(",\"snapshotSaturatedFat\":[^,]*,\"snapshotSugars\":[^,]*,\"snapshotFibre\":[^,]*,\"snapshotSalt\":[^,}]*"), "")
            .replace(",\"fibreTarget\":null,\"trackFibre\":false", "")
        listOf("saturatedFat", "Sugars", "Fibre", "\"salt\"", "fibreTarget", "trackFibre").forEach { assertFalse(it, it in old) }
        val imported = decodeDiary(old) as DiaryImport.Valid
        val read = imported.rows.foods.single()
        assertNull(read.saturatedFat); assertNull(read.sugars); assertNull(read.fibre); assertNull(read.salt)
        assertNull(imported.rows.entries.single().snapshotFibre)
        assertNull(imported.rows.recipeIngredients.single().snapshotSalt)
        assertNull(imported.rows.profile!!.fibreTarget)
        assertFalse(imported.rows.profile!!.trackFibre)
        assertEquals(16.9, read.protein!!, 0.0)
    }

    @Test fun negativeNutrientsOrAFibreTargetOutOfRangeAreRefused() {
        val file = diaryFileOf(rows(), settings, 5_000L)
        fun refused(f: DiaryFile) = assertTrue(decodeDiary(encodeDiary(f)) is DiaryImport.Invalid)
        refused(file.copy(foods = file.foods.map { it.copy(fibre = -1.0) }))
        refused(file.copy(mealEntries = file.mealEntries.map { it.copy(snapshotSalt = -0.1) }))
        refused(file.copy(recipeIngredients = file.recipeIngredients.map { it.copy(snapshotSugars = -2.0) }))
        refused(file.copy(profile = ProfileDto("", null, null, null, null, null, fibreTarget = 0.0)))
        assertNull(diaryProblem(file.copy(profile = ProfileDto("", null, null, null, null, null, fibreTarget = 30.0))))
    }

    @Test fun theProfileKeepsItsFibreTarget() {
        val store = FakeSettingsStore()
        ProfilePrefs(store).write(ProfileValues(fibreTarget = 30.0, trackFibre = true))
        assertEquals(30.0, ProfilePrefs(store).read().fibreTarget)
        assertTrue(ProfilePrefs(store).read().trackFibre)
        ProfilePrefs(store).write(ProfileValues())
        assertNull(ProfilePrefs(store).read().fibreTarget)
        assertFalse(ProfilePrefs(store).read().trackFibre)
    }

    @Test fun openFoodFactsReadsTheFour() {
        val parsed = OpenFoodFactsClient().parseProductForTesting(
            """{"code":"123","product_name":"Oats","product_quantity":"500","product_quantity_unit":"g","nutriments":{"energy-kcal_100g":389,"proteins_100g":16.9,
               "saturated-fat_100g":1.2,"sugars_100g":"1","fiber_100g":10.6,"salt_100g":0.02,"sodium_100g":0.008}}""",
        )!!.nutrition.values
        assertEquals(1.2, parsed.saturatedFat!!, 0.0)
        assertEquals(1.0, parsed.sugars!!, 0.0)
        assertEquals(10.6, parsed.fibre!!, 0.0)
        assertEquals(0.02, parsed.salt!!, 0.0)
    }

    /** One of the four that is missing, not a number or out of bounds is unknown, and the product still reads. */
    @Test fun openFoodFactsLeavesABadOrMissingOneUnknown() {
        val parsed = OpenFoodFactsClient().parseProductForTesting(
            """{"code":"123","product_name":"Oats","product_quantity":"500","product_quantity_unit":"g","nutriments":{"energy-kcal_100g":389,
               "sugars_100g":"lots","fiber_100g":150,"salt_100g":-1}}""",
        )!!.nutrition.values
        assertNull(parsed.saturatedFat); assertNull(parsed.sugars); assertNull(parsed.fibre); assertNull(parsed.salt)
    }

    @Test fun aContributionSendsTheFourWhenEntered() {
        val fields = OffContributionDraft("5310382009733", "Oats", null, 50.0, MeasureUnit.GRAM, values * 0.5).asFormFields()
        assertEquals("1.2", fields["nutriment_saturated-fat"])
        assertEquals("g", fields["nutriment_saturated-fat_unit"])
        assertEquals("1", fields["nutriment_sugars"])
        assertEquals("10.6", fields["nutriment_fiber"])
        assertEquals("0.02", fields["nutriment_salt"])
        assertEquals("g", fields["nutriment_salt_unit"])
        assertFalse(fields.keys.any { "sodium" in it })
        val none = OffContributionDraft("5310382009733", "Oats", null, 100.0, MeasureUnit.GRAM, NutritionValues(389.0, 16.9, null, null)).asFormFields()
        listOf("saturated-fat", "sugars", "fiber", "salt").forEach { assertFalse(it, "nutriment_$it" in none) }
    }

    @Test fun aUsdaRowCarriesTheFour() {
        val record = UsdaFoodRow(
            2705975, "0", "Chicken breast", null, 219.0, 24.6, 9.99, 6.01, emptyList(),
            saturatedFat = 1.58, sugars = 0.1, fibre = 0.2, salt = 0.795,
        ).toFoodRecord()!!.nutrition.values
        assertEquals(NutritionValues(219.0, 24.6, 6.01, 9.99, 1.58, 0.1, 0.2, 0.795), record)
        val unknown = UsdaFoodRow(1, "0", "Water", null, 0.0, null, null, null, emptyList()).toFoodRecord()!!.nutrition.values
        assertNull(unknown.fibre); assertNull(unknown.salt)
    }
}
