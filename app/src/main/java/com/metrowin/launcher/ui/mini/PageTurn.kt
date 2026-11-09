package com.metrowin.launcher.ui.mini

import androidx.compose.animation.core.Animatable
import android.view.Choreographer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.metrowin.launcher.data.LaunchPlans
import com.metrowin.launcher.ui.anim.Motion
import com.metrowin.launcher.ui.anim.MotionStyle
import com.metrowin.launcher.ui.anim.metroTween
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private val choreographerClock = object : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = suspendCancellableCoroutine { cont ->
        val callback = Choreographer.FrameCallback { cont.resumeWith(runCatching { onFrame(it) }) }
        Choreographer.getInstance().postFrameCallback(callback)
        cont.invokeOnCancellation { Choreographer.getInstance().removeFrameCallback(callback) }
    }
}

/**
 * The turnstile (the original Windows Phone one or the modern one, by [MotionStyle]) for an activity of the launcher itself (the mini apps, the mail, the messages, the dialer): the
 * page swings in from the right edge when the launcher opens it and swings out again before it closes. It only happens when the launcher
 * asked for it ([EXTRA]); an app opened by someone else, or with the modern animation, takes the window animation of Android.
 */
class PageTurn(private val on: Boolean) {
    /** 1 turned away, 0 facing the screen. */
    val away = Animatable(if (on) 1f else 0f)
    private var leaving = false

    suspend fun enter() {
        if (!on) return
        if (MotionStyle.original) away.animateTo(0f, metroTween(LaunchPlans.ORIGINAL_IN_MS, easing = Motion.ExpoOut))
        else away.animateTo(0f, metroTween(Motion.TURNSTILE_IN_MS, easing = Motion.TurnstileIn))
    }

    /** Turns the page away and then calls [done]. False (and [done] not called) when there is no turn, or it is already going. */
    fun leave(scope: CoroutineScope, done: () -> Unit): Boolean {
        if (!on || leaving) return false
        leaving = true
        // The scope of an activity has no frame clock of Compose: without this the animation throws.
        scope.launch(choreographerClock) {
            if (MotionStyle.original) away.animateTo(1f, metroTween(LaunchPlans.ORIGINAL_OUT_MS, easing = Motion.ExpoIn))
            else away.animateTo(1f, metroTween(Motion.TURNSTILE_OUT_MS, easing = Motion.TurnstileOut))
            done()
        }
        return true
    }

    companion object {
        const val EXTRA = "metrowin.pageTurn"
    }
}

/** The page of an activity of the launcher, turning in when it opens and out when [PageTurn.leave] says so. */
@Composable
fun TurnPage(turn: PageTurn, content: @Composable () -> Unit) {
    LaunchedEffect(turn) { turn.enter() }
    Box(
        Modifier.graphicsLayer {
            val a = turn.away.value
            cameraDistance = Motion.CAMERA * density
            if (MotionStyle.original) {
                transformOrigin = TransformOrigin(-0.1f, 0.5f)
                rotationY = -Motion.ORIGINAL_PAGE_DEGREES * a
                alpha = if (a >= 0.999f) 0f else 1f
            } else {
                // The modern turnstile: the page swings around its left edge and fades as it turns away.
                transformOrigin = TransformOrigin(0f, 0.5f)
                rotationY = -Motion.TURNSTILE_DEGREES * a
                alpha = 1f - a * a * a
            }
        },
    ) { content() }
}

/**
 * When the screen that ends is the last of its task (the launcher was closed by Android while it was open), Android has to find the Home
 * app again and may ask which one to use. Opening the launcher by its name never asks. Only for a screen the launcher opened
 * ([openedByLauncher]): one opened by another app goes back to that app.
 */
internal fun android.app.Activity.returnToLauncher(openedByLauncher: Boolean = intent?.hasExtra(PageTurn.EXTRA) == true) {
    if (!openedByLauncher || !isTaskRoot) return
    runCatching {
        startActivity(android.content.Intent(this, com.metrowin.launcher.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
}
