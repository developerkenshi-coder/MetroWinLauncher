package com.metrowin.launcher.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.metrowin.launcher.R
import java.io.File

/**
 * The fonts the user can pick besides Open Sans (the font of the look) and the one of the phone: twenty variable fonts from the Google
 * Fonts collection (SIL Open Font License, see THIRD_PARTY_NOTICES.md), and one file of the user's own. The code is what the settings keep.
 */
object AppFonts {
    /** The code of the font the user brought. */
    const val CUSTOM = "custom"

    class Entry(val code: String, val label: String, @param:FontRes val res: Int)

    val all: List<Entry> = listOf(
        Entry("comfortaa", "Comfortaa", R.font.font_comfortaa),
        Entry("dosis", "Dosis", R.font.font_dosis),
        Entry("exo2", "Exo 2", R.font.font_exo2),
        Entry("figtree", "Figtree", R.font.font_figtree),
        Entry("jetbrains", "JetBrains Mono", R.font.font_jetbrains_mono),
        Entry("josefin", "Josefin Sans", R.font.font_josefin_sans),
        Entry("jost", "Jost", R.font.font_jost),
        Entry("karla", "Karla", R.font.font_karla),
        Entry("lexend", "Lexend", R.font.font_lexend),
        Entry("lora", "Lora", R.font.font_lora),
        Entry("manrope", "Manrope", R.font.font_manrope),
        Entry("mulish", "Mulish", R.font.font_mulish),
        Entry("nunito", "Nunito", R.font.font_nunito),
        Entry("oswald", "Oswald", R.font.font_oswald),
        Entry("outfit", "Outfit", R.font.font_outfit),
        Entry("quicksand", "Quicksand", R.font.font_quicksand),
        Entry("raleway", "Raleway", R.font.font_raleway),
        Entry("sora", "Sora", R.font.font_sora),
        Entry("spacegrotesk", "Space Grotesk", R.font.font_space_grotesk),
        Entry("urbanist", "Urbanist", R.font.font_urbanist),
    )

    /** The weights the launcher asks for (Light, Regular, SemiBold and Bold); a variable font gives each from its one file. */
    private val weights = listOf(300, 400, 600, 700)

    private val families = HashMap<String, FontFamily>()

    /** Where the font the user brought is kept. */
    fun customFile(context: Context) = File(context.filesDir, "fonts/custom.ttf")

    /** The family for [code]; null for the font of the look ("" or a code this version does not know). */
    @OptIn(ExperimentalTextApi::class)
    fun family(context: Context, code: String, revision: Int): FontFamily? {
        if (code == CUSTOM) return custom(customFile(context), revision)
        val entry = all.firstOrNull { it.code == code } ?: return null
        return families.getOrPut(code) {
            FontFamily(weights.map { Font(entry.res, FontWeight(it), variationSettings = FontVariation.Settings(FontVariation.weight(it))) })
        }
    }

    private fun custom(file: File, revision: Int): FontFamily? {
        if (!file.isFile) return null
        return families.getOrPut("$CUSTOM$revision") {
            // A file that cannot be read as a font gives no family, and the look keeps its own.
            val typeface = runCatching { Typeface.createFromFile(file) }.getOrNull() ?: return null
            FontFamily(typeface)
        }
    }

    /** True when [head] (the first bytes of a file) is the start of a TrueType, OpenType or font collection file. */
    fun looksLikeFont(head: ByteArray): Boolean {
        if (head.size < 4) return false
        val tag = String(head, 0, 4, Charsets.ISO_8859_1)
        return tag == "OTTO" || tag == "true" || tag == "ttcf" || (head[0].toInt() == 0 && head[1].toInt() == 1 && head[2].toInt() == 0 && head[3].toInt() == 0)
    }

    /** A font file is a few hundred kilobytes, a big one with many scripts some megabytes: more than this is not one the launcher wants. */
    const val MAX_BYTES = 20L * 1024 * 1024
}
