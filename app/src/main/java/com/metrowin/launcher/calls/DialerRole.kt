package com.metrowin.launcher.calls

import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.BlockedNumberContract
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.metrowin.launcher.data.PhoneCalls

/** Being the phone app of the phone: the role that makes Android show the calls on the screens of the launcher. */
object DialerRole {
    fun isDefault(context: Context): Boolean =
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage == context.packageName

    /** Only the phone app of the phone (or the system) can keep the blocklist of Android: for any other app this is false. */
    fun canBlock(context: Context): Boolean = runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(context) }.getOrDefault(false)

    /** Puts [number] on the blocklist of Android, whose calls the phone then refuses before they ring. True when it was added. */
    fun block(context: Context, number: String): Boolean = runCatching {
        context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, ContentValues().apply { put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) }) != null
    }.getOrDefault(false)

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
    fun call(context: Context, number: String, direct: Boolean, sim: PhoneAccountHandle? = null) {
        fun intent() = PhoneCalls.intent(number, direct).also { if (sim != null) it.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, sim) }
        runCatching {
            context.startActivity(intent().setPackage(context.packageName))
        }.onFailure {
            runCatching {
                context.startActivity(intent())
            }
        }
    }

    /** The SIMs that can call, when there are two or more (with one, or none, the phone chooses and no choice is asked). Needs the phone permission. */
    fun sims(context: Context): List<PhoneAccountHandle> = runCatching {
        val telecom = context.getSystemService(TelecomManager::class.java)
        telecom.callCapablePhoneAccounts.filter { telecom.getPhoneAccount(it)?.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION) == true }
    }.getOrDefault(emptyList()).takeIf { it.size > 1 }.orEmpty()

    fun simLabel(context: Context, sim: PhoneAccountHandle): String =
        runCatching { context.getSystemService(TelecomManager::class.java).getPhoneAccount(sim)?.label?.toString() }.getOrNull().orEmpty()

    /** Calls the voicemail of the phone: the call goes through Android, which knows the number of the operator. */
    fun voicemail(context: Context) {
        val uri = Uri.fromParts(PhoneAccount.SCHEME_VOICEMAIL, "", null)
        runCatching { context.startActivity(Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
}
