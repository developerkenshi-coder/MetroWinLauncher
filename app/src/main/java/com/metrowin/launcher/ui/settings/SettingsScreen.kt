package com.metrowin.launcher.ui.settings

import com.metrowin.launcher.ui.components.WindowClass
import com.metrowin.launcher.ui.components.windowClass
import com.metrowin.launcher.ui.components.Spacing
import com.metrowin.launcher.ui.components.MetroType
import com.metrowin.launcher.ui.components.topBarsPadding
import com.metrowin.launcher.ui.theme.LocalAccent
import kotlinx.coroutines.delay
import com.metrowin.launcher.data.SectionReset
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import com.metrowin.launcher.ui.anim.metroTween
import com.metrowin.launcher.ui.theme.pageBackground
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.theme.litFill
import com.metrowin.launcher.ui.theme.onAccent
import androidx.compose.ui.Alignment
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.metrowin.launcher.ui.anim.Motion
import com.metrowin.launcher.ui.anim.featherIn
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.key
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
import com.metrowin.launcher.ui.components.AppTitleLine
import com.metrowin.launcher.ui.components.LocalAppTitle
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.components.TurnstileHost
import com.metrowin.launcher.ui.theme.LocalMetroColors

/**
 * The launcher's settings, laid out like the Windows Phone settings: a home that lists the categories (an icon, a name, a line about what is in
 * it, an "i" that says more), and a category that swings in over the list as a pivot of its pages (two levels, never more). A search box on top
 * finds any setting by name or by what it is for (see [SettingsIndex]) and takes you to it, saying where it is.
 *
 * [section] is the open page, or null for the home: the category it belongs to is the one that is open, and swiping its pivot changes it. It is
 * held by the caller (and saved with the state of the screen) so that Back can close it before it closes the settings, and so that Back
 * from a page always lands in the category you came from; [sectionAway] is how far the category is turned away, which the caller also moves
 * while the back swipe is under the finger. [target] is the setting the page was opened for (an id of the [SettingsIndex]): the page scrolls to
 * it and lights it. It is held by the caller, which can open the settings on a setting from anywhere (the search of the app list does).
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
    val categories = remember { SettingsCategory.entries.toList() }
    // The page keeps its content while it swings away, after the caller has already set the section to null.
    var shown by remember { mutableStateOf(section) }
    if (section != null) shown = section
    // Closing the section is the end of the visit to a setting.
    LaunchedEffect(section) { if (section == null) onTargetChange(null) }
    // The search box stays under the page that opens; its keyboard must not stay over it.
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(section) {
        if (section != null) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val index = remember(configuration) { SettingsIndex.searchIndex(context) }
    // Kept with the state of the screen: coming back from a page lands on the same search, or on the same home.
    var query by rememberSaveable { mutableStateOf("") }
    val hits = remember(query, index) { index.search(query) }
    // Where the name that was touched was on the screen: the title of the page flies up from there (the "continuum" of Windows Phone).
    var tappedTop by remember { mutableFloatStateOf(0f) }

    // The box of an "i" is drawn here, over everything, not where the "i" is: a page scrolls, and a dialog cannot be measured inside a scroll.
    var info by remember { mutableStateOf<InfoRequest?>(null) }
    CompositionLocalProvider(LocalInfoHost provides { info = it }) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .pageBackground()
            // Swallow touches so nothing underneath reacts while this page is up.
            .pointerInput(Unit) { detectTapGestures { } }
            .topBarsPadding()
            .navigationBarsPadding()
            .cutoutSidesPadding(),
    ) {
        // An expanded screen keeps the list at the left and opens the category beside it (the list is 440 dp: below 840 dp the category would have no room).
        val twoPanes = maxWidth.windowClass == WindowClass.Expanded
        Row(Modifier.fillMaxSize()) {
        Column(if (twoPanes) Modifier.width(LIST_PANE).fillMaxHeight() else Modifier.fillMaxSize()) {
            SettingsHeader()
            MetroTextField(
                value = query, onValueChange = { query = it },
                placeholder = stringResource(R.string.settings_search_hint),
                modifier = Modifier.padding(start = Spacing.Page, end = Spacing.Page, top = 10.dp, bottom = 6.dp).testTag("settings:search"),
            )
            if (query.isBlank()) {
                CategoryList(categories, if (twoPanes) section?.category else null, Modifier.weight(1f)) { opened, top ->
                    tappedTop = top
                    onSectionChange(opened.pages.first())
                }
            } else {
                SearchResults(query, hits, Modifier.weight(1f)) { hit, top ->
                    tappedTop = top
                    onTargetChange(hit.setting?.id)
                    onSectionChange(hit.section)
                }
            }
        }
        if (twoPanes) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                section?.let { opened ->
                    key(opened.category) { CategoryPage(opened, target, env, 0f, onSectionChange = { if (section != null) onSectionChange(it) }) }
                }
            }
        }
        }
        if (!twoPanes) TurnstileHost(show = section != null, away = sectionAway) {
            shown?.let { opened ->
                key(opened.category) { CategoryPage(opened, target, env, tappedTop, onSectionChange = { if (section != null) onSectionChange(it) }) }
            }
        }
        info?.let { InfoBox(it) { info = null } }
    }
    }
}

/** From this screen height (dp) a page says what it holds under its pivot. */
private const val DESCRIPTION_FROM_HEIGHT = 600

