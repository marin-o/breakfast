# Architecture

A single-module Android app (`app/`, package `com.iron.breakfast`): Kotlin, Jetpack Compose, Material 3, Room. There is no DI framework, no navigation library and no repository layer; this is deliberate for a one-person, local-only app. Read `PRODUCT.md` for product rules and `DESIGN.md` for the locked Monochrome design.

## Package map

| Package / file | Responsibility |
| --- | --- |
| `MainActivity.kt` | Entry point. `MainActivity` sets edge-to-edge and hosts `IronApp` inside `BreakfastTheme`. `BreakfastViewModel` (`AndroidViewModel` + `SavedStateHandle`) owns all screen state and every user action. Routing is a set of VM flags, not a nav graph. |
| `domain/` | Pure Kotlin rules with no Android types (except `BreakfastEngine`). `ShorthandParser`/`ShorthandLexicon` (text to segments), `FoodMatcher` (local ranking, script rule), `LogPlanner` (draft rows and totals), `NutritionCalculator`, `NutritionShorthand`, `Barcode`, `Models`, `FoodCatalog` (bundled USDA and generic fixtures), `BuiltInCatalogSync`. `BreakfastEngine` is the facade the VM calls; it wraps `BreakfastStore`. |
| `data/BreakfastStore.kt` | The only writer. Opens Room (`breakfast.db`), seeds and syncs the catalogue, holds the in-memory `StoreSnapshot` the UI reads. |
| `data/RoomStorage.kt` | Entities, DAOs, `BreakfastDatabase` (schema v6), migrations 1 to 6. Exported schemas: `app/schemas/debug` (used by every variant). |
| `data/OpenFoodFactsClient.kt` | The only network code (see OFF boundary). |
| `FoodDraft.kt` | New food / edit food form state, its typed values (`FoodDraftFields`) and the dirty check. |
| `AppDecisions.kt` | Pure routing decisions the VM makes (`topOverlay`, `exitLibraryTarget`, submit and review plans), unit-tested. |
| `SavedDrafts.kt` | Save and restore of the log draft and an open food form in `SavedStateHandle`. |
| `ui/screens/` | One file per surface: Daily (the Today tab), Log, Meal, Library, Entry, Search, Scan, CreateFood, plus `ScreenData.kt` (pure UI mapping, unit-tested). |
| `ui/components/`, `ui/theme/` | Shared Compose components; Monochrome tokens (`Color.kt` mirrors DESIGN.md), type, shape, dimens. |

## State owners

All state lives in `BreakfastViewModel`, so it survives rotation; very little survives process death.

| State | Owner | Survives process death |
| --- | --- | --- |
| Selected day | VM + `SavedStateHandle` (`selected_day`) | Yes |
| Open meal, selected entry | VM + `SavedStateHandle` (`meal_slot`, `entry_id`) | Yes |
| Route and overlay flags (Library, Log, scanner, sheets) | VM | No; the app reopens on the diary, with an open food form over it |
| Log draft text and committed flag | VM `logDraft`, one draft for Daily and Meal, separate from `input` (the Library bar and edit amount); only Add or clearing it removes it | Yes (`log_draft`, `log_draft_committed`; removed once the draft is empty) |
| Log pins, resolve queue | VM | No |
| New / edit food form (`FoodDraft`) | VM + `SavedStateHandle` (`food_form`, one JSON string) | Yes, the form reopens; not while resolving a part or a queue |
| Entry edit typed values | Composition `remember` | Not even rotation (MOB-026) |
| Pending Undo | VM | No |
| OFF search query and results, scan review fields | VM | No |
| OFF username and password | VM memory only | No, by design |
| Diary, foods, aliases, conversions, preferences | Room | Yes |

## Durable save path

Add (or any edit) runs in `viewModelScope`: `BreakfastViewModel` calls `BreakfastEngine` (for example `save`, `saveCustomAndLog`, `replaceEntry*`), which calls a `BreakfastStore` suspend function. The store takes one `Mutex`, switches to `Dispatchers.IO`, writes inside `database.withTransaction`, then reloads the whole `StoreSnapshot`. "Saved" means that Room transaction committed. A logged entry stores its own nutrition snapshot and conversion provenance, so later food edits never rewrite history. Migrations are additive and there is no destructive fallback.

## OFF boundary

`OpenFoodFactsClient` uses `HttpURLConnection`, HTTPS only, 4.5 s timeouts, off the main thread. It is called only on an explicit action: submitted search (`cgi/search.pl`, then `search.openfoodfacts.org`), barcode lookup (`api/v3/product`), and the optional contribution from food detail (`cgi/session.pl` login, then `cgi/product_jqm2.pl`). There is no search-as-you-type and no automatic retry of writes. The User-Agent is `Breakfast/<versionName> (daily food logger)`, from `BuildConfig.VERSION_NAME`. The OFF password is held in VM memory only, cleared when the submit starts, and **never persisted** (not to Room, not to `SavedStateHandle`). Core logging works offline.

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

`android:allowBackup="true"` with no backup rules, so Android auto-backup may include `breakfast.db`. Whether to keep, restrict or disable it is undecided (MOB-012, open). Do not rely on it as the only copy.

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
- If storage fails to open, the app shows an error screen with no retry (MOB-019); the database file is untouched, so force-stop and reopen.
