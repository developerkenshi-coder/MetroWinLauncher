import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// An example app for the SDK of the live tiles (docs/progetti/tile-sdk.md): install it next to MetroWin Launcher, pin it on Start, press "publish".
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.metrowin.sampletile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.metrowin.sampletile"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":tile-sdk"))
}
