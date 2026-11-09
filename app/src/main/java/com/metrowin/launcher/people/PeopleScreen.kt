package com.metrowin.launcher.people

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.calls.DialerRole
import com.metrowin.launcher.data.Favourite
import com.metrowin.launcher.data.LivePermissions
import com.metrowin.launcher.data.PeopleReader
import com.metrowin.launcher.data.RecentCall
import com.metrowin.launcher.messages.MessagesActivity
import com.metrowin.launcher.messages.Sms
import com.metrowin.launcher.messages.SmsRepository
import com.metrowin.launcher.notifications.NotificationHub
import com.metrowin.launcher.ui.anim.featherIn
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.apps.JumpList
import com.metrowin.launcher.ui.components.HubPager
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.start.ageText
import com.metrowin.launcher.ui.start.whenText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.onAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SIDE = 24.dp

/** Who the card shows: a contact ([key] is its lookup address) or just a number from the call log. */
private data class Target(val name: String, val key: String?, val number: String?, val starred: Boolean = false, val photoUri: String? = null, val photo: ImageBitmap? = null)

private val photos = object : LruCache<String, ImageBitmap>(80) {}

@Composable
private fun rememberPhoto(uri: String?): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<ImageBitmap?>(uri?.let(photos::get), uri) {
        if (uri != null && value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use(BitmapFactory::decodeStream)?.asImageBitmap() }.getOrNull()
            }?.also { photos.put(uri, it) }
        }
    }
    return bitmap
}

/** A contact as a tile: its photo, or its first letter on the accent colour, and the name over the foot of it. */
@Composable
private fun PhotoTile(name: String, photo: ImageBitmap?, modifier: Modifier = Modifier, label: Boolean = true) {
    Box(modifier.aspectRatio(1f).background(LocalAccent.current)) {
        if (photo != null) Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else MetroText(name.take(1).uppercase(), size = 40.sp, color = onAccent(), modifier = Modifier.align(Alignment.Center))
        if (label) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))).padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 6.dp)) {
            MetroText(name, size = 14.sp, weight = FontWeight.Normal, maxLines = 1, color = Color.White)
        }
    }
}

/**
 * The People hub of Windows Phone: a panorama with all the contacts under their letters (the letter opens the jump list), what is new (the
 * latest calls and notifications) and the favourites as photo tiles. A touch on a person opens the card with a way to call or write and
 * the history of calls and messages.
 */
