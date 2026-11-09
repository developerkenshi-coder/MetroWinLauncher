package com.metrowin.launcher.photos

import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.lazy.grid.itemsIndexed
import com.metrowin.launcher.ui.components.featherRow
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.components.arrival
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.HubPager
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val SIDE = 24.dp
private val GAP = 4.dp

/**
 * The Photo hub: the title, a pivot with three pages (albums, date, favourites) and a grid of small pictures on each. [photos] is null while they
 * are being read; with [needsPermission] the page asks for the access instead. No editing, no deleting: a tap opens the picture in the viewer, which grows out of the small picture ([onOpen] gets where that was, in window pixels).
 */
@Composable
fun PhotosScreen(photos: List<Photo>?, needsPermission: Boolean, onAllow: () -> Unit, onOpen: (Photo, Rect) -> Unit) {
    val pager = rememberPagerState { 3 }
    // The open album is kept here, not in the page: the pager drops the page that is swiped away.
    var album by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = album != null) { album = null }

    MiniFrame {
        HubPager(
            stringResource(R.string.stock_photohub),
            listOf(stringResource(R.string.photos_albums), stringResource(R.string.photos_date), stringResource(R.string.photos_favorites)), pager,
            userScrollEnabled = !needsPermission && photos != null, titleTag = "photos:title", pagerTag = "photos:pager",
        ) { page ->
            when {
                needsPermission -> if (page == 0) Column(Modifier.fillMaxWidth().padding(horizontal = SIDE).testTag("photos:permission"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetroText(stringResource(R.string.photos_allow_why), size = 18.sp, color = LocalMetroColors.current.subtle)
                    MetroButton(stringResource(R.string.photos_allow), Modifier.testTag("photos:allow"), onClick = onAllow)
                }
                photos == null -> Box(Modifier.fillMaxSize().testTag("photos:loading"))
                else -> when (page) {
                    0 -> AlbumsPage(photos, album, onAlbum = { album = it }, onOpen)
                    1 -> DatePage(photos, onOpen)
                    else -> PhotoGrid(PhotoLibrary.favorites(photos), onOpen, "photos:favorites")
                }
            }
        }
    }
}

@Composable
private fun Placeholder() = Box(Modifier.fillMaxSize().background(LocalAccent.current.copy(alpha = if (LocalStyle.current.isClassic) 1f else 0.35f)))

@Composable
private fun Thumb(photo: Photo, modifier: Modifier = Modifier) {
    val bitmap = PhotoThumbs.load(photo.id)
    // A thumbnail that was not ready when the cell came up fades in over the colour of the accent; one from the cache is just there.
    val cached = remember { bitmap != null }
    Box(modifier.aspectRatio(1f).clipToBounds()) {
        if (bitmap == null) Placeholder() else {
            Placeholder()
            Image(bitmap, null, Modifier.fillMaxSize().arrival(instant = cached), contentScale = ContentScale.Crop)
        }
    }
}

private fun day(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

@Composable
private fun Empty(tag: String) = MetroText(stringResource(R.string.photos_empty), size = 18.sp, color = LocalMetroColors.current.subtle, modifier = Modifier.padding(horizontal = SIDE).testTag(tag))

@Composable
private fun PhotoGrid(photos: List<Photo>, onOpen: (Photo, Rect) -> Unit, tag: String) {
    if (photos.isEmpty()) return Empty("$tag:empty")
    LazyVerticalGrid(
        GridCells.Adaptive(96.dp), Modifier.fillMaxSize().testTag(tag), contentPadding = PaddingValues(start = SIDE, end = SIDE, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(GAP), verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        itemsIndexed(photos, key = { _, it -> it.id }) { index, photo -> PhotoCell(photo, onOpen, Modifier.featherRow(index)) }
    }
}

@Composable
private fun PhotoCell(photo: Photo, onOpen: (Photo, Rect) -> Unit, modifier: Modifier = Modifier) {
    val description = day(java.time.Instant.ofEpochMilli(photo.takenAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Thumb(
        photo,
        modifier.testTag("photo:${photo.id}").semantics { contentDescription = description }.onGloballyPositioned { bounds = it.boundsInWindow() }.metroPress(onClick = { onOpen(photo, bounds) }),
    )
}

@Composable
private fun AlbumsPage(photos: List<Photo>, open: String?, onAlbum: (String?) -> Unit, onOpen: (Photo, Rect) -> Unit) {
    val albums = PhotoLibrary.albums(photos)
    val inside = albums.firstOrNull { it.first == open }
    if (inside != null) return PhotoGrid(inside.second, onOpen, "photos:album")
    if (albums.isEmpty()) return Empty("photos:albums:empty")
    val other = stringResource(R.string.photos_other)
    LazyVerticalGrid(
        GridCells.Adaptive(150.dp), Modifier.fillMaxSize().testTag("photos:albums"), contentPadding = PaddingValues(start = SIDE, end = SIDE, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(albums, key = { it.first }) { (name, list) ->
            Column(Modifier.metroPress(onClick = { onAlbum(name) }).testTag("album:$name")) {
                Thumb(list.first())
                MetroText(name.ifEmpty { other }, size = 18.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                MetroText(list.size.toString(), size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DatePage(photos: List<Photo>, onOpen: (Photo, Rect) -> Unit) {
    val days = remember(photos) { PhotoLibrary.byDay(photos) }
    if (days.isEmpty()) return Empty("photos:date:empty")
    LazyVerticalGrid(
        GridCells.Adaptive(96.dp), Modifier.fillMaxSize().testTag("photos:date"), contentPadding = PaddingValues(start = SIDE, end = SIDE, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(GAP), verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        days.forEach { (date, list) ->
            item(key = "day:$date", span = { GridItemSpan(maxLineSpan) }) {
                MetroText(day(date), size = 20.sp, color = LocalAccent.current, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).testTag("day:$date"))
            }
            items(list, key = { it.id }) { photo -> PhotoCell(photo, onOpen) }
        }
    }
}
