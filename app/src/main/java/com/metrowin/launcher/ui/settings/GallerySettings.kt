package com.metrowin.launcher.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.AlbumInfo
import com.metrowin.launcher.data.GallerySource
import com.metrowin.launcher.data.SettingsCodec

/** The Photos tile: the gallery app it opens, and the albums its pictures come from (the pictures of the gallery, not everything in the memory of the phone). */
@Composable
fun GallerySettings(env: SettingsEnv) {
    val apps = env.galleryApps
    val chosen = env.settings.galleryAlbums
    // null until the albums are read; empty without the permission to read photos.
    val albums by produceState<List<AlbumInfo>?>(null, env.settings.galleryApp) { value = runCatching { env.loadAlbums() }.getOrDefault(emptyList()) }
    Column(androidx.compose.ui.Modifier.settingAnchor("live.gallery"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingHeading(stringResource(R.string.gallery_heading))
        if (apps.isNotEmpty()) {
            val names = listOf(stringResource(R.string.gallery_auto)) + apps.map { it.label }
            OptionList(
                label = stringResource(R.string.gallery_app),
                options = names,
                selected = apps.indexOfFirst { it.packageName == env.settings.galleryApp } + 1,
                onSelect = { i -> env.onChange { it.copy(galleryApp = if (i == 0) "" else apps[i - 1].packageName) } },
                hint = stringResource(R.string.gallery_app_hint),
                tag = "gallery:app",
            )
        }
        val source = env.settings.gallerySource
        OptionList(
            label = stringResource(R.string.gallery_source),
            options = GallerySource.entries.map { stringResource(sourceLabel(it)) },
            selected = source.ordinal,
            onSelect = { i -> env.onChange { it.copy(gallerySource = GallerySource.entries[i]) } },
            tag = "gallery:source",
        )
        if (source == GallerySource.Chosen) ChosenPhotos(env)
        if (source == GallerySource.Albums) SettingLabelHint(albums)
        if (source == GallerySource.Albums) albums?.take(MAX_ALBUMS)?.forEach { album ->
            val on = chosen.isEmpty() || album.id in chosen
            ToggleSetting(
                label = album.name,
                checked = on,
                onChange = { value ->
                    env.onChange { s ->
                        val all = albums.orEmpty().take(MAX_ALBUMS).map { it.id }.toSet()
                        val base = s.galleryAlbums.ifEmpty { all }
                        val next = if (value) base + album.id else base - album.id
                        // Every album ticked is the same as none chosen.
                        s.copy(galleryAlbums = if (next.containsAll(all)) emptySet() else next)
                    }
                },
                tag = "gallery:album:${album.id}",
            )
        }
    }
}

private fun sourceLabel(source: GallerySource) = when (source) {
    GallerySource.Recent -> R.string.gallery_source_recent
    GallerySource.Albums -> R.string.gallery_source_albums
    GallerySource.Chosen -> R.string.gallery_source_chosen
}

/** Picks photos with the document picker, which lets the launcher keep reading them after it restarts. */
@Composable
private fun ChosenPhotos(env: SettingsEnv) {
    val resolver = LocalContext.current.contentResolver
    val picked = env.settings.galleryPhotos
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val kept = uris.filter { uri -> runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.isSuccess }.map(Uri::toString)
        if (kept.isNotEmpty()) env.onChange { s -> s.copy(galleryPhotos = (s.galleryPhotos + kept).distinct().take(SettingsCodec.MAX_GALLERY_PHOTOS)) }
    }
    SettingHint(
        if (picked.isEmpty()) stringResource(R.string.gallery_chosen_none) else stringResource(R.string.gallery_chosen_count, picked.size),
        androidx.compose.ui.Modifier.testTag("gallery:chosen"),
    )
    ActionSetting(stringResource(R.string.gallery_pick), onClick = { picker.launch(arrayOf("image/*")) }, tag = "gallery:pick")
    if (picked.isNotEmpty()) {
        ActionSetting(
            stringResource(R.string.gallery_pick_clear),
            onClick = {
                picked.forEach { runCatching { resolver.releasePersistableUriPermission(it.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
                env.onChange { it.copy(galleryPhotos = emptyList()) }
            },
            tag = "gallery:clear",
        )
    }
}

@Composable
private fun SettingLabelHint(albums: List<AlbumInfo>?) {
    SettingHint(
        stringResource(if (albums != null && albums.isEmpty()) R.string.gallery_albums_none else R.string.gallery_albums_hint),
        androidx.compose.ui.Modifier.testTag("gallery:hint"),
    )
}

private const val MAX_ALBUMS = 25
