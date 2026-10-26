# Architecture

A single-module Android app (`app/`, package `com.iron.breakfast`): Kotlin, Jetpack Compose, Material 3, Room. There is no DI framework, no navigation library and no repository layer; this is deliberate for a one-person, local-only app. Read `PRODUCT.md` for product rules and `DESIGN.md` for the locked Monochrome design.

## Package map

Packages under `com.iron.breakfast`. Feature packages hold that feature's screens and the types and pure helpers only it uses; its state still lives in `BreakfastViewModel` (see State owners).

| Package / file | Responsibility |
| --- | --- |
| `app/MainActivity.kt` | Entry point. `MainActivity` applies Theme and Text size before its window exists, sets edge-to-edge and hosts `IronApp` inside `BreakfastTheme`. |
| `app/AppRoot.kt` | `IronApp`, the root composable: the diary pickers, the lifecycle hooks, which screen and overlays are drawn and in what order, and the root dialogs (discard food, notice, Replace diary, Delete all, Storage unavailable). Routing is a set of VM flags, not a nav graph. |
| `app/BreakfastViewModel.kt` | `BreakfastViewModel` (`AndroidViewModel` + `SavedStateHandle`) owns all screen state and every user action. |
| `app/SavedDrafts.kt` | Save and restore of the log draft, an open food form and the selected day in `SavedStateHandle`. |
| `AppDecisions.kt` | Pure routing decisions the VM makes (`topOverlay`, `exitLibraryTarget`, submit and review plans, the Undo window, `resolveSelectedDay`), unit-tested. Not yet split by feature. |
| `SuspendResult.kt` | `suspendRunCatching`, which rethrows `CancellationException`. |
| `domain/` | Pure Kotlin rules with no Android types (except `BreakfastEngine`). `ShorthandParser`/`ShorthandLexicon` (text to segments), `FoodMatcher` (local ranking, script rule), `LogPlanner` (draft rows and totals), `NutritionCalculator`, `NutritionShorthand`, `Barcode`, `Models`, `FoodCatalog` (bundled USDA and generic fixtures), `BuiltInCatalogSync`, `MealMarkers` (meal words in a draft), `FoodRules` (Library order, applying a food edit, default serving kcal). `BreakfastEngine` is the facade the VM calls; it wraps `BreakfastStore`. |
| `data/BreakfastStore.kt` | The only writer. Opens Room (`breakfast.db`), seeds and syncs the catalogue, holds the in-memory `StoreSnapshot` the UI reads. |
| `data/RoomStorage.kt` | Entities, DAOs, `BreakfastDatabase` (schema v6), migrations 1 to 6. Exported schemas: `app/schemas/debug` (used by every variant). |
| `data/DiaryExport.kt` | The diary file for Export diary and Import diary: DTOs, Room rows to file and back, and the import checks. Pure, unit-tested (see Export and import). |
| `data/OpenFoodFactsClient.kt` | The only network code (see OFF boundary). |
| `today/` | Daily (the Today tab) and Meal screens (`DailyScreen.kt`, `MealScreen.kt`) and `UndoOffer`. |
| `log/` | The Log page (`LogScreen.kt`) and `LogDraftView`, what it shows for one draft. |
| `library/` | The Library list, food detail and amount sheet, and the Edit-food screen with `FoodBarcodeField` (`LibraryScreen.kt`); the meal pickers (`LibraryMealPickers.kt`); the Library bar's reading of a draft (`LibraryInput.kt`). |
| `food/` | New food (`CreateFoodScreen.kt`), the food form state `FoodDraft` with its typed values (`FoodDraftFields`) and dirty check (`FoodDraft.kt`), and `FoodFormFocus`. |
| `entry/` | Entry detail, quick edit, full edit and the replacement food picker (`EntryScreens.kt`), and `EntryEditMode`. |
| `scan/` | The scanner, manual barcode and product review sheet (`ScanScreens.kt`), the product review values (`ProductReview.kt`) and `ScannerUnavailableReason`. |
| `search/` | Open Food Facts search and the Library's "No local match" screen (`SearchScreens.kt`). |
| `resolve/` | The unknown-food resolver (`ResolveUnknownScreen.kt`) and `LogResolveRequest`, a "Not in your foods" part sent to the Library. |
| `contribution/` | The Open Food Facts contribution screens (`ContributionScreens.kt`) and their pure helpers (`Contribution.kt`). |
| `settings/` | User settings in plain `SharedPreferences` (`settings`; `AppSettings.kt`: one typed field per setting, see Settings, with the pure decision for each) and the Settings list and pages (`SettingsScreen.kt`). |
| `transfer/` | `DiaryPicker` and the export and import messages (`DiaryTransfer.kt`). |
| `ui/Format.kt` | Shared display formatting: `MealSlot.label()`, `Double.plain()`, `Double.pretty()`. |
| `ui/screens/ScreenData.kt` | Pure UI mapping used by several features, unit-tested. |
| `ui/components/`, `ui/theme/` | Shared Compose components; Monochrome tokens (`Color.kt` mirrors DESIGN.md), type, shape, dimens. |

