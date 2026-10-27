package com.metrowin.launcher.ui.gesture

import com.metrowin.launcher.ui.anim.metroTween
import android.annotation.SuppressLint
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import com.metrowin.launcher.ui.anim.Motion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * What a back gesture does while the finger is on the screen edge and when it ends. The system swipe
 * reports its progress, so the screen can follow the finger and undo itself if the gesture is cancelled.
 */
interface BackDriver {
    /** The swipe is [progress] (0..1) of the way, coming from the right edge if [fromRight]. */
    suspend fun onProgress(progress: Float, fromRight: Boolean) {}

    /** The gesture was completed (or back was pressed on a three-button bar, which reports no progress). */
    fun onCommit()

    /** The finger went back and let go: put everything back. */
    fun onCancel() {}
}

/**
 * Runs one back gesture. The system may cancel the collection, and then it also cancels the coroutine we
 * run in, so everything that has to go on afterwards is started by the driver in its own scope.
 */
suspend fun runBackGesture(events: Flow<BackEventCompat>, driver: BackDriver) {
    try {
        events.collect { driver.onProgress(it.progress, it.swipeEdge == BackEventCompat.EDGE_RIGHT) }
    } catch (cancelled: CancellationException) {
        driver.onCancel()
        throw cancelled
    }
    driver.onCommit()
}

/**
 * Handles every back gesture with the driver [driver] picks when the gesture starts, so the layer on top at that
 * moment is the one that follows the finger.
 */
@SuppressLint("NoCollectCallFound") // runBackGesture collects the progress flow; the check cannot see through it
@Composable
fun BackGestureHandler(driver: () -> BackDriver) {
    PredictiveBackHandler { events -> runBackGesture(events, driver()) }
}

/** Back that has nothing to show along the way. */
class InstantBack(private val action: () -> Unit) : BackDriver {
    override fun onCommit() = action()
}

/** The Action Center follows the finger up, and is put away or brought back. */
class PanelBack(private val panel: PullPanel) : BackDriver {
    private val from = panel.progress

    override suspend fun onProgress(progress: Float, fromRight: Boolean) = panel.follow(from * (1f - progress))

    override fun onCommit() = panel.close()

    override fun onCancel() = panel.animateTo(from)
}

/**
 * A page that swings away with the finger (the settings). It is left to [commit] to take it away for
 * good; the page then carries on from where the finger left it.
 */
class TurnstileBack(
    private val away: Animatable<Float, AnimationVector1D>,
    private val scope: CoroutineScope,
    private val commit: () -> Unit,
) : BackDriver {
    override suspend fun onProgress(progress: Float, fromRight: Boolean) {
        away.snapTo(progress)
    }

    override fun onCommit() = commit()

    override fun onCancel() {
        scope.launch { away.animateTo(0f, metroTween(durationMillis = 200, easing = Motion.Decelerate)) }
    }
}

/**
 * The pager pans to [toPage] under the finger. With [onlyFromRight] it is a swipe from the right edge on
 * Start that pans forward to the app list: the system keeps that edge for Back, so it is the only way
 * for a finger that starts right at the edge to turn the page. Back on a three-button bar does nothing there.
 */
class PagerBack(
    private val state: PagerState,
    private val scope: CoroutineScope,
    private val toPage: Int,
    private val onlyFromRight: Boolean = false,
) : BackDriver {
    private val fromPage = state.currentPage
    private var last = 0f
    private var moved = false

    override suspend fun onProgress(progress: Float, fromRight: Boolean) {
        if (onlyFromRight && !fromRight) return
        moved = true
        val pageSize = state.layoutInfo.pageSize + state.layoutInfo.pageSpacing
        val direction = if (toPage > fromPage) 1f else -1f
        state.dispatchRawDelta(direction * (progress - last) * pageSize)
        last = progress
    }

    override fun onCommit() {
        if (onlyFromRight && !moved) return
        scope.launch { state.animateScrollToPage(toPage) }
    }

    override fun onCancel() {
        if (moved) scope.launch { state.animateScrollToPage(fromPage) }
    }
}

/**
 * On a Start screen the system keeps the edges of the screen for Back, so a swipe that starts there turns the
 * page instead: from the right edge on to the next page, from the left edge back to the previous one. Nothing
 * happens when there is no such page, and Back on a three-button bar (no progress, no edge) does nothing.
 */
class EdgePageBack(
    private val state: PagerState,
    private val scope: CoroutineScope,
    private val pageCount: Int,
) : BackDriver {
    private val fromPage = state.currentPage
    private var target: Int? = null
    private var decided = false
    private var last = 0f

    override suspend fun onProgress(progress: Float, fromRight: Boolean) {
        if (!decided) {
            decided = true
            target = (if (fromRight) fromPage + 1 else fromPage - 1).takeIf { it in 0 until pageCount }
        }
        val to = target ?: return
        val pageSize = state.layoutInfo.pageSize + state.layoutInfo.pageSpacing
        val direction = if (to > fromPage) 1f else -1f
        state.dispatchRawDelta(direction * (progress - last) * pageSize)
        last = progress
    }

    override fun onCommit() {
        val to = target ?: return
        scope.launch { state.animateScrollToPage(to) }
    }

    override fun onCancel() {
        if (target != null) scope.launch { state.animateScrollToPage(fromPage) }
    }
}
