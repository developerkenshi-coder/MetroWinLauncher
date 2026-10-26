package com.metrowin.launcher.ui.pages

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrowin.launcher.LauncherViewModel
import com.metrowin.launcher.data.Favourite
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LivePermissions
import com.metrowin.launcher.data.NewsSources
import com.metrowin.launcher.data.PhoneCalls
import com.metrowin.launcher.data.RecentCall
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.TileData
import com.metrowin.launcher.ui.settings.SettingsSection

/** A page that opens over Start for one of the tiles that do more than show: a timer, the news, the reminders. */
sealed interface Panel {
    data class Timer(val id: String) : Panel
    data class Stopwatch(val id: String) : Panel
    data object News : Panel
    data object Finance : Panel

    /** The reminders; [draft] is a text already written (from the search box) waiting for a time. */
    data class Reminders(val draft: String = "") : Panel
    data object DialGrid : Panel
    data object PeopleHub : Panel
}

/** Which page is open, if any. The pages come and go with a turnstile (see TurnstileHost); Back closes them. */
@Stable
class PanelState {
    var current by mutableStateOf<Panel?>(null)
        private set

    val isOpen: Boolean get() = current != null

    fun open(panel: Panel) {
        current = panel
    }

    fun close() {
        current = null
    }
}

/** Asks for the permission to show notifications (Android 13 and later) when it is missing: a timer or a reminder needs it to be seen. */
@Composable
fun rememberNotificationPermission(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(launcher, context) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

/**
 * The page that is open, drawn with what the launcher knows: the state of a timer comes from its tile, the headlines and the
 * quotes from what was read for the tiles, the reminders from their list. Everything that needs the activity (a permission,
 * the settings) is asked of the caller.
 */
@Composable
fun PanelContent(
    panel: Panel,
    vm: LauncherViewModel,
    items: List<AppItem>,
    settings: LauncherSettings,
    onOpenSettings: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val ensureNotifications = rememberNotificationPermission()
    val direct = settings.voiceCallDirect && LivePermissions.isGranted(context, PhoneCalls.permission)
    when (panel) {
        is Panel.Timer -> {
            val item = items.firstOrNull { it.id == panel.id }
            val state = (item?.data as? TileData.Timer)?.state
            if (item != null && state != null) {
                TimerPage(
                    label = item.label, state = state,
                    onStart = {
                        ensureNotifications()
                        vm.timerStart(panel.id)
                    },
                    onPause = { vm.timerPause(panel.id) },
                    onReset = { vm.timerReset(panel.id) },
                    onLength = { vm.timerLength(panel.id, it) },
                    onAdd = { vm.timerAdd(panel.id, it) },
                    modifier = modifier,
                )
            }
        }
        is Panel.Stopwatch -> {
            val item = items.firstOrNull { it.id == panel.id }
            val state = (item?.data as? TileData.Stopwatch)?.state
            if (item != null && state != null) {
                StopwatchPage(
                    label = item.label, state = state,
                    onStart = { vm.stopwatchStart(panel.id) },
                    onStop = { vm.stopwatchStop(panel.id) },
                    onLap = { vm.stopwatchLap(panel.id) },
                    onReset = { vm.stopwatchReset(panel.id) },
                    modifier = modifier,
                )
            }
        }
        Panel.News -> {
            val live by vm.live.collectAsStateWithLifecycle()
            // Opened before anything was read (the tile was just put there): read now.
            LaunchedEffect(Unit) { if (live.news == null) vm.refreshFeedsNow() }
            NewsPage(
                news = live.news, googleSource = NewsSources.isGoogle(settings.newsSource),
                onOpen = vm::openUrl, onRefresh = vm::refreshFeedsNow, onChooseSource = { onOpenSettings(SettingsSection.LiveTiles) },
                modifier = modifier,
            )
        }
        Panel.Finance -> {
            val live by vm.live.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { if (live.finance == null) vm.refreshFeedsNow() }
            FinancePage(
                finance = live.finance, onOpen = vm::openUrl, onRefresh = vm::refreshFeedsNow,
                onChoose = { onOpenSettings(SettingsSection.LiveTiles) }, modifier = modifier,
            )
        }
        is Panel.Reminders -> {
            val reminders by vm.reminders.collectAsStateWithLifecycle()
            var canExact by remember { mutableStateOf(vm.canScheduleExactAlarms()) }
            // Coming back from the screen of Android where it is allowed.
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canExact = vm.canScheduleExactAlarms() }
            RemindersPage(
                reminders = reminders, draft = panel.draft, canExact = canExact,
                onAdd = { text, at ->
                    val added = vm.addReminder(text, at)
                    if (added) ensureNotifications()
                    added
                },
                onDone = vm::completeReminder, onRemove = vm::removeReminder, onClearDone = vm::clearDoneReminders,
                onAllowExact = vm::openExactAlarmAccess, modifier = modifier,
            )
        }
        Panel.DialGrid -> {
            var hasContacts by remember { mutableStateOf(LivePermissions.isGranted(context, LivePermissions.contacts)) }
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasContacts = it }
            val favourites by produceState<List<Favourite>?>(null, hasContacts) { value = if (hasContacts) vm.favourites() else emptyList() }
            DialGridPage(
                favourites = favourites, hasPermission = hasContacts, onRequestPermission = { permission.launch(LivePermissions.contacts) },
                onCall = { vm.dial(it, direct) }, modifier = modifier,
            )
        }
        Panel.PeopleHub -> {
            var hasContacts by remember { mutableStateOf(LivePermissions.isGranted(context, LivePermissions.contacts)) }
            var hasCalls by remember { mutableStateOf(LivePermissions.isGranted(context, LivePermissions.calls)) }
            val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasContacts = it }
            val callsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCalls = it }
            val favourites by produceState<List<Favourite>?>(null, hasContacts) { value = if (hasContacts) vm.favourites() else emptyList() }
            val calls by produceState<List<RecentCall>?>(null, hasCalls) { value = if (hasCalls) vm.recentCalls() else emptyList() }
            val notifications by vm.notifications.collectAsStateWithLifecycle()
            val labels = remember(items) { items.mapNotNull { app -> app.packageName?.let { it to app.label } }.toMap() }
            PeopleHubPage(
                favourites = favourites, calls = calls, news = notifications, labelOf = { labels[it] ?: it },
                hasContacts = hasContacts, hasCallLog = hasCalls,
                onRequestContacts = { contactsPermission.launch(LivePermissions.contacts) },
                onRequestCallLog = { callsPermission.launch(LivePermissions.calls) },
                onCall = { vm.dial(it, direct) }, onOpenNotification = vm::openNotification, modifier = modifier,
            )
        }
    }
}
