package com.metrowin.launcher.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import com.metrowin.launcher.files.ActionsDialog
import com.metrowin.launcher.files.Place
import com.metrowin.launcher.files.PlacesDialog
import com.metrowin.launcher.files.PropertiesDialog
import com.metrowin.launcher.news.NewsPresets
import com.metrowin.launcher.news.NewsSourceList
import com.metrowin.launcher.news.SourcesDialog
import com.metrowin.launcher.systemsettings.DialogSlot
import com.metrowin.launcher.systemsettings.LocalDialogSlot
import com.metrowin.launcher.systemsettings.PageDialog
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.components.MetroText
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
                opts = { it.default }, tag = 0, onTag = {}, onBulkRename = {}, onArchiveList = {},
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

    @Test
    fun aDialogOfASettingsPageOpensOutsideTheScrollingPage() {
        // The pages of the settings scroll: a Wi-Fi password or the manual time asked in them used to crash the app (26.10.3S).
        show {
            val slot = remember { DialogSlot() }
            CompositionLocalProvider(LocalDialogSlot provides slot) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        PageDialog { DialogFrame(onCancel = {}) { MetroText("password", Modifier.testTag("ss:dlg")) } }
                    }
                    slot.content?.invoke()
                }
            }
        }
        advance(500)
        compose.onNodeWithTag("ss:dlg", useUnmergedTree = true).assertIsDisplayedOrExists()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertIsDisplayedOrExists() = assertExists()
}
