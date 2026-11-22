import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The SDK of the live tiles (docs/progetti/tile-sdk.md): one object, MetroTile, that another app calls to put a title, a text and a number on its
// own tile of MetroWin Launcher. Apache-2.0 (the launcher is GPL-3.0): an app that is not free can use it.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.metrowin.launcher.tilesdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        // The permission and the <queries> of src/main/AndroidManifest.xml go into every app that uses the library.
        consumerProguardFiles("consumer-rules.pro")
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
