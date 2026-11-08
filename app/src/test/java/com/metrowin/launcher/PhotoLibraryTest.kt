package com.metrowin.launcher

import android.database.MatrixCursor
import android.provider.MediaStore
import com.metrowin.launcher.photos.Photo
import com.metrowin.launcher.photos.PhotoLibrary
import com.metrowin.launcher.photos.PhotoReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotoLibraryTest {
    private val day = 86_400_000L
    private val base = LocalDate.of(2026, 11, 8).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private fun photo(id: Long, album: String, ago: Long = 0, favorite: Boolean = false) = Photo(id, album, base + 3_600_000 - ago * day, favorite)

    private val library = listOf(
        photo(1, "Camera", 0), photo(2, "Camera", 1, favorite = true), photo(3, "WhatsApp", 1), photo(4, "", 5), photo(5, "camera roll", 2, favorite = true),
    )

    @Test
    fun albumsAreByNameWithTheNamelessOnesLastAndNewestFirstInside() {
        val albums = PhotoLibrary.albums(library)
        assertEquals(listOf("Camera", "camera roll", "WhatsApp", ""), albums.map { it.first })
        assertEquals(listOf(1L, 2L), albums.first().second.map { it.id })
    }

    @Test
    fun theDateGroupsAreDaysLatestFirst() {
        val days = PhotoLibrary.byDay(library, ZoneOffset.UTC)
        assertEquals(listOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 3)), days.map { it.first })
        assertEquals(setOf(2L, 3L), days[1].second.map { it.id }.toSet())
    }

    @Test
    fun theDayDependsOnTheTimeZone() {
        val lateEvening = Photo(9, "Camera", base + 23 * 3_600_000L, false)
        assertEquals(LocalDate.of(2026, 11, 8), PhotoLibrary.byDay(listOf(lateEvening), ZoneOffset.UTC).single().first)
        assertEquals(LocalDate.of(2026, 11, 9), PhotoLibrary.byDay(listOf(lateEvening), ZoneOffset.ofHours(5)).single().first)
    }

    @Test
    fun favouritesAreOnlyTheMarkedOnesNewestFirst() {
        assertEquals(listOf(2L, 5L), PhotoLibrary.favorites(library).map { it.id })
        assertTrue(PhotoLibrary.favorites(emptyList()).isEmpty())
        assertTrue(PhotoLibrary.albums(emptyList()).isEmpty())
    }

    private fun cursor(columns: Array<String>, vararg rows: Array<Any?>) = MatrixCursor(columns).also { c -> rows.forEach { c.addRow(it) } }

    @Test
    fun rowsBecomePicturesAndTheDateAddedStandsInForAMissingDateTaken() {
        val c = cursor(
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED, MediaStore.MediaColumns.IS_FAVORITE),
            arrayOf(7L, "Camera", 1_700_000_000_000L, 1L, 1),
            arrayOf(8L, null, 0L, 1_700_000_100L, 0),
        )
        val photos = PhotoReader.parse(c)
        assertEquals(listOf(Photo(7, "Camera", 1_700_000_000_000L, true), Photo(8, "", 1_700_000_100_000L, false)), photos)
    }

    @Test
    fun aPhoneWithoutTheFavouriteColumnHasNoFavourites() {
        val photos = PhotoReader.parse(cursor(arrayOf(MediaStore.Images.Media._ID), arrayOf(1L)))
        assertEquals(1, photos.size)
        assertFalse(photos.single().favorite)
    }
}
