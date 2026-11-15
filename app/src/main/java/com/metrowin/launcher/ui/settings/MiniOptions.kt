package com.metrowin.launcher.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import com.metrowin.launcher.R
import com.metrowin.launcher.calculator.CalcOption
import com.metrowin.launcher.calculator.CalcOptions
import com.metrowin.launcher.calendar.CalendarOptions
import com.metrowin.launcher.calendar.Option as CalendarOption
import com.metrowin.launcher.data.MetroCatalog
import com.metrowin.launcher.data.Prefs
import com.metrowin.launcher.datasense.DsOptions
import com.metrowin.launcher.datasense.Opt as DataOpt
import com.metrowin.launcher.mail.MailOption
import com.metrowin.launcher.mail.MailOptions
import com.metrowin.launcher.messages.MessagesOptions
import com.metrowin.launcher.recorder.Opt as RecorderOpt
import com.metrowin.launcher.recorder.RecorderOptions
import com.metrowin.launcher.recorder.RecorderPrefs

/**
 * One switch of a mini app as the settings show it. It reads and writes the very preference the "options" menu of the app does, so the two
 * places never disagree (an app that is open keeps what it read when it started until it is opened again).
 */
class MiniOption(
    val app: String,
    key: String,
    @param:StringRes val label: Int,
    @param:StringRes val hint: Int?,
    val read: (Context) -> Boolean,
    val write: (Context, Boolean) -> Unit,
) {
    val id = "mini.$app.$key"
}

/**
 * The switches of the mini apps, in one list: the page "Mini apps" shows them and the [SettingsIndex] finds them, so a search for "week numbers"
 * or "delivery reports" leads to the switch. The options that are not a plain switch (the quality of a recording, the signature of Mail)
 * and the one that asks for a permission when it turns on (the SIM choice of Messages) stay in the menu of their app.
 */
object MiniOptions {
    private val messagesLabels = mapOf(
        MessagesOptions.Key.Bubbles to R.string.messages_opt_bubbles, MessagesOptions.Key.Avatars to R.string.messages_opt_avatars,
        MessagesOptions.Key.Live to R.string.messages_opt_live, MessagesOptions.Key.Panes to R.string.messages_opt_panes,
        MessagesOptions.Key.Quick to R.string.messages_opt_quick, MessagesOptions.Key.Delivery to R.string.messages_opt_delivery,
        MessagesOptions.Key.Otp to R.string.messages_opt_otp, MessagesOptions.Key.Block to R.string.messages_opt_block,
        MessagesOptions.Key.Lock to R.string.messages_opt_lock,
    )

    val all: List<MiniOption> = buildList {
        MessagesOptions.Key.entries.forEach { key ->
            val label = messagesLabels[key] ?: return@forEach
            add(MiniOption("messaging", key.id, label, null, { MessagesOptions(it).on(key) }, { c, v -> MessagesOptions(c).set(key, v) }))
        }
        MailOption.entries.forEach { o -> add(MiniOption("mail", o.key, o.label, o.hint, { MailOptions.on(it, o) }, { c, v -> MailOptions.set(c, o, v) })) }
        CalendarOption.entries.forEach { o -> add(MiniOption("calendar", o.key, o.label, null, { CalendarOptions(it)[o] }, { c, v -> CalendarOptions(c).set(o, v) })) }
        CalcOption.entries.forEach { o -> add(MiniOption("calculator", o.key.removePrefix("opt_"), o.title, o.hint, { CalcOptions(it)[o] }, { c, v -> CalcOptions(c)[o] = v })) }
        DataOpt.entries.forEach { o ->
            add(MiniOption("datasense", o.key, o.label, null, { DsOptions.read(Prefs.open(it, Prefs.DATA_SENSE), o) }, { c, v -> DsOptions(Prefs.open(c, Prefs.DATA_SENSE)).set(o, v) }))
        }
        RecorderOpt.entries.filter { it.max == 1 }.forEach { o ->
            add(MiniOption("voicerecorder", o.key, recorderLabel(o), null, { RecorderPrefs.on(it, o) }, { c, v -> RecorderOptions(c)[o] = if (v) 1 else 0 }))
        }
    }

    val byId: Map<String, MiniOption> = all.associateBy { it.id }

    /** The apps that have switches, in the order of the list. */
    val apps: List<String> = all.map { it.app }.distinct()

    @StringRes
    fun appLabel(app: String): Int = MetroCatalog.apps.first { it.id == app }.label
}
