package com.metrowin.launcher.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The gaps of the launcher: use these in the common components instead of a bare dp, so that a page is spaced the same everywhere. */
object Spacing {
    val Small = 4.dp
    val Medium = 8.dp
    val Large = 12.dp
    /** The margin at the side of a page; [pageSide] scales it by the setting. */
    val Page = 24.dp
}

/** The sizes of text, by what the text is: a screen picks a role, not a number. A size not here is an exception that has a reason. */
object MetroType {
    val Micro = 11.sp
    val Caption = 12.sp
    val Note = 14.sp
    val Body = 16.sp
    val Lead = 18.sp
    val Subtitle = 20.sp
    val Heading = 24.sp
    val Title = 34.sp
    /** The titles of a pivot. */
    val Pivot = 44.sp
    /** The page titles of a pivot in the Classic look (the others keep [Pivot]): 56 sp, in lower case. */
    val PivotClassic = 56.sp
    /** The name of the app over the pivot: 14 sp in capitals with the letters spaced. Classic only. */
    val AppTitleClassic = 14.sp
    /** Titles of the pivot that are not the current one: 0.4 of the ink (`PhonePivotUnselectedItemOpacity`; the light theme's 0.23 would not read). */
    const val PivotIdleAlpha = 0.4f
}
