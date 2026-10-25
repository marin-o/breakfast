# Architecture

A single-module Android app (`app/`, package `com.iron.breakfast`): Kotlin, Jetpack Compose, Material 3, Room. There is no DI framework, no navigation library and no repository layer; this is deliberate for a one-person, local-only app. Read `PRODUCT.md` for product rules and `DESIGN.md` for the locked Monochrome design.

## Package map

| Package / file | Responsibility |
| --- | --- |
| `MainActivity.kt` | Entry point. `MainActivity` sets edge-to-edge and hosts `IronApp` inside `BreakfastTheme`. `BreakfastViewModel` (`AndroidViewModel` + `SavedStateHandle`) owns all screen state and every user action. Routing is a set of VM flags, not a nav graph. |
| `domain/` | Pure Kotlin rules with no Android types (except `BreakfastEngine`). `ShorthandParser`/`ShorthandLexicon` (text to segments), `FoodMatcher` (local ranking, script rule), `LogPlanner` (draft rows and totals), `NutritionCalculator`, `NutritionShorthand`, `Barcode`, `Models`, `FoodCatalog` (bundled USDA and generic fixtures), `BuiltInCatalogSync`. `BreakfastEngine` is the facade the VM calls; it wraps `BreakfastStore`. |
| `data/BreakfastStore.kt` | The only writer. Opens Room (`breakfast.db`), seeds and syncs the catalogue, holds the in-memory `StoreSnapshot` the UI reads. |
| `data/RoomStorage.kt` | Entities, DAOs, `BreakfastDatabase` (schema v6), migrations 1 to 6. Exported schemas: `app/schemas/debug` (used by every variant). |
| `data/DiaryExport.kt` | The diary file for Export diary and Import diary: DTOs, Room rows to file and back, and the import checks. Pure, unit-tested (see Export and import). |
| `data/OpenFoodFactsClient.kt` | The only network code (see OFF boundary). |
| `FoodDraft.kt` | New food / edit food form state, its typed values (`FoodDraftFields`) and the dirty check. |
| `AppDecisions.kt` | Pure routing decisions the VM makes (`topOverlay`, `exitLibraryTarget`, submit and review plans, the Undo window, `resolveSelectedDay`), unit-tested. |
| `SavedDrafts.kt` | Save and restore of the log draft, an open food form and the selected day in `SavedStateHandle`. |
| `AppSettings.kt` | User settings in plain `SharedPreferences` (`settings`): the day start hour and the composer's "Long draft" mode, with their pure helpers. |
| `ui/screens/` | One file per surface: Daily (the Today tab), Log, Meal, Library, Entry, Search, Scan, CreateFood, Settings, plus `ScreenData.kt` (pure UI mapping, unit-tested). |
| `ui/components/`, `ui/theme/` | Shared Compose components; Monochrome tokens (`Color.kt` mirrors DESIGN.md), type, shape, dimens. |

## State owners

All state lives in `BreakfastViewModel`, so it survives configuration changes (the app is portrait only; dark mode and font size still recreate the activity); very little survives process death.

| State | Owner | Survives process death |
| --- | --- | --- |
| Selected day | VM `selectedDayKey` and `followingToday` + `SavedStateHandle` (`following_today`; `selected_day` only for a picked past day) | Yes; a user following today reopens on the new today |
| Settings (day start, long draft) | `AppSettings` (`SharedPreferences`), read once into the VM, written on each change | Yes |
| Open meal, selected entry | VM + `SavedStateHandle` (`meal_slot`, `entry_id`) | Yes |
| Route and overlay flags (Library, Log, scanner, sheets) | VM | No; the app reopens on the diary, with an open food form over it |
| Log draft text and committed flag | VM `logDraft`, one draft for Daily and Meal, separate from `input` (the Library bar and edit amount); only Add or clearing it removes it | Yes (`log_draft`, `log_draft_committed`; removed once the draft is empty) |
| Log pins, resolve queue | VM | No |
| New / edit food form (`FoodDraft`) | VM + `SavedStateHandle` (`food_form`, one JSON string) | Yes, the form reopens; not while resolving a part or a queue |
| Edit-sheet typed values (entry quick and full edit, Library amount sheet, manual barcode, scan review) and the scanner's "camera permission asked" flag | Composition `rememberSaveable`, so a configuration change (dark mode, font size) keeps them; a save error (`mealDraftError`) stays until one of them changes | No; these sheets do not reopen after process death |
| Pending Undo (`UndoOffer`) | VM; expires 15 s after the log or delete, or at the next log, edit or delete | No |
| OFF search query and results, scan review fields | VM | No |
| OFF username and password | VM memory only | No, by design |
| Diary, foods, aliases, conversions, preferences | Room | Yes |

