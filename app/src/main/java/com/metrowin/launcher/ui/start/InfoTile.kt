package com.metrowin.launcher.ui.start

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.ClockFormat
import com.metrowin.launcher.data.InfoPlan
import com.metrowin.launcher.data.InfoPriority
import com.metrowin.launcher.data.InfoTileState
import com.metrowin.launcher.data.LiveEvent
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.MemorySpec
import com.metrowin.launcher.data.MiniApps
import com.metrowin.launcher.data.PhotoCard
import com.metrowin.launcher.data.PhotoSpec
import com.metrowin.launcher.data.Reminders
import com.metrowin.launcher.data.WeatherData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.InfoConfig
import com.metrowin.launcher.model.InfoLayout
import com.metrowin.launcher.model.InfoPart
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.notifications.LiveCard
import com.metrowin.launcher.ui.anim.FlipContainer
import com.metrowin.launcher.ui.anim.FlipControl
import com.metrowin.launcher.ui.anim.LocalPowerSaving
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

/** One turn of an info tile: a face, and which of the things that face has (which event, which photo, which part of the weather). */
internal data class InfoStep(val part: InfoPart, val sub: Int = 0)

/** The controls of the info tiles on Start, by tile, so that the screen reader's actions can turn a tile to its next face. */
internal object InfoControls {
    private val controls = java.util.concurrent.ConcurrentHashMap<String, FlipControl>()

    fun of(id: String): FlipControl? = controls[id]

    fun set(id: String, control: FlipControl) { controls[id] = control }

    fun forget(id: String) { controls.remove(id) }
}

/** The events a tile shows: those of the calendars it chose (all by default) that are not over yet. */
internal fun infoEvents(config: InfoConfig, live: LiveTileData, now: Long): List<LiveEvent> =
    (if (config.calendars.isEmpty()) live.events else live.info.events[config.calendars.toSet()].orEmpty()).filter { !it.isOver(now) }

internal fun infoWeather(config: InfoConfig, live: LiveTileData): WeatherData? =
    if (config.weatherCity.isEmpty()) live.weather else live.cityWeather[config.weatherCity]

internal fun photoSpecOf(c: InfoConfig) = PhotoSpec(c.album, c.favorites, c.noShots, c.excluded.toSet(), c.picked)

internal fun memorySpecOf(c: InfoConfig) = MemorySpec(c.album, c.noShots, c.excluded.toSet(), c.memoryDays)

internal fun infoPhotos(config: InfoConfig, live: LiveTileData): List<PhotoCard> = live.info.photos[photoSpecOf(config)].orEmpty()

internal fun infoMemories(config: InfoConfig, live: LiveTileData): List<PhotoCard> = live.info.memories[memorySpecOf(config)].orEmpty()

/** Whether a face has anything to say now (a face with nothing is left out of the turn). */
internal fun InfoPart.hasData(config: InfoConfig, live: LiveTileData, events: List<LiveEvent>, now: Long): Boolean = when (this) {
    InfoPart.Time -> true
    InfoPart.Calendar -> events.isNotEmpty()
    InfoPart.Alarm -> live.nextAlarm > 0L
    InfoPart.Weather, InfoPart.Sky -> infoWeather(config, live) != null
    InfoPart.Battery -> live.battery != null
    InfoPart.Photos -> infoPhotos(config, live).isNotEmpty()
    InfoPart.Memories -> infoMemories(config, live).isNotEmpty()
    InfoPart.Week -> live.info.weekDays.isNotEmpty()
    InfoPart.Day -> live.info.today.isNotEmpty()
    InfoPart.Countdown -> config.countdownDay != 0L
    InfoPart.Timer -> true
    InfoPart.Todo -> (live.todo?.open ?: 0) > 0
    InfoPart.Reminders -> Reminders.upcoming(live.reminders, now).isNotEmpty()
    InfoPart.Birthdays -> live.birthdays.isNotEmpty()
    InfoPart.Waiting -> live.missedCalls > 0 || live.unreadMail > 0
    InfoPart.Music -> live.nowPlaying != null
    InfoPart.Steps -> live.extras.steps != null
    InfoPart.BatteryGraph -> live.extras.battery != null
    InfoPart.People -> live.people.isNotEmpty()
    InfoPart.Chat -> chatUp()
    InfoPart.Published -> publishedUp(config.published)
    InfoPart.Note -> config.note.isNotEmpty()
    InfoPart.Parking -> live.extras.parked != null
    InfoPart.DataUse -> live.extras.dataUsage != null
    InfoPart.AppsUsed -> live.extras.apps != null
    InfoPart.Toggles -> true
    InfoPart.Widget -> config.widget != 0
    InfoPart.WeatherChart -> (infoWeather(config, live)?.hours?.size ?: 0) >= 3
    InfoPart.Advice -> adviceOf(infoWeather(config, live)) != null
    InfoPart.News -> live.news != null
    InfoPart.Finance -> live.finance != null
}

