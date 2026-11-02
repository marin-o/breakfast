package mk.bosa.breakfast.data

import mk.bosa.breakfast.domain.FoodMatcher
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.LogPlanner
import mk.bosa.breakfast.domain.Measure
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.ServingConversion
import mk.bosa.breakfast.domain.servingNameFor
import mk.bosa.breakfast.domain.servingNameMatches
import mk.bosa.breakfast.scan.ProductPrefill
import mk.bosa.breakfast.scan.productPrefill
import mk.bosa.breakfast.ui.screens.countedQuantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** A bare count of a USDA food counts its ordinary unit. Every row is copied from the catalogue file, v7. */
class UsdaCountedServingTest {
    private fun row(fdcId: Long, description: String, kcal: Double, vararg portions: Pair<String, Double>) = UsdaFoodRow(
        fdcId, "0", description, null, kcal, null, null, null, portions.map { (label, grams) -> UsdaPortionRow(label, grams) },
    ).toFoodRecord()!!

    private val tomato = row(2709719, "Tomatoes, raw", 20.0, "1 slice" to 20.0, "1 grape tomato" to 8.0, "1 cherry" to 17.0,
        "1 Italian tomato" to 60.0, "1 plum tomato" to 60.0, "1 whole" to 125.0, "1 cup" to 180.0)
    private val banana = row(2709224, "Banana, raw", 97.0, "1 banana" to 126.0, "1 slice" to 6.0, "1 cup" to 150.0, "1 cup, mashed" to 225.0, "1 linear inch" to 15.0)
    private val apple = row(2709215, "Apple, raw", 61.0, "1 small" to 165.0, "1 medium" to 200.0, "1 large" to 242.0, "1 extra large" to 295.0,
        "1 slice" to 25.0, "1 cup" to 125.0, "1 single serving package" to 34.0)
    private val potato = row(2709385, "Potato, boiled, NFS", 126.0, "1 baby potato" to 60.0, "1 new potato" to 60.0, "1 small" to 130.0,
        "1 medium" to 170.0, "1 large" to 300.0, "1 cup" to 160.0)
    private val onion = row(2709795, "Onions, raw", 38.0, "1 slice" to 15.0, "1 ring" to 5.0, "1 cup" to 160.0, "1 whole" to 148.0)
    private val carrot = row(2709660, "Carrots, raw", 44.0, "1 cup" to 120.0, "1 baby carrot" to 10.0, "1 regular carrot" to 60.0,
        "1 strip/stick" to 10.0, "1 piece/slice" to 5.0, "1 single serving bag" to 80.0)
    private val orange = row(2709171, "Orange, raw", 50.0, "1 fruit" to 154.0, "1 section/slice" to 15.0, "1 cup" to 180.0)
    private val cucumber = row(2709784, "Cucumber, raw", 16.0, "1 piece/slice" to 10.0, "1 stick" to 10.0, "1 regular" to 200.0, "1 miniature" to 65.0, "1 cup" to 120.0)
    private val pepper = row(2709801, "Peppers, sweet, red, raw", 31.0, "1 piece" to 10.0, "1 ring" to 10.0, "1 miniature" to 30.0, "1 regular" to 120.0, "1 cup" to 150.0)
    private val hotPepper = row(2709798, "Peppers, hot, raw", 34.0, "1 piece" to 5.0, "1 slice" to 5.0, "1 whole" to 15.0, "1 cup" to 150.0)
    private val grilledBreast = row(2705968, "Chicken breast, grilled without sauce, skin not eaten", 176.0, "1 cup, cooked, diced" to 135.0,
        "1 small breast" to 105.0, "1 medium breast" to 120.0, "1 large breast" to 135.0, "1 small or thin slice" to 30.0,
        "1 medium slice" to 60.0, "1 large or thick slice" to 85.0, "1 oz, cooked" to 28.35)
    private val stewedBreast = row(2705965, "Chicken breast, stewed, skin eaten", 181.0, "1 cup, cooked, diced" to 135.0, "1 small breast" to 130.0,
        "1 medium breast" to 150.0, "1 large breast" to 170.0, "1 breast, NS as to size" to 150.0, "1 small or thin slice" to 30.0,
        "1 medium slice" to 60.0, "1 large or thick slice" to 85.0, "1 oz, cooked" to 28.35)
    private val egg = row(2707152, "Egg, whole, raw", 143.0, "1 egg" to 50.0, "1 cup" to 245.0)
    private val bread = row(2707598, "Bread, white", 267.0, "1 small or thin/very thin slice" to 24.0, "1 medium or regular slice" to 28.0,
        "1 large or thick slice" to 43.0, "1 slice, crust not eaten" to 13.0, "1 slice, snack-size" to 10.0, "1 cup" to 40.0, "1 cubic inch" to 2.8)
    private val avocado = row(2709223, "Avocado, raw", 160.0, "1 slice" to 15.0, "1 fruit" to 150.0, "1 cup" to 150.0, "1 cup, mashed or pureed" to 230.0)
    private val bagel = row(2707684, "Bagel", 264.0, "1 miniature" to 26.0, "1 small" to 69.0, "1 regular" to 105.0, "1 large" to 131.0, "1 Bagel Thin" to 46.0)
    private val mango = row(2709242, "Mango, raw", 60.0, "1 slice/chunk" to 25.0, "1 mango" to 210.0, "1 cup" to 165.0)
    private val celery = row(2709778, "Celery, raw", 17.0, "1 piece" to 4.0, "1 stick" to 4.0, "1 stalk" to 40.0, "1 cup" to 120.0)
    private val mushroom = row(2709793, "Mushrooms, raw", 31.0, "1 piece/slice" to 6.0, "1 whole" to 18.0, "1 cup" to 70.0)
    private val corn = row(2709783, "Corn, raw", 86.0, "1 cup" to 150.0, "1 regular ear" to 105.0)
    private val gouda = row(2705717, "Cheese, Gouda or Edam", 356.0, "1 cracker-size slice" to 9.0, "1 slice" to 21.0, "1 package (7 oz)" to 198.0,
        "1 cup, diced" to 132.0, "1 cubic inch" to 17.0)
    private val cuban = row(2707604, "Bread, Cuban", 272.0, "1 small or thin/very thin slice" to 10.0, "1 medium or regular slice" to 20.0, "1 large or thick slice" to 30.0, "1 slice, crust not eaten" to 9.0, "1 loaf (13\" long)" to 1185.0)
    private val garlicBread = row(2707626, "Garlic bread, NFS", 349.0, "1 small slice" to 39.0, "1 medium slice" to 79.0, "1 large slice" to 118.0, "1 piece/slice Texas Toast" to 41.0, "1 baguette (about 22\" long)" to 393.0, "1 mini baguette (about 9\" long)" to 185.0)
    private val chocolate = row(2710328, "Chocolate candy", 535.0, "1 piece" to 8.0, "1 miniature/bite size" to 10.0, "1 fun/snack size" to 15.0, "1 pouch/regular size" to 45.0, "1 large/king size" to 90.0, "1 sharing/movie theater size" to 180.0, "1 cup" to 160.0)
    private val gummy = row(2710359, "Candy, gummy", 396.0, "1 piece" to 2.0, "1 miniature/bite size/fun size" to 15.0, "1 snack size/pouch/regular size" to 60.0, "1 large/king size" to 120.0, "1 sharing/movie theater size" to 240.0, "1 cup" to 160.0)
    private val pretzelChips = row(2708256, "Pretzel chips, hard, plain", 375.0, "1 pretzel chip/crisp/thin" to 2.7, "1 small single serving bag" to 28.0, "1 medium single serving bag" to 57.0, "1 large single serving bag" to 85.0, "1 100 calorie package" to 25.0, "1 cup" to 40.0)
    private val meatLoaf = row(2706499, "Meat loaf made with beef", 199.0, "1 small or thin slice" to 86.0, "1 medium slice" to 108.0, "1 large slice" to 144.0, "1 cocktail meatball" to 7.0, "1 small meatball" to 14.0, "1 medium meatball" to 28.0, "1 large meatball" to 42.0, "1 small patty" to 81.0, "1 medium patty" to 101.0, "1 large patty" to 134.0, "1 cup" to 140.0, "1 cubic inch" to 19.0, "1 oz" to 28.35)
    private val watermelon = row(2709270, "Watermelon, raw", 30.0, "1 cube" to 15.0, "1 ball" to 15.0, "1 cup" to 155.0, "1 small wedge/slice" to 218.0, "1 medium wedge/slice" to 325.0, "1 large wedge/slice" to 432.0, "1 miniature melon" to 1300.0, "1 small melon" to 3200.0, "1 medium melon" to 5200.0, "1 large melon" to 6000.0)
    private val cabbage = row(2709773, "Cabbage, green, raw", 31.0, "1 cup" to 90.0, "1 leaf" to 25.0, "1 head" to 900.0)
    private val lettuce = row(2709789, "Lettuce, raw", 20.0, "1 leaf" to 8.0, "1 head" to 539.0, "1 cup" to 35.0)
    private val shrimp = row(2706360, "Shrimp, NFS", 146.0, "1 tiny shrimp" to 5.0, "1 small/medium shrimp" to 10.0, "1 large/jumbo shrimp" to 15.0, "1 prawn" to 20.0, "1 cup" to 135.0)
    private val crackers = row(2708132, "Crackers, NFS", 510.0, "1 cracker" to 3.0, "1 miniature/bite size" to 1.0, "1 small or medium single serving bag" to 45.0, "1 large single serving bag" to 85.0, "1 sleeve" to 115.0, "1 cup, NFS" to 50.0, "1 cup, bite size" to 60.0)
    private val cheese = row(2705704, "Cheese, NFS", 381.0, "1 cracker-size slice" to 9.0, "1 slice" to 21.0, "1 stick" to 28.0, "1 cup" to 113.0, "1 cup, melted" to 244.0, "1 cubic inch" to 17.3)
    private val brie = row(2705708, "Cheese, Brie", 334.0, "1 package (4.5 oz)" to 128.0, "1 cup, sliced" to 144.0, "1 cup, melted" to 240.0, "1 cup, NFS" to 144.0, "1 cubic inch" to 17.0)
    private val milk = row(2705385, "Milk, whole", 61.0, "1 cup" to 244.0, "1 fl oz" to 30.5, "1 individual school container" to 244.0)
    private val cola = row(2710541, "Soft drink, cola", 42.0, "1 fl oz (no ice)" to 31.0, "1 fl oz (with ice)" to 23.0, "1 fl oz (NFS)" to 31.0, "1 can (12 fl oz)" to 372.0, "1 bottle (16.9 fl oz or 500 ml)" to 524.0, "1 bottle (20 fl oz)" to 620.0, "1 child/senior drink" to 279.0, "1 small drink" to 372.0, "1 medium drink" to 512.0, "1 large drink" to 744.0, "1 extra large drink" to 1023.0)
    private val beer = row(2710616, "Beer", 43.0, "1 fl oz" to 30.0, "1 can or bottle (12 fl oz)" to 360.0, "1 can or bottle (16 fl oz)" to 480.0, "1 bottle (40 fl oz)" to 1200.0)
    private val ham = row(2705878, "Ham", 117.0, "1 regular/thin slice" to 60.0, "1 thick slice" to 90.0, "1 cubic inch" to 17.0, "1 cup" to 135.0)
    private val juice = row(2709315, "Fruit juice, NFS", 51.0, "1 fl oz (no ice)" to 31.0, "1 fl oz (with ice)" to 23.0, "1 fl oz (NFS)" to 31.0, "1 fun size box (4.23 fl oz)" to 131.0, "1 juice box/pouch (6.75 fl oz)" to 209.0, "1 individual school container" to 124.0)
    private val applePie = row(2707995, "Pie, apple", 296.0, "1 mini/small slice" to 75.0, "1 regular slice" to 150.0, "1 large slice" to 250.0, "1 individual pie" to 150.0, "1 cup" to 250.0, "1 regular pie (8-9\" dia)" to 1200.0, "1 large pie (10\" dia plus)" to 2000.0)
    private val popcorn = row(2708216, "Popcorn, NFS", 518.0, "1 regular microwave bag" to 85.0, "1 snack/mini microwave bag" to 43.0, "1 small single serving bag" to 28.0, "1 medium single serving bag" to 57.0, "1 large single serving bag" to 85.0, "1 single serving bag, NFS" to 57.0, "1 100 calorie package" to 30.0, "1 cup, popped" to 14.0, "1 kernel" to 0.2)

