package com.metrowin.launcher.data

import org.json.JSONObject

/**
 * Putting the settings of one page back to what they were at the start, and nothing else. A page is listed by the names its
 * settings have in the saved file (see [SettingsCodec]); what the user keeps on it as data of their own (the hidden and locked apps,
 * the volume presets, the number of Start screens) is left alone. Pure, so it is tested.
 */
object SectionReset {
    /** The pages that can be reset, by the name of the section, and the settings of each. */
    val keys: Map<String, Set<String>> = mapOf(
        "StartTheme" to setOf(
            "accentApps", "accentIndex", "accentNightIndex", "accentPanel", "accentStart", "autoAccent", "customAccent", "darkFromHour", "darkToHour",
            "dynamicColor", "oledMode", "pageBackgrounds", "panoramaTitles", "parallax", "startPhotoFit", "startPhotoX", "startPhotoY", "startPhotoZoom",
            "themeMode", "themeStyle",
        ),
        "Start" to setOf(
            "appBar", "batteryEstimate", "calendarAgenda", "clockAlarm", "clockHours", "clockTaps", "clockWeek", "clockZone", "collectionTiles",
            "groupTitles", "hideStatusBar", "largeTiles", "layoutSchedule", "lockStart", "moreTiles", "showClock", "showTileNames", "startLowerPercent",
            "startTopAfterMin", "stepGoal", "tileMusicControls", "tileSpacing", "undoSteps",
        ),
        "Search" to setOf(
            "rememberSearches", "searchAnswers", "searchAssistant", "searchCalendar", "searchCommands", "searchContacts", "searchCurrency", "searchEngine",
            "searchFileTypes", "searchLauncherSettings", "searchNotifications", "searchOperators", "searchSettings", "searchSmartDial", "searchTiles",
            "searchToday", "searchWeb", "tipOfDay", "todayAlarm", "todayDate", "todayNear", "todayWeather",
        ),
        "Gestures" to setOf(
            "animationSpeed", "doubleTapAction", "doubleTapApp", "edgeBackPan", "edgePull", "homeDoubleAction", "homeDoubleApp", "tapHaptics", "tapSound",
            "tiltStrength", "twoFingerDownAction", "twoFingerDownApp", "twoFingerUpAction", "twoFingerUpApp", "vibrationStrength",
        ),
        "Actions" to setOf(
            "actionSliders", "clearOnOpen", "dndMinutes", "edgeActionCenter", "groupConversations", "hideSystemNotifications", "inlineReply", "metroSounds", "muteFromPanel",
            "notifFilterOn", "notifFilterWords", "notifPriority", "notificationPeek", "overlayBanner", "quickActions", "toastBanner", "torchAutoOffMin",
            "panelSounds", "torchStrength", "volumePanel", "volumeProfilesShow", "vpnApp",
        ),
        "LiveTiles" to setOf(
            "liveHideText", "liveMessages", "liveNowPlaying", "liveOffApps", "livePictures", "liveProgress", "notificationPeek", "thirdPartyTiles",
            "galleryAlbums", "galleryApp", "weatherBlocks", "weatherFahrenheit",
        ),
        "Apps" to setOf("appLabels", "compactList", "showCategories", "showFrequentApps", "showNewApps"),
        "Screen" to setOf("highRefreshRate", "launchAnimation", "orientation"),
        "Battery" to setOf("batterySaving"),
        "Accessibility" to setOf("boldText", "highContrast", "reduceMotion", "systemFont", "textScale"),
        "Icons" to setOf("iconPack", "iconShape", "iconTileColors"),
        "Lock" to setOf("lockShowAlarm", "lockShowBattery", "lockShowEvent", "lockShowNotifications", "lockShowWeather"),
        "Voice" to setOf("assistantIcon", "voiceButton", "voiceCallDirect", "voiceOffline"),
    )

    fun canReset(section: String) = section in keys

    private val defaults: JSONObject by lazy { JSONObject(SettingsCodec.encode(LauncherSettings())) }

    /** The page has a setting that is not what it was at the start. */
    fun changed(settings: LauncherSettings, section: String): Boolean {
        val now = JSONObject(SettingsCodec.encode(settings))
        return keys[section].orEmpty().any { key -> defaults.has(key) && now.opt(key).toString() != defaults.opt(key).toString() }
    }

    /** [settings] with the settings of the page put back; everything else as it is. */
    fun reset(settings: LauncherSettings, section: String): LauncherSettings {
        val now = JSONObject(SettingsCodec.encode(settings))
        keys[section].orEmpty().forEach { key -> if (defaults.has(key)) now.put(key, defaults.get(key)) }
        return SettingsCodec.decode(now.toString(), base = settings) ?: settings
    }
}
