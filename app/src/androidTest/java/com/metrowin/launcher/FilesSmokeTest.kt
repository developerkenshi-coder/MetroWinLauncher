package com.metrowin.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Environment
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.metrowin.launcher.files.FilesPrefs
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The file manager on a device, over a folder made for the test: the list, the tiles, the groups, the hidden files and the bar of a
 * selection. Each test leaves a screenshot in `/sdcard/Pictures/fmshots` (the app is removed after the run, the pictures stay).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FilesSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val home = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "fmtest")

    @Before fun seed() {
        // The test app is installed fresh each time, without the access to all the files that the file manager asks for.
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow").let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() } }
        home.deleteRecursively()
        File(home, "Photos").mkdirs(); File(home, ".secret").mkdirs()
        File(home, "report.pdf").writeBytes("%PDF-1.4".toByteArray())
        File(home, "noext").writeBytes("%PDF-1.5 and a few more words".toByteArray())
        File(home, "notes.txt").writeText("hello")
        File(home, ".dotfile.txt").writeText("x")
        File(home, "pic.png").outputStream().use { Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FilesPrefs(context).apply { setLastPath(0, home); setLastPath(1, File(home, "Photos")); showHidden = false; tiles = false }
    }

    @After fun clean() { home.deleteRecursively() }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(600)
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "fmshots").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun back() { androidx.test.espresso.Espresso.pressBack() }

    private fun launch() = ActivityScenario.launch<android.app.Activity>(
        Intent().setClassName(context, "com.metrowin.launcher.files.FilesActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    private fun bar(res: Int) = hasContentDescription(context.getString(res))
    private fun tag(t: String) = compose.onNodeWithTag(t, useUnmergedTree = true)
    private fun rows() = compose.onAllNodesWithTag("fm:row", useUnmergedTree = true)

    @Test fun theListShowsTheFolderAndHidesTheDotFiles() {
        launch().use {
            compose.waitUntil(8000) { rows().fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("report.pdf")).assertIsDisplayed()
            assertTrue(compose.onAllNodes(hasText(".secret")).fetchSemanticsNodes().isEmpty())
            assertTrue(compose.onAllNodes(hasText(".dotfile.txt")).fetchSemanticsNodes().isEmpty())
            shot("files-list")
        }
    }

    @Test fun theMenuShowsTheHiddenFilesAndHidesThemAgain() {
        launch().use {
            compose.waitUntil(8000) { rows().fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(bar(R.string.fm_more)).performClick()
            shot("files-menu")
            tag("fm:menu:hidden").performClick()
            compose.waitUntil(5000) { compose.onAllNodes(hasText(".secret")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText(".dotfile.txt")).assertIsDisplayed()
            compose.onNode(bar(R.string.fm_more)).performClick()
            tag("fm:menu:hidden").performClick()
            compose.waitUntil(5000) { compose.onAllNodes(hasText(".secret")).fetchSemanticsNodes().isEmpty() }
        }
    }

    @Test fun theTilesShowTheFolderAsTilesAndTheListComesBack() {
        launch().use {
            compose.waitUntil(8000) { rows().fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(bar(R.string.fm_view)).performClick()
            shot("files-view")
            tag("fm:view:tiles").performClick()
            back()
            compose.waitUntil(5000) { rows().fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("report.pdf")).assertIsDisplayed()
            shot("files-tiles")
            // Back to the list, and the choice is kept for the next time.
            compose.onNode(bar(R.string.fm_view)).performClick()
            tag("fm:view:list").performClick()
            back()
            compose.onNode(hasText("report.pdf")).assertIsDisplayed()
        }
    }

    @Test fun theGroupsListDocumentsByTheirHeaderToo() {
        launch().use {
            compose.waitUntil(8000) { rows().fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(bar(R.string.fm_categories)).performClick()
            shot("files-categories")
            tag("fm:cat:Documents").performClick()
            compose.waitUntil(15000) { compose.onAllNodes(hasText("noext")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("report.pdf")).assertIsDisplayed()
            tag("fm:doc:Pdf").performClick()
            compose.waitUntil(15000) { compose.onAllNodes(hasText("notes.txt")).fetchSemanticsNodes().isEmpty() }
            compose.onNode(hasText("noext")).assertIsDisplayed()
            shot("files-documents")
        }
    }

    @Test fun aSelectionGetsItsOwnBar() {
        launch().use {
            compose.waitUntil(8000) { rows().fetchSemanticsNodes().isNotEmpty() }
            rows()[0].performTouchInput { longClick() }
            compose.waitUntil(5000) { compose.onAllNodes(bar(R.string.fm_compress)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(bar(R.string.fm_rename_short)).assertIsDisplayed()
            compose.onNode(bar(R.string.fm_share)).assertIsDisplayed()
            shot("files-selection")
            compose.onNode(bar(R.string.fm_compress)).performClick()
            compose.waitUntil(5000) { compose.onAllNodes(hasTestTag("fm:format:7z")).fetchSemanticsNodes().isNotEmpty() }
            shot("files-compress")
        }
    }
}
