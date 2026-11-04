package com.metrowin.launcher.recorder

import androidx.compose.runtime.Composable
import com.metrowin.launcher.ui.mini.MiniActivity

/** The voice recorder of the launcher: records, plays, cuts a piece into a new recording, writes down what is said and sends it to the notes. */
class RecorderActivity : MiniActivity() {
    @Composable
    override fun Content() = RecorderScreen()
}
