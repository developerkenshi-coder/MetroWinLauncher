package com.metrowin.launcher.ui.anim

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope

/** True while the launcher spares the battery (see PowerPolicy): what moves by itself stands still. */
val LocalPowerSaving = staticCompositionLocalOf { false }

/**
 * Runs [block] only while the launcher is on screen. With another app in front or the screen off the block is
 * cancelled, so its timers stop and the phone can sleep; when the launcher comes back it starts again from the top
 * (and again when [key] changes). A clock that redraws every minute, a tile that flips every few seconds: they go here.
 */
@Composable
fun LaunchedWhileStarted(key: Any?, block: suspend CoroutineScope.() -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(key, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED, block) }
}
