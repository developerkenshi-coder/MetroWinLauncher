package com.metrowin.launcher.messages

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.metrowin.launcher.ui.mini.MiniActivity

/**
 * The messages app of the launcher. Android opens it for "send a message" links (`sms:`, `smsto:`) once the launcher is the messaging app
 * of the phone, and the notification of a message opens the conversation of its sender.
 */
class MessagesActivity : MiniActivity() {
    private var address by mutableStateOf<String?>(null)
    private var body by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) read(intent) else address = savedInstanceState.getString(EXTRA_ADDRESS)
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun Content() = MessagesScreen(address, body, onAddress = { address = it }, onBody = { body = it })

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        address?.let { outState.putString(EXTRA_ADDRESS, it) }
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
