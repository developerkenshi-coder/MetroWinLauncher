package com.metrowin.launcher.data

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
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
            is QuickAnswer.CalendarEvent -> Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
                if (answer.title.isNotBlank()) putExtra(CalendarContract.Events.TITLE, answer.title)
                answer.start?.let { start ->
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + if (answer.allDay) DAY_MS else HOUR_MS)
                    if (answer.allDay) putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                }
            }
            is QuickAnswer.NearMe -> Intent(Intent.ACTION_VIEW, nearMeUri(answer.what).toUri())
            QuickAnswer.Scan -> Intent(Intent.ACTION_VIEW, LENS_URI.toUri()).setPackage(GOOGLE_APP)
            // These are done by the launcher itself, not by another app.
            is QuickAnswer.Calculation, is QuickAnswer.Conversion, is QuickAnswer.Currency,
            is QuickAnswer.WorldTime, is QuickAnswer.DateOffset, is QuickAnswer.DaysUntil,
            is QuickAnswer.Dice, is QuickAnswer.Coin, is QuickAnswer.Reminder, is QuickAnswer.Note, is QuickAnswer.Command,
            -> return null
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** [what] on the map, around where the user is: Google Maps takes a `geo:` address with no position as "near me". */
    fun nearMeUri(what: String): String = "geo:0,0?q=" + Uri.encode(what)

    /** The note as text to share to Google Keep. */
    fun noteIntent(text: String): Intent =
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The camera, for a phone that has no Google Lens. */
    fun cameraIntent(): Intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
    const val GOOGLE_KEEP = "com.google.android.keep"
    const val GOOGLE_CALENDAR = "com.google.android.calendar"
    const val GOOGLE_MAPS = "com.google.android.apps.maps"
    private const val LENS_URI = "googleapp://lens"
    private const val HOUR_MS = 60L * 60 * 1000
    private const val DAY_MS = 24L * HOUR_MS

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
