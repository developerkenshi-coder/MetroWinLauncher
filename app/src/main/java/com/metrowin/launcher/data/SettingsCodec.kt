package com.metrowin.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * [LauncherSettings] as JSON, kept apart from Android storage so it can be unit tested. It is what the
 * launcher saves, and what backups, syncing and theme files carry. Unknown keys are ignored and missing
 * ones take their default, so files from older and newer versions both load.
 */
object SettingsCodec {
    /**
     * With [portable] the settings that belong to this phone alone are left out (the backup folder, the sync
     * passphrase and device id, the last backup time, the weather position, whether the Kid's Corner is on): a
     * file that is shared or copied to another phone must not carry them.
     */
    fun encode(s: LauncherSettings, portable: Boolean = false): String = JSONObject().apply {
        put("themeMode", s.themeMode.code)
        put("darkFromHour", s.darkFromHour)
        put("darkToHour", s.darkToHour)
        put("accentIndex", s.accentIndex)
        put("autoAccent", s.autoAccent)
        put("moreTiles", s.moreTiles)
        put("parallax", s.parallax)
        put("startPages", s.startPages)
        put("showClock", s.showClock)

        put("orientation", s.orientation.code)
        put("highRefreshRate", s.highRefreshRate)
        put("launchAnimation", s.launchAnimation.code)

        put("edgePull", s.edgePull)
        put("edgeBackPan", s.edgeBackPan)
        put("doubleTapAction", s.doubleTapAction.code)
        put("twoFingerDownAction", s.twoFingerDownAction.code)
        put("twoFingerUpAction", s.twoFingerUpAction.code)
        put("tapSound", s.tapSound)
        put("tapHaptics", s.tapHaptics)

        put("quickActions", JSONArray(s.quickActions))
        put("notificationPeek", s.notificationPeek)
        put("inlineReply", s.inlineReply)
        put("groupConversations", s.groupConversations)

        put("weatherCity", s.weatherCity)
        put("weatherFahrenheit", s.weatherFahrenheit)
        put("thirdPartyTiles", s.thirdPartyTiles)

        put("hiddenApps", JSONArray(s.hiddenApps.sorted()))
        put("searchContacts", s.searchContacts)
        put("searchSettings", s.searchSettings)
        put("searchWeb", s.searchWeb)
        put("iconPack", s.iconPack)

        put("showWorkApps", s.showWorkApps)
        put("showPrivateApps", s.showPrivateApps)
        put("lockShowAlarm", s.lockShowAlarm)
        put("lockShowNotifications", s.lockShowNotifications)
        put("lockShowBattery", s.lockShowBattery)
        put("voiceOffline", s.voiceOffline)
        put("voiceButton", s.voiceButton)
        put("reduceMotion", s.reduceMotion)
        put("highContrast", s.highContrast)

        put("backupEveryDays", s.backupEveryDays)
        put("syncEnabled", s.syncEnabled)

        put("kidsApps", JSONArray(s.kidsApps.sorted()))

        if (!portable) {
            // Whether the Kid's Corner is on is a state of this phone, not a preference to carry over.
            put("kidsMode", s.kidsMode)
            // The photos are files of this phone: a copy would point at pictures that are not there.
            put("backgroundRevision", s.backgroundRevision)
            put("lockPhotoRevision", s.lockPhotoRevision)
            if (!s.weatherLatitude.isNaN()) put("weatherLatitude", s.weatherLatitude)
            if (!s.weatherLongitude.isNaN()) put("weatherLongitude", s.weatherLongitude)
            put("backupFolder", s.backupFolder)
            put("lastBackupAt", s.lastBackupAt)
            put("syncPassphrase", s.syncPassphrase)
            put("deviceId", s.deviceId)
            put("syncLastHash", s.syncLastHash)
            put("syncLastStamp", s.syncLastStamp)
            put("syncLastAt", s.syncLastAt)
        }
    }.toString()

