package com.metrowin.launcher.data

/**
 * How the photo of Start is framed: zoomed in (100 to 300 percent) and moved so that a point of the photo stays in view.
 * The sliders work in fractions from 0 to 1; these turn them into the percentages the settings keep. Pure.
 */
object PhotoFraming {
    const val MIN_ZOOM = 100
    const val MAX_ZOOM = 300

    fun zoomOf(fraction: Float): Int = (MIN_ZOOM + fraction.coerceIn(0f, 1f) * (MAX_ZOOM - MIN_ZOOM)).let { Math.round(it) }

    fun fractionOfZoom(zoom: Int): Float = ((zoom.coerceIn(MIN_ZOOM, MAX_ZOOM) - MIN_ZOOM).toFloat() / (MAX_ZOOM - MIN_ZOOM))

    /** A position slider (0 to 1) as the percent kept (0 to 100). */
    fun percentOf(fraction: Float): Int = Math.round(fraction.coerceIn(0f, 1f) * 100)

    /** The percent kept as the slider's fraction. */
    fun fractionOf(percent: Int): Float = percent.coerceIn(0, 100) / 100f

    /** The alignment bias (-1 to 1) of a position percent: 0 is the left or top edge of the photo, 100 the right or bottom. */
    fun bias(percent: Int): Float = (percent.coerceIn(0, 100) - 50) / 50f
}
