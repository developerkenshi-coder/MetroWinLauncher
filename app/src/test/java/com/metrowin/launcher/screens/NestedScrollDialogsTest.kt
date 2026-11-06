package com.metrowin.launcher.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import com.metrowin.launcher.files.ActionsDialog
import com.metrowin.launcher.files.Place
import com.metrowin.launcher.files.PlacesDialog
import com.metrowin.launcher.files.PropertiesDialog
import com.metrowin.launcher.news.NewsPresets
import com.metrowin.launcher.news.NewsSourceList
import com.metrowin.launcher.news.SourcesDialog
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A dialog of the launcher scrolls by itself (see `DialogFrame`): a list or a column that scrolls inside it, with no height of its own, is
 * measured with no limit and crashes ("Vertically scrollable component was measured with an infinity maximum height"). A phone
 * did that when these were opened; opening each one here is what keeps it from coming back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class NestedScrollDialogsTest : ScreenshotBase() {
    @Test
    fun thePlacesOfTheFileManagerOpenWithAManyPlaces() {
        val many = (1..40).map { Place("Folder $it", File("/sdcard/f$it"), Place.Kind.Bookmark) }
        show { PlacesDialog(listOf(Place("Phone", File("/sdcard"), Place.Kind.Storage)), many, many.map { it.file }, onGo = {}, onTrash = {}, onCancel = {}) }
        advance(500)
        compose.onNodeWithTag("fm:place:trash", useUnmergedTree = true).assertIsDisplayedOrExists()
    }

    @Test
    fun theActionsOfASelectionOpen() {
        val files = listOf(File("/sdcard/a.zip"))
        show {
            ActionsDialog(
                files, File("/sdcard/other"), onExtract = {}, onToOther = {}, onProperties = {}, onInvert = {}, onSelectAll = {},
                onCopyPath = {}, onBookmark = {}, bookmarks = emptyList(), onEdit = {}, onCancel = {},
            )
        }
        advance(500)
        compose.onNodeWithTag("fm:act:extract", useUnmergedTree = true).assertIsDisplayedOrExists()
    }

    @Test
    fun thePropertiesOfAFileOpen() {
        show { PropertiesDialog(File("/sdcard/a.txt"), onClose = {}) }
        advance(500)
    }

    @Test
    fun theSourcesOfTheNewsOpenWithAllThePresetsAdded() {
        val all = NewsPresets.feeds.fold(listOf(NewsSourceList.googleTop())) { list, p -> NewsSourceList.add(list, p) }
        show { SourcesDialog(all, mapOf("WORLD" to "World"), onChange = {}, onRemove = {}, showImages = true, onImages = {}, onClose = {}) }
        advance(500)
        compose.onNodeWithTag("nws:add", useUnmergedTree = true).assertIsDisplayedOrExists()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertIsDisplayedOrExists() = assertExists()
}
