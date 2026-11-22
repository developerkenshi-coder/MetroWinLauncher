package com.metrowin.launcher.data

/** When an automatic backup is due. Pure, so it can be tested. */
object BackupSchedule {
    const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** The choices of "back up by itself", in days (0 is never). */
    val intervals = listOf(0, 1, 7, 30)

    /**
     * Due when a folder is chosen, backups are on, and the last one is [everyDays] days old or older. If the clock
     * went back (the last backup seems to be in the future) it is due: waiting would mean never.
     */
    fun isDue(everyDays: Int, folder: String, lastBackupAt: Long, now: Long): Boolean =
        everyDays > 0 && folder.isNotBlank() &&
            (lastBackupAt <= 0L || now < lastBackupAt || now - lastBackupAt >= everyDays * DAY_MILLIS)
}
