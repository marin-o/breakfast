# Breakfast prototype

This is a deliberately small, offline Android spike for testing fast breakfast logging. The single Breakfast screen accepts shorthand such as `2 tbsp oats, half a tbsp peanut butter, banana`, highlights detected quantities while typing, and only commits entries when Done is pressed. Matching is deterministic and local: explicit aliases and modifiers constrain candidates, then name/category compatibility and explicit frequency/recency preferences rank the fixture catalogue. Automatic guesses never reinforce a preference; Confirm, an alternative, or a catalogue search selection reinforces once.

Saved entries and totals remain visible after the temporary feedback window and are reloaded from local storage on app relaunch. Each entry keeps the original amount/unit, a nutrition snapshot, and serving-conversion provenance. Choosing an alternative or a Search result replaces the targeted saved entry using its original amount/unit, recalculates its snapshot, and records one explicit preference. The post-save alternatives, Search, and Confirm controls are temporary and disappear after 30 seconds or an app restart. Unresolved comma segments stay in the input for correction instead of being silently discarded. The catalogue includes competing milk and peanut-butter records plus English and Cyrillic custom fixtures. Remote providers, barcode, onboarding, custom-food creation, recipes, and conversational parsing are intentionally outside this spike.

## Build locally

Tool downloads and caches belong under `_stack`; the source tree and debug APK stay in normal project paths. Use JDK 21 at `<jdk21>` if the machine default is Java 8. A local Gradle distribution can be placed at `_stack/gradle/gradle-8.9/bin/gradle.bat`, and an Android SDK can be placed at `_stack/android-sdk`.

```powershell
$env:JAVA_HOME = '<jdk21>'
$env:ANDROID_SDK_ROOT = './_stack/android-sdk'
$env:GRADLE_USER_HOME = './_stack/gradle-home'
& './_stack/gradle/gradle-8.9/bin/gradle.bat' testDebugUnitTest assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. Install it with the existing platform-tools `adb` when a device is attached.

## Focused checks

`BreakfastDomainTest` covers quantity-first and quantity-after parsing, fractions, comma segments, modifier-constrained matching in English and Cyrillic, serving conversion and nutrition calculation, and persistence codec round trips. The app has no network or API dependency.

## Commit messages

The repository uses a local `commit-msg` hook at `.githooks/commit-msg`, enabled through repository-local `core.hooksPath`. It accepts Conventional Commit prefixes such as `feat:`, `fix:`, `docs:`, `test:`, `chore:`, and `build:` with optional scopes or `!`, and rejects other messages before commit creation. No global Git configuration is changed.
