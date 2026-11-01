package com.metrowin.launcher.calls

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import com.metrowin.launcher.data.PhoneCalls

/** Being the phone app of the phone: the role that makes Android show the calls on the screens of the launcher. */
object DialerRole {
    fun isDefault(context: Context): Boolean =
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage == context.packageName

    /** The screen where the user makes the launcher the phone app, or null when the phone has none. */
    fun requestIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= 29) {
            context.getSystemService(RoleManager::class.java)?.takeIf { it.isRoleAvailable(RoleManager.ROLE_DIALER) }
                ?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
        } else {
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
        }

    /**
     * Calls [number]: directly using MetroWin Launcher's Dialer ([DialerActivity]),
     * explicitly targetting this package so Google Dialer or external dialers are never opened.
     */
    @Suppress("DEPRECATION")
    fun call(context: Context, number: String, direct: Boolean) {
        runCatching {
            val intent = PhoneCalls.intent(number, direct).setPackage(context.packageName)
            context.startActivity(intent)
        }.onFailure {
            runCatching {
                context.startActivity(PhoneCalls.intent(number, direct))
            }
        }
    }
}
