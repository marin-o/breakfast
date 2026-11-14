#!/usr/bin/env python3
"""Builds the app's USDA food catalogue (docs/usda-catalogue-spec.md) from the official FNDDS 2021-2023 archive and the
home-cooking groups of the official SR Legacy 2018-04 archive (flours, salt, leavening, oils, plain raw foods).

Stdlib only:
    python -I tools/build_usda_catalogue.py                                           downloads both archives to a temporary directory
    python -I tools/build_usda_catalogue.py --archive survey.zip --sr-archive sr.zip  uses copies already downloaded

Each archive is checked against its SHA-256 before it is opened and is never kept in the repository. The same archives
and the same SQLite version give the same bytes. Any failed check ends with a non-zero exit and leaves the asset as it was.
"""
import argparse
import contextlib
import csv
import gzip
import hashlib
import json
import os
import re
import shutil
import sys
import sqlite3
import tempfile
import urllib.request
import zipfile
from collections import Counter
from pathlib import Path

ARCHIVE_URL = "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_survey_food_json_2024-10-31.zip"
ARCHIVE_SHA256 = "dfb06ae7ddc397ccd570b91c14b75438ab2ba39f64f22d321f61d4a52a77f3eb"
ARCHIVE_MEMBER = "surveyDownload.json"
SR_ARCHIVE_URL = "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_json_2018-04.zip"
SR_ARCHIVE_SHA256 = "0fe8ae486a2c8eb42cb96413f058deb51863a46c8fb8eeb4b1fb45006dd338ef"
SR_ARCHIVE_MEMBER = "FoodData_Central_sr_legacy_food_json_2018-04.json"
EXPECTED_SR_FOODS_IN = 7793
# What each row says it came from: the release is shown to the user, its words before the year ("FNDDS", "SR Legacy")
# head the source description.
FNDDS_RELEASE = "FNDDS 2021-2023"
SR_RELEASE = "SR Legacy 2018-04"

# Raise it whenever the file's content changes, with USDA_CATALOGUE_VERSION in UsdaCatalogueReader.kt.
CATALOGUE_VERSION = 9
# Not ".gz": the Android Gradle plugin unpacks a .gz asset when it merges assets and drops the extension.
DEFAULT_OUTPUT = (
    Path(__file__).resolve().parent.parent / "app/src/main/assets/usda" / f"usda-v{CATALOGUE_VERSION}.sqlite.gzip"
)
# The Macedonian table as the owner reads it.
WORDS_CSV = Path(__file__).resolve().parent.parent / "docs/usda-macedonian-words.csv"

# What the archive holds, by the spec. A different count means a different archive or a broken reader.
EXPECTED_FOODS_IN = 5432
EXPECTED_PORTIONS_IN = 22194
EXPECTED_ADDITIONAL_IN = 9647

ADDITIONAL_DESCRIPTION = 1001
KCAL, PROTEIN, FAT, CARBS = 1008, 1003, 1004, 1005
# Saturated fat, total sugars and fibre in g; sodium in mg, kept only as salt (mg x 2.5 / 1000, in g): the catalogue's own
# conversion, as labels list salt. There is no sodium column.
SATURATED_FAT, SUGARS, FIBRE, SODIUM = 1258, 2000, 1079, 1093
UNSPECIFIED_MODIFIER = "90000"
DROPPED_LABEL_WORDS = ("guideline amount", "yield", "surface")

# The reviewed aliases: a typed word also tries its target, and the typed word stays in the query. They ship in the
# file, so the app has one list.
ALIASES = (("breaded", "coated"),)