## State owners

All state lives in `BreakfastViewModel`, so it survives configuration changes (the app is portrait only; dark mode and font size still recreate the activity); very little survives process death.

| State | Owner | Survives process death |
| --- | --- | --- |
| Selected day | VM `selectedDayKey` and `followingToday` + `SavedStateHandle` (`following_today`; `selected_day` only for a picked past day) | Yes; a user following today reopens on the new today |
| Settings (every setting in the six groups, plus the last export time and when the export reminder last showed) | `AppSettings` (`SharedPreferences`), read once into the VM, written on each change; Theme and Text size are also read by `MainActivity` before its window exists | Yes |
| Open Settings page (a group, or Licences) | VM `settingsPage`, so a recreation (Theme, Text size) keeps it | No; Settings itself is not reopened after process death |
| Export reminder on Daily | VM `exportReminderVisible`: shown when due, hidden by Export, a successful export or leaving the app | No; it counts as shown when it appears |
| Open meal, selected entry | VM + `SavedStateHandle` (`meal_slot`, `entry_id`) | Yes |
| Route and overlay flags (Library, Log, scanner, sheets) | VM | No; the app reopens on the diary, with an open food form over it |
| Log draft text and committed flag | VM `logDraft`, one draft for Daily and Meal, separate from `input` (the Library bar and edit amount); only Add or clearing it removes it | Yes (`log_draft`, `log_draft_committed`; removed once the draft is empty) |
| Log pins, resolve queue | VM | No |
| New / edit food form (`FoodDraft`) | VM + `SavedStateHandle` (`food_form`, one JSON string) | Yes, the form reopens; not while resolving a part or a queue |
| Edit-sheet typed values (entry quick and full edit, Library amount sheet, manual barcode, scan review) and the scanner's "camera permission asked" flag | Composition `rememberSaveable`, so a configuration change (dark mode, font size) keeps them; a save error (`mealDraftError`) stays until one of them changes | No; these sheets do not reopen after process death |
| Pending Undo (`UndoOffer`) | VM; expires after the Undo setting's window (15 s by default; 5 s, 30 s, or none: "Until next action") from the log or delete, and always at the next log, edit or delete; a "Not right?" pick keeps it (`undoEndsAt`) | No |
| OFF search query and results, scan review fields | VM | No |
| OFF username and password | VM memory only | No, by design |
| Diary, foods, aliases, conversions, preferences | Room | Yes |

## Durable save path

