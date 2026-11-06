package com.metrowin.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Environment
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.metrowin.launcher.data.Recordings
import com.metrowin.launcher.recorder.AudioTools
import com.metrowin.launcher.recorder.RecorderShare
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Sound that comes from outside, on a device: the import copies what the phone can read, the list shows it, and the cut says when it cannot. */
@RunWith(AndroidJUnit4::class)
class RecorderImportTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = Recordings(context)
    private val shared = File(context.cacheDir, "shared").apply { mkdirs() }

    @Before fun clean() { store.list().forEach { store.delete(it) } }
    @After fun after() { store.list().forEach { store.delete(it) }; shared.listFiles()?.forEach { it.delete() } }

    /** A second of silence as a WAV file: sound every Android can read. */
    private fun wav(name: String): File {
        val rate = 8000
        val data = ByteArray(rate * 2)
        val out = ByteArrayOutputStream()
        val head = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        head.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVE".toByteArray()).put("fmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16).put("data".toByteArray()).putInt(data.size)
        out.write(head.array()); out.write(data)
        return File(shared, name).apply { writeBytes(out.toByteArray()) }
    }

    private fun uri(file: File) = FileProvider.getUriForFile(context, context.packageName + ".files", file)

    private fun shot(name: String) {
        compose.waitForIdle(); Thread.sleep(600)
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "fmshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun theImportCopiesSoundAndRefusesTheRest() {
        val good = wav("Lesson one.wav"); val twice = wav("Lesson one.wav".replace("one", "two"))
        val text = File(shared, "notes.txt").apply { writeText("not sound") }
        val (made, refused) = RecorderShare.import(context, store, listOf(uri(good), uri(twice), uri(text)))
        assertEquals(2, made.size); assertEquals(1, refused)
        assertTrue(made.all { it.extension == "wav" && it.length() > 0 })
        assertTrue(made[0].name.endsWith(" Lesson one.wav"))
        assertEquals(2, store.list().size)
        assertTrue("the length is read from the file", AudioTools.durationMs(made[0]) in 900L..1100L)
        assertFalse("a wav cannot be cut here", AudioTools.canCut(made[0]))
    }

    @Test fun theListShowsImportedSoundAndTheCutSaysItCannot() {
        RecorderShare.import(context, store, listOf(uri(wav("Memo.wav"))))
        ActivityScenario.launch<android.app.Activity>(Intent().setClassName(context, "com.metrowin.launcher.recorder.RecorderActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use {
            compose.onNodeWithText("recordings", ignoreCase = true).performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTagCount("rec:row") > 0 }
            shot("recorder-list")
            compose.onNodeWithTag("rec:row").performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTagCount("rec:nocut") > 0 }
            compose.onNodeWithTag("rec:nocut").assertIsDisplayed()
            compose.onNodeWithTag("rec:cut").performClick()
            shot("recorder-detail")
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}