    private fun planner(food: FoodRecord) = LogPlanner(FoodMatcher(listOf(food)))

    /** What Add logs for [input] on [food] once it is chosen, and that the review of a tapped row starts on the same. */
    private fun check(food: FoodRecord, input: String, serving: String?, grams: Double, kcal: Double, unit: MeasureUnit = MeasureUnit.SERVING) {
        val planner = planner(food)
        val (_, measure) = planner.quantityOf(input, food)
        assertEquals(input, serving, measure.name.takeIf { measure.unit == MeasureUnit.SERVING })
        assertEquals(input, unit, measure.unit)
        val calculation = planner.calculationFor(input, food)
        assertEquals("$input grams", grams, calculation.resolvedBaseAmount, 1e-6)
        assertEquals("$input kcal", kcal, calculation.nutrition.calories, 1e-6)
    }

    @Test fun `the bare counts of the bug read the ordinary tomato, and 2 is two of what 1 is`() {
        check(tomato, "1 tomato", "whole", 125.0, 25.0)
        check(tomato, "2 tomato", "whole", 250.0, 50.0)
        check(tomato, "2 tomatoes", "whole", 250.0, 50.0)
        check(tomato, "a tomato", "whole", 125.0, 25.0)
    }

    @Test fun `a plain count logs through the whole plan with no tap`() {
        val logged = planner(tomato).plan("2 tomato").entries.single()
        assertEquals(tomato.id, logged.food.id)
        assertEquals(250.0, logged.calculation.resolvedBaseAmount, 1e-6)
    }

