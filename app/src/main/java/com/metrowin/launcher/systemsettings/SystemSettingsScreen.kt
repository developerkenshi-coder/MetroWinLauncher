package com.metrowin.launcher.systemsettings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroProgressBar
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.settings.ToggleSetting
import com.metrowin.launcher.ui.components.DialogFrame
import java.util.Locale

/** What the pivot calls a page. */
private fun Page.title(): Int = when (this) {
    Page.Wifi -> R.string.qa_wifi
    Page.Cellular -> R.string.ss_cellular
    Page.Bluetooth -> R.string.qa_bluetooth
    Page.DateTime -> R.string.ss_datetime
    Page.Sounds -> R.string.ss_sounds
    Page.Display -> R.string.ssx_page_display
    Page.System -> R.string.ssx_page_system
    Page.Info -> R.string.ssx_page_info
}

/** The waiting screen: Shizuku hands its connection over a moment after the process starts. */
@Composable
internal fun SettingsSplash() {
    MiniFrame {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroText(stringResource(R.string.ssx_wait), size = 22.sp)
            MetroProgressBar()
            PageNote(stringResource(R.string.ssx_why))
        }
    }
}

/** [reduced]: Shizuku is not ready, so only what public APIs can read or change is shown; [onRetry] looks for Shizuku again. */
@Composable
internal fun SystemSettingsScreen(reduced: Boolean = false, onRetry: () -> Unit = {}) {
    val context = LocalContext.current
    val opts = remember { Opts(context) }
    var refresh by remember { mutableIntStateOf(0) }
    var options by remember { mutableStateOf(false) }
    val dialogs = remember { DialogSlot() }
    CompositionLocalProvider(LocalOpts provides opts, LocalDialogSlot provides dialogs) {
        val pages = SettingsLogic.pages(reduced, opts[Option.MorePages])
        val pager = rememberPagerState { pages.size }
        MiniFrame {
            PivotHeader(
                pages.map { stringResource(it.title()).lowercase(Locale.getDefault()) },
                pager, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp),
            )
            if (reduced) PageNote(stringResource(R.string.ssx_reduced_note), Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 0) { index ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (pages.getOrNull(index)) {
                        Page.Wifi -> WifiPage(refresh)
                        Page.Cellular -> CellularPage()
                        Page.Bluetooth -> BluetoothPage()
                        Page.DateTime -> DateTimePage()
                        Page.Sounds -> SoundsPage()
                        Page.Display -> DisplayPage()
                        Page.System -> SystemPage()
                        Page.Info -> InfoPage()
                        null -> Unit
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            val items = buildList {
                if (reduced) add(AppBarItem("↻", stringResource(R.string.messages_refresh)) { onRetry() })
                else if (pages.getOrNull(pager.currentPage) == Page.Wifi) add(AppBarItem("↻", stringResource(R.string.messages_refresh)) { refresh++ })
                add(AppBarItem("⚙", stringResource(R.string.ss_android)) { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } })
            }
            AppBar(items, menu = listOf(AppBarItem("☰", stringResource(R.string.ssx_options)) { options = true }))
        }
        if (options) OptionsDialog(opts) { options = false }
        dialogs.content?.invoke()
    }
}

@Composable
private fun OptionsDialog(opts: Opts, onClose: () -> Unit) {
    val context = LocalContext.current
    var history by remember { mutableStateOf(SettingsPrefs.history(context)) }
    DialogFrame(onCancel = onClose) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MetroText(stringResource(R.string.ssx_options), size = 22.sp)
            Option.entries.forEach { option ->
                ToggleSetting(label = stringResource(option.label), checked = opts[option], onChange = { opts.set(option, it) }, hint = stringResource(option.hint), tag = "ss:opt:${option.key}")
            }
            if (opts[Option.History]) {
                MetroText(stringResource(R.string.ssx_history_title), size = 22.sp, modifier = Modifier.padding(top = 8.dp))
                if (history.isEmpty()) PageNote(stringResource(R.string.ssx_history_empty))
                history.forEach { PageNote(it) }
                if (history.isNotEmpty()) MetroButton(stringResource(R.string.ssx_history_clear), Modifier.fillMaxWidth()) { SettingsPrefs.clearHistory(context); history = emptyList() }
            }
        }
    }
}
