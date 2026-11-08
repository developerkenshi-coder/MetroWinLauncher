package com.metrowin.launcher.shizukuterminal

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.ShizukuShell
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.start.DialogFrame
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private class AppRow(val label: String, val pkg: String, val system: Boolean, val frozen: Boolean, val removed: Boolean)

private enum class Filter(val label: Int) { Apps(R.string.st_f_apps), System(R.string.st_f_system), Frozen(R.string.st_f_frozen), Removed(R.string.st_f_removed) }

/** The user this app runs as (the uid holds it above the 100000 range of each user), for `pm --user`: a work profile or a second user is not user 0. */
private fun currentUser(): String = (android.os.Process.myUid() / 100_000).toString()

/**
 * What must not be frozen or removed: the phone stops working, or cannot be brought back without a computer, without them. Besides the fixed ones,
 * the launcher itself and the apps that hold the default phone, messages and home roles.
 */
internal fun protectedPackages(own: String, vararg roles: String?): Set<String> =
    setOf(own, "com.android.systemui", "com.android.settings", "com.android.phone", "com.android.server.telecom", "com.android.providers.telephony", "moe.shizuku.privileged.api") +
        roles.filterNotNull()

private fun rolePackages(context: android.content.Context): Array<String?> = arrayOf(
    runCatching { context.getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage }.getOrNull(),
    if (android.os.Build.VERSION.SDK_INT >= 29) runCatching { context.getSystemService(android.telecom.TelecomManager::class.java)?.systemDialerPackage }.getOrNull() else null,
    runCatching { android.provider.Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull(),
)

/** Freeze, stop, remove and bring back apps (for the current user; the data stays, so a removed app comes back as it was). */
@Composable
fun AppsPage(state: ShizukuState, refresh: Int) {
    val context = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf(Filter.Apps) }
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<AppRow?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val keep = remember { protectedPackages(context.packageName, *rolePackages(context)) }
    val apps by produceState(emptyList<AppRow>(), refresh, reload) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES).map { info ->
                AppRow(
                    pm.getApplicationLabel(info).toString(), info.packageName, info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    !info.enabled, info.flags and ApplicationInfo.FLAG_INSTALLED == 0,
                )
            }.sortedBy { it.label.lowercase(Locale.getDefault()) }
        }
    }
    val shown = apps.filter { app ->
        when (filter) {
            Filter.Apps -> !app.system && !app.removed
            Filter.System -> app.system && !app.removed
            Filter.Frozen -> app.frozen && !app.removed
            Filter.Removed -> app.removed
        } && (query.isBlank() || app.label.contains(query.trim(), true) || app.pkg.contains(query.trim(), true))
    }
    fun run(app: AppRow, vararg command: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { ShizukuShell.run(arrayOf(*command)) }
            message = "${app.label}: ${if (result.ok) context.getString(R.string.st_done) else result.output.lineSequence().firstOrNull().orEmpty().ifEmpty { "[${result.code}]" }}"
            chosen = null
            confirmRemove = false
            reload++
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PageNote(stringResource(if (state.ready) R.string.st_apps_hint else R.string.st_pick_locked))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Filter.entries) { PageChip(stringResource(it.label), it == filter, "st:filter:${it.name}") { filter = it } }
        }
        MetroTextField(query, { query = it }, stringResource(R.string.st_search), Modifier.fillMaxWidth())
        if (message.isNotEmpty()) PageNote(message)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(shown, key = { it.pkg }) { app ->
                Column(Modifier.fillMaxWidth().metroPress(onClick = { if (state.ready) { chosen = app; confirmRemove = false } }).padding(vertical = 8.dp)) {
                    MetroText(app.label, size = 20.sp, maxLines = 1)
                    MetroText(app.pkg, size = 13.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, maxLines = 1)
                }
            }
        }
    }
    chosen?.let { app ->
        DialogFrame(onCancel = { chosen = null }) {
            MetroText(app.label, size = 22.sp, maxLines = 2)
            PageNote(app.pkg)
            when {
                app.pkg == context.packageName -> PageNote(stringResource(R.string.st_self))
                app.pkg in keep && !app.removed -> {
                    PageNote(stringResource(R.string.st_protected))
                    MetroButton(stringResource(R.string.st_force_stop), Modifier.fillMaxWidth()) { run(app, "am", "force-stop", app.pkg) }
                }
                app.removed -> MetroButton(stringResource(R.string.st_restore), Modifier.fillMaxWidth()) { run(app, "cmd", "package", "install-existing", app.pkg) }
                confirmRemove -> {
                    PageNote(stringResource(R.string.st_remove_confirm))
                    MetroButton(stringResource(R.string.st_remove_yes), Modifier.fillMaxWidth()) { run(app, "pm", "uninstall", "-k", "--user", currentUser(), app.pkg) }
                }
                else -> {
                    if (app.frozen) MetroButton(stringResource(R.string.st_unfreeze), Modifier.fillMaxWidth()) { run(app, "pm", "enable", app.pkg) }
                    else MetroButton(stringResource(R.string.st_freeze), Modifier.fillMaxWidth()) { run(app, "pm", "disable-user", "--user", currentUser(), app.pkg) }
                    MetroButton(stringResource(R.string.st_force_stop), Modifier.fillMaxWidth()) { run(app, "am", "force-stop", app.pkg) }
                    MetroButton(stringResource(R.string.st_remove_app), Modifier.fillMaxWidth()) { confirmRemove = true }
                }
            }
        }
    }
}
