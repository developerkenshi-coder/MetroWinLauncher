package com.metrowin.launcher

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Split screen, free windows and folding phones change the size of the window while the app runs. An activity that does not declare
 * these changes is made anew (typed text, scroll and pages are lost); one that does follows the size (see WindowClass).
 */
class WindowChangesManifestTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun everyScreenFollowsTheSizeOfItsWindow() {
        val tags = Regex("<activity\\b[^>]*>", RegexOption.DOT_MATCHES_ALL).findAll(manifest).map { it.value }.toList()
        assertTrue("no activity found", tags.size >= 20)
        for (tag in tags) {
            val name = Regex("android:name=\"([^\"]+)\"").find(tag)!!.groupValues[1]
            if (name == ".dialer.PeopleActivity") continue // only hands over to the People hub
            val changes = Regex("android:configChanges=\"([^\"]+)\"").find(tag)?.groupValues?.get(1)?.split('|').orEmpty()
            for (need in listOf("orientation", "screenSize", "smallestScreenSize", "screenLayout")) {
                assertTrue("$name does not declare $need", need in changes)
            }
        }
    }
}
