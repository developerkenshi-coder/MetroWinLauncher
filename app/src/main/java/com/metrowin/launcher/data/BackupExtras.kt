package com.metrowin.launcher.data

import android.content.Context
import com.metrowin.launcher.notifications.Alerts
import com.metrowin.launcher.todo.TodoStore
import com.metrowin.launcher.wallet.WalletVault

/** Reads what a backup keeps apart from the layout (see [BackupExtras]) from the phone, and puts it back. */
object BackupExtrasStore {
    fun read(context: Context, withWallet: Boolean = false) = BackupExtras(
        todos = TodoStore(context).load(),
        reminders = ReminderStore(context).load(),
        layouts = LayoutStore(context).load(),
        car = ParkedCarStore(context).load(),
        wallet = if (withWallet) WalletVault(context).load() else null,
    )

    /** Replaces what the phone has with [extras]; the alarms of the old reminders are cancelled and the ones to come are set again. */
    fun apply(context: Context, extras: BackupExtras) {
        val reminders = ReminderStore(context)
        reminders.load().forEach { Alerts.cancelReminder(context, it.id) }
        reminders.save(extras.reminders)
        val now = System.currentTimeMillis()
        extras.reminders.filter { !it.done && !it.rang && it.at > now }.forEach { Alerts.scheduleReminder(context, it) }
        TodoStore(context).save(extras.todos)
        LayoutStore(context).save(extras.layouts)
        ParkedCarStore(context).save(extras.car)
        // A phone that cannot write the vault keeps its own cards; a backup without the wallet leaves them alone.
        extras.wallet?.let { WalletVault(context).save(it) }
    }
}
