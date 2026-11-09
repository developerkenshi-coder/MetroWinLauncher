package com.metrowin.launcher.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.theme.LocalMetroColors

/**
 * The heading of a section of a list (a letter, a day, "late") that stays at the top while the rows of its section scroll under
 * it. Every list of the launcher and of the mini apps with sections uses this one, so they all stick the same way. With
 * [onClick] a touch on it does something (the lists with letters open the jump list).
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.sectionHeader(key: Any, onClick: (() -> Unit)? = null, animated: Boolean = false, content: @Composable () -> Unit) {
    stickyHeader(key = key, contentType = "section") {
        Box(
            Modifier
                // The heading glides to its new place when the order of the list changes.
                .then(if (animated) Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = ReorderSpring) else Modifier)
                .fillMaxWidth()
                // Opaque enough that the rows under it do not read through the heading.
                .background(LocalMetroColors.current.background.copy(alpha = STRIP_ALPHA))
                .then(if (onClick != null) Modifier.metroPress(onClick = onClick) else Modifier),
        ) { content() }
    }
}

private const val STRIP_ALPHA = 0.96f

/** How the rows of a list glide to their places when the order changes: soft, no bounce. */
val ReorderSpring = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntOffset>(stiffness = androidx.compose.animation.core.Spring.StiffnessLow)

/** Where each section starts in a list: [sizes] are the rows of every section, each with one heading before them, from item [first]. */
fun sectionStarts(sizes: List<Int>, first: Int = 0): List<Int> {
    var next = first
    return sizes.map { size -> next.also { next += 1 + size } }
}
