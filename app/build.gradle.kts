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
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}
kotlin {
    jvmToolchain(17)
}

// 🔥 THE FIX: Using the explicit AGP 9.0+ ApplicationExtension to bypass the deprecation
configure<ApplicationExtension> {
    namespace = "com.example.tigerplayer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.tigerplayer"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
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
        val clientSecret = secrets.getProperty("SPOTIFY_CLIENT_SECRET") ?: "MISSING_SECRET"

        // BuildConfig Fields
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$clientId\"")
        buildConfigField("String", "SPOTIFY_CLIENT_SECRET", "\"$clientSecret\"")
        buildConfigField("String", "LASTFM_API_KEY", "\"$lastfmApiKey\"")
    }

    buildTypes {
        getByName("release") { // Safely scoped inside the new extension
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


    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}




dependencies {
    // --- Compose & UI (Using Version Catalog) ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.animation)
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
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.glance)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Remote Compose (The Alpha Library)
    implementation(libs.androidx.compose.remote.creation.compose)

    // --- Media3 (The Heart of TigerPlayer) ---
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.palette.ktx)

    // --- Networking & Storage ---
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.gson)

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

    // --- Spotify Integration ---
    implementation(libs.auth)
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))

    // --- Google Play Services ---
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)

    // --- Testing ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// =========================================================================
// THE FIREWALL: KSP & KOTLIN VERSION SYNCHRONIZATION
// =========================================================================
// This forces all alpha libraries (like remote-creation-compose) to back down
// and use the exact stable version of Kotlin that KSP is expecting.
configurations.all {
    resolutionStrategy {
        // Hardcoded to 2.2.20 to match your KSP compiler exactly
        val kotlinVersion = "2.2.20"
        force("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
        force("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion")
    }
}