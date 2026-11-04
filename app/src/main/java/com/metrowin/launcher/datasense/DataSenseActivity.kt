package com.metrowin.launcher.datasense

import androidx.compose.runtime.Composable
import com.metrowin.launcher.ui.mini.MiniActivity

/** Data Sense and Storage Sense of the launcher: the data used (by day and by app), the memory, the time on screen, the battery and the network. */
class DataSenseActivity : MiniActivity() {
    @Composable
    override fun Content() = DataSenseScreen()
}