# Common foods, by their exact description: the ranking bump orders results and can never make a food green.
COMMON_BUMP = 1
# Above the common foods: plain "flour" is all-purpose wheat flour, not the shorter name of the corn flour that is also common.
STAPLE_BUMP = 2
STAPLE_FOODS = ("Wheat flour, white, all-purpose, enriched, bleached",)
COMMON_FOODS = (
    "Chicken breast, grilled without sauce, skin not eaten", "Rice, cooked, NFS", "Bread, white", "Potato, boiled, NFS",
    "Beans, NFS", "Lentils, NFS", "Water, tap", "Shrimp, NFS", "Sausage, NFS", "Corn, raw", "Peppers, raw, NFS",
    "Egg, whole, raw", "Milk, NFS", "Yogurt, NFS", "Cheese, NFS", "Butter, NFS", "Olive oil", "Sunflower oil",
    "Banana, raw", "Apple, raw", "Orange, raw", "Lemon, raw", "Pear, raw", "Peach, raw", "Plum, raw", "Cherries, raw",
    "Strawberries, raw", "Grapes, raw", "Watermelon, raw", "Pineapple, raw", "Avocado, raw", "Tomatoes, raw",
    "Cucumber, raw", "Carrots, raw", "Onions, raw", "Garlic, raw", "Lettuce, raw", "Spinach, raw", "Broccoli, raw",
    "Cauliflower, raw", "Mushrooms, raw", "Eggplant, raw", "Oats, raw", "Pasta, cooked", "Noodles, cooked", "Honey",
    "Sugar, NFS", "Coffee, brewed", "Beef, NFS", "Pork, NFS", "Turkey, NFS", "Fish, NFS", "Ham", "Beer", "Ketchup",
    "Mustard", "Jam", "Mayonnaise, regular", "Hazelnuts", "Almonds, NFS", "Peanuts, NFS",
    # SR Legacy: the plain home staples, so "flour", "salt" or "baking powder" finds them first.
    "Corn flour, whole-grain, yellow", "Cornmeal, whole-grain, yellow",
    "Semolina, unenriched", "Cornstarch", "Salt, table", "Spices, pepper, black",
    "Leavening agents, baking powder, double-acting, sodium aluminum sulfate", "Leavening agents, baking soda",
    "Leavening agents, yeast, baker's, active dry", "Sugars, granulated", "Lentils, raw", "Beans, kidney, all types, mature seeds, raw",
    "Chickpeas (garbanzo beans, bengal gram), mature seeds, raw", "Rice, white, long-grain, regular, raw, enriched",
)

# SR Legacy foods kept, by food category: only raw and home ingredients. Restaurant and fast food, baby food, meals,
# snacks, beverages and branded foods are left out, and so are the SR foods FNDDS already has.
SR_FOOD_DISTRIBUTION_NOTE = re.compile(r"\s*\(Includes foods for USDA's Food Distribution Program\)")
SR_RAW = re.compile(r"\braw\b", re.I)
SR_PLAIN_COOKED = re.compile(r"\b(boiled|baked)\b.*\bwithout salt\b", re.I)
# Processed variants of a home food: frozen, canned, imitation or formulated foods, mixtures, meat substitutes and the like.
SR_PROCESSED = re.compile(
    r"\b(frozen|canned|imitation|formulated|mixture|meatless|dehydrated|ready-to-\w+|pasteurized process|replacement|pressurized|stabilized|precooked or instant)\b",
    re.I,
)
# The plain canned tomatoes and tomato products that cook at home; the seasoned sauces stay out.
SR_PLAIN_TOMATO = re.compile(
    r"^(Tomatoes, red, ripe, canned, packed in tomato juice(, no salt added)?|Tomato products, canned, (sauce|paste, without salt added|puree, (with|without) salt added))$"
)
SR_BRAND_WORDS = re.compile(r"\b[A-Z][A-Z'&-]{2,}\b")
SR_UPPERCASE_OK = {"USDA", "NFS", "NS"}
# "select" grade beef and imported cuts only repeat the choice and all-grades foods; "industrial" foods are not for homes.
SR_NEVER = re.compile(
    r"restaurant|fast food|babyfood|baby food|imported|\bselect\b|industrial|Vitasoy|Mckee|Little Debbie|Pillsbury|Nasoya|Tinkyada|Ancient Harvest|De Boles|chili with|veggie burgers|meatloaf|meat extender",
    re.I,
)
# The same food as an FNDDS one under other words; the words check in sr_duplicates finds the rest.
SR_SAME_AS_FNDDS = ("Oil, olive, salad or cooking",)
SR_MEAT_GROUPS = ("Beef Products", "Lamb, Veal, and Game Products", "Poultry Products", "Pork Products", "Finfish and Shellfish Products")
SR_GROUPS = {
    "Cereal Grains and Pasta": lambda d: True,
    "Spices and Herbs": lambda d: True,
    "Baked Products": lambda d: d.startswith("Leavening agents"),
    # Only the staples, not the packaged breakfast cereals.
    "Breakfast Cereals": lambda d: d.startswith(("Cereals, oats, regular and quick", "Cereals, corn grits", "Cereals, farina")),
    "Fats and Oils": lambda d: re.match(r"(Oil|Butter|Margarine,|Lard|Shortening|Fat,|Vegetable oil)", d) is not None,
    "Sweets": lambda d: re.match(r"(Sugars?\b|Honey$|Molasses$|Syrups?, (maple|corn|malt|sorghum|cane)|Sweetener, syrup, agave)", d, re.I) is not None,
    "Dairy and Egg Products": lambda d: re.match(r"(Cheese, |Milk, |Cream, |Yogurt, |Egg|Butter|Sour cream|Cream cheese)", d) is not None
    and not re.search("chocolate|human|filled", d),
    "Legumes and Legume Products": lambda d: True,
    "Nut and Seed Products": lambda d: True,
    "Vegetables and Vegetable Products": lambda d: bool(SR_RAW.search(d) or SR_PLAIN_COOKED.search(d) or SR_PLAIN_TOMATO.match(d)),
    "Fruits and Fruit Juices": lambda d: bool(SR_RAW.search(d)),
    **{group: (lambda d: bool(SR_RAW.search(d))) for group in SR_MEAT_GROUPS},
}

