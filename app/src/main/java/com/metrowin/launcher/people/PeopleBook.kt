package com.metrowin.launcher.people

import androidx.compose.runtime.Immutable
import java.text.Normalizer
import java.time.LocalDate

/**
 * A contact of the People hub: [key] is its lookup address, which survives merges. [photoUri] is the small photo, when there is one.
 * The rest is what the search and the filters need, read once with the list: [sources] is the mask of [com.metrowin.launcher.data.ContactSource]
 * where the contact is kept, [groups] the ids of its groups, [birthday] the raw date of the contact data ("1980-05-17" or "--05-17").
 */
@Immutable
data class Person(
    val key: String,
    val name: String,
    val starred: Boolean,
    val photoUri: String?,
    val numbers: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val org: String = "",
    val sources: Int = 0,
    val groups: Set<Long> = emptySet(),
    val birthday: String? = null,
    /** Name, numbers, emails, company, note and address, folded, for the search: built once, not at every key typed. */
    val haystack: String = "",
)

/** The pure part of the People hub: how names are listed, found and compared. Nothing here touches Android. */
object PeopleBook {
    /** Letters, accents and case taken away, so "Émile" is found by "emile". */
    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }.lowercase()

    /** The first letter of [name] as a whole character (an emoji is one, not two halves), or null when the name has none. */
    private fun firstLetter(name: String): String? {
        var i = 0
        while (i < name.length) {
            val cp = name.codePointAt(i)
            if (Character.isLetter(cp)) return String(Character.toChars(cp))
            i += Character.charCount(cp)
        }
        return null
    }

    /** The letter a name is listed under: its first character without accents, in capitals; "#" for a name that does not start with a letter. */
    fun initial(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || !Character.isLetter(trimmed.codePointAt(0))) return "#"
        val first = String(Character.toChars(trimmed.codePointAt(0)))
        val base = Normalizer.normalize(first, Normalizer.Form.NFD).first()
        return if (base.uppercaseChar() in 'A'..'Z') base.uppercaseChar().toString() else first.uppercase()
    }

    /** The letter of the tile of a contact without a photo: the first letter of the name, like the call screen; "#" when there is none. */
    fun avatar(name: String): String = firstLetter(name)?.uppercase() ?: "#"

    /** The contacts under their letters: A to Z, then the letters of other alphabets, "#" last. */
    fun grouped(people: List<Person>): List<Pair<String, List<Person>>> =
        people.groupBy { initial(it.name) }.toList().sortedWith(compareBy({ it.first == "#" }, { it.first[0] !in 'A'..'Z' }, { it.first }))

    /** True when two numbers are the same line however they are written (+39 333 1234567 and 3331234567). Two numbers with a country code must agree on all of it. */
    fun sameNumber(a: String, b: String): Boolean {
        val x = a.filter { it.isDigit() }
        val y = b.filter { it.isDigit() }
        if (x.isEmpty() || y.isEmpty()) return false
        if (international(a) && international(b)) return x.removePrefix(if (a.trim().startsWith("+")) "" else "00") == y.removePrefix(if (b.trim().startsWith("+")) "" else "00")
        return x.takeLast(NUMBER_TAIL) == y.takeLast(NUMBER_TAIL)
    }

    private fun international(number: String) = number.trim().let { it.startsWith("+") || it.startsWith("00") }

    /** The key a number is indexed by: its last nine digits. */
    fun tail(number: String): String = number.filter { it.isDigit() }.takeLast(NUMBER_TAIL)

    /** The search words, all of which must be in the contact: a word of digits is found in the numbers however they are spaced. */
    fun matches(person: Person, query: String): Boolean {
        val words = fold(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val digits = person.numbers.joinToString(" ") { it.filter { ch -> ch.isDigit() } }
        return words.all { w -> person.haystack.contains(w) || (w.all { it.isDigit() } && digits.contains(w)) }
    }

    /** What the search looks at. */
    fun haystackOf(name: String, numbers: List<String>, emails: List<String>, org: String, extra: String = ""): String =
        fold(listOf(name, numbers.joinToString(" "), emails.joinToString(" "), org, extra).joinToString(" "))

    /** The month and the day of a birthday as the contacts keep it ("1980-05-17" or "--05-17"), or null when it is not a date. */
    fun birthdayOf(raw: String?): Pair<Int, Int>? {
        val m = raw?.let { Regex("""^(?:\d{4}|-)-(\d{2})-(\d{2})""").find(it.trim()) } ?: return null
        val month = m.groupValues[1].toInt()
        val day = m.groupValues[2].toInt()
        return if (month in 1..12 && day in 1..31) month to day else null
    }

    /** The year of a birthday when the contact has it. */
    fun birthYear(raw: String?): Int? = raw?.let { Regex("""^(\d{4})-""").find(it.trim()) }?.groupValues?.get(1)?.toInt()?.takeIf { it > 1 }

    /** Days from [today] to the next [month]/[day] (0 when it is today). A 29 February is kept on the 28th in a year without it. */
    fun daysUntil(month: Int, day: Int, today: LocalDate): Int {
        fun on(year: Int) = runCatching { LocalDate.of(year, month, day) }.getOrElse { LocalDate.of(year, month, 28) }
        val next = on(today.year).let { if (it.isBefore(today)) on(today.year + 1) else it }
        return java.time.temporal.ChronoUnit.DAYS.between(today, next).toInt()
    }

    /** The people whose birthday is within [days] of [today], the nearest first: (person, days to go). */
    fun upcomingBirthdays(people: List<Person>, today: LocalDate, days: Int = 30): List<Pair<Person, Int>> =
        people.mapNotNull { p -> birthdayOf(p.birthday)?.let { (m, d) -> p to daysUntil(m, d, today) } }.filter { it.second <= days }.sortedBy { it.second }

    /** The people that share a number or a whole name with another: each group is a list of two or more contacts that may be one person. */
    fun duplicates(people: List<Person>): List<List<Person>> {
        val groups = ArrayList<List<Person>>()
        val seen = HashSet<String>()
        fun add(list: List<Person>) {
            val ids = list.map { it.key }.sorted().joinToString("|")
            if (list.size > 1 && seen.add(ids)) groups += list
        }
        people.groupBy { fold(it.name).trim() }.values.forEach(::add)
        people.flatMap { p -> p.numbers.map { tail(it) to p } }.filter { it.first.length >= MIN_TAIL }.groupBy({ it.first }, { it.second }).values.forEach { add(it.distinctBy(Person::key)) }
        return groups
    }

    /** The lookup key inside a lookup address ("content://com.android.contacts/contacts/lookup/KEY/ID"), still encoded as the address has it; null for any other address. */
    fun lookupKey(lookupUri: String): String? = lookupUri.substringAfter("/lookup/", "").substringBefore('/').takeIf { it.isNotEmpty() }

    private const val NUMBER_TAIL = 9
    private const val MIN_TAIL = 6
}
