package com.metrowin.launcher.data

import java.util.TreeMap
import kotlin.math.abs
import kotlin.math.min

/**
 * One thing the search can find. The [title] is what the user sees and what counts most; the [keywords] are other words that find
 * it (another language, what an app is for, the words of a note) and count less. [boost] (0 to 9) lifts it a little among
 * matches that are otherwise equal, such as the apps that are opened often; it never lifts it over a better match.
 */
class SearchEntry<T>(
    val value: T,
    val title: String,
    val keywords: List<String> = emptyList(),
    val boost: Int = 0,
)

/** A match: the [value] and how well it matched (only good to compare with the other hits of the same search). */
class SearchHit<T>(val value: T, val score: Int)

/** How text is cut into the words the search compares. */
object SearchText {
    private val separators = Regex("[^\\p{L}\\p{N}]+")

    /** Lower case, without accents or marks: how everything is compared. */
    fun fold(text: String): String = AppSections.fold(text)

    /** The words of [text] once folded: letters and digits, whatever is between them is a separator ("Wi-Fi" is "wi" and "fi"). */
    fun words(text: String): List<String> = fold(text).split(separators).filter { it.isNotEmpty() }

    /**
     * The parts of a name, where a new capital letter also starts a part: "YouTube Music" is You, Tube, Music. Not folded, and with
     * no part that is only a separator.
     */
    fun parts(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var previous: Char? = null
        for (c in text) {
            if (!c.isLetterOrDigit()) {
                if (current.isNotEmpty()) result += current.toString()
                current.clear()
                previous = null
                continue
            }
            if (previous != null && c.isUpperCase() && previous.isLowerCase() && current.isNotEmpty()) {
                result += current.toString()
                current.clear()
            }
            current.append(c)
            previous = c
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    /** The first letters of [parts], folded ("Google Maps" is "gm"), or null when there is only one part. */
    fun initials(parts: List<String>): String? {
        if (parts.size < 2) return null
        val letters = parts.mapNotNull { fold(it).firstOrNull() }
        return letters.joinToString("").takeIf { it.length >= 2 }
    }

    /** True when [a] and [b] are at most [max] edits apart (a letter more, less, other, or two next to each other swapped). */
    fun within(a: String, b: String, max: Int): Boolean = distance(a, b, max) <= max

    /** Optimal string alignment distance, stopping once it is known to be over [max] (then it returns [max] + 1). */
    fun distance(a: String, b: String, max: Int): Int {
        if (abs(a.length - b.length) > max) return max + 1
        if (a == b) return 0
        var beforePrevious = IntArray(b.length + 1)
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var best = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = min(min(previous[j] + 1, current[j - 1] + 1), previous[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) value = min(value, beforePrevious[j - 2] + 1)
                current[j] = value
                best = min(best, value)
            }
            if (best > max) return max + 1
            val spare = beforePrevious
            beforePrevious = previous
            previous = current
            current = spare
        }
        return previous[b.length]
    }
}

/**
 * An index over a list of [entries], built once and asked for every key typed: the words of every title and keyword are kept in
 * a sorted vocabulary that points back at the entries that have them, so a prefix is found by looking up a range instead of reading every name.
 *
 * What a typed word finds, best first: the same word, a word that starts with it, the first letters of a name's words ("gm" finds
 * Google Maps), a word that has it inside. A word of a keyword counts less than one of the title. When the whole query finds
 * nothing, words that are one or two typing mistakes away are accepted as well ("calnedar" finds Calendar).
 *
 * Every word typed has to be found (in any order), so "google ma" finds Google Maps and not every Google app. Among equal hits
 * the order of [entries] is kept.
 */
class SearchIndex<T>(
    private val entries: List<SearchEntry<T>>,
    /**
     * How many letters a typed word needs before it finds a keyword by its start. A title is found by its first letter, but the
     * keywords are wide (every word that says what an app is for), so a few letters would bring in everything ("cal" is the start
     * of "call"): a keyword is found by exactly the word at any length, and by its start only from here on.
     */
    private val keywordPrefixFrom: Int = 1,
) {
    private class Doc(val phrase: String, val boost: Int)

    private val docs = entries.map { Doc(SearchText.words(it.title).joinToString(" "), it.boost.coerceIn(0, MAX_BOOST)) }

    /** word → entries that have it, as `entry * 2 + (1 when the word comes from a keyword, not from the title)`. */
    private val vocabulary = TreeMap<String, MutableList<Int>>()

    /** The first letters of the words of the titles, → entries. */
    private val initials = TreeMap<String, MutableList<Int>>()

    init {
        entries.forEachIndexed { index, entry ->
            val seen = HashSet<Pair<String, Boolean>>()
            fun add(word: String, weak: Boolean) {
                if (seen.add(word to weak)) vocabulary.getOrPut(word) { mutableListOf() } += index * 2 + if (weak) 1 else 0
            }
            SearchText.words(entry.title).forEach { add(it, weak = false) }
            entry.keywords.forEach { keyword -> SearchText.words(keyword).forEach { add(it, weak = true) } }
            val wordParts = entry.title.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
            val forms = listOfNotNull(SearchText.initials(wordParts), SearchText.initials(SearchText.parts(entry.title))).distinct()
            forms.forEach { initials.getOrPut(it) { mutableListOf() } += index }
        }
    }

    /** The values that match [query], best first. Nothing typed matches nothing. */
    fun search(query: String): List<T> = hits(query).map { it.value }

    fun hits(query: String): List<SearchHit<T>> {
        val words = SearchText.words(query).take(MAX_WORDS)
        if (words.isEmpty()) return emptyList()
        return run(words, fuzzy = false).ifEmpty { run(words, fuzzy = true) }
    }

    private fun run(words: List<String>, fuzzy: Boolean): List<SearchHit<T>> {
        var total: HashMap<Int, Int>? = null
        for (word in words) {
            val scores = score(word, fuzzy)
            val previous = total
            total = if (previous == null) {
                scores
            } else {
                HashMap<Int, Int>().also { sum -> previous.forEach { (doc, score) -> scores[doc]?.let { sum[doc] = score + it } } }
            }
            if (total.isEmpty()) return emptyList()
        }
        val phrase = words.joinToString(" ")
        return total.orEmpty().entries
            .map { (doc, score) ->
                val d = docs[doc]
                val bonus = when {
                    d.phrase == phrase -> TITLE_IS_QUERY
                    d.phrase.startsWith(phrase) -> TITLE_STARTS
                    else -> 0
                }
                Triple(doc, score + bonus + d.boost, d)
            }
            .sortedWith(compareByDescending<Triple<Int, Int, Doc>> { it.second }.thenBy { it.first })
            .map { SearchHit(entries[it.first].value, it.second) }
    }

    /** For each entry that [word] finds, how well. */
    private fun score(word: String, fuzzy: Boolean): HashMap<Int, Int> {
        val scores = HashMap<Int, Int>()
        fun offer(posting: Int, base: Int) {
            val weak = posting and 1 == 1
            if (weak && base < SAME && word.length < keywordPrefixFrom) return
            val doc = posting ushr 1
            val value = if (weak) base * WEAK_PERCENT / 100 else base
            if (value > (scores[doc] ?: 0)) scores[doc] = value
        }
        // The same word, or one that starts with it: a range of the sorted vocabulary.
        for ((key, postings) in vocabulary.subMap(word, true, word + '￿', true)) {
            val base = if (key == word) SAME else STARTS
            postings.forEach { offer(it, base) }
        }
        if (word.length >= 2) {
            for ((_, postings) in initials.subMap(word, true, word + '￿', true)) postings.forEach { scores.merge(it, INITIALS) { a, b -> maxOf(a, b) } }
        }
        for ((key, postings) in vocabulary) {
            if (!key.startsWith(word) && key.contains(word)) postings.forEach { offer(it, INSIDE) }
        }
        if (fuzzy && word.length >= MIN_FUZZY) {
            val allowed = if (word.length >= 8) 2 else 1
            for ((key, postings) in vocabulary) {
                if (key.length < word.length - allowed) continue
                // Typing is not finished: the start of a longer word is as good as the word.
                val near = min(
                    SearchText.distance(word, key, allowed),
                    if (key.length > word.length) SearchText.distance(word, key.substring(0, word.length), allowed) else allowed + 1,
                )
                if (near <= allowed) postings.forEach { offer(it, FUZZY - 5 * near) }
            }
        }
        return scores
    }

    companion object {
        const val MAX_BOOST = 9
        private const val MAX_WORDS = 6
        private const val MIN_FUZZY = 4
        private const val WEAK_PERCENT = 60

        // The steps are wide enough that a boost (at most 9) and a title bonus never turn one kind of match into another.
        private const val SAME = 100
        private const val STARTS = 80
        private const val INITIALS = 70
        private const val INSIDE = 40
        private const val FUZZY = 30
        private const val TITLE_IS_QUERY = 60
        private const val TITLE_STARTS = 50
    }
}
