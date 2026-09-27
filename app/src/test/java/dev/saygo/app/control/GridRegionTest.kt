package dev.saygo.app.control

import org.junit.Assert.*
import org.junit.Test

class GridRegionTest {
    @Test fun cellsCoverOddSizedOffsetWindowWithoutGapsOrOverlap() {
        val window = GridRegion(17, 43, 1096, 2211)
        val cells = (1..9).map { window.cell(it)!! }
        assertEquals(window.width * window.height, cells.sumOf { it.width * it.height })
        assertEquals(window.left, cells.first().left)
        assertEquals(window.top, cells.first().top)
        assertEquals(window.right, cells.last().right)
        assertEquals(window.bottom, cells.last().bottom)
        for (row in 0..2) {
            for (col in 0..1) assertEquals(cells[row * 3 + col].right, cells[row * 3 + col + 1].left)
        }
        for (i in 0..5) assertEquals(cells[i].bottom, cells[i + 3].top)
        cells.forEach { assertTrue(it.centerX >= it.left && it.centerX < it.right && it.centerY >= it.top && it.centerY < it.bottom) }
    }
    @Test fun nestedZoomStaysInsideTheChosenCell() {
        val window = GridRegion(0, 0, 900, 1800)
        assertEquals(GridRegion(300, 600, 600, 1200), window.cell(5))
        assertEquals(GridRegion(500, 1000, 600, 1200), window.cell(5)!!.cell(9))
        assertNull(window.cell(0))
        assertNull(window.cell(10))
        assertNull(GridRegion(0, 0, 2, 2).cell(5))
    }
}
