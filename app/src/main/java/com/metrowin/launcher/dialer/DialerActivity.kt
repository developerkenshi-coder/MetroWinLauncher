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
open class DialerActivity : ComponentActivity() {
    /** The page that opens first: the keypad, to type a number. [PeopleActivity] opens the contacts. */
    protected open val firstPage = PAGE_KEYPAD

    private val turn by lazy { PageTurn(intent.getBooleanExtra(PageTurn.EXTRA, false)) }
    private var number by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        number = savedInstanceState?.getString(STATE_NUMBER) ?: numberIn(intent)
        val initialPage = intent.getIntExtra(
            "page",
            when {
                intent.action == "com.metrowin.launcher.ACTION_CONTACTS" -> PAGE_CONTACTS
                number.isNotEmpty() -> PAGE_KEYPAD
                else -> firstPage
            },
        )
        val settings = SettingsStore.of(this).load()
        setContent {
            OverlayTheme(settings, remember { IconLoader(applicationContext) }) {
                TurnPage(turn) { DialerScreen(number, { number = it }, onPlaced = { finish() }, initialPage = initialPage) }
            }
        }
    }

    /** A turn of the screen recreates the activity: the digits typed are kept. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_NUMBER, number)
    }

    /** With the original animation the page turns away first, and the window closes without its own animation. */
    override fun finish() {
        if (!turn.leave(lifecycleScope) { returnToLauncher(); super.finish(); overridePendingTransition(0, 0) }) { returnToLauncher(); super.finish() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        numberIn(intent).takeIf { it.isNotEmpty() }?.let { number = it }
    }

    private companion object {
        const val STATE_NUMBER = "number"
    }

    private fun numberIn(intent: Intent) = PhoneCalls.numberOf(intent.dataString.orEmpty().let { android.net.Uri.decode(it) }).orEmpty()
}

/**
 * The "people" of the launcher's mini apps: it hands over to the People hub ([com.metrowin.launcher.people.PeopleActivity]), the real
 * app of the contacts, and closes at once. (It used to open this phone screen on its contacts page, so two apps had the same name.)
 */
class PeopleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { startActivity(Intent(this, com.metrowin.launcher.people.PeopleActivity::class.java).putExtras(intent)) }
        finish()
        overridePendingTransition(0, 0)
    }
}
