package com.metrowin.launcher

import com.metrowin.launcher.data.Recordings
import com.metrowin.launcher.recorder.AudioTools
import com.metrowin.launcher.recorder.Transcriber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecorderImportTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun theExtensionComesFromTheNameThenFromTheType() {
        assertEquals("mp3", Recordings.extensionFor("Song.MP3", null))
        assertEquals("opus", Recordings.extensionFor("voice.opus", "audio/ogg"))
        assertEquals("ogg", Recordings.extensionFor("download", "audio/ogg"))
        assertEquals("wav", Recordings.extensionFor("x.bin", "audio/x-wav; codecs=1"))
        assertEquals("m4a", Recordings.extensionFor(null, "audio/x-m4a"))
        assertEquals("3gp", Recordings.extensionFor("clip", "audio/3gpp"))
        assertNull(Recordings.extensionFor("notes.txt", "text/plain"))
        assertNull(Recordings.extensionFor(null, null))
    }

    @Test
    fun theTypeASoundIsSentAs() {
        assertEquals("audio/mp4", Recordings.mimeOf(File("a.m4a")))
        assertEquals("audio/mpeg", Recordings.mimeOf(File("a.MP3")))
        assertEquals("audio/*", Recordings.mimeOf(File("a.xyz")))
    }

    @Test
    fun titlesAndNamesIgnoreTheExtensionOfImportedSound() {
        assertEquals("2026-10-26 18:30:05 · Lesson", Recordings.title("20261026-183005 Lesson.mp3"))
        assertEquals("Lesson", Recordings.nameOf("20261026-183005 Lesson.flac"))
        assertEquals("", Recordings.nameOf("20261026-183005.ogg"))
        assertEquals("odd", Recordings.title("odd.opus"))
    }

    @Test
    fun anImportIsNeverOverAnotherAndKeepsItsFormat() {
        val store = Recordings(tmp.newFolder("rec"))
        val one = store.addImported("My song.mp3", "mp3") { it.writeText("one") }!!
        val two = store.addImported("My song.mp3", "mp3") { it.writeText("two") }!!
        val three = store.addImported("", "wav") { it.writeText("three") }!!
        assertNotEquals(one, two)
        assertEquals("one", one.readText()); assertEquals("two", two.readText())
        assertTrue(one.name.endsWith(" My song.mp3")); assertTrue(three.name.endsWith(".wav"))
        assertEquals(3, store.list().size)
        // Renaming keeps the format.
        assertTrue(store.rename(three, "Voice")!!.name.endsWith(" Voice.wav"))
    }

    @Test
    fun anImportThatFailsLeavesNothing() {
        val dir = tmp.newFolder("rec2")
        val store = Recordings(dir)
        assertNull(store.addImported("a.mp3", "mp3") { it.writeText("x"); throw java.io.IOException("cut off") })
        assertNull(store.addImported("a.mp3", "mp3") { /* nothing written */ })
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test
    fun onlyAacCanBeCut() {
        assertTrue(AudioTools.canCutMime("audio/mp4a-latm"))
        assertFalse(AudioTools.canCutMime("audio/mpeg"))
        assertFalse(AudioTools.canCutMime("audio/flac"))
        assertFalse(AudioTools.canCutMime(null))
    }

    @Test
    fun aLanguagePackOfAnotherRegionStillServes() {
        assertTrue(Transcriber.languageAvailable(listOf("en-US", "it-IT"), "it-IT"))
        assertTrue(Transcriber.languageAvailable(listOf("it_IT"), "it"))
        assertTrue(Transcriber.languageAvailable(listOf("en-GB"), "en-US"))
        assertFalse(Transcriber.languageAvailable(listOf("en-US"), "it-IT"))
        assertFalse(Transcriber.languageAvailable(emptyList(), "it-IT"))
    }
}
