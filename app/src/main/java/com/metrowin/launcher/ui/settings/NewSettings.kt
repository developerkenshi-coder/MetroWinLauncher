package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.AppSearch
import com.metrowin.launcher.data.Currencies
import com.metrowin.launcher.data.Finance
import com.metrowin.launcher.data.FinanceItems
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.NewsSources
import com.metrowin.launcher.data.TileTransition
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppSquare
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.SettingLabel
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.theme.LocalMetroColors
import java.util.Locale

/** How the faces of a live tile follow one another. */
@Composable
fun TileTransitionSetting(env: SettingsEnv) {
    OptionList(
        label = stringResource(R.string.transition_title),
        options = TileTransition.entries.map { stringResource(it.label) },
        selected = TileTransition.entries.indexOf(env.settings.tileTransition),
        onSelect = { index -> env.onChange { it.copy(tileTransition = TileTransition.entries[index]) } },
        hint = stringResource(R.string.transition_hint),
        tag = "live:transition", id = "live.transition",
    )
}

private fun topicString(topic: String): Int = when (topic) {
    "WORLD" -> R.string.news_topic_WORLD
    "NATION" -> R.string.news_topic_NATION
    "BUSINESS" -> R.string.news_topic_BUSINESS
    "TECHNOLOGY" -> R.string.news_topic_TECHNOLOGY
    "ENTERTAINMENT" -> R.string.news_topic_ENTERTAINMENT
    "SPORTS" -> R.string.news_topic_SPORTS
    "SCIENCE" -> R.string.news_topic_SCIENCE
    else -> R.string.news_topic_HEALTH
}

