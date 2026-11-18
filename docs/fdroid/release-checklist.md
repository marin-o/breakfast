# F-Droid release checklist

Nothing here has been done yet: no tag, no fork, no merge request. The docs were read on 2026-11-02. Everything is waiting for the owner's go.

## Open problems, by severity

### Blocking or likely to cause rejection

1. **The app is too young for the policy.** The Inclusion Policy says an app "should have been actively developed for at least 3 months", unless it has proven users, its author maintains other F-Droid apps, or it was built for one event. The first commit is dated 2026-10-05, so three months is reached on 2027-01-05. None of the exceptions is known to apply. Submitting earlier means asking reviewers for leniency.
2. **Application ID: settled.** `mk.bosa.breakfast`, from the owner's own domain bosa.mk, as the Inclusion Policy advises.

### Must be finished before submitting

3. **Screenshots: done.** Five from the emulator with made-up sample data, in `images/phoneScreenshots/`.
4. **Release tags.** The Quick Start Guide requires a tag on each release commit that matches `versionName`, so `1.0` needs `v1.0`; the release workflow checks this.
5. **The changelog is named for today's versionCode.** `changelogs/53.txt` must match the released `versionCode` exactly. The code goes up with every phone build, so rename the file if the release ends up above 53.
6. **Wording needs the owner's approval.** All text in `fastlane/` is a draft. See "Metadata notes".

### Should be checked with a test build

7. **Build image compatibility is not proven.** The project needs Gradle 9.8.2, AGP 9.5.0, compileSdk 37 and, per the README, JDK 21. The docs read do not say which JDK, Gradle versions or SDK platforms the build image offers. Only `fdroid build` can answer this. The recipe has a commented-out JDK step in case it is needed.
8. **`gradlePluginPortal()` is in `settings.gradle.kts`.** The Inclusion Policy's list of trusted Maven repositories names Maven Central, Google Maven, OSS Sonatype, OSS JFrog, JitPack.io and Clojars, and not the Gradle Plugin Portal. Which plugins, if any, actually resolve from it has not been tested. If the scanner or a reviewer objects, try removing the line.
9. **Scanner results are unknown.** fdroidserver was not available to run. The files it could flag are in "Binaries in the repository".
10. **The wrapper's distribution checksum is not independently confirmed.** See "Gradle wrapper".

### Minor

11. **NOTICE names the transitive libraries now**, and the in-app Licences page shows NOTICE.
12. **The README is out of date on the SDK.** It says API 35 is needed, and `compileSdk` is now 37.
13. **APK size.** The unsigned release is 25.4 MB. `isMinifyEnabled` is false for release, and the minified `perf` build is 11.0 MB. Turning on R8 for release would save more than ABI splits would, but it needs testing. The Quick Start Guide only encourages ABI splits for large APKs, so the recipe has none.

## Audit results

### Gradle wrapper

- `gradle/wrapper/gradle-wrapper.jar` (48,749 bytes, SHA-256 `3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98`) is byte-identical to the wrapper jar inside a local Gradle 9.8.2 distribution. That was the only comparison made; Gradle's published checksum list was not contacted.
- `gradle-wrapper.properties` points at `gradle-9.8.2-bin.zip`, sets `distributionSha256Sum=21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607` and has `validateDistributionUrl=true`. The checksum was not compared against the real zip, because no copy was on hand.

### Dependencies

All 195 runtime modules resolve from Google Maven or Maven Central. Licences come from their POM files.

| Group | Licence | Notes |
|---|---|---|
| AndroidX, Compose, Room, CameraX | Apache-2.0 (one CameraX module adds BSD-3-Clause) | Source in AOSP. Includes prebuilt `libimage_processing_util_jni.so`, `libsurface_util_jni.so` and `libandroidx.graphics.path.so` |
| `io.github.zxing-cpp:android:3.1.1` | Apache-2.0 | Prebuilt native code, source below |
| `com.patrykandpatrick.vico` | Apache-2.0 | |
| Kotlin, kotlinx, JetBrains Compose | Apache-2.0 | |
| Guava, Dagger, Error Prone annotations, j2objc annotations, jsr305, javax and jakarta inject | Apache-2.0 | Pulled in by other libraries. The POM of auto-value-annotations and its parent state no licence, so check that one by hand (believed Apache-2.0) |
| Checker Framework `checker-qual` | MIT | Annotations |