    @Test fun `the review of a tapped row starts on the same serving as the Log`() {
        val planner = planner(tomato)
        listOf("1 tomato", "2 tomato").forEach { input ->
            assertEquals("whole", productPrefill(tomato, planner.typed(input)).servingName)
        }
        assertEquals(ProductPrefill(2.0, MeasureUnit.SERVING, "slice"), productPrefill(tomato, planner.typed("2 slices tomato")))
        assertEquals(ProductPrefill(1.0, MeasureUnit.SERVING, "whole"), productPrefill(tomato, planner.typed("a tomato")))
        assertEquals(ProductPrefill(100.0, MeasureUnit.GRAM), productPrefill(tomato, planner.typed("100 g tomato")))
        assertEquals(ProductPrefill(100.0, MeasureUnit.GRAM), productPrefill(milk, planner(milk).typed("a milk")))
    }

    @Test fun `a big whole item is no bare count while a smaller unit exists`() {
        check(cuban, "1 bread", "medium or regular slice", 20.0, 54.4)
        check(garlicBread, "1 garlic bread", "medium slice", 79.0, 275.71)
        check(watermelon, "1 watermelon", "medium wedge/slice", 325.0, 97.5)
        check(cabbage, "1 cabbage", "leaf", 25.0, 7.75)
        check(lettuce, "1 lettuce", "leaf", 8.0, 1.6)
        check(applePie, "1 pie", "regular slice", 150.0, 444.0)
        check(lettuce, "1 head lettuce", "head", 539.0, 107.8)
        check(cuban, "1 loaf bread", "loaf (13\" long)", 1185.0, 3223.2)
        check(watermelon, "1 whole watermelon", "medium melon", 5200.0, 1560.0)
    }

