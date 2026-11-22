package com.metrowin.launcher.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.DialogButtons
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SIDE = 24.dp

private enum class MusicPage { Artists, Albums, Songs, Playlists }

/**
 * The Music hub: a pivot of artists, albums, songs and playlists. A touch on an artist opens its albums, on an album its songs, on a song
 * plays it in the music app of the user; a long touch on a song offers the playlists. [tracks] is null while it is being read.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MusicScreen(
    tracks: List<Track>?,
    needsPermission: Boolean,
    onAllow: () -> Unit,
    playlists: MusicPlaylists,
    onPlay: (Track) -> Unit,
    onPlayAlbum: (MusicAlbum) -> Unit,
) {
    val context = LocalContext.current
    val pages = MusicPage.entries
    val pager = rememberPagerState { pages.size }
    var artist by remember { mutableStateOf<String?>(null) }
    var album by remember { mutableStateOf<String?>(null) }
    var list by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf<Track?>(null) }
    var creating by remember { mutableStateOf(false) }
    // The playlists are in a file: this number makes the lists below read it again after a change.
    var version by remember { mutableIntStateOf(0) }

    val shown = remember(tracks, query) { tracks?.let { MusicLibrary.search(it, query) } }
    BackHandler(enabled = album != null || artist != null || list != null || searching) {
        when {
            album != null -> album = null
            list != null -> list = null
            artist != null -> artist = null
            else -> { searching = false; query = "" }
        }
    }
    val titles = listOf(R.string.music_artists, R.string.music_albums, R.string.music_songs, R.string.music_playlists).map { stringResource(it) }

    Box(Modifier.fillMaxSize()) {
        MiniFrame {
            PivotHeader(titles, pager, Modifier.padding(start = SIDE, top = 12.dp, bottom = 8.dp))
            if (searching) MetroTextField(query, { query = it }, stringResource(R.string.music_search_hint), Modifier.padding(horizontal = SIDE, vertical = 6.dp).testTag("music:search"))
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 0, userScrollEnabled = !needsPermission && tracks != null) { index ->
                when {
                    needsPermission -> if (index == 0) Column(Modifier.fillMaxWidth().padding(horizontal = SIDE).testTag("music:permission"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        MetroText(stringResource(R.string.music_allow_why), size = 18.sp, color = LocalMetroColors.current.subtle)
                        MetroButton(stringResource(R.string.music_allow), Modifier.testTag("music:allow"), onClick = onAllow)
                    }
                    shown == null -> MetroText(stringResource(R.string.music_loading), Modifier.padding(horizontal = SIDE).testTag("music:loading"), size = 18.sp, color = LocalMetroColors.current.subtle)
                    else -> {
                        val all = tracks.orEmpty()
                        val albumsOf = remember(shown) { MusicLibrary.albums(shown) }
                        val openAlbum = albumsOf.firstOrNull { it.key == album }
                        when {
                            // An album, from any page, shows its songs.
                            openAlbum != null -> AlbumSongs(openAlbum, onPlay, onPlayAlbum, onMore = { adding = it })
                            pages[index] == MusicPage.Artists -> {
                                val inside = artist
                                if (inside == null) ArtistList(remember(shown) { MusicLibrary.artists(shown) }) { artist = it }
                                else AlbumList(albumsOf.filter { it.artist == inside }, "music:artist-albums") { album = it.key }
                            }
                            pages[index] == MusicPage.Albums -> AlbumList(albumsOf, "music:albums") { album = it.key }
                            pages[index] == MusicPage.Songs -> SongList(MusicLibrary.songs(shown), "music:songs", onPlay, onMore = { adding = it })
                            else -> PlaylistPage(
                                playlists, version, list, all, query, onOpen = { list = it }, onNew = { creating = true },
                                onPlay = onPlay, onRemove = { name, track -> playlists.remove(name, track.id); version++ },
                                onDelete = { playlists.delete(it); list = null; version++ },
                            )
                        }
                    }
                }
            }
            if (!needsPermission && tracks != null) AppBar(
                listOf(
                    AppBarItem("⌕", stringResource(R.string.music_search)) { searching = !searching; if (!searching) query = "" }.marked(searching),
                ),
            )
        }
        adding?.let { track ->
            DialogFrame(onCancel = { adding = null }) {
                MetroText(track.title.ifEmpty { stringResource(R.string.music_unknown) }, size = 22.sp, maxLines = 2)
                MetroText(stringResource(R.string.music_add_to), size = 16.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
                playlists.names().forEach { name ->
                    MetroButton(name, Modifier.fillMaxWidth().testTag("music:add:$name")) { playlists.add(name, track.id); version++; adding = null }
                }
                MetroButton(stringResource(R.string.music_new_list), Modifier.fillMaxWidth().testTag("music:add:new")) { adding = null; creating = true }
            }
        }
        if (creating) NewPlaylistDialog(onCancel = { creating = false }) { name ->
            if (playlists.create(name)) { version++; creating = false } else android.widget.Toast.makeText(context, R.string.music_list_exists, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun NewPlaylistDialog(onCancel: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    DialogFrame(onCancel = onCancel) {
        MetroText(stringResource(R.string.music_new_list), size = 22.sp)
        MetroTextField(name, { name = it.take(MusicPlaylists.MAX_NAME) }, stringResource(R.string.music_list_name), Modifier.fillMaxWidth().testTag("music:new:name"), onSearch = { onCreate(name) })
        DialogButtons({ onCreate(name) }, onCancel, "music:new:ok", "music:new:cancel", okText = stringResource(R.string.music_create))
    }
}

@Composable
private fun Empty(tag: String) {
    MetroText(stringResource(R.string.music_empty), Modifier.padding(horizontal = SIDE).testTag(tag), size = 18.sp, color = LocalMetroColors.current.subtle)
}

@Composable
private fun name(text: String) = text.ifEmpty { stringResource(R.string.music_unknown) }

@Composable
private fun Row2(title: String, subtitle: String, modifier: Modifier, lead: @Composable () -> Unit = {}, trail: String = "") {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = SIDE), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        lead()
        Column(Modifier.weight(1f)) {
            MetroText(title, size = 20.sp, maxLines = 1)
            if (subtitle.isNotEmpty()) MetroText(subtitle, size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, maxLines = 1)
        }
        if (trail.isNotEmpty()) MetroText(trail, size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, tabular = true)
    }
}

@Composable
private fun ArtistList(artists: List<MusicArtist>, onOpen: (String) -> Unit) {
    if (artists.isEmpty()) return Empty("music:artists:empty")
    val resources = LocalContext.current.resources
    LazyColumn(Modifier.fillMaxSize().testTag("music:artists"), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(artists, key = { it.name }) { artist ->
            val label = name(artist.name)
            Row2(
                label, "${resources.getQuantityString(R.plurals.music_albums_count, artist.albums, artist.albums)} · ${resources.getQuantityString(R.plurals.music_songs_count, artist.tracks, artist.tracks)}",
                Modifier.metroPress(onClick = { onOpen(artist.name) }).testTag("music:artist:${artist.name}"),
            )
        }
    }
}

@Composable
private fun AlbumList(albums: List<MusicAlbum>, tag: String, onOpen: (MusicAlbum) -> Unit) {
    if (albums.isEmpty()) return Empty("$tag:empty")
    val resources = LocalContext.current.resources
    LazyColumn(Modifier.fillMaxSize().testTag(tag), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(albums, key = { it.key }) { album ->
            Row2(
                name(album.name), "${name(album.artist)} · ${resources.getQuantityString(R.plurals.music_songs_count, album.tracks.size, album.tracks.size)}",
                Modifier.metroPress(onClick = { onOpen(album) }).testTag("music:album:${album.name}"),
                lead = { Cover(album.tracks.first()) },
            )
        }
    }
}

@Composable
private fun AlbumSongs(album: MusicAlbum, onPlay: (Track) -> Unit, onPlayAlbum: (MusicAlbum) -> Unit, onMore: (Track) -> Unit) {
    Column(Modifier.fillMaxSize().testTag("music:album-songs")) {
        Row(Modifier.padding(horizontal = SIDE).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(album.tracks.first(), 72)
            Column(Modifier.weight(1f)) {
                MetroText(name(album.name), size = 22.sp, maxLines = 2)
                MetroText(listOf(name(album.artist), album.year.takeIf { it > 0 }?.toString().orEmpty()).filter { it.isNotEmpty() }.joinToString(" · "), size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, maxLines = 1)
            }
        }
        MetroButton(stringResource(R.string.music_play_album), Modifier.padding(horizontal = SIDE, vertical = 4.dp).fillMaxWidth().testTag("music:play-album")) { onPlayAlbum(album) }
        SongList(album.tracks, "music:album-tracks", onPlay, onMore, numbered = true)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongList(tracks: List<Track>, tag: String, onPlay: (Track) -> Unit, onMore: (Track) -> Unit, numbered: Boolean = false, onLong: ((Track) -> Unit)? = null) {
    if (tracks.isEmpty()) return Empty("$tag:empty")
    LazyColumn(Modifier.fillMaxSize().testTag(tag), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(tracks, key = { it.id }) { track ->
            val sub = if (numbered) "" else listOf(track.artist, track.album).filter { it.isNotEmpty() }.joinToString(" · ")
            Row2(
                (if (numbered && track.number > 0) "${track.number}  " else "") + name(track.title), sub,
                Modifier.metroPress(onClick = { onPlay(track) }, onLongClick = { (onLong ?: onMore)(track) }).testTag("music:song:${track.id}"),
                trail = MusicLibrary.duration(track.durationMs),
            )
        }
    }
}

@Composable
private fun PlaylistPage(
    playlists: MusicPlaylists, version: Int, open: String?, all: List<Track>, query: String,
    onOpen: (String) -> Unit, onNew: () -> Unit, onPlay: (Track) -> Unit, onRemove: (String, Track) -> Unit, onDelete: (String) -> Unit,
) {
    val names = remember(version) { playlists.names() }
    val resources = LocalContext.current.resources
    if (open != null && open in names) {
        val byId = remember(all) { all.associateBy { it.id } }
        val songs = remember(version, open, all, query) { MusicLibrary.search(playlists.idsOf(open).mapNotNull { byId[it] }, query) }
        Column(Modifier.fillMaxSize().testTag("music:playlist")) {
            MetroText(open, Modifier.padding(horizontal = SIDE), size = 22.sp, maxLines = 2)
            MetroButton(stringResource(R.string.music_delete_list), Modifier.padding(horizontal = SIDE, vertical = 8.dp).fillMaxWidth().testTag("music:delete-list")) { onDelete(open) }
            MetroText(stringResource(R.string.music_remove_hint), Modifier.padding(horizontal = SIDE, vertical = 4.dp), size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
            SongList(songs, "music:playlist-songs", onPlay, onMore = { onRemove(open, it) }, onLong = { onRemove(open, it) })
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("music:playlists"), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { MetroButton(stringResource(R.string.music_new_list), Modifier.padding(horizontal = SIDE, vertical = 4.dp).fillMaxWidth().testTag("music:new"), onClick = onNew) }
        items(names, key = { it }) { listName ->
            val count = playlists.idsOf(listName).size
            Row2(listName, resources.getQuantityString(R.plurals.music_songs_count, count, count), Modifier.metroPress(onClick = { onOpen(listName) }).testTag("music:list:$listName"))
        }
    }
}

/** The cover of an album, loaded off the main thread; a square in the accent with a note until it is there (or when there is none). */
@Composable
private fun Cover(track: Track, size: Int = 48) {
    val context = LocalContext.current
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.dp.roundToPx() }
    val bitmap by produceState<android.graphics.Bitmap?>(null, track.albumId, track.id) { value = withContext(Dispatchers.IO) { MusicCovers.load(context, track, px) } }
    Box(Modifier.size(size.dp).background(LocalAccent.current), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image == null) MetroText("♪", size = (size / 2).sp)
        else androidx.compose.foundation.Image(image.asImageBitmap(), null, Modifier.size(size.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    }
}