Nothing from Google Play Services, Firebase, ML Kit or Crashlytics. The four DEX files contain no `com/google/android/gms`, `firebase`, `crashlytics`, `datatransport`, `com/google/android/play` or `billingclient` strings, and no strings from common trackers (Sentry, Facebook, AppsFlyer, Amplitude, Mixpanel). The five `analytics` matches are a TLD list inside a library and one unrelated `_analytics` string.

**zxing-cpp.** The AAR holds `libzxingcpp_android.so` for four ABIs (1.2 to 1.9 MB each), with source at https://github.com/zxing-cpp/zxing-cpp. The Inclusion Policy allows prebuilt FLOSS binaries from trusted Maven repositories including Maven Central, provided they are "freely licensed", and also says "Library dependencies must be built from source or come from a trusted Maven repository." So the docs don't require a source build and the recipe doesn't do one. A reviewer may still ask, because the Quick Start Guide says they check for pre-compiled libraries. In that case the recipe would need `srclibs` for zxing-cpp at the matching commit, `ndk`, and a `prebuild` that swaps the Maven artefact for the locally built library. That is real work: the library's Android wrapper would have to build inside this project.

Native libraries on arm64-v8a and x86_64 are aligned to 16 KB.

### Binaries in the repository

| File | Size | Origin |
|---|---|---|
| `gradle/wrapper/gradle-wrapper.jar` | 48,749 B | Gradle, see above |
| `app/src/main/assets/usda/usda-v9.sqlite.gzip` | 701,095 B | Generated by `tools/build_usda_catalogue.py` from the official FNDDS 2021-2023 and SR Legacy 2018-04 archives, each checked against a SHA-256. Data is CC0 per NOTICE |
| `app/src/main/res/font/geologica_{light,regular,medium,semibold}.ttf` | about 130 KB each | Geologica, OFL-1.1, licence text in `app/src/main/assets/OFL.txt` |

There are no tracked `.so`, `.aar`, `.apk`, `.dex` or `.class` files, and `tools/` holds only one Python script. The docs read don't say whether the scanner flags a `.gzip` database. The recipe has a commented-out `scanignore` for it. If it is flagged, the fallback is to explain in the merge request where it comes from.

### Network and privacy

- The only hosts in the app's code are `world.openfoodfacts.org` and `search.openfoodfacts.org`. There is no USDA network use at runtime.
- Permissions: `INTERNET` and `CAMERA`, with the camera optional.
- Search terms go out on a press, or as you type only after opting in. "Look up unknown barcodes" is on by default, and a barcode goes out only when the user scans one.
- The optional contribution sends the user's Open Food Facts login to that service. The password is saved, encrypted, only if the user chooses "Remember password".
- No analytics, crash reporting or update check was found.

### Anti-Features

None is proposed. The call on network services is a judgement that a reviewer could question.

- **Tracking:** lookups happen because the user asked for them, and no reporting exists. A reviewer could look at the barcode lookup being on by default; the description discloses it.
- **NonFreeNet, TetheredNet:** both are about promoting or depending entirely on a service. Core logging works offline and Open Food Facts is optional, with open data under ODbL.
- **NonFreeAssets:** the font is OFL and the bundled data is CC0. The icon is the project's own.
- **Ads, NonFreeDep, NonFreeAdd, KnownVuln, NSFW:** nothing in the repo points to these.

### Versions

- `versionCode = 64` and `versionName = "1.0"` are plain literals inside `android { defaultConfig }`, which the Quick Start Guide says is the normal location, read by regex. `fdroid checkupdates` should confirm it.
- The tag convention from the Quick Start Guide is `v<versionName>`, which gives `v1.0`.

### Signing block