/** From this width the settings show the list and the open category side by side. */
private val LIST_PANE = 440.dp

/** What the search of the settings found: each a setting (or a whole page) with the place it is in, as "category › page". */
@Composable
private fun SearchResults(query: String, hits: List<SettingHit>, modifier: Modifier = Modifier, onOpen: (SettingHit, Float) -> Unit) {
    val colors = LocalMetroColors.current
    if (hits.isEmpty()) {
        MetroText(
            stringResource(R.string.settings_search_none, query.trim()),
            size = MetroType.Body, weight = FontWeight.Normal, color = colors.subtle, maxLines = Int.MAX_VALUE, lineHeight = 22.sp,
            modifier = modifier.padding(horizontal = Spacing.Page, vertical = 14.dp).testTag("settings:search:none"),
        )
        return
    }
    LazyColumn(modifier.fillMaxSize()) {
        items(hits, key = { it.setting?.id ?: it.category?.let { c -> "category:${c.name}" } ?: "section:${it.section.name}" }) { hit ->
            val where = when {
                hit.category != null -> stringResource(hit.category.description)
                // A category with one page is its own page: the place is its name alone.
                hit.section.category.pages.size == 1 -> stringResource(hit.section.category.title)
                else -> stringResource(R.string.settings_where, stringResource(hit.section.category.title), stringResource(hit.section.title))
            }
            var top by remember { mutableFloatStateOf(0f) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .testTag("settings:result:${hit.setting?.id ?: hit.category?.name?.let { "cat:$it" } ?: hit.section.name}")
                    .onGloballyPositioned { top = it.positionInRoot().y }
                    .metroPress(onClick = { onOpen(hit, top) })
                    .padding(horizontal = Spacing.Page, vertical = Spacing.Medium),
            ) {
                MetroText(stringResource(hit.setting?.title ?: hit.category?.title ?: hit.section.title), size = MetroType.Heading)
                MetroText(where, size = MetroType.Note, weight = FontWeight.Normal, color = colors.subtle, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun SettingsHeader(modifier: Modifier = Modifier) {
    // The same app-name line as the mini apps (LocalAppTitle), so the name is in one place and one look.
    CompositionLocalProvider(LocalAppTitle provides stringResource(R.string.stock_settings)) {
        Box(modifier.padding(start = Spacing.Page, top = 28.dp)) { AppTitleLine() }
    }
}

/** The home: one big row for each category, in the order you reach for them. On a wide screen the open one is marked. */
@Composable
private fun CategoryList(categories: List<SettingsCategory>, open: SettingsCategory?, modifier: Modifier = Modifier, onOpen: (SettingsCategory, Float) -> Unit) {
    val colors = LocalMetroColors.current
    // Thirteen rows: a plain scrolling column, so that every row is there (and found by the semantics) whether it is on screen or not.
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("settings:categories")) {
        categories.forEachIndexed { index, category ->
            var top by remember { mutableFloatStateOf(0f) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .featherIn(index, "categories")
                    .testTag("settings:category:${category.name}")
                    .onGloballyPositioned { top = it.positionInRoot().y }
                    .metroPress(onClick = { onOpen(category, top) })
                    .padding(start = Spacing.Page, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(56.dp).litFill(category == open), contentAlignment = Alignment.Center) {
                    GlyphIcon(category.glyph, 34.dp, color = onAccent())
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    MetroText(stringResource(category.title), size = 26.sp, maxLines = Int.MAX_VALUE, lineHeight = 30.sp)
                    MetroText(
                        stringResource(category.description),
                        size = MetroType.Note, weight = FontWeight.Normal, color = colors.subtle, lineHeight = 19.sp, maxLines = Int.MAX_VALUE,
                    )
                }
                InfoMark(category.info, stringResource(category.title), tag = "info:cat:${category.name}")
            }
        }
    }
}

/**
 * A category: its name big over a pivot of its pages (no pivot when it has one page), and the page that is open. Swiping the pivot, or touching a
 * title of it, changes [onSectionChange]: the open page is saved by the caller, so Back and a new start of the screen find it again.
 */
@Composable
private fun CategoryPage(
    opened: SettingsSection,
    target: String?,
    env: SettingsEnv,
    fromTop: Float,
    onSectionChange: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = opened.category
    val pages = category.pages
    val reduced = LocalReduceMotion.current
    var titleTop by remember { mutableFloatStateOf(0f) }
    // 0 when the page has just opened, 1 when it has settled: the title comes from where the touched name was, bigger as it goes, and the
    // settings slide in after it.
    val settled = remember(category) { Animatable(if (reduced || fromTop <= 0f) 1f else 0f) }
    LaunchedEffect(category) {
        if (settled.value < 1f) settled.animateTo(1f, metroTween(durationMillis = 420, easing = Motion.Decelerate))
    }
    val pager = rememberPagerState(initialPage = pages.indexOf(opened).coerceAtLeast(0)) { pages.size }
    // A search can take you to another page of the category: the pivot follows. And a swipe of the pivot is told to the caller.
    LaunchedEffect(opened) {
        val i = pages.indexOf(opened)
        if (i >= 0 && pager.currentPage != i) pager.scrollToPage(i)
    }
    val current by rememberUpdatedState(opened)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page -> if (pages[page] != current) onSectionChange(pages[page]) }
    }
    Column(modifier.fillMaxSize().pageBackground()) {
        SettingsHeader()
        MetroText(
            stringResource(category.title),
            size = 40.sp,
            modifier = Modifier
                .padding(start = Spacing.Page, end = Spacing.Page, top = 2.dp, bottom = Spacing.Medium)
                .testTag("settings:title")
                .onGloballyPositioned { titleTop = it.positionInRoot().y }
                .graphicsLayer {
                    val away = 1f - settled.value
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    translationY = (fromTop - titleTop) * away
                    scaleX = 1f - 0.3f * away
                    scaleY = 1f - 0.3f * away
                },
        )
        Box(
            Modifier
                .weight(1f)
                .graphicsLayer {
                    alpha = settled.value
                    translationX = (1f - settled.value) * 56.dp.toPx()
                },
        ) {
            Column(Modifier.fillMaxSize()) {
                if (pages.size > 1) {
                    PivotHeader(
                        pages.map { stringResource(it.title) }, pager,
                        Modifier.padding(start = Spacing.Page, top = 2.dp, bottom = Spacing.Small),
                        size = 30.sp,
                    )
                }
                // A line about what the page holds, when the screen is tall enough to spare it (on its side the settings need every row).
                if (LocalConfiguration.current.screenHeightDp >= DESCRIPTION_FROM_HEIGHT) {
                    MetroText(
                        stringResource(pages[pager.currentPage].description),
                        size = MetroType.Note, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, maxLines = Int.MAX_VALUE, lineHeight = 19.sp,
                        modifier = Modifier.padding(horizontal = Spacing.Page).padding(bottom = Spacing.Small).testTag("settings:pagedesc"),
                    )
                }
                HorizontalPager(pager, Modifier.weight(1f), key = { pages[it].name }) { index ->
                    val page = pages[index]
                    CompositionLocalProvider(LocalSettingsTarget provides target?.takeIf { SettingsIndex.get(it)?.section == page }) {
                        // The page can be put back to the start when it allows it (see SectionReset).
                        CompositionLocalProvider(LocalPageReset provides PageReset(page.name, env.settings) { env.onChange { SectionReset.reset(it, page.name) } }) {
                            PageContent(page, env)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageContent(section: SettingsSection, env: SettingsEnv) {
    when (section) {
        SettingsSection.StartTheme -> AppearancePage(env)
        SettingsSection.Screen -> ScreenPage(env)
        SettingsSection.Start -> StartPage(env)
        SettingsSection.StartBars -> StartBarsPage(env)
        SettingsSection.StartBehaviour -> StartBehaviourPage(env)
        SettingsSection.LiveTiles -> LiveTilesPage(env)
        SettingsSection.Lock -> LockScreenPage(env)
        SettingsSection.StatusBar -> StatusBarPage(env)
        SettingsSection.Actions -> ActionsPage(env)
        SettingsSection.NavBar -> NavBarPage(env)
        SettingsSection.Gestures -> GesturesPage(env)
        SettingsSection.Feel -> FeelPage(env)
        SettingsSection.Voice -> VoicePage(env)
        SettingsSection.Apps -> AppsPage(env)
        SettingsSection.Icons -> IconsPage(env)
        SettingsSection.Search -> SearchPage(env)
        SettingsSection.MiniApps -> MiniAppsPage()
        SettingsSection.Notifications -> NotificationsPage(env)
        SettingsSection.Privacy -> PrivacyPage(env)
        SettingsSection.Kids -> KidsPage(env)
        SettingsSection.Battery -> BatteryPage(env)
        SettingsSection.Accessibility -> AccessibilityPage(env)
        SettingsSection.Backup -> BackupPage(env)
        SettingsSection.Profiles -> ProfilesPage(env)
        SettingsSection.About -> AboutPage(env)
        SettingsSection.BugReport -> BugReportPage(env)
        SettingsSection.OtherProjects -> OtherProjectsPage()
    }
}
