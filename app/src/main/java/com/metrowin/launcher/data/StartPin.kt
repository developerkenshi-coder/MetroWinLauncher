package com.metrowin.launcher.data

import android.content.Context
import android.content.Intent
import com.metrowin.launcher.MainActivity

/** The intent a mini app starts to have a note or a contact kept on Start: the launcher pins it and Start comes on screen with the new tile. */
object StartPin {
    fun intent(context: Context, uri: String, label: String): Intent =
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_PIN_URI, uri)
            .putExtra(MainActivity.EXTRA_PIN_LABEL, label)
}
