package com.metrowin.launcher.messages

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Android asks the messaging app to declare this receiver and the service below. The messages show the multimedia messages that are
 * already in the phone (pictures, group texts); an incoming one is not downloaded here:
 * shortcut: no download of MMS, needs the WAP push notification parsed and the PDU fetched with SmsManager, add it when MetroWin is the messaging app and pictures must arrive.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
