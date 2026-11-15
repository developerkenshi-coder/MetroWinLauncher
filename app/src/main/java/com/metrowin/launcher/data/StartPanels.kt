package com.metrowin.launcher.data

import com.metrowin.launcher.ui.components.WindowClass

/** Start and the app list side by side, as the two panels of a wide screen, instead of one swipe away. */
object StartPanels {
    /** A screen wider than high and no higher than this is a phone on its side: it keeps the pages (see [TileGrid.columnsFor]). */
    private const val PHONE_HEIGHT_DP = 500f

    /** Only an expanded window with room in height: a tablet, an unfolded big phone, a free window; not a phone on its side. */
    fun active(enabled: Boolean, widthDp: Float, heightDp: Float): Boolean =
        enabled && WindowClass.of(widthDp) == WindowClass.Expanded && heightDp > PHONE_HEIGHT_DP

    /** The width the tiles of Start have: half of the window when the panels are on. */
    fun startWidthDp(panels: Boolean, widthDp: Float): Float = if (panels) widthDp / 2 else widthDp
}