# Local dishes USDA prints under other names, by the food's exact description: extra search words, indexed like the
# additional descriptions and never shown.
EXTRA_SEARCH_WORDS = {
    "Stuffed cabbage rolls with beef and rice": "sarma",
    "Stuffed cabbage, with meat, Puerto Rican style": "sarma",
    "Stuffed cabbage, with meat and rice, Syrian dish, Puerto Rican style": "sarma",
    "Stuffed grape leaves with beef and rice": "sarma dolma",
    "Stuffed grape leaves with lamb and rice": "sarma dolma",
    "Grape leaves stuffed with rice": "sarma dolma",
}

# Macedonian words, singular and plural, and the English words each searches this database by (the owner checks them;
# docs/usda-macedonian-words.csv is written from here). A word listed twice finds either English word, and two words
# ("црн дроб") are read together before each is read alone. Every target must be a word of some food. The English plural
# where the file prints it so: a plural also tries its singular.
MACEDONIAN_WORDS = (
    ("јајце", "egg"), ("јајца", "eggs"), ("млеко", "milk"), ("леб", "bread"), ("ориз", "rice"), ("јогурт", "yogurt"),
    ("сирење", "cheese"), ("кашкавал", "cheese"), ("урда", "ricotta"), ("путер", "butter"), ("павлака", "cream"),
    ("месо", "meat"), ("пилешко", "chicken"), ("пилешки", "chicken"), ("гради", "breast"), ("говедско", "beef"),
    ("свинско", "pork"), ("јагнешко", "lamb"), ("телешко", "veal"), ("мисирка", "turkey"), ("мисиркино", "turkey"),
    ("риба", "fish"), ("риби", "fish"), ("туна", "tuna"), ("лосос", "salmon"), ("пастрмка", "trout"),
    ("сардини", "sardines"), ("ракчиња", "shrimp"), ("шунка", "ham"), ("сланина", "bacon"), ("колбас", "sausage"),
    ("колбаси", "sausage"), ("дроб", "liver"), ("црн дроб", "liver"), ("стек", "steak"), ("хамбургер", "hamburger"), ("ќофте", "meatball"),
    ("ќофтиња", "meatballs"),
    ("банана", "banana"), ("банани", "bananas"), ("јаболко", "apple"), ("јаболка", "apple"), ("портокал", "orange"),
    ("портокали", "orange"), ("лимон", "lemon"), ("лимони", "lemon"), ("круша", "pear"), ("круши", "pear"),
    ("праска", "peach"), ("праски", "peach"), ("слива", "plum"), ("сливи", "plum"), ("цреша", "cherries"),
    ("цреши", "cherries"), ("јагода", "strawberries"), ("јагоди", "strawberries"), ("грозје", "grapes"),
    ("лубеница", "watermelon"), ("лубеници", "watermelon"), ("кајсија", "apricot"), ("кајсии", "apricot"),
    ("смоква", "fig"), ("смокви", "fig"), ("ананас", "pineapple"), ("киви", "kiwi"), ("авокадо", "avocado"),
    ("домат", "tomatoes"), ("домати", "tomatoes"), ("компир", "potatoes"), ("компири", "potatoes"), ("кромид", "onions"),
    ("лук", "garlic"), ("пиперка", "peppers"), ("пиперки", "peppers"), ("краставица", "cucumber"),
    ("краставици", "cucumber"), ("морков", "carrots"), ("моркови", "carrots"), ("зелка", "cabbage"), ("марула", "lettuce"),
    ("спанаќ", "spinach"), ("печурка", "mushrooms"), ("печурки", "mushrooms"), ("тиквичка", "zucchini"),
    ("тиквички", "zucchini"), ("тиква", "pumpkin"), ("патлиџан", "eggplant"), ("патлиџани", "eggplant"),
    ("брокула", "broccoli"), ("карфиол", "cauliflower"), ("цвекло", "beets"), ("ротквица", "radish"),
    ("магдонос", "parsley"), ("целер", "celery"), ("пченка", "corn"), ("грав", "beans"), ("грашок", "peas"),
    ("леќа", "lentils"),
    ("овес", "oats"), ("овесна", "oatmeal"), ("брашно", "flour"), ("тестенина", "pasta"), ("тестенини", "pasta"),
    ("шпагети", "spaghetti"), ("пица", "pizza"), ("кифла", "roll"), ("кифли", "roll"), ("пита", "pie"), ("пита", "pita"),
    ("палачинка", "crepe"), ("палачинки", "crepe"), ("сендвич", "sandwich"), ("салата", "salad"), ("супа", "soup"),
    ("помфрит", "fries"), ("чипс", "chips"), ("пуканки", "popcorn"),
    ("шеќер", "sugar"), ("мед", "honey"), ("масло", "oil"), ("маслиново", "olive"), ("сончогледово", "sunflower"),
    ("џем", "jam"), ("мајонез", "mayonnaise"), ("кечап", "ketchup"), ("сенф", "mustard"), ("чоколадо", "chocolate"),
    ("торта", "cake"), ("колаче", "cookie"), ("колачи", "cookie"), ("орев", "walnuts"), ("ореви", "walnuts"),
    ("бадем", "almonds"), ("бадеми", "almonds"), ("лешник", "hazelnuts"), ("лешници", "hazelnuts"),
    ("кикирики", "peanuts"),
    ("кафе", "coffee"), ("чај", "tea"), ("сок", "juice"), ("вода", "water"), ("вино", "wine"), ("пиво", "beer"),
    ("сарма", "sarma"), ("долма", "dolma"),
    ("пченкарно брашно", "corn flour"), ("сол", "salt"), ("прашок за пециво", "baking powder"),
    ("сода бикарбона", "baking soda"), ("гриз", "semolina"),
    ("поховано", "breaded"), ("поховани", "breaded"), ("пржено", "fried"), ("пржени", "fried"), ("варено", "boiled"),
    ("варени", "boiled"), ("печено", "baked"), ("печено", "roasted"), ("печени", "baked"), ("печени", "roasted"),
)