    @Test fun `a container comes after every other unit`() {
        check(chocolate, "10 chocolate candy", "piece", 80.0, 428.0)
        check(gummy, "10 gummy", "piece", 20.0, 79.2)
        check(pretzelChips, "3 pretzel chips", "pretzel chip/crisp/thin", 8.1, 30.375)
        check(crackers, "3 crackers", "cracker", 9.0, 45.9)
        check(crackers, "3 крекер", "cracker", 9.0, 45.9)
        check(popcorn, "1 popcorn", "regular microwave bag", 85.0, 440.3)
    }

    @Test fun `a serving that names another food is not the food`() {
        check(meatLoaf, "1 meat loaf", "medium slice", 108.0, 214.92)
    }

    @Test fun `any distinctive word of a serving that is typed wins`() {
        check(bread, "1 thick bread", "large or thick slice", 43.0, 114.81)
        check(bread, "1 thin bread", "small or thin/very thin slice", 24.0, 64.08)
        check(carrot, "1 stick carrot", "strip/stick", 10.0, 4.4)
        check(shrimp, "1 jumbo shrimp", "large/jumbo shrimp", 15.0, 21.9)
        check(chocolate, "1 king size chocolate", "large/king size", 90.0, 481.5)
        check(chocolate, "1 snack size chocolate", "fun/snack size", 15.0, 80.25)
        check(chocolate, "1 bite size chocolate", "miniature/bite size", 10.0, 53.5)
        check(juice, "1 juice pouch", "juice box/pouch (6.75 fl oz)", 209.0, 106.59)
        check(beer, "1 bottle beer", "can or bottle (12 fl oz)", 360.0, 154.8)
        check(mango, "1 chunk mango", "slice/chunk", 25.0, 15.0)
    }

