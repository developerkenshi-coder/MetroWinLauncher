package com.metrowin.launcher.ui.anim

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.clipToBounds
import com.metrowin.launcher.data.TileTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.LocalStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Easing and angles shared by every Metro animation. */
object Motion {
    /** Fast start and a long, soft stop: how Windows Phone content settles into place. */
    val Decelerate = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)
    val Accelerate = CubicBezierEasing(0.7f, 0f, 0.9f, 0.1f)

    /** Rotation of an element that is fully "turned away" in the turnstile transition. */
    const val TURNSTILE_DEGREES = 80f

    /** Camera distance, in multiples of the screen density, so rotated elements keep a natural perspective. */
    const val CAMERA = 14f
}

/**
 * The turnstile used when Start and the app list come in and go out: the element swings around its
 * left edge like a turnstile arm. [away] is 0 when it faces the screen and 1 when it is turned away.
 */
fun GraphicsLayerScope.turnstile(away: Float, reduced: Boolean = false) {
    if (reduced) {
        // Reduced motion: no swing, the element just fades.
        alpha = 1f - away
        return
    }
    transformOrigin = TransformOrigin(0f, 0.5f)
    rotationY = Motion.TURNSTILE_DEGREES * away
    cameraDistance = Motion.CAMERA * density
    alpha = 1f - away * away * away
}

/**
 * Windows Phone press feedback: the element sinks and tilts away from the finger, then springs back.
 * Tapping near the right edge pushes the right edge down, and so on.
 */
@Composable
fun Modifier.metroPress(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    maxTiltDegrees: Float = 9f,
    pressedScale: Float = 0.95f,
    /** What TalkBack calls the long press ("More options"). */
    longClickLabel: String? = null,
): Modifier {
    val scope = rememberCoroutineScope()
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val currentClick by rememberUpdatedState(onClick)
    val currentLongClick by rememberUpdatedState(onLongClick)
    // The click and the vibration of a tap, if the settings ask for them: every button, tile and row that can be touched has them.
    val currentFeedback by rememberUpdatedState(LocalTapFeedback.current)
    val hasLongClick = onLongClick != null
    val reduced = LocalReduceMotion.current
    // The tilt is the Windows Phone press: the other looks only sink a little.
    val tilt = if (reduced || !LocalStyle.current.isClassic) 0f else maxTiltDegrees

    return this
        // What TalkBack and the other accessibility services see: one button that says its text and can be activated.
        .semantics(mergeDescendants = true) {
            role = Role.Button
            if (enabled) {
                onClick(label = null) {
                    currentClick()
                    true
                }
                if (hasLongClick) {
                    onLongClick(label = longClickLabel) {
                        currentLongClick?.invoke()
                        true
                    }
                }
            } else {
                disabled()
            }
        }
        .graphicsLayer {
            rotationX = tiltX.value
            rotationY = tiltY.value
            scaleX = scale.value
            scaleY = scale.value
            cameraDistance = Motion.CAMERA * density
        }
        .pointerInput(enabled, hasLongClick, tilt) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = { point ->
                    val dx = (point.x / size.width * 2f - 1f).coerceIn(-1f, 1f)
                    val dy = (point.y / size.height * 2f - 1f).coerceIn(-1f, 1f)
                    scope.launch { tiltY.animateTo(TILT_Y_SIGN * dx * tilt, PressSpring) }
                    scope.launch { tiltX.animateTo(TILT_X_SIGN * dy * tilt, PressSpring) }
                    scope.launch { scale.animateTo(pressedScale, PressSpring) }
                    tryAwaitRelease()
                    scope.launch { tiltY.animateTo(0f, ReleaseSpring) }
                    scope.launch { tiltX.animateTo(0f, ReleaseSpring) }
                    scope.launch { scale.animateTo(1f, ReleaseSpring) }
                },
                onTap = {
                    currentFeedback()
                    currentClick()
                },
                onLongPress = if (hasLongClick) ({ currentLongClick?.invoke() }) else null,
            )
        }
}

// In Compose a positive rotationY brings the left edge towards the viewer (the right edge recedes) and
// a positive rotationX brings the bottom edge towards the viewer (the top edge recedes). The finger
// pushes the touched edge away, hence these signs. ScreensTest.pressOnRightEdge renders it.
private const val TILT_Y_SIGN = 1f
private const val TILT_X_SIGN = -1f

private val PressSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
private val ReleaseSpring = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)

/** How the faces of a live tile follow one another (see [TileTransition]); the same for every tile of Start. */
val LocalTileTransition = staticCompositionLocalOf { TileTransition.Flip }

/**
 * The faces of a live tile, one after the other: the tile turns over (the face rotates away around its horizontal axis and the
 * next one swings in from behind) or, in the slide transition, the face goes up and the next one comes in from below. Tiles
 * change at different times, as on the phone, so Start never moves all at once. Nothing moves while the launcher is not on
 * screen, with reduced motion, or while the battery is spared.
 */
@Composable
fun FlipContainer(
    faces: Int,
    seed: Int,
    modifier: Modifier = Modifier,
    content: @Composable (face: Int) -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    // 0 to 180: how far the change has gone, whichever way the tile changes.
    val angle = remember { Animatable(0f) }
    val showingNext by remember { derivedStateOf { angle.value >= 90f } }
    val moving by remember { derivedStateOf { angle.value > 0f } }
    val slide = when (LocalTileTransition.current) {
        TileTransition.Flip -> false
        TileTransition.Slide -> true
        // Each tile keeps to one way: half of Start turns over, the other half slides.
        TileTransition.Mixed -> seed.mod(2) == 1
    }

    val reduced = LocalReduceMotion.current
    val saving = LocalPowerSaving.current
    LaunchedWhileStarted(Triple(faces, reduced || saving, slide)) {
        // With reduced motion, or while saving battery, the tile keeps its face: nothing flips by itself.
        if (faces <= 1 || reduced || saving) return@LaunchedWhileStarted
        // Coming back after being off screen in the middle of a change: settle on the face that was coming in.
        if (angle.value >= 90f) index = (index + 1) % faces
        angle.snapTo(0f)
        delay(1_500L + (seed.mod(6)) * 800L)
        while (true) {
            angle.animateTo(180f, if (slide) tween(durationMillis = 600, easing = Motion.Decelerate) else tween(durationMillis = 700, easing = FastOutSlowInEasing))
            index = (index + 1) % faces
            angle.snapTo(0f)
            delay(5_000L + (seed.mod(4)) * 900L)
        }
    }

    if (slide && faces > 1) {
        Box(modifier.clipToBounds()) {
            Box(Modifier.graphicsLayer { translationY = -size.height * angle.value / 180f }) { content(index % faces) }
            // The next face is only there while the change goes on.
            if (moving) Box(Modifier.graphicsLayer { translationY = size.height * (1f - angle.value / 180f) }) { content((index + 1) % faces) }
        }
        return
    }
    val visible = if (faces <= 1) 0 else (index + if (showingNext) 1 else 0) % faces
    Box(
        modifier.graphicsLayer {
            val a = angle.value
            // 0..90 the current face turns away, 90..180 the next one comes in from the other side.
            rotationX = FLIP_SIGN * (if (a < 90f) a else a - 180f)
            cameraDistance = Motion.CAMERA * density
        },
    ) {
        content(visible)
    }
}

private const val FLIP_SIGN = 1f
