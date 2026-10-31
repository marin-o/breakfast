#!/usr/bin/env python3
"""Builds the app's USDA food catalogue (docs/usda-catalogue-spec.md) from the official FNDDS 2021-2023 archive.

Stdlib only:
    python -I tools/build_usda_catalogue.py                       downloads the archive to a temporary directory
    python -I tools/build_usda_catalogue.py --archive survey.zip  uses a copy already downloaded

The archive is checked against its SHA-256 before it is opened and is never kept in the repository. The same archive
and the same SQLite version give the same bytes. Any failed check ends with a non-zero exit and leaves the asset as it was.
"""
import argparse
import contextlib
import gzip
import hashlib
import json
import os
import shutil
import sqlite3
import tempfile
import urllib.request
import zipfile
from collections import Counter
from pathlib import Path

ARCHIVE_URL = "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_survey_food_json_2024-10-31.zip"
ARCHIVE_SHA256 = "dfb06ae7ddc397ccd570b91c14b75438ab2ba39f64f22d321f61d4a52a77f3eb"
ARCHIVE_MEMBER = "surveyDownload.json"

# Raise it whenever the file's content changes, with USDA_CATALOGUE_VERSION in UsdaCatalogueReader.kt.
CATALOGUE_VERSION = 1
# Not ".gz": the Android Gradle plugin unpacks a .gz asset when it merges assets and drops the extension.
DEFAULT_OUTPUT = (
    Path(__file__).resolve().parent.parent / "app/src/main/assets/usda" / f"fndds-2021-2023-v{CATALOGUE_VERSION}.sqlite.gzip"
)

# What the archive holds, by the spec. A different count means a different archive or a broken reader.
EXPECTED_FOODS_IN = 5432
EXPECTED_PORTIONS_IN = 22194
EXPECTED_ADDITIONAL_IN = 9647

ADDITIONAL_DESCRIPTION = 1001
KCAL, PROTEIN, FAT, CARBS = 1008, 1003, 1004, 1005
UNSPECIFIED_MODIFIER = "90000"
DROPPED_LABEL_WORDS = ("guideline amount", "yield", "surface")

# The reviewed aliases: a typed word also tries its target, and the typed word stays in the query. They ship in the
# file, so the app has one list.
ALIASES = (("breaded", "coated"),)

