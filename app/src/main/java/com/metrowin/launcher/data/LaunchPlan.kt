package com.metrowin.launcher.data

/** What happens on screen between a tap and the app opening. */
data class LaunchPlan(
    /** The tiles (or the list) swing away first, the tapped one last. */
    val tilesTurnAway: Boolean,
    /** The app grows out of the tile that was touched, or out of the middle of the screen when it was not a tile. */
    val expand: Boolean,
    /** How long to wait for the tiles to turn away before the app is started. */
    val waitMs: Long,
)

object LaunchPlans {
    const val START_TURN_MS = 300L
    const val LIST_TURN_MS = 240L

    /** [fromStart] is true when the tap was on a tile of Start, false when it was on the app list or elsewhere. */
    fun decide(animation: LaunchAnimation, reduceMotion: Boolean, fromStart: Boolean): LaunchPlan = when {
        reduceMotion -> LaunchPlan(tilesTurnAway = false, expand = false, waitMs = 0L)
        animation == LaunchAnimation.Expand -> LaunchPlan(tilesTurnAway = false, expand = true, waitMs = 0L)
        else -> LaunchPlan(tilesTurnAway = true, expand = false, waitMs = if (fromStart) START_TURN_MS else LIST_TURN_MS)
    }
}
