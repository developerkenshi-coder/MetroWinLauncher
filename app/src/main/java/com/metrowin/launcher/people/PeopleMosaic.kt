package com.metrowin.launcher.people

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
import com.metrowin.launcher.ui.anim.LocalPowerSaving
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.start.rememberTouchExploration
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay

private const val COLUMNS = 3
internal const val MOSAIC_SLOTS = 6

/** Who is on the mosaic: only people with a photo, each once: favourites first, then the latest callers, then the rest. */
internal fun mosaicPool(people: List<Person>, favourites: Set<String>, callers: List<Person>, max: Int = 18): List<Person> {
    val order = people.filter { it.starred || it.key in favourites } + callers + people
    return order.filter { it.photoUri != null }.distinctBy { it.key }.take(max)
}

/** The pool index that goes on a tile next: the first one at or after [cursor] (round the pool) that is not on show. [shown] must be smaller than [poolSize]. */
internal fun nextFace(shown: List<Int>, poolSize: Int, cursor: Int): Int =
    (0 until poolSize).map { (cursor + it) % poolSize }.first { it !in shown }

/**
 * The mosaic of the People hub: six tiles with the photos of the people that matter, and when there are more people than tiles the tiles
 * turn into another face one after the other, in a wave, then rest. It stands still with reduced motion, while saving battery, with
 * TalkBack on, and with the launcher out of sight. A touch on a tile opens the person. Photos are read before they show, off the main thread.
 */
@Composable
internal fun PeopleMosaic(pool: List<Person>, onOpen: (Person) -> Unit, modifier: Modifier = Modifier) {
    val slots = minOf(MOSAIC_SLOTS, pool.size)
    val shown = remember(pool) { mutableStateListOf<Int>().apply { addAll(0 until slots) } }
    val still = LocalReduceMotion.current || LocalPowerSaving.current || rememberTouchExploration()
    val context = LocalContext.current.applicationContext
    if (pool.size > slots && !still) LaunchedWhileStarted(pool) {
        var cursor = slots
        val wave = listOf(0, 3, 1, 4, 2, 5)
        while (true) {
            delay(4_000)
            for (slot in wave) {
                if (slot >= slots) continue
                val next = nextFace(shown, pool.size, cursor)
                loadPhoto(context, pool[next].photoUri.orEmpty())
                shown[slot] = next
                cursor = next + 1
                delay(260)
            }
        }
    }
    Column(modifier.fillMaxWidth().testTag("people:mosaic"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (0 until slots).chunked(COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { slot ->
                    Box(Modifier.weight(1f)) {
                        AnimatedContent(
                            shown[slot],
                            transitionSpec = { (fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }) togetherWith (fadeOut(tween(300)) + slideOutVertically(tween(300)) { -it / 4 }) },
                            label = "mosaic",
                        ) { index ->
                            val p = pool[index.coerceIn(pool.indices)]
                            PhotoTile(p.name, rememberPhoto(p.photoUri), Modifier.metroPress(onClick = { onOpen(p) }).semantics { contentDescription = p.name }.testTag("people:mosaic:tile"))
                        }
                    }
                }
                repeat(COLUMNS - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
