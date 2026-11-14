package com.metrowin.launcher.photos

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.HubPager
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.theme.LocalMetroColors
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val SIDE = 24.dp

/** What the hub asks of the activity: the things that need the system (the share sheet, the dialogs that delete and mark). Do nothing by default, for the tests. */
class PhotoActions(
    val share: (List<Photo>) -> Unit = {},
    val delete: (List<Photo>) -> Unit = {},
    val favorite: (List<Photo>, Boolean) -> Unit = { _, _ -> },
    /** Reads the next page of pictures; null when there is no more. */
    val loadMore: (() -> Unit)? = null,
    val partial: Boolean = false,
    val selectMore: () -> Unit = {},
)

@Composable
fun rememberPhotoPrefs(): PhotoPrefs {
    val context = LocalContext.current
    return remember { PhotoPrefs(context) }
}

private enum class Page { Albums, Date, Favorites, Memories, Duplicates }

private fun monthName(month: Int): String = java.time.Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())

/**
 * The Photo hub: the title, a pivot with the pages (albums, date, favourites, and when there are some memories and duplicates) and a grid of
 * small pictures on each. [photos] is null while they are being read; with [needsPermission] the page asks for the access instead. A tap
 * opens the picture in the viewer, which grows out of the small picture ([onOpen] gets where that was, in window pixels); a long press picks
 * pictures to share, mark or delete ([actions]: the activity does it, through the dialogs of Android).
 */
