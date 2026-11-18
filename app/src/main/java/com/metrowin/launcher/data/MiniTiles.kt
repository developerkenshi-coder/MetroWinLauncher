package com.metrowin.launcher.data

import android.Manifest
import android.content.Context
import android.provider.CallLog
import androidx.core.content.edit
import com.metrowin.launcher.R
import com.metrowin.launcher.messages.SmsRepository
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.notes.Note
import com.metrowin.launcher.notes.NoteStore
import com.metrowin.launcher.notifications.LiveCard
import com.metrowin.launcher.mail.MailSync
import com.metrowin.launcher.wallet.Card
import com.metrowin.launcher.wallet.WalletPrefs
import com.metrowin.launcher.wallet.WalletVault

/** The words of the faces, so the builders of [MiniTiles] stay plain functions (a test gives its own). */
class MiniWords(
    val unreadMail: (Int) -> String,
    val unreadTexts: (Int) -> String,
    val missedCalls: (Int) -> String,
    val lockedNote: String,
    val lastRecording: String,
    val mostUsedCard: String,
) {
    companion object {
        fun of(context: Context): MiniWords {
            val r = context.resources
            return MiniWords(
                { r.getQuantityString(R.plurals.minilive_mail, it, it) },
                { r.getQuantityString(R.plurals.minilive_texts, it, it) },
                { r.getQuantityString(R.plurals.minilive_missed, it, it) },
                r.getString(R.string.minilive_note_locked), r.getString(R.string.minilive_recording), r.getString(R.string.minilive_card),
            )
        }
    }
}

/**
 * The live tile of a mini app of the launcher: its faces say what is waiting in the app (unread mail, the last note, a missed call).
 * The apps that already have a tile of their own (Calendar, To-do, Photos, People, Music, Storage) are not here. What is personal
 * (Mail, Messages, Notes, Wallet, Recorder) is off until the user turns the switch on; nothing is read for an app that has no tile on
 * Start, without its permission, or whose content is locked. The faces go to the tile as [LiveCard.Digest], under the id of the stock app.
 */
object MiniTiles {
    /** The apps that get a face, in the order of the settings. */
    val apps = listOf("mail", "messaging", "phone", "notes", "voicerecorder", "wallet", "nowplaying")

    /** The apps whose face says something personal: their switch starts off. */
    val personal = setOf("mail", "messaging", "notes", "voicerecorder", "wallet")

    private fun key(app: String) = "mini_live:$app"

    fun on(context: Context, app: String): Boolean = Prefs.main(context).getBoolean(key(app), app !in personal)

    fun set(context: Context, app: String, value: Boolean) = Prefs.main(context).edit { putBoolean(key(app), value) }

    /** The faces of the apps among [apps]: the id of a stock app, as the tile has it ("stock:notes"). */
    fun tileId(app: String) = "stock:$app"

    /** The mini apps with a switch that is on and a tile among the [pinned] ones of Start. */
    fun wanted(context: Context, pinned: Set<String>, items: List<AppItem>): Set<String> =
        items.filter { it.id in pinned }.mapNotNullTo(HashSet()) { item -> apps.firstOrNull { tileId(it) == item.id }?.takeIf { on(context, it) } }

    // --- the faces, from plain data

    private fun digest(title: String, text: String = "") = listOf<LiveCard>(LiveCard.Digest(title, text))

    fun mail(w: MiniWords, unread: Int, last: Pair<String, String>?): List<LiveCard> =
        if (unread <= 0) emptyList() else digest(w.unreadMail(unread), last?.let { (from, subject) -> listOf(from, subject).filter { it.isNotBlank() }.joinToString(": ") }.orEmpty())

    /** Unread texts and who wrote the last. The words of the text are left out when [hideText] (the setting of the live tiles). */
    fun texts(w: MiniWords, unread: Int, sender: String?, text: String?, hideText: Boolean): List<LiveCard> =
        if (unread <= 0) emptyList() else digest(w.unreadTexts(unread), listOfNotNull(sender, text?.takeIf { !hideText && it.isNotBlank() }).joinToString(": "))

