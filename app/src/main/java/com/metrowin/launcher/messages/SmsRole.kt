package com.metrowin.launcher.messages

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

/** Being the messaging app of the phone: the role that lets the launcher receive text messages and keep them in the messages of Android. */
object SmsRole {
    @Suppress("DEPRECATION") // before Android 10 there are no roles: the package that Android names as the default one is asked
    fun isDefault(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 29) {
            context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }

    /** The screen where the user makes the launcher the messaging app, or null when the phone has none. */
    @Suppress("DEPRECATION")
    fun requestIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= 29) {
            context.getSystemService(RoleManager::class.java)?.takeIf { it.isRoleAvailable(RoleManager.ROLE_SMS) }
                ?.createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
}
