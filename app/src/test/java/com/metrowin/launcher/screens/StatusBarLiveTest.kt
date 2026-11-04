package com.metrowin.launcher.screens

import android.content.Intent
import android.os.BatteryManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import com.metrowin.launcher.data.BatteryInfo
import com.metrowin.launcher.ui.components.MetroStatusBar
import com.metrowin.launcher.ui.components.rememberBroadcast
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The bar says what the phone says when it says it: a change of the battery is on the bar at once, not at the next poll. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class StatusBarLiveTest : ScreenshotBase() {
    private fun battery(level: Int) = Intent(Intent.ACTION_BATTERY_CHANGED)
        .putExtra(BatteryManager.EXTRA_LEVEL, level).putExtra(BatteryManager.EXTRA_SCALE, 100)
        .putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)

    @Test
    fun theBatteryPercentFollowsTheBroadcastOfAndroid() {
        show {
            // As the overlay of the status bar reads it.
            val intent = rememberBroadcast(Intent.ACTION_BATTERY_CHANGED)
            val info = remember(intent) { intent?.let { BatteryInfo.from(it, context) } }
            MetroStatusBar(info)
        }
        compose.activity.sendBroadcast(battery(80))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        advance(100)
        compose.onNodeWithTag("statusbar:battery").assertTextEquals("80%")
        compose.activity.sendBroadcast(battery(79))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        advance(100)
        compose.onNodeWithTag("statusbar:battery").assertTextEquals("79%")
    }
}
