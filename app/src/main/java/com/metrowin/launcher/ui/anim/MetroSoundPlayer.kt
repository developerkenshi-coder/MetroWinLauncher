package com.metrowin.launcher.ui.anim

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.metrowin.launcher.data.MetroSound
import com.metrowin.launcher.data.MetroSounds
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

/**
 * Plays the sounds of [MetroSounds]. They are made into small files once and loaded when the launcher comes on screen (or at the first
 * sound, for the windows over other apps); a sound asked for while its file loads plays when it is ready. They follow the volume of the phone
 * and are silent on a silent phone, like the click.
 */
class MetroSoundPlayer(context: Context) {
    private val app = context.applicationContext
    private var pool: SoundPool? = null
    private val ids = HashMap<MetroSound, Int>()
    private val loaded = HashSet<Int>()
    private var waiting: Int? = null

    /** Blocking: writes the small files once and loads them. Call it off the main thread. */
    @Synchronized
    fun prepare() {
        if (pool != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val made = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attributes).build()
        made.setOnLoadCompleteListener { pool, id, status ->
            if (status != 0) return@setOnLoadCompleteListener
            synchronized(this) {
                loaded += id
                if (waiting == id) {
                    waiting = null
                    pool.play(id, 1f, 1f, 1, 0, 1f)
                }
            }
        }
        MetroSound.entries.forEach { sound ->
            val file = File(app.cacheDir, "metro_${sound.name.lowercase()}.wav")
            runCatching { if (!file.exists() || file.length() == 0L) file.writeBytes(MetroSounds.wav(sound)) }.onSuccess {
                ids[sound] = made.load(file.path, 1)
            }
        }
        pool = made
    }

    @Synchronized
    fun release() {
        pool?.release()
        pool = null
        ids.clear()
        loaded.clear()
        waiting = null
    }

    @Synchronized
    fun play(sound: MetroSound) {
        val id = ids[sound] ?: return
        if (id in loaded) pool?.play(id, 1f, 1f, 1, 0, 1f) else waiting = id
    }
}

/** The sounds of the Metro things while the launcher is on screen, when the setting is on; call the result with the sound to play. */
@Composable
fun rememberMetroSounds(enabled: Boolean): (MetroSound) -> Unit {
    val context = LocalContext.current
    val player = remember(context) { MetroSoundPlayer(context) }
    val on by rememberUpdatedState(enabled)
    LaunchedWhileStarted(enabled) {
        if (!enabled) return@LaunchedWhileStarted
        try {
            withContext(Dispatchers.IO) { player.prepare() }
            awaitCancellation()
        } finally {
            player.release()
        }
    }
    return remember(player) { { sound -> if (on) player.play(sound) } }
}
