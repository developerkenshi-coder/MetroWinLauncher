package com.metrowin.launcher

import com.metrowin.launcher.data.AppUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The update from GitHub: which version is newer, what a release says, what a checksum file says. */
class AppUpdateTest {
    @Test
    fun versionsAreComparedByTheirNumbersAndTheLetterDoesNotCount() {
        assertEquals(listOf(26, 3), AppUpdate.numbers("v26.3S"))
        assertEquals(listOf(26, 3), AppUpdate.numbers("26.3S"))
        assertNull(AppUpdate.numbers("beta"))
        assertTrue(AppUpdate.isNewer("v26.4S", "26.3S"))
        assertTrue(AppUpdate.isNewer("27.0S", "26.9S"))
        assertTrue(AppUpdate.isNewer("26.10S", "26.9S"))
        assertFalse(AppUpdate.isNewer("26.3S", "26.3S"))
        assertFalse(AppUpdate.isNewer("26.2S", "26.3S"))
        assertFalse(AppUpdate.isNewer("nonsense", "26.3S"))
    }

    private fun release(tag: String = "v26.4S", draft: Boolean = false, pre: Boolean = false, apk: String = "https://github.com/developerkenshi-coder/MetroWinLauncher/releases/download/$tag/MetroWinLauncher-$tag.apk") = """
        {"tag_name":"$tag","draft":$draft,"prerelease":$pre,"assets":[
          {"name":"MetroWinLauncher-$tag.apk","size":2815397,"browser_download_url":"$apk"},
          {"name":"SHA256SUMS","size":93,"browser_download_url":"https://github.com/developerkenshi-coder/MetroWinLauncher/releases/download/$tag/SHA256SUMS"}
        ]}
    """.trimIndent()

    @Test
    fun aReleaseGivesItsVersionItsApkAndItsChecksums() {
        val found = AppUpdate.parseLatest(release())!!
        assertEquals("26.4S", found.version)
        assertEquals(2815397L, found.apkSize)
        assertTrue(found.apkUrl.endsWith("MetroWinLauncher-v26.4S.apk"))
        assertTrue(found.sumsUrl!!.endsWith("/SHA256SUMS"))
    }

    @Test
    fun draftsTestVersionsAndFilesFromOtherPlacesAreNotTaken() {
        assertNull(AppUpdate.parseLatest(release(draft = true)))
        assertNull(AppUpdate.parseLatest(release(pre = true)))
        assertNull(AppUpdate.parseLatest(release(apk = "https://evil.example.com/MetroWinLauncher.apk")))
        assertNull(AppUpdate.parseLatest(release(tag = "latest")))
        assertNull(AppUpdate.parseLatest("not json"))
    }

    @Test
    fun theNotesOfAReleaseAreReadAsPlainLines() {
        val md = "## What is new\r\n- **Weather** tile\r\n- Fix: [details](https://x.example/y)\r\n\r\n\r\n\r\nUse `Settings`"
        val text = AppUpdate.notesText(md)
        assertEquals("What is new\n\u2022 Weather tile\n\u2022 Fix: details\n\nUse Settings", text)
        assertEquals(AppUpdate.MAX_NOTES, AppUpdate.notesText("x".repeat(10_000)).length)
        val json = release().replace("\"assets\"", "\"body\":\"- one\\n- two\",\"assets\"")
        assertEquals("\u2022 one\n\u2022 two", AppUpdate.parseLatest(json)!!.notes)
    }

    @Test
    fun theChecksumOfAFileIsReadFromTheSumsFile() {
        val hash = "a".repeat(64)
        val other = "B".repeat(64)
        val sums = "$other  other.apk\n$hash *MetroWinLauncher-26.4S.apk\n"
        assertEquals(hash, AppUpdate.checksumFor(sums, "MetroWinLauncher-26.4S.apk"))
        assertEquals(other.lowercase(), AppUpdate.checksumFor(sums, "other.apk"))
        assertNull(AppUpdate.checksumFor(sums, "missing.apk"))
        assertNull(AppUpdate.checksumFor("short  MetroWinLauncher-26.4S.apk", "MetroWinLauncher-26.4S.apk"))
    }
}