    /** Null when [json] is not JSON at all. [base] supplies the values the file leaves out. */
    fun decode(json: String, base: LauncherSettings = LauncherSettings()): LauncherSettings? = runCatching {
        val o = JSONObject(json)
        fun strings(key: String, default: Collection<String>): List<String> =
            o.optJSONArray(key)?.let { a -> List(a.length()) { a.getString(it) } } ?: default.toList()

        LauncherSettings(
            themeMode = if (o.has("themeMode")) ThemeMode.fromCode(o.optString("themeMode")) else base.themeMode,
            darkFromHour = o.optInt("darkFromHour", base.darkFromHour).coerceIn(0, 23),
            darkToHour = o.optInt("darkToHour", base.darkToHour).coerceIn(0, 23),
            accentIndex = o.optInt("accentIndex", base.accentIndex).coerceIn(0, ACCENT_COUNT - 1),
            autoAccent = o.optBoolean("autoAccent", base.autoAccent),
            moreTiles = o.optBoolean("moreTiles", base.moreTiles),
            backgroundRevision = o.optInt("backgroundRevision", base.backgroundRevision).coerceAtLeast(0),
            parallax = o.optBoolean("parallax", base.parallax),
            startPages = o.optInt("startPages", base.startPages).coerceIn(1, LauncherSettings.MAX_START_PAGES),
            showClock = o.optBoolean("showClock", base.showClock),

            orientation = if (o.has("orientation")) OrientationMode.fromCode(o.optString("orientation")) else base.orientation,
            highRefreshRate = o.optBoolean("highRefreshRate", base.highRefreshRate),
            launchAnimation = if (o.has("launchAnimation")) LaunchAnimation.fromCode(o.optString("launchAnimation")) else base.launchAnimation,

            edgePull = o.optBoolean("edgePull", base.edgePull),
            edgeBackPan = o.optBoolean("edgeBackPan", base.edgeBackPan),
            doubleTapAction = GestureAction.fromCode(o.optString("doubleTapAction").ifEmpty { null }, base.doubleTapAction),
            twoFingerDownAction = GestureAction.fromCode(o.optString("twoFingerDownAction").ifEmpty { null }, base.twoFingerDownAction),
            twoFingerUpAction = GestureAction.fromCode(o.optString("twoFingerUpAction").ifEmpty { null }, base.twoFingerUpAction),
            tapSound = o.optBoolean("tapSound", base.tapSound),
            tapHaptics = o.optBoolean("tapHaptics", base.tapHaptics),

            quickActions = QuickAction.slots(QuickAction.migrate(strings("quickActions", base.quickActions))).map { it.id },
            notificationPeek = o.optBoolean("notificationPeek", base.notificationPeek),
            inlineReply = o.optBoolean("inlineReply", base.inlineReply),
            groupConversations = o.optBoolean("groupConversations", base.groupConversations),

            weatherCity = o.optString("weatherCity", base.weatherCity),
            weatherLatitude = if (o.has("weatherLatitude")) o.optDouble("weatherLatitude") else base.weatherLatitude,
            weatherLongitude = if (o.has("weatherLongitude")) o.optDouble("weatherLongitude") else base.weatherLongitude,
            weatherFahrenheit = o.optBoolean("weatherFahrenheit", base.weatherFahrenheit),
            thirdPartyTiles = o.optBoolean("thirdPartyTiles", base.thirdPartyTiles),

            hiddenApps = strings("hiddenApps", base.hiddenApps).toSet(),
            searchContacts = o.optBoolean("searchContacts", base.searchContacts),
            searchSettings = o.optBoolean("searchSettings", base.searchSettings),
            searchWeb = o.optBoolean("searchWeb", base.searchWeb),
            iconPack = o.optString("iconPack", base.iconPack),

            showWorkApps = o.optBoolean("showWorkApps", base.showWorkApps),
            showPrivateApps = o.optBoolean("showPrivateApps", base.showPrivateApps),
            lockPhotoRevision = o.optInt("lockPhotoRevision", base.lockPhotoRevision).coerceAtLeast(0),
            lockShowAlarm = o.optBoolean("lockShowAlarm", base.lockShowAlarm),
            lockShowNotifications = o.optBoolean("lockShowNotifications", base.lockShowNotifications),
            lockShowBattery = o.optBoolean("lockShowBattery", base.lockShowBattery),
            voiceOffline = o.optBoolean("voiceOffline", base.voiceOffline),
            voiceButton = o.optBoolean("voiceButton", base.voiceButton),
            reduceMotion = o.optBoolean("reduceMotion", base.reduceMotion),
            highContrast = o.optBoolean("highContrast", base.highContrast),

            backupFolder = o.optString("backupFolder", base.backupFolder),
            backupEveryDays = o.optInt("backupEveryDays", base.backupEveryDays).coerceIn(0, 365),
            lastBackupAt = o.optLong("lastBackupAt", base.lastBackupAt),
            syncEnabled = o.optBoolean("syncEnabled", base.syncEnabled),
            syncPassphrase = o.optString("syncPassphrase", base.syncPassphrase),
            deviceId = o.optString("deviceId", base.deviceId),
            syncLastHash = o.optString("syncLastHash", base.syncLastHash),
            syncLastStamp = o.optLong("syncLastStamp", base.syncLastStamp),
            syncLastAt = o.optLong("syncLastAt", base.syncLastAt),

            kidsApps = strings("kidsApps", base.kidsApps).toSet(),
            kidsMode = o.optBoolean("kidsMode", base.kidsMode),
        )
    }.getOrNull()

    private const val ACCENT_COUNT = 20
}
