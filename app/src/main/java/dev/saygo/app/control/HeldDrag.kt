package dev.saygo.app.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.view.ViewConfiguration

/** One continuous pointer: hold, validate, then move and release. Never queues another command. */
internal class HeldDrag(
    private val service: AccessibilityService,
    private val handler: Handler,
    private val startX: Float,
    private val startY: Float,
    private val endX: Float,
    private val endY: Float,
    private val canMove: () -> Boolean,
    private val onFinished: (String, Boolean) -> Unit,
) {
    private var cancelled = false
    private var finished = false
    private var afterHold: Runnable? = null
    fun cancel() {
        cancelled = true
        afterHold?.let { handler.removeCallbacks(it); it.run() }
    }

    fun start() {
        val hold = GestureDescription.StrokeDescription(point(), 0, 1, true)
        dispatch(hold) {
            // Android drops stationary MOVE samples, so a continued point stroke can
            // complete immediately after DOWN regardless of its declared duration.
            // Keep the pointer down for a real timed interval before continuing it.
            val continuation = Runnable { continueDrag(hold) }
            afterHold = continuation
            if (cancelled) continuation.run()
            else handler.postDelayed(continuation, ViewConfiguration.getLongPressTimeout().toLong() + 150)
        }
    }

    private fun continueDrag(hold: GestureDescription.StrokeDescription) {
        afterHold = null
        if (finished) return
        val move = !cancelled && canMove()
        val path = point().apply { if (move) lineTo(endX, endY) }
        // Continuing the same stroke preserves ACTION_DOWN across the hold and movement.
        // If invalidated, release at the source; the initial hold cannot be undone.
        val release = hold.continueStroke(path, 0, if (move) 700 else 1, false)
        dispatch(release) {
            when {
                !move -> finish("Drag stopped before moving. The initial hold may have affected the app.", false)
                cancelled -> finish("Drag finished after cancellation. Check the target app.", false)
                else -> finish("Drag gesture sent.", true)
            }
        }
    }

    private fun point() = Path().apply { moveTo(startX, startY) }
    private fun dispatch(stroke: GestureDescription.StrokeDescription, complete: () -> Unit) {
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val accepted = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { if (!finished) complete() }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                finish("Drag interrupted. Nothing else will run.", false)
            }
        }, handler)
        if (!accepted) finish("Android couldn’t finish the drag. Check the target app.", false)
    }
    private fun finish(message: String, success: Boolean) {
        if (finished) return
        finished = true
        afterHold?.let(handler::removeCallbacks)
        afterHold = null
        onFinished(message, success)
    }
}