Add (or any edit) runs in `viewModelScope`: `BreakfastViewModel` calls `BreakfastEngine` (for example `save`, `saveCustomAndLog`, `replaceEntry*`), which calls a `BreakfastStore` suspend function. The store takes one `Mutex`, switches to `Dispatchers.IO` under `NonCancellable` (every user-confirmed write; a cancel while waiting for the lock writes nothing), writes inside `database.withTransaction`, then reloads the whole `StoreSnapshot`. The VM wraps these calls in `suspendRunCatching`, which rethrows `CancellationException`, so a cancel is never shown as a failed save. "Saved" means that Room transaction committed. If the reload after it fails, the write still counts as saved: the snapshot is marked stale and reloaded on the next refresh, so a retry never logs the same food twice. Entry edits re-read the row inside the transaction and write it with `UPDATE ... WHERE id`, so a deleted entry is never re-created; saving an edited food keeps its stored archived state. A logged entry stores its own nutrition snapshot and conversion provenance, so later food edits never rewrite history. Migrations are additive and there is no destructive fallback.

## Export and import

Settings has Export diary and Import diary. Both use the system document picker (Storage Access Framework), so no permission is needed. The pickers are registered in `IronApp`, which is always composed, so a result that arrives after the activity was recreated still reaches the VM; it waits until storage is open and no write is running. The import picker offers `application/json`, `application/octet-stream` and `text/plain` (files from file managers, messengers and email). Export says "Diary exported." only after the file was checked, written, read back identical and parsed as an import would; on any failure the created document is deleted and "Could not export the diary." shows, so no empty or partial file is left looking like a backup. The file is one JSON document (`data/DiaryExport.kt`, kotlinx-serialization, strict: unknown or missing fields fail):

- `format` (`"breakfast-diary"`), `formatVersion` (1: the layout of this file), `schemaVersion` (the Room schema the rows came from, now 6), `exportedAtEpochMs`.
- `settings`: `dayStartHour` and `longDraft` (a `LongDraft` name).
- One array per Room table, every row and column as stored: `foods` (`foods`: bundled catalogue rows, custom, Open Food Facts, archived and personally edited foods), `foodAliases` (`food_aliases`), `servingConversions` (`serving_conversions`), `preferences` (`preferences`), `mealEntries` (`meal_entries`, each with its own nutrition snapshot). Rows are sorted by key, so the same diary always writes the same file. An alias, conversion or preference whose food is not stored is left out (the app never shows one).

Catalogue rows are included as stored rather than re-derived, so a built-in food the user edited or archived comes back exactly. Import reads the whole file (at most 32 MB) and checks all of it before anything is written: the format and its version, a schema version no newer than the app's, settings in range, every enum name known, every number finite and not negative (a nutrition basis and a serving size above zero), day keys real dates, ids unique (per table key), and each alias, conversion and preference naming a food in the file. An entry's food id need not name a food: the entry keeps its own name and snapshot. A file that fails any check changes nothing and shows "That file can't be imported."; the reason goes to logcat (tag `Breakfast`). A file that passes asks, in a dialog with one line ("This replaces entries and foods, not settings."), Cancel or Replace diary. Replace diary first writes the current diary, in the same format, to the safety copy `no_backup/diary-before-import.json` (see Recovery); if that fails, nothing is replaced and "That file can't be imported." shows. It then runs `BreakfastStore.replaceDiary`: one Room transaction deletes every table, inserts the file's rows (a clash aborts), then seeds and syncs the catalogue as at start, so a file from an older build gets the current catalogue. A failure rolls everything back. The settings in the file are checked but never applied: day start and long draft stay as they are on the phone. Undo, log pins and pending confirmations from the old diary are dropped. `DiaryImportTest` (androidTest) covers export, wipe, import with equal counts and checksum, and a failing insert that leaves the previous diary.

