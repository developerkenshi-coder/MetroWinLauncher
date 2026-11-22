package com.metrowin.launcher.data

/**
 * The apps with news on the lock screen put in one row: the icons are as big as the row allows (never bigger than [MAX_ICON_DP], never
 * smaller than [MIN_ICON_DP]), and when even the smallest do not all fit the last ones become a "+N". Pure, so it is tested.
 */
object LockIconRow {
    const val MAX_ICON_DP = 28f
    const val MIN_ICON_DP = 18f
    const val GAP_DP = 14f

    /** How wide a character of the counter is, and how big the counter is, in multiples of the icon. */
    private const val CHAR = 0.45f

    /** [shown] badges at [iconDp], and [hidden] more that the "+N" stands for. */
    data class Fit(val iconDp: Float, val shown: Int, val hidden: Int)

    /** The size of the counter, in sp, that goes with an icon of [iconDp]. */
    fun textSp(iconDp: Float) = iconDp * 0.75f

    /** The width of the badge of an app whose counter has [digits] characters (none when it counts nothing), with an icon of [iconDp]. */
    fun slotDp(iconDp: Float, digits: Int) = iconDp + if (digits > 0) 4f + digits * CHAR * textSp(iconDp) else 0f

    private fun moreDp(iconDp: Float, hidden: Int) = (1 + hidden.toString().length) * CHAR * textSp(iconDp)

    /** [digits] are the characters of the counter of each app, in order; [widthDp] is the room of the row. */
    fun fit(digits: List<Int>, widthDp: Float): Fit {
        if (digits.isEmpty()) return Fit(MAX_ICON_DP, 0, 0)
        fun width(icon: Float, count: Int) = (0 until count).sumOf { slotDp(icon, digits[it]).toDouble() }.toFloat() + GAP_DP * (count - 1).coerceAtLeast(0)
        var icon = MAX_ICON_DP
        while (icon >= MIN_ICON_DP) {
            if (width(icon, digits.size) <= widthDp) return Fit(icon, digits.size, 0)
            icon -= 1f
        }
        // Not even the smallest all fit: as many as leave room for the "+N" after them, and at least one.
        var shown = digits.size - 1
        while (shown > 1 && width(MIN_ICON_DP, shown) + GAP_DP + moreDp(MIN_ICON_DP, digits.size - shown) > widthDp) shown--
        return Fit(MIN_ICON_DP, shown.coerceAtLeast(1), digits.size - shown.coerceAtLeast(1))
    }
}
