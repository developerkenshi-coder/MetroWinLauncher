import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Macrobenchmarks of the launcher (docs/progetti/macrobenchmark.md). They need a phone or an emulator and are NOT run by CI or by `./gradlew test`:
//   ./gradlew :benchmark:connectedOptimizedAndroidTest
// The app side is the "optimized" build type (release code, debug key); its manifest in app/src/optimized makes it profileable.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.metrowin.launcher.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
    // The test app measures another app, so it carries its own instrumentation.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("optimized") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    // Only the variant that measures the optimized app: the others would measure a debug build, whose numbers mean nothing.
    beforeVariants(selector().all()) { it.enable = it.buildType == "optimized" }
}
