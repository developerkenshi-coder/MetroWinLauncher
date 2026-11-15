package com.metrowin.launcher.ui.settings

import com.metrowin.launcher.R

/** The id of each setting (see [SettingsIndex]) and the text that says what it is for. One row for a setting, or one text for a family of alike ones. Written with the strings of strings_settings_info.xml. */
internal object SettingsInfoTable {
    val byId: Map<String, Int> = mapOf(
    )

    /** The text of the settings that are made from a list (the switches of a mini app, the blocks of the weather tile), by the start of their id. */
    val byPrefix: Map<String, Int> = mapOf(
        "mini." to R.string.info_mini_option,
        "live.wx." to R.string.info_live_weather_block,
    )
}
