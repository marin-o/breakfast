# Breakfast

Breakfast is an Android-first calorie and macro tracker built around fast natural-language logging. Enter shorthand such as `2 tbsp oats, half a tbsp peanut butter, banana` in a meal, and the app parses the portions and resolves familiar foods locally. Explicit aliases and personal history rank matches; automatic guesses never reinforce a preference. Submitted Open Food Facts search and barcode scanning cover foods outside the local catalogue.

The Room-backed diary has Breakfast, Lunch, Dinner, and Snacks for each date. Swipe the main diary between dates, open a meal to inspect entries, or browse the local Food Library to manage personal foods. A logged entry keeps its amount, nutrition snapshot, and conversion provenance so later food edits do not silently rewrite history. Tap an entry for details, double-tap for amount-only quick edit, hold for full entry edit, or swipe left inside a meal to delete with Undo. Eligible saved barcode foods can be reviewed and optionally contributed to Open Food Facts from their detail view. Recipes, conversational parsing, cloud sync, and account-backed storage are outside the current app.

## Build locally

Tool downloads and caches belong under `_stack`; the source tree and debug APK stay in normal project paths. Use JDK 21 at `<jdk21>` if the machine default is Java 8. An Android SDK can be placed at `_stack/android-sdk`. The Gradle wrapper is pinned to Gradle 8.9; with `GRADLE_USER_HOME` set as below, it downloads the distribution once into `_stack/gradle-home`.

```powershell
git config core.hooksPath .githooks
$env:JAVA_HOME = '<jdk21>'
$env:ANDROID_HOME = './_stack/android-sdk'
$env:GRADLE_USER_HOME = './_stack/gradle-home'
.\gradlew.bat testDebugUnitTest assembleDebug assemblePerf
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk` and the `perf` APK (R8-minified, signed with the debug key) at `app/build/outputs/apk/perf/app-perf.apk`. Install with the existing platform-tools `adb install -r`, which keeps the app's local data. Bump `versionCode` in `app/build.gradle.kts` by 1 for every build installed on the phone; `versionName` stays `0.x` until a release. Signing, backup and recovery are in [`docs/architecture.md`](docs/architecture.md).

Room uses version 2.8.5 with KAPT and the official Room Gradle plugin. Versioned schema JSON for migrations is exported under `app/schemas/debug`, which every variant uses. KAPT emits its standard Kotlin 2.0 language-version fallback warning, but the Room compiler and both variants build successfully.

## Fixture catalogue

The prototype includes 31 additional generic records from USDA FoodData Central Foundation Foods (2026-04-30) and SR Legacy (2018-04). Each record keeps its FDC source ID, source release, preparation state, and per-100-gram calories, protein, carbohydrate, and fat values. The expansion covers raw chicken, beef, pork, salmon, drained tuna; raw broccoli, carrots, cucumber, Roma and grape tomatoes, bell peppers, spinach, onions, green and red cabbage, zucchini, lettuce, and eggplant; plus ketchup, prepared yellow mustard, canned tomato sauce, regular mayonnaise, shoyu soy sauce, and barbecue sauce. Meat records are explicitly raw because the user weighs meat before cooking, and unqualified meat queries therefore resolve to raw records. Household conversions are included only where the USDA source supplies a portion, such as mustard teaspoon, mayonnaise tablespoon, soy sauce tablespoon/teaspoon, and barbecue sauce tablespoon.

The pinned exports are small build-time source snapshots; the app has no runtime USDA dependency or API key. Turkey breast and a raw mushroom record remain omitted because the available exports did not provide matching records with an explicit preparation and complete macro set. Sources: [USDA FoodData Central downloads](https://fdc.nal.usda.gov/download-datasets/). Existing installs receive missing fixture IDs through an additive migration that preserves custom foods, aliases, preferences, and saved nutrition snapshots.

## Focused checks

`BreakfastDomainTest` covers quantity-first and quantity-after parsing, fractions, comma segments, modifier-constrained matching in English and Cyrillic, serving conversion and nutrition calculation, and legacy codec round trips. `RoomStorageMappingTest` covers stable-ID seeding, entity round trips, source metadata, and nullable-vs-zero snapshot columns. Core logging works locally; submitted OFF search, barcode lookup, and optional contribution require a connection.

`BreakfastDatabaseTest` is an Android test for additive seed idempotence, meal snapshots surviving a database close/reopen, transactional food upserts, and the Room migrations 1→5, 2→4 and 5→6 (the schema is at version 6). It runs only on a device; the JVM tests do not cover migrations. Compile it with `compileDebugAndroidTestKotlin`. Never run `connectedDebugAndroidTest` on the phone that holds the diary: AGP uninstalls the app afterwards. Use the manual procedure in [`docs/architecture.md`](docs/architecture.md).

## Commit messages

The repository uses a local `commit-msg` hook at `.githooks/commit-msg`. Enable it once per clone with `git config core.hooksPath .githooks` (repository-local; no global Git configuration is changed). It accepts Conventional Commit prefixes such as `feat:`, `fix:`, `docs:`, `test:`, `chore:`, and `build:` with optional scopes or `!`, plus the subjects git writes itself for `git revert` (`Revert "…"`) and `git merge` (`Merge …`), and rejects other messages before commit creation.
