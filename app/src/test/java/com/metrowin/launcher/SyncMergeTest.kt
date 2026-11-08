package com.metrowin.launcher

import com.metrowin.launcher.data.SyncCrypto
import com.metrowin.launcher.data.SyncEngine
import com.metrowin.launcher.data.SyncLocal
import com.metrowin.launcher.data.SyncMerge
import com.metrowin.launcher.data.SyncOutcome
import com.metrowin.launcher.data.SyncStorage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class SyncMergeTest {
    private fun item(id: String, text: String = id) = """{"id":"$id","text":"$text"}"""

    /** A layout file with the given tiles on one page, shortcuts, to-dos and settings. */
    private fun layout(
        tiles: List<String> = emptyList(),
        shortcuts: List<String> = emptyList(),
        todos: List<String> = emptyList(),
        settings: String = """{"theme":"dark","accent":1}""",
        car: String? = null,
    ) = """{"format":"metrowin-layout","version":2,"exportedAt":0,"pages":[[${tiles.joinToString(",")}]],"shortcuts":[${shortcuts.joinToString(",")}],""" +
        """"settings":$settings,"extras":{"todos":[${todos.joinToString(",")}],"reminders":[],"layouts":[]${car?.let { ""","car":$it""" } ?: ""}}}"""

    private fun ids(json: String, path: String): List<String> {
        val o = JSONObject(json)
        val a = when (path) {
            "tiles" -> o.getJSONArray("pages").getJSONArray(0)
            "shortcuts" -> o.getJSONArray("shortcuts")
            else -> o.getJSONObject("extras").getJSONArray(path)
        }
        return List(a.length()) { a.getJSONObject(it).getString("id") }
    }

    private fun text(json: String, path: String, id: String): String {
        val a = JSONObject(json).getJSONObject("extras").getJSONArray(path)
        return (0 until a.length()).map { a.getJSONObject(it) }.first { it.getString("id") == id }.getString("text")
    }

    private fun merge(base: String, local: String, remote: String) = SyncMerge.merge(base, local, remote)!!

    @Test
    fun additionsOnBothPhonesAreBothKept() {
        val base = layout(shortcuts = listOf(item("s1")), todos = listOf(item("t1")))
        val local = layout(shortcuts = listOf(item("s1"), item("s2")), todos = listOf(item("t1"), item("t2")))
        val remote = layout(shortcuts = listOf(item("s1"), item("s3")), todos = listOf(item("t1"), item("t3")))
        val m = merge(base, local, remote)
        assertEquals(listOf("s1", "s2", "s3"), ids(m.json, "shortcuts"))
        assertEquals(listOf("t1", "t2", "t3"), ids(m.json, "todos"))
        assertEquals(0, m.kept)
        assertFalse(m.lost)
    }

    @Test
    fun aTileAddedOnTheOtherPhoneAndOneRemovedHereBothHappen() {
        val base = layout(tiles = listOf(item("a"), item("b")))
        val local = layout(tiles = listOf(item("a")))
        val remote = layout(tiles = listOf(item("a"), item("b"), item("c")))
        assertEquals(listOf("a", "c"), ids(merge(base, local, remote).json, "tiles"))
    }

    @Test
    fun aDeletionOnOneSideIsTakenWhenTheOtherDidNotTouchIt() {
        val base = layout(todos = listOf(item("t1"), item("t2")))
        val m = merge(base, layout(todos = listOf(item("t1"), item("t2"))), layout(todos = listOf(item("t1"))))
        assertEquals(listOf("t1"), ids(m.json, "todos"))
        assertEquals(0, m.kept)
    }

    @Test
    fun anItemDeletedOnOnePhoneAndChangedOnTheOtherStays() {
        val base = layout(todos = listOf(item("t1", "milk"), item("t2")))
        // Phone A deleted t1, phone B edited it.
        val a = layout(todos = listOf(item("t2")))
        val b = layout(todos = listOf(item("t1", "oat milk"), item("t2")))
        val onA = merge(base, a, b)
        assertEquals(setOf("t1", "t2"), ids(onA.json, "todos").toSet())
        assertEquals("oat milk", text(onA.json, "todos", "t1"))
        assertEquals(1, onA.kept)
        assertFalse(onA.lost)
        // And the other way round.
        val onB = merge(base, b, a)
        assertEquals("oat milk", text(onB.json, "todos", "t1"))
        assertEquals(1, onB.kept)
    }

    @Test
    fun theSameItemChangedOnBothKeepsThisPhoneAndAsksForTheOtherToBeKeptAside() {
        val base = layout(todos = listOf(item("t1", "milk")))
        val m = merge(base, layout(todos = listOf(item("t1", "mine"))), layout(todos = listOf(item("t1", "theirs"))))
        assertEquals("mine", text(m.json, "todos", "t1"))
        assertTrue(m.lost)
    }

    @Test
    fun theSameChangeOnBothIsNotAConflict() {
        val base = layout(todos = listOf(item("t1", "milk")))
        val same = layout(todos = listOf(item("t1", "bread")))
        val m = merge(base, same, same)
        assertEquals("bread", text(m.json, "todos", "t1"))
        assertFalse(m.lost)
        assertEquals(0, m.kept)
    }

    @Test
    fun theOtherPhoneEndsWithTheSameItemsWhenNothingClashed() {
        // Phone A merges and uploads; phone B then merges A's file with its own layout and has the same items: the sync converges.
        val base = layout(shortcuts = listOf(item("s1")), todos = listOf(item("t1", "milk"), item("t2", "eggs")))
        val a = layout(shortcuts = listOf(item("s1"), item("s2")), todos = listOf(item("t1", "bread"), item("t2", "eggs")))
        val b = layout(shortcuts = listOf(item("s1"), item("s3")), todos = listOf(item("t1", "milk"), item("t2", "eggs"), item("t4")))
        val onA = merge(base, a, b)
        val onB = merge(base, b, onA.json)
        assertEquals(ids(onA.json, "shortcuts").toSet(), ids(onB.json, "shortcuts").toSet())
        assertEquals(ids(onA.json, "todos").toSet(), ids(onB.json, "todos").toSet())
        assertEquals(text(onA.json, "todos", "t1"), text(onB.json, "todos", "t1"))
        assertFalse(onA.lost || onB.lost)
    }

    // shortcut of the merge: a clash is won by "this phone" on both phones, so each keeps its own version (see docs/progetti/sincronizzazione.md).
    @Test
    fun aClashIsNotSettledForBothPhones() {
        val base = layout(todos = listOf(item("t1", "milk")))
        val a = layout(todos = listOf(item("t1", "mine")))
        val b = layout(todos = listOf(item("t1", "theirs")))
        val onA = merge(base, a, b)
        val onB = merge(base, b, onA.json)
        assertEquals("mine", text(onA.json, "todos", "t1"))
        assertEquals("theirs", text(onB.json, "todos", "t1"))
    }

    @Test
    fun withoutAConflictTheOrderOfThePhonesDoesNotChangeWhatIsKept() {
        val base = layout(shortcuts = listOf(item("s1"), item("s2")), todos = listOf(item("t1", "milk")))
        val a = layout(shortcuts = listOf(item("s1"), item("s2"), item("s3")), todos = listOf(item("t1", "bread")))
        val b = layout(shortcuts = listOf(item("s1"), item("s4")), todos = listOf(item("t1", "milk")))
        val ab = merge(base, a, b)
        val ba = merge(base, b, a)
        assertEquals(ids(ab.json, "shortcuts").toSet(), ids(ba.json, "shortcuts").toSet())
        assertEquals(setOf("s1", "s3", "s4"), ids(ab.json, "shortcuts").toSet())
        assertEquals(text(ab.json, "todos", "t1"), text(ba.json, "todos", "t1"))
        assertFalse(ab.lost || ba.lost)
    }

    // shortcut of the merge itself: the order is this phone's, see the comment on SyncMerge.list.
    @Test
    fun aListOrderedAgainOnlyOnTheOtherPhoneKeepsThisPhonesOrder() {
        val base = layout(shortcuts = listOf(item("s1"), item("s2")))
        val remote = layout(shortcuts = listOf(item("s2"), item("s1")))
        assertEquals(listOf("s1", "s2"), ids(merge(base, base, remote).json, "shortcuts"))
    }

    @Test
    fun noChangeOnEitherSideGivesTheSameLayout() {
        val base = layout(tiles = listOf(item("a")), shortcuts = listOf(item("s1")), todos = listOf(item("t1")))
        val m = merge(base, base, base)
        assertEquals(ids(base, "tiles"), ids(m.json, "tiles"))
        assertEquals(ids(base, "shortcuts"), ids(m.json, "shortcuts"))
        assertEquals(ids(base, "todos"), ids(m.json, "todos"))
        assertFalse(m.lost)
        assertEquals(0, m.kept)
    }

    @Test
    fun settingsAreMergedKeyByKey() {
        val base = layout(settings = """{"theme":"dark","accent":1,"font":"a"}""")
        val local = layout(settings = """{"theme":"light","accent":1,"font":"a"}""")
        val remote = layout(settings = """{"theme":"dark","accent":5,"font":"a"}""")
        val m = merge(base, local, remote)
        val s = JSONObject(m.json).getJSONObject("settings")
        assertEquals("light", s.getString("theme"))
        assertEquals(5, s.getInt("accent"))
        assertFalse(m.lost)
        // The same key changed on both: this phone wins, and the other value is kept aside.
        val clash = merge(base, local, layout(settings = """{"theme":"blue","accent":1,"font":"a"}"""))
        assertEquals("light", JSONObject(clash.json).getJSONObject("settings").getString("theme"))
        assertTrue(clash.lost)
    }

    @Test
    fun theCarIsTakenFromTheSideThatChangedIt() {
        val base = layout(car = """{"lat":1}""")
        val m = merge(base, base, layout(car = """{"lat":2}"""))
        assertEquals(2, JSONObject(m.json).getJSONObject("extras").getJSONObject("car").getInt("lat"))
    }

    @Test
    fun aTileMovedToAnotherPageOnBothPhonesIsNotDoubled() {
        fun two(a: List<String>, b: List<String>) =
            layout().replace(""""pages":[[]]""", """"pages":[[${a.joinToString(",")}],[${b.joinToString(",")}]]""")
        val base = two(listOf(item("a"), item("b")), emptyList())
        val m = merge(base, two(listOf(item("a")), listOf(item("b"))), two(listOf(item("a")), listOf(item("b"))))
        val pages = JSONObject(m.json).getJSONArray("pages")
        assertEquals(1, pages.getJSONArray(0).length())
        assertEquals(1, pages.getJSONArray(1).length())
        // One phone moved it to page 2, the other kept page 1 and added a tile: it stays on one page only.
        val m2 = merge(base, two(listOf(item("a")), listOf(item("b"))), two(listOf(item("a"), item("b"), item("c")), emptyList()))
        val all = (0 until 2).flatMap { p -> val a = JSONObject(m2.json).getJSONArray("pages").getJSONArray(p); List(a.length()) { a.getJSONObject(it).getString("id") } }
        assertEquals(listOf("a", "b", "c"), all.sorted())
    }

    @Test
    fun filesThatAreNotLayoutsGiveNoMerge() {
        assertNull(SyncMerge.merge("nope", layout(), layout()))
        assertNull(SyncMerge.merge(layout(), "{}", layout()))
    }

    // --- the engine, with two phones that both changed

    private class MemoryStorage : SyncStorage {
        val files = LinkedHashMap<String, ByteArray>()
        override fun read(name: String) = files[name]
        override fun write(name: String, bytes: ByteArray): Boolean {
            files[name] = bytes
            return true
        }
    }

    private val fast = 1_000

    @Test
    fun twoPhonesThatBothChangedEndUpWithBothChanges() {
        val folder = MemoryStorage()
        var clock = 1_000L
        val engine = SyncEngine(folder, { clock }, fast)
        val base = layout(todos = listOf(item("t1")))
        val first = engine.sync(SyncLocal(base, "pw", "A", "", 0)) as SyncOutcome.Uploaded
        // B joins, then edits and uploads.
        clock = 2_000L
        engine.sync(SyncLocal(layout(), "pw", "B", "", 0))
        clock = 3_000L
        val remoteLayout = layout(todos = listOf(item("t1"), item("tB")))
        val other = engine.sync(SyncLocal(remoteLayout, "pw", "B", first.hash, first.stamp, base)) as SyncOutcome.Uploaded
        assertEquals(3_000L, other.stamp)
        // A changed meanwhile: both changed, so the engine merges.
        clock = 4_000L
        val mine = layout(todos = listOf(item("t1"), item("tA")))
        val merged = engine.sync(SyncLocal(mine, "pw", "A", first.hash, first.stamp, base)) as SyncOutcome.Merged
        assertEquals(setOf("t1", "tA", "tB"), ids(merged.layoutJson, "todos").toSet())
        assertNull(merged.keptTheOtherAs)
        // No other-phone copy was needed, and the folder holds the merged layout.
        assertEquals(listOf(SyncEngine.FILE), folder.files.keys.toList())
        val payload = JSONObject(SyncCrypto.decrypt(folder.files.getValue(SyncEngine.FILE), "pw")!!.decodeToString())
        assertEquals(merged.layoutJson, payload.getString("layout"))
        assertEquals(4_000L, merged.stamp)
    }

    @Test
    fun aClashKeepsTheOtherPhonesFileAsBefore() {
        val folder = MemoryStorage()
        var clock = 1_000L
        val engine = SyncEngine(folder, { clock }, fast)
        val base = layout(todos = listOf(item("t1", "milk")))
        val first = engine.sync(SyncLocal(base, "pw", "A", "", 0)) as SyncOutcome.Uploaded
        clock = 2_000L
        engine.sync(SyncLocal(layout(todos = listOf(item("t1", "theirs"))), "pw", "B", first.hash, first.stamp, base))
        clock = 3_000L
        val merged = engine.sync(SyncLocal(layout(todos = listOf(item("t1", "mine"))), "pw", "A", first.hash, first.stamp, base)) as SyncOutcome.Merged
        assertEquals("metrowin-sync-other-2000.bin", merged.keptTheOtherAs)
        assertTrue(folder.files.containsKey("metrowin-sync-other-2000.bin"))
        assertEquals("mine", text(merged.layoutJson, "todos", "t1"))
    }

    @Test
    fun withoutTheLayoutOfTheLastSyncTheOldRuleApplies() {
        val folder = MemoryStorage()
        var clock = 1_000L
        val engine = SyncEngine(folder, { clock }, fast)
        val first = engine.sync(SyncLocal(layout(), "pw", "A", "", 0)) as SyncOutcome.Uploaded
        clock = 2_000L
        engine.sync(SyncLocal(layout(todos = listOf(item("tB"))), "pw", "B", first.hash, first.stamp))
        clock = 3_000L
        val out = engine.sync(SyncLocal(layout(todos = listOf(item("tA"))), "pw", "A", first.hash, first.stamp)) as SyncOutcome.Uploaded
        assertEquals("metrowin-sync-other-2000.bin", out.keptTheOtherAs)
    }

    // --- the number of iterations

    @Test
    fun newFilesUseTheCurrentIterationsAndOldOnesAreStillRead() {
        assertTrue(SyncCrypto.ITERATIONS >= 600_000)
        val old = SyncCrypto.encrypt("old file".toByteArray(), "pw", 200_000)
        assertEquals("old file", SyncCrypto.decrypt(old, "pw")!!.decodeToString())
        // The default is written into the header, and that is the number read back.
        val fresh = SyncCrypto.encrypt("new file".toByteArray(), "pw")
        assertEquals(SyncCrypto.ITERATIONS, ByteBuffer.wrap(fresh, 7, 4).getInt())
        assertEquals("new file", SyncCrypto.decrypt(fresh, "pw")!!.decodeToString())
    }
}
