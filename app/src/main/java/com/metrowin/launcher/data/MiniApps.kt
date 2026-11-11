package com.metrowin.launcher.data

import android.app.Activity
import com.metrowin.launcher.calculator.CalculatorActivity
import com.metrowin.launcher.calendar.CalendarActivity
import com.metrowin.launcher.datasense.DataSenseActivity
import com.metrowin.launcher.dialer.DialerActivity
import com.metrowin.launcher.dialer.PeopleActivity
import com.metrowin.launcher.files.FilesActivity
import com.metrowin.launcher.mail.MailActivity
import com.metrowin.launcher.messages.MessagesActivity
import com.metrowin.launcher.music.NowPlayingActivity
import com.metrowin.launcher.news.NewsActivity
import com.metrowin.launcher.notes.NotesActivity
import com.metrowin.launcher.photos.PhotosActivity
import com.metrowin.launcher.recorder.RecorderActivity
import com.metrowin.launcher.shizukuterminal.ShizukuTerminalActivity
import com.metrowin.launcher.systemsettings.SystemSettingsActivity
import com.metrowin.launcher.todo.TodoActivity
import com.metrowin.launcher.wallet.WalletActivity

/** The stock apps ([StockApp.id]) that the launcher does itself, each in an activity of its own. */
object MiniApps {
    val activities: Map<String, Class<out Activity>> = mapOf(
        "mail" to MailActivity::class.java,
        "messaging" to MessagesActivity::class.java,
        "phone" to DialerActivity::class.java,
        "people" to PeopleActivity::class.java,
        "calculator" to CalculatorActivity::class.java,
        "notes" to NotesActivity::class.java,
        "news" to NewsActivity::class.java,
        "todo" to TodoActivity::class.java,
        "voicerecorder" to RecorderActivity::class.java,
        "calendar" to CalendarActivity::class.java,
        "wallet" to WalletActivity::class.java,
        "files" to FilesActivity::class.java,
        "photohub" to PhotosActivity::class.java,
        "nowplaying" to NowPlayingActivity::class.java,
        "shizukuterminal" to ShizukuTerminalActivity::class.java,
        "settings" to SystemSettingsActivity::class.java,
        "datasense" to DataSenseActivity::class.java,
        "storagesense" to DataSenseActivity::class.java,
    )
}
