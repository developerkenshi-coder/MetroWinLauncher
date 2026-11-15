package com.metrowin.launcher.shizukuterminal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.metrowin.launcher.R
import com.metrowin.launcher.data.ShizukuShell
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private class AppRow(val label: String, val pkg: String, val system: Boolean, val frozen: Boolean, val removed: Boolean)

internal enum class AppFilter(val label: Int) { Apps(R.string.st_f_apps), System(R.string.st_f_system), Frozen(R.string.st_f_frozen), Removed(R.string.st_f_removed) }

/** The user this app runs as (the uid holds it above the 100000 range of each user), for `pm --user`: a work profile or a second user is not user 0. */
private fun currentUser(): String = (android.os.Process.myUid() / 100_000).toString()

/** What the phone needs on top of the roles: the system, the settings, the calls, the radios, the network, the web view, the camera and Google's services. */
private val FIXED = setOf(
    "android", "com.android.systemui", "com.android.settings", "com.android.phone", "com.android.server.telecom", "com.android.providers.telephony", "moe.shizuku.privileged.api",
    "com.android.shell", "com.android.bluetooth", "com.android.nfc", "com.android.networkstack", "com.google.android.networkstack", "com.android.networkstack.tethering",
    "com.google.android.networkstack.tethering", "com.android.webview", "com.google.android.webview", "com.google.android.gms", "com.google.android.gsf",
    "com.android.providers.settings", "com.android.providers.contacts", "com.android.providers.media", "com.android.providers.downloads", "com.android.camera",
    "com.android.camera2", "com.google.android.GoogleCamera", "com.android.inputmethod.latin", "com.google.android.inputmethod.latin",
    "com.android.packageinstaller", "com.google.android.packageinstaller", "com.android.permissioncontroller", "com.google.android.permissioncontroller",
)

/**
 * What must not be frozen or removed: the phone stops working, or cannot be brought back without a computer, without them. Besides the fixed ones,
 * the launcher itself and the apps that hold the roles of the moment: the dialer, messages, the keyboard, every launcher, the web view and the camera.
 */
internal fun protectedPackages(own: String, vararg roles: String?): Set<String> = FIXED + own + roles.filterNotNull()

private fun rolePackages(context: Context): List<String?> {
    val pm = context.packageManager
    val telecom = context.getSystemService(android.telecom.TelecomManager::class.java)
    val keyboard = runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.let { ComponentName.unflattenFromString(it)?.packageName } }.getOrNull()
    val homes = runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo.packageName } }.getOrDefault(emptyList())
    val camera = runCatching { pm.resolveActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE), 0)?.activityInfo?.packageName }.getOrNull()
    return listOf(
        runCatching { telecom?.defaultDialerPackage }.getOrNull(),
        if (android.os.Build.VERSION.SDK_INT >= 29) runCatching { telecom?.systemDialerPackage }.getOrNull() else null,
        runCatching { android.provider.Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull(),
        keyboard, camera, runCatching { android.webkit.WebView.getCurrentWebViewPackage()?.packageName }.getOrNull(),
    ) + homes
}

private val ICONS = LruCache<String, ImageBitmap>(160)

private val APK_PATH = Regex("/[A-Za-z0-9_./=+~-]+\\.apk")