SCHEMA = """
CREATE TABLE food (
    fdc_id INTEGER PRIMARY KEY,
    release TEXT NOT NULL,
    food_code TEXT NOT NULL,
    description TEXT NOT NULL,
    additional TEXT,
    kcal REAL NOT NULL,
    protein REAL,
    fat REAL,
    carbs REAL,
    saturated_fat REAL,
    sugars REAL,
    fibre REAL,
    salt REAL,
    search_name TEXT NOT NULL,
    search_extra TEXT NOT NULL,
    bump INTEGER NOT NULL
);
CREATE TABLE portion (
    fdc_id INTEGER NOT NULL,
    position INTEGER NOT NULL,
    label TEXT NOT NULL,
    grams REAL NOT NULL,
    PRIMARY KEY (fdc_id, position)
) WITHOUT ROWID;
CREATE TABLE alias (
    word TEXT NOT NULL,
    target TEXT NOT NULL,
    PRIMARY KEY (word, target)
) WITHOUT ROWID;
CREATE TABLE translation (
    word TEXT NOT NULL,
    english TEXT NOT NULL,
    PRIMARY KEY (word, english)
) WITHOUT ROWID;
CREATE VIRTUAL TABLE food_fts USING fts4(content="food", search_name, search_extra, tokenize=unicode61);
"""


def fail(message):
    raise SystemExit(f"FAILED: {message}")


