package com.metrowin.launcher.shizukuterminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.metrowin.launcher.R
import com.metrowin.launcher.data.ShizukuRadio
import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.mini.MiniFrame

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
    var state by remember { mutableStateOf(ShizukuState.read(context)) }
    var refresh by remember { mutableIntStateOf(0) }
    // Shizuku hands its connection to the launcher a moment after the process starts, and the user may start it or allow the launcher with this screen open: the state is looked at again, not read once.
    LaunchedWhileStarted(Unit) {
        while (true) {
            delay(1_500)
            val now = ShizukuState.read(context)
            if (now != state) state = now
        }
    }
    var copy by remember { mutableStateOf<() -> String>({ "" }) }
    // Shizuku's own app and its dialog are other screens: the answer is read again when the Terminal is back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state = ShizukuState.read(context) }
    val pager = rememberPagerState(initialPage = startPage.coerceIn(0, 6)) { 7 }
    MiniFrame {
        PivotHeader(
            listOf(R.string.st_buttons, R.string.st_battery, R.string.st_monitor, R.string.st_more, R.string.st_terminal, R.string.st_apps, R.string.st_system).map { stringResource(it) },
            pager, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp),
        )
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 0) { page ->
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                when (page) {
                    0 -> ButtonsPage(state)
                    1 -> BatteryTab(state, refresh) { copy = it }
                    2 -> MonitorPage(state, refresh)
                    3 -> MorePage(state, refresh)
                    4 -> TerminalPage(state)
                    5 -> AppsPage(state, refresh)
                    else -> SystemPage(state)
                }
            }
        }
        AppBar(
            buildList {
                add(AppBarItem("↻", stringResource(R.string.messages_refresh)) { state = ShizukuState.read(context); refresh++ })
                if (pager.currentPage == 1) add(AppBarItem("⧉", stringResource(R.string.st_copy)) { copyText(context, copy()) })
            },
        )
    }
}

internal fun copyText(context: Context, text: String) {
    runCatching { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Shizuku Terminal", text)) }
}
