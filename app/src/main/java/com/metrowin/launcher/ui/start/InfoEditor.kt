package com.metrowin.launcher.ui.start

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.InfoConfig
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.anim.featherIn
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppTitleLine
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.LocalAppTitle
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.model.InfoShare
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroType
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.components.Spacing
import com.metrowin.launcher.ui.components.TurnstileHost
import com.metrowin.launcher.ui.components.cutoutSidesPadding
import com.metrowin.launcher.ui.components.topBarsPadding
import com.metrowin.launcher.ui.settings.InfoBox
import com.metrowin.launcher.ui.settings.InfoMark
import com.metrowin.launcher.ui.settings.InfoRequest
import com.metrowin.launcher.ui.settings.LocalInfoHost
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.litFill
import com.metrowin.launcher.ui.theme.onAccent
import com.metrowin.launcher.ui.theme.onTile
import com.metrowin.launcher.ui.theme.pageBackground
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalDensity

/**
 * The editor of an info tile, laid out like the settings of the launcher: a home that lists the categories (an icon, a name, a line about what is in it, an
 * "i" that says more), and a category that swings in over the list as a pivot of its pages (two levels, never more). The tile itself is shown live on top,
 * so every change is seen at once. It opens when the tile is put on Start and again from the pencil of the tile in the edit mode of Start.
 *
 * [page] is the open page, or null for the home; it is held by the caller so that Back can close it before it closes the editor. Every change is kept at
 * once through [onChange].
 */
@Composable
fun InfoEditor(
    item: AppItem,
    initial: InfoConfig,
    live: LiveTileData,
    page: InfoPage?,
    onPage: (InfoPage?) -> Unit,
    env: InfoEditorEnv,
    onChange: (InfoConfig) -> Unit,
    onClose: () -> Unit,
    /** True for a tile that was just put on Start: it first offers a model to start from. */
    isNew: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var config by remember(initial) { mutableStateOf(initial) }
    fun change(next: InfoConfig) {
        config = next
        onChange(next)
    }
    var shown by remember { mutableStateOf(page) }
    if (page != null) shown = page
    var tappedTop by remember { mutableFloatStateOf(0f) }
    var info by remember { mutableStateOf<InfoRequest?>(null) }
    var models by remember { mutableStateOf(isNew) }
    var dialog by remember { mutableStateOf<InfoDialog?>(null) }
    CompositionLocalProvider(LocalInfoHost provides { info = it }) {
        Box(
            modifier
                .fillMaxSize()
                .pageBackground()
                .pointerInput(Unit) { detectTapGestures { } }
                .topBarsPadding()
                .navigationBarsPadding()
                .cutoutSidesPadding()
                .testTag("info:editor"),
        ) {
            Column(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalAppTitle provides stringResource(R.string.info_title)) {
                    Box(Modifier.padding(start = Spacing.Page, top = 28.dp)) { AppTitleLine() }
                }
                InfoPreview(item, config, live, Modifier.padding(horizontal = Spacing.Page, vertical = 10.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("info:categories")) {
                    InfoCategory.entries.forEachIndexed { index, category ->
                        var top by remember { mutableFloatStateOf(0f) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .featherIn(index, "info-categories")
                                .testTag("info:category:${category.name}")
                                .onGloballyPositioned { top = it.positionInRoot().y }
                                .metroPress(onClick = {
                                    tappedTop = top
                                    onPage(category.pages.first())
                                })
                                .padding(start = Spacing.Page, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(56.dp).litFill(false), contentAlignment = Alignment.Center) { GlyphIcon(category.glyph, 34.dp, color = onAccent()) }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                MetroText(stringResource(category.title), size = 26.sp, maxLines = Int.MAX_VALUE, lineHeight = 30.sp)
                                MetroText(
                                    stringResource(category.description), size = MetroType.Note, weight = FontWeight.Normal,
                                    color = LocalMetroColors.current.subtle, lineHeight = 19.sp, maxLines = Int.MAX_VALUE,
                                )
                            }
                            InfoMark(category.info, stringResource(category.title), tag = "info:cat:${category.name}")
                        }
                    }
                    val context = androidx.compose.ui.platform.LocalContext.current
                    MetroButton(
                        stringResource(R.string.infoe_share_code), Modifier.fillMaxWidth().padding(horizontal = Spacing.Page, vertical = 4.dp).testTag("info:share"),
                        onClick = {
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, InfoShare.toCode(config))
                            runCatching { context.startActivity(android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        },
                    )
                    MetroButton(
                        stringResource(R.string.infoe_use_code), Modifier.fillMaxWidth().padding(horizontal = Spacing.Page, vertical = 4.dp).testTag("info:usecode"),
                        onClick = {
                            val text = context.getSystemService(android.content.ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            val shared = InfoShare.fromCode(text)
                            if (shared != null) change(shared)
                            com.metrowin.launcher.ui.actioncenter.MetroToast.show(context, if (shared != null) R.string.infoe_code_used else R.string.infoe_code_bad)
                        },
                    )
                    MetroButton(stringResource(R.string.info_done), Modifier.fillMaxWidth().padding(horizontal = Spacing.Page, vertical = 16.dp).testTag("info:done"), onClick = onClose)
                }
            }
            TurnstileHost(show = page != null) {
                shown?.let { opened ->
                    androidx.compose.runtime.key(opened.category) {
                        InfoCategoryPage(item, opened, config, live, env, tappedTop, onPageChange = { if (page != null) onPage(it) }, onChange = ::change, open = { dialog = it })
                    }
                }
            }
            if (models) {
                InfoModelDialog(
                    onPick = { preset -> models = false; change(preset) },
                    onCancel = { models = false },
                )
            }
            dialog?.let { InfoDialogs(it, config, ::change, env) { dialog = null } }
            info?.let { InfoBox(it) { info = null } }
        }
    }
}

/** The tile as it will look, at the shape of a wide tile, with what the choices make of it. */
@Composable
internal fun InfoPreview(item: AppItem, config: InfoConfig, live: LiveTileData, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val height = 110.dp
        val width = maxWidth
        Box(Modifier.fillMaxWidth().height(height).background(LocalAccent.current).testTag("info:preview")) {
            CompositionLocalProvider(LocalMetroColors provides LocalMetroColors.current.onTile()) {
                InfoFace(item, TileSize.Wide, width, height, config, live, seed = 0)
            }
        }
    }
}