## Durable save path

Add (or any edit) runs in `viewModelScope`: `BreakfastViewModel` calls `BreakfastEngine` (for example `save`, `saveCustomAndLog`, `replaceEntry*`), which calls a `BreakfastStore` suspend function. The store takes one `Mutex`, switches to `Dispatchers.IO`, writes inside `database.withTransaction`, then reloads the whole `StoreSnapshot`. "Saved" means that Room transaction committed. A logged entry stores its own nutrition snapshot and conversion provenance, so later food edits never rewrite history. Migrations are additive and there is no destructive fallback.

## Export and import

Settings has Export diary and Import diary. Both use the system document picker (Storage Access Framework), so no permission is needed. The file is one JSON document (`data/DiaryExport.kt`, kotlinx-serialization, strict: unknown or missing fields fail):

- `format` (`"breakfast-diary"`), `formatVersion` (1: the layout of this file), `schemaVersion` (the Room schema the rows came from, now 6), `exportedAtEpochMs`.
- `settings`: `dayStartHour` and `longDraft` (a `LongDraft` name).
- One array per Room table, every row and column as stored: `foods` (`foods`: bundled catalogue rows, custom, Open Food Facts, archived and personally edited foods), `foodAliases` (`food_aliases`), `servingConversions` (`serving_conversions`), `preferences` (`preferences`), `mealEntries` (`meal_entries`, each with its own nutrition snapshot). Rows are sorted by key, so the same diary always writes the same file. An alias, conversion or preference whose food is not stored is left out (the app never shows one).

Catalogue rows are included as stored rather than re-derived, so a built-in food the user edited or archived comes back exactly. Import reads the whole file (at most 32 MB) and checks all of it before anything is written: the format and its version, a schema version no newer than the app's, settings in range, every enum name known, every number finite and not negative (a nutrition basis and a serving size above zero), day keys real dates, ids unique (per table key), and each alias, conversion and preference naming a food in the file. An entry's food id need not name a food: the entry keeps its own name and snapshot. A file that fails any check changes nothing and shows "That file can't be imported."; the reason goes to logcat (tag `Breakfast`). A file that passes asks Cancel or Replace diary. Replace diary runs `BreakfastStore.replaceDiary`: one Room transaction deletes every table, inserts the file's rows (a clash aborts), then seeds and syncs the catalogue as at start, so a file from an older build gets the current catalogue. A failure rolls everything back. The settings from the file are then applied, and Undo, log pins and pending confirmations from the old diary are dropped. `DiaryImportTest` (androidTest) covers export, wipe, import with equal counts and checksum, and a failing insert that leaves the previous diary.

When the schema changes, bump `DIARY_SCHEMA_VERSION` with it (a unit test fails until it matches `app/schemas` and covers every table), and keep reading files from older schema versions.

## Day boundary