Settings, Data has two more switches and an action. **Safety copy** Keep (default) is the behaviour above; Don't keep writes no copy before Replace diary and deletes an existing one (also at once when chosen). **Export reminder** Weekly or Monthly shows "Export your diary." with Export on Daily when 7 or 30 days have passed since the latest of the last successful export (`last_export_ms`), the last time it was on screen (`export_reminder_shown_ms`) and the moment it was turned on from Never (`export_reminder_enabled_ms`), so the first one comes one interval after turning it on. It is drawn only on Daily itself (not under the Log page or an overlay) and counts as shown only once drawn. **Delete all data** opens a dialog ("This deletes all entries and foods.", the chip "Also settings and learned picks", off, then Cancel or Delete all). Delete all always writes the safety copy first, whatever Safety copy says (no copy, no delete), then runs `BreakfastStore.deleteAllData`: one Room transaction deletes every entry, food, alias and conversion and seeds and syncs the catalogue as at a first start; with the chip it also deletes the preferences, and every setting is cleared to its default. A failure rolls everything back and shows "Could not delete the diary." (`BreakfastStoreTest`, androidTest).

When the schema changes, bump `DIARY_SCHEMA_VERSION` with it (a unit test fails until it matches `app/schemas` and covers every table), and keep reading files from older schema versions.

## Settings

