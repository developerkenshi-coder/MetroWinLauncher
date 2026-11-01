package com.metrowin.launcher.mail

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.metrowin.launcher.data.IconLoader
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.notifications.OverlayTheme

class MailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        intent?.data?.let(MicrosoftOAuth::deliver)
        val settings = SettingsStore(this).load()
        setContent {
            OverlayTheme(settings, remember { IconLoader(applicationContext) }) {
                MailScreen()
            }
        }
    }

    /** The browser comes back here with the answer of the sign in of Microsoft. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.data?.let(MicrosoftOAuth::deliver)
    }
}
