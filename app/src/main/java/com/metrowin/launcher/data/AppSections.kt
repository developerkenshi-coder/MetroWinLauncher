package com.metrowin.launcher.data

import androidx.compose.runtime.Immutable
import com.metrowin.launcher.model.AppItem
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

@Immutable
data class AppSection(val key: String, val items: List<AppItem>)

/**
 * Alphabetical grouping of the app list, as on Windows Phone: numbers and symbols first (`#`),
 * then A to Z, then any other script. Accents are ignored for the group (`Élan` goes under E).
 */
object AppSections {
    const val SYMBOLS = "#"

    private val combiningMarks = Regex("\\p{M}+")

    /** Lower case, without accents or surrounding spaces: how labels are compared and searched. */
    fun fold(text: String): String =
        Normalizer.normalize(text.trim(), Normalizer.Form.NFD).replace(combiningMarks, "").lowercase(Locale.ROOT)

    fun keyOf(label: String): String {
        val folded = Normalizer.normalize(label.trim(), Normalizer.Form.NFD).replace(combiningMarks, "")
        if (folded.isEmpty()) return SYMBOLS
        val codePoint = folded.codePointAt(0)
        if (!Character.isLetter(codePoint)) return SYMBOLS
        return String(Character.toChars(codePoint)).uppercase(Locale.ROOT)
    }

    private fun rank(key: String): Int = when {
        key == SYMBOLS -> 0
        key.length == 1 && key[0] in 'A'..'Z' -> 1
        else -> 2
    }

    fun sort(items: List<AppItem>, locale: Locale = Locale.getDefault()): List<AppItem> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
        val keyed = items.map { it to keyOf(it.label) }
        return keyed
            .sortedWith(
                compareBy<Pair<AppItem, String>> { rank(it.second) }
                    .thenBy { it.second }
                    .thenComparator { a, b -> collator.compare(a.first.label, b.first.label) },
            )
            .map { it.first }
    }

    /** Groups an already [sort]ed list. */
    fun group(sorted: List<AppItem>): List<AppSection> {
        val sections = LinkedHashMap<String, MutableList<AppItem>>()
        sorted.forEach { sections.getOrPut(keyOf(it.label)) { mutableListOf() } += it }
        return sections.map { (key, items) -> AppSection(key, items) }
    }

    /** Every cell of the jump list: `#`, A to Z, then the other scripts that are in use. */
    fun jumpKeys(sections: List<AppSection>): List<String> {
        val base = listOf(SYMBOLS) + ('A'..'Z').map { it.toString() }
        val extra = sections.map { it.key }.filter { it !in base }
        return base + extra
    }
}
