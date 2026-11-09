package com.metrowin.launcher.recents

import com.metrowin.launcher.ui.actioncenter.MetroToast
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.provider.Settings
import com.metrowin.launcher.R
import com.metrowin.launcher.data.RecentTasks
import com.metrowin.launcher.gestures.LockAccessibilityService
import com.metrowin.launcher.notifications.overlayAppItem
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.AppIcon
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun label(context: Context, packageName: String): String = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
}.getOrDefault(packageName)

@Composable
fun RecentsScreen(finish: () -> Unit) {
    val context = LocalContext.current
    val cards = remember { mutableStateListOf<String>() }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        cards.addAll(withContext(Dispatchers.IO) { RecentTasks.read(context) })
        loaded = true
    }
    MiniFrame {
        MetroText(stringResource(R.string.rc_title), size = 44.sp, modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp))
        if (loaded && cards.isEmpty()) PageNote(stringResource(R.string.rc_empty), Modifier.padding(horizontal = 24.dp))
        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.weight(1f).padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cards.toList(), key = { it }) { pkg ->
                Box(
                    Modifier.fillMaxWidth().aspectRatio(0.8f).testTag("recents:$pkg").background(LocalMetroColors.current.surface)
                        .metroPress(onClick = {
                            context.packageManager.getLaunchIntentForPackage(pkg)?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                            finish()
                        }),
                ) {
                    Box(Modifier.align(Alignment.Center)) { AppIcon(overlayAppItem(pkg), 72.dp) }
                    MetroText(label(context, pkg), size = 16.sp, weight = FontWeight.Normal, maxLines = 1, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(4.dp).testTag("recents:close:$pkg").metroPress(onClick = { RecentTasks.close(context, pkg); cards.remove(pkg) }).padding(10.dp),
                    ) { MetroText("✕", size = 18.sp, weight = FontWeight.Normal) }
                }
            }
        }
        AppBar(
            listOf(
                AppBarItem("✕", stringResource(R.string.rc_close_all)) {
                    cards.toList().forEach { RecentTasks.close(context, it) }
                    cards.clear()
                },
                AppBarItem("▭", stringResource(R.string.rc_android)) {
                    // Without the service there is nothing to ask: the cards stay, and the user is told why and taken to where it is switched on.
                    if (LockAccessibilityService.systemRecents()) finish() else {
                        MetroToast.show(context, R.string.rc_no_service)
                        runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                },
            ),
        )
    }
}
