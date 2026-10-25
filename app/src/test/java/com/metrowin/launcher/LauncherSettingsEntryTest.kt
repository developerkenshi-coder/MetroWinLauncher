package com.metrowin.launcher

import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.data.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherSettingsEntryTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun theEntryOfTheAppListIsCalledLauncherSettings() {
        assertEquals("Launcher settings", context.getString(R.string.app_launcher_settings))
    }

    @Test
    @Config(qualifiers = "it")
    fun inItalianItIsImpostazioniLauncher() {
        assertEquals("Impostazioni Launcher", context.getString(R.string.app_launcher_settings))
    }

    @Test
    fun anOldInstallWithTheUntouchedDefaultGetsTheNewButton() {
        // What version 1 saved for the four buttons, as part of the settings JSON.
        val json = """{"quickActions": ["wifi", "bluetooth", "airplane", "flashlight"]}"""
        val settings = SettingsCodec.decode(json)!!
        assertEquals(listOf("wifi", "bluetooth", "flashlight", "launcher"), settings.quickActions)
        SettingsStore(context).save(LauncherSettings(quickActions = listOf("vpn", "cast", "wifi", "camera")))
        assertEquals(listOf("vpn", "cast", "wifi", "camera"), SettingsStore(context).load().quickActions)
    }
}
