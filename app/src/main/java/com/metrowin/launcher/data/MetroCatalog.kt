package com.metrowin.launcher.data

import android.content.Intent
import android.provider.MediaStore
import android.provider.Settings
import androidx.annotation.StringRes
import com.metrowin.launcher.R
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.LiveKind

/** How to find the Android app that plays the role of a Windows Phone stock app. */
sealed interface Target {
    /** A specific app, e.g. the Microsoft Office apps. */
    data class Pkg(val name: String) : Target

    /** Apps registered for `ACTION_MAIN` + an `Intent.CATEGORY_APP_*` category. */
    data class Category(val category: String) : Target

    /** Apps handling an intent. [defaultOnly] keeps only the one the user picked as default. */
    data class Action(val action: String, val data: String? = null, val defaultOnly: Boolean = false) : Target
}

/**
 * A Windows Phone 8.1 stock app.
 *
 * It only shows up in the app list when the device has an app that can play its role ([targets],
 * tried in order), or, for [settingsAction], when that system screen exists. When it does, the
 * stock entry replaces the Android app in the list, with the Windows Phone name and Metro glyph.
 */
data class StockApp(
    val id: String,
    @param:StringRes val label: Int,
    val glyph: Glyph,
    val targets: List<Target> = emptyList(),
    val settingsAction: String? = null,
    val live: LiveKind? = null,
)

/** The stock app list of Windows Phone 8.1 (alphabetical order comes from the app list, not here). */
object MetroCatalog {
    private fun category(c: String) = Target.Category(c)
    private fun pkg(vararg names: String) = names.map { Target.Pkg(it) }

