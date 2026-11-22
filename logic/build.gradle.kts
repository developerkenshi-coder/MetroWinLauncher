import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The plain-Kotlin part of the launcher's data package: the calculator, the date and time parser, the sync encryption, formats and small rules.
// No Android, nothing of the rest of the app (docs/progetti/moduli.md); the package stays com.metrowin.launcher.data, so no other file changed.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}
