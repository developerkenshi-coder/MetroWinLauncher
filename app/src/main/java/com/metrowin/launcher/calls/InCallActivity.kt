package com.metrowin.launcher.calls

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import com.metrowin.launcher.data.IconLoader
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.dialer.DialerActivity
import com.metrowin.launcher.notifications.OverlayTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * The screen of the calls: it comes up over the lock screen for a call and goes when the last call ends. The face stays dark against the
 * ear, as on every phone: the proximity sensor turns the screen off while a call is on, and the speaker keeps it on.
 */
class InCallActivity : ComponentActivity() {
    private var proximity: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION") // the flags work on every version; the methods that replace them come with Android 8.1
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val actions = CallActions(
            onAnswer = CallManager::answer, onDecline = CallManager::decline, onHangUp = CallManager::hangUp, onHold = CallManager::hold,
            onMute = CallManager::mute, onSpeaker = CallManager::speaker, onTone = CallManager::tone,
            onMerge = { id -> if (!CallManager.merge(id)) android.widget.Toast.makeText(this, com.metrowin.launcher.R.string.incall_merge_failed, android.widget.Toast.LENGTH_SHORT).show() },
            onAddCall = { startActivity(Intent(this, DialerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
        )
        val settings = SettingsStore(this).load()
        setContent {
            val lines by CallManager.lines.collectAsState()
            val audio by CallManager.audio.collectAsState()
            // The screen stays a moment on "call ended", then goes; a new call in that moment keeps it.
            LaunchedEffect(lines.isEmpty()) {
                if (lines.isEmpty()) {
                    delay(ENDED_MS)
                    finish()
                }
            }
            OverlayTheme(settings, remember { IconLoader(applicationContext) }) { InCallScreen(lines, audio, actions) }
        }
        lifecycleScope.launch {
            CallManager.lines.collect { lines -> holdProximity(lines.any { !it.ringing && !it.held } && !CallManager.audio.value.speaker) }
        }
        lifecycleScope.launch { CallManager.audio.collect { audio -> if (audio.speaker) holdProximity(false) } }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** The buttons of the notification of a call coming in open this screen with what to do. */
    private fun handle(intent: Intent) {
        val ringing = CallManager.lines.value.firstOrNull { it.ringing } ?: return
        when (intent.getStringExtra(EXTRA_ACTION)) {
            ACTION_ANSWER -> CallManager.answer(ringing.id)
            ACTION_DECLINE -> CallManager.decline(ringing.id)
        }
        intent.removeExtra(EXTRA_ACTION)
    }

    private fun holdProximity(on: Boolean) {
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (on && proximity == null && power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            // A lock that cannot be had must not take the screen of the call down with it: the call shows, only the screen stays on.
            proximity = runCatching { power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "metrowin:call").apply { acquire(PROXIMITY_MAX_MS) } }.getOrNull()
        } else if (!on) {
            proximity?.takeIf { it.isHeld }?.release()
            proximity = null
        }
    }

    override fun onDestroy() {
        holdProximity(false)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ACTION = "call_action"
        const val ACTION_ANSWER = "answer"
        const val ACTION_DECLINE = "decline"
        private const val ENDED_MS = 1_200L

        /** A call does not last longer than this: the lock is let go by itself if the screen is somehow never told the call ended. */
        private const val PROXIMITY_MAX_MS = 4 * 60 * 60 * 1_000L
    }
}
