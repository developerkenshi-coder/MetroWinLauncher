package com.metrowin.launcher.ui.anim

import com.metrowin.launcher.data.ThemeStyle

/** The things that open and close on top of the screen and need a time to do it. */
enum class Surface { Page, Dialog, Menu }

/** How long a [Surface] takes to come in and to go, in milliseconds (before the speed of the settings, see [metroTween]). */
data class Timing(val inMs: Int, val outMs: Int)

/**
 * The one table of the times of the transitions: a row for each [Surface], bent by the look in use (Glass is a little slower and softer, Droid a
 * little brisker, Classic is the reference the constants of [Motion] were written for) and by "reduce motion" (a page only fades, quickly; a
 * dialog and a menu are simply there). The turnstile host, the dialog and the pop-up menu read their times here, so they cannot drift apart.
 * The turn of the activity of a mini app ([com.metrowin.launcher.ui.mini.PageTurn]) runs where no look is known and keeps the Classic row
 * through the same [Motion] constants.
 */
object MotionTable {
    private val classic = mapOf(
        Surface.Page to Timing(Motion.TURNSTILE_IN_MS, Motion.TURNSTILE_OUT_MS),
        Surface.Dialog to Timing(250, 180),
        Surface.Menu to Timing(160, 120),
    )
    private val reducedPage = Timing(120, 120)
    private val still = Timing(0, 0)

    private fun scale(style: ThemeStyle) = when (style) {
        ThemeStyle.Classic -> 1f
        ThemeStyle.Glass -> 1.15f
        ThemeStyle.Droid -> 0.85f
    }

    fun timing(surface: Surface, style: ThemeStyle, reduced: Boolean): Timing {
        if (reduced) return if (surface == Surface.Page) reducedPage else still
        val base = classic.getValue(surface)
        val k = scale(style)
        return Timing((base.inMs * k).toInt(), (base.outMs * k).toInt())
    }
}
