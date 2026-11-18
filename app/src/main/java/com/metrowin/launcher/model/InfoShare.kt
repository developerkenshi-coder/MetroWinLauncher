package com.metrowin.launcher.model

/**
 * A super tile as a piece of text that can be sent to someone and used by them: which faces, in which order, and every choice about them. What
 * only means something on this phone (the album, the pictures, the calendars and the apps picked, the home zone, the widget and the published tile) is left out.
 * Pure, so it is tested.
 */
object InfoShare {
    const val PREFIX = "MWINFO1:"

    fun toCode(config: InfoConfig): String =
        PREFIX + config.copy(
            album = "", albumName = "", calendars = emptyList(), excluded = emptyList(), picked = emptyList(), homeZone = "", published = "", widget = 0,
        ).encode()

    /** The tile a code stands for, or null when [text] is not one of ours. */
    fun fromCode(text: String): InfoConfig? {
        val code = text.trim()
        return if (code.startsWith(PREFIX) && code.length > PREFIX.length) InfoConfig.decode(code.removePrefix(PREFIX)) else null
    }
}
