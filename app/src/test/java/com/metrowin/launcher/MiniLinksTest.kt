package com.metrowin.launcher

import android.content.Intent
import com.metrowin.launcher.data.MiniLink
import com.metrowin.launcher.data.ShortcutCodec
import com.metrowin.launcher.data.ShortcutDef
import com.metrowin.launcher.data.ShortcutKind
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.sharein.SharedIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MiniLinksTest {
    @Test
    fun aNoteKeptOnStartOpensTheNoteAndSurvivesASave() {
        val tile = ShortcutDef("shortcut:notelink:1", ShortcutKind.NoteLink, "Shopping", MiniLink.note("abc"))
        val back = ShortcutCodec.decode(ShortcutCodec.encode(listOf(tile)))!!.single()
        assertEquals(ShortcutKind.NoteLink, back.kind)
        assertEquals("abc", MiniLink.parse((back.toItem(null).launch as Launch.Link).uri)?.second)
    }

    @Test
    fun sharedTextIsTheTitleWhenThereIsNoSubject() {
        val text = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "\nMilk and bread\nand eggs")
        assertEquals("Milk and bread", SharedIn.of(text).title)
        assertEquals("Shopping", SharedIn.of(text.putExtra(Intent.EXTRA_SUBJECT, " Shopping ")).title)
        assertTrue(SharedIn.of(Intent(Intent.ACTION_SEND)).pictures.isEmpty())
    }
}
