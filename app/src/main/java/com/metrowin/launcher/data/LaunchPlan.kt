package com.metrowin.launcher.data

/** What happens on screen between a tap and the app opening. */
data class LaunchPlan(
    /** The tiles (or the list) swing away first, the tapped one last. */
    val tilesTurnAway: Boolean,
    /** The app grows out of the tile that was touched, or out of the middle of the screen when it was not a tile. */
    val expand: Boolean,
    /** How long to wait for the tiles to turn away before the app is started. */
    val waitMs: Long,
    /** The turnstile of the original Windows Phone (feathered, exponential), not the modern one. */
    val original: Boolean = false,
    /** For the styles that only change how the window of the app comes in (fade, slide, rise, zoom, none): which one. */
    val window: LaunchAnimation = LaunchAnimation.Turnstile,
)

object LaunchPlans {
    const val START_TURN_MS = 300L
    const val LIST_TURN_MS = 240L

    /** The original one starts the app a little later from Start, so the last tile is nearly gone when the window comes in; it is still no slower than a tap-and-wait of a third of a second. */
    const val ORIGINAL_START_MS = 320L

    /** How the tiles of Start leave in the original turnstile: a row after the other (top first), the tapped tile last, and when they come back. */
    const val ORIGINAL_OUT_MS = 250
    const val ORIGINAL_IN_MS = 350
    private const val OUT_STEP_MS = 20L
    private const val OUT_MAX_MS = 100L
    private const val IN_STEP_MS = 50L
    private const val IN_MAX_MS = 250L

    /** How long a tile waits to turn away: [row] counts the rows of the grid from the top; the tapped tile always goes after the others. */
    fun outDelay(row: Int, tapped: Boolean): Long =
        if (tapped) OUT_MAX_MS + OUT_STEP_MS else (row.coerceAtLeast(0) * OUT_STEP_MS).coerceAtMost(OUT_MAX_MS)

    /** How long a tile waits to turn back in, after coming back to Start: the top rows first. */
    fun inDelay(row: Int): Long = (row.coerceAtLeast(0) * IN_STEP_MS).coerceAtMost(IN_MAX_MS)

    /** [fromStart] is true when the tap was on a tile of Start, false when it was on the app list or elsewhere. */
    fun decide(animation: LaunchAnimation, reduceMotion: Boolean, fromStart: Boolean): LaunchPlan = when {
        reduceMotion -> LaunchPlan(tilesTurnAway = false, expand = false, waitMs = 0L)
        animation == LaunchAnimation.Expand -> LaunchPlan(tilesTurnAway = false, expand = true, waitMs = 0L)
        animation == LaunchAnimation.Original ->
            LaunchPlan(tilesTurnAway = true, expand = false, waitMs = if (fromStart) ORIGINAL_START_MS else LIST_TURN_MS, original = true)
        animation == LaunchAnimation.Turnstile -> LaunchPlan(tilesTurnAway = true, expand = false, waitMs = if (fromStart) START_TURN_MS else LIST_TURN_MS)
        // Fade, slide, rise, zoom, none: Start stays still and the window comes in its own way.
        else -> LaunchPlan(tilesTurnAway = false, expand = false, waitMs = 0L, window = animation)
    }
}
