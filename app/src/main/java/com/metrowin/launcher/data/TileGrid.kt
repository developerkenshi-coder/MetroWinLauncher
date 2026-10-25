package com.metrowin.launcher.data

import com.metrowin.launcher.model.TileSize

/** Where a tile sits on the Start grid, in small-tile units. */
data class Placement(val col: Int, val row: Int, val cols: Int, val rows: Int)

data class GridLayout(val placements: List<Placement>, val rows: Int)

/**
 * A free place where the tile being dragged can be dropped: the cell or area at [col], [row] that is [cols] by [rows]
 * cells. [after] is the index (in the list of sizes that was laid out) of the tile the dropped one will come after, and
 * that is what puts it there.
 */
data class DropTarget(val col: Int, val row: Int, val cols: Int, val rows: Int, val after: Int)

/**
 * Start screen layout. The grid is made of 2x2 blocks, two blocks across ([TileGrid.DEFAULT_COLUMNS]
 * small tiles) or three with "show more tiles": a medium tile fills one block, a wide tile fills two
 * blocks side by side, and up to four small tiles share a block.
 *
 * Tiles flow in pinning order and never go back to an earlier block: a new tile is appended at the
 * end, and a block that is only part full stays that way, until a small tile is dropped in one of its
 * free cells (see [holes]), which puts it right after the small tiles of that block in the order.
 */
object TileGrid {
    const val DEFAULT_COLUMNS = 4
    const val MORE_COLUMNS = 6
    private const val BLOCK = 2
    private const val MAX_COLUMNS = 12

    /** Width of a small tile, in dp, that the portrait phone grid has: four columns on 360 dp, six with "more tiles". */
    private const val UNIT_DP = 79.5f
    private const val MORE_UNIT_DP = 51f
    private const val GAP_DP = 6f
    private const val SIDES_DP = 24f

    /**
     * How many columns of small tiles fit a screen [widthDp] wide, in whole blocks, so the tiles keep the size they
     * have on a portrait phone (4 columns, 6 with [more]) and a wider screen, such as a phone in landscape, gets more
     * of them instead of bigger ones.
     */
    fun columnsFor(widthDp: Float, more: Boolean): Int {
        val unit = if (more) MORE_UNIT_DP else UNIT_DP
        val fit = (widthDp - SIDES_DP + GAP_DP) / (unit + GAP_DP)
        return (BLOCK * Math.round(fit / BLOCK)).coerceIn(if (more) MORE_COLUMNS else DEFAULT_COLUMNS, MAX_COLUMNS)
    }