    fun missed(w: MiniWords, count: Int, who: String?): List<LiveCard> = if (count <= 0) emptyList() else digest(w.missedCalls(count), who.orEmpty())

    /** The note changed last, not in the bin. A locked one says only that it is locked: not its title, not a word of it. */
    fun note(w: MiniWords, notes: List<Note>): List<LiveCard> {
        val note = notes.filter { it.deleted == 0L && !it.isBlank }.maxByOrNull { it.updated } ?: return emptyList()
        if (note.locked) return digest(w.lockedNote)
        val lines = note.text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val title = note.title.ifBlank { lines.firstOrNull().orEmpty() }
        val rest = (if (note.title.isBlank()) lines.drop(1) else lines).firstOrNull().orEmpty()
        return if (title.isBlank()) emptyList() else digest(title, rest)
    }

    fun recording(w: MiniWords, fileName: String?): List<LiveCard> = if (fileName == null) emptyList() else digest(w.lastRecording, Recordings.title(fileName))

    /** The card used most (or, without a history, the first favourite): only its name, never its code or its notes. A locked wallet says nothing. */
    fun wallet(w: MiniWords, cards: List<Card>, locked: Boolean): List<LiveCard> {
        if (locked) return emptyList()
        val card = cards.filter { it.uses > 0 }.maxByOrNull { it.uses } ?: cards.firstOrNull { it.favorite } ?: return emptyList()
        return digest(w.mostUsedCard, card.name)
    }

    // --- the reading

    /** Blocking, call it off the main thread. The faces of the [ids] whose switch is on, by tile id; an app with nothing to say is not in the map. */
    fun read(context: Context, ids: Set<String>, hideText: Boolean): Map<String, List<LiveCard>> {
        val w = MiniWords.of(context)
        val out = LinkedHashMap<String, List<LiveCard>>()
        for (app in ids) {
            if (!on(context, app)) continue
            val faces = runCatching {
                when (app) {
                    "mail" -> mail(w, MailSync.unread(context), MailSync.last(context))
                    "messaging" -> texts(context, w, hideText)
                    "phone" -> missed(context, w)
                    "notes" -> note(w, NoteStore(context).load())
                    "voicerecorder" -> recording(w, Recordings(context).list().firstOrNull()?.name)
                    "wallet" -> WalletPrefs(context).lock.on.let { locked -> wallet(w, if (locked) emptyList() else WalletVault(context).load(), locked) }
                    else -> emptyList()
                }
            }.getOrDefault(emptyList())
            if (faces.isNotEmpty()) out[tileId(app)] = faces
        }
        return out
    }

    private fun texts(context: Context, w: MiniWords, hideText: Boolean): List<LiveCard> {
        if (!LivePermissions.isGranted(context, Manifest.permission.READ_SMS)) return emptyList()
        val all = SmsRepository(context).conversations()
        val last = all.firstOrNull { it.unread > 0 }
        return texts(w, all.sumOf { it.unread }, last?.title, last?.snippet, hideText)
    }

    private fun missed(context: Context, w: MiniWords): List<LiveCard> {
        if (!LivePermissions.isGranted(context, LivePermissions.calls)) return emptyList()
        var count = 0
        var who: String? = null
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER),
            "${CallLog.Calls.TYPE}=? AND ${CallLog.Calls.NEW}=1 AND ${CallLog.Calls.DATE}>?",
            arrayOf(CallLog.Calls.MISSED_TYPE.toString(), MissedCalls.seenAt(context).toString()), "${CallLog.Calls.DATE} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (count++ == 0) who = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: cursor.getString(1)
            }
        }
        return missed(w, count, who)
    }

    /** What changes the faces from outside the launcher: the permissions and the places the observers watch. */
    fun sources(ids: Set<String>): Set<DataChanges.Source> =
        buildSet { if ("phone" in ids) add(DataChanges.Source.Calls); if ("messaging" in ids) add(DataChanges.Source.Messages) }

    /** How many of the permissions behind the faces are given: a change shows at the next return to Start. */
    fun grants(context: Context): Int =
        listOf(LivePermissions.calls, Manifest.permission.READ_SMS).count { LivePermissions.isGranted(context, it) }
}
