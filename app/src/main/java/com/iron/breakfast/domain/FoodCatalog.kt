package com.iron.breakfast.domain

object FoodCatalog {
    val fixtures: List<FoodRecord> = listOf(
        FoodRecord(
            id = "milk_28",
            name = "Milk 2.8%",
            aliases = listOf("milk", "cow milk", "млеко 2.8"),
            category = "milk dairy",
            origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(100.0, MeasureUnit.MILLILITER, NutritionValues(54.0, 3.2, 4.8, 2.8)),
            conversions = listOf(
                ServingConversion(MeasureUnit.TABLESPOON, 15.0, "15 ml household tablespoon"),
                ServingConversion(MeasureUnit.TEASPOON, 5.0, "5 ml household teaspoon")
            ),
            defaultAmount = 100.0,
            defaultUnit = MeasureUnit.MILLILITER
        ),
        FoodRecord(
            id = "milk_32",
            name = "Milk 3.2%",
            aliases = listOf("full fat milk", "cow milk 3.2"),
            category = "milk dairy",
            origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(100.0, MeasureUnit.MILLILITER, NutritionValues(61.0, 3.2, 4.7, 3.2)),
            conversions = listOf(
                ServingConversion(MeasureUnit.TABLESPOON, 15.0, "15 ml household tablespoon"),
                ServingConversion(MeasureUnit.TEASPOON, 5.0, "5 ml household teaspoon")
            ),
            defaultAmount = 100.0,
            defaultUnit = MeasureUnit.MILLILITER
        ),
        FoodRecord(
            id = "oat_milk",
            name = "Oat milk",
            aliases = listOf("oat drink", "овесно млеко"),
            category = "milk plant",
            origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(100.0, MeasureUnit.MILLILITER, NutritionValues(46.0, 1.0, 6.7, 1.5)),
            conversions = listOf(
                ServingConversion(MeasureUnit.TABLESPOON, 15.0, "15 ml household tablespoon"),
                ServingConversion(MeasureUnit.TEASPOON, 5.0, "5 ml household teaspoon")
            ),
            defaultAmount = 100.0,
            defaultUnit = MeasureUnit.MILLILITER
        ),
        FoodRecord(
            id = "peanut_butter_smooth",
            name = "Peanut butter smooth",
            aliases = listOf("peanut butter", "smooth pb", "путер од кикирики"),
            category = "nut spread",
            origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(588.0, 25.1, 20.0, 50.0)),
            conversions = listOf(
                ServingConversion(MeasureUnit.TABLESPOON, 16.0, "standard tablespoon", false),
                ServingConversion(MeasureUnit.TEASPOON, 5.3, "standard teaspoon", false)
            ),
            defaultAmount = 1.0,
            defaultUnit = MeasureUnit.TABLESPOON
        ),
        FoodRecord(
            id = "peanut_butter_crunchy",
            name = "Peanut butter crunchy",
            aliases = listOf("crunchy peanut butter", "crunchy pb"),
            category = "nut spread",
            origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(590.0, 24.0, 21.0, 50.5)),
            conversions = listOf(
                ServingConversion(MeasureUnit.TABLESPOON, 16.0, "standard tablespoon", false),
                ServingConversion(MeasureUnit.TEASPOON, 5.3, "standard teaspoon", false)
            ),
            defaultAmount = 1.0,
            defaultUnit = MeasureUnit.TABLESPOON
        ),
        FoodRecord(
            id = "rolled_oats",
            name = "Rolled oats",
            aliases = listOf("oats", "oatmeal", "овесни снегулки"),
            category = "grain",
            origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(389.0, 16.9, 66.3, 6.9)),
            conversions = listOf(
                ServingConversion(MeasureUnit.TABLESPOON, 6.5, "generic tablespoon estimate", true),
                ServingConversion(MeasureUnit.TEASPOON, 2.2, "generic teaspoon estimate", true)
            ),
            defaultAmount = 40.0,
            defaultUnit = MeasureUnit.GRAM
        ),
        FoodRecord(
            id = "banana",
            name = "Banana",
            aliases = listOf("банана", "банан"),
            category = "fruit",
            origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(89.0, 1.1, 22.8, 0.3)),
            conversions = listOf(ServingConversion(MeasureUnit.PIECE, 118.0, "medium banana reference", true)),
            defaultAmount = 1.0,
            defaultUnit = MeasureUnit.PIECE
        ),
        FoodRecord(
            id = "egg",
            name = "Egg",
            aliases = listOf("eggs", "јајце", "јајца"),
            category = "egg",
            origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(143.0, 12.6, 0.7, 9.5)),
            conversions = listOf(ServingConversion(MeasureUnit.PIECE, 50.0, "large egg reference", true)),
            defaultAmount = 1.0,
            defaultUnit = MeasureUnit.PIECE
        ),
        FoodRecord(
            id = "custom_home_milk",
            name = "Домашно млеко",
            aliases = listOf("домашно млеко", "domashno mleko", "млеко"),
            category = "milk custom",
            origin = FoodOrigin.CUSTOM,
            nutrition = NutritionBasis(100.0, MeasureUnit.MILLILITER, NutritionValues(64.0, 3.3, 4.8, 3.7)),
            conversions = listOf(
                ServingConversion(MeasureUnit.TABLESPOON, 15.0, "15 ml household tablespoon"),
                ServingConversion(MeasureUnit.TEASPOON, 5.0, "5 ml household teaspoon")
            ),
            defaultAmount = 100.0,
            defaultUnit = MeasureUnit.MILLILITER
        ),
        FoodRecord(
            id = "custom_grandmas_ajvar",
            name = "Grandma's ajvar",
            aliases = listOf("ајвар", "домашен ајвар", "home ajvar"),
            category = "spread custom",
            origin = FoodOrigin.CUSTOM,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(82.0, 1.2, 8.0, 4.8)),
            conversions = listOf(
                ServingConversion(MeasureUnit.TABLESPOON, 18.0, "generic spread tablespoon estimate", true),
                ServingConversion(MeasureUnit.TEASPOON, 6.0, "generic spread teaspoon estimate", true)
            ),
            defaultAmount = 1.0,
            defaultUnit = MeasureUnit.TABLESPOON
        )
    )
}
