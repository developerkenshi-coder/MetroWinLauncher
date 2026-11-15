package com.metrowin.launcher.dialer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.metrowin.launcher.data.PhoneCalls
import com.metrowin.launcher.ui.mini.MiniActivity

/**
 * The phone app of the launcher. Android opens it for a `tel:` link or for "dial" from any app, once the launcher is the phone app of the
 * phone (or the user picks it), and the launcher opens it from the Phone tile. A number in the link is written in the keypad, not called.
 */
open class DialerActivity : MiniActivity() {
    /** The page that opens first: the keypad, to type a number. [PeopleActivity] opens the contacts. */
    protected open val firstPage = PAGE_KEYPAD

    private var number by mutableStateOf("")
    private var initialPage = PAGE_KEYPAD

    override fun onCreate(savedInstanceState: Bundle?) {
        number = savedInstanceState?.getString(STATE_NUMBER) ?: numberIn(intent)
        initialPage = intent.getIntExtra(
            "page",
            when {
                intent.action == "com.metrowin.launcher.ACTION_CONTACTS" -> PAGE_CONTACTS
                number.isNotEmpty() -> PAGE_KEYPAD
                else -> firstPage
            },
        )
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun Content() = DialerScreen(number, { number = it }, onPlaced = { finish() }, initialPage = initialPage)

    /** A turn of the screen recreates the activity: the digits typed are kept. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_NUMBER, number)
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