`dependenciesInfo` has `includeInApk` and `includeInBundle` set to false. The unsigned release APK has no APK Signing Block at all. The signed `debug` and `perf` APKs in the local build output have only the v2 signature and verity padding blocks (`0x7109871a`, `0x42726577`), and no dependency info block.

### Metadata notes

| File | Length | Limit |
|---|---|---|
| `title.txt` | 30 | 50 |
| `short_description.txt` | 68 | 80 |
| `full_description.txt` | 1920 | 4000 |
| `changelogs/53.txt` | 25 | 500 |

- The Quick Start Guide asks for "no trailing dot" on the short description, so the draft's final full stop was removed.
- PRODUCT.md says not to invent logging-speed claims before the evaluation has been run. "Fast" in the title and short description is such a claim in tone. The full description makes no speed claim.
- The icon is 512x512, the size the docs give as usual for the Triple-T layout; they name none for Fastlane. It is the launcher background and foreground, cropped to the 72dp visible area of the 108dp canvas.
- `featureGraphic.png` is optional, and none was made. To appear on the client's Latest tab, the descriptions page asks for one screenshot or feature graphic, a What's New entry, and at least one of the listed items translated. No translation exists yet.

## Steps left, in order

1. Decide the application ID (problem 2). If it changes, update the recipe's file name and contents.
2. Decide when to submit, given the three-month line (problem 1).
3. Approve or change the wording in `fastlane/metadata/android/en-US/`.
4. Add at least two phone screenshots.
5. Commit the metadata and these drafts, then push.
6. Set the release's `versionName` and `versionCode` in `app/build.gradle.kts`. Rename `changelogs/53.txt` to match the code, and commit.
7. Tag that commit with `git tag v<versionName>` and push the tag.
8. Put the tag's full hash into `commit:` in the recipe, and update `versionName`, `versionCode`, `CurrentVersion` and `CurrentVersionCode`.
9. Fork `fdroiddata` on GitLab, make a branch named after the application ID, and copy the recipe to `metadata/<application id>.yml`.
10. Run the verification below, or let your fork's GitLab CI run it.
11. Commit as `New App: <application id>`, push, and open the merge request. Answer the reviewers' questions promptly.
12. After the merge, expect about 24 to 48 hours until the app appears, per the Quick Start Guide.

## Local verification

These commands follow the Quick Start Guide. They need Docker, about 2 GB of network traffic and about 5 GB of disk. They have not been run. Replace the ID if it changes.

```sh
git clone --depth=1 https://gitlab.com/fdroid/fdroidserver ~/fdroidserver
sudo docker run --rm -itu vagrant --entrypoint /bin/bash \
  -v ~/fdroiddata:/build:z \
  -v ~/fdroidserver:/home/vagrant/fdroidserver:Z \
  registry.gitlab.com/fdroid/fdroidserver:buildserver
```

Inside the container:

```sh
. /etc/profile
export PATH="$fdroidserver:$PATH" PYTHONPATH="$fdroidserver"
export JAVA_HOME=$(java -XshowSettings:properties -version 2>&1 > /dev/null | grep 'java.home' | awk -F'=' '{print $2}' | tr -d ' ')
cd /build
fdroid readmeta
fdroid rewritemeta mk.bosa.breakfast
fdroid checkupdates --allow-dirty mk.bosa.breakfast
fdroid lint mk.bosa.breakfast
fdroid build mk.bosa.breakfast
```

The project's own unsigned release build, from the repository root:

```sh
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/app-release-unsigned.apk`.

## Owner's other choice: who signs

The recipe leaves signing to F-Droid. The Quick Start Guide calls reproducible builds best practice, not a requirement, and warns that switching later forces users to reinstall because the signing key changes. Reproducible builds would mean a release key for the owner to keep, a published signed APK, and `Binaries` and `AllowedAPKSigningKeys` in the recipe. The Reproducible Builds page also lists things that could get in the way here: prebuilt native code pinned to the same version, `baseline.prof` files (present in this APK) and the VCS info block (also present). This has not been tried.
