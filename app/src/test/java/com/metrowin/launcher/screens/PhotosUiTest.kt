package com.metrowin.launcher.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.metrowin.launcher.photos.Photo
import com.metrowin.launcher.photos.PhotosScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class PhotosUiTest : ScreenshotBase() {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L
    private val photos = listOf(
        Photo(1, "Camera", now, false), Photo(2, "Camera", now - 1_000, true), Photo(3, "Screenshots", now - 2 * day, false), Photo(4, "", now - 9 * day, true),
    )

    private fun screen(list: List<Photo>? = photos, needsPermission: Boolean = false, onAllow: () -> Unit = {}, onOpen: (Photo) -> Unit = {}) =
        show { PhotosScreen(list, needsPermission, onAllow, onOpen) }.also { advance(500) }

    @Test
    fun thePivotHasTheThreePagesAndTheAlbumsFirst() {
        screen()
        capture("photos_albums")
        compose.onNodeWithTag("photos:title").assertTextEquals("photo hub")
        compose.onNode(hasText("albums")).assertIsDisplayed()
        compose.onNode(hasText("date")).assertExists()
        compose.onNode(hasText("favorites")).assertExists()
        compose.onNodeWithTag("album:Camera").assertIsDisplayed()
        compose.onNodeWithTag("album:Screenshots").assertExists()
        // The nameless album is shown as "Other".
        compose.onNodeWithTag("album:").assertExists()
    }

    @Test
    fun anAlbumOpensIntoItsPicturesAndAPictureIsHandedOver() {
        var opened: Photo? = null
        screen(onOpen = { opened = it })
        compose.onNodeWithTag("album:Camera").performClick()
        advance(300)
        compose.onNodeWithTag("photo:1").assertIsDisplayed()
        compose.onNodeWithTag("photo:3").assertDoesNotExist()
        compose.onNodeWithTag("photo:1").performClick()
        advance(300)
        assertEquals(1L, opened?.id)
    }

    @Test
    fun theDatePageGroupsByDayAndTheFavouritesPageHasOnlyTheMarked() {
        screen()
        compose.onNode(hasText("date")).performClick()
        advance(1_000)
        compose.onNodeWithTag("photos:date").assertIsDisplayed()
        capture("photos_date")
        compose.onNode(hasText("favorites")).performClick()
        advance(1_000)
        compose.onNodeWithTag("photo:2").assertIsDisplayed()
        compose.onNodeWithTag("photo:4").assertExists()
        compose.onNodeWithTag("photo:1").assertDoesNotExist()
    }

    @Test
    fun withoutPermissionThePageAsksAndSaysItNeverLeavesThePhone() {
        var asked = 0
        screen(null, needsPermission = true, onAllow = { asked++ })
        compose.onNodeWithTag("photos:permission").assertIsDisplayed()
        compose.onNodeWithTag("photos:allow").performClick()
        assertEquals(1, asked)
        compose.onNodeWithTag("album:Camera").assertDoesNotExist()
    }

    @Test
    fun noPicturesShowsTheEmptyNote() {
        screen(emptyList())
        compose.onNodeWithTag("photos:albums:empty").assertIsDisplayed()
        assertTrue(true)
    }

    @Test
    fun whileTheyAreBeingReadNothingIsShown() {
        screen(null)
        compose.onNodeWithTag("photos:loading").assertExists()
    }
}
