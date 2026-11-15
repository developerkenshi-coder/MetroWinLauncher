package com.metrowin.launcher.ui.lock

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.metrowin.launcher.calendar.CalendarActivity
import com.metrowin.launcher.music.NowPlayingActivity
import com.metrowin.launcher.recorder.RecorderActivity
import com.metrowin.launcher.todo.TodoActivity
import com.metrowin.launcher.ui.mini.PageTurn

/** The mini apps a line or a card of the lock screen opens when it is touched. */
enum class LockTarget(private val activity: Class<out Activity>) {
    Calendar(CalendarActivity::class.java),
    Todo(TodoActivity::class.java),
    Recorder(RecorderActivity::class.java),
    NowPlaying(NowPlayingActivity::class.java),
    ;

    /** The mini app opens with the page turn (the turnstile), as it does from a tile. */
    fun intent(context: Context): Intent = Intent(context, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(PageTurn.EXTRA, true)
}
