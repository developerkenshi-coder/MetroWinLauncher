package com.metrowin.launcher.dialer

import android.provider.ContactsContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.calls.DialerRole
import com.metrowin.launcher.data.RecentCall
import com.metrowin.launcher.data.SimPrefs
import com.metrowin.launcher.data.SimReader
import com.metrowin.launcher.data.SimUse
import com.metrowin.launcher.ui.components.SimSelector
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** The switches of the Phone app, with the three things the menu can open: the statistics, the blocked numbers, the texts for declining. */
@Composable
internal fun OptionsDialog(prefs: PhonePrefs, onTexts: () -> Unit, onDismiss: () -> Unit) {
    DialogFrame(onCancel = onDismiss, title = stringResource(R.string.dialer_options)) {
        Column(Modifier.testTag("dialer:options"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MetroToggle(stringResource(R.string.dialer_opt_confirm_quick), prefs.confirmQuickDial, { prefs.confirmQuickDial = it }, Modifier.testTag("dialer:opt:confirm"))
            MetroToggle(stringResource(R.string.dialer_opt_ask_sim), prefs.askSim, { prefs.askSim = it }, Modifier.testTag("dialer:opt:asksim"))
            SimDefaults()
            MetroToggle(stringResource(R.string.dialer_opt_quick_row), prefs.quickRow, { prefs.quickRow = it }, Modifier.testTag("dialer:opt:quickrow"))
            MetroToggle(stringResource(R.string.dialer_opt_key_tones), prefs.keyTones, { prefs.keyTones = it }, Modifier.testTag("dialer:opt:tones"))
            MetroToggle(stringResource(R.string.dialer_opt_day_headers), prefs.dayHeaders, { prefs.dayHeaders = it }, Modifier.testTag("dialer:opt:days"))
            MetroToggle(stringResource(R.string.dialer_opt_timeline), prefs.timeline, { prefs.timeline = it }, Modifier.testTag("dialer:opt:timeline"))
            MetroToggle(stringResource(R.string.dialer_opt_multi_select), prefs.multiSelect, { prefs.multiSelect = it }, Modifier.testTag("dialer:opt:multi"))
            MetroToggle(stringResource(R.string.dialer_opt_page_fade), prefs.pageFade, { prefs.pageFade = it }, Modifier.testTag("dialer:opt:fade"))
            MetroToggle(stringResource(R.string.dialer_opt_big_text), prefs.bigText, { prefs.bigText = it }, Modifier.testTag("dialer:opt:big"))
            MetroToggle(stringResource(R.string.dialer_opt_ring_animation), prefs.ringAnimation, { prefs.ringAnimation = it }, Modifier.testTag("dialer:opt:rings"))
            MetroToggle(stringResource(R.string.dialer_opt_audio_picker), prefs.audioPicker, { prefs.audioPicker = it }, Modifier.testTag("dialer:opt:audio"))
            MetroToggle(stringResource(R.string.dialer_opt_swipe_answer), prefs.swipeAnswer, { prefs.swipeAnswer = it }, Modifier.testTag("dialer:opt:swipe"))
            MetroToggle(stringResource(R.string.dialer_opt_car_mode), prefs.carMode, { prefs.carMode = it }, Modifier.testTag("dialer:opt:car"))
            MetroToggle(stringResource(R.string.dialer_opt_decline_message), prefs.declineMessage, { prefs.declineMessage = it }, Modifier.testTag("dialer:opt:declinetext"))
        }
        PageNote(stringResource(R.string.dialer_opt_decline_note))
        MetroButton(stringResource(R.string.dialer_opt_edit_texts), Modifier.fillMaxWidth().testTag("dialer:opt:edittexts"), onClick = onTexts)
    }
}

/** The texts offered when a call is declined with a message: three, edited here; a blank one is not offered, and emptying all of them gives the standard ones back. */
@Composable
internal fun DeclineTextsDialog(prefs: PhonePrefs, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val standard = remember { listOf(R.string.incall_text_busy, R.string.incall_text_later, R.string.incall_text_driving).map(context::getString) }
    val fields = remember { mutableStateOf((prefs.declineTexts ?: standard).let { list -> List(PhonePrefs.TEXT_COUNT) { list.getOrElse(it) { "" } } }) }
    DialogFrame(onCancel = onDismiss, title = stringResource(R.string.dialer_opt_edit_texts)) {
        fields.value.forEachIndexed { index, text ->
            MetroTextField(text, { value -> fields.value = fields.value.toMutableList().also { it[index] = value.take(MAX_TEXT) } }, stringResource(R.string.dialer_text_hint), Modifier.testTag("dialer:text:$index"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroButton(stringResource(android.R.string.ok), Modifier.weight(1f).testTag("dialer:text:ok")) {
                val kept = fields.value.map { it.trim() }
                prefs.declineTexts = if (kept.all { it.isEmpty() }) null else kept
                onDismiss()
            }
            MetroButton(stringResource(android.R.string.cancel), Modifier.weight(1f), onClick = onDismiss)
        }
    }
}

/** The numbers of the last 30 days: how many, how long, with whom most. */
@Composable
internal fun StatsDialog(calls: List<RecentCall>, onDismiss: () -> Unit) {
    val stats = remember(calls) { CallHistory.stats(calls, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(DAYS)) }
    DialogFrame(onCancel = onDismiss, title = stringResource(R.string.dialer_stats_title)) {
        Column(Modifier.testTag("dialer:stats"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PageNote(stringResource(R.string.dialer_stats_period, DAYS.toInt()))
            StatRow(stringResource(R.string.dialer_stats_total), stats.total.toString())
            StatRow(stringResource(R.string.dialer_stats_received), stats.received.toString())
            StatRow(stringResource(R.string.dialer_stats_sent), stats.sent.toString())
            StatRow(stringResource(R.string.dialer_stats_missed), stats.missed.toString())
            StatRow(stringResource(R.string.dialer_stats_talk), durationText(stats.talkSeconds))
            StatRow(stringResource(R.string.dialer_stats_longest), durationText(stats.longestSeconds.toLong()))
            if (stats.top.isNotEmpty()) {
                PageNote(stringResource(R.string.dialer_stats_top), Modifier.padding(top = 8.dp))
                stats.top.forEach { (name, count) -> StatRow(name, count.toString()) }
            }
        }
        MetroButton(stringResource(android.R.string.ok), Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MetroText(label, size = 18.sp, weight = FontWeight.Normal, maxLines = 1, modifier = Modifier.weight(1f))
        MetroText(value, size = 18.sp, color = LocalAccent.current)
    }
}

/** The numbers on the blocklist of Android, each with a way to take it off. Only the phone app of the phone can keep that list. */
@Composable
internal fun BlockedDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var reload by remember { mutableStateOf(0) }
    val blocked by produceState<List<Pair<Long, String>>?>(null, reload) { value = withContext(Dispatchers.IO) { DialerRole.blocked(context) } }
    DialogFrame(onCancel = onDismiss, title = stringResource(R.string.dialer_blocked_title)) {
        val list = blocked
        when {
            list == null -> Unit
            list.isEmpty() -> PageNote(stringResource(if (DialerRole.canBlock(context)) R.string.dialer_blocked_none else R.string.dialer_blocked_not_default), Modifier.testTag("dialer:blocked:none"))
            else -> list.forEach { (id, number) ->
                Row(Modifier.fillMaxWidth().testTag("dialer:blocked:$id"), verticalAlignment = Alignment.CenterVertically) {
                    MetroText(prettyNumber(number), size = 20.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    MetroText(
                        stringResource(R.string.dialer_unblock), size = 16.sp, weight = FontWeight.Normal, color = LocalAccent.current,
                        modifier = Modifier.testTag("dialer:unblock:$id").metroPress(onClick = { DialerRole.unblock(context, id); reload++ }).padding(vertical = 12.dp, horizontal = 8.dp),
                    )
                }
            }
        }
        MetroButton(stringResource(android.R.string.ok), Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}

/** A contact with several numbers: which one to call. [onPick] gets the chosen number. */
@Composable
internal fun NumberChooser(name: String, numbers: List<Pair<Int, String>>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    DialogFrame(onCancel = onDismiss, title = name) {
        numbers.forEach { (type, number) ->
            Column(Modifier.fillMaxWidth().testTag("dialer:choose:$number").metroPress(onClick = { onPick(number) }).padding(vertical = 8.dp)) {
                MetroText(prettyNumber(number), size = 22.sp)
                MetroText(
                    ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, type, "").toString(), size = 14.sp, weight = FontWeight.Normal,
                    color = LocalMetroColors.current.subtle,
                )
            }
        }
    }
}

/** The SIM for calls and the SIM for texts the phone uses when a contact has none of its own: "ask" or one of the two SIMs. Shown only with two SIMs. */
@Composable
internal fun SimDefaults() {
    val context = LocalContext.current
    val sims = remember { SimReader.read(context) }
    if (sims.size < 2) return
    val prefs = remember { SimPrefs(context) }
    var call by remember { mutableStateOf(prefs.default(SimUse.Call)) }
    var sms by remember { mutableStateOf(prefs.default(SimUse.Sms)) }
    Column(Modifier.testTag("sim:defaults")) {
        MetroText(stringResource(R.string.sim_default_title), size = 18.sp, color = LocalAccent.current, modifier = Modifier.padding(top = 8.dp))
        SimSelector(stringResource(R.string.sim_default_call), sims, call, { call = it; prefs.setDefault(SimUse.Call, it) }, "sim:default:call")
        SimSelector(stringResource(R.string.sim_default_sms), sims, sms, { sms = it; prefs.setDefault(SimUse.Sms, it) }, "sim:default:sms")
    }
}

/** A long press on a key of the keypad: asks before it calls the number kept on it. */
@Composable
internal fun QuickDialConfirm(label: String, number: String, onYes: () -> Unit, onDismiss: () -> Unit) {
    DialogFrame(onCancel = onDismiss, title = stringResource(R.string.dialer_quick_title)) {
        MetroText(label.ifBlank { prettyNumber(number) }, size = 24.sp)
        if (label.isNotBlank()) MetroText(prettyNumber(number), size = 16.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroButton(stringResource(R.string.dialer_call), Modifier.weight(1f).testTag("dialer:quick:yes"), onClick = onYes)
            MetroButton(stringResource(android.R.string.cancel), Modifier.weight(1f).testTag("dialer:quick:no"), onClick = onDismiss)
        }
    }
}

/** The length of a talk as the statistics show it: hours and minutes, or seconds for a short one. */
private fun durationText(seconds: Long): String = when {
    seconds >= 3600 -> "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else -> "%d:%02d".format(seconds / 60, seconds % 60)
}

private const val DAYS = 30L
private const val MAX_TEXT = 100
