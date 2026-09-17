plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// The FlickBeam Remote phone companion app. Discovers the TV over the network and
// talks to it using the shared FlickBeam protocol. Holds Send, Cast, and Remote.
android {
    namespace = "com.flickbeam.remote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.flickbeam.remote"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // The bundled TV apk (see copyTvApk below) must stay uncompressed inside this
    // app's own apk so it can be served with a known length and file descriptor.
    androidResources {
        noCompress += "apk"
    }
}

// Bundles the TV app's apk as a phone asset so "Install TV app" can serve it locally
// without needing the internet or a published release. Manual on purpose: run
// `./gradlew :tv:assembleDebug :phone:copyTvApk` whenever you want to refresh the
// bundled TV apk with a fresh build, instead of every phone build rebuilding TV too.
val copyTvApk by tasks.registering(Copy::class) {
    dependsOn(":tv:assembleDebug")
    from(project(":tv").layout.buildDirectory.dir("outputs/apk/debug"))
    include("*.apk")
    into("src/main/assets")
    // Keep this name in sync with TV_APK_ASSET_NAME in TvApkServer.kt.
    rename { "flickbeam-tv.apk" }
}

dependencies {
    // Shared FlickBeam protocol types and constants.
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)

    // Phone uses regular Material 3 (the TV app uses Compose for TV instead).
    implementation(libs.androidx.compose.material3)

    // Dependency injection (Hilt), same pattern as the TV app.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Coroutines for discovery and connection state flows.
    implementation(libs.kotlinx.coroutines.android)

    // OkHttp WebSocket client for the /control channel to the TV.
    implementation(libs.okhttp)

    // Tiny embedded HTTP server used only to serve the bundled TV apk to a browser
    // or sideload app on a TV that doesn't have FlickBeam installed yet.
    implementation(libs.nanohttpd)

    debugImplementation(libs.androidx.ui.tooling)
}
