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
            if (entry is Folder && entry.id == folderId) entry.children.map { Tile(it, TileSize.Medium) }
            else listOf(entry)
        }

    fun resize(entries: List<StartEntry>, id: String): List<StartEntry> =
        entries.map { entry ->
            when {
                entry.id != id -> entry
                entry is Tile -> entry.copy(size = entry.size.next())
                entry is Folder -> entry.copy(size = entry.size.next())
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

    fun setStyle(entries: List<StartEntry>, id: String, style: TileStyle): List<StartEntry> =
        entries.map { if (it is Tile && it.id == id) it.copy(style = style) else it }

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
                else -> Folder(newFolderId(), name = "", size = TileSize.Medium, children = listOf(entry.id, dragged.id))
            }
        }
    }

    /** Takes an app out of a folder and puts it on Start right after it. */
    fun removeFromFolder(entries: List<StartEntry>, folderId: String, childId: String): List<StartEntry> =
        entries.flatMap { entry ->
            if (entry is Folder && entry.id == folderId && childId in entry.children) {
                dissolveIfTooSmall(entry.copy(children = entry.children - childId)) + Tile(childId, TileSize.Medium)
            } else {
                listOf(entry)
            }
        }

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
        1 -> listOf(Tile(folder.children.single(), TileSize.Medium))
        else -> listOf(folder)
    }
}
