package com.metrowin.launcher.data

import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.model.TileStyle
import java.util.UUID

/**
 * Everything that changes the Start screen, as pure functions on the list of pinned entries: pinning,
 * resizing, dragging a tile to another place, dropping a tile on another to make a folder.
 */
object StartEntries {
    /** True when [id] is on Start, as a tile or inside a folder. */
    fun isPinned(entries: List<StartEntry>, id: String): Boolean =
        entries.any { it.id == id || (it is Folder && id in it.children) }

    fun pin(entries: List<StartEntry>, id: String): List<StartEntry> =
        if (isPinned(entries, id)) entries else entries + Tile(id, TileSize.Medium)

    /** Removes an app from Start wherever it is. Unpinning a folder removes its apps too. */
    fun unpin(entries: List<StartEntry>, id: String): List<StartEntry> =
        entries
            .filterNot { it.id == id }
            .flatMap { entry ->
                if (entry is Folder && id in entry.children) dissolveIfTooSmall(entry.copy(children = entry.children - id))
                else listOf(entry)
            }

    /** Turns a folder back into the tiles it holds, in its place. */
    fun release(entries: List<StartEntry>, folderId: String): List<StartEntry> =
        entries.flatMap { entry ->
            if (entry is Folder && entry.id == folderId) entry.children.mapIndexed { i, child ->
                Tile(child, TileSize.Medium, group = if (i == 0) entry.group else "", col = if (i == 0) entry.col else -1, row = if (i == 0) entry.row else -1)
            }
            else listOf(entry)
        }

    /** [onlyMediumOrWide]: the cycle skips the small and the large size (the full weather tile has no face for them). */
    fun resize(entries: List<StartEntry>, id: String, allowLarge: Boolean = true, onlyMediumOrWide: Boolean = false): List<StartEntry> =
        entries.map { entry ->
            fun next(size: TileSize) = if (onlyMediumOrWide) (if (size == TileSize.Medium) TileSize.Wide else TileSize.Medium) else size.next(allowLarge)
            when {
                entry.id != id -> entry
                entry is Tile -> entry.copy(size = next(entry.size))
                entry is Folder -> entry.copy(size = next(entry.size))
                else -> entry
            }
        }

    /** Sets the size of a tile or folder directly, instead of cycling through them like [resize]. */
    fun setSize(entries: List<StartEntry>, id: String, size: TileSize): List<StartEntry> =
        entries.map { entry ->
            when {
                entry.id != id -> entry
                entry is Tile -> entry.copy(size = size)
                entry is Folder -> entry.copy(size = size)
                else -> entry
            }
        }

    /** Gives a tile or folder its own color (ARGB), or 0 to take the theme's accent back. */
    fun setColor(entries: List<StartEntry>, id: String, color: Int): List<StartEntry> =
        entries.map { entry ->
            when {
                entry.id != id -> entry
                entry is Tile -> entry.copy(color = color)
                entry is Folder -> entry.copy(color = color)
                else -> entry
            }
        }

    fun setStyle(entries: List<StartEntry>, id: String, style: TileStyle): List<StartEntry> =
        entries.map { if (it is Tile && it.id == id) it.copy(style = style) else it }

    /**
     * Free mode: the tile [id] goes to the cell ([col], [row]). [current] is where every tile is now (by id): the ones that were let flow are fixed
     * there first, so that nothing else moves, whether they had a cell of their own or not.
     */
    fun place(entries: List<StartEntry>, id: String, col: Int, row: Int, current: Map<String, Pair<Int, Int>>): List<StartEntry> {
        val moved = entries.firstOrNull { it.id == id } ?: return entries
        if (col < 0 || row < 0) return entries
        return entries.map { entry ->
            if (entry.id == id) return@map if (col == entry.col && row == entry.row) entry else entry.at(col, row)
            val (c, r) = current[entry.id] ?: (entry.col to entry.row)
            // A tile under the one that lands is pushed out: it has no cell of its own any more and flows around the others.
            if (c >= 0 && r >= 0 && overlaps(c, r, entry.size, col, row, moved.size)) unplaced(entry)
            else if (c == entry.col && r == entry.row) entry else entry.at(c, r)
        }
    }

    private fun overlaps(c1: Int, r1: Int, s1: TileSize, c2: Int, r2: Int, s2: TileSize) =
        c1 < c2 + s2.cols && c2 < c1 + s1.cols && r1 < r2 + s2.rows && r2 < r1 + s1.rows

    /** [entry] without the cell it was given in free mode: it goes where the flow puts it. */
    fun unplaced(entry: StartEntry): StartEntry = entry.at(-1, -1)

    private fun StartEntry.at(col: Int, row: Int): StartEntry = when (this) {
        is Tile -> copy(col = col, row = row)
        is Folder -> copy(col = col, row = row)
    }

    /** [draggedId] takes the place of [targetId], and the entries in between shift by one. */
    fun moveTo(entries: List<StartEntry>, draggedId: String, targetId: String): List<StartEntry> {
        val from = entries.indexOfFirst { it.id == draggedId }
        val to = entries.indexOfFirst { it.id == targetId }
        if (from < 0 || to < 0 || from == to) return entries
        val moved = entries[from]
        return entries.toMutableList().apply {
            removeAt(from)
            add(to, moved)
        }
    }

