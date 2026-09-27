package dev.saygo.app.control

/** Integer boundaries keep adjacent cells continuous on displays of any size. */
internal data class GridRegion(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f

    fun cell(number: Int): GridRegion? {
        if (number !in 1..9 || width < 3 || height < 3) return null
        val column = (number - 1) % 3
        val row = (number - 1) / 3
        return GridRegion(left + width * column / 3, top + height * row / 3,
            left + width * (column + 1) / 3, top + height * (row + 1) / 3)
    }
}