def sha256_of(path):
    digest = hashlib.sha256()
    with open(path, "rb") as stream:
        for block in iter(lambda: stream.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def load_foods(archive, sha256, member, key):
    actual = sha256_of(archive)
    if actual != sha256:
        fail(f"{archive} has SHA-256 {actual}, expected {sha256}")
    with zipfile.ZipFile(archive) as zipped:
        return json.loads(zipped.read(member))[key]


def sr_clean_description(food):
    return SR_FOOD_DISTRIBUTION_NOTE.sub("", food["description"])


def words_of(description):
    """A description as a set of singular words, parentheses and order ignored: "Pears, raw" is "Pear, raw"."""
    words = re.sub(r"\(.*?\)", "", description).lower().replace(",", " ").split()
    return frozenset(w[:-3] + "y" if w.endswith("ies") else w[:-1] if w.endswith("s") and not w.endswith("ss") else w for w in words if w != "fresh")


def sr_duplicates(sr_foods, fndds_foods):
    """The SR foods that are a food FNDDS has: the same words, or listed in SR_SAME_AS_FNDDS. FNDDS is kept: it is the newer release."""
    known = {words_of(food["description"]) for food in fndds_foods}
    return [food for food in sr_foods if words_of(sr_clean_description(food)) in known or food["description"] in SR_SAME_AS_FNDDS]


def select_sr_foods(sr_foods, fndds_foods, report):
    """The SR Legacy foods the catalogue keeps, and per group how many were kept and how many were left out as FNDDS's own."""
    chosen = []
    for food in sr_foods:
        group = food["foodCategory"]["description"]
        description = sr_clean_description(food)
        if group not in SR_GROUPS or not SR_GROUPS[group](description) or SR_NEVER.search(description):
            continue
        if SR_PROCESSED.search(description) and not SR_PLAIN_TOMATO.match(description):
            report["SR processed dropped"] += 1
            continue
        if any(word not in SR_UPPERCASE_OK for word in SR_BRAND_WORDS.findall(description)):
            report["SR branded dropped"] += 1
            continue
        chosen.append(food)
    duplicates = {food["fdcId"] for food in sr_duplicates(chosen, fndds_foods)}
    for food in chosen:
        key = "dropped as FNDDS's" if food["fdcId"] in duplicates else "kept"
        report[f"SR {food['foodCategory']['description']}: {key}"] += 1
    return [food for food in chosen if food["fdcId"] not in duplicates]


def portion_label(portion):
    """The portion as the user reads it: FNDDS prints "1 cup, cooked, diced"; SR gives an amount and a unit word ("1 tsp")."""
    printed = (portion.get("portionDescription") or "").strip()
    if printed:
        return printed
    modifier = (portion.get("modifier") or "").strip()
    if not modifier:
        return ""
    for short, whole in SR_UNIT_WORDS:
        modifier = re.sub(rf"^{short}\b", whole, modifier)
    return f"{portion.get('amount') or 1:g} {modifier}"


# SR's short unit words as FNDDS prints them, so a cup, tablespoon and teaspoon of an SR food are read as such.
SR_UNIT_WORDS = (("tbsp", "tablespoon"), ("tsp", "teaspoon"))


def portion_drop_reason(portion):
    """Why a portion is not kept, or None when it has a clear label and grams above 0."""
    label = portion_label(portion)
    lower = label.lower()
    if portion.get("modifier") == UNSPECIFIED_MODIFIER or "quantity not specified" in lower:
        return "quantity not specified"
    for word in DROPPED_LABEL_WORDS:
        if word in lower:
            return word
    if not label:
        return "no label"
    grams = portion.get("gramWeight")
    if grams is None or grams <= 0:
        return "no grams"
    return None


def additional_descriptions(food):
    """The food's additional descriptions in rank order, each once whatever its case."""
    rows = [a for a in food["foodAttributes"] if a["foodAttributeType"]["id"] == ADDITIONAL_DESCRIPTION]
    rows.sort(key=lambda a: (a.get("rank", 0), a["id"]))
    seen = {}
    for row in rows:
        value = (row.get("value") or "").strip()
        if value:
            seen.setdefault(value.lower(), value)
    return list(seen.values())


def search_text(text):
    """The text as the index reads it: an apostrophe joins its word ("farmer's" is "farmers"), as `matchWords` does."""
    return text.replace("'", "").replace("’", "")


def salt_of(sodium_mg):
    """Salt in g from sodium in mg, as labels print it; None stays unknown. Rounded so the bytes do not hang on float noise."""
    return None if sodium_mg is None else round(sodium_mg * 2.5 / 1000, 4)


def build_database(foods, sr_foods, path):
    """FNDDS [foods] first, then the chosen SR Legacy [sr_foods]; their FDC IDs never meet (FNDDS 2.0 million and up, SR below 0.2 million)."""
    report = Counter()
    dropped_foods = []
    db = sqlite3.connect(path)
    db.executescript("PRAGMA page_size=4096; PRAGMA journal_mode=DELETE; PRAGMA encoding='UTF-8';")
    db.execute(f"PRAGMA user_version={CATALOGUE_VERSION}")
    db.executescript(SCHEMA)
    with db:
        db.executemany("INSERT INTO alias VALUES (?, ?)", ALIASES)
        db.executemany("INSERT INTO translation VALUES (?, ?)", MACEDONIAN_WORDS)
        for release, group, code_of in ((FNDDS_RELEASE, foods, lambda f: f["foodCode"]), (SR_RELEASE, sr_foods, lambda f: str(f["ndbNumber"]))):
            prefix = "" if release == FNDDS_RELEASE else "SR "
            for food in sorted(group, key=lambda f: f["fdcId"]):
                add_food(db, food, release, code_of(food), prefix, report, dropped_foods)
        db.execute("INSERT INTO food_fts(food_fts) VALUES ('rebuild')")
        db.execute("INSERT INTO food_fts(food_fts) VALUES ('optimize')")
    db.execute("VACUUM")
    db.close()
    return report, dropped_foods


def add_food(db, food, release, code, prefix, report, dropped_foods):
    report[f"{prefix}foods in"] += 1
    report[f"{prefix}portions in"] += len(food["foodPortions"])
    report[f"{prefix}additional descriptions in"] += sum(
        1 for a in food["foodAttributes"] if a["foodAttributeType"]["id"] == ADDITIONAL_DESCRIPTION
    )
    amounts = {n["nutrient"]["id"]: n.get("amount") for n in food["foodNutrients"]}
    description = sr_clean_description(food) if release == SR_RELEASE else food["description"]
    if amounts.get(KCAL) is None:
        dropped_foods.append((food["fdcId"], description))
        report[f"{prefix}portions of dropped foods"] += len(food["foodPortions"])
        return
    additional = additional_descriptions(food)
    report[f"{prefix}additional descriptions kept"] += len(additional)
    db.execute(
        "INSERT INTO food VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        (
            food["fdcId"], release, code, description, "; ".join(additional) or None,
            amounts[KCAL], amounts.get(PROTEIN), amounts.get(FAT), amounts.get(CARBS),
            amounts.get(SATURATED_FAT), amounts.get(SUGARS), amounts.get(FIBRE), salt_of(amounts.get(SODIUM)),
            search_text(description),
            search_text(" ".join(additional + [EXTRA_SEARCH_WORDS.get(description, "")]).strip()),
            STAPLE_BUMP if description in STAPLE_FOODS else COMMON_BUMP if description in COMMON_FOODS else 0,
        ),
    )
    report[f"{prefix}foods kept"] += 1
    for macro in (PROTEIN, FAT, CARBS):
        if amounts.get(macro) is None:
            report[f"{prefix}unknown macros"] += 1
    for nutrient in (SATURATED_FAT, SUGARS, FIBRE, SODIUM):
        if amounts.get(nutrient) is None:
            report[f"{prefix}unknown saturated fat, sugars, fibre or sodium"] += 1
    position = 0
    for portion in sorted(food["foodPortions"], key=lambda p: (p["sequenceNumber"], p["id"])):
        reason = portion_drop_reason(portion)
        if reason:
            report[f"{prefix}portions dropped: {reason}"] += 1
            continue
        db.execute("INSERT INTO portion VALUES (?, ?, ?, ?)", (food["fdcId"], position, portion_label(portion), portion["gramWeight"]))
        position += 1
        report[f"{prefix}portions kept"] += 1


