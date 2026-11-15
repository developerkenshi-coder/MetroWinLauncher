package com.metrowin.launcher.ui.mini

import com.metrowin.launcher.ui.components.PageMaxWidth
import com.metrowin.launcher.ui.components.PageMaxWidthPanes
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
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
import com.metrowin.launcher.data.MiniApps
import com.metrowin.launcher.ui.theme.MetroAccents
import androidx.compose.ui.graphics.toArgb
import com.metrowin.launcher.data.Prefs
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalOled
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.theme.pageIsLight
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import com.metrowin.launcher.ui.anim.rememberMetroHaptics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.metrowin.launcher.data.IconLoader
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.notifications.OverlayTheme
import com.metrowin.launcher.data.MetroSound
import com.metrowin.launcher.ui.anim.MetroSoundPlayer
import com.metrowin.launcher.ui.anim.rotateOnTurn
import com.metrowin.launcher.ui.gesture.twoFingerSwipe
import com.metrowin.launcher.ui.components.cutoutSidesPadding
import com.metrowin.launcher.ui.components.LocalBottomBarsPadded
import com.metrowin.launcher.ui.components.navigationBarsPaddingWithButtons
import com.metrowin.launcher.ui.components.navigationSidesPadding
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

    /** The sounds of Start (the same setting): a glide up when the app opens and down when it closes. Made only when the setting is on. */
    private var sounds: MetroSoundPlayer? = null
    private var closeSounded = false

    private fun sound(which: MetroSound) {
        if (!settings.metroSounds) return
        (sounds ?: MetroSoundPlayer(this).also { sounds = it; Thread { it.prepare() }.start() }).play(which)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        settings = SettingsStore.of(this).load()
        // Not again when the screen only turns or the font changes and the app is made anew.
        if (savedInstanceState == null) sound(MetroSound.Open)
        setContent {
            // An app the user gave its own accent (Settings, Theme) opens in it; the others follow the theme.
            val own = MiniApps.activities.entries.firstOrNull { it.value == javaClass }?.key?.let { settings.miniAccents[it] }?.let { MetroAccents[it] }
            OverlayTheme(settings, remember { IconLoader(applicationContext) }, own) {
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
                val swipe = with(LocalDensity.current) { HOME_SWIPE.toPx() }
                CompositionLocalProvider(
                    LocalAppTitle provides title.toString(),
                    LocalHapticFeedback provides rememberMetroHaptics(settings.tapHaptics, settings.vibrationStrength),
                ) {
                    TurnPage(turn, onBack = ::finish) {
                        // Two fingers up or down close the app and go back to Start (the gesture of Start's own two-finger actions; off by default).
                        Box(Modifier.twoFingerSwipe(settings.miniHomeSwipe, swipe, onDown = ::finish, onUp = ::finish).rotateOnTurn()) { Content() }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        settings = SettingsStore.of(this).load()
        Prefs.main(this).registerOnSharedPreferenceChangeListener(settingsListener)
    }

    override fun onDestroy() {
        // The close sound is short but is played as the window goes: give it the time before the pool is let go.
        sounds?.let { player -> android.os.Handler(mainLooper).postDelayed({ player.release() }, SOUND_TAIL_MS) }
        super.onDestroy()
    }

    override fun onStop() {
        Prefs.main(this).unregisterOnSharedPreferenceChangeListener(settingsListener)
        super.onStop()
    }

    /** With the original animation the page turns away first, and the window closes without its own animation. */
    override fun finish() {
        if (!closeSounded) { closeSounded = true; sound(MetroSound.Close) }
        if (!turn.leave(lifecycleScope) { returnToLauncher(); super.finish(); overridePendingTransition(0, 0) }) { returnToLauncher(); super.finish() }
    }

}

/**
 * The page of a mini app: the background of the look in use, clear of the bars and of the camera hole. [backdrop] draws the page
 * behind it (a cover, a picture): the default is the background of the look; pass `{ this }` for none, when the content draws its own.
 * The sides of the content: see [miniPageSide], which the screens use in place of a fixed 24 dp.
 */
@Composable
fun MiniFrame(
    modifier: Modifier = Modifier,
    backdrop: @Composable Modifier.() -> Modifier = { pageBackground() },
    /** The page is lifted by the keyboard. */
    ime: Boolean = false,
    /** The page has two panes side by side: it goes as wide as [PageMaxWidthPanes], not the 640 dp of a single column. */
    panes: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    // shortcut: this frame adds only what "wide" says, because the screens have their own 24 dp inside; they move to miniPageSide(24.dp) one by one.
    Column(
        modifier.fillMaxSize().backdrop().topBarsPadding().then(if (LocalBottomBarsPadded.current) Modifier.navigationSidesPadding() else Modifier.navigationBarsPaddingWithButtons()).then(if (ime) Modifier.imePadding() else Modifier)
            .cutoutSidesPadding().centeredContent(if (panes) PageMaxWidthPanes else PageMaxWidth).padding(horizontal = pageSide(0.dp)).padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

private const val SOUND_TAIL_MS = 600L
private val HOME_SWIPE = 96.dp
