package com.metrowin.launcher.data

/** Telling a double press of the Home button from two separate ones. Pure. */
object HomePresses {
    /** The most time between two presses that still make a double one. */
    const val DOUBLE_MS = 450L

    /** Whether a press at [now] follows one at [previous] closely enough (0 or less: there was no earlier press). */
    fun isDouble(previous: Long, now: Long, window: Long = DOUBLE_MS): Boolean = previous > 0L && now - previous in 0..window
}

/** The languages the launcher can be switched to (Android 13 and later), as language tags; the empty one follows the phone. */
object AppLanguages {
    val tags = listOf("", "en", "it")

    /** The tag of [language] among [tags] (the empty one for any language the launcher does not have). */
    fun normalize(language: String?): String = tags.firstOrNull { it.isNotEmpty() && it == language?.lowercase()?.substringBefore('-') } ?: ""
}
