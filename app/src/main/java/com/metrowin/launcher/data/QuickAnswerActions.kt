package com.metrowin.launcher.data

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.provider.AlarmClock
import java.util.Locale

/** What touching a [QuickAnswer] does: an intent to start, or a text to copy. Pure where it can be. */
object QuickAnswerActions {
    /** The intent for the answers that open something; null for the ones that copy (see [copyText]). */
    fun intent(answer: QuickAnswer): Intent? {
        val intent = when (answer) {
            is QuickAnswer.Call -> Intent(Intent.ACTION_DIAL, PhoneCalls.uri(answer.number))
            is QuickAnswer.Message -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("sms", PhoneCalls.clean(answer.number), null))
            is QuickAnswer.Link -> Intent(Intent.ACTION_VIEW, answer.url.toUri())
            is QuickAnswer.Email -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", answer.address, null))
            is QuickAnswer.Timer -> Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, answer.seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            is QuickAnswer.Alarm -> Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, answer.hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, answer.minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            is QuickAnswer.Calculation, is QuickAnswer.Conversion, is QuickAnswer.Currency,
            is QuickAnswer.WorldTime, is QuickAnswer.DateOffset, is QuickAnswer.DaysUntil,
            is QuickAnswer.Dice, is QuickAnswer.Coin,
            -> return null
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** The text that touching a calculation or a conversion copies: the result alone, ready to paste. */
    fun copyText(answer: QuickAnswer, locale: Locale = Locale.getDefault()): String? = when (answer) {
        is QuickAnswer.Calculation -> answer.result
        is QuickAnswer.Conversion -> answer.result
        is QuickAnswer.Currency -> answer.result
        is QuickAnswer.WorldTime -> clock(answer.time.hour, answer.time.minute)
        is QuickAnswer.DateOffset -> fullDate(answer.date, locale)
        is QuickAnswer.DaysUntil -> answer.days.toString()
        else -> null
    }

    /** "Friday, 25 December 2026", in the language of the phone. */
    fun fullDate(date: java.time.LocalDate, locale: Locale = Locale.getDefault()): String =
        java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL).withLocale(locale).format(date)

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
