package com.metrowin.launcher

import com.metrowin.launcher.data.InfoBrief
import com.metrowin.launcher.data.InfoDevice
import com.metrowin.launcher.data.LiveEvent
import com.metrowin.launcher.data.MeetingLinks
import com.metrowin.launcher.data.PhotoFilter
import com.metrowin.launcher.data.PhotoMemories
import com.metrowin.launcher.data.PowerPolicy
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.ui.start.InfoCategory
import com.metrowin.launcher.ui.start.InfoIndex
import com.metrowin.launcher.ui.start.InfoPage
import com.metrowin.launcher.model.InfoPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** What the info tiles read: the photos of this day year by year, what a query leaves out, the link of a meeting, the hour of the summary, the editor's index. */
class InfoDataTest {
    private val rome = ZoneId.of("Europe/Rome")
    private fun ms(iso: String) = LocalDateTime.parse(iso).atZone(rome).toInstant().toEpochMilli()

    // --- the photos of this day

    @Test
    fun everyYearBeforeHasASpanThatHoldsThisDay() {
        val now = ms("2026-11-15T10:00:00")
        val ranges = PhotoMemories.ranges(now, rome, years = 3)
        assertEquals(3, ranges.size)
        assertTrue(ms("2025-11-15T00:00:00") in ranges[0] && ms("2025-11-15T23:59:59") in ranges[0])
        assertFalse(ms("2025-11-16T00:00:00") in ranges[0])
        assertTrue(ms("2023-11-15T12:00:00") in ranges[2])
    }

    @Test
    fun theDaysAroundAreAsked() {
        val now = ms("2026-11-15T10:00:00")
        val span = PhotoMemories.ranges(now, rome, days = 3, years = 1).single()
        assertTrue(ms("2025-11-12T00:00:00") in span && ms("2025-11-18T23:59:00") in span)
        assertFalse(ms("2025-11-11T23:59:00") in span || ms("2025-11-19T00:00:00") in span)
    }

    @Test
    fun theLeapDayIsAMemoryOfTheDayBefore() {
        // On 28 February of a year without a 29th, the photos of a 29 February are memories too.
        val now = ms("2027-02-28T10:00:00")
        assertTrue(PhotoMemories.isMemory(ms("2024-02-29T12:00:00"), now, rome))
        assertTrue(PhotoMemories.isMemory(ms("2024-02-28T12:00:00"), now, rome))
        assertFalse(PhotoMemories.isMemory(ms("2025-03-01T12:00:00"), now, rome))
        // And on the leap day itself, the photos of the 28th of other years are not.
        val leap = ms("2028-02-29T10:00:00")
        assertTrue(PhotoMemories.isMemory(ms("2024-02-29T08:00:00"), leap, rome))
    }

    @Test
    fun aPhotoOfThisDayInAnEarlierYearIsAMemory() {
        val now = ms("2026-11-15T10:00:00")
        assertTrue(PhotoMemories.isMemory(ms("2023-11-15T18:20:00"), now, rome))
        assertTrue(PhotoMemories.isMemory(ms("2010-11-15T00:05:00"), now, rome))
        assertTrue(PhotoMemories.isMemory(ms("2024-11-13T10:00:00"), now, rome, days = 2))
        assertFalse(PhotoMemories.isMemory(ms("2024-11-12T10:00:00"), now, rome, days = 2))
    }

    @Test
    fun aPhotoOfThisYearOrAnotherDayIsNot() {
        val now = ms("2026-11-15T10:00:00")
        assertFalse(PhotoMemories.isMemory(ms("2026-11-15T08:00:00"), now, rome))
        assertFalse(PhotoMemories.isMemory(ms("2024-11-14T23:59:00"), now, rome))
        assertFalse(PhotoMemories.isMemory(ms("2024-12-15T10:00:00"), now, rome))
        assertFalse(PhotoMemories.isMemory(0L, now, rome))
    }