@Composable
fun PeopleScreen() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(LivePermissions.isGranted(context, LivePermissions.contacts)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    var calls by remember { mutableStateOf(LivePermissions.isGranted(context, LivePermissions.calls)) }
    val askCalls = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { calls = it }
    val people by produceState<List<Person>?>(null, allowed) { value = if (allowed) withContext(Dispatchers.IO) { PeopleBook.read(context) } else emptyList() }
    val favourites by produceState<List<Favourite>>(emptyList(), allowed) { value = if (allowed) withContext(Dispatchers.IO) { PeopleReader(context).favourites() } else emptyList() }
    val recent by produceState<List<RecentCall>>(emptyList(), calls) { value = if (calls) withContext(Dispatchers.IO) { PeopleReader(context).recentCalls() } else emptyList() }
    val news by NotificationHub.entries.collectAsState()
    var target by remember { mutableStateOf<Target?>(null) }
    val pager = rememberPagerState { 3 }
    BackHandler(enabled = target != null) { target = null }

    Box(Modifier.fillMaxSize()) {
        MiniFrame {
            HubPager(
                stringResource(R.string.peoplehub_default_label),
                listOf(stringResource(R.string.ppl_all), stringResource(R.string.peoplehub_whats_new), stringResource(R.string.peoplehub_favourites)), pager,
                userScrollEnabled = allowed,
            ) { page ->
                if (!allowed) {
                    if (page == 0) Column(Modifier.padding(horizontal = SIDE).testTag("people:permission"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        MetroText(stringResource(R.string.page_needs_contacts), size = 18.sp, color = LocalMetroColors.current.subtle, maxLines = Int.MAX_VALUE)
                        MetroButton(stringResource(R.string.photos_allow), onClick = { ask.launch(LivePermissions.contacts) })
                    }
                    return@HubPager
                }
                when (page) {
                    0 -> AllPage(people) { target = Target(it.name, it.key, null, it.starred, it.photoUri) }
                    1 -> NewPage(recent, calls, news, onAllowCalls = { askCalls.launch(LivePermissions.calls) }) { target = it }
                    else -> FavouritesPage(favourites) { target = Target(it.name, it.lookupUri, it.number, true, null, it.photo) }
                }
            }
        }
        target?.let { ContactCard(it, onBack = { target = null }) }
    }
}

@Composable
private fun AllPage(people: List<Person>?, onOpen: (Person) -> Unit) {
    if (people == null) return
    if (people.isEmpty()) return MetroText(stringResource(R.string.ppl_empty), size = 18.sp, color = LocalMetroColors.current.subtle, modifier = Modifier.padding(horizontal = SIDE).testTag("people:empty"))
    val groups = remember(people) { PeopleBook.grouped(people) }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var jump by remember { mutableStateOf(false) }
    // Where each letter starts in the list: its header, then its people.
    val starts = remember(groups) { var at = 0; groups.associate { (letter, list) -> letter to at.also { at += list.size + 1 } } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().testTag("people:all"), state, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            groups.forEach { (letter, list) ->
                item(key = "letter:$letter") {
                    Box(Modifier.padding(start = SIDE, top = 8.dp, bottom = 4.dp).size(52.dp).background(LocalAccent.current).metroPress(onClick = { jump = true }).testTag("people:letter:$letter"), contentAlignment = Alignment.Center) {
                        MetroText(letter.lowercase(), size = 30.sp, color = onAccent())
                    }
                }
                itemsIndexed(list, key = { _, p -> p.key }) { index, person ->
                    Row(Modifier.fillMaxWidth().featherIn(index, person.key).metroPress(onClick = { onOpen(person) }).padding(horizontal = SIDE, vertical = 5.dp).testTag("people:person"), verticalAlignment = Alignment.CenterVertically) {
                        PhotoTile(person.name, rememberPhoto(person.photoUri), Modifier.size(56.dp), label = false)
                        MetroText(person.name, size = 24.sp, maxLines = 1, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
        if (jump) {
            // The whole alphabet; the letters that have people are the ones that answer.
            val keys = remember { ('A'..'Z').map { it.toString() } + "#" }
            JumpList(
                keys = keys, available = groups.map { it.first }.toSet(), showRecent = false,
                onPick = { letter -> jump = false; scope.launch { state.scrollToItem(starts[letter] ?: 0) } },
                onDismiss = { jump = false },
            )
        }
    }
}

@Composable
private fun NewPage(
    recent: List<RecentCall>, hasCalls: Boolean, news: List<com.metrowin.launcher.notifications.NotificationEntry>,
    onAllowCalls: () -> Unit, onOpen: (Target) -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalMetroColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = SIDE).testTag("people:new")) {
        MetroText(stringResource(R.string.peoplehub_recent), size = 18.sp, color = colors.subtle, modifier = Modifier.padding(bottom = 6.dp))
        when {
            !hasCalls -> {
                MetroText(stringResource(R.string.page_needs_calls), size = 16.sp, color = colors.subtle, maxLines = Int.MAX_VALUE, modifier = Modifier.padding(bottom = 8.dp))
                MetroButton(stringResource(R.string.photos_allow), onClick = onAllowCalls)
            }
            recent.isEmpty() -> MetroText(stringResource(R.string.peoplehub_no_calls), size = 16.sp, color = colors.subtle)
            else -> recent.take(10).forEachIndexed { index, call ->
                Row(Modifier.fillMaxWidth().featherIn(index, call.date).metroPress(onClick = { onOpen(Target(call.name, null, call.number)) }).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetroText(if (call.missed) "✕" else if (call.outgoing) "↗" else "↙", size = 20.sp, weight = FontWeight.Normal, color = if (call.missed) LocalAccent.current else colors.subtle, modifier = Modifier.padding(end = 12.dp))
                    Column(Modifier.weight(1f)) {
                        MetroText(call.name, size = 22.sp, maxLines = 1)
                        MetroText(whenText(call.date), size = 13.sp, weight = FontWeight.Normal, color = colors.subtle)
                    }
                }
            }
        }
        MetroText(stringResource(R.string.peoplehub_whats_new), size = 18.sp, color = colors.subtle, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
        if (news.isEmpty()) MetroText(stringResource(R.string.peoplehub_nothing_new), size = 16.sp, color = colors.subtle)
        news.take(10).forEach { entry ->
            Column(Modifier.fillMaxWidth().metroPress(onClick = { NotificationHub.open(context, entry) }).padding(bottom = 10.dp)) {
                MetroText(entry.title.ifBlank { entry.packageName }, size = 19.sp, maxLines = 1)
                if (entry.text.isNotBlank()) MetroText(entry.text, size = 15.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 2)
                ageText(entry.postTime)?.let { MetroText(it, size = 12.sp, weight = FontWeight.Normal, color = colors.subtle) }
            }
        }
    }
}

@Composable
private fun FavouritesPage(favourites: List<Favourite>, onOpen: (Favourite) -> Unit) {
    if (favourites.isEmpty()) return MetroText(stringResource(R.string.dialgrid_empty), size = 18.sp, color = LocalMetroColors.current.subtle, modifier = Modifier.padding(horizontal = SIDE).testTag("people:favourites:empty"))
    LazyVerticalGrid(
        GridCells.Fixed(3), Modifier.fillMaxSize().testTag("people:favourites"), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = SIDE, end = SIDE, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(favourites, key = { it.lookupUri }) { person -> PhotoTile(person.name, person.photo, Modifier.metroPress(onClick = { onOpen(person) }).testTag("people:fav")) }
    }
}

/** A round button with a 2 dp edge, like the ones of the bars of Windows Phone. */
@Composable
private fun Round(symbol: String, label: String, onClick: () -> Unit) {
    val colors = LocalMetroColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.metroPress(onClick = onClick).padding(horizontal = 12.dp)) {
        Box(Modifier.size(48.dp).border(2.dp, colors.foreground, CircleShape).clip(CircleShape), contentAlignment = Alignment.Center) { MetroText(symbol, size = 22.sp, weight = FontWeight.Normal) }
        MetroText(label, size = 12.sp, weight = FontWeight.Normal, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
    }
}

/** The card of a person: the photo across the top, a way to call and to write, the numbers, and the history with them. */
@Composable
private fun ContactCard(target: Target, onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalMetroColors.current
    val photo = target.photo ?: rememberPhoto(target.photoUri)
    val numbers by produceState(listOfNotNull(target.number), target.key) {
        value = withContext(Dispatchers.IO) { (target.key?.let { PeopleBook.numbersOf(context, it) }.orEmpty() + listOfNotNull(target.number)).distinct() }
    }
    val history by produceState<Pair<List<RecentCall>, List<Sms>>?>(null, numbers) {
        value = withContext(Dispatchers.IO) {
            val calls = if (LivePermissions.isGranted(context, LivePermissions.calls)) PeopleReader(context).recentCalls(200).filter { c -> numbers.any { PeopleBook.sameNumber(it, c.number) } } else emptyList()
            val texts = runCatching {
                val repo = SmsRepository(context)
                repo.conversations().filter { c -> numbers.any { PeopleBook.sameNumber(it, c.address) } }.flatMap { repo.messages(it.threadId) }.sortedByDescending { it.date }
            }.getOrDefault(emptyList())
            calls to texts
        }
    }
    val main = numbers.firstOrNull()
    Column(Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).testTag("people:card")) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.2f).background(LocalAccent.current)) {
            if (photo != null) Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else MetroText(target.name.take(1).uppercase(), size = 96.sp, color = onAccent(), modifier = Modifier.align(Alignment.Center))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))).padding(start = SIDE, end = SIDE, top = 40.dp, bottom = 12.dp)) {
                MetroText(target.name, size = 36.sp, color = Color.White, maxLines = 2)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (main != null) {
                Round("☎", stringResource(R.string.ppl_call)) { DialerRole.call(context, main, direct = false) }
                Round("✉", stringResource(R.string.ppl_message)) {
                    runCatching { context.startActivity(Intent(context, MessagesActivity::class.java).putExtra(MessagesActivity.EXTRA_ADDRESS, main)) }
                }
            }
            Round("←", stringResource(R.string.ppl_back), onBack)
        }
        Column(Modifier.padding(horizontal = SIDE)) {
            numbers.forEach { number ->
                MetroText(number, size = 22.sp, modifier = Modifier.padding(bottom = 6.dp).metroPress(onClick = { DialerRole.call(context, number, direct = false) }))
            }
            MetroText(stringResource(R.string.ppl_history), size = 18.sp, color = colors.subtle, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            val (calls, texts) = history ?: (emptyList<RecentCall>() to emptyList())
            if (history != null && calls.isEmpty() && texts.isEmpty()) MetroText(stringResource(R.string.ppl_no_history), size = 16.sp, color = colors.subtle, modifier = Modifier.padding(bottom = 24.dp))
            // Calls and messages together, the latest first, as one line of time.
            val lines = (calls.take(8).map { it.date to ((if (it.missed) "✕ " else if (it.outgoing) "↗ " else "↙ ") + stringResource(R.string.ppl_call)) } +
                texts.take(8).map { it.date to ((if (it.outgoing) "↗ " else "↙ ") + it.body.take(80)) }).sortedByDescending { it.first }
            lines.forEachIndexed { index, (date, text) ->
                Column(Modifier.fillMaxWidth().featherIn(index, target.key ?: "").padding(bottom = 10.dp).testTag("people:history")) {
                    MetroText(text, size = 18.sp, weight = FontWeight.Normal, maxLines = 2)
                    MetroText(whenText(date), size = 12.sp, weight = FontWeight.Normal, color = colors.subtle)
                }
            }
            Box(Modifier.padding(bottom = 32.dp))
        }
    }
}