/** How long (milliseconds, before the pace) a turn stays, when the tile gives each what it takes to read. */
private fun dwellOf(step: InfoStep, config: InfoConfig, events: List<LiveEvent>): Long {
    if (!config.dwell) return 5_500L
    return when (step.part) {
        InfoPart.Time, InfoPart.Alarm, InfoPart.Battery -> 4_000L
        InfoPart.Calendar -> 3_500L + (events.getOrNull(step.sub)?.title?.length ?: 0).coerceAtMost(60) * 45L
        InfoPart.Photos, InfoPart.Memories, InfoPart.People -> 4_500L
        InfoPart.Chat, InfoPart.Note, InfoPart.Advice, InfoPart.Published, InfoPart.News -> 6_500L
        else -> 5_000L
    }
}

/** The colour a tile that follows what it says takes, or null for its own: red for a low battery, blue for rain, dark at night. */
private fun tintOf(config: InfoConfig, live: LiveTileData, hour: Int): Int? = when {
    !config.tint -> null
    InfoPriority.lowBattery(live.battery) -> 0xFFE51400.toInt()
    InfoPriority.rainSoon(infoWeather(config, live)) -> 0xFF1BA1E2.toInt()
    InfoPriority.isNight(hour, config.nightFrom, config.nightTo) -> 0xFF1D1D1D.toInt()
    else -> null
}

