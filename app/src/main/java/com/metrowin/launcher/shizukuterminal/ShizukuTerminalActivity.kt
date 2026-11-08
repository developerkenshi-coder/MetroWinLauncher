package com.metrowin.launcher.shizukuterminal

import androidx.compose.runtime.Composable
import com.metrowin.launcher.ui.mini.MiniActivity

/** Shizuku Terminal: the tools that need the rights of the shell (direct buttons, battery doctor, a command line, apps, system tweaks). */
class ShizukuTerminalActivity : MiniActivity() {
    @Composable
    override fun Content() = ShizukuTerminalScreen(intent.getIntExtra(EXTRA_PAGE, 0))

    companion object {
        /** The page to open on (the System tile opens the monitor). */
        const val EXTRA_PAGE = "page"
    }
}
