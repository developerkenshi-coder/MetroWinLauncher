package com.metrowin.launcher

import com.metrowin.launcher.data.BatteryInfo
import com.metrowin.launcher.data.BatteryState
import com.metrowin.launcher.data.InfoContext
import com.metrowin.launcher.data.InfoPlan
import com.metrowin.launcher.data.InfoPriority
import com.metrowin.launcher.data.InfoTileState
import com.metrowin.launcher.data.LiveSources
import com.metrowin.launcher.data.ShortcutDef
import com.metrowin.launcher.data.ShortcutKind
import com.metrowin.launcher.model.InfoConfig
import com.metrowin.launcher.model.InfoPart
import com.metrowin.launcher.model.InfoRule
import com.metrowin.launcher.model.InfoSlot
import com.metrowin.launcher.model.InfoTrigger
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.RuleKind
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.model.TileData
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.model.TriggerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** The choices of the info tile that decide which faces turn: smart order, plans, rules, Do Not Disturb, the Kid's Corner, and several tiles at once. */
class InfoTileMoreTest {
    private val rome = ZoneId.of("Europe/Rome")
    private fun ms(iso: String) = LocalDateTime.parse(iso).atZone(rome).toInstant().toEpochMilli()

    private fun battery(percent: Int, state: BatteryState = BatteryState.Discharging) = BatteryInfo(percent, state, null, null)

    @Test
    fun severalInfoTilesEachKeepTheirOwnChoice() {
        val first = ShortcutDef(ShortcutDef.newId(ShortcutKind.Info, "info"), ShortcutKind.Info, "A", text = "t")
        val second = ShortcutDef(ShortcutDef.newId(ShortcutKind.Info, "info"), ShortcutKind.Info, "B", text = "cp;z=Asia/Tokyo")
        assertTrue(first.id != second.id)
        assertEquals(InfoConfig(parts = listOf(InfoPart.Time)), (first.toItem(null).data as TileData.Info).config)
        assertEquals("Asia/Tokyo", (second.toItem(null).data as TileData.Info).config.zone)
        val pages = listOf(listOf<StartEntry>(Tile(first.id, TileSize.Wide), Tile(second.id, TileSize.Medium)))
        assertEquals(setOf(LiveKind.Calendar, LiveKind.Photos), LiveSources.wanted(pages, listOf(first.toItem(null), second.toItem(null))))
    }

    @Test
    fun theTileRemembersWhichFaceIsOnShowAndWhatATouchOpens() {
        assertNull(InfoTileState.current("nothing"))
        InfoTileState.show("x", InfoPart.Battery)
        assertEquals(InfoPart.Battery, InfoTileState.current("x"))
        assertNull(InfoTileState.target("x"))
        InfoTileState.show("x", InfoPart.Photos, "content://media/1")
        assertEquals("content://media/1", InfoTileState.target("x"))
        InfoTileState.setSummary("x", "ten past ten")
        assertEquals("ten past ten", InfoTileState.summary("x"))
        InfoTileState.setTint("x", 0xFF112233.toInt())
        assertEquals(0xFF112233.toInt(), InfoTileState.tint("x"))
        InfoTileState.forget("x")
        assertNull(InfoTileState.current("x"))
        assertNull(InfoTileState.summary("x"))
        assertNull(InfoTileState.tint("x"))
    }

    // --- smart order

    private val faces = listOf(InfoPart.Time, InfoPart.Calendar, InfoPart.Alarm, InfoPart.Photos)

    @Test
    fun notSmartKeepsTheOrder() {
        val now = ms("2026-11-15T10:00:00")
        assertEquals(faces, InfoPriority.order(faces, smart = false, nowMs = now, hour = 10, eventStartMs = now + 600_000, alarmMs = now + 300_000))
    }

    @Test
    fun atNightOnlyTheTime() {
        val now = ms("2026-11-15T23:30:00")
        assertEquals(listOf(InfoPart.Time), InfoPriority.order(faces, true, now, 23, now + 600_000, 0L))
        assertEquals(listOf(InfoPart.Time), InfoPriority.order(faces, true, ms("2026-11-15T05:59:00"), 5, null, 0L))
        assertFalse(InfoPriority.isNight(6))
    }

    @Test
    fun theNightIsTheOneTheUserChose() {
        assertTrue(InfoPriority.isNight(2, from = 1, to = 5))
        assertFalse(InfoPriority.isNight(23, from = 1, to = 5))
        assertTrue(InfoPriority.isNight(22, from = 21, to = 7))
        assertTrue(InfoPriority.isNight(3, from = 21, to = 7))
        assertFalse(InfoPriority.isNight(10, from = 8, to = 8))
        val now = ms("2026-11-15T02:00:00")
        assertEquals(faces, InfoPriority.order(faces, true, now, 2, null, 0L, nightFrom = 4, nightTo = 8))
    }