Library ⋮ → Settings is a list of six groups; each opens its own page (TopBar with the group name; Back and the arrow return to the list, the list's Back to the Library). Every default is the behaviour before the setting existed, and the stored day start and long draft keep their keys. Keys in `settings`:

| Group | Setting (key) | Default | Effect |
| --- | --- | --- | --- |
| Logging | Day starts at (`day_start_hour`) | 00:00 | See Day boundary. |
| Logging | Undo (`undo_window`) | 15 s | 5 s, 30 s or Until next action; `undoOpen` with `undoWindowMs`. |
| Logging | Meal (`meal_mode`), Breakfast/Lunch/Dinner until (`breakfast_until`, `lunch_until`, `dinner_until`), Before day start (`before_day_start`) | Ask; 12, 17, 22; Snacks | `automaticMeal` is the last fallback of `logTargetMealOf` on the Log page: Ask opens the meal menu, By time takes `mealByTime` (from "Day starts at" on: Breakfast, Lunch, Dinner, Snacks by the boundaries, each later than the one above; an hour before the day start gets the Before day start choice, Ask opening the menu), Last used the meal of the latest saved entry. In the Library the same meal is preselected: the food page's meal starts on it (`libraryPresetMeal`, Lunch for Ask) and the row "+" shows it selected among the four meals. |
| Logging | Count what I'm typing (`count_typing`) | On | Off: the add bar (`LogPlanner(countTyping = false)`) leaves out the part being typed until it is finished (separator, Done) or picked (pinned). Add finishes the draft first in both modes, so it saves what the finished draft counts. |
| Logging | Learn from my picks (`learn_from_picks`) | On | Off: `BreakfastEngine` ranks with no preferences and `reinforce` records nothing; stored preferences stay. |
| Display | Theme (`theme`) | System | API 31+: `UiModeManager.setApplicationNightMode` (the system applies it to the app and its launch window). Below 31: the activity's uiMode is overridden in `attachBaseContext` and the activity recreated on a change; the system launch window there keeps the phone's mode. |
| Display | Long draft (`long_draft`) | End | The composer's long draft. |
| Display | Macros on today (`show_macros`), Missing values note (`show_missing_note`) | Show | Hide drops Daily's macro block (and its note), or only the "?" note. |
| Display | Decimal mark (`decimal_mark`) | Point | Comma shows decimals with a comma (`DisplayDecimal`, `pretty()`); fields keep a point (`plain()`), input reads both. |
| Accessibility | Motion (`motion`) | System | Reduced makes `rememberMotionReduced` true, as "Remove animations" does. |
| Accessibility | Text size (`text_size`) | System | Larger overrides the activity's font scale with 1.15 × the phone's (`attachBaseContext`, recreated on a change), so `LocalDensity`, dialogs and sheets follow and `isLargeFont` applies as before. |
| Accessibility | Haptics (`haptics`) | Off | On: the standard CONFIRM (VIRTUAL_KEY below API 30) on Add and Undo; no permission. |
| Scanner | Flashlight at start (`flashlight_at_start`) | Off | On: the torch starts on when the camera has a flash unit. |
| Scanner | Look up unknown barcodes (`look_up_barcodes`) | On | Off: a barcode not in the Library opens New food with it; no network request. |
| Scanner | Contribute (`show_contribute`) | Show | Hide: no Contribute to Open Food Facts on food detail or after a scanned product. |
| Data | Export reminder (`export_reminder`; `last_export_ms`, `export_reminder_shown_ms`, `export_reminder_enabled_ms`) | Never | See Export and import. |
| Data | Safety copy (`keep_safety_copy`) | Keep | See Export and import. |
| About | Version, Licences | — | "0.1 (10)" from `BuildConfig`; Licences shows `assets/OFL.txt`. |

The diary file still carries only `dayStartHour` and `longDraft`, and Import still applies no settings.

## Day boundary

A diary day starts at the "Day starts at" hour (Settings, 00:00 to 23:00, default 00:00). `dayKeyFor(epochMs, zone, dayStartHour)` gives the day an instant belongs to: before the start hour it is still the previous date, read from the wall clock so a daylight-saving day is one day. It decides what "today" is (`todayKey`: the Daily header and Today button, Next, the date picker's last day) and so the day a new log lands on, since every log goes to the selected day. Stored entries keep their stored `dayKey`; changing the setting rewrites nothing. `resolveSelectedDay` (in `AppDecisions.kt`) moves the selection only on a return: when the activity starts again after a stop, or the VM is created or restored, a user following today lands on the new today and a picked past day stays. While the app is on screen the selection never moves; the 60-second loop only recomputes `todayKey`, so after the day turns the old day shows with the Today button, as after a swipe. Changing the setting counts as a return.

## OFF boundary

`OpenFoodFactsClient` uses `HttpURLConnection`, HTTPS only, 4.5 s timeouts, off the main thread. It is called only on an explicit action: submitted search (`cgi/search.pl`, then `search.openfoodfacts.org`), barcode lookup (`api/v3/product`), and the optional contribution from food detail (`cgi/session.pl` login, then `cgi/product_jqm2.pl`). There is no search-as-you-type and no automatic retry of writes. Search and barcode lookup are suspend functions: a new query or leaving the scanner cancels them, which disconnects the request in flight and skips the fallback search. Preflight and contribution run to the end. Only a transport failure (`IOException`) is "no connection"; an HTTP error other than 404 or an unreadable body is a server error. A contribution whose answer is lost after its body was written is reported as possibly sent, and Retry reads the product again before anything is resent. Data read from and sent to OFF passes one plausibility rule, `plausibleNutrition` (per 100 g or ml: kcal 0 to 1000, macros 0 to 100 g). The User-Agent is `Breakfast/<versionName> (daily food logger)`, from `BuildConfig.VERSION_NAME`. The OFF password is held in VM memory only, cleared when the submit starts, and **never persisted** (not to Room, not to `SavedStateHandle`). Core logging works offline.

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

Decided 2026-10-26 (P10): this key stays the permanent signing key, and the owner keeps a copy off this machine. There is no separate debug `applicationId`, so debug and perf share `com.iron.breakfast` and `breakfast.db`. The recovery path for the diary is Export diary: if the key is ever lost or replaced, export the diary first (or use the latest exported file), uninstall, install the new build, then Import diary and Replace diary.

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

- Safety copy: every Replace diary (unless Settings, Safety copy is Don't keep) and every Delete all data first saves the diary as it was to `/data/user/0/com.iron.breakfast/no_backup/diary-before-import.json` (`noBackupFilesDir`; only the latest, written to a temp file and renamed). It is never backed up and has no UI. On the non-debuggable perf build it cannot be read without root. To get it: install the debug build over perf (`adb install -r app-debug.apk`, same key and data), then `adb exec-out run-as com.iron.breakfast cat no_backup/diary-before-import.json > diary-before-import.json`, then put perf back. That file can be imported with Import diary.
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
