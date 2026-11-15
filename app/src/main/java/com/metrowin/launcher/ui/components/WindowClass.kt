package com.metrowin.launcher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp

/**
 * The one place that decides what "wide" means, for the launcher and every mini app.
 * Compact: a phone. Medium: a small tablet or a folding phone opened. Expanded: a big tablet or a free window.
 * Same limits as Android's window size classes.
 */
enum class WindowClass {
    Compact, Medium, Expanded;

    /** Two columns (the lists at the left) from here. */
    val isWide: Boolean get() = this != Compact

    companion object {
        const val MEDIUM_DP = 600
        const val EXPANDED_DP = 840

        fun of(widthDp: Float): WindowClass = when {
            widthDp >= EXPANDED_DP -> Expanded
            widthDp >= MEDIUM_DP -> Medium
            else -> Compact
        }
    }
}

/** The class of the window the screen is in (not of the device: it follows split screen and free windows). */
@Composable
fun windowClass(): WindowClass = WindowClass.of(LocalConfiguration.current.screenWidthDp.toFloat())

@Composable
fun isWide(): Boolean = windowClass().isWide

/** For screens that measure their own box (`maxWidth`) instead of the window. */
val Dp.windowClass: WindowClass get() = WindowClass.of(value)
