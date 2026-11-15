package com.metrowin.launcher.data

import org.json.JSONObject

/**
 * The icon the user chose for one app: [pack] (the package of an icon pack, or empty for the icon of the app itself) and, optionally, the
 * name of one drawable of that pack. Without a drawable the app gets what the pack has for it (and the pack's dressing when it has none).
 */
data class IconPick(val pack: String, val drawable: String? = null) {
    /** `pack` or `pack|drawable`: neither a package name nor a drawable name can hold a bar. */
    fun encode(): String = if (drawable == null) pack else "$pack|$drawable"

    companion object {
        fun parse(value: String?): IconPick? {
            value ?: return null
            val pack = value.substringBefore('|').trim()
            val drawable = if (pack.isEmpty() || '|' !in value) null else value.substringAfter('|').trim().takeIf { it.isNotEmpty() }
            return IconPick(pack, drawable)
        }
    }
}

/** The icons chosen app by app (by [com.metrowin.launcher.model.AppItem.id], to an [IconPick] as text). Pure, so it is tested. */
object IconPicks {
    /** [picks] with [pick] for each of [ids]; null takes the choice away (the app is back to the default pack). */
    fun with(picks: Map<String, String>, ids: Collection<String>, pick: IconPick?): Map<String, String> =
        if (pick == null) picks - ids.toSet() else picks + ids.associateWith { pick.encode() }

    /** [picks] without those that name a pack that is not installed any more: the app falls back to the default pack. */
    fun withoutMissing(picks: Map<String, String>, installed: Set<String>): Map<String, String> =
        picks.filterValues { value -> IconPick.parse(value)?.pack.let { it != null && (it.isEmpty() || it in installed) } }

    /** The packs the choices name (not the empty one), so they are opened. */
    fun packsOf(picks: Map<String, String>): Set<String> = picks.values.mapNotNull { IconPick.parse(it)?.pack?.takeIf(String::isNotEmpty) }.toSet()

    /**
     * The pack that dresses an app: [default] without a choice; none for the icon of the app itself (pack ""); the chosen one from [opened],
     * and [default] again when that pack is not there (it was removed).
     */
    fun <P> packFor(pick: IconPick?, default: P?, opened: Map<String, P>): P? = when {
        pick == null -> default
        pick.pack.isEmpty() -> null
        else -> opened[pick.pack] ?: default
    }

    /** The names that hold [query] (without caring for case, and with `_` and `-` as spaces), in the order they come. */
    fun filterNames(names: List<String>, query: String): List<String> {
        val words = query.lowercase().replace('_', ' ').replace('-', ' ').split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return names
        return names.filter { name ->
            val plain = name.lowercase().replace('_', ' ').replace('-', ' ')
            words.all { it in plain }
        }
    }

    fun encode(picks: Map<String, String>): JSONObject = JSONObject().also { o -> picks.toSortedMap().forEach { (k, v) -> o.put(k, v) } }

    fun decode(o: JSONObject?): Map<String, String> {
        o ?: return emptyMap()
        return o.keys().asSequence().mapNotNull { key -> IconPick.parse(o.opt(key) as? String)?.let { key to it.encode() } }.toMap()
    }
}
