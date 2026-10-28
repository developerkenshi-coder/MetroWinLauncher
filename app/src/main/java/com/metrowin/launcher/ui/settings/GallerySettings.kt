package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.AlbumInfo

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
        SettingLabelHint(albums)
        albums?.take(MAX_ALBUMS)?.forEach { album ->
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

@Composable
private fun SettingLabelHint(albums: List<AlbumInfo>?) {
    SettingHint(
        stringResource(if (albums != null && albums.isEmpty()) R.string.gallery_albums_none else R.string.gallery_albums_hint),
        androidx.compose.ui.Modifier.testTag("gallery:hint"),
    )
}

private const val MAX_ALBUMS = 25