    @Test fun `an article counts one as a number does`() {
        listOf(beer to "beer", ham to "ham", bagel to "bagel").forEach { (food, word) ->
            val one = planner(food).resolve("1 $word")!!
            listOf("a $word", "an $word").forEach { typed ->
                val read = planner(food).resolve(typed)!!
                assertEquals(typed, one.calculation.resolvedBaseAmount, read.calculation.resolvedBaseAmount, 1e-6)
                assertEquals(typed, one.label, read.label)
            }
        }
    }

    @Test fun `an article on a container or a tiny part keeps the default`() {
        listOf(milk to "a milk", cheese to "a cheese", cola to "a coke", brie to "a brie").forEach { (food, typed) ->
            assertEquals(typed, Measure(MeasureUnit.GRAM), planner(food).quantityOf(typed, food).second)
            assertEquals(typed, 100.0, planner(food).calculationFor(typed, food).resolvedBaseAmount, 1e-6)
        }
        check(cola, "a can of coke", "can (12 fl oz)", 372.0, 156.24)
        check(cheese, "1 cheese", "cracker-size slice", 9.0, 34.29)
    }

    @Test fun `an article counts only at the very start of a part`() {
        val king = row(1, "Chicken a la king", 150.0, "1 cup" to 245.0)
        assertEquals(king.name, planner(king).pickedText("chicken a la king", king))
        assertEquals(100.0, planner(king).calculationFor("chicken a la king", king).resolvedBaseAmount, 1e-6)
    }

    @Test fun `a picked food leaves the article behind`() {
        listOf("a bananna", "an bananna", "A bananna").forEach { assertEquals(banana.name, planner(banana).pickedText(it, banana)) }
        assertEquals("${banana.name} 2", planner(banana).pickedText("bananna 2", banana))
    }

