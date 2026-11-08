package com.metrowin.launcher.ui.settings

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent

/** One project of the author: what it is, where to get it and the pictures that show it. */
private class Project(
    val name: String,
    @param:StringRes val about: Int,
    val links: List<Pair<Int, String>>,
    val pictures: List<Int>,
    /** The height of a thumbnail: a phone screen is tall, a keyboard is a strip. */
    val thumbnail: Int,
    @param:StringRes val note: Int? = null,
)

private val LAUNCHER = Project(
    "MetroWin Launcher", R.string.prj_launcher_about, listOf(R.string.prj_github to "https://github.com/developerkenshi-coder/MetroWinLauncher"),
    listOf(R.drawable.prj_launcher_1, R.drawable.prj_launcher_2, R.drawable.prj_launcher_3, R.drawable.prj_launcher_4, R.drawable.prj_launcher_5, R.drawable.prj_launcher_6), 220,
)

private val KEYBOARD = Project(
    "MetroWin Keyboard", R.string.prj_keyboard_about, listOf(R.string.prj_github to "https://github.com/developerkenshi-coder/MetroWin-Keyboard"),
    listOf(R.drawable.prj_keyboard_looks, R.drawable.prj_keyboard_classic, R.drawable.prj_keyboard_glass, R.drawable.prj_keyboard_droid), 110, note = R.string.prj_public_soon,
)

private val SPESA = Project(
    "SpesaTracker", R.string.prj_spesa_about, listOf(R.string.prj_play to "https://play.google.com/store/apps/details?id=com.spesatracker.app"),
    listOf(R.drawable.prj_spesa_1, R.drawable.prj_spesa_2, R.drawable.prj_spesa_3, R.drawable.prj_spesa_4, R.drawable.prj_spesa_5, R.drawable.prj_spesa_6), 220,
)

/** The other projects of the author, free software first, then the one that is not; every picture can be enlarged. */
@Composable
fun OtherProjectsPage() {
    var viewing by remember { mutableStateOf<Pair<List<Int>, Int>?>(null) }
    SettingsPage {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingHeading(stringResource(R.string.prj_foss))
            SettingHint(stringResource(R.string.prj_foss_hint))
            ProjectCard(LAUNCHER, thisApp = true) { viewing = it }
            ProjectCard(KEYBOARD) { viewing = it }
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingHeading(stringResource(R.string.prj_nonfoss))
            SettingHint(stringResource(R.string.prj_nonfoss_hint))
            ProjectCard(SPESA) { viewing = it }
        }
    }
    viewing?.let { (pictures, start) -> PictureViewer(pictures, start) { viewing = null } }
}

@Composable
private fun ProjectCard(project: Project, thisApp: Boolean = false, onPicture: (Pair<List<Int>, Int>) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.testTag("projects:${project.name}"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MetroText(project.name, size = 24.sp, color = LocalAccent.current)
        if (thisApp) SettingHint(stringResource(R.string.prj_this_app))
        SettingHint(stringResource(project.about), size = 16.sp, lineHeight = 22.sp, minScale = 1f)
        project.note?.let { SettingHint(stringResource(it)) }
        project.links.forEach { (label, url) ->
            ActionSetting(
                stringResource(label), onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                tag = "projects:${project.name}:link",
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(project.pictures) { index, picture ->
                Image(
                    painterResource(picture), contentDescription = project.name, contentScale = ContentScale.Fit,
                    modifier = Modifier.height(project.thumbnail.dp).metroPress(onClick = { onPicture(project.pictures to index) }).testTag("projects:${project.name}:picture:$index"),
                )
            }
        }
    }
}

/** The pictures of a project full screen: swipe for the next, double tap to zoom in and out, drag to move when zoomed, back to close. */
@Composable
private fun PictureViewer(pictures: List<Int>, start: Int, onClose: () -> Unit) {
    val pager = rememberPagerState(initialPage = start) { pictures.size }
    var zoomed by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("projects:viewer")) {
            HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed) { page ->
                var offset by remember { mutableStateOf(Offset.Zero) }
                var scale by remember { mutableFloatStateOf(1f) }
                Image(
                    painterResource(pictures[page]), contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { if (!zoomed) onClose() }, onDoubleTap = {
                                zoomed = scale == 1f
                                scale = if (zoomed) ZOOM else 1f
                                offset = Offset.Zero
                            })
                        }
                        .pointerInput(zoomed) {
                            if (zoomed) detectDragGestures { change, amount ->
                                change.consume()
                                offset += amount
                            }
                        }
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                )
            }
            MetroText(
                "${pager.currentPage + 1} / ${pictures.size}", size = 16.sp, color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
            )
        }
    }
}

private const val ZOOM = 2.5f
