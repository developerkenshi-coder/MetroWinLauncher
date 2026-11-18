package com.metrowin.launcher.mail

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.metrowin.launcher.ui.mini.MiniActivity

/** The mail app: the shell of the mini apps (look, edge to edge, the page turn) with [MailScreen] in it. */
class MailActivity : MiniActivity() {
    // What a notification asked for: the account, and the message when it was one (see [MailSync]); spent when the screen has shown it.
    private var link by mutableStateOf<MailLink?>(null)

    @Composable
    override fun Content() = MailScreen(link = link, onLinkUsed = { link = null })

    override fun onCreate(savedInstanceState: Bundle?) {
        intent?.data?.let(MicrosoftOAuth::deliver)
        if (savedInstanceState == null) link = MailLink.of(intent)
        // The accounts are read and the login to the server is made while the screen is drawn, not after it.
        Thread { MailClients.warm(applicationContext) }.start()
        super.onCreate(savedInstanceState)
    }

    /** The browser comes back here with the answer of the sign in of Microsoft. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let(MicrosoftOAuth::deliver)
        MailLink.of(intent)?.let { link = it }
    }
}

/** The account and the message (its uid) that a mail notification opens; either can be missing. */
data class MailLink(val account: String?, val uid: String?) {
    companion object {
        const val ACCOUNT = "account"
        const val UID = "uid"
        fun of(intent: Intent?): MailLink? {
            val account = intent?.getStringExtra(ACCOUNT)
            return if (account == null) null else MailLink(account, intent.getStringExtra(UID))
        }
    }
}
