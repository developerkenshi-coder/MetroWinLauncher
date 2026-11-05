package com.metrowin.launcher.dialer

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
import com.metrowin.launcher.data.PhoneCalls
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.notifications.OverlayTheme

/**
 * The phone app of the launcher. Android opens it for a `tel:` link or for "dial" from any app, once the launcher is the phone app of the
 * phone (or the user picks it), and the launcher opens it from the Phone tile. A number in the link is written in the keypad, not called.
 */
class DialerActivity : ComponentActivity() {
    private val turn by lazy { PageTurn(intent.getBooleanExtra(PageTurn.EXTRA, false)) }
    private var number by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        number = numberIn(intent)
        val initialPage = intent.getIntExtra(
            "page",
            when {
                intent.action == "com.metrowin.launcher.ACTION_CONTACTS" -> PAGE_CONTACTS
                number.isNotEmpty() -> PAGE_KEYPAD
                else -> PAGE_HISTORY
            },
        )
        val settings = SettingsStore.of(this).load()
        setContent {
            OverlayTheme(settings, remember { IconLoader(applicationContext) }) {
                TurnPage(turn) { DialerScreen(number, { number = it }, onPlaced = { finish() }, initialPage = initialPage) }
            }
        }
    }

    /** With the original animation the page turns away first, and the window closes without its own animation. */
    override fun finish() {
        if (!turn.leave(lifecycleScope) { returnToLauncher(); super.finish(); overridePendingTransition(0, 0) }) { returnToLauncher(); super.finish() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        numberIn(intent).takeIf { it.isNotEmpty() }?.let { number = it }
    }

    private fun numberIn(intent: Intent) = PhoneCalls.numberOf(intent.dataString.orEmpty().let { android.net.Uri.decode(it) }).orEmpty()
}
