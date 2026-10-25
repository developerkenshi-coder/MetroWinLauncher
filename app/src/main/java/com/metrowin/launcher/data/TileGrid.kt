package com.metrowin.launcher.data

import com.metrowin.launcher.model.TileSize

/** Where a tile sits on the Start grid, in small-tile units. */
data class Placement(val col: Int, val row: Int, val cols: Int, val rows: Int)

data class GridLayout(val placements: List<Placement>, val rows: Int)

/**
 * Start screen layout. The grid is made of 2x2 blocks, two blocks across ([TileGrid.DEFAULT_COLUMNS]
 * small tiles) or three with "show more tiles": a medium tile fills one block, a wide tile fills two
 * blocks side by side, and up to four small tiles share a block.
 *
 * Tiles flow in pinning order and never go back to an earlier block: a new tile is appended at the
 * end, and a block that is only part full stays that way.
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
