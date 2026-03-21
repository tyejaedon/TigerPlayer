// Top-level build file where you can add configuration options common to all sub/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false

    // Add the KSP plugin here.
    // Replace "2.0.21" with whatever Kotlin version your project is using.
    id("com.google.devtools.ksp") version "2.3.2" apply false

    id("com.google.dagger.hilt.android") version "2.59.2" apply false
    alias(libs.plugins.kotlin.android) apply false
}