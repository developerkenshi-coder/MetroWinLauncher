package com.metrowin.launcher

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke tests on a device for the small apps added late: each opens, draws for a moment and is still on screen (as in [SmokeTest]). */
@RunWith(AndroidJUnit4::class)
class MiniAppsSmokeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun staysUp(name: String) {
        val intent = Intent().setClassName(context, "com.metrowin.launcher.$name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<android.app.Activity>(intent).use { scenario ->
            Thread.sleep(1500)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    // Without Shizuku the screens show their own "needs Shizuku" note: that is still a screen that stays up.
    @Test fun shizukuTerminal() = staysUp("shizukuterminal.ShizukuTerminalActivity")
    @Test fun systemSettings() = staysUp("systemsettings.SystemSettingsActivity")
    @Test fun recents() = staysUp("recents.RecentsActivity")
    @Test fun power() = staysUp("power.PowerActivity")
}