/** A category: its name big over a pivot of its pages (no pivot when it has one page), the tile on show, and the page that is open. */
@Composable
private fun InfoCategoryPage(
    item: AppItem,
    opened: InfoPage,
    config: InfoConfig,
    live: LiveTileData,
    env: InfoEditorEnv,
    fromTop: Float,
    onPageChange: (InfoPage) -> Unit,
    onChange: (InfoConfig) -> Unit,
    open: (InfoDialog) -> Unit,
) {
    val category = opened.category
    val pages = category.pages
    val pager = rememberPagerState(initialPage = pages.indexOf(opened).coerceAtLeast(0)) { pages.size }
    LaunchedEffect(opened) {
        val i = pages.indexOf(opened)
        if (i >= 0 && pager.currentPage != i) pager.scrollToPage(i)
    }
    val current by rememberUpdatedState(opened)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page -> if (pages[page] != current) onPageChange(pages[page]) }
    }
    Column(Modifier.fillMaxSize().pageBackground()) {
        CompositionLocalProvider(LocalAppTitle provides stringResource(R.string.info_title)) {
            Box(Modifier.padding(start = Spacing.Page, top = 28.dp)) { AppTitleLine() }
        }
        MetroText(
            stringResource(category.title), size = 40.sp,
            modifier = Modifier.padding(start = Spacing.Page, end = Spacing.Page, top = 2.dp, bottom = Spacing.Small).testTag("info:title"),
        )
        Column(Modifier.weight(1f)) {
            if (pages.size > 1) {
                PivotHeader(pages.map { stringResource(it.title) }, pager, Modifier.padding(start = Spacing.Page, top = 2.dp, bottom = Spacing.Small), size = 30.sp)
            }
            if (LocalConfiguration.current.screenHeightDp >= DESCRIPTION_FROM_HEIGHT) {
                MetroText(
                    stringResource(pages[pager.currentPage].description),
                    size = MetroType.Note, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, maxLines = Int.MAX_VALUE, lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = Spacing.Page).padding(bottom = Spacing.Small).testTag("info:pagedesc"),
                )
            }
            // The tile stays in sight while its choices are changed.
            if (LocalConfiguration.current.screenHeightDp >= DESCRIPTION_FROM_HEIGHT) InfoPreview(item, config, live, Modifier.padding(horizontal = Spacing.Page, vertical = 6.dp))
            HorizontalPager(pager, Modifier.weight(1f), key = { pages[it].name }) { index ->
                InfoPageContent(pages[index], item, config, live, env, onChange, open)
            }
        }
    }
}

private const val DESCRIPTION_FROM_HEIGHT = 700