@Composable
internal fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(AccessibilityManager::class.java) }
    var on by remember { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { on = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return on
}

/**
 * The info tile: the faces the user chose, turning one after the other (or laid out side by side on a wide tile). What matters now comes first when
 * the tile is smart; a face tied to a plan, a rule or Do Not Disturb shows only then. With reduced motion or while saving battery it changes its faces
 * without moving (slowly, while saving). A touch opens what the face on show is about (see [openInfoPart]).
 */
@Composable
internal fun InfoFace(item: AppItem, size: TileSize, width: Dp, height: Dp, config: InfoConfig, live: LiveTileData, seed: Int) {
    val now = rememberNowMinute()
    val saving = LocalPowerSaving.current
    val timers = LocalTimers.current
    val hour = remember(now) { Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY) }
    val events = remember(config, live, now) { infoEvents(config, live, now) }
    val weather = infoWeather(config, live)
    val moment = InfoPlan.Moment(
        nowMs = now, hour = hour, battery = live.battery, weather = weather,
        eventStartMs = events.firstOrNull { !it.allDay }?.startMillis, alarmMs = live.nextAlarm, context = live.context,
        hasData = { part -> if (part == InfoPart.Timer) timerGoing(timers, now) else part.hasData(config, live, events, now) },
    )
    val parts = remember(config, live, now, saving, timers) { InfoPlan.parts(config, moment, saving) }
    val dashboard = config.layout == InfoLayout.Dashboard && size != TileSize.Small && size != TileSize.Medium
    val steps = parts.flatMap { part -> expand(part, config, live, events, weather, size, crowded = parts.size > 1) }
    val keys = steps.map { it.part to it.sub }
    // Only a tile that follows what it says paints its colour; the summary and the controls are for the screen reader and the dots.
    SideEffect { InfoTileState.setTint(item.id, tintOf(config, live, hour)) }
    val summary = infoSummary(config, live, events, weather)
    SideEffect { InfoTileState.setSummary(item.id, summary) }
    val control = remember(item.id) { FlipControl() }
    SideEffect { InfoControls.set(item.id, control) }
    DisposableEffect(item.id) { onDispose { InfoControls.forget(item.id) } }
    val touch = rememberTouchExploration()
    var shownIndex by remember { mutableIntStateOf(InfoTileState.resume(item.id, keys)) }
    // What a touch opens in each turn, said by the face when it is drawn.
    val targets = remember { HashMap<InfoStep, String?>() }
    // Each time the photos come round they start somewhere else.
    val turns = remember { intArrayOf(0) }
    val locale = Locale.getDefault()

    @Composable
    fun face(step: InfoStep) {
        val photos = remember(config, live) { infoPhotos(config, live) }
        val memories = remember(config, live) { infoMemories(config, live) }
        val offset = remember { turns[0]++ }
        val card = when (step.part) {
            InfoPart.Photos -> photos.getOrNull((step.sub + offset) % photos.size.coerceAtLeast(1))
            InfoPart.Memories -> memories.getOrNull((step.sub + offset) % memories.size.coerceAtLeast(1))
            else -> null
        }
        val target = when (step.part) {
            InfoPart.Photos, InfoPart.Memories -> card?.uri?.takeIf { it.isNotEmpty() }
            InfoPart.Calendar -> events.getOrNull(step.sub)?.startMillis?.let { "day:" + java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay() }
            else -> null
        }
        SideEffect {
            targets[step] = target
            // The face on show says what a touch opens in it (the photo, the day of the event) as soon as it is drawn.
            if (steps.getOrNull(shownIndex % steps.size.coerceAtLeast(1)) == step) InfoTileState.show(item.id, step.part, target)
        }
        val place = config.weatherCityName.ifEmpty { live.place }.takeIf { it.isNotEmpty() }?.let { name ->
            // A forecast that could not be renewed for hours says how old it is.
            val hours = weather?.let { ((now - it.fetchedAt) / 3_600_000L).toInt() } ?: 0
            if (hours >= 3) name + " · " + stringResource(R.string.infot_stale, hours) else name
        } ?: weather?.let { ((now - it.fetchedAt) / 3_600_000L).toInt() }?.takeIf { it >= 3 }?.let { stringResource(R.string.infot_stale, it) }
        when (step.part) {
            InfoPart.Time -> ClockPane(item, size, width, height, config, photos.ifEmpty { memories }.getOrNull(offset % photos.ifEmpty { memories }.size.coerceAtLeast(1)), locale)
            InfoPart.Calendar -> CalendarFace(item, size, width, height, events.firstOrNull(), events, seed, sub = step.sub, hideTitle = config.hideTitles, actions = true)
            InfoPart.Alarm -> AlarmFace(item, size, width, height, live.nextAlarm, seed)
            InfoPart.Weather -> WeatherFace(item, size, width, height, weather, seed, sub = step.sub, place = place)
            InfoPart.Battery -> BatteryFace(item, size, width, height, live.battery, seed, sub = step.sub)
            InfoPart.Photos, InfoPart.Memories -> if (card != null) PhotoCardFace(item, size, card, config.hidePhotos, config.shareMemory && step.part == InfoPart.Memories) else StaticFace(item, size, width, height)
            InfoPart.Week -> WeekFace(item, size, live.info.weekDays)
            InfoPart.Day -> DayLineFace(item, size, live.info.today)
            InfoPart.Countdown -> CountdownFace(item.copy(label = config.countdownName.ifEmpty { item.label }, targetDay = config.countdownDay), size, width, height)
            InfoPart.Timer -> TimerStepFace(item, size, timers)
            InfoPart.Sky -> if (step.sub == 0) SunFace(item, size, width, height, weather) else MoonFace(item, size, width, height)
            InfoPart.Todo -> TodoFace(item, size, width, height, live.todo)
            InfoPart.Reminders -> RemindersFace(item, size, width, height, live.reminders)
            InfoPart.Birthdays -> BirthdaysFace(item, size, width, height, live.birthdays, seed)
            InfoPart.Waiting -> WaitingFace(item, size, live)
            InfoPart.Music -> live.nowPlaying?.let { CardsFace(item, size, width, height, listOf(LiveCard.Playing(it.title, it.artist, it.art, it.playing)), seed) } ?: StaticFace(item, size, width, height)
            InfoPart.Steps -> StepsFace(item, size, width, height, live.extras.steps, LocalTileFeatures.current.stepGoal)
            InfoPart.BatteryGraph -> BatteryGraphFace(item, size, width, height, live.extras.battery)
            InfoPart.People -> live.people.getOrNull(step.sub)?.let { PersonFace(item, size, it) } ?: StaticFace(item, size, width, height)
            InfoPart.Chat -> ChatFace(item, size, live.context.hideText)
            InfoPart.Published -> PublishedFace(item, size, config.published)
            InfoPart.Note -> NoteTextFace(item, size, config.note)
            InfoPart.Parking -> NoteLineFace(item, size, width, height, live.extras.parked?.let { parkedLine(it) }, "info:parking")
            InfoPart.DataUse -> DataUseFace(item, size, width, height, live.extras.dataUsage, live.extras.usageAllowed)
            InfoPart.AppsUsed -> AppsFace(item, size, width, height, live.extras.apps, live.extras.usageAllowed, seed)
            InfoPart.Toggles -> TogglesFace(item, size)
            InfoPart.Widget -> WidgetFace(item, size, width, height, config.widget, true) {}
            InfoPart.WeatherChart -> weather?.let { WeatherChartFace(item, size, it) } ?: StaticFace(item, size, width, height)
            InfoPart.Advice -> AdviceFace(item, size, weather)
            InfoPart.News -> NewsFace(item, size, width, height, live.news, seed)
            InfoPart.Finance -> FinanceFace(item, size, width, height, live.finance)
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (dashboard) {
            SideEffect { InfoTileState.show(item.id, parts.firstOrNull() ?: InfoPart.Time) }
            DashboardFace(item, size, width, height, config, live, events, parts)
        } else if (steps.size <= 1) {
            val only = steps.firstOrNull() ?: InfoStep(InfoPart.Time)
            face(only)
            SideEffect { InfoTileState.show(item.id, only.part, targets[only]) }
        } else {
            FlipContainer(
                steps.size, seed, startFace = InfoTileState.resume(item.id, keys), resetKey = keys, pace = config.pace.scale, calm = true, auto = !touch, control = control,
                dwell = { i -> dwellOf(steps[i % steps.size], config, events) },
                onFace = { i ->
                    val on = steps[i % steps.size]
                    shownIndex = i
                    InfoTileState.rest(item.id, keys, i % steps.size)
                    InfoTileState.show(item.id, on.part, targets[on])
                },
            ) { index ->
                face(steps[index % steps.size])
            }
            if (config.dots) InfoDots(steps.size, shownIndex % steps.size, Modifier.align(Alignment.BottomEnd)) { control.next() }
        }
    }
}

/** The faces a part takes in the turn: some have several (an event after the other, the parts of the weather, the photos). */
@Composable
private fun expand(part: InfoPart, config: InfoConfig, live: LiveTileData, events: List<LiveEvent>, weather: WeatherData?, size: TileSize, crowded: Boolean): List<InfoStep> = when (part) {
    InfoPart.Calendar -> (0 until events.size.coerceAtMost(if (size == TileSize.Small) 1 else 2)).map { InfoStep(part, it) }
    // With other faces to turn through the weather gives two of its own at most, so it does not crowd them out.
    InfoPart.Weather -> (0 until (weather?.let { weatherFaceCount(it, size) } ?: 1).coerceAtMost(if (crowded) 2 else Int.MAX_VALUE)).map { InfoStep(part, it) }
    InfoPart.Battery -> (0 until if (size == TileSize.Small) 1 else 2).map { InfoStep(part, it) }
    InfoPart.Photos -> (0 until infoPhotos(config, live).size.coerceIn(1, 3)).map { InfoStep(part, it) }
    InfoPart.Memories -> (0 until infoMemories(config, live).size.coerceIn(1, 3)).map { InfoStep(part, it) }
    InfoPart.People -> (0 until live.people.size.coerceIn(1, 3)).map { InfoStep(part, it) }
    InfoPart.Sky -> listOf(InfoStep(part, 0), InfoStep(part, 1))
    else -> listOf(InfoStep(part))
}

/** The face of the time: a time zone (or two, or the time at home while away), a clock of hands, over a photo if the tile asks for it. */
@Composable
private fun ClockPane(item: AppItem, size: TileSize, width: Dp, height: Dp, config: InfoConfig, backdrop: PhotoCard?, locale: Locale) {
    val phone = java.time.ZoneId.systemDefault()
    val zones = config.zones
    val away = config.travel && config.homeZone.isNotEmpty() && runCatching {
        val now = java.time.Instant.now()
        java.time.ZoneId.of(config.homeZone).rules.getOffset(now) != phone.rules.getOffset(now)
    }.getOrDefault(false)
    val timeItem = if (zones.isEmpty()) item.copy(label = ClockFormat.date(LocalDate.now(), locale)) else item.copy(label = config.zoneNames.firstOrNull()?.takeIf { it.isNotBlank() } ?: zones.first().substringAfter('/').replace('_', ' '))
    Box(Modifier.fillMaxSize()) {
        if (backdrop != null) {
            Image(backdrop.bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        }
        when {
            zones.size >= 2 -> ZonesFace(item, size, zones, config.zoneNames)
            away && zones.isEmpty() -> HomeAwayFace(item, size, config.homeZone)
            config.analog && zones.isEmpty() -> AnalogClockFace(item, size, width, height)
            else -> WorldClockFace(timeItem, size, width, height, zones.firstOrNull() ?: phone.id)
        }
        if (config.quickTimers && size != TileSize.Small) QuickTimers(Modifier.align(Alignment.BottomEnd))
    }
}

/**
 * What a touch on an info tile opens: the calendar, the clock, the photo itself, the forecast, the phone, the mini app of the face on show. False when the app it names
 * is not there or does not start; true, doing nothing, for a face with no app of its own. [target] is what the face says it is about (the address of a photo, the day of an event).
 */
fun openInfoPart(context: Context, part: InfoPart?, config: InfoConfig = InfoConfig(), target: String? = null, openWeather: () -> Unit = {}): Boolean {
    fun mini(id: String): Intent? = MiniApps.activities[id]?.let { Intent(context, it) }
    val intent = when (part) {
        InfoPart.Calendar, InfoPart.Week, InfoPart.Day -> mini("calendar")?.also { i -> target?.takeIf { it.startsWith("day:") }?.removePrefix("day:")?.toLongOrNull()?.let { i.putExtra(com.metrowin.launcher.calendar.CalendarActivity.EXTRA_DAY, it) } }
        InfoPart.Time, InfoPart.Alarm, InfoPart.Timer, InfoPart.Countdown -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
        InfoPart.Photos, InfoPart.Memories ->
            target?.let { Intent(Intent.ACTION_VIEW, android.net.Uri.parse(it)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) } ?: mini("photohub")
        InfoPart.Battery, InfoPart.BatteryGraph, InfoPart.DataUse, InfoPart.AppsUsed -> mini("datasense")
        InfoPart.Todo, InfoPart.Reminders -> mini("todo")
        InfoPart.Birthdays, InfoPart.People -> mini("people")
        InfoPart.Waiting -> mini("phone")
        InfoPart.Chat -> mini("messaging")
        InfoPart.Music -> mini("nowplaying")
        InfoPart.News -> mini("news")
        InfoPart.Note -> mini("notes")
        InfoPart.Published -> context.packageManager.getLaunchIntentForPackage(config.published)
        InfoPart.Weather, InfoPart.Sky, InfoPart.WeatherChart, InfoPart.Advice -> {
            openWeather()
            return true
        }
        // A face with no app of its own (the steps, the toggles, a note of the tile) has nothing to open: the touch does nothing, quietly.
        else -> return part != null
    } ?: return false
    return runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}
