package com.metrowin.launcher

import com.metrowin.launcher.ui.settings.RecentPages
import com.metrowin.launcher.ui.settings.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentPagesTest {
    @Test
    fun newestFirstNoRepeatsAtMostThree() {
        var list = emptyList<SettingsSection>()
        listOf(SettingsSection.Start, SettingsSection.Lock, SettingsSection.Start, SettingsSection.Screen, SettingsSection.NavBar).forEach { list = RecentPages.push(list, it) }
        assertEquals(listOf(SettingsSection.NavBar, SettingsSection.Screen, SettingsSection.Start), list)
    }

    @Test
    fun readSkipsNamesThatNoLongerExist() {
        assertEquals(listOf(SettingsSection.Lock, SettingsSection.Start), RecentPages.read("Lock,Gone,Start,Lock"))
        assertEquals(emptyList<SettingsSection>(), RecentPages.read(null))
    }
}
