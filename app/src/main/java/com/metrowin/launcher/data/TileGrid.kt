package com.metrowin.launcher.data

import com.metrowin.launcher.model.TileSize

/** Where a tile sits on the Start grid, in small-tile units. */
data class Placement(val col: Int, val row: Int, val cols: Int, val rows: Int)

data class GridLayout(val placements: List<Placement>, val rows: Int)

/**
 * Start screen layout. The grid is 4 small tiles wide and is made of 2x2 blocks: a medium tile
 * fills one block, a wide tile fills a whole row of two blocks, and up to four small tiles share a
 * block.
 *
 * Tiles flow in pinning order and never go back to an earlier block: a new tile is appended at the
 * end, and a block that is only part full stays that way.
 */
object TileGrid {
    const val COLUMNS = 4
    private const val BLOCK = 2
    private const val BLOCKS_PER_ROW = COLUMNS / BLOCK

    fun place(sizes: List<TileSize>): GridLayout {
        val occupied = ArrayList<BooleanArray>()

        fun isFree(col: Int, row: Int): Boolean {
            while (occupied.size <= row) occupied += BooleanArray(COLUMNS)
            return !occupied[row][col]
        }

        fun isFree(col: Int, row: Int, cols: Int, rows: Int): Boolean =
            (row until row + rows).all { r -> (col until col + cols).all { c -> isFree(c, r) } }

        /** First spot for [size] in a block at or after [fromBlock] (blocks are numbered row by row). */
        fun find(size: TileSize, fromBlock: Int): Placement {
            var block = fromBlock
            while (true) {
                val row = block / BLOCKS_PER_ROW * BLOCK
                val blockCol = block % BLOCKS_PER_ROW
                val col = blockCol * BLOCK
                when (size) {
                    TileSize.Wide ->
                        if (blockCol == 0 && isFree(0, row, COLUMNS, BLOCK)) return Placement(0, row, COLUMNS, BLOCK)
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
                cursor = p.row / BLOCK * BLOCKS_PER_ROW + p.col / BLOCK
            }
        }
        return GridLayout(placements, placements.maxOfOrNull { it.row + it.rows } ?: 0)
    }
}
