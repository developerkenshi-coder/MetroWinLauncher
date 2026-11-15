package com.metrowin.launcher.data

import android.annotation.SuppressLint
import android.content.Context
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/** A SIM of the phone: its slot (0, 1), the id Android sends texts with, the handle it calls with (null when Telecom does not say) and a name. */
data class SimInfo(val slot: Int, val subId: Int, val name: String, val handle: PhoneAccountHandle?) {
    /** "SIM 1 · Operator". */
    val label: String get() = if (name.isBlank()) "SIM ${slot + 1}" else "SIM ${slot + 1} · $name"
}

/** What a choice of SIM is for. */
enum class SimUse(val id: String) { Call("call"), Sms("sms") }

/** What to do with a call or a text. */
sealed interface SimChoice {
    /** Use this SIM: the contact asked for it, or the phone has a default for it. */
    data class Use(val sim: SimInfo) : SimChoice

    /** Two SIMs and nothing said: ask the user. */
    data object Ask : SimChoice

    /** One SIM (or none readable): let Android decide, as before. */
    data object System : SimChoice
}

/** Which SIM places a call or sends a text: the one kept for that contact, else the default of the phone, else the user is asked. */
object SimRouting {
    fun choose(sims: List<SimInfo>, contactSlot: Int?, defaultSlot: Int?): SimChoice {
        if (sims.size < 2) return SimChoice.System
        sims.firstOrNull { it.slot == contactSlot }?.let { return SimChoice.Use(it) }
        sims.firstOrNull { it.slot == defaultSlot }?.let { return SimChoice.Use(it) }
        return SimChoice.Ask
    }

    /** What a number is kept under: its last nine digits, as [com.metrowin.launcher.data.PeopleBook] compares numbers (+39 333… and 333… are one). Empty when there is no digit. */
    fun key(number: String): String = number.filter { it.isDigit() }.takeLast(9)
}

/** The SIM kept for a contact, and the default of the phone, for calls and for texts. A SIM is kept by its slot: a SIM taken out leaves the choice unused. */
class SimPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun forContact(use: SimUse, number: String): Int? = SimRouting.key(number).takeIf { it.isNotEmpty() }?.let { read("c.${use.id}.$it") }

    /** Keeps [slot] for [number], or forgets it when [slot] is null. */
    fun setForContact(use: SimUse, number: String, slot: Int?) {
        val key = SimRouting.key(number).takeIf { it.isNotEmpty() } ?: return
        write("c.${use.id}.$key", slot)
    }

    fun default(use: SimUse): Int? = read("d.${use.id}")

    fun setDefault(use: SimUse, slot: Int?) = write("d.${use.id}", slot)

    private fun read(key: String): Int? = if (prefs.contains(key)) prefs.getInt(key, -1).takeIf { it >= 0 } else null

    private fun write(key: String, slot: Int?) {
        prefs.edit().apply { if (slot == null) remove(key) else putInt(key, slot) }.apply()
    }

    private companion object {
        const val FILE = "sim_prefs"
    }
}

/** Reads the SIMs of the phone. */
object SimReader {
    /**
     * The SIMs in the phone, by slot, when there are two or more (with one nothing is asked). Needs the phone permission; without it the list is empty.
     * shortcut: a SIM is matched to its Telecom account by the id of the account (the ICC id or the subscription id), then by the subscription of the
     * account (Android 11+), and only as a last resort by order; a SIM without an account can still send texts but cannot be chosen for a call.
     */
    @SuppressLint("MissingPermission") // runCatching absorbs the SecurityException
    fun read(context: Context): List<SimInfo> = runCatching {
        if (!LivePermissions.isGranted(context, android.Manifest.permission.READ_PHONE_STATE)) return@runCatching emptyList()
        val subs = context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty().sortedBy { it.simSlotIndex }
        if (subs.size < 2) return@runCatching emptyList()
        val telecom = context.getSystemService(TelecomManager::class.java)
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val accounts = telecom.callCapablePhoneAccounts.filter { telecom.getPhoneAccount(it)?.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION) == true }
        fun subscriptionOf(handle: PhoneAccountHandle): Int? = if (android.os.Build.VERSION.SDK_INT >= 30) {
            runCatching { telephony.createForPhoneAccountHandle(handle)?.subscriptionId }.getOrNull()
        } else null
        val byId = subs.map { sub ->
            accounts.firstOrNull { it.id == sub.iccId || it.id == sub.subscriptionId.toString() } ?: accounts.firstOrNull { subscriptionOf(it) == sub.subscriptionId }
        }
        // Order is used only when no SIM was matched at all and the counts agree.
        val handles = if (byId.all { it == null } && accounts.size == subs.size) accounts else byId
        subs.mapIndexed { index, sub -> SimInfo(sub.simSlotIndex, sub.subscriptionId, sub.displayName?.toString().orEmpty(), handles.getOrNull(index)) }
    }.getOrDefault(emptyList())
}
