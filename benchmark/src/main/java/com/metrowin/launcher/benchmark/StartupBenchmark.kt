package com.metrowin.launcher.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Start of the launcher from cold, with no compilation (what a fresh install gets) and with the baseline profile (what a store install gets).
 * The thresholds the CI would apply are in docs/progetti/macrobenchmark.md. Needs a phone or an emulator; not run by `./gradlew test`.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    private fun start(mode: CompilationMode) = rule.measureRepeated(
        packageName = "com.metrowin.launcher",
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        iterations = 10,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun coldStartWithoutCompilation() = start(CompilationMode.None())

    @Test
    fun coldStartWithTheBaselineProfile() = start(CompilationMode.Partial())
}
