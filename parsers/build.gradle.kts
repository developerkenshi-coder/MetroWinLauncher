import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The parsers of what the shell prints (dumpsys, sysfs, /proc, iw scan): plain Kotlin, no Android, nothing of the rest of the app.
// The first module cut out of :app (docs/progetti/moduli.md); the package stays com.metrowin.launcher.data, so no other file changed.
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

dependencies {
    testImplementation(libs.junit)
}
