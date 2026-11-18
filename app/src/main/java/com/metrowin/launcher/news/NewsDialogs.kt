package com.metrowin.launcher.news

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.NewsItem
import com.metrowin.launcher.data.NewsSources
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.ButtonRole
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.start.ageText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.launch

/** A button of a dialog of the news app that is a glyph: at least 48 dp wide and high, and it says what it does to a screen reader. */
@Composable
private fun GlyphButton(glyph: String, label: String, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).alpha(if (enabled) 1f else 0.3f).testTag(tag).semantics { contentDescription = label }.metroPress(onClick = onClick, enabled = enabled),
        contentAlignment = Alignment.Center,
    ) { MetroText(glyph, size = 20.sp, color = LocalMetroColors.current.subtle) }
}

@Composable
internal fun SourcesDialog(
    list: List<NewsSourceDef>,
    topics: Map<String, String>,
    onChange: (List<NewsSourceDef>) -> Unit,
    onRemove: (String) -> Unit,
    showImages: Boolean,
    onImages: (Boolean) -> Unit,
    onClose: () -> Unit,
    /** The id of the source the tile of Start shows: it gets a star. */
    tile: String = "",
    /** The feeds offered to add, the ones of the user's country first. */
    presets: List<NewsSourceDef> = NewsPresets.feeds,
    /** Reads a web page to find the feed it offers (null: the option is off, no button). Returns the address of a feed, or null. */
    onFind: (suspend (String) -> String?)? = null,
) {
    val colors = LocalMetroColors.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf<NewsSourceDef?>(null) }
    var finding by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<Boolean?>(null) }
    DialogFrame(onCancel = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MetroText(stringResource(R.string.nws_sources), size = 22.sp)
            MetroToggle(stringResource(R.string.nws_pictures), showImages, onImages, Modifier.testTag("nws:pictures"))
            asking?.let { source ->
                // Taking a feed away loses its address: ask first.
                Column(Modifier.testTag("nws:confirm"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetroText(stringResource(R.string.nws_remove_ask, source.name), size = 18.sp, weight = FontWeight.Normal, maxLines = 3)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetroButton(stringResource(R.string.nws_remove_yes), Modifier.weight(1f).testTag("nws:remove_yes"), role = ButtonRole.Danger) { onRemove(source.id); asking = null }
                        MetroButton(stringResource(R.string.nws_keep), Modifier.weight(1f).testTag("nws:remove_no")) { asking = null }
                    }
                }
            }
            list.forEachIndexed { index, source ->
                Row(Modifier.fillMaxWidth().testTag("nws:source"), verticalAlignment = Alignment.CenterVertically) {
                    MetroText(
                        (if (source.id == tile) "★ " else "") + source.name, size = 19.sp, maxLines = 1, modifier = Modifier.weight(1f),
                        color = if (source.id == tile) LocalAccent.current else colors.foreground,
                    )
                    GlyphButton("▲", stringResource(R.string.nws_move_up), "nws:up:${source.id}", enabled = index > 0) { onChange(NewsSourceList.move(list, index, -1)) }
                    GlyphButton("▼", stringResource(R.string.nws_move_down), "nws:down:${source.id}", enabled = index < list.lastIndex) { onChange(NewsSourceList.move(list, index, 1)) }
                    GlyphButton("✕", stringResource(R.string.nws_remove_yes), "nws:remove:${source.id}") { asking = source }
                }
            }
            MetroText(stringResource(R.string.nws_add_google), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(NewsSources.TOPICS) { topic ->
                    PageChip(topics[topic].orEmpty(), list.any { it.id == "google:$topic" }, "nws:topic:$topic") {
                        if (list.any { it.id == "google:$topic" }) onRemove("google:$topic") else onChange(NewsSourceList.add(list, NewsSourceList.googleTopic(topic, topics[topic].orEmpty())))
                    }
                }
            }
            MetroText(stringResource(R.string.nws_add_feed), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(presets) { preset ->
                    PageChip(preset.name, list.any { it.url == preset.url }, "nws:preset:${preset.id}") {
                        val existing = list.firstOrNull { it.url == preset.url }
                        if (existing != null) onRemove(existing.id) else onChange(NewsSourceList.add(list, preset))
                    }
                }
            }
            MetroTextField(name, { name = it.take(40) }, stringResource(R.string.nws_name_hint), Modifier.testTag("nws:name"))
            MetroTextField(url, { url = it.take(NewsSources.MAX_URL); bad = false; found = null }, stringResource(R.string.news_url_hint), Modifier.testTag("nws:url"))
            if (bad) PageNote(stringResource(R.string.news_url_bad), Modifier.testTag("nws:bad"))
            found?.let { PageNote(stringResource(if (it) R.string.nws_found else R.string.nws_not_found), Modifier.testTag("nws:found")) }
            if (onFind != null) {
                MetroButton(if (finding) stringResource(R.string.rec_working) else stringResource(R.string.nws_find), Modifier.fillMaxWidth().testTag("nws:find")) {
                    if (finding) return@MetroButton
                    finding = true
                    scope.launch {
                        val address = onFind(url)
                        finding = false
                        found = address != null
                        if (address != null) url = address.take(NewsSources.MAX_URL)
                    }
                }
            }
            MetroButton(stringResource(R.string.nws_add), Modifier.fillMaxWidth().testTag("nws:add")) {
                val next = NewsSourceList.addFeed(list, name, url)
                if (next == null) bad = true else { onChange(next); name = ""; url = ""; found = null }
            }
        }
    }
}