/** Where the News tile reads from: Google News (the default), one of its sections, or a feed of your own. */
@Composable
fun NewsSourceSetting(env: SettingsEnv) {
    val settings = env.settings
    val choices = NewsSources.choices
    val labels = choices.map { code ->
        when {
            code == NewsSources.GOOGLE_TOP -> stringResource(R.string.news_source_google)
            code == NewsSources.CUSTOM -> stringResource(R.string.news_source_custom)
            else -> stringResource(topicString(NewsSources.topicOf(code) ?: "WORLD"))
        }
    }
    Column(Modifier.settingAnchor("live.news"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DropdownSetting(
            label = stringResource(R.string.news_source_title),
            options = labels,
            selected = choices.indexOf(NewsSources.normalize(settings.newsSource)).coerceAtLeast(0),
            onSelect = { index -> env.onChange { it.copy(newsSource = choices[index]) } },
            tag = "live:news",
        )
        if (settings.newsSource == NewsSources.CUSTOM) {
            MetroTextField(
                value = settings.newsUrl,
                onValueChange = { text -> env.onChange { it.copy(newsUrl = text.take(NewsSources.MAX_URL)) } },
                placeholder = stringResource(R.string.news_url_hint),
                modifier = Modifier.testTag("live:newsurl"),
            )
            val good = NewsSources.customUrl(settings.newsUrl) != null
            if (settings.newsUrl.isNotBlank()) {
                SettingHint(stringResource(if (good) R.string.news_url_ok else R.string.news_url_bad), Modifier.testTag("live:newsurl:state"))
            }
        }
        SettingHint(stringResource(R.string.news_source_hint))
    }
}

/** The currency the Finance tile gives its values in. */
@Composable
fun FinanceBaseSetting(env: SettingsEnv) {
    val settings = env.settings
    val region = Finance.baseFor("", Locale.getDefault())
    val codes = Currencies.codes.sorted()
    val options = listOf(stringResource(R.string.finance_base_auto, region)) + codes
    DropdownSetting(
        label = stringResource(R.string.finance_base_title),
        options = options,
        selected = if (settings.financeBase.isEmpty()) 0 else codes.indexOf(settings.financeBase) + 1,
        onSelect = { index ->
            env.onChange { s ->
                val base = if (index == 0) "" else codes[index - 1]
                s.copy(financeBase = base, financeItems = FinanceItems.normalize(s.financeItems, Finance.baseFor(base, Locale.getDefault())))
            }
        },
        tag = "live:financebase", id = "live.financebase",
    )
}

/** The currencies and the coins the Finance tile follows: chips, up to [FinanceItems.MAX] lit at a time. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FinanceItemsSetting(env: SettingsEnv) {
    val settings = env.settings
    val base = Finance.baseFor(settings.financeBase, Locale.getDefault())
    val chosen = FinanceItems.normalize(settings.financeItems, base)
    Column(Modifier.settingAnchor("live.financeitems")) {
        SettingLabel(stringResource(R.string.finance_items_title))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FinanceItems.available(base).forEach { item ->
                val on = item in chosen
                PageChip(FinanceItems.symbol(item), on, "live:finance:$item") {
                    env.onChange { s ->
                        val now = FinanceItems.normalize(s.financeItems, base)
                        // A fifth choice takes the place of the oldest one.
                        s.copy(financeItems = if (item in now) now - item else (now + item).takeLast(FinanceItems.MAX))
                    }
                }
            }
        }
        SettingHint(stringResource(R.string.finance_items_hint), Modifier.padding(top = 8.dp))
    }
}

/** The apps of the quick status of the lock screen, one slot each, and the app of the detailed status. */
@Composable
fun LockQuickSettings(env: SettingsEnv) {
    val settings = env.settings
    // The setting keeps the packages (what a notification says it comes from); the pickers speak in the ids of the apps.
    fun idOf(pkg: String) = env.apps.firstOrNull { it.packageName == pkg }?.id.orEmpty()
    fun packageOf(id: String) = env.apps.firstOrNull { it.id == id }?.packageName.orEmpty()
    val slots = (settings.lockQuickApps + List(LauncherSettings.MAX_QUICK_APPS) { "" }).take(LauncherSettings.MAX_QUICK_APPS)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingHeading(stringResource(R.string.lock_quick_title))
        ToggleSetting(
            stringResource(R.string.lock_quick), settings.lockQuickStatus,
            onChange = { value -> env.onChange { it.copy(lockQuickStatus = value) } },
            hint = stringResource(R.string.lock_quick_hint), tag = "lock:quick", id = "lock.quick",
        )
        if (settings.lockQuickStatus) {
            Column(Modifier.settingAnchor("lock.quickapps"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingLabel(stringResource(R.string.lock_quick_apps))
                slots.forEachIndexed { index, pkg ->
                    AppPickerSetting(
                        label = stringResource(R.string.lock_quick_slot, index + 1),
                        apps = env.apps, selectedId = idOf(pkg),
                        onSelect = { picked ->
                            val chosen = packageOf(picked)
                            if (chosen.isNotEmpty()) {
                                env.onChange { s ->
                                    val list = (s.lockQuickApps + List(LauncherSettings.MAX_QUICK_APPS) { "" }).take(LauncherSettings.MAX_QUICK_APPS).toMutableList()
                                    list[index] = chosen
                                    s.copy(lockQuickApps = list.filter { it.isNotEmpty() }.distinct())
                                }
                            }
                        },
                        onClear = if (pkg.isNotEmpty()) ({ env.onChange { s -> s.copy(lockQuickApps = s.lockQuickApps.filter { it != pkg }) } }) else null,
                        tag = "lock:quickapp:$index",
                    )
                }
            }
            Column(Modifier.settingAnchor("lock.detail"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppPickerSetting(
                    label = stringResource(R.string.lock_detail),
                    apps = env.apps, selectedId = idOf(settings.lockDetailApp),
                    onSelect = { picked -> env.onChange { it.copy(lockDetailApp = packageOf(picked)) } },
                    onClear = if (settings.lockDetailApp.isNotEmpty()) ({ env.onChange { it.copy(lockDetailApp = "") } }) else null,
                    tag = "lock:detail",
                )
                SettingHint(stringResource(R.string.lock_detail_hint))
            }
        }
        ToggleSetting(
            stringResource(R.string.lock_music), settings.lockShowMusic,
            onChange = { value -> env.onChange { it.copy(lockShowMusic = value) } },
            hint = stringResource(R.string.lock_music_hint), tag = "lock:music", id = "lock.music",
        )
    }
}

/**
 * Where the notifications of each app show: the Action Center, the lock screen, the banner. A touch on an app unfolds its three
 * switches and a button for its sound (which only Android can change: the page of the app opens). With the master switch off
 * nothing of this is applied, and what was chosen is kept.
 */
@Composable
fun NotificationAppsSetting(env: SettingsEnv) {
    val settings = env.settings
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingHeading(stringResource(R.string.ac_notifapps_title))
        ToggleSetting(
            stringResource(R.string.ac_notifcontrol), settings.notifControl,
            onChange = { value -> env.onChange { it.copy(notifControl = value) } },
            hint = stringResource(R.string.ac_notifcontrol_hint), tag = "actions:notifcontrol", id = "ac.notifcontrol",
        )
        if (settings.notifControl) {
            var query by remember { mutableStateOf("") }
            var open by remember { mutableStateOf<String?>(null) }
            Column(Modifier.settingAnchor("ac.notifapps"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MetroTextField(query, { query = it }, stringResource(R.string.search_apps), Modifier.testTag("actions:notifapps:query"))
                // The apps that have something up now come first; the rest are found by typing.
                val choices = remember(env.apps, env.notificationPackages, query) {
                    val byPackage = env.apps.filter { it.packageName != null }.distinctBy { it.packageName }
                    val found = if (query.isBlank()) byPackage else AppSearch.filter(byPackage, query)
                    found.sortedWith(
                        compareByDescending<com.metrowin.launcher.model.AppItem> { it.packageName in env.notificationPackages }
                            .thenBy { it.label.lowercase() },
                    ).take(NOTIF_APPS_SHOWN)
                }
                choices.forEach { app ->
                    val pkg = app.packageName ?: return@forEach
                    NotificationAppRow(
                        app = app, expanded = open == pkg, onToggle = { open = if (open == pkg) null else pkg },
                        center = pkg !in settings.notifHideCenter, lock = pkg !in settings.notifHideLock, banner = pkg !in settings.notifNoBanner,
                        onCenter = { on -> env.onChange { it.copy(notifHideCenter = if (on) it.notifHideCenter - pkg else it.notifHideCenter + pkg) } },
                        onLock = { on -> env.onChange { it.copy(notifHideLock = if (on) it.notifHideLock - pkg else it.notifHideLock + pkg) } },
                        onBanner = { on -> env.onChange { it.copy(notifNoBanner = if (on) it.notifNoBanner - pkg else it.notifNoBanner + pkg) } },
                        onSound = { env.onAppNotificationSettings(pkg) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationAppRow(
    app: com.metrowin.launcher.model.AppItem,
    expanded: Boolean,
    onToggle: () -> Unit,
    center: Boolean,
    lock: Boolean,
    banner: Boolean,
    onCenter: (Boolean) -> Unit,
    onLock: (Boolean) -> Unit,
    onBanner: (Boolean) -> Unit,
    onSound: () -> Unit,
) {
    val colors = LocalMetroColors.current
    val pkg = app.packageName.orEmpty()
    Column(Modifier.fillMaxWidth().testTag("actions:notifapp:$pkg")) {
        Row(
            Modifier.fillMaxWidth().testTag("actions:notifapp:$pkg:header").metroPress(onClick = onToggle).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppSquare(app, 36.dp)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                MetroText(app.label, size = 19.sp, maxLines = 1)
                val off = listOfNotNull(
                    stringResource(R.string.ac_notif_center).takeIf { !center },
                    stringResource(R.string.ac_notif_lock).takeIf { !lock },
                    stringResource(R.string.ac_notif_banner).takeIf { !banner },
                )
                if (off.isNotEmpty()) {
                    MetroText(stringResource(R.string.ac_notif_off_in, off.joinToString(", ")), size = 13.sp, weight = FontWeight.Normal, color = colors.subtle)
                }
            }
            MetroText(if (expanded) "−" else "+", size = 24.sp, weight = FontWeight.Normal, color = colors.subtle)
        }
        if (expanded) {
            Column(Modifier.padding(start = 50.dp, bottom = 8.dp)) {
                ToggleSetting(stringResource(R.string.ac_notif_center), center, onCenter, tag = "actions:notifapp:$pkg:center")
                ToggleSetting(stringResource(R.string.ac_notif_lock), lock, onLock, tag = "actions:notifapp:$pkg:lock")
                ToggleSetting(stringResource(R.string.ac_notif_banner), banner, onBanner, tag = "actions:notifapp:$pkg:banner")
                ActionSetting(stringResource(R.string.ac_notif_sound), onClick = onSound, tag = "actions:notifapp:$pkg:sound")
            }
        }
    }
}

private const val NOTIF_APPS_SHOWN = 40
