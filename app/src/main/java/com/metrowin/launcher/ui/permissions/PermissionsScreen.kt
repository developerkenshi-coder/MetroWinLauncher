package com.metrowin.launcher.ui.permissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.LivePermissions
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.MetroBlack
import com.metrowin.launcher.ui.theme.MetroSubtle
import com.metrowin.launcher.ui.theme.MetroWhite

/**
 * First-run page in the Windows Phone style (small caps header, big lowercase title). It explains
 * what each permission is for and asks for all of them at once. Every one of them is optional.
 */
@Composable
fun PermissionsScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onFinished()
    }
    Box(
        modifier
            .fillMaxSize()
            .background(MetroBlack)
            // Swallow touches so nothing underneath reacts while this page is up.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(28.dp))
                MetroText(stringResource(R.string.perm_header), size = 14.sp, weight = FontWeight.SemiBold)
                MetroText(
                    stringResource(R.string.perm_title),
                    size = 48.sp,
                    color = LocalAccent.current,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                MetroText(
                    stringResource(R.string.perm_intro),
                    size = 17.sp,
                    color = MetroSubtle,
                    maxLines = Int.MAX_VALUE,
                    lineHeight = 24.sp,
                )
                Spacer(Modifier.height(24.dp))
                PermissionRow(Glyph.Photos, R.string.perm_photos, R.string.perm_photos_why)
                PermissionRow(Glyph.Calendar, R.string.perm_calendar, R.string.perm_calendar_why)
                PermissionRow(Glyph.People, R.string.perm_contacts, R.string.perm_contacts_why)
                PermissionRow(Glyph.Phone, R.string.perm_calls, R.string.perm_calls_why)
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetroButton(stringResource(R.string.perm_allow), Modifier.weight(1f)) {
                    request.launch(LivePermissions.all.toTypedArray())
                }
                MetroButton(stringResource(R.string.perm_skip), Modifier.weight(1f), onClick = onFinished)
            }
        }
    }
}

@Composable
private fun PermissionRow(glyph: Glyph, title: Int, why: Int) {
    Row(Modifier.fillMaxWidth().padding(bottom = 22.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(52.dp).background(LocalAccent.current), contentAlignment = Alignment.Center) {
            GlyphIcon(glyph, 32.dp)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            MetroText(stringResource(title), size = 24.sp)
            MetroText(
                stringResource(why),
                size = 15.sp,
                color = MetroSubtle,
                maxLines = Int.MAX_VALUE,
                lineHeight = 21.sp,
                weight = FontWeight.Normal,
            )
        }
    }
}

/** Windows Phone buttons are plain outlined rectangles. */
@Composable
private fun MetroButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .metroPress(onClick = onClick)
            .border(2.dp, MetroWhite)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(text, size = 20.sp, weight = FontWeight.Normal)
    }
}