    @Test
    fun anEventWithinTheHourStaysAlone() {
        val now = ms("2026-11-15T10:00:00")
        assertEquals(listOf(InfoPart.Calendar), InfoPriority.order(faces, true, now, 10, now + 30 * 60_000, 0L))
        // An event just begun still counts; one far away does not.
        assertEquals(listOf(InfoPart.Calendar), InfoPriority.order(faces, true, now, 10, now - 5 * 60_000, 0L))
        assertEquals(faces, InfoPriority.order(faces, true, now, 10, now + 3 * 3_600_000, 0L))
    }

    @Test
    fun whatCountsAsSoonIsTheTilesChoice() {
        val now = ms("2026-11-15T10:00:00")
        assertEquals(faces, InfoPriority.order(faces, true, now, 10, now + 30 * 60_000, 0L, soonMs = 15 * 60_000L))
        assertEquals(listOf(InfoPart.Calendar), InfoPriority.order(faces, true, now, 10, now + 100 * 60_000, 0L, soonMs = 120 * 60_000L))
    }

    @Test
    fun anAlarmWithinTheHourStaysAloneAndTheNearerOfTwoComesFirst() {
        val now = ms("2026-11-15T07:00:00")
        assertEquals(listOf(InfoPart.Alarm), InfoPriority.order(faces, true, now, 7, null, now + 20 * 60_000))
        assertEquals(listOf(InfoPart.Alarm, InfoPart.Calendar), InfoPriority.order(faces, true, now, 7, now + 40 * 60_000, now + 10 * 60_000))
    }

    @Test
    fun smartDoesNotCountWhatIsNotAmongTheFaces() {
        val now = ms("2026-11-15T10:00:00")
        val only = listOf(InfoPart.Time, InfoPart.Photos)
        assertEquals(only, InfoPriority.order(only, true, now, 10, now + 600_000, now + 300_000))
        assertEquals(listOf(InfoPart.Time), InfoPriority.order(listOf(InfoPart.Time), true, now, 3, null, 0L))
    }

    @Test
    fun theBatteryComesOnlyWhenItMatters() {
        val now = ms("2026-11-15T10:00:00")
        val all = listOf(InfoPart.Time, InfoPart.Battery, InfoPart.Photos)
        // Fine and not charging: left out. Low: alone. Charging: in the turn. Switched off: as it was.
        assertEquals(listOf(InfoPart.Time, InfoPart.Photos), InfoPriority.order(all, true, now, 10, null, 0L, battery = battery(80)))
        assertEquals(listOf(InfoPart.Battery), InfoPriority.order(all, true, now, 10, null, 0L, battery = battery(15)))
        assertEquals(all, InfoPriority.order(all, true, now, 10, null, 0L, battery = battery(80, BatteryState.Charging)))
        assertEquals(all, InfoPriority.order(all, true, now, 10, null, 0L, battery = battery(15), smartBattery = false))
        assertEquals(listOf(InfoPart.Battery), InfoPriority.order(listOf(InfoPart.Battery), true, now, 10, null, 0L, battery = battery(80)))
    }

    @Test
    fun rainAboutToFallComesFirstAndAlone() {
        val now = ms("2026-11-15T10:00:00")
        val all = listOf(InfoPart.Time, InfoPart.Weather, InfoPart.Photos)
        assertEquals(listOf(InfoPart.Weather), InfoPriority.order(all, true, now, 10, null, 0L, rainSoon = true))
        assertEquals(all, InfoPriority.order(all, true, now, 10, null, 0L, rainSoon = true, smartRain = false))
        assertEquals(all, InfoPriority.order(all, true, now, 10, null, 0L, rainSoon = false))
    }

    // --- the plan

    private fun moment(hour: Int = 10, context: InfoContext = InfoContext(), battery: BatteryInfo? = null, hasData: (InfoPart) -> Boolean = { true }, eventStart: Long? = null) =
        InfoPlan.Moment(nowMs = ms("2026-11-15T10:00:00"), hour = hour, battery = battery, weather = null, eventStartMs = eventStart, alarmMs = 0L, context = context, hasData = hasData)

    private val config = InfoConfig(parts = listOf(InfoPart.Time, InfoPart.Calendar, InfoPart.Alarm, InfoPart.Photos))

    @Test
    fun aFaceWithNothingToSayIsLeftOutAndATileWithNothingShowsTheTime() {
        assertEquals(listOf(InfoPart.Time, InfoPart.Photos), InfoPlan.parts(config, moment(hasData = { it == InfoPart.Time || it == InfoPart.Photos }), false))
        assertEquals(listOf(InfoPart.Time), InfoPlan.parts(config, moment(hasData = { false }), false))
    }

