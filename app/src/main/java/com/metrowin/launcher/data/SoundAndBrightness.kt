package com.metrowin.launcher.data

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.annotation.StringRes
import com.metrowin.launcher.R
import kotlin.math.roundToInt

/** How the phone rings: out loud, with a vibration, or not at all. */
enum class RingerMode(@param:StringRes val label: Int) {
    Normal(R.string.ringer_sound),
    Vibrate(R.string.ringer_vibrate),
    Silent(R.string.ringer_silent),
}

/** Fractions and steps of a level that goes from 0 to [max]. Pure, so it is tested with numbers. */
object Levels {
    /** [level] of [max] as a fraction, 0 to 1. */
    fun fraction(level: Int, max: Int): Float = if (max <= 0) 0f else (level.toFloat() / max).coerceIn(0f, 1f)

    /** The level for a [fraction] of the way, in whole steps, never below [min]. */
    fun level(fraction: Float, max: Int, min: Int = 0): Int = (fraction.coerceIn(0f, 1f) * max).roundToInt().coerceIn(min.coerceAtMost(max), max)
}

/**
 * The media volume and the sound mode, for the sliders of the Action Center. No permission is needed to move the media
 * volume or to go from sound to vibration; going all the way to silent needs the "do not disturb" access, and without it the
 * user is taken to the screen where it is given.
 */
class SoundController(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)

    /** The media volume as a fraction, or null when the phone has no volume to read. */
    fun volume(): Float? = runCatching {
        Levels.fraction(audio.getStreamVolume(AudioManager.STREAM_MUSIC), audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
    }.getOrNull()

    fun setVolume(fraction: Float) {
        runCatching {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, Levels.level(fraction, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)), 0)
        }
    }

    fun ringer(): RingerMode? = runCatching {
        when (audio.ringerMode) {
            AudioManager.RINGER_MODE_VIBRATE -> RingerMode.Vibrate
            AudioManager.RINGER_MODE_SILENT -> RingerMode.Silent
            else -> RingerMode.Normal
        }
    }.getOrNull()

    /** Changes the mode. False when it needs the "do not disturb" access that has not been given (silent). */
    fun setRinger(mode: RingerMode): Boolean = runCatching {
        if (mode == RingerMode.Silent && !context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) {
            return@runCatching false
        }
        audio.ringerMode = when (mode) {
            RingerMode.Normal -> AudioManager.RINGER_MODE_NORMAL
            RingerMode.Vibrate -> AudioManager.RINGER_MODE_VIBRATE
            RingerMode.Silent -> AudioManager.RINGER_MODE_SILENT
        }
        true
    }.getOrDefault(false)
}

/**
 * The brightness of the screen, for the slider of the Action Center. Writing it is the "modify system settings" access:
 * given by the user in a screen of Android, never by a dialog, and the slider says so until it is.
 */
class BrightnessController(private val context: Context) {
    fun canWrite(): Boolean = Settings.System.canWrite(context)

    /** The brightness as a fraction (from the lowest the slider goes to full), or null when it cannot be read. */
    fun brightness(): Float? = runCatching {
        val value = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        Levels.fraction(value, MAX)
    }.getOrNull()

    /** Sets the brightness and switches the automatic one off, which would put it back. False without the access. */
    fun set(fraction: Float): Boolean {
        if (!canWrite()) return false
        return runCatching {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, Levels.level(fraction, MAX, MIN))
            true
        }.getOrDefault(false)
    }

    companion object {
        /** The scale of the system setting. */
        const val MAX = 255

        /** Never so low that the screen cannot be seen to put it back up. */
        const val MIN = 8
    }
}
