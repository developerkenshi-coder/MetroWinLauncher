package com.metrowin.launcher.ui.tutorial

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.metrowin.launcher.data.HomeApp
import com.metrowin.launcher.data.Need
import com.metrowin.launcher.data.NeedChecker

/**
 * The tour with the real requests behind its buttons: a permission dialog, the dialog of Android for the Home app, or the
 * screen of the settings where the user turns the rest on. What is granted is read again whenever the launcher comes
 * back to the front, since the user may have changed it in the settings.
 */
@Composable
fun TutorialHost(
    pager: PagerState,
    onFinish: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenAccessibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val checker = remember(context) { NeedChecker(context) }
    var met by remember { mutableStateOf(checker.met()) }
    var denied by remember { mutableStateOf(emptySet<Need>()) }
    var requesting by remember { mutableStateOf<Need?>(null) }

    fun refresh() {
        met = checker.met()
        denied = denied.filterTo(HashSet()) { it !in met }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // The dialog gave an answer and nothing is granted: Android will not show it again for this permission.
        val asked = requesting
        met = checker.met()
        if (asked != null && asked !in met) denied = denied + asked
        requesting = null
    }
    val home = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    fun request(need: Need) {
        when {
            need in denied -> openAppSettings(context)
            need == Need.HomeApp -> runCatching { home.launch(HomeApp.requestIntent(context)) }
            need == Need.NotificationAccess -> onOpenNotificationAccess()
            need == Need.LockService -> onOpenAccessibility()
            need == Need.Battery -> openBatterySettings(context)
            else -> {
                requesting = need
                runtime.launch(need.permissions.toTypedArray())
            }
        }
    }

    TutorialScreen(pager, met, ::request, onFinish, modifier, denied)
}

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }
}

/**
 * Asks that the launcher not be limited by battery optimization; Android's list of such apps if that dialog is missing.
 * Google Play allows this only to some kinds of app: for a build that is not published there it is the way to keep a
 * launcher, which has to hear about notifications and sync, from being closed in the background.
 */
@SuppressLint("BatteryLife")
private fun openBatterySettings(context: Context) {
    val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
    try {
        context.startActivity(ask)
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}