def check_sqlite_supports_fts4():
    options = {row[0] for row in sqlite3.connect(":memory:").execute("PRAGMA compile_options")}
    if "ENABLE_FTS4" not in options and "ENABLE_FTS3" not in options:
        fail("this Python's sqlite3 has no FTS4")
    print(f"sqlite {sqlite3.sqlite_version}: FTS4 available")


def ids_for(db, match):
    """The foods a plain MATCH finds: quoted words only, the form the app sends."""
    return {row[0] for row in db.execute("SELECT docid FROM food_fts WHERE food_fts MATCH ?", (match,))}



# What each typed home staple must find first on the real file: the same ranking as the app (UsdaQuery.rank).
STAPLE_SEARCHES = {
    "flour": "Wheat flour, white, all-purpose, enriched, bleached", "брашно": "Wheat flour, white, all-purpose, enriched, bleached",
    "corn flour": "Corn flour, whole-grain, yellow", "пченкарно брашно": "Corn flour, whole-grain, yellow",
    "cornmeal": "Cornmeal, whole-grain, yellow", "salt": "Salt, table", "сол": "Salt, table",
    "baking powder": "Leavening agents, baking powder, double-acting, sodium aluminum sulfate",
    "прашок за пециво": "Leavening agents, baking powder, double-acting, sodium aluminum sulfate",
    "baking soda": "Leavening agents, baking soda", "semolina": "Semolina, unenriched",
}
MAX_EXPRESSIONS = 64
PREFIX_MIN_LETTERS = 3
CYRILLIC = re.compile("[Ѐ-ӿ]")


def search_words(text):
    """matchWords: lowercase, apostrophes dropped, cut to letters, digits, "." and "%"."""
    return [w for w in re.sub(r"[^\w.%]+|_", " ", search_text(text.lower())).split() if w]


def name_words(text):
    return {part for w in search_words(text) for part in re.split(r"[.%]", w) if part}


def singular_forms(word):
    """singularForms in Servings.kt, without the irregular plurals; Latin words only."""
    forms = {word}
    if CYRILLIC.search(word):
        return forms
    if word.endswith("ies") and len(word) > 4:
        forms.add(word[:-3] + "y")
    if word.endswith("es") and len(word) > 3:
        forms.add(word[:-2])
    if word.endswith("s") and not word.endswith("ss") and len(word) > 2:
        forms.add(word[:-1])
    return forms


