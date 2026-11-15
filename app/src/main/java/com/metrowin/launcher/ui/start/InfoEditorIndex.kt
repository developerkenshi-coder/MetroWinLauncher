package com.metrowin.launcher.ui.start

import androidx.annotation.StringRes
import com.metrowin.launcher.R
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.InfoPart

/**
 * What an info tile can be told, laid out as the settings of the launcher are: a short list of categories (an icon, a name, a line about what is in it,
 * an "i" that says more), and a category that is a pivot of its pages. Two levels, never more.
 */
enum class InfoCategory(
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val glyph: Glyph,
    /** What the category is for, in a few lines (the "i" of its row). */
    @param:StringRes val info: Int,
) {
    Faces(R.string.infoc_faces, R.string.infoc_faces_desc, Glyph.Widget, R.string.infoc_faces_info),
    Order(R.string.infoc_order, R.string.infoc_order_desc, Glyph.Recents, R.string.infoc_order_info),
    Clock(R.string.infoc_clock, R.string.infoc_clock_desc, Glyph.Alarm, R.string.infoc_clock_info),
    Calendar(R.string.infoc_calendar, R.string.infoc_calendar_desc, Glyph.Calendar, R.string.infoc_calendar_info),
    Photos(R.string.infoc_photos, R.string.infoc_photos_desc, Glyph.Photos, R.string.infoc_photos_info),
    Look(R.string.infoc_look, R.string.infoc_look_desc, Glyph.Theme, R.string.infoc_look_info),
    Privacy(R.string.infoc_privacy, R.string.infoc_privacy_desc, Glyph.Lock, R.string.infoc_privacy_info),
    Share(R.string.infoc_share, R.string.infoc_share_desc, Glyph.Globe, R.string.infoc_share_info);

    /** The pages of the category, in order: the pivot it opens as. */
    val pages: List<InfoPage> get() = InfoPage.entries.filter { it.category == this }
}

/** The pages of the editor. Each is a pivot page of one [category]; what is on each is listed in [InfoIndex]. */
enum class InfoPage(val category: InfoCategory, @param:StringRes val title: Int, @param:StringRes val description: Int) {
    FacesDay(InfoCategory.Faces, R.string.infop_faces_day, R.string.infop_faces_day_desc),
    FacesPhone(InfoCategory.Faces, R.string.infop_faces_phone, R.string.infop_faces_phone_desc),
    FacesNature(InfoCategory.Faces, R.string.infop_faces_nature, R.string.infop_faces_nature_desc),
    FacesWeb(InfoCategory.Faces, R.string.infop_faces_web, R.string.infop_faces_web_desc),

    OrderList(InfoCategory.Order, R.string.infop_order, R.string.infop_order_desc),
    Smart(InfoCategory.Order, R.string.infop_smart, R.string.infop_smart_desc),
    Plans(InfoCategory.Order, R.string.infop_plans, R.string.infop_plans_desc),

    ClockPage(InfoCategory.Clock, R.string.infop_clock, R.string.infop_clock_desc),

    CalendarPage(InfoCategory.Calendar, R.string.infop_calendar, R.string.infop_calendar_desc),
    WeatherPage(InfoCategory.Calendar, R.string.infop_weather, R.string.infop_weather_desc),

    PhotosPage(InfoCategory.Photos, R.string.infop_photos, R.string.infop_photos_desc),
    MemoriesPage(InfoCategory.Photos, R.string.infop_memories, R.string.infop_memories_desc),

    LookPage(InfoCategory.Look, R.string.infop_look, R.string.infop_look_desc),

    PrivacyPage(InfoCategory.Privacy, R.string.infop_privacy, R.string.infop_privacy_desc),
    AutoPage(InfoCategory.Privacy, R.string.infop_auto, R.string.infop_auto_desc),

    SharePage(InfoCategory.Share, R.string.infop_share, R.string.infop_share_desc),
}

