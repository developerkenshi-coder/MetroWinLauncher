package com.metrowin.launcher.calculator

import androidx.compose.runtime.Composable
import com.metrowin.launcher.ui.mini.MiniActivity

/** The calculator of the launcher: standard and scientific, with a history and a converter of units and money. */
class CalculatorActivity : MiniActivity() {
    @Composable
    override fun Content() = CalculatorScreen()
}