def top_result(db, text):
    """The description the app ranks first for [text] (usdaQuery, matchExpressions and rank in UsdaSearch.kt), or None."""
    aliases, translations = {}, {}
    for word, target in db.execute("SELECT word, target FROM alias ORDER BY word, target"):
        aliases.setdefault(word, []).append(target)
    for word, english in db.execute("SELECT word, english FROM translation ORDER BY word, english"):
        translations.setdefault(word, []).append(english)
    typed, groups, at = search_words(text), [], 0
    while at < len(typed):
        phrase = next(((size, translations[" ".join(typed[at:at + size])]) for size in (3, 2)
                       if len(typed[at:at + size]) == size and " ".join(typed[at:at + size]) in translations), None)
        if phrase:
            groups.append(phrase[1])
            at += phrase[0]
        else:
            word = typed[at]
            groups.append(translations[word] if CYRILLIC.search(word) else [word])
            at += 1
    words = list(dict.fromkeys(part for group in groups for part in name_words(group[0])))
    forms = dict(aliases)
    for group in groups:
        if len(group) > 1:
            forms[group[0]] = group[1:] + aliases.get(group[0], [])
    last_is_prefix = sum(c.isalpha() for c in words[-1]) >= PREFIX_MIN_LETTERS

    def other_forms(word):
        ies = word[:-1] + "ies" if len(word) > 2 and word.endswith("y") and word[-2] not in "aeiou" else None
        return forms.get(word, []) + sorted(singular_forms(word) - {word}) + ([ies] if ies else [])

    expressions = [[]]
    for word in words:
        tries = [f'"{word}*"' if word == words[-1] and last_is_prefix else f'"{word}"'] + [f'"{f}"' for f in other_forms(word)]
        expressions = [e + [t] for e in expressions for t in tries][:MAX_EXPRESSIONS]
    found = {}
    for expression in expressions:
        for row in db.execute("SELECT fdc_id, description, bump FROM food WHERE fdc_id IN (SELECT docid FROM food_fts WHERE food_fts MATCH ?)", (" ".join(expression),)):
            found.setdefault(row[0], row)

    def has(word, name):
        return word in name or any(f in name for f in other_forms(word)) or any(word in singular_forms(n) for n in name)

    def key(row):
        name = name_words(row[1])
        whole = sum(has(w, name) for w in words)
        prefix = 1 if last_is_prefix and not has(words[-1], name) and any(n.startswith(words[-1]) for n in name) else 0
        return (-whole, -(whole + prefix), -row[2], len(row[1]), row[0])

    return min(found.values(), key=key)[1] if found else None


def check_database(path, report):
    with contextlib.closing(sqlite3.connect(path)) as db:
        check_contents(db, report)


def check_contents(db, report):
    if report["foods in"] != EXPECTED_FOODS_IN:
        fail(f"{report['foods in']} foods in, expected {EXPECTED_FOODS_IN}")
    if report["portions in"] != EXPECTED_PORTIONS_IN:
        fail(f"{report['portions in']} portions in, expected {EXPECTED_PORTIONS_IN}")
    if report["additional descriptions in"] != EXPECTED_ADDITIONAL_IN:
        fail(f"{report['additional descriptions in']} additional descriptions in, expected {EXPECTED_ADDITIONAL_IN}")
    if db.execute("SELECT count(*) FROM food WHERE release = ?", (SR_RELEASE,)).fetchone()[0] != report["SR foods kept"]:
        fail("the SR Legacy foods in the file")
    if db.execute("SELECT count(*) FROM food WHERE fdc_id >= 1000000 AND release != ?", (FNDDS_RELEASE,)).fetchone()[0]:
        fail("an FNDDS food labelled as another release")

    with db:
        db.execute("INSERT INTO food_fts(food_fts) VALUES ('integrity-check')")
    integrity = db.execute("PRAGMA integrity_check").fetchone()[0]
    print(f"integrity_check: {integrity}; FTS integrity-check: ok")
    if integrity != "ok":
        fail("integrity_check")

    food = db.execute("SELECT kcal, protein, fat, carbs FROM food WHERE fdc_id = 2705975").fetchone()
    print(f"FDC 2705975: {food[0]} kcal, {food[1]} g protein, {food[2]} g fat, {food[3]} g carbs; 165 g is {round(food[0] * 1.65, 2)} kcal")
    if food != (219.0, 24.6, 9.99, 6.01) or round(food[0] * 1.65, 2) != 361.35:
        fail("FDC 2705975's values")
    extras = db.execute("SELECT saturated_fat, sugars, fibre, salt FROM food WHERE fdc_id = 2705975").fetchone()
    print(f"FDC 2705975: {extras[0]} g saturated fat, {extras[1]} g sugars, {extras[2]} g fibre, {extras[3]} g salt")
    if extras != (1.58, 0.1, 0.2, 0.795):
        fail("FDC 2705975's saturated fat, sugars, fibre or salt")

    coated = ids_for(db, '"chicken" "breast" "coated*"')
    breaded = ids_for(db, '"breaded" "chicken" "breast*"') | ids_for(db, '"coated" "chicken" "breast*"')
    print(f'"chicken breast coated" matches {len(coated)}, "breaded chicken breast" matches {len(breaded)}')
    if 2705975 not in coated or 2705975 not in breaded:
        fail("2705975 is not found")
    if ids_for(db, '"млеко"'):
        fail('"млеко" finds something')
    print('the Cyrillic word for milk matches 0')

    missing = [name for name in COMMON_FOODS + STAPLE_FOODS if not db.execute("SELECT 1 FROM food WHERE description = ?", (name,)).fetchone()]
    if missing:
        fail(f"common foods not in the file: {missing}")
    print(f"{db.execute('SELECT count(*) FROM food WHERE bump > 0').fetchone()[0]} foods have a ranking bump")

    missing = [name for name in EXTRA_SEARCH_WORDS if not db.execute("SELECT 1 FROM food WHERE description = ?", (name,)).fetchone()]
    if missing:
        fail(f"foods with extra search words not in the file: {missing}")
    print(f"{len(EXTRA_SEARCH_WORDS)} foods have extra search words")

    if len(set(MACEDONIAN_WORDS)) != len(MACEDONIAN_WORDS):
        fail("a Macedonian word and its English word are listed twice")
    empty = [word for word, english in MACEDONIAN_WORDS if not ids_for(db, f'"{english}"')]
    if empty:
        fail(f"Macedonian words whose English finds nothing: {empty}")
    print(f"{len(MACEDONIAN_WORDS)} Macedonian words, each finds a food")
    for typed, name in STAPLE_SEARCHES.items():
        top = top_result(db, typed)
        print(f'top result for "{typed}": {top}')
        if top != name:
            fail(f'"{typed}" ranks {top!r} first, expected {name!r}')
    for word, english in (("пита", "pie"), ("пита", "pita"), ("печено", "baked"), ("печено", "roasted"), ("црн дроб", "liver")):
        if (word, english) not in MACEDONIAN_WORDS:
            fail(f"{word} does not ask {english}")


