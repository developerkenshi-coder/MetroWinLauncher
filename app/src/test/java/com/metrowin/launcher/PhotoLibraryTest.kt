package com.metrowin.launcher

import android.database.MatrixCursor
import android.provider.MediaStore
import com.metrowin.launcher.photos.Photo
import com.metrowin.launcher.photos.PhotoLibrary
import com.metrowin.launcher.photos.PhotoReader
import com.metrowin.launcher.photos.PhotoRows
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
        assertEquals(listOf("Camera", "camera roll", "WhatsApp", ""), albums.map { it.name })
        assertEquals(listOf(1L, 2L), albums.first().photos.map { it.id })
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

    @Test
    fun twoStoragesWithTheSameAlbumNameStayTwoAlbums() {
        val two = listOf(Photo(1, "Camera", base, false, volume = "external_primary"), Photo(2, "Camera", base, false, volume = "1234-ABCD"))
        val albums = PhotoLibrary.albums(two)
        assertEquals(2, albums.size)
        assertEquals(2, albums.map { it.key }.toSet().size)
    }

    @Test
    fun theCameraAlbumCanLeadTheOthers() {
        val list = listOf(Photo(1, "Alpha", base, false), Photo(2, "Camera", base, false), Photo(3, "", base, false))
        assertEquals(listOf("Alpha", "Camera", ""), PhotoLibrary.albums(list).map { it.name })
        assertEquals(listOf("Camera", "Alpha", ""), PhotoLibrary.albums(list, cameraFirst = true).map { it.name })
    }

    @Test
    fun sectionsGroupByMonthAndYearWithTheLatestFirst() {
        val list = listOf(
            Photo(1, "", LocalDate.of(2026, 11, 8).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), false),
            Photo(2, "", LocalDate.of(2026, 11, 2).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), false),
            Photo(3, "", LocalDate.of(2026, 3, 2).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), false),
            Photo(4, "", LocalDate.of(2025, 3, 2).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), false),
        )
        val months = PhotoLibrary.sections(list, com.metrowin.launcher.photos.Granularity.Month, ZoneOffset.UTC)
        assertEquals(listOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 3, 1), LocalDate.of(2025, 3, 1)), months.map { it.date })
        assertEquals(2, months.first().photos.size)
        val years = PhotoLibrary.sections(list, com.metrowin.launcher.photos.Granularity.Year, ZoneOffset.UTC)
        assertEquals(listOf(3, 1), years.map { it.photos.size })
    }

    @Test
    fun memoriesAreTheSameDayOfEarlierYears() {
        val list = listOf(
            Photo(1, "", LocalDate.of(2025, 11, 8).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 1000, false),
            Photo(2, "", LocalDate.of(2023, 11, 8).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 1000, false),
            Photo(3, "", LocalDate.of(2025, 11, 9).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 1000, false),
            Photo(4, "", LocalDate.of(2026, 11, 8).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 1000, false),
        )
        val memories = PhotoLibrary.memories(list, LocalDate.of(2026, 11, 8), ZoneOffset.UTC)
        assertEquals(listOf(1, 3), memories.map { it.first })
        assertEquals(listOf(1L), memories.first().second.map { it.id })
    }

    @Test
    fun searchNeedsEveryWordInTheAlbumTheMonthOrTheYear() {
        val month = { m: Int -> if (m == 11) "november" else "other" }
        assertEquals(listOf(1L, 2L), PhotoLibrary.search(library, "camera", month, ZoneOffset.UTC).map { it.id }.sorted().take(2))
        assertEquals(5, PhotoLibrary.search(library, "2026", month, ZoneOffset.UTC).size)
        assertEquals(listOf(3L), PhotoLibrary.search(library, "whatsapp november", month, ZoneOffset.UTC).map { it.id })
        assertTrue(PhotoLibrary.search(library, "nothing here", month, ZoneOffset.UTC).isEmpty())
        assertEquals(library, PhotoLibrary.search(library, "  ", month, ZoneOffset.UTC))
    }

    @Test
    fun duplicatesShareSizeDimensionsAndMoment() {
        val a = Photo(1, "A", base, false, width = 100, height = 50, bytes = 999)
        val copy = a.copy(id = 2, album = "B")
        val other = a.copy(id = 3, bytes = 1000)
        val unknown = Photo(4, "", base, false)
        val groups = PhotoLibrary.duplicates(listOf(a, copy, other, unknown, unknown.copy(id = 5)))
        assertEquals(1, groups.size)
        assertEquals(setOf(1L, 2L), groups.single().map { it.id }.toSet())
    }

    @Test
    fun squareRowsHaveTheSameNumberOfPicturesAndTheLastOneIsShort() {
        val rows = PhotoRows.layout(List(7) { 1.5f }, width = 1000f, targetHeight = 240f, gap = 10f, square = true)
        assertEquals(listOf(4, 3), rows.map { it.fractions.size })
        assertEquals(rows[0].height, rows[1].height, 0.01f)
        // Four squares and three gaps fill the row exactly.
        assertEquals(1000f, rows[0].fractions.sum() * 1000f + 30f, 0.5f)
    }

    @Test
    fun proportionalRowsFillTheWidthExceptTheLastOne() {
        val ratios = listOf(1.5f, 0.75f, 1f, 1.78f, 1f, 1f, 1f)
        val rows = PhotoRows.layout(ratios, width = 1000f, targetHeight = 200f, gap = 8f, square = false)
        rows.dropLast(1).forEach { row -> assertEquals(1000f, row.fractions.sum() * 1000f + 8f * (row.fractions.size - 1), 1f) }
        assertEquals(ratios.size, rows.sumOf { it.fractions.size })
        assertEquals(200f, rows.last().height, 0.01f)
        assertTrue(PhotoRows.layout(emptyList(), 1000f, 100f, 4f, false).isEmpty())
    }

    @Test
    fun aHugeLibraryIsGroupedAtOnce() {
        val big = List(60_000) { Photo(it.toLong(), "A${it % 40}", base - it * 60_000L, it % 17 == 0, width = 4000, height = 3000, bytes = 1_000L + it) }
        val started = System.nanoTime()
        assertEquals(40, PhotoLibrary.albums(big).size)
        assertTrue(PhotoLibrary.byDay(big).isNotEmpty())
        assertTrue(PhotoLibrary.duplicates(big).isEmpty())
        assertTrue("grouping 60000 pictures took ${(System.nanoTime() - started) / 1_000_000} ms", System.nanoTime() - started < 10_000_000_000L)
    }

    @Test
    fun theColumnsOfSizeAndStorageAreRead() {
        val c = cursor(
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT, MediaStore.Images.Media.SIZE, MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.DATE_MODIFIED, MediaStore.MediaColumns.VOLUME_NAME),
            arrayOf(5L, 4000, 3000, 2_000_000L, "image/jpeg", 77L, "external_primary"),
        )
        val p = PhotoReader.parse(c).single()
        assertEquals(4000, p.width); assertEquals(3000, p.height); assertEquals(2_000_000L, p.bytes)
        assertEquals("image/jpeg", p.mime); assertEquals(77L, p.modified); assertEquals("external_primary", p.volume)
        assertEquals(4f / 3f, p.ratio, 0.001f)
    }
}
