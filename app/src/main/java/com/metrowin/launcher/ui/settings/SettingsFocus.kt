package com.metrowin.launcher.ui.settings

import com.metrowin.launcher.ui.anim.metroTween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import com.metrowin.launcher.ui.theme.LocalAccent

/** The setting a page was opened for (an id of the [SettingsIndex]), or null. Given by the section page to the page inside it. */
val LocalSettingsTarget = compositionLocalOf<String?> { null }

/** The focus of the page that is on screen: where each setting is, and which one to scroll to and light up. Null outside a settings page. */
internal val LocalSettingsFocus = compositionLocalOf<SettingsFocus?> { null }

/** What a setting says to the semantics tree: the id it has in the [SettingsIndex]. Tests read it to check that the index and the pages agree. */
val SettingAnchorKey = SemanticsPropertyKey<String>("SettingAnchor")
private var SemanticsPropertyReceiver.settingAnchor by SettingAnchorKey

private const val FRAME_MS = 32L
private const val LAYOUT_WAIT_FRAMES = 40

/**
 * Where the settings of a page are, so that the page can scroll to the one that was searched for and light it up for a moment.
 * The tops are kept relative to the start of the scrolled content, so they stay right while the page moves.
 */
@Stable
internal class SettingsFocus(val target: String?, val scroll: ScrollState) {
    // Not state: every scroll reports every setting again, and nothing needs to be recomposed for that.
    private val tops = HashMap<String, Float>()
    private var viewportTop = 0f

    /** 1 when the target has just been reached, down to 0: how strongly it is lit. */
    val glow = Animatable(0f)

    fun viewport(coordinates: LayoutCoordinates) {
        viewportTop = coordinates.positionInRoot().y
    }

    fun report(id: String, coordinates: LayoutCoordinates) {
        tops[id] = coordinates.positionInRoot().y - viewportTop + scroll.value
    }

    /** Where the target starts in the scrolled content, or null while it has not been laid out (or is not on this page). */
    fun targetTop(): Float? = target?.let { tops[it] }

    /** Waits for the target to be laid out (the page is still being built when it opens), scrolls to it and lights it. */
    suspend fun reveal(marginPx: Int) {
        var top = targetTop()
        var waited = 0
        while (top == null && waited < LAYOUT_WAIT_FRAMES) {
            delay(FRAME_MS)
            waited++
            top = targetTop()
        }
        if (top == null) return
        scroll.animateScrollTo((top.toInt() - marginPx).coerceAtLeast(0), metroTween(350))
        glow.snapTo(1f)
        glow.animateTo(0f, metroTween(1_600))
    }
}

/**
 * Marks a control as the setting [id]: the page can scroll to it, and it is lit for a moment when it is the one that was asked
 * for. Nothing is done for a null id.
 */
@Composable
fun Modifier.settingAnchor(id: String?): Modifier {
    if (id == null) return this
    val focus = LocalSettingsFocus.current
    val accent = LocalAccent.current
    val marked = this.semantics { settingAnchor = id }
    if (focus == null) return marked
    val isTarget = focus.target == id
    return marked
        .onGloballyPositioned { focus.report(id, it) }
        .then(
            if (isTarget) {
                Modifier.drawWithContent {
                    val strength = focus.glow.value
                    if (strength > 0f) drawRect(accent.copy(alpha = 0.28f * strength))
                    drawContent()
                }
            } else {
                Modifier
            },
        )
}

@Composable
internal fun rememberSettingsFocus(scroll: ScrollState): SettingsFocus {
    val target = LocalSettingsTarget.current
    return remember(target, scroll) { SettingsFocus(target, scroll) }
}
