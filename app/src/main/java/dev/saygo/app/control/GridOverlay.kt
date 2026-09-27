package dev.saygo.app.control

import androidx.core.graphics.withTranslation
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/** A visual coordinate guide; it never intercepts touch or stores a screen image. */
// Coordinates are absolute screen coordinates, including in RTL layouts.
@android.annotation.SuppressLint("RtlHardcoded")
internal class GridOverlay(context: Context, val origin: String, val windowId: Int, bounds: Rect) {
    val createdAt = android.os.SystemClock.uptimeMillis()
    val sourceBounds = Rect(bounds)
    private val history = mutableListOf(GridRegion(bounds.left, bounds.top, bounds.right, bounds.bottom))
    val region get() = history.last()
    val level get() = history.size
    private val windows = context.getSystemService(WindowManager::class.java)
    private val pixelDensity = context.resources.displayMetrics.density
    private val view = object : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.withTranslation(-sourceBounds.left.toFloat(), -sourceBounds.top.toFloat()) {
                paint.color = 0x16084BDD
                paint.style = Paint.Style.FILL
                canvas.drawRect(region.left.toFloat(), region.top.toFloat(), region.right.toFloat(), region.bottom.toFloat(), paint)
                for (number in 1..9) {
                    val cell = region.cell(number) ?: continue
                    paint.color = 0xDD084BDD.toInt()
                    paint.strokeWidth = pixelDensity
                    paint.style = Paint.Style.STROKE
                    canvas.drawRect(cell.left.toFloat(), cell.top.toFloat(), cell.right.toFloat(), cell.bottom.toFloat(), paint)
                    val radius = minOf(15 * pixelDensity, cell.width * .42f, cell.height * .42f)
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(cell.centerX, cell.centerY, radius, paint)
                    paint.color = Color.WHITE
                    paint.textAlign = Paint.Align.CENTER
                    paint.textSize = radius * 1.15f
                    paint.isFakeBoldText = true
                    canvas.drawText(number.toString(), cell.centerX, cell.centerY - (paint.ascent() + paint.descent()) / 2, paint)
                }
            }
        }
    }.apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES }

    init {
        val params = WindowManager.LayoutParams(bounds.width(), bounds.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = bounds.left
            y = bounds.top
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        update()
        windows.addView(view, params)
    }

    fun matches(packageName: String?, id: Int, bounds: Rect) = origin == packageName && windowId == id && sourceBounds == bounds
    fun zoom(number: Int): Boolean {
        val next = region.cell(number) ?: return false
        if (level >= 3 || next.width < 9 || next.height < 9) return false
        history.add(next)
        update()
        return true
    }
    fun back(): Boolean {
        if (history.size <= 1) return false
        history.removeAt(history.lastIndex)
        update()
        return true
    }
    fun setVisible(visible: Boolean) { view.visibility = if (visible) View.VISIBLE else View.GONE }
    fun remove() { if (view.isAttachedToWindow) windows.removeViewImmediate(view) }
    private fun update() {
        view.contentDescription = "saygo grid, level $level. Cells 1 to 9, left to right, top to bottom."
        view.invalidate()
    }
}