/** The options of the news app. What reaches the network, costs data or keeps a trace is off until the user turns it on. */
@Composable
internal fun NewsOptionsDialog(opts: NewsOpts, onChange: (NewsOpts) -> Unit, onClose: () -> Unit) {
    val colors = LocalMetroColors.current
    DialogFrame(onCancel = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MetroText(stringResource(R.string.nws_options), size = 22.sp)
            MetroToggle(stringResource(R.string.nws_o_any_site), opts.anySite, { onChange(opts.copy(anySite = it)) }, Modifier.testTag("nws:opt:any_site"))
            MetroToggle(stringResource(R.string.nws_o_unmetered), opts.unmeteredOnly, { onChange(opts.copy(unmeteredOnly = it)) }, Modifier.testTag("nws:opt:unmetered"))
            MetroToggle(stringResource(R.string.nws_o_more), opts.moreHeadlines, { onChange(opts.copy(moreHeadlines = it)) }, Modifier.testTag("nws:opt:more"))
            MetroToggle(stringResource(R.string.nws_o_discover), opts.discover, { onChange(opts.copy(discover = it)) }, Modifier.testTag("nws:opt:discover"))
            MetroToggle(stringResource(R.string.nws_o_track), opts.trackRead, { onChange(opts.copy(trackRead = it)) }, Modifier.testTag("nws:opt:track"))
            MetroToggle(stringResource(R.string.nws_o_new), opts.markNew, { onChange(opts.copy(markNew = it)) }, Modifier.testTag("nws:opt:new"))
            MetroToggle(stringResource(R.string.nws_o_days), opts.days, { onChange(opts.copy(days = it)) }, Modifier.testTag("nws:opt:days"))
            MetroToggle(stringResource(R.string.nws_o_compact), opts.compact, { onChange(opts.copy(compact = it)) }, Modifier.testTag("nws:opt:compact"))
            MetroToggle(stringResource(R.string.nws_o_dim), opts.dimImages, { onChange(opts.copy(dimImages = it)) }, Modifier.testTag("nws:opt:dim"))
            MetroToggle(stringResource(R.string.nws_o_read), opts.readMode, { onChange(opts.copy(readMode = it)) }, Modifier.testTag("nws:opt:read"))
            MetroToggle(stringResource(R.string.nws_o_all), opts.allPage, { onChange(opts.copy(allPage = it)) }, Modifier.testTag("nws:opt:all"))
            MetroText(stringResource(R.string.nws_o_text), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(top = 6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(NewsOpts.TEXT_SIZES) { PageChip("$it%", opts.textPercent == it, "nws:text:$it") { onChange(opts.copy(textPercent = it)) } }
            }
            MetroText(stringResource(R.string.nws_o_fresh), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(top = 6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(NewsOpts.FRESH_MINUTES) { PageChip(stringResource(R.string.news_age_minutes, it), opts.freshMinutes == it, "nws:fresh:$it") { onChange(opts.copy(freshMinutes = it)) } }
            }
        }
    }
}

/** What can be done with a headline: read all of it, see its picture larger, open it, share it, save it for later, mark it read. */
@Composable
internal fun NewsActionsDialog(
    item: NewsItem,
    images: NewsPictures,
    showImages: Boolean,
    saved: Boolean,
    read: Boolean,
    trackRead: Boolean,
    now: Long,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onRead: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalMetroColors.current
    DialogFrame(onCancel = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showImages && item.image.isNotEmpty()) RemoteImage(item.image, images, Modifier.fillMaxWidth().aspectRatio(1.6f).testTag("nws:preview"), maxWidth = 1080)
            MetroText(item.title, size = 21.sp, weight = FontWeight.Normal, maxLines = Int.MAX_VALUE, modifier = Modifier.testTag("nws:full_title"))
            val age = ageText(item.publishedAt, now)
            MetroText(listOfNotNull(item.source.ifEmpty { null }, age).joinToString("  ·  "), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle)
            MetroButton(stringResource(R.string.nws_open), Modifier.fillMaxWidth().testTag("nws:act:open")) { onOpen() }
            MetroButton(stringResource(R.string.nws_share), Modifier.fillMaxWidth().testTag("nws:act:share")) { onShare() }
            MetroButton(stringResource(if (saved) R.string.nws_unsave else R.string.nws_save), Modifier.fillMaxWidth().testTag("nws:act:save")) { onSave() }
            if (trackRead) MetroButton(stringResource(if (read) R.string.nws_mark_unread else R.string.nws_mark_read), Modifier.fillMaxWidth().testTag("nws:act:read")) { onRead() }
        }
    }
}