A diary day starts at the "Day starts at" hour (Settings, 00:00 to 23:00, default 00:00). `dayKeyFor(epochMs, zone, dayStartHour)` gives the day an instant belongs to: before the start hour it is still the previous date, read from the wall clock so a daylight-saving day is one day. It decides what "today" is (`todayKey`: the Daily header and Today button, Next, the date picker's last day) and so the day a new log lands on, since every log goes to the selected day. Stored entries keep their stored `dayKey`; changing the setting rewrites nothing. `resolveSelectedDay` (in `AppDecisions.kt`) moves the selection only on a return: when the activity starts again after a stop, or the VM is created or restored, a user following today lands on the new today and a picked past day stays. While the app is on screen the selection never moves; the 60-second loop only recomputes `todayKey`, so after the day turns the old day shows with the Today button, as after a swipe. Changing the setting counts as a return.

## OFF boundary

`OpenFoodFactsClient` uses `HttpURLConnection`, HTTPS only, 4.5 s timeouts, off the main thread. It is called only on an explicit action: submitted search (`cgi/search.pl`, then `search.openfoodfacts.org`), barcode lookup (`api/v3/product`), and the optional contribution from food detail (`cgi/session.pl` login, then `cgi/product_jqm2.pl`). There is no search-as-you-type and no automatic retry of writes. Only a transport failure (`IOException`) is "no connection"; an HTTP error other than 404 or an unreadable body is a server error. A contribution whose answer is lost after its body was written is reported as possibly sent, and Retry reads the product again before anything is resent. Data read from and sent to OFF passes one plausibility rule, `plausibleNutrition` (per 100 g or ml: kcal 0 to 1000, macros 0 to 100 g). The User-Agent is `Breakfast/<versionName> (daily food logger)`, from `BuildConfig.VERSION_NAME`. The OFF password is held in VM memory only, cleared when the submit starts, and **never persisted** (not to Room, not to `SavedStateHandle`). Core logging works offline.

## Dependencies and data flow

| Dependency | Purpose | Data access |
| --- | --- | --- |
| Compose BOM 2024.09.03 (ui, material3, material-icons-extended), activity-compose 1.9.2, core-ktx 1.13.1, lifecycle 2.8.6 | UI, ViewModel, SavedStateHandle | Screen state only |
| Room 2.8.5 (runtime, ktx; compiler via KAPT; Room Gradle plugin) | Local database `breakfast.db` | All diary and food data, on device |
| kotlinx-serialization-json 1.8.1 | OFF response parsing, storage codecs | OFF responses, local rows |
| CameraX 1.4.2 (camera2, lifecycle, view) | Scanner preview and frame analysis | Camera frames, in memory, while the scanner is open |
| ML Kit barcode-scanning 17.3.0 | Barcode decoding on device | Camera frames. It pulls `play-services-mlkit-barcode-scanning` and `com.google.mlkit:common`, which bring the Google Data Transport stack (`transport-backend-cct`, `firebase-components`). Whether it sends telemetry at runtime is **not verified**; check before any Play release (MOB-032). |
| Open Food Facts (network service, no SDK) | Search, barcode lookup, contribution | Query text, barcodes; on contribution, the product fields and the OFF login |
| Test only: JUnit 4.13.2, kotlin-test, room-testing, androidx.test runner/core/ext-junit | Unit and instrumented tests | None |

Permissions: `INTERNET` and `CAMERA` only; the camera is declared optional. No analytics or crash SDK.

## Signing

Every build, including `perf`, is signed with the machine's Android debug key, `%USERPROFILE%\.android\debug.keystore` (SHA-256 `98:93:7B:8D:95:25:A4:58:E1:71:55:F7:67:DB:0F:6A:26:09:3D:4A:23:74:7C:11:6E:D6:E4:5D:4B:AE:4B:1D`). **Back this file up.** A build signed with a different key cannot be installed over the current one: Android forces an uninstall first, and the uninstall deletes the diary.

## Backup

`android:allowBackup="true"` with explicit rules (MOB-012, decided 2026-10-26: the diary stays out of Google's cloud):

| Copy | Android 12+ (`res/xml/data_extraction_rules.xml`) | Android 11 and older (`res/xml/backup_rules.xml`) |
| --- | --- | --- |
| Google cloud backup | Settings (`SharedPreferences`) only. `breakfast.db` and its `-wal`, `-shm` and `-journal` files are excluded. | The same: the database files are excluded. |
| Device-to-device transfer (cable or local transfer to a new phone, such as Smart Switch) | Everything, the diary included (no device-transfer rules, so all app data moves). | Not the diary: one rule set covers both, and it excludes the database. |

The backup is the user's own file: Settings, Export diary (see Export and import). After a reinstall or on a new phone, Import diary puts it back.

## Versions

Bump `versionCode` by 1 for every build installed on the phone. `versionName` stays `0.x` until a release. Both live in `app/build.gradle.kts`.

## Recovery and testing on the phone

- Update in place only: `adb install -r app/build/outputs/apk/perf/app-perf.apk`. `-r` keeps the app's data. Never uninstall; never install a build signed with another key.
- **Never run `connectedDebugAndroidTest` against the owner's phone.** AGP uninstalls the app under test afterwards, and `debug` shares the `com.iron.breakfast` applicationId with `perf`, so it would wipe the diary.
- Safe manual run of the instrumented tests (`BreakfastDatabaseTest`; it uses its own throwaway databases):

  ```powershell
  .\gradlew.bat assembleDebug assembleDebugAndroidTest
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  adb shell am instrument -w com.iron.breakfast.test/androidx.test.runner.AndroidJUnitRunner
  adb install -r app/build/outputs/apk/perf/app-perf.apk
  ```

  The last step puts the perf build back. The test APK (`com.iron.breakfast.test`) can stay installed or be removed with `adb uninstall com.iron.breakfast.test`; that never touches the app's data.
- If storage fails to open, the app shows "Storage unavailable" with Try again, which runs the same open once more (`openStorage` in `AppDecisions.kt`); the cause goes to logcat (tag `Breakfast`), not to the screen. The database file is untouched.
