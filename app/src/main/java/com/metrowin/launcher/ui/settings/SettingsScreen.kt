package com.metrowin.launcher.ui.settings

import com.metrowin.launcher.ui.theme.pageBackground
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.components.cutoutSidesPadding
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.components.TurnstileHost
import com.metrowin.launcher.ui.theme.LocalMetroColors

/**
 * The launcher's settings, laid out like the Windows Phone settings: a pivot whose pages list the
 * sections (title and a line about what is in it), and a page for each section that swings in over the list.
 * A search box on top finds any setting by name or by what it is for (see [SettingsIndex]) and takes you to it.
 *
 * [section] is the open section, or null for the list. It is held by the caller so that Back can close it
 * before it closes the settings; [sectionAway] is how far the section page is turned away, which the
 * caller also moves while the back swipe is under the finger. [target] is the setting the open section was opened
 * for (an id of the [SettingsIndex]): the page scrolls to it and lights it. It is held by the caller, which can open the
 * settings on a setting from anywhere (the search of the app list does).
 */
@Composable
fun SettingsScreen(
    env: SettingsEnv,
    section: SettingsSection?,
    onSectionChange: (SettingsSection?) -> Unit,
    sectionAway: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier,
    target: String? = null,
    onTargetChange: (String?) -> Unit = {},
) {
    val colors = LocalMetroColors.current
    val groups = remember { SettingsGroup.entries.filter { group -> SettingsSection.entries.any { it.group == group } } }
    val pagerState = rememberPagerState { groups.size }
    // The page keeps its content while it swings away, after the caller has already set the section to null.
    var shown by remember { mutableStateOf(section) }
    if (section != null) shown = section
    // Closing the section is the end of the visit to a setting.
    LaunchedEffect(section) { if (section == null) onTargetChange(null) }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val index = remember(configuration) { SettingsIndex.searchIndex(context) }
    var query by remember { mutableStateOf("") }
    val hits = remember(query, index) { index.search(query) }

    Box(
        modifier
            .fillMaxSize()
            .pageBackground()
            // Swallow touches so nothing underneath reacts while this page is up.
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .navigationBarsPadding()
            .cutoutSidesPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            SettingsHeader()
            MetroTextField(
                value = query, onValueChange = { query = it },
                placeholder = stringResource(R.string.settings_search_hint),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 6.dp).testTag("settings:search"),
            )
            if (query.isBlank()) {
                PivotHeader(
                    groups.map { stringResource(it.title) }, pagerState,
                    Modifier.padding(start = 24.dp, top = 2.dp, bottom = 8.dp),
                )
                HorizontalPager(pagerState, Modifier.weight(1f)) { page ->
                    SectionList(SettingsSection.entries.filter { it.group == groups[page] }, onSectionChange)
                }
            } else {
                SearchResults(query, hits, Modifier.weight(1f)) { hit ->
                    onTargetChange(hit.setting?.id)
                    onSectionChange(hit.section)
                }
            }
        }
        TurnstileHost(show = section != null, away = sectionAway) {
            shown?.let { opened ->
                CompositionLocalProvider(LocalSettingsTarget provides target?.takeIf { SettingsIndex.get(it)?.section == opened }) {
                    SectionPage(opened, env)
                }
            }
        }
    }
}

/** What the search of the settings found: each a setting (or a whole section) with the place it is in. */
@Composable
private fun SearchResults(query: String, hits: List<SettingHit>, modifier: Modifier = Modifier, onOpen: (SettingHit) -> Unit) {
    val colors = LocalMetroColors.current
    if (hits.isEmpty()) {
        MetroText(
            stringResource(R.string.settings_search_none, query.trim()),
            size = 16.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = Int.MAX_VALUE, lineHeight = 22.sp,
            modifier = modifier.padding(horizontal = 24.dp, vertical = 14.dp).testTag("settings:search:none"),
        )
        return
    }
    LazyColumn(modifier.fillMaxSize()) {
        items(hits, key = { it.setting?.id ?: "section:${it.section.name}" }) { hit ->
            val where = if (hit.setting == null) stringResource(hit.section.description) else stringResource(R.string.settings_search_in, stringResource(hit.section.title))
            Column(
                Modifier
                    .fillMaxWidth()
                    .testTag("settings:result:${hit.setting?.id ?: hit.section.name}")
                    .metroPress(onClick = { onOpen(hit) })
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                MetroText(stringResource(hit.setting?.title ?: hit.section.title), size = 24.sp)
                MetroText(where, size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun SettingsHeader(modifier: Modifier = Modifier) {
    MetroText(
        stringResource(R.string.settings_header),
        size = 14.sp,
        weight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 24.dp, top = 28.dp),
    )
}

@Composable
private fun SectionList(sections: List<SettingsSection>, onOpen: (SettingsSection) -> Unit) {
    val colors = LocalMetroColors.current
    LazyColumn(Modifier.fillMaxSize()) {
        items(sections, key = { it.name }) { section ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .testTag("settings:section:${section.name}")
                    .metroPress(onClick = { onOpen(section) })
                    .padding(horizontal = 24.dp, vertical = 10.dp),
            ) {
                MetroText(stringResource(section.title), size = 28.sp)
                MetroText(
                    stringResource(section.description),
                    size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, lineHeight = 19.sp,
                )
            }
        }
    }
}

/** The page of one section: the small heading, the section's title, and its settings. */
@Composable
private fun SectionPage(section: SettingsSection, env: SettingsEnv, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().pageBackground()) {
        SettingsHeader()
        MetroText(
            stringResource(section.title),
            size = 40.sp,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 8.dp),
        )
        Box(Modifier.weight(1f)) {
            when (section) {
                SettingsSection.StartTheme -> StartThemePage(env)
                SettingsSection.Start -> StartPage(env)
                SettingsSection.Search -> SearchPage(env)
                SettingsSection.Gestures -> GesturesPage(env)
                SettingsSection.Actions -> ActionsPage(env)
                SettingsSection.LiveTiles -> LiveTilesPage(env)
                SettingsSection.Apps -> AppsPage(env)
                SettingsSection.Screen -> ScreenPage(env)
                SettingsSection.Battery -> BatteryPage(env)
                SettingsSection.Accessibility -> AccessibilityPage(env)
                SettingsSection.Icons -> IconsPage(env)
                SettingsSection.Lock -> LockScreenPage(env)
                SettingsSection.Voice -> VoicePage(env)
                SettingsSection.Profiles -> ProfilesPage(env)
                SettingsSection.Backup -> BackupPage(env)
                SettingsSection.Kids -> KidsPage(env)
                SettingsSection.About -> AboutPage(env)
            }
        }
    }
}
