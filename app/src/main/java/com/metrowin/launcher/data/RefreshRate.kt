package com.metrowin.launcher.data

import kotlin.math.abs

/**
 * One display mode: resolution in pixels and refresh rate in Hz. [alternatives] are the rates the screen can switch to from
 * this mode without a flash (Android 12 and later says which; before that the list is empty and no switch is known to be clean).
 */
data class DisplayModeInfo(
    val id: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
    val alternatives: List<Float> = emptyList(),
)

object RefreshRate {
    /** How long after the last touch (or the launcher coming on screen) the high rate is kept. */
    const val IDLE_MS = 3_000L

    /**
     * The mode to ask for: the highest refresh rate among the modes that have the resolution of
     * [current], so the screen never changes resolution. A tie keeps [current]. Null if the display
     * reports no mode with the current resolution.
     */
    fun best(current: DisplayModeInfo, supported: List<DisplayModeInfo>): DisplayModeInfo? {
        val sameResolution = supported.filter { it.width == current.width && it.height == current.height }
        val fastest = sameResolution.maxByOrNull { it.refreshRate } ?: return null
        return if (fastest.refreshRate > current.refreshRate) fastest else sameResolution.firstOrNull { it.id == current.id } ?: fastest
    }

    /** The slowest rate asked for at rest: a little under 60, because panels report 59.94 or 60.000004. */
    const val REST_MIN_HZ = 59f

    /**
     * The mode to ask for while nobody touches the screen: the slowest one that is still 60 Hz or more, at the resolution of
     * [current] and one the screen can go to from it without a flash ([DisplayModeInfo.alternatives]). Android on its own does
     * not go down: a phone whose ceiling is 120 Hz (the user setting of many) stays there while the launcher only lets go of its
     * request. Below 60 Hz the tiles that turn by themselves would show steps, and phones list those rates only as divisors of
     * a physical mode. Null when the screen has no such mode.
     */
    fun atRest(current: DisplayModeInfo, supported: List<DisplayModeInfo>): DisplayModeInfo? =
        supported
            .filter { it.width == current.width && it.height == current.height && it.refreshRate >= REST_MIN_HZ }
            .filter { it.id == current.id || current.alternatives.any { rate -> abs(rate - it.refreshRate) < RATE_TOLERANCE_HZ } }
            .minWithOrNull(compareBy<DisplayModeInfo> { it.refreshRate }.thenBy { if (it.id == current.id) 0 else 1 })

    /**
     * Whether the high rate is asked for at [now]: wanted in the settings, not while saving battery, and only while the screen is
     * being used ([lastTouchAt] within [idleMs]). A screen that nobody touches shows what changes by itself (a clock, a tile that
     * turns), which looks the same at 60 Hz: kept at 120 Hz it would draw the most power for the longest time, on the screen it
     * shows the most. At rest [atRest] is asked for instead.
     */
    fun wantsHigh(wanted: Boolean, saving: Boolean, lastTouchAt: Long, now: Long, idleMs: Long = IDLE_MS): Boolean =
        wanted && !saving && now - lastTouchAt in 0 until idleMs

    /**
     * Whether the high rate can be taken away and given back from the mode the screen is in: it lists rates it can go to without
     * a flash. When it lists none (or the phone does not say, before Android 12) the rate is not known to be clean to change and
     * stays as asked, as it always did.
     */
    fun canLeave(current: DisplayModeInfo): Boolean = current.alternatives.isNotEmpty()

    private const val RATE_TOLERANCE_HZ = 0.5f
}
