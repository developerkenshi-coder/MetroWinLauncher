package com.metrowin.launcher.shizukuterminal

import com.metrowin.launcher.data.ShizukuShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class TerminalLogicTest {
    @Test
    fun commandsThatLoseDataAskFirst() {
        listOf(
            "rm -rf /sdcard/x", "rm a", "reboot", "pm clear com.a", "pm uninstall -k --user 0 com.a", "settings put global x 1", "setprop a b",
            "cmd package uninstall com.a", "ls; rm -r x", "echo hi | sh -c \"rm -rf /\"", "svc power reboot", "wm size 100x100", "FOO=1 rm x", "/system/bin/rm x",
        ).forEach { assertTrue(it, Command.destructive(it)) }
    }

    @Test
    fun commandsThatOnlyLookDoNotAsk() {
        listOf(
            "dumpsys battery", "getprop ro.build.version.release", "pm list packages -3", "settings get global x", "wm size", "wm size reset", "ls /sdcard",
            "cmd package list packages -d", "echo rm", "grep rm file", "pm enable --user 0 com.a", "logcat -d -t 200 *:W",
        ).forEach { assertFalse(it, Command.destructive(it)) }
    }

    @Test
    fun readOnlyLetsOnlyReadingThrough() {
        listOf("dumpsys battery | grep level", "pm list packages", "settings list global", "wm size", "logcat -d", "df -h /data; uptime", "appops get com.a").forEach { assertTrue(it, Command.readOnly(it)) }
        listOf("settings put global a 1", "pm clear com.a", "echo a > /sdcard/x", "logcat -c", "wm size 1x1", "am force-stop com.a", "find / -delete", "reboot", "ls; rm x").forEach { assertFalse(it, Command.readOnly(it)) }
    }

    @Test
    fun savedCommandsKeepALineWithSeveralLinesAndReadTheOldFormat() {
        val list = listOf("echo a\necho b", "uptime")
        assertEquals(list, SavedCommands.decode(SavedCommands.encode(list)))
        assertEquals(listOf("uptime", "wm size"), SavedCommands.decode("uptime\n\nwm size"))
        assertEquals(STARTERS, SavedCommands.decode(null))
        assertEquals(STARTERS, SavedCommands.decode("[broken"))
    }

    @Test
    fun suggestionsPutThePrefixFirst() {
        val saved = listOf("pm list packages", "dumpsys battery", "wm size", "cmd package list")
        assertEquals(saved, suggestions(saved, " "))
        assertEquals(listOf("pm list packages"), suggestions(saved, "pm"))
        assertEquals(listOf("pm list packages", "cmd package list"), suggestions(saved, "package").sortedBy { saved.indexOf(it) })
        assertTrue(suggestions(saved, "zzz").isEmpty())
    }

    @Test
    fun logLevelsAreReadFromEveryFormatOfLogcat() {
        assertEquals('E', logLevel("11-13 10:20:30.123  1234  1234 E AndroidRuntime: boom"))
        assertEquals('W', logLevel("11-13 10:20:30.123 W/Tag(1234): slow"))
        assertEquals('I', logLevel("I/Tag(12): ok"))
        assertNull(logLevel("total 12"))
        assertNull(logLevel("Error: not a log line"))
    }

    @Test
    fun topIsReadFromTheStateLetterOn() {
        val text = """
            Tasks: 1089 total,   2 running
            800%cpu  22%user
              PID USER         PR  NI VIRT  RES  SHR S[%CPU] %MEM     TIME+ ARGS
             1234 shell        20   0  14G 4.5M 3.2M R 12.0   0.1   0:00.12 top -b -n 1
              900 system       18  -2  20G 200M 90M S  45,5   3.2  10:11.00 /system/bin/surfaceflinger
        """.trimIndent()
        val rows = parseTop(text)
        assertEquals(listOf("surfaceflinger", "top"), rows.map { it.name })
        assertEquals(45.5f, rows[0].cpu, 0.01f)
        assertEquals("900", rows[0].pid)
        assertTrue(parseTop("nothing here").isEmpty())
    }

    @Test
    fun appOpsAreReadAndCycle() {
        val ops = parseAppOps("COARSE_LOCATION: allow; time=+1d ago\nREAD_CONTACTS: ignore\nNo operations.\nWAKE_LOCK: default")
        assertEquals(listOf("COARSE_LOCATION", "READ_CONTACTS", "WAKE_LOCK"), ops.map { it.name })
        assertEquals("ignore", nextAppOpMode("allow"))
        assertEquals("default", nextAppOpMode("ignore"))
        assertEquals("allow", nextAppOpMode("default"))
    }

    @Test
    fun theProtectedListHoldsTheRolesAndTheSystem() {
        val keep = protectedPackages("me", "com.keyboard", null, "com.home")
        listOf("me", "com.keyboard", "com.home", "com.google.android.gms", "com.android.bluetooth", "com.android.webview", "com.android.systemui", "com.android.camera2").forEach { assertTrue(it, it in keep) }
        assertFalse("com.other" in keep)
    }

    @Test
    fun onlyRealPackageNamesGoToTheShell() {
        assertTrue(PACKAGE_NAME.matches("com.metrowin.launcher"))
        assertTrue(PACKAGE_NAME.matches("android"))
        assertFalse(PACKAGE_NAME.matches("com.a; rm -rf /"))
        assertFalse(PACKAGE_NAME.matches("com.a b"))
        assertFalse(PACKAGE_NAME.matches(""))
    }

    @Test
    fun endingsAreTold() {
        assertEquals(Ending.Ok, Ending.of(0))
        assertEquals(Ending.Failed, Ending.of(127))
        assertEquals(Ending.NoShizuku, Ending.of(-1))
        assertEquals(Ending.Timeout, Ending.of(-2))
        assertEquals(Ending.Stopped, Ending.of(-3))
    }

    @Test
    fun aCommandThatCannotRunEndsWithACodeBelowZeroNeverWithAnException() {
        val result = ShizukuShell.shell("id", timeoutMs = 100, cancel = AtomicBoolean(false))
        assertTrue(result.code < 0)
        assertFalse(result.ok)
    }

    @Test
    fun blocksKeepTheOutputAndTrimTheOldest() {
        val tab = Tab("1")
        repeat(3) { i -> tab.blocks.add(Block("c$i").also { it.out = "x".repeat(40) }) }
        tab.trim(100)
        assertEquals(listOf("c1", "c2"), tab.blocks.map { it.command })
        assertTrue(tab.text().startsWith("$ c1\n"))
    }
}