@Composable
fun PhotosScreen(
    photos: List<Photo>?,
    needsPermission: Boolean,
    onAllow: () -> Unit,
    onOpen: (Photo, Rect) -> Unit,
    prefs: PhotoPrefs = rememberPhotoPrefs(),
    actions: PhotoActions = PhotoActions(),
    today: LocalDate = LocalDate.now(),
) {
    // The open album is kept here, not in the page: the pager drops the page that is swiped away.
    var album by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var options by rememberSaveable { mutableStateOf(false) }
    val selection = remember { Selection() }
    var comparing by remember { mutableStateOf<Pair<Photo, Photo>?>(null) }
    BackHandler(enabled = album != null) { album = null }
    BackHandler(enabled = searching) { searching = false; query = "" }
    BackHandler(enabled = selection.mode) { selection.clear() }

    val shown = remember(photos, query) { photos?.let { PhotoLibrary.search(it, query.trim(), ::monthName) } }
    val memories = remember(shown, today, prefs[PhotoOption.Memories]) { if (prefs[PhotoOption.Memories]) shown.orEmpty().let { PhotoLibrary.memories(it, today) } else emptyList() }
    val duplicates = remember(shown, prefs[PhotoOption.Duplicates]) { if (prefs[PhotoOption.Duplicates]) shown.orEmpty().let(PhotoLibrary::duplicates) else emptyList() }
    val pages = buildList { add(Page.Albums); add(Page.Date); add(Page.Favorites); if (memories.isNotEmpty()) add(Page.Memories); if (duplicates.isNotEmpty()) add(Page.Duplicates) }
    val titles = pages.map {
        when (it) {
            Page.Albums -> stringResource(R.string.photos_albums); Page.Date -> stringResource(R.string.photos_date); Page.Favorites -> stringResource(R.string.photos_favorites)
            Page.Memories -> stringResource(R.string.ph_memories); Page.Duplicates -> stringResource(R.string.ph_duplicates)
        }
    }
    val pager = rememberPagerState { pages.size }
    val ctx = GridContext(prefs, selection, onOpen)
    val picked = remember(photos, selection.ids) { photos.orEmpty().filter { it.id in selection.ids } }

    Box(Modifier.fillMaxSize()) {
        MiniFrame {
            HubPager(
                stringResource(R.string.stock_photohub), titles, pager,
                userScrollEnabled = !needsPermission && photos != null, titleTag = "photos:title", pagerTag = "photos:pager",
            ) { page ->
                when {
                    // Only on the first page: the next one peeks in at the edge and would ask twice.
                    needsPermission -> if (page == 0) Column(Modifier.fillMaxWidth().padding(horizontal = SIDE).testTag("photos:permission"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        MetroText(stringResource(R.string.photos_allow_why), size = 18.sp, color = LocalMetroColors.current.subtle)
                        MetroButton(stringResource(R.string.photos_allow), Modifier.testTag("photos:allow"), onClick = onAllow)
                    }
                    shown == null -> Loading("photos:loading")
                    else -> when (pages.getOrElse(page) { Page.Albums }) {
                        Page.Albums -> AlbumsPage(shown, album, ctx, onAlbum = { album = it })
                        Page.Date -> DatePage(shown, ctx)
                        Page.Favorites -> PhotoList(PhotoLibrary.favorites(shown), ctx, "photos:favorites")
                        Page.Memories -> MemoriesPage(memories, ctx)
                        Page.Duplicates -> DuplicatesPage(duplicates, ctx)
                    }
                }
            }
            if (!needsPermission && photos != null) {
                if (actions.partial) Notice(stringResource(R.string.ph_partial), stringResource(R.string.ph_partial_more), onAction = actions.selectMore)
                actions.loadMore?.let { more -> Notice(stringResource(R.string.ph_more_note, photos.size), stringResource(R.string.ph_more), onAction = more) }
                if (searching) MetroTextField(query, { query = it }, stringResource(R.string.ph_search_hint), Modifier.padding(horizontal = SIDE, vertical = 6.dp).testTag("photos:search"))
                if (selection.mode) {
                    val context = LocalContext.current
                    Notice(context.resources.getQuantityString(R.plurals.ph_selected, picked.size, picked.size), stringResource(R.string.ph_cancel), onAction = selection::clear)
                    PhotoBar(
                        buildList {
                            if (picked.isNotEmpty()) add(BarItem(PhotoIcon.Share, stringResource(R.string.pv_share), tag = "photos:bar:share") { actions.share(picked); selection.clear() })
                            if (picked.isNotEmpty() && prefs[PhotoOption.FavoriteEdit] && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val unmark = picked.all { it.favorite }
                                add(BarItem(PhotoIcon.Heart, stringResource(if (unmark) R.string.ph_fav_remove else R.string.ph_fav_add), on = !unmark) { actions.favorite(picked, !unmark); selection.clear() })
                            }
                            if (picked.size == 2) add(BarItem(PhotoIcon.Compare, stringResource(R.string.ph_compare)) { comparing = picked[0] to picked[1]; selection.clear() })
                            if (picked.isNotEmpty()) add(BarItem(PhotoIcon.Trash, stringResource(R.string.pv_delete), tag = "photos:bar:delete") { actions.delete(picked); selection.clear() })
                        },
                    )
                } else PhotoBar(
                    buildList {
                        if (prefs[PhotoOption.Search]) add(BarItem(PhotoIcon.Search, stringResource(R.string.ph_search), on = searching, tag = "photos:bar:search") { searching = !searching; if (!searching) query = "" })
                        if (prefs[PhotoOption.Select]) add(BarItem(PhotoIcon.Select, stringResource(R.string.ph_select), tag = "photos:bar:select") { selection.mode = true })
                        add(BarItem(PhotoIcon.Options, stringResource(R.string.ph_options), tag = "photos:bar:options") { options = true })
                    },
                )
            }
        }
        if (options) OptionsPanel(prefs) { options = false }
        comparing?.let { (a, b) -> PhotoCompare(a, b) { comparing = null } }
    }
}

/** The pictures of an album, of the favourites: one run, no headings. */
@Composable
private fun PhotoList(photos: List<Photo>, ctx: GridContext, tag: String) {
    if (photos.isEmpty()) return EmptyNote("$tag:empty")
    PhotoSections(listOf(GridSection(tag, null, photos)), tag, ctx)
}

@Composable
private fun AlbumsPage(photos: List<Photo>, open: String?, ctx: GridContext, onAlbum: (String?) -> Unit) {
    val prefs = ctx.prefs
    val cameraFirst = prefs[PhotoOption.CameraFirst]
    val albums = remember(photos, cameraFirst) { PhotoLibrary.albums(photos, cameraFirst) }
    val inside = albums.firstOrNull { it.key == open }
    if (inside != null) return PhotoList(inside.photos, ctx, "photos:album")
    if (albums.isEmpty()) return EmptyNote("photos:albums:empty")
    val other = stringResource(R.string.photos_other)
    val context = LocalContext.current
    // Two albums of the same name on different storages keep the storage in the label.
    val repeated = remember(albums) { albums.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys }
    LazyVerticalGrid(
        GridCells.Adaptive(150.dp), Modifier.fillMaxSize().testTag("photos:albums"), contentPadding = PaddingValues(start = SIDE, end = SIDE, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(albums, key = { it.key }) { album ->
            Column(Modifier.metroPress(onClick = { onAlbum(album.key) }).testTag("album:${album.name}")) {
                AlbumCover(album.photos, prefs)
                val label = album.name.ifEmpty { other } + if (album.name in repeated && album.volume.isNotEmpty()) " · ${album.volume}" else ""
                MetroText(label, size = 18.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                MetroText(context.resources.getQuantityString(R.plurals.ph_count, album.photos.size, album.photos.size), size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, maxLines = 1)
            }
        }
    }
}

/** The cover of an album: its latest picture, or four of them in a square. */
@Composable
private fun AlbumCover(photos: List<Photo>, prefs: PhotoPrefs) {
    if (!prefs[PhotoOption.Mosaic] || photos.size < 4) return Thumb(photos.first(), prefs = prefs)
    Column(Modifier.fillMaxWidth().aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (row in 0..1) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (col in 0..1) Thumb(photos[row * 2 + col], Modifier.weight(1f).fillMaxHeight(), prefs, fill = true)
        }
    }
}

@Composable
private fun DatePage(photos: List<Photo>, ctx: GridContext) {
    val granularity = ctx.prefs.granularity
    val today = LocalDate.now()
    val todayLabel = stringResource(R.string.ph_today)
    val yesterdayLabel = stringResource(R.string.ph_yesterday)
    val sections = remember(photos, granularity) {
        PhotoLibrary.sections(photos, granularity).map { s ->
            val title = when (granularity) {
                Granularity.Day -> when (s.date) { today -> todayLabel; today.minusDays(1) -> yesterdayLabel; else -> day(s.date) }
                Granularity.Month -> s.date.format(DateTimeFormatter.ofPattern("LLLL yyyy"))
                Granularity.Year -> s.date.year.toString()
            }
            GridSection(s.key, title, s.photos)
        }
    }
    if (sections.isEmpty()) return EmptyNote("photos:date:empty")
    PhotoSections(sections, "photos:date", ctx, scrubber = true)
}

@Composable
private fun MemoriesPage(memories: List<Pair<Int, List<Photo>>>, ctx: GridContext) {
    val resources = LocalContext.current.resources
    val sections = remember(memories) { memories.map { (years, list) -> GridSection("memory:$years", resources.getQuantityString(R.plurals.ph_years_ago, years, years), list) } }
    PhotoSections(sections, "photos:memories", ctx)
}

@Composable
private fun DuplicatesPage(groups: List<List<Photo>>, ctx: GridContext) {
    val context = LocalContext.current
    val sections = remember(groups) {
        groups.mapIndexed { i, g -> GridSection("dup:$i", context.getString(R.string.ph_dup_group, g.size, android.text.format.Formatter.formatShortFileSize(context, g.first().bytes)), g) }
    }
    PhotoSections(sections, "photos:duplicates", ctx)
}
