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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.LauncherSettings
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
    modifier: Modifier = Modifier,
) {
    val colors = LocalMetroColors.current
    val titles = listOf(stringResource(R.string.settings_start_theme))
    val pagerState = rememberPagerState { titles.size }
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