SCHEMA = """
CREATE TABLE food (
    fdc_id INTEGER PRIMARY KEY,
    food_code TEXT NOT NULL,
    description TEXT NOT NULL,
    additional TEXT,
    kcal REAL NOT NULL,
    protein REAL,
    fat REAL,
    carbs REAL,
    search_name TEXT NOT NULL,
    search_extra TEXT NOT NULL
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


def load_foods(archive):
    actual = sha256_of(archive)
    if actual != ARCHIVE_SHA256:
        fail(f"{archive} has SHA-256 {actual}, expected {ARCHIVE_SHA256}")
    with zipfile.ZipFile(archive) as zipped:
        return json.loads(zipped.read(ARCHIVE_MEMBER))["SurveyFoods"]


def portion_drop_reason(portion):
    """Why a portion is not kept, or None when it has a clear label and grams above 0."""
    label = (portion.get("portionDescription") or "").strip()
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


def build_database(foods, path):
    report = Counter()
    dropped_foods = []
    db = sqlite3.connect(path)
    db.executescript("PRAGMA page_size=4096; PRAGMA journal_mode=DELETE; PRAGMA encoding='UTF-8';")
    db.execute(f"PRAGMA user_version={CATALOGUE_VERSION}")
    db.executescript(SCHEMA)
    with db:
        db.executemany("INSERT INTO alias VALUES (?, ?)", ALIASES)
        for food in sorted(foods, key=lambda f: f["fdcId"]):
            report["foods in"] += 1
            report["portions in"] += len(food["foodPortions"])
            report["additional descriptions in"] += sum(
                1 for a in food["foodAttributes"] if a["foodAttributeType"]["id"] == ADDITIONAL_DESCRIPTION
            )
            amounts = {n["nutrient"]["id"]: n.get("amount") for n in food["foodNutrients"]}
            if amounts.get(KCAL) is None:
                dropped_foods.append((food["fdcId"], food["description"]))
                report["portions of dropped foods"] += len(food["foodPortions"])
                continue
            additional = additional_descriptions(food)
            report["additional descriptions kept"] += len(additional)
            db.execute(
                "INSERT INTO food VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (
                    food["fdcId"], food["foodCode"], food["description"], "; ".join(additional) or None,
                    amounts[KCAL], amounts.get(PROTEIN), amounts.get(FAT), amounts.get(CARBS),
                    search_text(food["description"]), search_text(" ".join(additional)),
                ),
            )
            report["foods kept"] += 1
            for macro in (PROTEIN, FAT, CARBS):
                if amounts.get(macro) is None:
                    report["unknown macros"] += 1
            position = 0
            for portion in sorted(food["foodPortions"], key=lambda p: (p["sequenceNumber"], p["id"])):
                reason = portion_drop_reason(portion)
                if reason:
                    report[f"portions dropped: {reason}"] += 1
                    continue
                db.execute(
                    "INSERT INTO portion VALUES (?, ?, ?, ?)",
                    (food["fdcId"], position, portion["portionDescription"].strip(), portion["gramWeight"]),
                )
                position += 1
                report["portions kept"] += 1
        db.execute("INSERT INTO food_fts(food_fts) VALUES ('rebuild')")
        db.execute("INSERT INTO food_fts(food_fts) VALUES ('optimize')")
    db.execute("VACUUM")
    db.close()
    return report, dropped_foods


def check_sqlite_supports_fts4():
    options = {row[0] for row in sqlite3.connect(":memory:").execute("PRAGMA compile_options")}
    if "ENABLE_FTS4" not in options and "ENABLE_FTS3" not in options:
        fail("this Python's sqlite3 has no FTS4")
    print(f"sqlite {sqlite3.sqlite_version}: FTS4 available")


def ids_for(db, match):
    """The foods a plain MATCH finds: quoted words only, the form the app sends."""
    return {row[0] for row in db.execute("SELECT docid FROM food_fts WHERE food_fts MATCH ?", (match,))}


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

    coated = ids_for(db, '"chicken" "breast" "coated*"')
    breaded = ids_for(db, '"breaded" "chicken" "breast*"') | ids_for(db, '"coated" "chicken" "breast*"')
    print(f'"chicken breast coated" matches {len(coated)}, "breaded chicken breast" matches {len(breaded)}')
    if 2705975 not in coated or 2705975 not in breaded:
        fail("2705975 is not found")
    if ids_for(db, '"млеко"'):
        fail('"млеко" finds something')
    print('the Cyrillic word for milk matches 0')


def write_gzip(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_name(target.name + ".tmp")
    # No name and no time in the header, so the bytes depend only on the database.
    with open(source, "rb") as raw, open(temporary, "wb") as out:
        with gzip.GzipFile(filename="", mode="wb", compresslevel=9, fileobj=out, mtime=0) as zipped:
            shutil.copyfileobj(raw, zipped)
    os.replace(temporary, target)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--archive", type=Path, help="a downloaded copy of the archive; without it the script downloads it")
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
        foods = load_foods(archive)
        print(f"archive SHA-256 ok: {ARCHIVE_SHA256}")

        database = work / "catalogue.sqlite"
        report, dropped_foods = build_database(foods, database)
        for key in sorted(report):
            print(f"{key}: {report[key]}")
        for fdc_id, description in dropped_foods:
            print(f"food dropped, no energy: {fdc_id} {description}")
        check_database(database, report)

        write_gzip(database, args.output)
        print(f"sqlite bytes: {database.stat().st_size}")
    print(f"gzip bytes: {args.output.stat().st_size}")
    print(f"gzip SHA-256: {sha256_of(args.output)}")
    print(f"written: {args.output}")


if __name__ == "__main__":
    main()
