package com.metrowin.launcher.mail

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import com.metrowin.launcher.ui.mini.PageTurn
import com.metrowin.launcher.ui.mini.returnToLauncher
import com.metrowin.launcher.ui.mini.TurnPage
import com.metrowin.launcher.data.IconLoader
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.notifications.OverlayTheme

class MailActivity : ComponentActivity() {
    private val turn by lazy { PageTurn(intent.getBooleanExtra(PageTurn.EXTRA, false)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        intent?.data?.let(MicrosoftOAuth::deliver)
        val settings = SettingsStore.of(this).load()
        setContent {
            OverlayTheme(settings, remember { IconLoader(applicationContext) }) {
                TurnPage(turn) { MailScreen() }
            }
        }
    }

    /** With the original animation the page turns away first, and the window closes without its own animation. */
    override fun finish() {
        if (!turn.leave(lifecycleScope) { returnToLauncher(); super.finish(); overridePendingTransition(0, 0) }) { returnToLauncher(); super.finish() }
    }

    /** The browser comes back here with the answer of the sign in of Microsoft. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.data?.let(MicrosoftOAuth::deliver)
    }
}