    /**
     * The free places where the tile at [dragged] can be dropped to put it there, instead of swapping it with a tile.
     * [sizes] are the cells laid out and [leader] says which of them lead a tile or folder (the apps of an open folder
     * follow it and move with it, so a tile never goes between them). Two kinds of place:
     *
     *  - for a small tile, the free cells of every block that holds one to three small tiles, apart from its own block.
     *    The free cells of a block are always its last ones, as small tiles fill a block in reading order, and a tile put
     *    right after the last small tile of a block in the order takes the first of them. The cells of a block stay where
     *    they are when the tile moves, so the picture the user drops on is the one the tile joins;
     *  - for a tile of any size, the empty room that a tile before it in the order leaves, such as the block beside four
     *    small tiles when the tile after them is wide. The tile is tried there: taken out, put back after each tile that
     *    comes before it, and the layout is run again to see where it lands, so a place is always exactly where the tile
     *    ends up. Only tiles before it count, as taking a tile out does not move the ones before it, so what the user
     *    sees is what happens; and only room that is empty now.
     */
    fun dropTargets(sizes: List<TileSize>, columns: Int, dragged: Int, leader: List<Boolean> = List(sizes.size) { true }): List<DropTarget> {
        if (dragged !in sizes.indices || !leader[dragged]) return emptyList()
        val current = place(sizes, columns)
        val blocksPerRow = columns / BLOCK
        fun block(p: Placement) = p.row / BLOCK * blocksPerRow + p.col / BLOCK

        // The tiles as groups of cells: a leader and what follows it up to the next leader.
        val groups = ArrayList<IntRange>()
        var start = 0
        for (i in 1..sizes.size) if (i == sizes.size || leader[i]) {
            groups += start until i
            start = i
        }
        val draggedGroup = groups.first { dragged in it }
        val draggedSize = sizes[dragged]
        val own = current.placements[dragged]
        val found = LinkedHashSet<DropTarget>()

        // What the other tiles occupy now, cell by cell.
        val occupied = HashSet<Int>()
        current.placements.forEachIndexed { i, p ->
            if (i !in draggedGroup) for (r in p.row until p.row + p.rows) for (c in p.col until p.col + p.cols) occupied += r * columns + c
        }
        fun free(col: Int, row: Int, cols: Int, rows: Int) =
            (row until row + rows).none { r -> (col until col + cols).any { c -> r * columns + c in occupied } }

        if (draggedSize == TileSize.Small) {
            val smalls = HashMap<Int, MutableList<Int>>()
            current.placements.forEachIndexed { index, p ->
                if (sizes[index] == TileSize.Small) smalls.getOrPut(block(p)) { mutableListOf() } += index
            }
            val ownBlock = block(own)
            for ((block, indices) in smalls.toSortedMap()) {
                if (block == ownBlock || indices.size >= BLOCK * BLOCK) continue
                val row = block / blocksPerRow * BLOCK
                val col = block % blocksPerRow * BLOCK
                val used = indices.map { current.placements[it] }.map { (it.row - row) * BLOCK + (it.col - col) }.toSet()
                for (cell in 0 until BLOCK * BLOCK) if (cell !in used) found += DropTarget(col + cell % BLOCK, row + cell / BLOCK, 1, 1, indices.last())
            }
        }

        // Room left empty before the tile: where it lands when put back after each tile that comes before it.
        val landings = LinkedHashMap<Placement, Int>()
        for (group in groups) {
            if (group.first >= draggedGroup.first) break
            val order = ArrayList<TileSize>()
            for (g in groups) {
                if (g.first > group.first) break
                g.forEach { order += sizes[it] }
            }
            val at = order.size
            draggedGroup.forEach { order += sizes[it] }
            landings[place(order, columns).placements[at]] = group.first
        }
        for ((landed, after) in landings) {
            if (landed == own || landed.row + landed.rows > current.rows) continue
            if (draggedSize == TileSize.Small) {
                // A block with nothing in it: any of its four cells.
                val row = landed.row / BLOCK * BLOCK
                val col = landed.col / BLOCK * BLOCK
                if (!free(col, row, BLOCK, BLOCK)) continue
                for (cell in 0 until BLOCK * BLOCK) found += DropTarget(col + cell % BLOCK, row + cell / BLOCK, 1, 1, after)
            } else if (free(landed.col, landed.row, landed.cols, landed.rows)) {
                found += DropTarget(landed.col, landed.row, landed.cols, landed.rows, after)
            }
        }
        return found.toList()
    }

    fun place(sizes: List<TileSize>, columns: Int = DEFAULT_COLUMNS): GridLayout {
        require(columns % BLOCK == 0) { "columns must be a whole number of blocks" }
        val blocksPerRow = columns / BLOCK
        val occupied = ArrayList<BooleanArray>()

        fun isFree(col: Int, row: Int): Boolean {
            while (occupied.size <= row) occupied += BooleanArray(columns)
            return !occupied[row][col]
        }

        fun isFree(col: Int, row: Int, cols: Int, rows: Int): Boolean =
            (row until row + rows).all { r -> (col until col + cols).all { c -> isFree(c, r) } }

        /** First spot for [size] in a block at or after [fromBlock] (blocks are numbered row by row). */
        fun find(size: TileSize, fromBlock: Int): Placement {
            var block = fromBlock
            while (true) {
                val row = block / blocksPerRow * BLOCK
                val blockCol = block % blocksPerRow
                val col = blockCol * BLOCK
                when (size) {
                    TileSize.Wide ->
                        if (blockCol + 1 < blocksPerRow && isFree(col, row, size.cols, BLOCK)) {
                            return Placement(col, row, size.cols, BLOCK)
                        }
                    TileSize.Medium ->
                        if (isFree(col, row, BLOCK, BLOCK)) return Placement(col, row, BLOCK, BLOCK)
                    TileSize.Small ->
                        for (dy in 0 until BLOCK) for (dx in 0 until BLOCK) {
                            if (isFree(col + dx, row + dy)) return Placement(col + dx, row + dy, 1, 1)
                        }
                }
                block++
            }
        }

        var cursor = 0
        val placements = sizes.map { size ->
            find(size, cursor).also { p ->
                for (r in p.row until p.row + p.rows) for (c in p.col until p.col + p.cols) occupied[r][c] = true
                cursor = p.row / BLOCK * blocksPerRow + p.col / BLOCK
            }
        }
        return GridLayout(placements, placements.maxOfOrNull { it.row + it.rows } ?: 0)
    }
}
