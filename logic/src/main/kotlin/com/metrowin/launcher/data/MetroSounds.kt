package com.metrowin.launcher.data

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** The sounds of the launcher, apart from the click of a tap. */
enum class MetroSound { Notify, Open, Close }

/**
 * The little sounds that go with the Metro things, made here like the click (no file to ship, none of Microsoft's): a two-note chime
 * for a new notification, and a short glide up when the Action Center comes down and down when it goes. Quiet, and short. Pure, so they are
 * tested.
 */
object MetroSounds {
    const val RATE = ClickSound.SAMPLE_RATE

    fun pcm(sound: MetroSound): ShortArray = when (sound) {
        MetroSound.Notify -> ping(988.0, 90) + ping(1319.0, 170)
        MetroSound.Open -> glide(330.0, 700.0, 120)
        MetroSound.Close -> glide(700.0, 330.0, 100)
    }

    fun wav(sound: MetroSound): ByteArray = ClickSound.wav(pcm(sound), RATE)

    /** A note that starts at once and dies away. */
    private fun ping(hertz: Double, millis: Int): ShortArray {
        val count = RATE * millis / 1000
        return ShortArray(count) { i ->
            val t = i.toDouble() / RATE
            (sin(2.0 * PI * hertz * t) * envelope(i, count, 14.0) * VOLUME * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /** A tone whose pitch goes from [from] to [to]. */
    private fun glide(from: Double, to: Double, millis: Int): ShortArray {
        val count = RATE * millis / 1000
        var phase = 0.0
        return ShortArray(count) { i ->
            val hertz = from + (to - from) * i / count
            phase += 2.0 * PI * hertz / RATE
            (sin(phase) * envelope(i, count, 6.0) * VOLUME * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /** A quick rise (no click at the start), a decay of the given speed, and nothing at the end. */
    private fun envelope(i: Int, count: Int, decayPerSecond: Double): Double {
        val attack = (RATE * 4 / 1000).coerceAtLeast(1)
        val fade = (RATE * 10 / 1000).coerceAtLeast(1)
        val rise = if (i < attack) i.toDouble() / attack else 1.0
        val tail = if (i >= count - fade) (count - 1 - i).toDouble() / fade else 1.0
        return rise * tail * exp(-(i.toDouble() / RATE) * decayPerSecond)
    }

    private const val VOLUME = 0.35
}
