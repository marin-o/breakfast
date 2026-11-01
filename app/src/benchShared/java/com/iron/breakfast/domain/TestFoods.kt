package com.iron.breakfast.domain

/** Foods for tests: the app ships none of its own. */
object TestFoods {
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
        foundationFood(
            id = "usda_2646170_chicken_breast_raw",
            sourceId = 2646170,
            name = "Chicken breast raw",
            aliases = listOf("chicken", "chicken breast", "skinless chicken", "boneless chicken", "пилешко", "пилешки гради"),
            category = "meat poultry raw",
            nutrition = NutritionValues(106.0, 22.5, 0.0, 1.93),
            preparation = "raw",
            sourceDescription = "Chicken, breast, boneless, skinless, raw"
        ),
        foundationFood(
            id = "usda_2646171_chicken_thigh_raw",
            sourceId = 2646171,
            name = "Chicken thigh raw",
            aliases = listOf("chicken thigh", "skinless chicken thigh", "boneless chicken thigh", "пилешки батак", "пилешки бут"),
            category = "meat poultry raw",
            nutrition = NutritionValues(144.0, 18.6, 0.0, 7.92),
            preparation = "raw",
            sourceDescription = "Chicken, thigh, boneless, skinless, raw"
        ),
        foundationFood(
            id = "usda_2514743_ground_beef_raw",
            sourceId = 2514743,
            name = "Ground beef 90/10 raw",
            aliases = listOf("ground beef", "beef mince", "мелено говедско", "мелено месо"),
            category = "meat beef raw",
            nutrition = NutritionValues(185.0, 18.2, 0.0, 12.8),
            preparation = "raw"
        ),
        foundationFood(
            id = "usda_2727574_sirloin_raw",
            sourceId = 2727574,
            name = "Beef top sirloin raw",
            aliases = listOf("sirloin", "beef steak", "steak", "говедски стек", "бифтек"),
            category = "meat beef raw",
            nutrition = NutritionValues(140.0, 22.0, 0.22, 5.71),
            preparation = "raw"
        ),
        foundationFood(
            id = "usda_2727573_tenderloin_raw",
            sourceId = 2727573,
            name = "Beef tenderloin raw",
            aliases = listOf("tenderloin", "filet", "говедско филе"),
            category = "meat beef raw",
            nutrition = NutritionValues(143.0, 21.1, 0.18, 6.46),
            preparation = "raw"
        ),
        foundationFood(
            id = "usda_2646168_pork_loin_raw",
            sourceId = 2646168,
            name = "Pork loin raw",
            aliases = listOf("pork", "pork loin", "свинско", "свински бут"),
            category = "meat pork raw",
            nutrition = NutritionValues(168.0, 21.1, 0.0, 9.47),
            preparation = "raw"
        ),
        foundationFood(
            id = "usda_2514745_ground_pork_raw",
            sourceId = 2514745,
            name = "Ground pork raw",
            aliases = listOf("ground pork", "мелено свинско"),
            category = "meat pork raw",
            nutrition = NutritionValues(228.0, 17.8, 0.0, 17.5),
            preparation = "raw"
        ),
        foundationFood(
            id = "usda_2684441_salmon_raw",
            sourceId = 2684441,
            name = "Atlantic salmon raw",
            aliases = listOf("salmon", "лосос"),
            category = "fish raw",
            nutrition = NutritionValues(197.0, 20.3, 0.0, 13.1),
            preparation = "raw"
        ),
        foundationFood(
            id = "usda_334194_tuna_canned",
            sourceId = 334194,
            name = "Tuna canned drained",
            aliases = listOf("tuna", "light tuna", "canned tuna", "туна"),
            category = "fish canned",
            nutrition = NutritionValues(90.0, 19.0, 0.08, 0.94),
            preparation = "canned, water-packed, drained",
            sourceDescription = "Fish, tuna, light, canned in water, drained solids"
        ),
        foundationFood(
            id = "usda_747447_broccoli_raw",
            sourceId = 747447,
            name = "Broccoli raw",
            aliases = listOf("broccoli", "брокула"),
            category = "vegetable raw",
            nutrition = NutritionValues(31.0, 2.57, 6.27, 0.34),
            preparation = "raw"
        ),
        foundationFood(
            id = "usda_2258586_carrot_raw",
            sourceId = 2258586,
            name = "Carrot raw",
            aliases = listOf("carrot", "морков"),
            category = "vegetable raw",
            nutrition = NutritionValues(48.0, 0.94, 10.3, 0.35),
            preparation = "raw"
        ),
        foundationFood(
            id = "usda_2346406_cucumber_raw",
            sourceId = 2346406,
            name = "Cucumber raw",
            aliases = listOf("cucumber", "краставица"),
            category = "vegetable raw",
            nutrition = NutritionValues(15.9, 0.625, 2.95, 0.178),
            preparation = "raw, with peel"
        ),
        foundationFood(
            id = "usda_1999634_tomato_raw",
            sourceId = 1999634,
            name = "Roma tomato raw",
            aliases = listOf("tomato", "roma tomato", "домат"),
            category = "vegetable raw",
            nutrition = NutritionValues(22.0, 0.696, 3.84, 0.425),
            preparation = "raw"
        ),
        foundationFood(
            id = "usda_321360_grape_tomato_raw",
            sourceId = 321360,
            name = "Grape tomato raw",
            aliases = listOf("grape tomato", "cherry tomato", "чери домат"),
            category = "vegetable raw",
            nutrition = NutritionValues(27.0, 0.83, 5.51, 0.63),
            preparation = "raw"
        ),
        foundationFood(
            id = "usda_2258590_red_pepper_raw",
            sourceId = 2258590,
            name = "Red bell pepper raw",
            aliases = listOf("red pepper", "red bell pepper", "црвена пиперка"),
            category = "vegetable raw",
            nutrition = NutritionValues(31.3, 0.896, 6.65, 0.126),
            preparation = "raw"
        ),
        foundationFood(
            id = "usda_2258588_green_pepper_raw",
            sourceId = 2258588,
            name = "Green bell pepper raw",
            aliases = listOf("green pepper", "green bell pepper", "зелена пиперка"),
            category = "vegetable raw",
            nutrition = NutritionValues(22.9, 0.715, 4.78, 0.106),
            preparation = "raw"
        ),
        foundationFood(
            id = "usda_2258591_orange_pepper_raw",
            sourceId = 2258591,
            name = "Orange bell pepper raw",
            aliases = listOf("orange pepper", "orange bell pepper", "портокалова пиперка"),
            category = "vegetable raw",
            nutrition = NutritionValues(31.7, 0.882, 6.7, 0.156),
            preparation = "raw"
        ),
        foundationFood(
            id = "usda_1999632_spinach_raw",
            sourceId = 1999632,
            name = "Baby spinach raw",
            aliases = listOf("spinach", "baby spinach", "спанаќ"),
            category = "vegetable raw",
            nutrition = NutritionValues(26.6, 2.85, 2.41, 0.619),
            preparation = "raw"
        ),
        foundationFood(
            id = "usda_790646_onion_raw",
            sourceId = 790646,
            name = "Yellow onion raw",
            aliases = listOf("onion", "yellow onion", "кромид"),
            category = "vegetable raw",
            nutrition = NutritionValues(38.0, 0.83, 8.61, 0.05),
            preparation = "raw"
        ),
        foundationFood(
            id = "usda_2346407_cabbage_raw",
            sourceId = 2346407,
            name = "Green cabbage raw",
            aliases = listOf("cabbage", "green cabbage", "зелка"),
            category = "vegetable raw",
            nutrition = NutritionValues(31.4, 0.961, 6.38, 0.228),
            preparation = "raw"
        ),
        foundationFood(
            id = "usda_2346408_red_cabbage_raw",
            sourceId = 2346408,
            name = "Red cabbage raw",
            aliases = listOf("red cabbage", "црвена зелка"),
            category = "vegetable raw",
            nutrition = NutritionValues(34.1, 1.24, 6.79, 0.214),
            preparation = "raw"
        ),
        foundationFood(
            id = "usda_2685568_zucchini_raw",
            sourceId = 2685568,
            name = "Zucchini raw",
            aliases = listOf("zucchini", "тиквичка"),
            category = "vegetable raw",
            nutrition = NutritionValues(19.0, 0.984, 3.27, 0.205),
            preparation = "raw, with skin"
        ),
        foundationFood(
            id = "usda_2346389_romaine_raw",
            sourceId = 2346389,
            name = "Romaine lettuce raw",
            aliases = listOf("lettuce", "romaine", "марула"),
            category = "vegetable raw",
            nutrition = NutritionValues(20.8, 0.977, 4.06, 0.071),
            preparation = "raw"
        ),
        foundationFood(
            id = "usda_2346391_leaf_lettuce_raw",
            sourceId = 2346391,
            name = "Green leaf lettuce raw",
            aliases = listOf("leaf lettuce", "green lettuce", "зелена салата"),
            category = "vegetable raw",
            nutrition = NutritionValues(22.0, 1.09, 4.07, 0.156),
            preparation = "raw"
        ),
        foundationFood(
            id = "usda_2685577_eggplant_raw",
            sourceId = 2685577,
            name = "Eggplant raw",
            aliases = listOf("eggplant", "aubergine", "модар патлиџан"),
            category = "vegetable raw",
            nutrition = NutritionValues(26.1, 0.852, 5.4, 0.12),
            preparation = "raw"
        ),
        foundationFood(
            id = "usda_747693_ketchup",
            sourceId = 747693,
            name = "Ketchup",
            aliases = listOf("ketchup", "кетчап"),
            category = "sauce",
            nutrition = NutritionValues(117.0, 1.11, 26.8, 0.55),
            preparation = "restaurant formulation",
            sourceDescription = "Ketchup, restaurant"
        ),
        foundationFood(
            id = "usda_326698_mustard",
            sourceId = 326698,
            name = "Yellow mustard",
            aliases = listOf("mustard", "yellow mustard", "сенф"),
            category = "sauce",
            nutrition = NutritionValues(61.0, 4.25, 5.3, 3.38),
            preparation = "prepared yellow",
            conversions = listOf(
                ServingConversion(MeasureUnit.TEASPOON, 6.0, "USDA Foundation 326698 portion: 1 tsp = 6 g")
            )
        ),
        foundationFood(
            id = "usda_2685579_tomato_sauce",
            sourceId = 2685579,
            name = "Tomato sauce canned",
            aliases = listOf("tomato sauce", "доматен сос", "томатен сос"),
            category = "sauce",
            nutrition = NutritionValues(34.2, 1.35, 6.33, 0.382),
            preparation = "canned, with salt added"
        ),
        foundationFood(
            id = "usda_171009_mayonnaise",
            sourceId = 171009,
            name = "Mayonnaise regular",
            aliases = listOf("mayonnaise", "mayo", "мајонез"),
            category = "sauce",
            nutrition = NutritionValues(680.0, 0.96, 0.57, 74.8),
            preparation = "regular",
            sourceDescription = "Salad dressing, mayonnaise, regular",
            conversions = listOf(
                ServingConversion(MeasureUnit.TABLESPOON, 13.8, "USDA SR Legacy 171009 portion: 1 tbsp = 13.8 g")
            ),
            sourceName = "USDA FoodData Central SR Legacy",
            sourceRelease = "2018-04"
        ),
        foundationFood(
            id = "usda_174277_soy_sauce",
            sourceId = 174277,
            name = "Soy sauce shoyu",
            aliases = listOf("soy sauce", "shoyu", "соја сос", "соја"),
            category = "sauce",
            nutrition = NutritionValues(53.0, 8.14, 4.93, 0.57),
            preparation = "shoyu",
            sourceDescription = "Soy sauce made from soy and wheat (shoyu)",
            conversions = listOf(
                ServingConversion(MeasureUnit.TABLESPOON, 16.0, "USDA SR Legacy 174277 portion: 1 tbsp = 16 g"),
                ServingConversion(MeasureUnit.TEASPOON, 5.3, "USDA SR Legacy 174277 portion: 1 tsp = 5.3 g")
            ),
            sourceName = "USDA FoodData Central SR Legacy",
            sourceRelease = "2018-04"
        ),
        foundationFood(
            id = "usda_174523_barbecue_sauce",
            sourceId = 174523,
            name = "Barbecue sauce",
            aliases = listOf("barbecue sauce", "bbq sauce", "сос за скара"),
            category = "sauce",
            nutrition = NutritionValues(172.0, 0.82, 40.8, 0.63),
            preparation = "regular",
            sourceDescription = "Sauce, barbecue",
            conversions = listOf(
                ServingConversion(MeasureUnit.TABLESPOON, 17.0, "USDA SR Legacy 174523 portion: 1 tbsp = 17 g")
            ),
            sourceName = "USDA FoodData Central SR Legacy",
            sourceRelease = "2018-04"
        ),
        FoodRecord(
            id = "custom_home_milk",
            name = "Домашно млеко",
            aliases = listOf("домашно млеко", "млеко"),
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

    private fun foundationFood(
        id: String,
        sourceId: Int,
        name: String,
        aliases: List<String>,
        category: String,
        nutrition: NutritionValues,
        preparation: String,
        sourceDescription: String? = null,
        conversions: List<ServingConversion> = emptyList(),
        sourceName: String = "USDA FoodData Central Foundation Foods",
        sourceRelease: String = "2026-04-30"
    ): FoodRecord = FoodRecord(
        id = id,
        name = name,
        aliases = aliases,
        category = category,
        origin = FoodOrigin.GENERIC,
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, nutrition),
        conversions = conversions,
        defaultAmount = 100.0,
        defaultUnit = MeasureUnit.GRAM,
        sourceId = sourceId.toString(),
        sourceName = sourceName,
        sourceRelease = sourceRelease,
        preparation = preparation,
        sourceDescription = sourceDescription
    )
}