/** Freeze, stop, remove and bring back apps (for the current user; the data stays, so a removed app comes back as it was). */
@Composable
internal fun AppsPage(state: ShizukuState, refresh: Int, ui: AppsUi, opts: TerminalOptions) {
    val context = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf<AppRow?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    var permissionsOf by remember { mutableStateOf<AppRow?>(null) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val keep = remember { protectedPackages(context.packageName, *rolePackages(context).toTypedArray()) }
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
    val query = ui.query.trim()
    val shown = apps.filter { app ->
        when (ui.filter) {
            AppFilter.Apps -> !app.system && !app.removed
            AppFilter.System -> app.system && !app.removed
            AppFilter.Frozen -> app.frozen && !app.removed
            AppFilter.Removed -> app.removed
        } && (query.isBlank() || app.label.contains(query, true) || app.pkg.contains(query, true))
    }
    val readOnly = opts.readOnly.on
    val timeoutText = stringResource(R.string.tm_end_noshizuku)
    fun run(app: AppRow, vararg command: String) {
        if (!PACKAGE_NAME.matches(app.pkg)) return
        scope.launch {
            val result = withContext(Dispatchers.IO) { ShizukuShell.run(arrayOf(*command)) }
            message = "${app.label}: ${when {
                result.ok -> context.getString(R.string.st_done)
                result.code == -1 -> timeoutText
                else -> result.output.lineSequence().firstOrNull().orEmpty().ifEmpty { "[${result.code}]" }
            }}"
            chosen = null
            confirmRemove = false
            reload++
        }
    }
    val apkDone = stringResource(R.string.tm_apk_done)
    val apkFailed = stringResource(R.string.tm_apk_failed)
    fun copyApk(app: AppRow) {
        scope.launch {
            val dir = "/sdcard/Download/MetroWin/${app.pkg}"
            val ok = withContext(Dispatchers.IO) {
                val paths = ShizukuShell.run(arrayOf("pm", "path", app.pkg)).output.lines().map { it.removePrefix("package:").trim() }.filter { APK_PATH.matches(it) }
                paths.isNotEmpty() && ShizukuShell.run(arrayOf("mkdir", "-p", dir)).ok && ShizukuShell.run(arrayOf("cp", *paths.toTypedArray(), "$dir/")).ok
            }
            message = "${app.label}: ${if (ok) "$apkDone $dir" else apkFailed}"
            chosen = null
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!opts.compact.on) PageNote(stringResource(if (state.ready) R.string.st_apps_hint else R.string.st_pick_locked))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AppFilter.entries) { PageChip(stringResource(it.label), it == ui.filter, "st:filter:${it.name}") { ui.filter = it } }
        }
        MetroTextField(ui.query, { ui.query = it }, stringResource(R.string.st_search), Modifier.fillMaxWidth())
        if (message.isNotEmpty()) PageNote(message)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(shown, key = { it.pkg }) { app ->
                Row(
                    Modifier.fillMaxWidth().metroPress(onClick = { if (state.ready) { chosen = app; confirmRemove = false } }).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (opts.icons.on) AppIcon(context, app.pkg)
                    Column(Modifier.weight(1f)) {
                        MetroText(app.label, size = 20.sp, maxLines = 1)
                        MetroText(app.pkg, size = 13.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, maxLines = 1)
                    }
                }
            }
        }
    }
    chosen?.let { app ->
        DialogFrame(onCancel = { chosen = null }) {
            MetroText(app.label, size = 22.sp, maxLines = 2)
            PageNote(app.pkg)
            when {
                readOnly -> PageNote(stringResource(R.string.tm_readonly_on))
                app.pkg == context.packageName -> PageNote(stringResource(R.string.st_self))
                app.pkg in keep && !app.removed -> {
                    PageNote(stringResource(R.string.st_protected))
                    MetroButton(stringResource(R.string.st_force_stop), Modifier.fillMaxWidth()) { run(app, "am", "force-stop", app.pkg) }
                }
                app.removed -> MetroButton(stringResource(R.string.st_restore), Modifier.fillMaxWidth()) { run(app, "cmd", "package", "install-existing", app.pkg) }
                confirmRemove -> {
                    PageNote(stringResource(R.string.st_remove_confirm))
                    // -k keeps the data: the app can be brought back as it was.
                    MetroButton(stringResource(R.string.st_remove_yes), Modifier.fillMaxWidth()) { run(app, "pm", "uninstall", "-k", "--user", currentUser(), app.pkg) }
                    MetroButton(stringResource(R.string.tm_cancel), Modifier.fillMaxWidth()) { confirmRemove = false }
                }
                else -> {
                    if (app.frozen) MetroButton(stringResource(R.string.st_unfreeze), Modifier.fillMaxWidth()) { run(app, "pm", "enable", "--user", currentUser(), app.pkg) }
                    else MetroButton(stringResource(R.string.st_freeze), Modifier.fillMaxWidth()) { run(app, "pm", "disable-user", "--user", currentUser(), app.pkg) }
                    MetroButton(stringResource(R.string.st_force_stop), Modifier.fillMaxWidth()) { run(app, "am", "force-stop", app.pkg) }
                    MetroButton(stringResource(R.string.st_remove_app), Modifier.fillMaxWidth()) { confirmRemove = true }
                }
            }
            if (opts.advanced.on && !app.removed && PACKAGE_NAME.matches(app.pkg)) {
                if (!readOnly) MetroButton(stringResource(R.string.tm_permissions), Modifier.fillMaxWidth()) { permissionsOf = app; chosen = null }
                MetroButton(stringResource(R.string.tm_copy_apk), Modifier.fillMaxWidth()) { if (!readOnly) copyApk(app) }
            }
        }
    }
    permissionsOf?.let { app -> OpsDialog(app.label, app.pkg) { permissionsOf = null } }
}

@Composable
private fun AppIcon(context: Context, pkg: String) {
    val icon by produceState(ICONS[pkg], pkg) {
        if (value == null) value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()?.also { ICONS.put(pkg, it) }
        }
    }
    Box(Modifier.size(40.dp)) { icon?.let { Image(it, null, Modifier.size(40.dp)) } }
}

/** The operations of an app (appops): a touch moves one from allowed to refused to what the system decides. */
@Composable
private fun OpsDialog(label: String, pkg: String, onClose: () -> Unit) {
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val ops by produceState(emptyList<AppOp>(), pkg, reload) { value = withContext(Dispatchers.IO) { parseAppOps(ShizukuShell.run(arrayOf("appops", "get", pkg)).output) } }
    DialogFrame(onCancel = onClose, title = stringResource(R.string.tm_permissions)) {
        MetroText(label, size = 20.sp, maxLines = 2)
        PageNote(stringResource(R.string.tm_permissions_hint))
        if (ops.isEmpty()) PageNote(stringResource(R.string.tm_permissions_none))
        LazyColumn(Modifier.fillMaxWidth().height(300.dp)) {
            items(ops, key = { it.name }) { op ->
                Row(
                    Modifier.fillMaxWidth().metroPress(onClick = {
                        scope.launch { withContext(Dispatchers.IO) { ShizukuShell.run(arrayOf("appops", "set", pkg, op.name, nextAppOpMode(op.mode))) }; reload++ }
                    }).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MetroText(op.name, size = 14.sp, weight = FontWeight.Normal, modifier = Modifier.weight(1f))
                    MetroText(op.mode, size = 14.sp, weight = FontWeight.SemiBold, color = LocalMetroColors.current.subtle)
                }
            }
        }
        MetroButton(stringResource(R.string.tm_close), Modifier.fillMaxWidth()) { onClose() }
    }
}
