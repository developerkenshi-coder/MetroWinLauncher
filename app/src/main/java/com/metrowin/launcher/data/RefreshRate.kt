package com.metrowin.launcher.data

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

    /**
     * Whether the high rate is asked for at [now]: wanted in the settings, not while saving battery, and only while the screen is
     * being used ([lastTouchAt] within [idleMs]). A screen that nobody touches shows what changes by itself (a clock, a tile that
     * turns), which looks the same at 60 Hz, and the phone is free to go lower: kept at 120 Hz it would draw the most power
     * for the longest time, on the screen it shows the most.
     */
    fun wantsHigh(wanted: Boolean, saving: Boolean, lastTouchAt: Long, now: Long, idleMs: Long = IDLE_MS): Boolean =
        wanted && !saving && now - lastTouchAt in 0 until idleMs

    /**
     * Whether the high rate can be taken away and given back from the mode the screen is in: it lists rates it can go to without
     * a flash. When it lists none (or the phone does not say, before Android 12) the rate is not known to be clean to change and
     * stays as asked, as it always did.
     */
    fun canLeave(current: DisplayModeInfo): Boolean = current.alternatives.isNotEmpty()
}
