package com.metrowin.launcher.messages

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import com.metrowin.launcher.ui.mini.PageTurn
import com.metrowin.launcher.ui.mini.returnToLauncher
import com.metrowin.launcher.ui.mini.TurnPage
import androidx.compose.runtime.setValue
import com.metrowin.launcher.data.IconLoader
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.notifications.OverlayTheme

/**
 * The messages app of the launcher. Android opens it for "send a message" links (`sms:`, `smsto:`) once the launcher is the messaging app
 * of the phone, and the notification of a message opens the conversation of its sender.
 */
class MessagesActivity : ComponentActivity() {
    private val turn by lazy { PageTurn(intent.getBooleanExtra(PageTurn.EXTRA, false)) }
    private var address by mutableStateOf<String?>(null)
    private var body by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        read(intent)
        val settings = SettingsStore(this).load()
        setContent {
            OverlayTheme(settings, remember { IconLoader(applicationContext) }) {
                TurnPage(turn) { MessagesScreen(address, body, onAddress = { address = it }, onBody = { body = it }) }
            }
        }
    }

    /** With the original animation the page turns away first, and the window closes without its own animation. */
    override fun finish() {
        if (!turn.leave(lifecycleScope) { returnToLauncher(); super.finish(); overridePendingTransition(0, 0) }) { returnToLauncher(); super.finish() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)
    }

    private fun read(intent: Intent) {
        val target = intent.getStringExtra(EXTRA_ADDRESS) ?: intent.data?.schemeSpecificPart?.substringBefore('?')?.trim()
        if (!target.isNullOrEmpty()) address = target
        intent.getStringExtra("sms_body")?.let { body = it }
    }

    companion object {
        const val EXTRA_ADDRESS = "address"
    }
}
