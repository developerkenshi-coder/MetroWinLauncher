package com.metrowin.launcher

import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.ui.settings.SettingToggles
import com.metrowin.launcher.ui.settings.SettingsIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingTogglesTest {
    @Test
    fun everySwitchThatCanBeFlippedFromTheSearchIsInTheIndex() {
        val indexed = SettingsIndex.entries.map { it.id }.toSet()
        assertTrue(SettingToggles.ids.isNotEmpty())
        assertEquals(emptySet<String>(), SettingToggles.ids - indexed)
    }

    @Test
    fun writingAndReadingGoTogether() {
        val toggle = SettingToggles.of("apps.compact")!!
        val off = LauncherSettings(compactList = false)
        assertEquals(false, toggle.read(off))
        val on = toggle.write(off, true)
        assertEquals(true, toggle.read(on))
        assertNotEquals(off, on)
    }

    @Test
    fun aChoiceOrAnUnknownIdHasNoSwitch() {
        assertNull(SettingToggles.of("apps.hidden"))
        assertNull(SettingToggles.of(null))
        assertNotNull(SettingToggles.of("voice.preview"))
        assertNotNull(SettingToggles.of("apps.hiddenlock"))
    }
}
