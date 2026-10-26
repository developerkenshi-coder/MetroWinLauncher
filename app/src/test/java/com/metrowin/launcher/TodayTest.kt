package com.metrowin.launcher

import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.LiveEvent
import com.metrowin.launcher.data.PowerPolicy
import com.metrowin.launcher.data.Reminder
import com.metrowin.launcher.data.Today
import com.metrowin.launcher.data.TodayExtras
import com.metrowin.launcher.data.WeatherCondition
import com.metrowin.launcher.data.WeatherData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.ui.apps.AppListModel
import com.metrowin.launcher.ui.apps.ListRow
import com.metrowin.launcher.ui.gesture.PANORAMA_SPEED
import com.metrowin.launcher.ui.gesture.panoramaOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Today card above the search box, and the titles of the panorama. */
class TodayTest {
    private val now = 1_800_000_000_000L
    private val hour = 60L * 60 * 1000

    private fun reminder(id: Long, at: Long, done: Boolean = false) = Reminder(id, "r$id", at, done)

    @Test
    fun theCardShowsTheNextTwoRemindersAndCountsTheMissedOnes() {
        val list = listOf(
            reminder(1, now + 3 * hour), reminder(2, now + hour), reminder(3, now + 5 * hour),
            reminder(4, now - 2 * hour), reminder(5, now - 3 * hour), reminder(6, now - 4 * hour, done = true),
        )
        val info = Today.info(TodayExtras(), null, list, now)
        // The nearest first, two of them.
        assertEquals(listOf(2L, 1L), info.reminders.map { it.id })
        // Two have passed their time without being done; the one that is done does not count.
        assertEquals(2, info.missed)
    }

    @Test
    fun anEventThatMustBeOverAndAnAlarmThatRangAreLeftOut() {
        val over = LiveEvent("old", now - 7 * hour, allDay = false)
        val soon = LiveEvent("soon", now + hour, allDay = false)
        val today = LiveEvent("birthday", now - 20 * hour, allDay = true)
        assertNull(Today.info(TodayExtras(event = over), null, emptyList(), now).event)
        assertEquals(soon, Today.info(TodayExtras(event = soon), null, emptyList(), now).event)
        // A whole-day event is dated at midnight: it is still today's whatever the hour.
        assertEquals(today, Today.info(TodayExtras(event = today), null, emptyList(), now).event)
        assertNull(Today.info(TodayExtras(alarmMillis = now - 1), null, emptyList(), now).alarmMillis)
        assertEquals(now + hour, Today.info(TodayExtras(alarmMillis = now + hour), null, emptyList(), now).alarmMillis)
    }

    @Test
    fun aQuietDayHasNothingButTheDate() {
        assertTrue(Today.info(TodayExtras(), null, emptyList(), now).isQuiet)
        val weather = WeatherData(18, WeatherCondition.Clear, true, emptyList(), false, now)
        assertFalse(Today.info(TodayExtras(), weather, emptyList(), now).isQuiet)
        assertFalse(Today.info(TodayExtras(), null, listOf(reminder(1, now - 2 * hour)), now).isQuiet)
    }

    @Test
    fun theCardGoesAboveTheSearchBoxAndTheOtherRowsStayWhereTheModelSaysTheyAre() {
        val apps = listOf(AppItem("app:a", "Alpha", Launch.Action("x")))
        val plain = AppListModel(AppSections.group(AppSections.sort(apps)))
        assertEquals(ListRow.Search, plain.rows.first())
        assertEquals(1, plain.head)

        val withCard = AppListModel(AppSections.group(AppSections.sort(apps)), today = true)
        assertEquals(listOf(ListRow.Today, ListRow.Search), withCard.rows.take(2))
        assertEquals(2, withCard.head)
        // The jump list scrolls to these positions: they count the card.
        assertEquals(plain.headerIndex.getValue("A") + 1, withCard.headerIndex.getValue("A"))
        assertEquals(plain.indexOfApp("app:a")!! + 1, withCard.indexOfApp("app:a"))
    }

    @Test
    fun theCalendarAndTheAlarmAreReadLessOftenWhileSaving() {
        assertTrue(PowerPolicy.todayMaxAgeMs(saving = true) > PowerPolicy.todayMaxAgeMs(saving = false))
        assertTrue(PowerPolicy.todayMaxAgeMs(saving = false) >= 60_000L)
    }

    // --- the panorama

    @Test
    fun aTitleIsAtTheEdgeWhileItsPageIsInViewAndMovesHalfAsFastAsThePages() {
        assertEquals(0f, panoramaOffset(anchor = 2, position = 2f, pageWidth = 360f), 0f)
        // The pages move a whole page (360) for a whole page of swipe; the title half of that.
        assertEquals(-180f, panoramaOffset(anchor = 0, position = 1f, pageWidth = 360f), 0.001f)
        assertEquals(180f, panoramaOffset(anchor = 1, position = 0f, pageWidth = 360f), 0.001f)
        // Half way between two pages.
        assertEquals(90f, panoramaOffset(anchor = 1, position = 0.5f, pageWidth = 360f), 0.001f)
        assertEquals(PANORAMA_SPEED, 0.5f, 0f)
        // Reduced motion: one to one, as the pages.
        assertEquals(-360f, panoramaOffset(anchor = 0, position = 1f, pageWidth = 360f, speed = 1f), 0.001f)
    }
}
