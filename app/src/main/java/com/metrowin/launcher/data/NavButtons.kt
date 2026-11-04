package com.metrowin.launcher.data

/** Where the three buttons of the navigation bar (back, the Windows logo, search) sit across a bar [width] wide, and which one a touch hits. */
object NavButtons {
    /** The centres of the three buttons: a third of the width apart by default, [spacingPercent] of that, around the middle. */
    fun centers(width: Float, spacingPercent: Int): FloatArray {
        val step = width / 3f * spacingPercent / 100f
        return floatArrayOf(width / 2f - step, width / 2f, width / 2f + step)
    }

    /** The button (0 to 2) whose centre is nearest to [x]: at the default spacing that is the third of the bar the touch is in. */
    fun nearest(x: Float, width: Float, spacingPercent: Int): Int {
        val centers = centers(width, spacingPercent)
        return centers.indices.minBy { kotlin.math.abs(centers[it] - x) }
    }
}