def write_gzip(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_name(target.name + ".tmp")
    # No name and no time in the header, so the bytes depend only on the database.
    with open(source, "rb") as raw, open(temporary, "wb") as out:
        with gzip.GzipFile(filename="", mode="wb", compresslevel=9, fileobj=out, mtime=0) as zipped:
            shutil.copyfileobj(raw, zipped)
    os.replace(temporary, target)


def write_words_csv(target):
    """The Macedonian table for the owner to check. UTF-8 with a BOM, so a spreadsheet reads the Cyrillic."""
    with open(target, "w", encoding="utf-8-sig", newline="\n") as out:
        writer = csv.writer(out, lineterminator="\n")
        writer.writerow(("macedonian", "english"))
        writer.writerows(MACEDONIAN_WORDS)


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--archive", type=Path, help="a downloaded copy of the FNDDS archive; without it the script downloads it")
    parser.add_argument("--sr-archive", type=Path, help="a downloaded copy of the SR Legacy archive; without it the script downloads it")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT, help="the gzipped asset to write")
    args = parser.parse_args()

    check_sqlite_supports_fts4()
    with tempfile.TemporaryDirectory() as work:
        work = Path(work)
        archive = args.archive
        if archive is None:
            archive = work / "survey.zip"
            print(f"downloading {ARCHIVE_URL}")
            urllib.request.urlretrieve(ARCHIVE_URL, archive)
        foods = load_foods(archive, ARCHIVE_SHA256, ARCHIVE_MEMBER, "SurveyFoods")
        print(f"archive SHA-256 ok: {ARCHIVE_SHA256}")
        sr_archive = args.sr_archive
        if sr_archive is None:
            sr_archive = work / "sr_legacy.zip"
            print(f"downloading {SR_ARCHIVE_URL}")
            urllib.request.urlretrieve(SR_ARCHIVE_URL, sr_archive)
        all_sr_foods = load_foods(sr_archive, SR_ARCHIVE_SHA256, SR_ARCHIVE_MEMBER, "SRLegacyFoods")
        print(f"SR Legacy archive SHA-256 ok: {SR_ARCHIVE_SHA256}")
        if len(all_sr_foods) != EXPECTED_SR_FOODS_IN:
            fail(f"{len(all_sr_foods)} SR Legacy foods in, expected {EXPECTED_SR_FOODS_IN}")
        selection_report = Counter()
        sr_foods = select_sr_foods(all_sr_foods, foods, selection_report)

        database = work / "catalogue.sqlite"
        report, dropped_foods = build_database(foods, sr_foods, database)
        report.update(selection_report)
        for key in sorted(report):
            print(f"{key}: {report[key]}")
        for fdc_id, description in dropped_foods:
            print(f"food dropped, no energy: {fdc_id} {description}")
        check_database(database, report)

        write_gzip(database, args.output)
        write_words_csv(WORDS_CSV)
        print(f"sqlite bytes: {database.stat().st_size}")
    print(f"gzip bytes: {args.output.stat().st_size}")
    print(f"gzip SHA-256: {sha256_of(args.output)}")
    print(f"written: {args.output}")


if __name__ == "__main__":
    main()
