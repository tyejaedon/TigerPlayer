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
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    alias(libs.plugins.kotlin.android)
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

android {
    namespace = "com.example.tigerplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.tigerplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.1"
        versionName = "0.1.1" // or "1.0-beta", or "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Spotify Manifest Placeholders
        manifestPlaceholders["redirectSchemeName"] = "tigerplayer"
        manifestPlaceholders["redirectHostName"] = "callback"

        // THE FIX: ABI Filters
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
        freeCompilerArgs = freeCompilerArgs + listOf("-Xannotation-default-target=param-property")
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    // THE FIX: Packaging Options
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
    // Compose & UI
    implementation(platform("androidx.compose:compose-bom:2026.03.00"))
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Media3 (The Heart of TigerPlayer)
    val media3_version = "1.9.3"
    implementation("androidx.media3:media3-exoplayer:$media3_version")
    implementation("androidx.media3:media3-session:$media3_version")
    implementation("androidx.media3:media3-ui:$media3_version")
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Networking & Storage
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Room (The Vault)
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // Hilt (DI)
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-android-compiler:2.59.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Spotify Integration
    implementation("com.spotify.android:auth:2.1.1")
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))

    implementation("com.google.code.gson:gson:2.13.2")
    //TESTING DEPENDECIES
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}