    val apps: List<StockApp> = listOf(
        StockApp(
            "phone", R.string.stock_phone, Glyph.Phone,
            listOf(Target.Action(Intent.ACTION_DIAL, "tel:")) +
                pkg("com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer"),
            live = LiveKind.Phone,
        ),
        StockApp(
            "people", R.string.stock_people, Glyph.People,
            listOf(category(Intent.CATEGORY_APP_CONTACTS)) +
                pkg("com.google.android.contacts", "com.samsung.android.app.contacts", "com.android.contacts"),
            live = LiveKind.People,
        ),
        StockApp(
            "messaging", R.string.stock_messaging, Glyph.Messaging,
            listOf(category(Intent.CATEGORY_APP_MESSAGING)) +
                pkg("com.google.android.apps.messaging", "com.samsung.android.messaging"),
            live = LiveKind.Messages,
        ),
        StockApp(
            "mail", R.string.stock_mail, Glyph.Mail,
            listOf(category(Intent.CATEGORY_APP_EMAIL)) + pkg("com.google.android.gm", "com.microsoft.office.outlook"),
            live = LiveKind.Messages,
        ),
        StockApp(
            "calendar", R.string.stock_calendar, Glyph.Calendar,
            listOf(category(Intent.CATEGORY_APP_CALENDAR)) +
                pkg("com.google.android.calendar", "com.samsung.android.calendar"),
            live = LiveKind.Calendar,
        ),
        StockApp(
            "camera", R.string.stock_camera, Glyph.Camera,
            listOf(Target.Action(MediaStore.ACTION_IMAGE_CAPTURE)) +
                pkg("com.google.android.GoogleCamera", "com.sec.android.app.camera", "com.android.camera2"),
        ),
        StockApp(
            "photos", R.string.stock_photos, Glyph.Photos,
            listOf(category(Intent.CATEGORY_APP_GALLERY)) +
                pkg("com.google.android.apps.photos", "com.sec.android.gallery3d", "com.miui.gallery"),
            live = LiveKind.Photos,
        ),
        StockApp(
            "internetexplorer", R.string.stock_internetexplorer, Glyph.Browser,
            listOf(
                category(Intent.CATEGORY_APP_BROWSER),
                Target.Action(Intent.ACTION_VIEW, "https://www.example.com", defaultOnly = true),
            ),
        ),
        StockApp(
            "music", R.string.stock_music, Glyph.Music,
            listOf(category(Intent.CATEGORY_APP_MUSIC)) +
                pkg("com.google.android.apps.youtube.music", "com.spotify.music"),
            live = LiveKind.Music,
        ),
        StockApp(
            "video", R.string.stock_video, Glyph.Video,
            pkg("com.google.android.videos", "com.samsung.android.video", "org.videolan.vlc"),
        ),
        StockApp(
            "maps", R.string.stock_maps, Glyph.Maps,
            listOf(category(Intent.CATEGORY_APP_MAPS)) + pkg("com.google.android.apps.maps"),
        ),
        StockApp(
            "store", R.string.stock_store, Glyph.Store,
            pkg("com.android.vending", "com.sec.android.app.samsungapps", "com.huawei.appmarket"),
        ),
        StockApp(
            "settings", R.string.stock_settings, Glyph.Settings,
            listOf(Target.Action(Settings.ACTION_SETTINGS)) + pkg("com.android.settings"),
        ),
        StockApp(
            "alarms", R.string.stock_alarms, Glyph.Alarm,
            // AlarmClock.ACTION_SHOW_ALARMS
            listOf(Target.Action("android.intent.action.SHOW_ALARMS")) +
                pkg("com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage"),
        ),
        StockApp(
            "calculator", R.string.stock_calculator, Glyph.Calculator,
            listOf(category(Intent.CATEGORY_APP_CALCULATOR)) +
                pkg("com.google.android.calculator", "com.sec.android.app.popupcalculator", "com.android.calculator2"),
        ),
        StockApp(
            "files", R.string.stock_files, Glyph.Files,
            pkg(
                "com.google.android.apps.nbu.files", "com.google.android.documentsui", "com.sec.android.app.myfiles",
                "com.android.documentsui", "com.mi.android.globalFileexplorer",
            ),
        ),
        StockApp(
            "wallet", R.string.stock_wallet, Glyph.Wallet,
            pkg("com.google.android.apps.walletnfcrel", "com.samsung.android.spay"),
        ),
        StockApp(
            "voicerecorder", R.string.stock_voicerecorder, Glyph.Mic,
            // MediaStore.Audio.Media.RECORD_SOUND_ACTION
            listOf(Target.Action("android.provider.MediaStore.RECORD_SOUND")) + pkg("com.google.android.apps.recorder"),
        ),
        StockApp("podcasts", R.string.stock_podcasts, Glyph.Podcasts, pkg("com.google.android.apps.podcasts")),
        StockApp("games", R.string.stock_games, Glyph.Games, pkg("com.google.android.play.games")),
        StockApp("xbox", R.string.stock_xbox, Glyph.Xbox, pkg("com.microsoft.xboxone.smartglass")),
        StockApp("cortana", R.string.stock_cortana, Glyph.Cortana, pkg("com.microsoft.cortana")),
        StockApp("skype", R.string.stock_skype, Glyph.Skype, pkg("com.skype.raider")),
        StockApp("onedrive", R.string.stock_onedrive, Glyph.OneDrive, pkg("com.microsoft.skydrive")),
        StockApp("office", R.string.stock_office, Glyph.Office, pkg("com.microsoft.office.officehubrow")),
        StockApp("word", R.string.stock_word, Glyph.Word, pkg("com.microsoft.office.word")),
        StockApp("excel", R.string.stock_excel, Glyph.Excel, pkg("com.microsoft.office.excel")),
        StockApp("powerpoint", R.string.stock_powerpoint, Glyph.PowerPoint, pkg("com.microsoft.office.powerpoint")),
        StockApp("onenote", R.string.stock_onenote, Glyph.OneNote, pkg("com.microsoft.office.onenote")),
        StockApp(
            "weather", R.string.stock_weather, Glyph.Weather,
            pkg(
                "com.microsoft.amp.apps.bingweather", "com.google.android.apps.weather",
                "com.sec.android.daemonapp", "com.miui.weather2",
            ),
            live = LiveKind.Weather,
        ),
        StockApp(
            "news", R.string.stock_news, Glyph.News,
            pkg("com.microsoft.amp.apps.bingnews", "com.google.android.apps.magazines"),
        ),
        StockApp("sports", R.string.stock_sports, Glyph.Sports, pkg("com.microsoft.amp.apps.bingsports")),
        StockApp("finance", R.string.stock_finance, Glyph.Finance, pkg("com.microsoft.amp.apps.bingfinance")),

        // System screens that Windows Phone 8.1 exposed as apps of their own.
        StockApp(
            "batterysaver", R.string.stock_batterysaver, Glyph.Battery,
            settingsAction = Settings.ACTION_BATTERY_SAVER_SETTINGS,
            live = LiveKind.Battery,
        ),
        StockApp(
            "datasense", R.string.stock_datasense, Glyph.Data,
            // Settings.ACTION_DATA_USAGE_SETTINGS
            settingsAction = "android.settings.DATA_USAGE_SETTINGS",
        ),
        StockApp(
            "storagesense", R.string.stock_storagesense, Glyph.Storage,
            settingsAction = Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
        ),
    )

    fun idOf(stock: StockApp) = "stock:${stock.id}"
}
