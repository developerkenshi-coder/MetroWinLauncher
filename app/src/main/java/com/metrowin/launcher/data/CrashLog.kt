package com.metrowin.launcher.data

import java.io.File

/**
 * The last crash of the launcher, kept in a file of the app: when something goes wrong the stack is already there, and the bug ticket carries it
 * (see [BugReport.writeLog]) without the user having to catch the log in time. Only the newest crash is kept; it holds the version, the thread and
 * the stack, nothing else.
 */
object CrashLog {
    const val NAME = "last-crash.txt"

    /** Longest text kept: a stack that deep is a loop, and the head of it says it all. */
    const val MAX_CHARS = 20_000

    fun text(at: Long, version: String, thread: String, error: Throwable): String =
        ("MetroWin Launcher $version, thread $thread, ${java.util.Date(at)}\n" + error.stackTraceToString()).take(MAX_CHARS)

    /** Writes the crash; never throws, as it runs while the app is dying. */
    fun save(dir: File, text: String) {
        runCatching { File(dir, NAME).writeText(text) }
    }

    /** The saved crash, or null when there is none. */
    fun read(dir: File): String? = runCatching { File(dir, NAME).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() } }.getOrNull()

    /** Puts the saving in front of the handler the system already has, which then does what it always did (the dialog, the process ending). */
    fun install(dir: File, version: String, onCrash: () -> Unit = {}) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            save(dir, text(System.currentTimeMillis(), version, thread.name, error))
            onCrash()
            previous?.uncaughtException(thread, error)
        }
    }
}
