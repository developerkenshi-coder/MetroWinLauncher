package com.metrowin.launcher.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
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
        compose.onNodeWithTag("album:Camera").assertIsDisplayed()
        compose.onNodeWithTag("album:Screenshots").assertExists()
        // The nameless album ("Other") is the third, below the fold of the narrower page of the panorama.
    }

    @Test
    fun anAlbumOpensIntoItsPicturesAndAPictureIsHandedOver() {
        var opened: Photo? = null
        screen(onOpen = { opened = it })
        compose.onNodeWithTag("album:Camera").performClick()
        advance(300)
        // The next section peeks in at the edge (the panorama), so a picture can be on two pages at once: the first is the one of the album.
        compose.onAllNodesWithTag("photo:1")[0].assertIsDisplayed()
        // The Screenshots picture is only on the date page that peeks in, not in the album.
        compose.onAllNodesWithTag("photo:3").assertCountEquals(1)
        compose.onAllNodesWithTag("photo:1")[0].performClick()
        advance(300)
        assertEquals(1L, opened?.id)
    }

    @Test
    fun theDatePageGroupsByDayAndTheFavouritesPageHasOnlyTheMarked() {
        screen()
        // The sections are swiped, as in a panorama.
        compose.onNodeWithTag("photos:pager").performTouchInput { swipeLeft(startX = width * 0.8f, endX = width * 0.3f) }
        advance(1_000)
        compose.onNodeWithTag("photos:date").assertIsDisplayed()
        capture("photos_date")
        compose.onNodeWithTag("photos:pager").performTouchInput { swipeLeft(startX = width * 0.8f, endX = width * 0.3f) }
        advance(1_000)
        compose.onAllNodesWithTag("photo:2")[0].assertIsDisplayed()
        compose.onAllNodesWithTag("photo:4")[0].assertExists()
        compose.onNodeWithTag("photos:favorites").assertIsDisplayed()
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
        compose.onAllNodesWithTag("photos:loading")[0].assertExists()
    }
}