    @Test fun `a food with no counted serving and a user food keep their rule`() {
        val gramsOnly = row(2, "Flour", 364.0, "1 cup" to 125.0)
        assertEquals(Measure(MeasureUnit.PIECE), planner(gramsOnly).quantityOf("2 flour", gramsOnly).second)
        assertEquals(Measure(MeasureUnit.GRAM), planner(gramsOnly).quantityOf("a flour", gramsOnly).second)
        val mine = tomato.copy(id = "custom_1", name = "Crisps", conversions = listOf(ServingConversion(MeasureUnit.GRAM, 40.0, "mine", label = "package")), defaultUnit = MeasureUnit.GRAM, defaultAmount = 100.0)
        // A user food with one serving counts it; with several nothing says which, so it keeps its default unit.
        assertEquals(Measure(MeasureUnit.SERVING, "package"), planner(mine).quantityOf("2 crisps", mine).second)
        assertEquals(Measure(MeasureUnit.SERVING, "package"), planner(mine).quantityOf("2 package crisps", mine).second)
        val several = mine.copy(conversions = mine.conversions + ServingConversion(MeasureUnit.GRAM, 25.0, "mine", label = "handful"))
        assertEquals(Measure(MeasureUnit.GRAM), planner(several).quantityOf("2 crisps", several).second)
    }

    @Test fun `plurals read right and match back`() {
        assertEquals("halves", servingNameFor("half", 2.0))
        assertEquals("leaves", servingNameFor("leaf", 2.0))
        assertEquals("loaves (13\" long)", servingNameFor("loaf (13\" long)", 2.0))
        assertEquals("feet", servingNameFor("foot", 2.0))
        assertEquals("slices with gravy", servingNameFor("slice with gravy", 2.0))
        assertEquals("bottle or box", servingNameFor("bottle or box", 2.0))
        assertEquals("piece/slice", servingNameFor("piece/slice", 2.0))
        assertEquals("large or thick slices", servingNameFor("large or thick slice", 2.0))
        assertEquals(true, servingNameMatches("halves", "half"))
        assertEquals(true, servingNameMatches("leaves", "leaf"))
        assertEquals(true, servingNameMatches("loaves", "loaf"))
        assertEquals(true, servingNameMatches("feet", "foot"))
        check(lettuce, "2 leaves lettuce", "leaf", 16.0, 3.2)
    }

    @Test fun `each counted food reads its ordinary unit`() {
        check(banana, "1 banana", "banana", 126.0, 122.22)
        check(apple, "1 apple", "medium", 200.0, 122.0)
        check(potato, "1 potato", "medium", 170.0, 214.2)
        check(onion, "1 onion", "whole", 148.0, 56.24)
        check(carrot, "1 carrot", "regular carrot", 60.0, 26.4)
        check(orange, "1 orange", "fruit", 154.0, 77.0)
        check(cucumber, "1 cucumber", "regular", 200.0, 32.0)
        check(pepper, "1 pepper", "regular", 120.0, 37.2)
        check(hotPepper, "1 pepper", "whole", 15.0, 5.1)
        check(grilledBreast, "1 chicken breast", "medium breast", 120.0, 211.2)
        check(stewedBreast, "1 chicken breast", "breast, NS as to size", 150.0, 271.5)
        check(egg, "1 egg", "egg", 50.0, 71.5)
        check(egg, "an egg", "egg", 50.0, 71.5)
        check(banana, "a banana", "banana", 126.0, 122.22)
        check(apple, "an apple", "medium", 200.0, 122.0)
        check(bread, "1 bread", "medium or regular slice", 28.0, 74.76)
        check(avocado, "1 avocado", "fruit", 150.0, 240.0)
        check(bagel, "1 bagel", "regular", 105.0, 277.2)
        check(mango, "1 mango", "mango", 210.0, 126.0)
        check(celery, "1 celery", "stalk", 40.0, 6.8)
        check(mushroom, "1 mushroom", "whole", 18.0, 5.58)
        check(corn, "1 corn", "regular ear", 105.0, 90.3)
    }

    @Test fun `a whole package is not a count of a cheese`() {
        check(gouda, "1 gouda", "cracker-size slice", 9.0, 32.04)
    }

