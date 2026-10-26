package com.metrowin.launcher.data

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.metrowin.launcher.R
import com.metrowin.launcher.model.Glyph

/** The volumes of Android a key can move, as the panel names them. */
enum class VolumeStream(val androidStream: Int, @param:StringRes val label: Int, val glyph: Glyph) {
    Voice(AudioManager.STREAM_VOICE_CALL, R.string.volume_call, Glyph.Phone),
    Music(AudioManager.STREAM_MUSIC, R.string.volume_media, Glyph.Music),
    Ring(AudioManager.STREAM_RING, R.string.volume_ring, Glyph.Bell),
    Alarm(AudioManager.STREAM_ALARM, R.string.volume_alarm, Glyph.Alarm),
}

/** Where a press of a volume key goes. */
sealed interface VolumeRoute {
    data class Stream(val stream: VolumeStream) : VolumeRoute

    /** A player with a volume of its own (a cast, a speaker that takes the volume from the phone): that one is moved. */
    data object Remote : VolumeRoute
}

/**
 * What a volume key moves, the way Android decides it: the call while there is one, then the player that has a volume of its
 * own, then the music while any app plays, and the ringer when nothing sounds. Pure, so it is tested.
 */
object VolumeRouting {
    fun route(inCall: Boolean, remotePlaying: Boolean, musicPlaying: Boolean): VolumeRoute = when {
        inCall -> VolumeRoute.Stream(VolumeStream.Voice)
        remotePlaying -> VolumeRoute.Remote
        musicPlaying -> VolumeRoute.Stream(VolumeStream.Music)
        else -> VolumeRoute.Stream(VolumeStream.Ring)
    }

    /** A level of a volume as a fraction from 0 (the lowest) to 1 (the highest). */
    fun fraction(level: Int, min: Int, max: Int): Float = if (max <= min) 0f else ((level - min).toFloat() / (max - min)).coerceIn(0f, 1f)

    /** The level a fraction stands for, rounded to a step the phone has. */
    fun level(fraction: Float, min: Int, max: Int): Int = (min + Math.round(fraction.coerceIn(0f, 1f) * (max - min))).coerceIn(min, max)

    /** What a key press does to a level: one step, never past the ends. */
    fun step(level: Int, min: Int, max: Int, raise: Boolean): Int = (level + if (raise) 1 else -1).coerceIn(min, max)
}

/**
 * One volume on the panel: a stream of Android ([stream]) or the volume of a player ([remotePackage]), where it is between [min]
 * and [max], and whether it is silenced (the ringer on vibrate or silent, a muted stream).
 */
@Immutable
data class VolumeRow(
    val stream: VolumeStream?,
    val remotePackage: String?,
    val level: Int,
    val min: Int,
    val max: Int,
    val muted: Boolean = false,
    /** For the ringer: Android's ringer mode (normal, vibrate, silent). */
    val ringerMode: Int = AudioManager.RINGER_MODE_NORMAL,
) {
    val fraction: Float get() = VolumeRouting.fraction(level, min, max)
}

/** What the volume panel shows: the volume the key moved, who is making the sound, and the other volumes when it is opened out. */
@Immutable
data class VolumeHud(
    /** Changes with every press, so the panel stays up while the keys are used. */
    val token: Int,
    val main: VolumeRow,
    /** The name of the app that plays, when the volume is the music's. */
    val app: String? = null,
    val others: List<VolumeRow> = emptyList(),
    val expanded: Boolean = false,
)

/** The volumes of the phone. A thin layer on the audio manager, so the rest can be tested with plain numbers. */
class VolumeControl(context: Context) {
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)

    fun inCall(): Boolean = audio?.mode.let { it == AudioManager.MODE_IN_CALL || it == AudioManager.MODE_IN_COMMUNICATION }

    fun musicActive(): Boolean = audio?.isMusicActive == true

    fun read(stream: VolumeStream): VolumeRow? {
        val manager = audio ?: return null
        return runCatching {
            val s = stream.androidStream
            val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) manager.getStreamMinVolume(s) else 0
            val ringer = manager.ringerMode
            VolumeRow(
                stream, null, manager.getStreamVolume(s), min, manager.getStreamMaxVolume(s),
                muted = if (stream == VolumeStream.Ring) ringer != AudioManager.RINGER_MODE_NORMAL else manager.isStreamMute(s),
                ringerMode = ringer,
            )
        }.getOrNull()
    }

    /** Moves the volume one step. The ringer may go on to vibrate and silent, as with the keys of the phone, where Android lets an app do that. */
    fun adjust(stream: VolumeStream, raise: Boolean): VolumeRow? {
        val manager = audio ?: return null
        val direction = if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        val moved = runCatching {
            manager.adjustStreamVolume(stream.androidStream, direction, if (stream == VolumeStream.Ring) AudioManager.FLAG_ALLOW_RINGER_MODES else 0)
        }.isSuccess
        // Silent needs the "do not disturb" access: without it the step is made without the change of mode.
        if (!moved) runCatching { manager.adjustStreamVolume(stream.androidStream, direction, 0) }
        return read(stream)
    }

    fun set(stream: VolumeStream, level: Int): VolumeRow? {
        val manager = audio ?: return null
        runCatching { manager.setStreamVolume(stream.androidStream, level, 0) }
        return read(stream)
    }
}