/** One thing the editor lets the user set: where it is, its name on the page and the text of its "i". [id] is what the control says it is. */
class InfoSettingEntry(val id: String, val page: InfoPage, @param:StringRes val title: Int, @param:StringRes val info: Int)

/** Every setting of the editor, in one place (a test checks that each page has exactly these, and each has its text in every language). */
object InfoIndex {
    private fun e(id: String, page: InfoPage, @StringRes title: Int, @StringRes info: Int) = InfoSettingEntry(id, page, title, info)

    /** The face each "face.*" setting switches. */
    val faces: Map<InfoPart, String> = mapOf(
        InfoPart.Time to "face.time", InfoPart.Calendar to "face.calendar", InfoPart.Alarm to "face.alarm", InfoPart.Week to "face.week", InfoPart.Day to "face.day",
        InfoPart.Countdown to "face.countdown", InfoPart.Timer to "face.timer", InfoPart.Sky to "face.sky",
        InfoPart.Battery to "face.battery", InfoPart.BatteryGraph to "face.batterygraph", InfoPart.Steps to "face.steps", InfoPart.Music to "face.music",
        InfoPart.Waiting to "face.waiting", InfoPart.Todo to "face.todo", InfoPart.Reminders to "face.reminders", InfoPart.Birthdays to "face.birthdays",
        InfoPart.Note to "face.note", InfoPart.People to "face.people", InfoPart.Chat to "face.chat", InfoPart.Published to "face.published",
        InfoPart.Parking to "face.parking", InfoPart.DataUse to "face.datause", InfoPart.AppsUsed to "face.appsused", InfoPart.Toggles to "face.toggles",
        InfoPart.Widget to "face.widget",
        InfoPart.Photos to "face.photos", InfoPart.Memories to "face.memories", InfoPart.Weather to "face.weather", InfoPart.WeatherChart to "face.wxchart", InfoPart.Advice to "face.advice",
        InfoPart.News to "face.news", InfoPart.Finance to "face.finance",
    )

