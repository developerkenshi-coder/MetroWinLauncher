package com.metrowin.launcher.mail

import android.os.Bundle
import androidx.compose.runtime.Composable
import com.metrowin.launcher.ui.mini.MiniActivity

/** The mail app: the shell of the mini apps (look, edge to edge, the page turn) with [MailScreen] in it. */
class MailActivity : MiniActivity() {
    @Composable
    override fun Content() = MailScreen()

    override fun onCreate(savedInstanceState: Bundle?) {
        intent?.data?.let(MicrosoftOAuth::deliver)
        // The accounts are read and the login to the server is made while the screen is drawn, not after it.
        Thread { MailClients.warm(applicationContext) }.start()
        super.onCreate(savedInstanceState)
    }

    /** The browser comes back here with the answer of the sign in of Microsoft. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.data?.let(MicrosoftOAuth::deliver)
    }
}
