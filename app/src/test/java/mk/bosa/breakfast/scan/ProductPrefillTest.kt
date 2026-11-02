package mk.bosa.breakfast.scan

import mk.bosa.breakfast.contribution.contributionDraftAfterLocalSave
import mk.bosa.breakfast.domain.TestFoods
import mk.bosa.breakfast.domain.FoodMatcher
import mk.bosa.breakfast.domain.FoodOrigin
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.LogPlanner
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.NutritionBasis
import mk.bosa.breakfast.domain.NutritionValues
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What product review starts from: the prefilled amount and unit, the basis label, and the contribution candidate. */
class ProductPrefillTest {
    @Test
    fun resolverProductPrefillPreservesEnteredServingAndDirectSearchKeepsBasisDefault() {
        val pesto = FoodRecord(
            id = "off-pesto",
            name = "Branded pesto",
            aliases = emptyList(),
            category = "Open Food Facts",
            origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(500.0, 4.0, 10.0, 48.0)),
            defaultAmount = 100.0,
            defaultUnit = MeasureUnit.GRAM
        )

        // A queued part prefills from the planner's reading, as the resolve flow passes it; a glued part keeps its amount.
        val planner = LogPlanner(FoodMatcher(TestFoods.fixtures))
        val resolver = productPrefill(pesto, planner.typed("1 tbsp pesto"))
        assertEquals(1.0, resolver.amount)
        assertEquals(MeasureUnit.TABLESPOON, resolver.unit)

        val glued = productPrefill(pesto, planner.typed("млеко200г"))
        assertEquals(200.0, glued.amount)
        assertEquals(MeasureUnit.GRAM, glued.unit)

        val directSearch = productPrefill(pesto, planner.typed("pesto"))
        assertEquals(100.0, directSearch.amount)
        assertEquals(MeasureUnit.GRAM, directSearch.unit)

        assertEquals(MeasureUnit.MILLILITER, parseProductUnit(" ml "))
        assertEquals(MeasureUnit.TABLESPOON, parseProductUnit(" tbsp "))
        assertEquals(MeasureUnit.CUP, parseProductUnit(" cups "))
        assertNull(parseProductUnit(" mugs "))
    }

    @Test
    fun contributionRequiresExplicitScanProvenanceAfterLocalSave() {
        val scanned = FoodRecord(
            id = "off-scan", name = "Scanned drink", aliases = emptyList(), category = "Open Food Facts",
            origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(100.0, MeasureUnit.MILLILITER, NutritionValues(42.0, null, null, null)),
            defaultAmount = 100.0, defaultUnit = MeasureUnit.MILLILITER,
            sourceName = "Open Food Facts", barcode = "4006381333931"
        )
        assertNull(contributionDraftAfterLocalSave(scanned, fromScan = false))
        assertEquals("4006381333931", contributionDraftAfterLocalSave(scanned, fromScan = true)?.barcode)
    }
}
