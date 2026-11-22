package com.metrowin.launcher

import android.app.Application
import android.media.AudioManager
import com.metrowin.launcher.data.VolumeControl
import com.metrowin.launcher.data.VolumeHud
import com.metrowin.launcher.data.VolumeRoute
import com.metrowin.launcher.data.VolumeRouting
import com.metrowin.launcher.data.VolumeRow
import com.metrowin.launcher.data.VolumeStream
import com.metrowin.launcher.notifications.MediaSessions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The panel of the volume keys over the launcher (the second area cut out of the big [LauncherViewModel]): which volume a key moves, the panel itself,
 * its sliders and its ringer key. It needs one thing of the rest: whether the panel is on in the settings ([panelOn]).
 */
internal class VolumeHudController(private val app: Application, private val panelOn: () -> Boolean) {
    private val volumeControl = VolumeControl(app)
    private val _volumeHud = MutableStateFlow<VolumeHud?>(null)

    /** The volume panel, or null while it is away. It goes by itself (see VolumeHudPanel) and with the launcher leaving the screen. */
    val volumeHud: StateFlow<VolumeHud?> = _volumeHud.asStateFlow()
    private var hudToken = 0

    /** The player whose own volume the panel moves, while it is that one's. */
    private var hudPlayer: android.media.session.MediaController? = null

    private fun labelOf(packageName: String): String = runCatching {
        val manager = app.packageManager
        manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    /**
     * A press of a volume key while the launcher is on screen. It moves what is sounding, the way Android does (the call, then a
     * player with a volume of its own, then the music of whatever app plays, then the ringer), and shows the panel with the name
     * of the app that plays. False when the panel is off in the settings or nothing could be moved: Android then handles the key.
     */
    fun onVolumeKey(raise: Boolean): Boolean {
        if (!panelOn()) return false
        return runCatching {
            val sessions = MediaSessions.active(app)
            val playing = sessions.firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING }
            val remote = playing?.takeIf {
                val info = it.playbackInfo
                info != null && info.playbackType == android.media.session.MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE &&
                    info.volumeControl != android.media.VolumeProvider.VOLUME_CONTROL_FIXED
            }
            val hud = when (val route = VolumeRouting.route(volumeControl.inCall(), remote != null, volumeControl.musicActive() || playing != null)) {
                VolumeRoute.Remote -> remote?.let { moveRemote(it, raise) }
                is VolumeRoute.Stream -> volumeControl.adjust(route.stream, raise)?.let { row ->
                    hudPlayer = null
                    VolumeHud(++hudToken, row, app = playing?.takeIf { route.stream == VolumeStream.Music }?.let { labelOf(it.packageName) })
                }
            } ?: return false
            _volumeHud.value = hud
            true
        }.getOrDefault(false)
    }

    private fun moveRemote(player: android.media.session.MediaController, raise: Boolean): VolumeHud? {
        val info = player.playbackInfo ?: return null
        runCatching { player.adjustVolume(if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0) }
        hudPlayer = player
        // The level is worked out here: the player reports the new one a moment later.
        val level = VolumeRouting.step(info.currentVolume, 0, info.maxVolume, raise)
        return VolumeHud(++hudToken, VolumeRow(null, player.packageName, level, 0, info.maxVolume), app = labelOf(player.packageName))
    }

    /** A slider of the panel was touched: sets that volume and keeps the panel up. */
    fun setVolume(row: VolumeRow, fraction: Float) {
        val hud = _volumeHud.value ?: return
        val level = VolumeRouting.level(fraction, row.min, row.max)
        val updated = when {
            row.remotePackage != null -> {
                hudPlayer?.takeIf { it.packageName == row.remotePackage }?.let { runCatching { it.setVolumeTo(level, 0) } }
                row.copy(level = level)
            }
            row.stream != null -> volumeControl.set(row.stream, level) ?: row
            else -> row
        }
        fun same(other: VolumeRow) = other.stream == row.stream && other.remotePackage == row.remotePackage
        _volumeHud.value = hud.copy(
            token = ++hudToken,
            main = if (same(hud.main)) updated else hud.main,
            others = hud.others.map { if (same(it)) updated else it },
        )
    }

    /** The ringer key of the panel was touched: the ringer goes from ringing to vibrating and back, and the panel stays up. */
    fun toggleRinger() {
        val hud = _volumeHud.value ?: return
        val updated = volumeControl.toggleRinger() ?: return
        fun swap(row: VolumeRow) = if (row.stream == VolumeStream.Ring) updated else row
        _volumeHud.value = hud.copy(token = ++hudToken, main = swap(hud.main), others = hud.others.map(::swap))
    }

    /** Opens the panel out to the other volumes, or folds it back. */
    fun toggleVolumeHud() {
        val hud = _volumeHud.value ?: return
        val expanded = !hud.expanded
        val others = if (expanded) {
            listOf(VolumeStream.Music, VolumeStream.Ring, VolumeStream.Alarm)
                .filter { it != hud.main.stream }
                .mapNotNull(volumeControl::read)
        } else {
            emptyList()
        }
        _volumeHud.value = hud.copy(token = ++hudToken, expanded = expanded, others = others)
    }

    fun dismissVolumeHud() {
        _volumeHud.value = null
    }
}
