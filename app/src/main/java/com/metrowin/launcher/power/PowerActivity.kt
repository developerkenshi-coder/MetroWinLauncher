package com.metrowin.launcher.power

import androidx.compose.runtime.Composable
import com.metrowin.launcher.ui.mini.MiniActivity

/** What a long press of the power button showed on Windows Phone: pull the screen down to power off, or restart. Needs Shizuku (a shell is what may switch the phone off). */
class PowerActivity : MiniActivity() {
    @Composable
    override fun Content() = PowerScreen(::finish)
}
