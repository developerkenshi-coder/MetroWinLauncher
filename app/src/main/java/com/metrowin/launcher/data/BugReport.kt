package com.metrowin.launcher.data

import java.io.File

/**
 * The bug ticket: the mail the user sends to the author, with what they wrote and the last lines of the log of the app. Android
 * only lets an app read its own log, so the file holds nothing of other apps. The mail is only prepared; the user sends it.
 */
object BugReport {
    const val TO = "developerkenshi@gmail.com"
    const val SUBJECT = "TICKET BUG REPORT METROWIN LAUNCHER"
    const val LOG_LINES = 8000
    const val LOG_NAME = "metrowin-log.txt"

    /** What goes in the body of the mail: the words of the user, then the facts about the app and the phone. */
    fun body(description: String, about: AboutInfo): String = buildString {
        appendLine(description.trim())
        appendLine()
        appendLine("---")
        appendLine("MetroWin Launcher ${about.versionName} (${about.versionCode})")
        appendLine("Android ${about.androidRelease} (API ${about.sdk})")
        appendLine(about.device)
    }

    /** Blocking. Writes the last [LOG_LINES] lines of the log of the app in [dir] and returns the file, or null when it cannot be read. */
    fun writeLog(dir: File, about: AboutInfo): File? = runCatching {
        dir.mkdirs()
        val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", LOG_LINES.toString())
            .redirectErrorStream(true).start()
        // Reading to the end is the wait: logcat -d stops by itself. The lines are kept as they come, so only the last ones stay.
        val lines = ArrayDeque<String>()
        process.inputStream.bufferedReader().useLines { all ->
            all.forEach { line ->
                lines.addLast(line)
                if (lines.size > LOG_LINES) lines.removeFirst()
            }
        }
        process.destroy()
        File(dir, LOG_NAME).apply {
            writeText("MetroWin Launcher ${about.versionName} (${about.versionCode}), Android ${about.androidRelease}, ${about.device}\n")
            appendText(lines.joinToString("\n", postfix = "\n"))
        }
    }.getOrNull()
}
