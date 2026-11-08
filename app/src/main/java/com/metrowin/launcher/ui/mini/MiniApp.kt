package com.metrowin.launcher.ui.mini

import com.metrowin.launcher.ui.components.centeredContent
import com.metrowin.launcher.ui.components.pageSide
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.metrowin.launcher.ui.components.LocalAppTitle
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.metrowin.launcher.data.IconLoader
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.notifications.OverlayTheme
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val settings = SettingsStore.of(this).load()
        setContent {
            OverlayTheme(settings, remember { IconLoader(applicationContext) }) {
                CompositionLocalProvider(LocalAppTitle provides title.toString()) { TurnPage(turn) { Content() } }
            }
        }
    }

    /** With the original animation the page turns away first, and the window closes without its own animation. */
    override fun finish() {
        if (!turn.leave(lifecycleScope) { returnToLauncher(); super.finish(); overridePendingTransition(0, 0) }) { returnToLauncher(); super.finish() }
    }

}

/** The page of a mini app: the background of the look in use, clear of the bars and of the camera hole. */
@Composable
fun MiniFrame(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    // shortcut: the screens of the mini apps have their own 24 dp inside, so only "wide" changes them, compact leaves them as they are; give them pageSide when they are touched next.
    Column(
        modifier.fillMaxSize().pageBackground().topBarsPadding().navigationBarsPadding().cutoutSidesPadding().centeredContent()
            .padding(horizontal = pageSide(0.dp)),
        content = content,
    )
}
