package com.metrowin.launcher.data

import android.content.Intent
import android.net.Uri

/** What the voice commands that leave the launcher start: directions in a maps app, a text message in the messages app. */
object VoiceActions {
    /** The addresses to try, in order, for directions to [destination]: the navigation of Google Maps, then any maps app. */
    fun navigationUris(destination: String): List<String> {
        val place = Uri.encode(destination.trim())
        return listOf("google.navigation:q=$place", "geo:0,0?q=$place")
    }

    /** The messages app with [number] as the receiver and [text] written in; the user sends it. */
    fun smsIntent(number: String, text: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", PhoneCalls.clean(number), null))
            .putExtra("sms_body", text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
