package com.metrowin.launcher.recorder

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import com.metrowin.launcher.R
import com.metrowin.launcher.data.VoiceRecorder
import com.metrowin.launcher.ui.actioncenter.MetroToast
import com.metrowin.launcher.ui.mini.MiniActivity

/** The voice recorder of the launcher: records, plays, cuts a piece into a new recording, writes down what is said and sends it to the notes. */
class RecorderActivity : MiniActivity() {
    @Composable
    override fun Content() = RecorderScreen()

    /** While recording, with the switch on, a volume key puts a marker (the volume does not change); held down it does not repeat. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val volume = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (volume && VoiceRecorder.active && !VoiceRecorder.paused && RecorderPrefs.on(this, Opt.VOLUME_MARK) && RecorderPrefs.on(this, Opt.MARKS)) {
            if (event.repeatCount == 0) {
                VoiceRecorder.addMark()
                MetroToast.show(this, R.string.rec_announce_marked)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
