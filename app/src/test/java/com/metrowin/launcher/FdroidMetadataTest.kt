package com.metrowin.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The text F-Droid shows lives in `fastlane/metadata/android` (docs/fdroid/GUIDA.md): it is checked against the limits of the store and
 * against the build file, so a changelog with the wrong name or a text too long is found here and not by the F-Droid robot.
 */
class FdroidMetadataTest {
    private val root = File("../fastlane/metadata/android")
    private val locales = root.listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }
    private val versionCode = Regex("versionCode = (\\d+)").find(File("build.gradle.kts").readText())!!.groupValues[1].toInt()

    @Test
    fun everyLanguageHasItsTextsWithinTheLimits() {
        assertTrue(locales.size >= 6)
        locales.forEach { dir ->
            fun text(name: String) = File(dir, name).also { assertTrue("${dir.name}/$name", it.isFile) }.readText().trim()
            assertTrue("${dir.name} title", text("title.txt").length in 1..30)
            assertTrue("${dir.name} short description", text("short_description.txt").length in 1..80)
            assertTrue("${dir.name} full description", text("full_description.txt").length in 1..4000)
        }
    }

    @Test
    fun changelogsAreNamedByVersionCodeAndShortEnough() {
        locales.forEach { dir ->
            File(dir, "changelogs").listFiles().orEmpty().forEach { file ->
                val code = file.name.removeSuffix(".txt").toIntOrNull()
                assertTrue("${dir.name}/${file.name} is not <versionCode>.txt", code != null && file.name.endsWith(".txt"))
                assertTrue("${dir.name}/${file.name} is of a version that does not exist yet", code!! <= versionCode)
                // F-Droid cuts a changelog at 500 characters (not bytes).
                assertTrue("${dir.name}/${file.name} has ${file.readText().trim().length} characters", file.readText().trim().length in 1..500)
            }
        }
    }

    @Test
    fun theDefaultLanguageHasEveryChangelogOfTheOthers() {
        val default = File(root, "en-US/changelogs").list().orEmpty().toSet()
        locales.forEach { dir -> assertEquals(dir.name, emptySet<String>(), File(dir, "changelogs").list().orEmpty().toSet() - default) }
    }

    @Test
    fun screenshotsAreTheSameInEveryLanguageThatHasThem() {
        val names = locales.associate { it.name to File(it, "images/phoneScreenshots").list().orEmpty().sorted() }.filterValues { it.isNotEmpty() }
        assertTrue(names["en-US"].orEmpty().size >= 2)
        names.forEach { (language, files) -> assertEquals(language, names["en-US"], files) }
        assertTrue(File(root, "en-US/images/icon.png").isFile)
    }
}
