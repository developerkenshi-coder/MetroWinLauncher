package com.metrowin.launcher.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.ui.components.AppSquare
import com.metrowin.launcher.ui.kids.CheckSquare
import com.metrowin.launcher.ui.actioncenter.QuickButton
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.ChoiceRow
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.components.SettingLabel
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.MetroAccents

/**
 * The launcher's own settings, laid out as a Windows Phone pivot: the page titles on top and a page of
 * settings below, swiping sideways moves between pages.
 */
@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    startBackground: ImageBitmap?,
    onChange: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onPickBackground: (Uri) -> Unit,
    onClearBackground: () -> Unit,
    notificationAccess: Boolean,
    onManageNotificationAccess: () -> Unit,
    apps: List<AppItem>,
    onStartKidsCorner: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
) {
    val colors = LocalMetroColors.current
    val titles = listOf(
        stringResource(R.string.settings_start_theme),
        stringResource(R.string.settings_actions),
        stringResource(R.string.kids_title),
    )
    val pagerState = rememberPagerState(initialPage = initialPage) { titles.size }
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            // Swallow touches so nothing underneath reacts while this page is up.
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        MetroText(
            stringResource(R.string.settings_header),
            size = 14.sp,
            weight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 24.dp, top = 28.dp),
        )
        PivotHeader(titles, pagerState, Modifier.padding(start = 24.dp, top = 2.dp, bottom = 8.dp))
        HorizontalPager(pagerState, Modifier.weight(1f)) { page ->
            when (page) {
                0 -> StartThemePage(settings, startBackground, onChange, onPickBackground, onClearBackground)
                1 -> ActionsPage(settings, notificationAccess, onChange, onManageNotificationAccess)
                2 -> KidsPage(settings, apps, onChange, onStartKidsCorner)
            }
        }
    }
}

@Composable
private fun StartThemePage(
    settings: LauncherSettings,
    startBackground: ImageBitmap?,
    onChange: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onPickBackground: (Uri) -> Unit,
    onClearBackground: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPickBackground(uri)
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Column {
            SettingLabel(stringResource(R.string.theme_background))
            ChoiceRow(
                options = listOf(stringResource(R.string.theme_dark), stringResource(R.string.theme_light)),
                selected = if (settings.darkTheme) 0 else 1,
                onSelect = { onChange { s -> s.copy(darkTheme = it == 0) } },
            )
        }
        Column {
            SettingLabel(stringResource(R.string.theme_accent))
            AccentGrid(settings.accentIndex) { index -> onChange { it.copy(accentIndex = index) } }
        }
        Column {
            SettingLabel(stringResource(R.string.theme_start_background))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                BackgroundPreview(startBackground)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    MetroButton(stringResource(R.string.theme_choose_photo), Modifier.fillMaxWidth()) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    if (settings.hasStartBackground) {
                        MetroButton(stringResource(R.string.theme_remove_photo), Modifier.fillMaxWidth(), onClick = onClearBackground)
                    }
                    MetroText(
                        stringResource(R.string.theme_start_background_hint),
                        size = 14.sp,
                        weight = FontWeight.Normal,
                        color = LocalMetroColors.current.subtle,
                        maxLines = Int.MAX_VALUE,
                    )
                }
            }
        }
        MetroToggle(
            label = stringResource(R.string.theme_more_tiles),
            checked = settings.moreTiles,
            onChange = { onChange { s -> s.copy(moreTiles = it) } },
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** The 20 accent colours in a grid of five columns. The chosen one has a frame. */
@Composable
private fun AccentGrid(selected: Int, onSelect: (Int) -> Unit) {
    val frame = LocalMetroColors.current.foreground
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MetroAccents.chunked(ACCENT_COLUMNS).forEachIndexed { row, colors ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                colors.forEachIndexed { column, color ->
                    val index = row * ACCENT_COLUMNS + column
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .metroPress(onClick = { onSelect(index) })
                            .background(color)
                            .then(if (index == selected) Modifier.border(3.dp, frame) else Modifier),
                    )
                }
            }
        }
    }
}

private const val ACCENT_COLUMNS = 5

@Composable
private fun BackgroundPreview(image: ImageBitmap?) {
    val colors = LocalMetroColors.current
    Box(Modifier.size(90.dp, 160.dp).background(colors.surface)) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Which four buttons sit on top of the Action Center, and whether notifications can be read. */
@Composable
private fun ActionsPage(
    settings: LauncherSettings,
    notificationAccess: Boolean,
    onChange: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onManageNotificationAccess: () -> Unit,
) {
    val colors = LocalMetroColors.current
    val slots = QuickAction.slots(settings.quickActions)
    var selected by remember { mutableIntStateOf(0) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Column {
            SettingLabel(stringResource(R.string.settings_quick_actions))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                slots.forEachIndexed { index, action ->
                    QuickButton(
                        action, on = true, onClick = { selected = index },
                        modifier = Modifier
                            .weight(1f)
                            .then(if (index == selected) Modifier.border(3.dp, colors.foreground) else Modifier),
                    )
                }
            }
            MetroText(
                stringResource(R.string.settings_pick_action),
                size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = Int.MAX_VALUE,
                modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
            )
            QuickAction.entries.chunked(QuickAction.SLOTS).forEach { row ->
                Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { action ->
                        QuickButton(
                            action, on = action in slots,
                            onClick = { onChange { s -> s.copy(quickActions = QuickAction.assign(slots, selected, action).map { it.id }) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        Column {
            SettingLabel(stringResource(R.string.settings_notification_access))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetroText(
                    stringResource(if (notificationAccess) R.string.toggle_on else R.string.toggle_off),
                    size = 22.sp, modifier = Modifier.weight(1f),
                )
                MetroButton(
                    stringResource(if (notificationAccess) R.string.settings_manage else R.string.ac_turn_on),
                    onClick = onManageNotificationAccess,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Which apps the Kid's Corner shows, and the button that starts it. */
@Composable
private fun KidsPage(
    settings: LauncherSettings,
    apps: List<AppItem>,
    onChange: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onStart: () -> Unit,
) {
    val colors = LocalMetroColors.current
    val choosable = remember(apps) { apps.filter { it.launch !is Launch.Internal } }
    val ready = settings.kidsApps.any { id -> choosable.any { it.id == id } }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        item {
            MetroText(
                stringResource(R.string.kids_intro),
                size = 16.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = Int.MAX_VALUE, lineHeight = 22.sp,
            )
            Spacer(Modifier.height(18.dp))
            MetroButton(
                stringResource(R.string.kids_start),
                Modifier.fillMaxWidth().testTag("kids:start").alpha(if (ready) 1f else 0.4f),
            ) { if (ready) onStart() }
            MetroText(
                stringResource(R.string.kids_pin_hint),
                size = 13.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = Int.MAX_VALUE,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(22.dp))
            SettingLabel(stringResource(R.string.kids_apps))
        }
        items(choosable, key = { it.id }) { app ->
            val checked = app.id in settings.kidsApps
            Row(
                Modifier
                    .fillMaxWidth()
                    .testTag("kids:app:${app.id}")
                    .metroPress(onClick = {
                        onChange { s -> s.copy(kidsApps = if (checked) s.kidsApps - app.id else s.kidsApps + app.id) }
                    })
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppSquare(app, 44.dp)
                Spacer(Modifier.width(14.dp))
                MetroText(app.label, size = 20.sp, modifier = Modifier.weight(1f))
                CheckSquare(checked)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
