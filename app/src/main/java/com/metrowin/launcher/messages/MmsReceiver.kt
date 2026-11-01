package com.metrowin.launcher.messages

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Android asks the messaging app to declare this receiver and the service below. Multimedia messages are not read or sent:
 * shortcut: text messages only, add the MMS download when pictures in messages are needed.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
