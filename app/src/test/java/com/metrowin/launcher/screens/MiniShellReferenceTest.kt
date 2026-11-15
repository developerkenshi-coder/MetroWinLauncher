package com.metrowin.launcher.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.LocalAppTitle
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.theme.Palettes
import com.metrowin.launcher.ui.theme.DefaultAccent
import com.metrowin.launcher.ui.theme.panel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shell every mini app is made of (page of the look, name of the app over the pivot, rows on the surface, the bar) on a phone, in each look
 * and in the light and the dark, against the reference pictures of [ReferenceImage]. The screens of the single mini apps need their own data and are not here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xhdpi")
class MiniShellReferenceTest : ScreenshotBase() {
    private fun shell(style: ThemeStyle, dark: Boolean) {
        show(dark = dark, style = style, droid = if (style == ThemeStyle.Droid) Palettes.fromSeed(DefaultAccent) else null) {
            CompositionLocalProvider(LocalAppTitle provides "Notes") {
                MiniFrame {
                    PivotHeader(listOf("all", "starred", "archive"), rememberPagerState { 3 }, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(4) { MetroText("Note $it", size = 20.sp, modifier = Modifier.fillMaxWidth().panel().padding(14.dp)) }
                    }
                    AppBar(listOf(AppBarItem("+", "new") {}, AppBarItem("?", "search") {}), menu = listOf(AppBarItem("", "settings") {}))
                }
            }
        }
        advance(500)
        ReferenceImage.check("mini_${style.code}_${if (dark) "dark" else "light"}", capture("mini_${style.code}_${if (dark) "dark" else "light"}"))
    }

    @Test fun classicDark() = shell(ThemeStyle.Classic, true)
    @Test fun classicLight() = shell(ThemeStyle.Classic, false)
    @Test fun glassDark() = shell(ThemeStyle.Glass, true)
    @Test fun glassLight() = shell(ThemeStyle.Glass, false)
    @Test fun droidDark() = shell(ThemeStyle.Droid, true)
    @Test fun droidLight() = shell(ThemeStyle.Droid, false)
}
