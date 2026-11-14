package com.metrowin.launcher.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.ChoiceRow
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion

/** One round button of a [PhotoBar]. [on] fills the sign (a favourite that is marked). */
internal class BarItem(val icon: PhotoIcon, val label: String, val on: Boolean = false, val tag: String? = null, val onClick: () -> Unit)

/** The bar at the foot of the hub and of the viewer: round buttons with a drawn sign and a label each, as in the other apps. [veil] lets the picture show through. */
@Composable
internal fun PhotoBar(items: List<BarItem>, modifier: Modifier = Modifier, veil: Float = 1f) {
    val colors = LocalMetroColors.current
    Row(
        modifier.fillMaxWidth().background((colors.containers.getOrElse(0) { colors.surface }).copy(alpha = veil)).padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            Column(Modifier.metroPress(onClick = item.onClick).padding(horizontal = 8.dp).let { if (item.tag != null) it.androidx_testTag(item.tag) else it }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(40.dp).border(2.dp, colors.foreground, CircleShape).clip(CircleShape), contentAlignment = Alignment.Center) {
                    PhotoIconView(if (item.on) item.icon.filled() else item.icon, size = 20.dp)
                }
                MetroText(item.label, size = 11.sp, color = colors.subtle, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

private fun Modifier.androidx_testTag(tag: String) = this.testTag(tag)

private fun PhotoIcon.filled() = if (this == PhotoIcon.Heart) PhotoIcon.HeartFilled else this

/** The loading of the pictures: a thin line of the accent that runs along, and a word. */
@Composable
internal fun Loading(tag: String) {
    val reduced = LocalReduceMotion.current
    val shift by rememberInfiniteTransition(label = "photos-loading").animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "run")
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).androidx_testTag(tag), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(LocalMetroColors.current.inactive)) {
            Box(Modifier.width(80.dp).height(4.dp).padding(start = (if (reduced) 120f else shift * 260f).dp).background(LocalAccent.current))
        }
        MetroText(stringResource(R.string.ph_loading), size = 18.sp, color = LocalMetroColors.current.subtle)
    }
}

/** A page with nothing on it: a drawn frame and the words [message]. */
@Composable
internal fun EmptyNote(tag: String, message: String = stringResource(R.string.photos_empty)) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).androidx_testTag(tag), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PhotoIllustration(Modifier.size(96.dp), LocalMetroColors.current.subtle)
        MetroText(message, size = 18.sp, color = LocalMetroColors.current.subtle)
    }
}

/** A line with a word of the user's on it, and a button: the question before a deletion, the note after one. */
@Composable
internal fun Notice(text: String, action: String, modifier: Modifier = Modifier, secondary: String? = null, onSecondary: () -> Unit = {}, onAction: () -> Unit) {
    val colors = LocalMetroColors.current
    Row(modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetroText(text, size = 16.sp, modifier = Modifier.weight(1f))
        if (secondary != null) MetroText(secondary, size = 16.sp, color = colors.subtle, modifier = Modifier.metroPress(onClick = onSecondary).padding(8.dp))
        MetroText(action, size = 16.sp, weight = FontWeight.SemiBold, color = LocalAccent.current, modifier = Modifier.metroPress(onClick = onAction).padding(8.dp))
    }
}

/** The question before a deletion that Android does not ask itself (before Android 11 a picture goes at once). */
@Composable
internal fun ConfirmDelete(count: Int, onYes: () -> Unit, onNo: () -> Unit) {
    BackHandler(onBack = onNo)
    val colors = LocalMetroColors.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).metroPress(onClick = onNo), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp).background(colors.surface).padding(24.dp).androidx_testTag("photos:confirm"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            MetroText(androidx.compose.ui.platform.LocalContext.current.resources.getQuantityString(R.plurals.ph_confirm_delete, count, count), size = 20.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetroButton(stringResource(R.string.ph_confirm_yes), onClick = onYes)
                MetroButton(stringResource(R.string.ph_cancel), onClick = onNo)
            }
        }
    }
}

/** The switches of the hub (the "⋯" button of its bar), the space between pictures and the grouping of the dates. */
@Composable
internal fun OptionsPanel(prefs: PhotoPrefs, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    MiniFrame {
        MetroText(stringResource(R.string.ph_options_title), size = 36.sp, modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp).androidx_testTag("photos:options"))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PhotoOption.entries.forEach { option -> MetroToggle(stringResource(optionLabel(option)), prefs[option], { prefs.set(option, it) }) }
            MetroText(stringResource(R.string.ph_gap), size = 18.sp, color = LocalMetroColors.current.subtle, modifier = Modifier.padding(top = 12.dp))
            ChoiceRow(PhotoPrefs.GAPS.map { "$it" }, PhotoPrefs.GAPS.indexOf(prefs.gap).coerceAtLeast(0), { prefs.gap = PhotoPrefs.GAPS[it] })
            MetroText(stringResource(R.string.ph_group), size = 18.sp, color = LocalMetroColors.current.subtle, modifier = Modifier.padding(top = 12.dp))
            ChoiceRow(Granularity.entries.map { stringResource(granularityLabel(it)) }, prefs.granularity.ordinal, { prefs.granularity = Granularity.entries[it] })
        }
        MetroButton(stringResource(R.string.ph_done), Modifier.padding(24.dp), onClick = onClose)
    }
}

internal fun granularityLabel(g: Granularity) = when (g) { Granularity.Day -> R.string.ph_group_day; Granularity.Month -> R.string.ph_group_month; Granularity.Year -> R.string.ph_group_year }

internal fun optionLabel(option: PhotoOption): Int = when (option) {
    PhotoOption.Select -> R.string.ph_opt_select
    PhotoOption.FavoriteEdit -> R.string.ph_opt_favorite_edit
    PhotoOption.Search -> R.string.ph_opt_search
    PhotoOption.KeepRatio -> R.string.ph_opt_keep_ratio
    PhotoOption.DateScrubber -> R.string.ph_opt_date_scrubber
    PhotoOption.Mosaic -> R.string.ph_opt_mosaic
    PhotoOption.CameraFirst -> R.string.ph_opt_camera_first
    PhotoOption.Memories -> R.string.ph_opt_memories
    PhotoOption.ViewerFx -> R.string.ph_opt_viewer_fx
    PhotoOption.Immersive -> R.string.ph_opt_immersive
    PhotoOption.SwipeClose -> R.string.ph_opt_swipe_close
    PhotoOption.Filmstrip -> R.string.ph_opt_filmstrip
    PhotoOption.AdaptiveBackground -> R.string.ph_opt_adaptive_background
    PhotoOption.Extras -> R.string.ph_opt_extras
    PhotoOption.StripMetadata -> R.string.ph_opt_strip_metadata
    PhotoOption.Duplicates -> R.string.ph_opt_duplicates
    PhotoOption.Hints -> R.string.ph_opt_hints
    PhotoOption.FavoriteMark -> R.string.ph_opt_favorite_mark
    PhotoOption.NeutralPlaceholder -> R.string.ph_opt_neutral_placeholder
}
