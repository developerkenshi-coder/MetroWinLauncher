package com.metrowin.launcher.people

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.Mark
import com.metrowin.launcher.ui.components.MarkIcon
import com.metrowin.launcher.ui.theme.LocalMetroColors

/**
 * The little pictures of the People app: the thin line glyphs and marks of the launcher (the glyphs ☎ ✉ ← are not in every font,
 * and TalkBack reads them letter by letter).
 */
enum class PeopleGlyph(val glyph: Glyph? = null, val mark: Mark? = null) {
    Phone(glyph = Glyph.Phone),
    Message(glyph = Glyph.Messaging),
    Mail(glyph = Glyph.Mail),
    Star(mark = Mark.StarFill),
    StarOutline(mark = Mark.Star),
    Person(glyph = Glyph.People),
    Edit(mark = Mark.Edit),
    Share(mark = Mark.Share),
    CallOut(mark = Mark.Open),
    CallIn(mark = Mark.CallIn),
    CallMissed(mark = Mark.CallMissed),
    Bell(glyph = Glyph.Bell),
    Gift(mark = Mark.Gift),
    Pin(mark = Mark.Pin),
    Close(mark = Mark.Close),
}

/** A [PeopleGlyph] at [size], in [color] (the text colour of the page when none is given). */
@Composable
fun PeopleIcon(glyph: PeopleGlyph, size: Dp, modifier: Modifier = Modifier, color: Color = LocalMetroColors.current.foreground) {
    glyph.glyph?.let { GlyphIcon(it, size, modifier, color) } ?: MarkIcon(glyph.mark!!, size, modifier, color)
}
