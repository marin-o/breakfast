/** The repository's NOTICE as an app asset, for Settings, About, Licences: one text, in one place. */
abstract class NoticeAsset : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val notice: RegularFileProperty
    @get:OutputDirectory abstract val assets: DirectoryProperty
    @TaskAction fun copy() { notice.get().asFile.copyTo(assets.get().file("NOTICE.txt").asFile, overwrite = true) }
}

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("androidx.room")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "mk.bosa.breakfast"
    compileSdk = 37

    defaultConfig {
        applicationId = "mk.bosa.breakfast"
        minSdk = 26
        targetSdk = 35
        // Bump versionCode by 1 for every build installed on the phone; versionName stays 0.x until a release.
        versionCode = 64
        // A literal, as F-Droid reads it from this file; the release workflow checks that the tag (v1.0) matches it.
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    // The release key comes from the environment (the GitHub release workflow's secrets); without it a release APK is
    // unsigned, as F-Droid builds and signs its own. The keystore never enters the repository.
    val releaseKeystore = System.getenv("BREAKFAST_KEYSTORE")?.let(::file)?.also { check(it.isFile) { "BREAKFAST_KEYSTORE: no file at $it" } }
    signingConfigs {
        if (releaseKeystore != null) create("release") {
            storeFile = releaseKeystore
            storePassword = System.getenv("BREAKFAST_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("BREAKFAST_KEY_ALIAS")
            keyPassword = System.getenv("BREAKFAST_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
        // The release build signed with the debug key, so it installs over the phone's debug-signed app.
        create("perf") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    // F-Droid objects to Google's encrypted dependency list in the APK's signing block.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    // The synthetic year and timing helpers, shared by the JVM benchmark test and the device benchmark (MOB-028).
    sourceSets {
        getByName("test").kotlin.directories += "src/benchShared/java"
        getByName("androidTest").kotlin.directories += "src/benchShared/java"
    }
}

androidComponents {
    onVariants { variant ->
        val task = tasks.register<NoticeAsset>("${variant.name}NoticeAsset") { notice.set(rootProject.layout.projectDirectory.file("NOTICE")) }
        variant.sources.assets?.addGeneratedSourceDirectory(task, NoticeAsset::assets)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("io.github.zxing-cpp:android:3.1.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("com.patrykandpatrick.vico:compose-m3:3.3.1")

    ksp("androidx.room:room-compiler:2.8.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.21")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.21")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect:2.4.21")
    testImplementation("androidx.room:room-testing:2.8.5")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.room:room-testing:2.8.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
}

room {
    schemaDirectory("debug", "$projectDir/schemas/debug")
    schemaDirectory("debugUnitTest", "$projectDir/schemas/debug")
    schemaDirectory("debugAndroidTest", "$projectDir/schemas/debug")
    schemaDirectory("perf", "$projectDir/schemas/debug")
    schemaDirectory("release", "$projectDir/schemas/debug")
}