    /** [draggedId] goes right after [afterId]: the way a small tile is dropped in a free cell next to other small tiles. */
    fun moveAfter(entries: List<StartEntry>, draggedId: String, afterId: String): List<StartEntry> {
        if (draggedId == afterId) return entries
        val moved = entries.firstOrNull { it.id == draggedId } ?: return entries
        if (entries.none { it.id == afterId }) return entries
        val without = entries.filterNot { it.id == draggedId }
        val result = without.toMutableList().apply { add(indexOfFirst { it.id == afterId } + 1, moved) }
        return if (result == entries) entries else result
    }

    /**
     * Drops the tile [draggedId] on [ontoId]. On a tile that makes a folder holding both, where the
     * target was. On a folder it adds the tile to it. Folders cannot go into folders.
     */
    fun merge(
        entries: List<StartEntry>,
        draggedId: String,
        ontoId: String,
        newFolderId: () -> String = { UUID.randomUUID().toString() },
    ): List<StartEntry> {
        val dragged = entries.firstOrNull { it.id == draggedId } as? Tile ?: return entries
        val target = entries.firstOrNull { it.id == ontoId } ?: return entries
        if (target.id == dragged.id) return entries
        val without = entries.filterNot { it.id == dragged.id }
        return without.map { entry ->
            when {
                entry.id != target.id -> entry
                entry is Folder -> entry.copy(children = entry.children + dragged.id)
                else -> Folder(newFolderId(), name = "", size = TileSize.Medium, children = listOf(entry.id, dragged.id), group = entry.group, col = entry.col, row = entry.row)
            }
        }
    }

    /**
     * Puts the tiles [ids] together at once. Among them there may be a folder: then the tiles join it. Without one
     * they make a new folder where the first of them was. Folders do not go into folders, so two folders (or a
     * lone tile) change nothing.
     */
    fun group(
        entries: List<StartEntry>,
        ids: Set<String>,
        newFolderId: () -> String = { UUID.randomUUID().toString() },
    ): List<StartEntry> {
        val picked = entries.filter { it.id in ids }
        val folders = picked.filterIsInstance<Folder>()
        val tiles = picked.filterIsInstance<Tile>()
        if (folders.size > 1) return entries
        if (folders.size == 1) {
            if (tiles.isEmpty()) return entries
            val folder = folders.single()
            val joining = tiles.mapTo(HashSet()) { it.id }
            return entries.filterNot { it.id in joining }
                .map { if (it.id == folder.id) folder.copy(children = folder.children + tiles.map(Tile::id)) else it }
        }
        if (tiles.size < 2) return entries
        val folder = Folder(newFolderId(), name = "", size = TileSize.Medium, children = tiles.map(Tile::id), group = tiles.first().group, col = tiles.first().col, row = tiles.first().row)
        val first = tiles.first().id
        val others = tiles.drop(1).mapTo(HashSet()) { it.id }
        return buildList {
            for (entry in entries) {
                when {
                    entry.id == first -> add(folder)
                    entry.id in others -> Unit
                    else -> add(entry)
                }
            }
        }
    }

    /** Whether [group] would change anything with these [ids]: enough tiles for a folder, or tiles for a folder. */
    fun canGroup(entries: List<StartEntry>, ids: Set<String>): Boolean = group(entries, ids) { "" } != entries

    /** Takes an app out of a folder and puts it on Start right after it. */
    fun removeFromFolder(entries: List<StartEntry>, folderId: String, childId: String): List<StartEntry> =
        entries.flatMap { entry ->
            if (entry is Folder && entry.id == folderId && childId in entry.children) {
                dissolveIfTooSmall(entry.copy(children = entry.children - childId)) + Tile(childId, TileSize.Medium)
            } else {
                listOf(entry)
            }
        }

    /** Gives the tile or folder [id] the title of the group it starts, or none when [title] is blank. */
    fun setGroup(entries: List<StartEntry>, id: String, title: String): List<StartEntry> {
        val clean = title.trim().take(MAX_GROUP)
        return entries.map { entry ->
            when {
                entry.id != id -> entry
                entry is Tile -> entry.copy(group = clean)
                entry is Folder -> entry.copy(group = clean)
                else -> entry
            }
        }
    }

    /**
     * The section [id] is in: from the tile or folder that carries the title of its group (or the start of the screen, before any title) up
     * to the next title. What the user means by "the whole group" when they pick one tile of it.
     */
    fun sectionIds(entries: List<StartEntry>, id: String): Set<String> {
        val at = entries.indexOfFirst { it.id == id }
        if (at < 0) return emptySet()
        val start = (at downTo 0).firstOrNull { entries[it].group.isNotBlank() } ?: 0
        val end = (at + 1 until entries.size).firstOrNull { entries[it].group.isNotBlank() } ?: entries.size
        return entries.subList(start, end).mapTo(LinkedHashSet()) { it.id }
    }

    const val MAX_GROUP = 32

    fun rename(entries: List<StartEntry>, folderId: String, name: String): List<StartEntry> =
        entries.map { if (it is Folder && it.id == folderId) it.copy(name = name.trim()) else it }

    /** Drops apps that are not installed any more. A folder left with one app becomes that app's tile. */
    fun prune(entries: List<StartEntry>, known: Set<String>): List<StartEntry> =
        entries.flatMap { entry ->
            when (entry) {
                is Tile -> if (entry.id in known) listOf(entry) else emptyList()
                is Folder -> dissolveIfTooSmall(entry.copy(children = entry.children.filter { it in known }))
            }
        }

    /** A folder needs two apps: with one it is just a tile, with none it is gone. */
    private fun dissolveIfTooSmall(folder: Folder): List<StartEntry> = when (folder.children.size) {
        0 -> emptyList()
        1 -> listOf(Tile(folder.children.single(), TileSize.Medium, group = folder.group))
        else -> listOf(folder)
    }
}
