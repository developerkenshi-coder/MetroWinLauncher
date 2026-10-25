package com.metrowin.launcher

import com.metrowin.launcher.data.BackupSchedule
import com.metrowin.launcher.data.BackupSchedule.DAY_MILLIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupScheduleTest {
    private val folder = "content://tree/backup"
    private val now = 1_800_000_000_000L

    private fun due(days: Int = 7, folder: String = this.folder, last: Long = 0L, at: Long = now) =
        BackupSchedule.isDue(days, folder, last, at)

    @Test
    fun theFirstBackupIsDueAsSoonAsAFolderIsChosen() {
        assertTrue(due(days = 1, last = 0L))
    }

    @Test
    fun aBackupIsDueWhenTheLastOneIsAsOldAsTheIntervalOrOlder() {
        assertFalse(due(days = 7, last = now - 6 * DAY_MILLIS))
        assertTrue(due(days = 7, last = now - 7 * DAY_MILLIS))
        assertTrue(due(days = 7, last = now - 30 * DAY_MILLIS))
        assertFalse(due(days = 1, last = now - 3_600_000L))
    }

    @Test
    fun withoutAFolderOrWithBackupsOffNothingIsDue() {
        assertFalse(due(folder = ""))
        assertFalse(due(folder = "   "))
        assertFalse(due(days = 0, last = 0L))
    }

    @Test
    fun ifTheClockWentBackABackupIsDueSoItDoesNotWaitForeverForTheFuture() {
        assertTrue(due(days = 7, last = now + 10 * DAY_MILLIS))
    }

    @Test
    fun theChoicesAreNeverDayWeekMonth() {
        assertEquals(listOf(0, 1, 7, 30), BackupSchedule.intervals)
    }
}
