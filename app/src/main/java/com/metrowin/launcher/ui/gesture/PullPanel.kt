package com.metrowin.launcher.ui.gesture

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** What happens when a finger lets go of a panel that was pulled down from the top. Pure, so it can be tested. */
object PullRules {
    /** Pulled or pushed this far (dp), the panel carries on by itself to the side the finger was heading. */
    const val COMMIT_DP = 110f

    /** A flick faster than this (dp per second) decides on its own, whatever the distance. */
    const val FLING_DP_PER_SECOND = 350f

    /**
     * Whether the panel ends open. [progress] is how far down it is (0..1) and [travelDp] how long its way is.
     * [velocityDpPerSecond] is positive when the finger moves down. A panel that was open when grabbed stays
     * open unless the finger pushed it up past [COMMIT_DP]; one that was closed opens once pulled past it.
     */
    fun shouldOpen(progress: Float, travelDp: Float, velocityDpPerSecond: Float, startedOpen: Boolean): Boolean = when {
        velocityDpPerSecond > FLING_DP_PER_SECOND -> true
        velocityDpPerSecond < -FLING_DP_PER_SECOND -> false
        startedOpen -> (1f - progress) * travelDp < COMMIT_DP
        else -> progress * travelDp > COMMIT_DP
    }
}

/**
 * A panel pulled down from the top of the screen, as the Action Center is: [progress] is 0 when hidden and
 * 1 when fully down. The finger moves it one to one ([dragBy]); letting go ([release]) hands it to a
 * spring that carries the finger's speed on. Grabbing it while it moves stops the spring at once, so a
 * panel can always be caught, pushed back or pulled again.
 *
 * [progress] is a single state that only drawing code should read (inside `graphicsLayer { }`), so a
 * pull never recomposes anything; [isShown] changes only when the panel appears or disappears.
 */
@Stable
class PullPanel(
    private val scope: CoroutineScope,
    /** Pixels the finger travels for the panel to go from 0 to 1: the height of the panel. */
    var travelPx: Float,
    private val density: Float,
    initialProgress: Float = 0f,
) {
    var progress by mutableFloatStateOf(initialProgress)
        private set

    val isShown: Boolean by derivedStateOf { progress > 0f }

    private var animation: Job? = null
    private var headingOpen = false
    private var startedOpen: Boolean? = null

    /** Moves the panel by [fraction] of its way (positive pulls down) and returns how much of that it used. */
    fun dragBy(fraction: Float): Float {
        if (startedOpen == null) {
            startedOpen = progress >= 1f || (animation?.isActive == true && headingOpen)
        }
        animation?.cancel()
        val target = (progress + fraction).coerceIn(0f, 1f)
        val used = target - progress
        progress = target
        return used
    }

    /** Same as [dragBy] in pixels; returns the pixels used. */
    fun dragByPx(pixels: Float): Float = dragBy(pixels / travelPx) * travelPx

    /** Follows something other than a finger on the panel, such as the back gesture. */
    fun follow(value: Float) {
        animation?.cancel()
        progress = value.coerceIn(0f, 1f)
    }

    /** The finger let go moving [velocityPxPerSecond] (positive is down). The panel settles open or closed. */
    fun release(velocityPxPerSecond: Float) {
        val open = PullRules.shouldOpen(progress, travelPx / density, velocityPxPerSecond / density, startedOpen ?: (progress >= 1f))
        startedOpen = null
        settle(if (open) 1f else 0f, velocityPxPerSecond / travelPx)
    }

    fun open() = settle(1f, 0f)

    fun close() = settle(0f, 0f)

    /** Glides to [target] (0..1) from wherever the panel is. */
    fun animateTo(target: Float) = settle(target.coerceIn(0f, 1f), 0f)

    /** Jumps, without animation. */
    fun snapTo(value: Float) {
        animation?.cancel()
        startedOpen = null
        progress = value.coerceIn(0f, 1f)
    }

    private fun settle(target: Float, velocity: Float) {
        animation?.cancel()
        headingOpen = target >= 1f
        animation = scope.launch {
            animate(progress, target, velocity, Settle) { value, _ -> progress = value.coerceIn(0f, 1f) }
            progress = target
        }
    }

    private companion object {
        /**
         * No bounce: a panel that overshoots its own edge looks loose. The threshold is about a pixel, so when
         * the spring is declared finished and the panel is put exactly in place, nothing visibly jumps.
         */
        val Settle = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 900f, visibilityThreshold = 0.0005f)
    }
}

@Composable
fun rememberPullPanel(initialProgress: Float = 0f): PullPanel {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height.toFloat()
    // The panel is as tall as the window, which the screen reports again with its real size once laid out.
    return remember(scope, density.density) {
        PullPanel(scope, travelPx = windowHeight, density = density.density, initialProgress = initialProgress)
    }
}