    @Test fun `explicit words win`() {
        check(tomato, "2 slices tomato", "slice", 40.0, 8.0)
        check(tomato, "1 grape tomato", "grape tomato", 8.0, 1.6)
        check(tomato, "2 grape tomatoes", "grape tomato", 16.0, 3.2)
        check(tomato, "1 cherry tomato", "cherry", 17.0, 3.4)
        check(tomato, "1 plum tomato", "plum tomato", 60.0, 12.0)
        check(tomato, "100 g tomato", null, 100.0, 20.0, MeasureUnit.GRAM)
        check(tomato, "1 cup tomato", null, 180.0, 36.0, MeasureUnit.CUP)
        check(apple, "1 large apple", "large", 242.0, 147.62)
        check(apple, "1 extra large apple", "extra large", 295.0, 179.95)
        check(apple, "2 slices apple", "slice", 50.0, 30.5)
        check(potato, "1 baby potato", "baby potato", 60.0, 75.6)
        check(carrot, "1 baby carrot", "baby carrot", 10.0, 4.4)
        check(grilledBreast, "1 large chicken breast", "large breast", 135.0, 237.6)
        check(bread, "1 large bread", "large or thick slice", 43.0, 114.81)
    }

    @Test fun `a size the food lacks logs its ordinary unit, typed or tapped, with or without another food that has the size`() {
        listOf(listOf(tomato), listOf(tomato, apple)).forEach { foods ->
            val resolution = LogPlanner(FoodMatcher(foods)).resolve("1 large tomato")!!
            assertEquals(tomato.id, resolution.food.id)
            assertEquals("whole", resolution.label)
            assertEquals(125.0, resolution.calculation.resolvedBaseAmount, 1e-6)
        }
    }

    @Test fun `a word that mixes scripts names no serving`() {
        check(tomato, "2 slicе tomato", "whole", 250.0, 50.0)
    }

    @Test fun `the named servings log through the whole plan`() {
        val planner = planner(tomato)
        assertEquals(40.0, planner.plan("2 slices tomato").entries.single().calculation.resolvedBaseAmount, 1e-6)
        assertEquals(100.0, planner.plan("100 g tomato").entries.single().calculation.resolvedBaseAmount, 1e-6)
    }

    @Test fun `a size the food has no portion for reads as its ordinary unit, not a smaller variety`() {
        // FNDDS gives raw tomatoes no sizes. Only this food in the matcher: nothing else has a serving named "large".
        check(tomato, "1 large tomato", "whole", 125.0, 25.0)
        assertNotEquals("grape tomato", planner(tomato).quantityOf("1 large tomato", tomato).second.name)
    }

    @Test fun `a word in another script names no serving`() {
        check(tomato, "2 чери", "whole", 250.0, 50.0)
        check(tomato, "2 домат", "whole", 250.0, 50.0)
    }

    @Test fun `a default the user moved off the basis keeps its rule`() {
        val moved = tomato.copy(defaultAmount = 2.0, defaultUnit = MeasureUnit.PIECE)
        assertEquals(Measure(MeasureUnit.PIECE), planner(moved).quantityOf("1 tomato", moved).second)
    }

    @Test fun `a count reads right in the plural and back`() {
        assertEquals("2 whole", countedQuantity(2.0, MeasureUnit.SERVING, "whole"))
        assertEquals("1 whole", countedQuantity(1.0, MeasureUnit.SERVING, "whole"))
        assertEquals("2 medium", countedQuantity(2.0, MeasureUnit.SERVING, "medium"))
        assertEquals("2 regular", countedQuantity(2.0, MeasureUnit.SERVING, "regular"))
        assertEquals("grape tomatoes", servingNameFor("grape tomato", 2.0))
        assertEquals("baby potatoes", servingNameFor("baby potato", 2.0))
        assertEquals("regular carrots", servingNameFor("regular carrot", 2.0))
        assertEquals("medium breasts", servingNameFor("medium breast", 2.0))
        val planner = planner(tomato)
        assertEquals("2 whole Tomatoes, raw", planner.partText(tomato, 2.0, Measure(MeasureUnit.SERVING, "whole")))
    }
}
