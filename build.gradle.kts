buildscript {
    dependencies {
        // AGP's built-in Kotlin compiles with this version instead of the 2.2.10 AGP depends on.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.5.0" apply false
    id("com.google.devtools.ksp") version "2.3.13" apply false
    id("androidx.room") version "2.8.5" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
}
