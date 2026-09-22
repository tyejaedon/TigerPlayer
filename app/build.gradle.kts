import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import java.util.Properties
import java.io.FileInputStream

// 1. Locate and open the Vault
val secretsFile = rootProject.file("secrets.properties")
val secrets = Properties()
if (secretsFile.exists()) {
    secrets.load(FileInputStream(secretsFile))
} else {
    // Optional warning if someone else clones your repo
    logger.warn("No secrets.properties found! Spotify API calls will fail.")
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// 🔥 THE FIX: Using the explicit AGP 9.0+ ApplicationExtension to bypass the deprecation
configure<ApplicationExtension> {
    namespace = "com.tigerplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tigerplayer"
        minSdk = 29
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = 2
        versionName = "2.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Spotify Manifest Placeholders
        manifestPlaceholders["redirectSchemeName"] = "tigerplayer"
        manifestPlaceholders["redirectHostName"] = "callback"
        manifestPlaceholders["redirectPathPattern"] = ".*"

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
        }

        val clientId = secrets.getProperty("SPOTIFY_CLIENT_ID") ?: "MISSING_ID"
        val lastfmApiKey = secrets.getProperty("LASTFM_API_KEY") ?: "MISSING_API_KEY"
        val youtubeApiKey = secrets.getProperty("YOUTUBE_API_KEY") ?: "MISSING_YOUTUBE_KEY"

        // BuildConfig Fields
        // NOTE: No SPOTIFY_CLIENT_SECRET field. Spotify auth uses Authorization Code + PKCE,
        // which requires no client secret. Never add one back here (issue #46).
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$clientId\"")
        buildConfigField("String", "LASTFM_API_KEY", "\"$lastfmApiKey\"")
        buildConfigField("String", "YOUTUBE_API_KEY", "\"$youtubeApiKey\"")
    }

    // FOSS/F-Droid distributability (see docs/system-review.md and CONTRIBUTING.md):
    //   - `foss`: no vendored proprietary AAR, no Spotify App Remote dependency. Builds and
    //     runs fully with zero external API keys. This is the flavor submitted to F-Droid.
    //   - `full`: current feature set, including Spotify App Remote playback.
    // Spotify App Remote access is guarded behind the `SpotifyAppRemoteClient` interface
    // (app/src/main/.../data/repository/SpotifyAppRemoteClient.kt), backed by a real
    // implementation in src/full and a no-op stub in src/foss, so common code never imports
    // the proprietary AAR directly.
    flavorDimensions += "distribution"
    productFlavors {
        create("foss") {
            dimension = "distribution"
            buildConfigField("boolean", "SPOTIFY_APP_REMOTE_AVAILABLE", "false")
        }
        create("full") {
            dimension = "distribution"
            buildConfigField("boolean", "SPOTIFY_APP_REMOTE_AVAILABLE", "true")
        }
    }

    buildTypes {
        getByName("release") { // Safely scoped inside the new extension
            isMinifyEnabled = true
            isShrinkResources = true
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


    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Required by Robolectric so JVM tests can resolve Android resources.
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        // Exposes the committed Room schema JSON to MigrationTestHelper (issue #43 / #74).
        // Added to debug so Robolectric JVM unit tests and androidTest can load schemas,
        // while ensuring schemas are never packaged into release builds.
        getByName("debug") {
            assets.srcDir("$projectDir/schemas")
        }
        getByName("test") {
            assets.srcDir("$projectDir/schemas")
        }
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
    }
}

// Room schema export - required so migrations can be verified/auto-generated
// against a committed baseline (see app/schemas). See issue #43.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // --- Compose & UI (Using Version Catalog) ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.animation)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.unit)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.animation)

    // Activity & Navigation
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.foundation.layout)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.glance)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.window)

    // --- Media3 (The Heart of TigerPlayer) ---
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.animation)

    // --- Networking & Storage ---
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.gson)
    implementation(libs.jtransforms)

    implementation(libs.kotlin.youtubeextractor)
    // --- Room (The Vault) ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)


    // --- Hilt (Dependency Injection) ---
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // --- Image Loading ---
    implementation(libs.coil.compose)

    // --- YouTube Player ---
    implementation(libs.youtubePlayer)

    // --- Spotify Integration ---
    // Vendored proprietary AAR + auth SDK are `full`-only so the `foss` flavor never depends
    // on, or ships, proprietary binaries (F-Droid requirement). See
    // data/repository/SpotifyAppRemoteClient.kt for the flavor-guarded abstraction.
    "fullImplementation"(libs.auth)
    "fullImplementation"(files("libs/spotify-app-remote-release-0.8.0.aar"))

    // --- Google Play Services ---
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)

    // --- Testing ---
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    // Backs BundledSQLiteDriver for Robolectric migration tests (see libs.versions.toml).
    testImplementation(libs.androidx.sqlite.bundled)
    // Robolectric provides a real android.net.Uri on the JVM, so URI/signing logic can be unit
    // tested without a device. Test-only; ships nothing.
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.benchmark.macro.junit4)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(libs.androidx.window.testing)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.leakcanary.android)
}


// Robolectric's FileDescriptorInterceptor reflectively reaches into the JDK-internal
// jdk.internal.access.SharedSecrets class while shimming android.os.SharedMemory on JDK 17+.
// That package isn't exported to unnamed modules by default, so every Robolectric test fails
// during environment setup with "Failed to interact with raw FileDescriptor internals" unless
// the test JVM explicitly opens it up. See issue #113 and robolectric/robolectric#11434.
tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
    )
}
