package com.metrowin.launcher.ui.mini

import com.metrowin.launcher.ui.components.centeredContent
import com.metrowin.launcher.ui.components.pageSide
import android.app.ActivityManager
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.metrowin.launcher.ui.components.LocalAppTitle
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import com.metrowin.launcher.data.LauncherSettings
import androidx.compose.ui.graphics.toArgb
import com.metrowin.launcher.data.Prefs
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalOled
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.theme.pageIsLight
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.metrowin.launcher.data.IconLoader
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.notifications.OverlayTheme
import com.metrowin.launcher.ui.anim.rotateOnTurn
import com.metrowin.launcher.ui.components.cutoutSidesPadding
import com.metrowin.launcher.ui.components.topBarsPadding
import com.metrowin.launcher.ui.theme.pageBackground

/**
 * The shell of a mini app of the launcher: an activity of its own in the look of the launcher (accent, light or dark, style), edge to
 * edge. Back leaves the app unless [Content] handles it first (a `BackHandler`).
 */
abstract class MiniActivity : ComponentActivity() {
    @Composable
    protected abstract fun Content()

    private val turn by lazy { PageTurn(intent.getBooleanExtra(PageTurn.EXTRA, false)) }

    /** The settings in use; read again at each start, and whenever they are saved while this app is on screen (the launcher is in this process). */
    private var settings by mutableStateOf(LauncherSettings())
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> settings = SettingsStore.of(this).load() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        settings = SettingsStore.of(this).load()
        setContent {
            OverlayTheme(settings, remember { IconLoader(applicationContext) }) {
                // Light page: dark icons in the bars. The page knows its real color up there (look, accent, wallpaper), so this follows it.
                val light = pageIsLight(LocalStyle.current.kind, LocalMetroColors.current, LocalAccent.current, LocalOled.current)
                val accent = LocalAccent.current.toArgb() or 0xFF000000.toInt()
                LaunchedEffect(light) {
                    val bar = if (light) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
                }
                // The Recents of Android show the name of the app and the accent of the theme.
                LaunchedEffect(accent) {
                    @Suppress("DEPRECATION")
                    setTaskDescription(ActivityManager.TaskDescription(title.toString(), null as Bitmap?, accent))
                }
                CompositionLocalProvider(LocalAppTitle provides title.toString()) { TurnPage(turn) { Box(Modifier.rotateOnTurn()) { Content() } } }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        settings = SettingsStore.of(this).load()
        Prefs.main(this).registerOnSharedPreferenceChangeListener(settingsListener)
    }

    override fun onStop() {
        Prefs.main(this).unregisterOnSharedPreferenceChangeListener(settingsListener)
        super.onStop()
    }

    /** With the original animation the page turns away first, and the window closes without its own animation. */
    override fun finish() {
        if (!turn.leave(lifecycleScope) { returnToLauncher(); super.finish(); overridePendingTransition(0, 0) }) { returnToLauncher(); super.finish() }
    }

}

/**
 * The page of a mini app: the background of the look in use, clear of the bars and of the camera hole. [backdrop] draws the page
 * behind it (a cover, a picture): the default is the background of the look; pass `{ this }` for none, when the content draws its own.
 * The sides of the content: see [miniPageSide], which the screens use in place of a fixed 24 dp.
 */
@Composable
fun MiniFrame(modifier: Modifier = Modifier, backdrop: @Composable Modifier.() -> Modifier = { pageBackground() }, content: @Composable ColumnScope.() -> Unit) {
    // shortcut: this frame adds only what "wide" says, because the screens have their own 24 dp inside; they move to miniPageSide(24.dp) one by one.
    Column(
        modifier.fillMaxSize().backdrop().topBarsPadding().navigationBarsPadding().cutoutSidesPadding().centeredContent()
            .padding(horizontal = pageSide(0.dp)),
        content = content,
    )
}