    val entries: List<InfoSettingEntry> = listOf(
        // --- what it shows
        e("face.time", InfoPage.FacesDay, R.string.infos_face_time, R.string.infoi_face_time),
        e("face.calendar", InfoPage.FacesDay, R.string.infos_face_calendar, R.string.infoi_face_calendar),
        e("face.alarm", InfoPage.FacesDay, R.string.infos_face_alarm, R.string.infoi_face_alarm),
        e("face.week", InfoPage.FacesDay, R.string.infos_face_week, R.string.infoi_face_week),
        e("face.day", InfoPage.FacesDay, R.string.infos_face_day, R.string.infoi_face_day),
        e("face.countdown", InfoPage.FacesDay, R.string.infos_face_countdown, R.string.infoi_face_countdown),
        e("setup.countdown", InfoPage.FacesDay, R.string.infos_setup_countdown, R.string.infoi_setup_countdown),
        e("face.timer", InfoPage.FacesDay, R.string.infos_face_timer, R.string.infoi_face_timer),
        e("face.sky", InfoPage.FacesDay, R.string.infos_face_sky, R.string.infoi_face_sky),

        e("face.battery", InfoPage.FacesPhone, R.string.infos_face_battery, R.string.infoi_face_battery),
        e("face.batterygraph", InfoPage.FacesPhone, R.string.infos_face_batterygraph, R.string.infoi_face_batterygraph),
        e("face.steps", InfoPage.FacesPhone, R.string.infos_face_steps, R.string.infoi_face_steps),
        e("face.music", InfoPage.FacesPhone, R.string.infos_face_music, R.string.infoi_face_music),
        e("face.waiting", InfoPage.FacesPhone, R.string.infos_face_waiting, R.string.infoi_face_waiting),
        e("face.todo", InfoPage.FacesPhone, R.string.infos_face_todo, R.string.infoi_face_todo),
        e("face.reminders", InfoPage.FacesPhone, R.string.infos_face_reminders, R.string.infoi_face_reminders),
        e("face.birthdays", InfoPage.FacesPhone, R.string.infos_face_birthdays, R.string.infoi_face_birthdays),
        e("face.note", InfoPage.FacesPhone, R.string.infos_face_note, R.string.infoi_face_note),
        e("setup.note", InfoPage.FacesPhone, R.string.infos_setup_note, R.string.infoi_setup_note),
        e("face.people", InfoPage.FacesPhone, R.string.infos_face_people, R.string.infoi_face_people),
        e("face.chat", InfoPage.FacesPhone, R.string.infos_face_chat, R.string.infoi_face_chat),
        e("face.published", InfoPage.FacesPhone, R.string.infos_face_published, R.string.infoi_face_published),
        e("setup.published", InfoPage.FacesPhone, R.string.infos_setup_published, R.string.infoi_setup_published),
        e("face.parking", InfoPage.FacesPhone, R.string.infos_face_parking, R.string.infoi_face_parking),
        e("face.datause", InfoPage.FacesPhone, R.string.infos_face_datause, R.string.infoi_face_datause),
        e("face.appsused", InfoPage.FacesPhone, R.string.infos_face_appsused, R.string.infoi_face_appsused),
        e("face.toggles", InfoPage.FacesPhone, R.string.infos_face_toggles, R.string.infoi_face_toggles),
        e("face.widget", InfoPage.FacesPhone, R.string.infos_face_widget, R.string.infoi_face_widget),
        e("setup.widget", InfoPage.FacesPhone, R.string.infos_setup_widget, R.string.infoi_setup_widget),

        e("face.photos", InfoPage.FacesNature, R.string.infos_face_photos, R.string.infoi_face_photos),
        e("face.memories", InfoPage.FacesNature, R.string.infos_face_memories, R.string.infoi_face_memories),
        e("face.weather", InfoPage.FacesNature, R.string.infos_face_weather, R.string.infoi_face_weather),
        e("face.wxchart", InfoPage.FacesNature, R.string.infos_face_wxchart, R.string.infoi_face_wxchart),
        e("face.advice", InfoPage.FacesNature, R.string.infos_face_advice, R.string.infoi_face_advice),

        e("face.news", InfoPage.FacesWeb, R.string.infos_face_news, R.string.infoi_face_news),
        e("face.finance", InfoPage.FacesWeb, R.string.infos_face_finance, R.string.infoi_face_finance),

        // --- order and pace
        e("order.list", InfoPage.OrderList, R.string.infos_order_list, R.string.infoi_order_list),
        e("order.pace", InfoPage.OrderList, R.string.infos_order_pace, R.string.infoi_order_pace),
        e("order.dwell", InfoPage.OrderList, R.string.infos_order_dwell, R.string.infoi_order_dwell),
        e("smart.on", InfoPage.Smart, R.string.infos_smart_on, R.string.infoi_smart_on),
        e("smart.saver", InfoPage.Smart, R.string.infos_smart_saver, R.string.infoi_smart_saver),
        e("smart.soon", InfoPage.Smart, R.string.infos_smart_soon, R.string.infoi_smart_soon),
        e("smart.night", InfoPage.Smart, R.string.infos_smart_night, R.string.infoi_smart_night),
        e("smart.battery", InfoPage.Smart, R.string.infos_smart_battery, R.string.infoi_smart_battery),
        e("smart.rain", InfoPage.Smart, R.string.infos_smart_rain, R.string.infoi_smart_rain),
        e("plan.slots", InfoPage.Plans, R.string.infos_plan_slots, R.string.infoi_plan_slots),
        e("plan.rules", InfoPage.Plans, R.string.infos_plan_rules, R.string.infoi_plan_rules),

        // --- the time
        e("clock.zones", InfoPage.ClockPage, R.string.infos_clock_zones, R.string.infoi_clock_zones),
        e("clock.analog", InfoPage.ClockPage, R.string.infos_clock_analog, R.string.infoi_clock_analog),
        e("clock.timers", InfoPage.ClockPage, R.string.infos_clock_timers, R.string.infoi_clock_timers),
        e("clock.background", InfoPage.ClockPage, R.string.infos_clock_background, R.string.infoi_clock_background),

        // --- calendar and weather
        e("cal.calendars", InfoPage.CalendarPage, R.string.infos_cal_calendars, R.string.infoi_cal_calendars),
        e("wx.city", InfoPage.WeatherPage, R.string.infos_wx_city, R.string.infoi_wx_city),

        // --- photos
        e("photos.album", InfoPage.PhotosPage, R.string.infos_photos_album, R.string.infoi_photos_album),
        e("photos.picked", InfoPage.PhotosPage, R.string.infos_photos_picked, R.string.infoi_photos_picked),
        e("photos.favorites", InfoPage.PhotosPage, R.string.infos_photos_favorites, R.string.infoi_photos_favorites),
        e("photos.noshots", InfoPage.PhotosPage, R.string.infos_photos_noshots, R.string.infoi_photos_noshots),
        e("photos.exclude", InfoPage.PhotosPage, R.string.infos_photos_exclude, R.string.infoi_photos_exclude),
        e("mem.days", InfoPage.MemoriesPage, R.string.infos_mem_days, R.string.infoi_mem_days),

        // --- look
        e("look.dots", InfoPage.LookPage, R.string.infos_look_dots, R.string.infoi_look_dots),
        e("look.tint", InfoPage.LookPage, R.string.infos_look_tint, R.string.infoi_look_tint),
        e("look.share", InfoPage.LookPage, R.string.infos_look_share, R.string.infoi_look_share),
        e("look.layout", InfoPage.LookPage, R.string.infos_look_layout, R.string.infoi_look_layout),

        // --- privacy and what it does by itself
        e("priv.titles", InfoPage.PrivacyPage, R.string.infos_priv_titles, R.string.infoi_priv_titles),
        e("priv.photos", InfoPage.PrivacyPage, R.string.infos_priv_photos, R.string.infoi_priv_photos),
        e("priv.kids", InfoPage.PrivacyPage, R.string.infos_priv_kids, R.string.infoi_priv_kids),
        e("auto.dnd", InfoPage.AutoPage, R.string.infos_auto_dnd, R.string.infoi_auto_dnd),
        e("auto.travel", InfoPage.AutoPage, R.string.infos_auto_travel, R.string.infoi_auto_travel),
        e("auto.wifi", InfoPage.AutoPage, R.string.infos_auto_wifi, R.string.infoi_auto_wifi),
        e("auto.bt", InfoPage.AutoPage, R.string.infos_auto_bt, R.string.infoi_auto_bt),
        e("auto.brief", InfoPage.AutoPage, R.string.infos_auto_brief, R.string.infoi_auto_brief),

        // --- models and sharing
        e("share.presets", InfoPage.SharePage, R.string.infos_share_presets, R.string.infoi_share_presets),
        e("share.send", InfoPage.SharePage, R.string.infos_share_send, R.string.infoi_share_send),
        e("share.import", InfoPage.SharePage, R.string.infos_share_import, R.string.infoi_share_import),
        e("share.speak", InfoPage.SharePage, R.string.infos_share_speak, R.string.infoi_share_speak),
        e("share.reset", InfoPage.SharePage, R.string.infos_share_reset, R.string.infoi_share_reset),
    )

    private val byId = entries.associateBy { it.id }

    fun get(id: String): InfoSettingEntry? = byId[id]

    /** The text of the "i" of a setting of the editor, or null when [id] is not one of them. */
    @StringRes
    fun infoOf(id: String): Int? = byId[id]?.info

    /** The name of a face as the editor says it. */
    @StringRes
    fun faceTitle(part: InfoPart): Int = byId.getValue(faces.getValue(part)).title
}