    @Test
    fun anHourOfTheDayBringsItsOwnFaces() {
        val planned = config.copy(slots = listOf(InfoSlot(6, listOf(InfoPart.Calendar)), InfoSlot(20, listOf(InfoPart.Photos, InfoPart.Time))))
        assertEquals(listOf(InfoPart.Calendar), InfoPlan.parts(planned, moment(hour = 9), false))
        assertEquals(listOf(InfoPart.Photos, InfoPart.Time), InfoPlan.parts(planned, moment(hour = 21), false))
        // Before the first hour of the day it is still last evening's plan.
        assertEquals(listOf(InfoPart.Photos, InfoPart.Time), InfoPlan.parts(planned, moment(hour = 3), false))
        assertEquals(InfoPart.Calendar, InfoPlan.slotAt(planned.slots, 6)!!.parts.single())
        assertNull(InfoPlan.slotAt(emptyList(), 6))
    }

    @Test
    fun aWifiOrBluetoothPlanHoldsOnlyWhileTheDeviceIsThere() {
        val planned = config.copy(
            triggers = listOf(InfoTrigger(TriggerKind.Wifi, "Home 5G", listOf(InfoPart.Photos)), InfoTrigger(TriggerKind.Bluetooth, "", listOf(InfoPart.Alarm))),
            slots = listOf(InfoSlot(0, listOf(InfoPart.Calendar))),
        )
        assertEquals(listOf(InfoPart.Photos), InfoPlan.parts(planned, moment(context = InfoContext(wifi = "home 5g")), false))
        assertEquals(listOf(InfoPart.Alarm), InfoPlan.parts(planned, moment(context = InfoContext(btAudio = true)), false))
        // Neither: the plan of the hour.
        assertEquals(listOf(InfoPart.Calendar), InfoPlan.parts(planned, moment(), false))
        assertFalse(InfoPlan.holds(TriggerKind.Wifi, "", InfoContext(wifi = "")))
    }

    @Test
    fun doNotDisturbAndTheKidsCornerNarrowTheTile() {
        val quiet = config.copy(dnd = true, kids = true)
        assertEquals(listOf(InfoPart.Time, InfoPart.Alarm), InfoPlan.parts(quiet, moment(context = InfoContext(dnd = true)), false))
        assertEquals(listOf(InfoPart.Time, InfoPart.Weather), InfoPlan.parts(quiet, moment(context = InfoContext(restricted = true)), false))
        // With no weather to say, only the time.
        assertEquals(listOf(InfoPart.Time), InfoPlan.parts(quiet, moment(context = InfoContext(restricted = true), hasData = { it == InfoPart.Time }), false))
        // Switched off, they change nothing.
        assertEquals(config.parts, InfoPlan.parts(config, moment(context = InfoContext(dnd = true, restricted = true)), false))
    }

    @Test
    fun aRuleKeepsItsFaceOutUntilItHolds() {
        val rules = config.copy(rules = listOf(InfoRule(InfoPart.Photos, RuleKind.Charging), InfoRule(InfoPart.Alarm, RuleKind.BatteryBelow, 30)))
        assertEquals(listOf(InfoPart.Time, InfoPart.Calendar), InfoPlan.parts(rules, moment(battery = battery(80)), false))
        assertEquals(listOf(InfoPart.Time, InfoPart.Calendar, InfoPart.Alarm), InfoPlan.parts(rules, moment(battery = battery(20)), false))
        assertEquals(listOf(InfoPart.Time, InfoPart.Calendar, InfoPart.Photos), InfoPlan.parts(rules, moment(battery = battery(80, BatteryState.Charging)), false))
    }

    @Test
    fun anEventRuleAndAnHoursRule() {
        val now = ms("2026-11-15T10:00:00")
        val rule = InfoRule(InfoPart.Alarm, RuleKind.EventWithin, 30)
        assertTrue(InfoPlan.allows(rule, moment(eventStart = now + 20 * 60_000)))
        assertFalse(InfoPlan.allows(rule, moment(eventStart = now + 50 * 60_000)))
        assertFalse(InfoPlan.allows(rule, moment(eventStart = null)))
        val hours = InfoRule(InfoPart.Photos, RuleKind.NotBetween, 22, 7)
        assertFalse(InfoPlan.allows(hours, moment(hour = 23)))
        assertTrue(InfoPlan.allows(hours, moment(hour = 12)))
    }

    @Test
    fun savingBatteryShowsWhatMattersOrOnlyTheTime() {
        val now = ms("2026-11-15T10:00:00")
        // Nothing turns while saving: the face that is on is the one that matters now (an event within the hour), not the first.
        assertEquals(listOf(InfoPart.Calendar), InfoPlan.parts(config, moment(eventStart = now + 20 * 60_000), saving = true))
        assertEquals(listOf(InfoPart.Time, InfoPart.Calendar, InfoPart.Alarm, InfoPart.Photos), InfoPlan.parts(config, moment(), saving = false))
        assertEquals(listOf(InfoPart.Time), InfoPlan.parts(config.copy(saver = true), moment(eventStart = now + 20 * 60_000), saving = true))
    }
}
