package com.metrowin.launcher.shizukuterminal

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.metrowin.launcher.R
import com.metrowin.launcher.data.Prefs
import com.metrowin.launcher.data.ShizukuRadio
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.components.pageSide
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.settings.ToggleSetting
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import rikka.shizuku.Shizuku

private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

/** What Shizuku is doing for the launcher: every page asks it before it runs a command. */
enum class ShizukuState {
    /** The Shizuku app is not on the phone. */
    Missing,

    /** It is installed but its service is not running (it has to be started again after every restart of the phone). */
    Stopped,

    /** It runs, the launcher has not been allowed yet. */
    NotAllowed,
    Ready;

    val ready get() = this == Ready

    companion object {
        fun read(context: Context): ShizukuState = when {
            ShizukuRadio.running() && ShizukuRadio.granted() -> Ready
            ShizukuRadio.running() -> NotAllowed
            context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) != null -> Stopped
            else -> Missing
        }
    }
}

@Composable
fun ShizukuTerminalScreen(startPage: Int = 0) {
    val context = LocalContext.current
    val opts = remember { TerminalOptions(context) }
    // What the pages keep while the user swipes away: the pager lets go of a page that is not next to the one in view.
    val session = remember { TerminalSession(Prefs.open(context, Prefs.SHIZUKU), opts.keepHistory.on) }
    val monitor = remember { MonitorBuffers() }
    val appsUi = remember { AppsUi() }
    var state by remember { mutableStateOf(ShizukuState.read(context)) }
    var refresh by remember { mutableIntStateOf(0) }
    // Shizuku tells the launcher when its connection comes, goes, or the user answers its dialog: no polling. Its own app and dialog are other screens, so the answer is also read when the Terminal is back.
    DisposableEffect(Unit) {
        val read = { state = ShizukuState.read(context) }
        val received = Shizuku.OnBinderReceivedListener { read() }
        val dead = Shizuku.OnBinderDeadListener { read() }
        val answer = Shizuku.OnRequestPermissionResultListener { _, _ -> read() }
        runCatching { Shizuku.addBinderReceivedListenerSticky(received); Shizuku.addBinderDeadListener(dead); Shizuku.addRequestPermissionResultListener(answer) }
        onDispose { runCatching { Shizuku.removeBinderReceivedListener(received); Shizuku.removeBinderDeadListener(dead); Shizuku.removeRequestPermissionResultListener(answer) } }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state = ShizukuState.read(context) }
    var copy by remember { mutableStateOf<() -> String>({ "" }) }
    var optionsOpen by remember { mutableStateOf(false) }
    // The screen lock, when the user asked for it: the Terminal and Apps stay closed until it is confirmed (a phone with no lock has nothing to ask).
    val keyguard = remember { context.getSystemService(KeyguardManager::class.java) }
    var unlocked by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { if (it.resultCode == Activity.RESULT_OK) unlocked = true }
    val lockTitle = stringResource(R.string.tm_lock_title)
    val closed = opts.lock.on && keyguard?.isDeviceSecure == true && !unlocked
    val pager = rememberPagerState(initialPage = startPage.coerceIn(0, 6)) { 7 }
    val side = pageSide(24.dp)
    MiniFrame {
        PivotHeader(
            listOf(R.string.st_buttons, R.string.st_battery, R.string.st_monitor, R.string.st_more, R.string.st_terminal, R.string.st_apps, R.string.st_system).map { stringResource(it) },
            pager, Modifier.padding(start = side, top = 12.dp, bottom = 8.dp),
        )
        if (opts.indicator.on) StateLight(state, Modifier.padding(start = side, bottom = 6.dp))
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 0) { page ->
            Column(Modifier.fillMaxSize().padding(horizontal = side)) {
                when {
                    closed && page in 4..5 -> Locked { @Suppress("DEPRECATION") keyguard?.createConfirmDeviceCredentialIntent(lockTitle, null)?.let { ask.launch(it) } }
                    page == 0 -> ButtonsPage(state)
                    page == 1 -> BatteryTab(state, refresh, opts) { copy = it }
                    page == 2 -> MonitorPage(state, refresh, monitor, opts)
                    page == 3 -> MorePage(state, refresh, opts)
                    page == 4 -> TerminalPage(state, session, opts)
                    page == 5 -> AppsPage(state, refresh, appsUi, opts)
                    else -> SystemPage(state, opts)
                }
            }
        }
        val onTerminal = pager.currentPage == 4 && !closed
        AppBar(
            buildList {
                add(AppBarItem("↻", stringResource(R.string.messages_refresh)) { state = ShizukuState.read(context); refresh++ })
                if (pager.currentPage == 1) add(AppBarItem("⧉", stringResource(R.string.st_copy)) { copyText(context, copy()) })
                if (onTerminal) add(AppBarItem("⧉", stringResource(R.string.st_copy)) { copyText(context, session.tab.text()) })
            },
            menu = buildList {
                add(AppBarItem("☰", stringResource(R.string.tm_options)) { optionsOpen = true })
                if (onTerminal) {
                    add(AppBarItem("↺", stringResource(R.string.tm_history)) { session.historyOpen = true })
                    add(AppBarItem("↗", stringResource(R.string.tm_export)) { shareText(context, session.tab.text() + "\n" + session.history.joinToString("\n") { "# $it" }) })
                }
            },
        )
    }
    if (optionsOpen) OptionsDialog(opts, session) { optionsOpen = false }
}

