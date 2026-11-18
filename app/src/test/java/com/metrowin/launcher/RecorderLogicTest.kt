package com.metrowin.launcher

import com.metrowin.launcher.recorder.AudioEdit
import com.metrowin.launcher.recorder.AudioTools
import com.metrowin.launcher.recorder.Opt
import com.metrowin.launcher.recorder.Rec
import com.metrowin.launcher.recorder.RecMeta
import com.metrowin.launcher.recorder.RecMetaStore
import com.metrowin.launcher.recorder.RecorderConfig
import com.metrowin.launcher.recorder.RecorderFormat
import com.metrowin.launcher.recorder.Transcriber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecorderLogicTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun rec(name: String, ms: Long = 1000, transcript: String = "", label: String = "") =
        Rec(File(name), ms, 100L, RecMeta(transcript = transcript, label = label))

    // --- the list

    @Test
    fun theStampIsReadFromTheName() {
        assertEquals("20261026-183005", RecorderFormat.stampOf("20261026-183005 Lesson.mp3"))
        assertEquals("", RecorderFormat.stampOf("odd.m4a"))
        assertEquals("20261026", RecorderFormat.dayOf("20261026-183005.m4a"))
    }

    @Test
    fun theListIsOrderedAndSearchedInTitlesAndText() {
        val a = rec("20261026-100000 Alpha.m4a", 5000)
        val b = rec("20261027-100000.m4a", 9000, transcript = "the quick brown fox")
        val c = rec("20261028-100000 Zulu.m4a", 1000, label = "work")
        val all = listOf(a, b, c)
        assertEquals(listOf(c, b, a), RecorderFormat.arrange(all, "", 0))
        assertEquals(listOf(a, b, c), RecorderFormat.arrange(all, "", 1))
        assertEquals(listOf(b, a, c), RecorderFormat.arrange(all, "", 2))
        assertEquals(listOf(a, c, b), RecorderFormat.arrange(all, "", 3))
        assertEquals(listOf(b), RecorderFormat.arrange(all, "BROWN", 0))
        assertEquals(listOf(c), RecorderFormat.arrange(all, "", 0, label = "work"))
        assertTrue(RecorderFormat.arrange(all, "nothing", 0).isEmpty())
    }

    @Test
    fun aSnippetCentresOnTheWordFound() {
        val text = "one two three four five six seven eight nine ten eleven twelve thirteen"
        val s = RecorderFormat.snippet(text, "SIX", radius = 10)
        assertTrue(s, s.contains("six") && s.startsWith("…") && s.endsWith("…"))
        assertEquals("", RecorderFormat.snippet(text, "absent"))
        assertEquals("", RecorderFormat.snippet(text, " "))
    }

    @Test
    fun theSummaryKeepsTheSentencesWithTheMostRepeatedWordsInOrder() {
        val text = "Cats sleep long. Dogs bark loud. The cats and the dogs play together outside. Weather was fine today. Cats purr softly."
        val short = RecorderFormat.summary(text, 2)
        assertTrue(short, short.length < text.length)
        assertTrue(short, short.contains("play together"))
        assertEquals("One. Two.", RecorderFormat.summary("One. Two.", 3))
    }

    @Test
    fun sizesAreRoundedForReading() = withLocale {
        assertEquals("1 KB", RecorderFormat.size(10))
        assertEquals("412 KB", RecorderFormat.size(412 * 1024L))
        assertEquals("3.4 MB", RecorderFormat.size((3.4 * 1048576).toLong()))
        assertEquals("3,4 MB", withLocale(java.util.Locale.ITALY) { RecorderFormat.size((3.4 * 1048576).toLong()) })
    }

    @Test
    fun markersAreFoundAroundThePosition() {
        val marks = listOf(10_000L, 2_000L, 30_000L)
        assertEquals(10_000L, RecorderFormat.nextMark(marks, 2_000))
        assertNull(RecorderFormat.nextMark(marks, 30_000))
        assertEquals(10_000L, RecorderFormat.previousMark(marks, 12_000))
        assertNull(RecorderFormat.previousMark(marks, 2_500))
    }

    // --- the meta files

    @Test
    fun aMetaFileKeepsWhatWasSavedAndAnEmptyOneIsRemoved() {
        val store = RecMetaStore(tmp.newFolder("meta"))
        val meta = RecMeta(transcript = "hello", transcribedAt = 5, noteId = "n1", marks = listOf(1000, 2500), label = "work", made = "cut")
        store.save("20261026-183005", meta)
        assertEquals(meta, store.load("20261026-183005"))
        assertEquals(mapOf("20261026-183005" to meta), store.loadAll())
        store.save("20261026-183005", RecMeta())
        assertEquals(RecMeta(), store.load("20261026-183005"))
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun aBrokenMetaFileReadsAsEmptyAndOrphansArePurged() {
        val dir = tmp.newFolder("meta2")
        File(dir, "20261026-183005.json").writeText("{not json")
        val store = RecMetaStore(dir)
        assertEquals(RecMeta(), store.load("20261026-183005"))
        store.save("20261027-000000", RecMeta(label = "x"))
        store.purgeOrphans(setOf("20261027-000000"))
        assertEquals(setOf("20261027-000000"), store.loadAll().keys)
    }

    // --- the switches

    @Test
    fun whatTouchesPrivacyDiskOrAnotherFolderIsOffUntilTurnedOn() {
        listOf(Opt.KEEP_TEXT, Opt.BACKUP, Opt.SAFE, Opt.CONTRAST).forEach { assertEquals(it.name, 0, it.default) }
        Opt.entries.forEach { assertTrue(it.name, it.default in 0..it.max) }
        assertEquals(Opt.entries.size, Opt.entries.map { it.key }.toSet().size)
        // the standard quality is what the recorder always did
        assertEquals(RecorderConfig.Quality(96_000, 44_100, 1), RecorderConfig.QUALITIES[0])
        assertEquals(Opt.QUALITY.max + 1, RecorderConfig.QUALITIES.size)
        assertEquals(Opt.MAX_MIN.max + 1, RecorderConfig.MAX_MINUTES.size)
    }

    // --- the sound

    @Test
    fun onlyAacIsCopiedWithoutBeingWrittenAgain() {
        assertTrue(AudioTools.canCutMime("audio/mp4a-latm"))
        assertFalse(AudioTools.canCutMime("audio/mpeg"))
        assertFalse(AudioTools.canCutMime(null))
    }

    @Test
    fun theTranscriberAcceptsAnotherRegionOfTheSameLanguage() {
        assertTrue(Transcriber.languageAvailable(listOf("en-US", "it-IT"), "it-CH"))
        assertTrue(Transcriber.languageAvailable(listOf("en-US"), "en-GB"))
        assertFalse(Transcriber.languageAvailable(listOf("en-US"), "de-DE"))
        assertFalse(Transcriber.languageAvailable(emptyList(), "it"))
    }

    @Test
    fun theTransformFindsTheFrequencyOfASine() {
        val n = 512
        val re = DoubleArray(n) { sin(2 * PI * 32 * it / n) }
        val im = DoubleArray(n)
        AudioEdit.fft(re, im)
        val power = DoubleArray(n / 2) { re[it] * re[it] + im[it] * im[it] }
        assertEquals(32, power.indices.maxByOrNull { power[it] })
    }

    @Test
    fun theResamplerChangesTheNumberOfFramesByTheRatio() {
        val input = ShortArray(4800) { (it % 100).toShort() }
        var out = 0
        val r = AudioEdit.Resampler(48_000, 44_100, 1)
        // in two blocks: the position carries over
        r.push(input.copyOfRange(0, 2400), 2400) { _, n -> out += n }
        r.push(input.copyOfRange(2400, 4800), 2400) { _, n -> out += n }
        assertEquals(4410.0, out.toDouble(), 3.0)
        var same = 0
        AudioEdit.Resampler(44_100, 44_100, 2).push(ShortArray(20), 10) { _, n -> same = n }
        assertEquals(10, same)
    }

    @Test
    fun theSilenceThresholdSitsAboveTheQuietAndBelowTheLoud() {
        val windows = IntArray(100) { if (it < 40) 40 else 3000 }
        val t = AudioEdit.silenceThreshold(windows)
        assertTrue("$t", t in 60..1500)
        assertEquals(0, AudioEdit.silenceThreshold(IntArray(0)))
    }

    @Test
    fun aWavHeaderSaysHowMuchSoundFollows() {
        val h = AudioEdit.wavHeader(44_100, 1, 1000)
        assertEquals(44, h.size)
        assertEquals("RIFF", String(h, 0, 4))
        assertEquals("WAVE", String(h, 8, 4))
        assertEquals(1036, java.nio.ByteBuffer.wrap(h, 4, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int)
        assertEquals(1000, java.nio.ByteBuffer.wrap(h, 40, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int)
    }
}
