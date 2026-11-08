package com.metrowin.launcher.ui.start

import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.anim.FlipContainer
import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
import com.metrowin.launcher.ui.anim.LocalPowerSaving
import com.metrowin.launcher.ui.anim.metroTween
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext






/** Windows Phone's Cycle template: photos fading into one another, one after the other. */
@Composable
internal fun CycleFace(item: AppItem, photos: List<ImageBitmap>, seed: Int) {
    var index by remember { mutableIntStateOf(0) }
    val reduced = LocalReduceMotion.current
    val saving = LocalPowerSaving.current
    LaunchedWhileStarted(photos.size to (reduced || saving)) {
        // With reduced motion, or while saving battery, the tile keeps its photo.
        if (photos.size < 2 || reduced || saving) return@LaunchedWhileStarted
        // Tiles start at different moments, so the whole screen does not change at once.
        delay(1_000L + seed.mod(5) * 700L)
        while (true) {
            delay(CYCLE_MS)
            index = (index + 1) % photos.size
        }
    }
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = index % photos.size, animationSpec = metroTween(durationMillis = 900), label = "cycle") { shown ->
            Image(bitmap = photos[shown], contentDescription = null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low, modifier = Modifier.fillMaxSize())
        }
        PhotoLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}


internal const val CYCLE_MS = 4_500L


/** The tile of a contact: the person's photo over the whole tile, the name on it. Until the photo is read, the glyph. */
@Composable
internal fun PhotoShortcutFace(item: AppItem, size: TileSize, width: Dp, height: Dp) {
    val path = item.photoPath
    val photo by produceState<ImageBitmap?>(null, path) {
        value = path?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    }
    val bitmap = photo
    if (bitmap == null) {
        StaticFace(item, size, width, height)
    } else {
        Box(Modifier.fillMaxSize()) {
            Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low, modifier = Modifier.fillMaxSize())
            if (size != TileSize.Small) PhotoLabel(item.label, Modifier.align(Alignment.BottomStart))
        }
    }
}


@Composable
internal fun PhotosFace(item: AppItem, size: TileSize, width: Dp, height: Dp, live: LiveTileData, seed: Int) {
    val photos = live.photos
    FlipContainer(if (size == TileSize.Small) 1 else 1 + photos.size, seed) { face ->
        if (face == 0 || photos.isEmpty()) {
            StaticFace(item, size, width, height)
        } else {
            Box(Modifier.fillMaxSize()) {
                Image(
                    bitmap = photos[(face - 1) % photos.size],
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize(),
                )
                PhotoLabel(item.label, Modifier.align(Alignment.BottomStart))
            }
        }
    }
}


@Composable
internal fun PeopleFace(item: AppItem, size: TileSize, width: Dp, height: Dp, live: LiveTileData, seed: Int) {
    val people = live.people
    FlipContainer(if (size == TileSize.Small) 1 else 1 + people.size, seed) { face ->
        if (face == 0 || people.isEmpty()) {
            StaticFace(item, size, width, height)
        } else {
            val person = people[(face - 1) % people.size]
            Box(Modifier.fillMaxSize()) {
                Image(
                    bitmap = person.photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize(),
                )
                PhotoLabel(person.name, Modifier.align(Alignment.BottomStart))
            }
        }
    }
}


/** Name over a photo: a soft shade at the bottom keeps it readable on any picture. */
@Composable
internal fun PhotoLabel(text: String, modifier: Modifier = Modifier) {
    if (!LocalTileNames.current) return
    Box(
        modifier.background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))),
    ) {
        TileLabel(text, Modifier.padding(top = 18.dp))
    }
}
