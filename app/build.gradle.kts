import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.metrowin.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.metrowin.launcher"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "26.3S"
    }

    // The key of the official release comes from keystore.properties in the root of the project (never committed: it is in
    // .gitignore): storeFile, storePassword, keyAlias and keyPassword. Without that file the release build stays unsigned.
    val keystoreFile = rootProject.file("keystore.properties")
    val official = keystoreFile.takeIf { it.exists() }?.let { file ->
        val keys = Properties().apply { file.inputStream().use { load(it) } }
        signingConfigs.create("official") {
            storeFile = rootProject.file(keys.getProperty("storeFile"))
            storePassword = keys.getProperty("storePassword")
            keyAlias = keys.getProperty("keyAlias")
            keyPassword = keys.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (official != null) signingConfig = official
        }
        // The release build (shrunk and optimized, nothing of the debugging in it: the one to live with, it draws less battery than the
        // debug build) signed with the debug key, so that it installs over the debug build without losing anything. The release build
        // itself stays unsigned, for the key of whoever publishes it.
        create("optimized") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // F-Droid's scanner reports the dependency list the plugin signs into the APK, and it keeps the build from being reproducible.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            // Robolectric needs the merged resources and manifest for the screenshot tests.
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
