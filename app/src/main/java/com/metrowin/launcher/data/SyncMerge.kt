package com.metrowin.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The three-way merge of two layouts that both changed since the last sync. Works on the layout files ([LayoutBackupCodec]) as JSON, so it
 * covers whatever the file carries: [base] is the layout both phones agreed on at the last sync, [local] is this phone now and [remote] the
 * folder's. Every list is merged by `id`, the settings key by key: what only one side changed is taken, what both changed the same way
 * is kept, and when both changed the same thing this phone wins. An item deleted on one side and changed on the other is kept (and counted).
 */
object SyncMerge {
    /**
     * [json] is the merged layout. [kept] counts the items deleted on one phone and changed on the other, which stayed. [lost] is true when
     * the same item or setting was changed on both phones and the other phone's version was not taken: the caller keeps that file aside.
     */
    class Result(val json: String, val kept: Int, val lost: Boolean)

    private class Tally(var kept: Int = 0, var lost: Boolean = false)

    /** Null when one of the three is not a layout file; the caller then falls back to keeping the other phone's file aside. */
    fun merge(base: String, local: String, remote: String): Result? = runCatching {
        val b = JSONObject(base)
        val l = JSONObject(local)
        val r = JSONObject(remote)
        if (listOf(b, l, r).any { it.optString("format") != LayoutBackupCodec.FORMAT }) return null
        val t = Tally()
        val out = JSONObject(local)
        out.put("pages", pages(b.optJSONArray("pages"), l.optJSONArray("pages"), r.optJSONArray("pages"), t))
        if (listOf(b, l, r).all { it.has("shortcuts") }) out.put("shortcuts", list(b.getJSONArray("shortcuts"), l.getJSONArray("shortcuts"), r.getJSONArray("shortcuts"), t))
        val settings = listOf(b, l, r).map { it.optJSONObject("settings") }
        if (settings.all { it != null }) out.put("settings", keys(settings[0]!!, settings[1]!!, settings[2]!!, t))
        val extras = listOf(b, l, r).map { it.optJSONObject("extras") }
        if (extras.all { it != null }) out.put("extras", extras(extras[0]!!, extras[1]!!, extras[2]!!, t))
        Result(out.toString(2), t.kept, t.lost)
    }.getOrNull()

    private fun extras(b: JSONObject, l: JSONObject, r: JSONObject, t: Tally): JSONObject {
        val out = JSONObject(l.toString())
        for (name in listOf("todos", "reminders", "layouts", "wallet")) {
            if (listOf(b, l, r).all { it.optJSONArray(name) != null }) out.put(name, list(b.getJSONArray(name), l.getJSONArray(name), r.getJSONArray(name), t))
        }
        // The place of the car is one value: the same rule as a setting.
        pick(b.opt("car"), l.opt("car"), r.opt("car"), t)?.let { out.put("car", it) } ?: out.remove("car")
        return out
    }

    // shortcut: the order of a list is this phone's (an item only the other phone has goes last), so a reordering done only on the other phone is not carried over; upgrade when order becomes data worth merging.
    private fun list(b: JSONArray, l: JSONArray, r: JSONArray, t: Tally): JSONArray {
        val bm = byId(b)
        val lm = byId(l)
        val rm = byId(r)
        return JSONArray().also { out -> (lm.keys + rm.keys).distinct().forEach { id -> pick(bm[id], lm[id], rm[id], t)?.let(out::put) } }
    }

    // An item that moved to another page on both phones would be on two: the first one stays. A page left empty stays too.
    private fun pages(b: JSONArray?, l: JSONArray?, r: JSONArray?, t: Tally): JSONArray {
        fun page(a: JSONArray?, i: Int) = a?.optJSONArray(i) ?: JSONArray()
        val seen = HashSet<String>()
        return JSONArray().also { out ->
            for (i in 0 until maxOf(l?.length() ?: 0, r?.length() ?: 0)) {
                val merged = list(page(b, i), page(l, i), page(r, i), t)
                out.put(JSONArray().also { p -> for (k in 0 until merged.length()) if (seen.add(merged.getJSONObject(k).opt("id").toString())) p.put(merged.get(k)) })
            }
        }
    }

    private fun keys(b: JSONObject, l: JSONObject, r: JSONObject, t: Tally): JSONObject =
        JSONObject().also { out -> (l.keys().asSequence() + r.keys().asSequence()).distinct().forEach { k -> pick(b.opt(k), l.opt(k), r.opt(k), t)?.let { out.put(k, it) } } }

    private fun byId(a: JSONArray): Map<String, JSONObject> =
        LinkedHashMap<String, JSONObject>().also { m -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { m.putIfAbsent(it.opt("id").toString(), it) } }

    /** The value of one item or setting after a merge; null means gone. */
    private fun pick(b: Any?, l: Any?, r: Any?, t: Tally): Any? {
        val cb = canon(b)
        val cl = canon(l)
        val cr = canon(r)
        return when {
            cl == cr -> l
            cl == cb -> r
            cr == cb -> l
            else -> {
                if (l != null && r != null) t.lost = true else t.kept++
                l ?: r
            }
        }
    }

    /** The same text for the same JSON, whatever the order of the keys. */
    private fun canon(v: Any?): String = when (v) {
        is JSONObject -> v.keys().asSequence().sorted().joinToString(",", "{", "}") { "\"$it\":" + canon(v.get(it)) }
        is JSONArray -> (0 until v.length()).joinToString(",", "[", "]") { canon(v.get(it)) }
        else -> v.toString()
    }
}
