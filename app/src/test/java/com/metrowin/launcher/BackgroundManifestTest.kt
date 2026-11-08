package com.metrowin.launcher

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The manifest side of the background work (docs/progetti/sfondo-affidabile.md): what Android needs to see so that jobs survive a reboot and
 * foreground services are allowed. A manufacturer's task killer is another matter and cannot be tested here.
 */
class BackgroundManifestTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.map { it.readText() }.toList()

    /** The tags of `<service ...>` as written. */
    private val services = Regex("<service\\b[^>]*>", RegexOption.DOT_MATCHES_ALL).findAll(manifest).map { it.value }.toList()

    @Test
    fun jobsThatAreKeptAcrossRebootsHaveThePermissionToBeRestored() {
        val persisted = sources.any { "setPersisted(true)" in it }
        assertTrue("a job is persisted", persisted)
        assertTrue("android.permission.RECEIVE_BOOT_COMPLETED" in manifest)
    }

    @Test
    fun everyJobTheCodeSchedulesIsAServiceOfTheManifestWithTheJobPermission() {
        val jobs = sources.flatMap { Regex("JobInfo\\.Builder\\([^,]+,\\s*ComponentName\\([^,]+,\\s*(\\w+)::class\\.java\\)").findAll(it).map { m -> m.groupValues[1] } }.toSet()
        assertTrue("no job found", jobs.size >= 5)
        for (job in jobs) {
            val tag = services.firstOrNull { Regex("android:name=\"[\\w.]*\\b$job\"").containsMatchIn(it) }
            assertTrue("$job is not declared as a service", tag != null)
            assertTrue("$job needs BIND_JOB_SERVICE", "android:permission=\"android.permission.BIND_JOB_SERVICE\"" in tag!!)
        }
    }

    @Test
    fun everyForegroundServiceTypeHasItsPermission() {
        assertTrue("android.permission.FOREGROUND_SERVICE\"" in manifest)
        val types = services.flatMap { Regex("foregroundServiceType=\"([\\w|]+)\"").findAll(it).flatMap { m -> m.groupValues[1].split('|') } }.toSet()
        assertTrue("no foreground service found", types.isNotEmpty())
        for (type in types) {
            val permission = "android.permission.FOREGROUND_SERVICE_" + type.replace(Regex("([A-Z])"), "_$1").uppercase()
            assertTrue("$type needs $permission", "\"$permission\"" in manifest)
        }
    }

    @Test
    fun theAlarmsAreSetAgainAfterARebootAndAnUpdate() {
        val receiver = Regex("<receiver\\b[^>]*AlertReceiver[^>]*>.*?</receiver>", RegexOption.DOT_MATCHES_ALL).find(manifest)?.value
        assertTrue("AlertReceiver is not declared", receiver != null)
        assertTrue("android.intent.action.BOOT_COMPLETED" in receiver!! && "android.intent.action.MY_PACKAGE_REPLACED" in receiver)
        assertTrue("android.permission.SCHEDULE_EXACT_ALARM" in manifest)
    }
}
