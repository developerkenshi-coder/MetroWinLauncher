package com.metrowin.launcher.data

import androidx.annotation.StringRes
import com.metrowin.launcher.R

/**
 * How much room the pages leave at the sides of the screen: Start, the app list, the settings and the other pages of the launcher. Three
 * steps, each a few dp less or more than the design, and [sideDp] keeps whatever the step is from leaving a page too narrow to use.
 */
enum class PagePadding(val code: String, val deltaDp: Float, @param:StringRes val label: Int) {
    Compact("compact", -6f, R.string.padding_compact),
    Normal("normal", 0f, R.string.padding_normal),
    Wide("wide", 6f, R.string.padding_wide);

    /**
     * The margin, in dp, of a page whose design has [baseDp] at each side, on a screen [widthDp] wide. Never under [MIN_SIDE_DP] (or the
     * design's own, when that is smaller) and never so wide that less than [MIN_CONTENT_DP] is left for the content, unless the design
     * itself leaves less.
     */
    fun sideDp(baseDp: Float, widthDp: Float): Float {
        val most = maxOf(baseDp, (widthDp - MIN_CONTENT_DP) / 2f)
        return (baseDp + deltaDp).coerceIn(minOf(MIN_SIDE_DP, baseDp), most)
    }

    companion object {
        const val MIN_SIDE_DP = 4f
        const val MIN_CONTENT_DP = 240f
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: Normal
    }
}
