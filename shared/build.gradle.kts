plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Pure Kotlin/JVM module. No Android APIs here on purpose: it holds the shared
// FlickBeam protocol types, models, and constants used by both the tv and phone apps.

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Exposed (api) so both apps can serialize protocol messages with the same
    // JSON configuration provided by this module.
    api(libs.kotlinx.serialization.json)
}