/** The page of the Terminal or Apps while the screen lock has not been confirmed. */
@Composable
private fun Locked(onUnlock: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageNote(stringResource(R.string.tm_locked))
        MetroButton(stringResource(R.string.tm_unlock), Modifier.fillMaxWidth()) { onUnlock() }
    }
}

/** A light and a few words under the title: green-ish (the accent) when Shizuku is ready, grey when it is not. */
@Composable
private fun StateLight(state: ShizukuState, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (state.ready) LocalAccent.current else LocalMetroColors.current.subtle))
        val version = if (state.ready) runCatching { " · v${Shizuku.getVersion()}" }.getOrDefault("") else ""
        MetroText(
            stringResource(
                when (state) {
                    ShizukuState.Ready -> R.string.tm_light_ready
                    ShizukuState.NotAllowed -> R.string.tm_light_not_allowed
                    ShizukuState.Stopped -> R.string.tm_light_stopped
                    ShizukuState.Missing -> R.string.tm_light_missing
                },
            ) + version,
            size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle,
        )
    }
}

/** What the user can switch in this app (the menu "…" of the bar opens it); each switch says what it does. */
@Composable
private fun OptionsDialog(opts: TerminalOptions, session: TerminalSession, onClose: () -> Unit) {
    DialogFrame(onCancel = onClose, title = stringResource(R.string.tm_options)) {
        opts.all.forEach { option ->
            ToggleSetting(
                label = stringResource(option.label), checked = option.on,
                onChange = { on ->
                    option.set(on)
                    // The commands kept on the phone go when the user stops asking to keep them.
                    if (option === opts.keepHistory) { if (on) session.save() else session.clearHistory() }
                },
                hint = stringResource(option.hint), tag = "st:opt:${option.key}",
            )
        }
        MetroText(stringResource(R.string.tm_o_theme), size = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.tm_theme_0, R.string.tm_theme_1, R.string.tm_theme_2, R.string.tm_theme_3).forEachIndexed { i, label ->
                PageChip(stringResource(label), i == opts.theme, "st:theme:$i") { opts.chooseTheme(i) }
            }
        }
        MetroText(stringResource(R.string.tm_o_font), size = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TerminalOptions.FONTS.forEach { size -> PageChip("$size", size == opts.fontSize, "st:font:$size") { opts.chooseFont(size) } }
        }
        MetroButton(stringResource(R.string.tm_close), Modifier.fillMaxWidth()) { onClose() }
    }
}

internal fun copyText(context: Context, text: String) {
    runCatching { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Shizuku Terminal", text)) }
}

/** Offers [text] to the apps that take text (the user picks where it goes). */
internal fun shareText(context: Context, text: String) {
    runCatching {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