    @Test
    fun theDayIsTheDayWhereThePhoneIs() {
        // 23:30 on 14 November in Rome is still the 14th; the same moment in Tokyo is the 15th.
        val taken = ms("2024-11-14T23:30:00")
        val now = ms("2026-11-14T12:00:00")
        assertTrue(PhotoMemories.isMemory(taken, now, rome))
        assertFalse(PhotoMemories.isMemory(taken, now, ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun theDateAPhotoWasTakenOn() {
        assertEquals(LocalDate.of(2024, 11, 14), PhotoMemories.dateOf(ms("2024-11-14T23:30:00"), rome))
    }

    // --- what a query leaves out

    @Test
    fun anUnfilteredQueryHasNoConditions() {
        val none = PhotoFilter.selection(null, favorites = false, noShots = false, excluded = emptySet(), sdk = 34)
        assertEquals("", none.text)
        assertEquals(0, none.args.size)
    }

    @Test
    fun theConditionsAreAddedOneByOne() {
        val all = PhotoFilter.selection("77", favorites = true, noShots = true, excluded = setOf("3", "4", "77"), sdk = 34)
        assertEquals(
            "bucket_id = ? AND is_favorite = 1 AND bucket_display_name NOT LIKE ? AND relative_path NOT LIKE ? AND bucket_id NOT IN (?,?)",
            all.text,
        )
        assertEquals(listOf("77", "%creenshot%", "%creenshot%", "3", "4"), all.args.toList())
    }

    @Test
    fun anOlderAndroidKnowsNeitherFavouritesNorTheFolderOfAShot() {
        val old = PhotoFilter.selection(null, favorites = true, noShots = true, excluded = emptySet(), sdk = 28)
        assertEquals("bucket_display_name NOT LIKE ?", old.text)
        val ten = PhotoFilter.selection(null, favorites = true, noShots = true, excluded = emptySet(), sdk = 29)
        assertEquals("bucket_display_name NOT LIKE ? AND relative_path NOT LIKE ?", ten.text)
    }

    // --- events

    @Test
    fun aMeetingLinkIsFoundInThePlaceOrTheNotes() {
        assertEquals("https://meet.google.com/abc-defg-hij", MeetingLinks.find("Join: https://meet.google.com/abc-defg-hij."))
        assertEquals("https://us02web.zoom.us/j/123?pwd=x", MeetingLinks.find("Zoom\nhttps://us02web.zoom.us/j/123?pwd=x\nbye"))
        assertEquals("https://teams.microsoft.com/l/meetup-join/1", MeetingLinks.find("see (https://teams.microsoft.com/l/meetup-join/1)"))
        assertNull(MeetingLinks.find("https://example.com/not-a-meeting and http://meet.google.com/x"))
        assertNull(MeetingLinks.find(""))
    }

    @Test
    fun anEventKnowsWhenItIsOnAndOver() {
        val event = LiveEvent("x", 1_000L, false, endMillis = 5_000L)
        assertFalse(event.isOn(500L))
        assertTrue(event.isOn(1_000L) && event.isOn(4_999L))
        assertFalse(event.isOn(5_000L))
        assertTrue(event.isOver(5_000L))
        assertFalse(event.isOver(4_000L))
        // Without an end, an event is over an hour after it began; one that lasts the day is never over by the hour.
        val open = LiveEvent("y", 0L, false)
        assertFalse(open.isOver(59 * 60_000L))
        assertTrue(open.isOver(61 * 60_000L))
        assertFalse(LiveEvent("z", 0L, true).isOver(10 * 3_600_000L))
        assertFalse(LiveEvent("z", 0L, true).isOn(10L))
    }

    // --- the summary of the morning, the networks, the age of what is read

    @Test
    fun theSummaryIsSetForTheNextTimeThatHourComes() {
        assertEquals(LocalDateTime.of(2026, 11, 15, 7, 0), InfoBrief.nextAt(LocalDateTime.of(2026, 11, 15, 6, 59), 7))
        assertEquals(LocalDateTime.of(2026, 11, 16, 7, 0), InfoBrief.nextAt(LocalDateTime.of(2026, 11, 15, 7, 0), 7))
        assertEquals(LocalDateTime.of(2026, 11, 16, 0, 0), InfoBrief.nextAt(LocalDateTime.of(2026, 11, 15, 23, 30), 0))
    }

    @Test
    fun aNetworkNameIsTakenWithoutItsQuotesAndTheUnknownOneIsEmpty() {
        assertEquals("Home", InfoDevice.clean("\"Home\""))
        assertEquals("Home 5G", InfoDevice.clean("  Home 5G "))
        assertEquals("", InfoDevice.clean("<unknown ssid>"))
        assertEquals("", InfoDevice.clean(null))
    }

    @Test
    fun theMemoriesAreNotReadAtEveryReturn() {
        assertEquals(6 * 60 * 60_000L, PowerPolicy.liveMaxAgeMs(LiveKind.Memories, saving = false))
        assertTrue(PowerPolicy.liveMaxAgeMs(LiveKind.Memories, saving = true) > PowerPolicy.liveMaxAgeMs(LiveKind.Memories, saving = false))
    }

    // --- the editor

    @Test
    fun everyPageOfTheEditorHasSettingsAndEveryCategoryPages() {
        InfoPage.entries.forEach { page -> assertTrue("${page.name} has no setting", InfoIndex.entries.any { it.page == page }) }
        InfoCategory.entries.forEach { category -> assertTrue("${category.name} has no page", category.pages.isNotEmpty()) }
    }

    @Test
    fun everySettingIsOnceAndEveryFaceHasItsToggleOnAFacesPage() {
        assertEquals(InfoIndex.entries.size, InfoIndex.entries.map { it.id }.toSet().size)
        InfoPart.entries.forEach { part ->
            val id = InfoIndex.faces[part]
            assertTrue("${part.name} has no setting", id != null)
            assertEquals(InfoCategory.Faces, InfoIndex.get(id!!)!!.page.category)
        }
    }

    @Test
    fun everySettingHasTheTextOfItsInfo() {
        InfoIndex.entries.forEach { assertEquals(it.info, InfoIndex.infoOf(it.id)) }
        assertNull(InfoIndex.infoOf("nothing.here"))
    }
}
