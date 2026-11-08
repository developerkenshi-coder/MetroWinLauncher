package com.metrowin.launcher

import com.metrowin.launcher.data.AboutInfo
import com.metrowin.launcher.data.BugReport
import com.metrowin.launcher.data.Diagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the bug report carries (the log of the app and the notification journal) must hold no words of the user: see docs/progetti/cifratura.md.
 * The scan is crude on purpose: a new log line outside the two known files fails here and has to be read by a person.
 */
class LogPrivacyTest {
    private val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()

    @Test
    fun onlyDiagnosticsAndTheNotificationListenerWriteToTheLog() {
        val writers = sources.filter { Regex("""\bLog\.[viwed]\(""").containsMatchIn(it.readText()) }.map { it.name }.toSet()
        assertEquals(setOf("Diagnostics.kt", "NotificationService.kt"), writers)
    }

    @Test
    fun noLogOrJournalLineMentionsTheContentOfAnything() {
        // Interpolations of what a person wrote or dialled: notification text, mail, numbers, addresses, intent extras.
        val content = Regex("""\$\{?[\w.!?]*\b(text|title|body|subText|tickerText|extras|number|address|contact|message|subject|data|uri|query)\b""", RegexOption.IGNORE_CASE)
        val lines = sources.flatMap { file ->
            file.readLines().filter { Regex("""\bLog\.[viwed]\(|NotificationHub\.note\(""").containsMatchIn(it) }.map { "${file.name}: ${it.trim()}" }
        }
        assertTrue("no log lines found", lines.isNotEmpty())
        val bad = lines.filter { content.containsMatchIn(it) }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun noStackTraceGoesToTheStandardOutput() {
        val bad = sources.filter { Regex("""printStackTrace\(|System\.(out|err)\.print""").containsMatchIn(it.readText()) }.map { it.name }
        assertTrue(bad.toString(), bad.isEmpty())
    }

    @Test
    fun aFailureIsLoggedWithPlaceAndKindAndAShortMessageOrNone() {
        val error = IllegalStateException("x".repeat(500))
        assertEquals("where: IllegalStateException: " + "x".repeat(200), Diagnostics.describe("where", error))
        // Code whose errors carry the number or text being worked on keeps the message out.
        assertEquals("backup.import: NumberFormatException", Diagnostics.describe("backup.import", NumberFormatException("For input string: \"+39 333 1234567\""), withMessage = false))
    }

    @Test
    fun theMailBodyHoldsTheWordsOfTheUserAndFactsOfTheAppNothingElse() {
        val about = AboutInfo("26.7.2S", 44, "17", 37, "Google Pixel", 60)
        val body = BugReport.body("  it crashes  ", about)
        assertEquals("it crashes\n\n---\nMetroWin Launcher 26.7.2S (44)\nAndroid 17 (API 37)\nGoogle Pixel\n", body)
        assertFalse(body.contains("@"))
    }
}
