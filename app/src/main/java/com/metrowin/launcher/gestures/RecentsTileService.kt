package com.metrowin.launcher.gestures

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.metrowin.launcher.R

/**
 * A tile for the Quick Settings (the panel that comes down from the top, in any app) that opens the recent apps at
 * once. The swipe up and hold of Android's gesture navigation is handled by the system, and with a launcher that is not
 * the phone's own some phones make it wait; this goes through the accessibility service of the screen lock, which needs
 * no gesture, so there is nothing to wait for. When the service is off the tile takes the user to where it is turned on.
 */
class RecentsTileService : TileService() {
    override fun onStartListening() {
        val tile = qsTile ?: return
        tile.label = getString(R.string.qa_recents)
        tile.state = Tile.STATE_INACTIVE
        tile.updateTile()
    }

    // The Intent version is only used before Android 14, where it is the only one; from 14 on the PendingIntent version is.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        if (LockAccessibilityService.recentsFromPanel()) return
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION") // the Intent version is the only one before Android 14
                startActivityAndCollapse(intent)
            }
        }
    }
}
