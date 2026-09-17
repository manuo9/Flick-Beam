plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.flickbeam.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.flickbeam.tv"
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
    debugImplementation(libs.androidx.ui.tooling)

    // Compose for TV — D-pad aware Material components (Card, Text, MaterialTheme) with
    // focus-driven styling built in. Regular Compose Foundation (already pulled in above)
    // provides the lazy list/grid APIs; as of the stable TV release, those gained
    // TV-focus-restoration support directly, so a separate tv-foundation dependency for
    // list/grid usage is no longer needed.
    implementation(libs.androidx.tv.material)

    // Dependency injection (Hilt) — wires ViewModels to their repositories, and
    // repositories/PermissionManager to the application Context, so no component has to
    // manually pass Context down through constructors.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Image loading (Coil) for the built-in image viewer — downsamples large photos so
    // they don't cause out-of-memory crashes on low-end TV hardware.
    implementation(libs.coil.compose)

    // Media3 (ExoPlayer) for the built-in video player: hardware-accelerated playback
    // of local files and phone-streamed URLs, with sidecar subtitle support.
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // Coroutines for the networking layer (server lifecycle, state flows).
    implementation(libs.kotlinx.coroutines.android)

    // Tiny embedded HTTP + WebSocket server for the FlickBeam protocol: NanoWSD hosts
    // the /control WebSocket, NanoHTTPD the /upload endpoint.
    implementation(libs.nanohttpd)
    implementation(libs.nanohttpd.websocket)
}
