package com.metrowin.launcher.data

/** One display mode: resolution in pixels and refresh rate in Hz. */
data class DisplayModeInfo(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

object RefreshRate {
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
}
