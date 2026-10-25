package com.metrowin.launcher.data

import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock

/** What touching a [QuickAnswer] does: an intent to start, or a text to copy. Pure where it can be. */
object QuickAnswerActions {
    /** The intent for the answers that open something; null for the ones that copy (see [copyText]). */
    fun intent(answer: QuickAnswer): Intent? {
        val intent = when (answer) {
            is QuickAnswer.Call -> Intent(Intent.ACTION_DIAL, PhoneCalls.uri(answer.number))
            is QuickAnswer.Message -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("sms", PhoneCalls.clean(answer.number), null))
            is QuickAnswer.Link -> Intent(Intent.ACTION_VIEW, Uri.parse(answer.url))
            is QuickAnswer.Email -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", answer.address, null))
            is QuickAnswer.Timer -> Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, answer.seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            is QuickAnswer.Alarm -> Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, answer.hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, answer.minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            is QuickAnswer.Calculation, is QuickAnswer.Conversion -> return null
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** The text that touching a calculation or a conversion copies: the result alone, ready to paste. */
    fun copyText(answer: QuickAnswer): String? = when (answer) {
        is QuickAnswer.Calculation -> answer.result
        is QuickAnswer.Conversion -> answer.result
        else -> null
    }

    /** Two digits for a clock: 7:05. */
    fun clock(hour: Int, minute: Int): String = "%d:%02d".format(hour, minute)

    /** "1 h 30 min", "45 s": a timer length as the row says it. */
    fun length(seconds: Int): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return listOfNotNull(h.takeIf { it > 0 }?.let { "$it h" }, m.takeIf { it > 0 }?.let { "$it min" }, s.takeIf { it > 0 }?.let { "$it s" })
            .joinToString(" ")
    }
}
