package com.metrowin.launcher.data

import androidx.annotation.StringRes
import com.metrowin.launcher.R

/** How the launcher looks, besides its colors: the shapes, the surfaces and the type. */
enum class ThemeStyle(val code: String, @param:StringRes val label: Int, @param:StringRes val description: Int) {
    /** The flat tiles and the light type of Windows Phone, as the launcher always looked. */
    Classic("classic", R.string.style_classic, R.string.style_classic_desc),

    /** Frosted, rounded glass over a glowing backdrop, with light rims and soft highlights. */
    Glass("glass", R.string.style_glass, R.string.style_glass_desc),

    /** Material You: tonal colors (the wallpaper's, from Android 12), large rounded shapes, filled buttons. */
    Droid("droid", R.string.style_droid, R.string.style_droid_desc);

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: Classic
    }
}
