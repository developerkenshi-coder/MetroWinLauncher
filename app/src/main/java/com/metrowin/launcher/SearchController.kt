package com.metrowin.launcher

import com.metrowin.launcher.data.FileType
import com.metrowin.launcher.data.FileSearch
import com.metrowin.launcher.data.FileMatch
import com.metrowin.launcher.data.LivePermissions
import android.app.Application
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.PhoneCalls
import com.metrowin.launcher.data.CalendarSearch
import com.metrowin.launcher.data.CurrencyRepository
import com.metrowin.launcher.data.Currencies
import com.metrowin.launcher.data.EventMatch
import com.metrowin.launcher.data.MiniSearch
import com.metrowin.launcher.data.NoteMatch
import com.metrowin.launcher.data.TodoMatch
import com.metrowin.launcher.notes.NoteStore
import com.metrowin.launcher.todo.TodoStore
import com.metrowin.launcher.data.QuickAnswer
import com.metrowin.launcher.data.PlaceMatch
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.ContactMatch
import com.metrowin.launcher.data.ContactPick
import com.metrowin.launcher.data.ContactSearch
import com.metrowin.launcher.data.WeatherRepository
import com.metrowin.launcher.data.ContactDirectory
import com.metrowin.launcher.data.SmartDial
import com.metrowin.launcher.notifications.NotificationEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * What the search box of the launcher asks of the phone (the third area cut out of the big [LauncherViewModel]): contacts, files, notifications,
 * messages, notes, things to do, events, a currency conversion and the places of the weather. It reads the settings and the Action Center through
 * the two functions it is given.
 */
internal class SearchController(
    private val app: Application,
    private val settings: () -> LauncherSettings,
    private val notifications: () -> List<NotificationEntry>,
    private val directory: ContactDirectory,
    private val currencyRepository: CurrencyRepository,
    private val weatherRepository: WeatherRepository,
) {
    /** The phone number of a contact found by [searchContacts], or null when it has none or the contacts are not readable. */
    suspend fun phoneNumberOf(lookupUri: String): String? =
        withContext(Dispatchers.IO) { PhoneCalls.numberOfContact(app, lookupUri) }

    /** The contact that a spoken name asks for: the first of [names] that finds one. By name only, never as a dial. */
    suspend fun pickContact(names: List<String>): ContactPick = withContext(Dispatchers.IO) {
        val search = ContactSearch(app)
        names.firstNotNullOfOrNull { name ->
            ContactSearch.pick(name, search.search(name, ContactSearch.SPOKEN_LIMIT)).takeIf { it != ContactPick.None }
        } ?: ContactPick.None
    }

    suspend fun searchContacts(query: String): List<ContactMatch> = withContext(Dispatchers.IO) {
        val digits = query.trim()
        // Digits are a dial: the keys of the names and the numbers, with the number to call at hand (see [SmartDial]).
        if (settings().searchSmartDial && SmartDial.isQuery(digits)) {
            directory.search(digits).map { ContactMatch(it.name, it.lookupUri, it.number) }
        } else {
            ContactSearch(app).search(query)
        }
    }

    /** The files whose name matches [query], of the kinds the user chose in the settings. */
    suspend fun searchFiles(query: String): List<FileMatch> = withContext(Dispatchers.IO) {
        FileSearch(app).search(query, settings().searchFileTypes.mapNotNullTo(HashSet(), FileType::fromCode))
    }

    /** The notifications that wait in the Action Center whose title or text has every word of [query]. */
    fun searchNotifications(query: String): List<NotificationEntry> {
        val words = AppSections.fold(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty() || query.trim().length < 2) return emptyList()
        return notifications().filter { entry ->
            val text = AppSections.fold(listOf(entry.title, entry.text, entry.longText.orEmpty()).joinToString(" "))
            words.all { it in text }
        }.take(NOTIFICATION_RESULTS)
    }

    /** The conversations that have a text with [query] (needs the permission to read the messages). */
    suspend fun searchMessages(query: String): List<com.metrowin.launcher.messages.Conversation> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2 || !LivePermissions.isGranted(app, android.Manifest.permission.READ_SMS)) emptyList()
        else com.metrowin.launcher.messages.SmsRepository(app).search(query).take(MESSAGE_RESULTS)
    }

    /** The notes and the things to do of the launcher that match [query]; read from their files off the main thread. */
    suspend fun searchNotes(query: String): List<NoteMatch> =
        withContext(Dispatchers.IO) { MiniSearch.notes(NoteStore(app).load(), query) }

    suspend fun searchTodos(query: String): List<TodoMatch> =
        withContext(Dispatchers.IO) { MiniSearch.todos(TodoStore(app).load(), query) }

    suspend fun searchEvents(query: String): List<EventMatch> =
        withContext(Dispatchers.IO) { CalendarSearch(app).search(query) }

    /**
     * The conversion [query] asks for, with the rates of the last six hours (asked from the Internet when they are older; the old
     * ones are used when there is no connection). Null when there are no rates at all.
     */
    suspend fun currencyAnswer(query: Currencies.Query): QuickAnswer.Currency? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val kept = currencyRepository.cached()
        val rates = kept?.takeIf { now - it.fetchedAt < Currencies.MAX_AGE_MS } ?: currencyRepository.fetch(now) ?: kept
        rates?.let { Currencies.answer(query, it) }
    }

    /** Null when the search could not be made (no connection), an empty list when nothing matches. */
    suspend fun searchPlaces(query: String): List<PlaceMatch>? =
        withContext(Dispatchers.IO) { weatherRepository.search(query, Locale.getDefault().language) }

    private companion object {
        const val NOTIFICATION_RESULTS = 4
        const val MESSAGE_RESULTS = 4
    }
}
