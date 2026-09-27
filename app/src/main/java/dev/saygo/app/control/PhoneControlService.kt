package dev.saygo.app.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageButton
import dev.saygo.app.MainActivity
import dev.saygo.app.R
import dev.saygo.app.VoiceActivity
import dev.saygo.app.commands.Command
import dev.saygo.app.commands.Destination
import dev.saygo.app.commands.Direction
import dev.saygo.app.commands.GridOperation
import dev.saygo.app.data.Preferences
import dev.saygo.app.data.SessionState
import kotlin.math.abs
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PhoneControlService : AccessibilityService() {
    companion object {
        private val mutableConnected = MutableStateFlow(false)
        val connected = mutableConnected.asStateFlow()
        private var reference = WeakReference<PhoneControlService>(null)
        val current: PhoneControlService? get() = reference.get()
        var voiceUiVisible: Boolean = false
            set(value) { field = value; current?.grid?.setVisible(!value) }
    }
    private val handler = Handler(Looper.getMainLooper())
    private var bubble: ImageButton? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var pending: Runnable? = null
    private var gestureInFlight = false
    private var activeDrag: HeldDrag? = null
    private var bubbleSuspended = false
    private var grid: GridOverlay? = null
    private val expireGrid = Runnable { dismissGrid() }
    private val validateGridWindow = Runnable {
        val active = grid
        if (active != null && !voiceUiVisible) {
            val root = rootInActiveWindow
            val bounds = Rect()
            root?.getBoundsInScreen(bounds)
            if (root == null || !active.matches(root.packageName?.toString(), root.windowId, bounds)) dismissGrid()
            @Suppress("DEPRECATION")
            root?.recycle()
        }
    }
    private var screenReceiverRegistered = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { cancelActions() }
    }
    private lateinit var prefs: Preferences
    private val windows get() = getSystemService(WindowManager::class.java)

    override fun onServiceConnected() {
        prefs = Preferences(this)
        if (!prefs.controlConsent) {
            disableSelf()
            return
        }
        reference = WeakReference(this)
        mutableConnected.value = true
        if (!screenReceiverRegistered) {
            ContextCompat.registerReceiver(this, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
            screenReceiverRegistered = true
        }
        refreshBubble()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val active = grid ?: return
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && event.eventTime >= active.createdAt &&
            event.packageName?.toString() == active.origin && event.windowId == active.windowId) {
            dismissGrid()
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            // Events may describe an old window. Validate Android's current active root after
            // the transition, including switches back to an already-existing Activity.
            handler.removeCallbacks(validateGridWindow)
            handler.postDelayed(validateGridWindow, 100)
        }
    }
    override fun onInterrupt() { cancelActions() }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        cancelActions()
        if (::prefs.isInitialized) refreshBubble()
    }

    fun refreshBubble() {
        removeBubble()
        if (!prefs.controlConsent || !prefs.showBubble) { dismissGrid(); return }
        val size = dp(56)
        val params = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = resources.displayMetrics.widthPixels - size - dp(16); y = dp(240) }
        val button = object : ImageButton(this) {
            override fun performClick(): Boolean = super.performClick()
        }.apply {
            setImageResource(R.drawable.ic_voice)
            contentDescription = "saygo: speak a command. Drag to move."
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF084BDD.toInt()) }
            elevation = dp(8).toFloat()
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener {
                if (voiceUiVisible || getSystemService(KeyguardManager::class.java).isKeyguardLocked) return@setOnClickListener
                if (!prefs.speechConsent || checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    startActivity(Intent(this@PhoneControlService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    SessionState.report("Set up microphone access in saygo first.", false)
                } else {
                    val node = rootInActiveWindow
                    val origin = node?.packageName?.toString()
                    @Suppress("DEPRECATION")
                    node?.recycle()
                    startActivity(Intent(this@PhoneControlService, VoiceActivity::class.java)
                        .putExtra(VoiceActivity.EXTRA_ORIGIN, origin)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
        var startX = 0; var startY = 0; var downX = 0f; var downY = 0f; var dragged = false
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX = params.x; startY = params.y; downX = event.rawX; downY = event.rawY; dragged = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) dragged = true
                    if (dragged) {
                        params.x = (startX + dx.toInt()).coerceIn(0, (resources.displayMetrics.widthPixels - size).coerceAtLeast(0))
                        params.y = (startY + dy.toInt()).coerceIn(0, (resources.displayMetrics.heightPixels - size - dp(32)).coerceAtLeast(0))
                        windows.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!dragged) view.performClick(); true }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        bubble = button
        bubbleParams = params
        restoreBubble()
    }

    /** Waits for the listening Activity to leave, then validates the original target. */
    fun executeWhenReady(command: Command, originPackage: String?) {
        if (gestureInFlight) { SessionState.report("A gesture is still finishing. Try again in a moment.", false); return }
        cancelPending()
        if (command !is Command.Grid) dismissGrid()
        if (command == Command.Grid(GridOperation.HIDE)) {
            dismissGrid(); SessionState.report("Grid hidden."); return
        }
        if (!prefs.controlConsent) { dismissGrid(); SessionState.report("Phone controls are off.", false); return }
        if ((command is Command.Swipe || command is Command.Pinch || command is Command.Tap || command is Command.EditText || command is Command.Grid) && (originPackage == null || originPackage == packageName)) {
            SessionState.report("Open the app you want to control, then use the floating microphone.", false)
            return
        }
        val isBack = command == Command.Navigate(Destination.BACK)
        if (isBack && originPackage == null) {
            SessionState.report("Couldn’t identify the original app. Tap the microphone and try again.", false)
            return
        }
        var deadline = SystemClock.uptimeMillis() + 2_000
        pending = object : Runnable {
            private var repeatQuickSettingsAt: Long? = null
            private var preparedPinchWindow: Int? = null
            private var preparedPinchBounds: Rect? = null
            override fun run() {
                if (!prefs.controlConsent) {
                    dismissGrid()
                    cancelPending()
                    SessionState.report("Phone controls are off.", false)
                    return
                }
                if (SystemClock.uptimeMillis() >= deadline) {
                    dismissGrid()
                    cancelPending()
                    SessionState.report("The screen wasn’t ready. Tap the floating microphone and try again.", false)
                    return
                }
                if (voiceUiVisible) { handler.postDelayed(this, 80); return }
                if (getSystemService(KeyguardManager::class.java).isKeyguardLocked || !getSystemService(android.os.PowerManager::class.java).isInteractive) {
                    dismissGrid(); cancelPending(); SessionState.report("Unlock your phone before using phone controls.", false); return
                }
                if (command is Command.DeviceControl) {
                    val repeatAt = repeatQuickSettingsAt
                    if (repeatAt != null) {
                        if (SystemClock.uptimeMillis() < repeatAt) {
                            handler.postDelayed(this, repeatAt - SystemClock.uptimeMillis())
                            return
                        }
                        pending = null
                        // A global panel can take focus without changing this service's
                        // cached windows. Refresh before checking current input focus.
                        if (android.os.Build.VERSION.SDK_INT >= 33) clearCache()
                        val root = getWindows().firstOrNull { it.isFocused }?.root
                        val stillInPanel = root?.packageName?.toString() == "com.android.systemui"
                        @Suppress("DEPRECATION")
                        root?.recycle()
                        // Never reopen the panel after the user has left SystemUI.
                        if (stillInPanel) DeviceControls.execute(this@PhoneControlService, command.action)
                        return
                    }
                    val accepted = DeviceControls.execute(this@PhoneControlService, command.action)
                    // Android 16 can stop at the collapsed shade on its first expansion
                    // after boot with animations disabled. One idempotent native repeat
                    // completes that same request; it stays in the cancellable queue.
                    val needsRepeat = accepted && command.action == dev.saygo.app.commands.DeviceAction.QUICK_SETTINGS &&
                        android.os.Build.VERSION.SDK_INT >= 36 &&
                        android.provider.Settings.Global.getFloat(contentResolver,
                            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
                    if (needsRepeat) {
                        repeatQuickSettingsAt = SystemClock.uptimeMillis() + 800
                        deadline = repeatQuickSettingsAt!! + 800
                        handler.postDelayed(this, 800)
                    } else pending = null
                    return
                }
                if (command is Command.Navigate && !isBack) {
                    pending = null
                    val action = when (command.destination) {
                        Destination.BACK -> GLOBAL_ACTION_BACK
                        Destination.HOME -> GLOBAL_ACTION_HOME
                        Destination.RECENTS -> GLOBAL_ACTION_RECENTS
                    }
                    val ok = performGlobalAction(action)
                    SessionState.report(if (ok) "${command.destination.name.lowercase().replaceFirstChar { it.uppercase() }} requested." else "This navigation action isn’t available here.", ok)
                    return
                }
                val root = rootInActiveWindow
                val target = root?.packageName?.toString()
                val targetWindow = root?.windowId ?: -1
                val bounds = Rect()
                root?.getBoundsInScreen(bounds)
                // A node command owns/recycles this snapshot after all target checks.
                if (command is Command.Tap || command is Command.EditText) {
                    if (target == null || target == packageName) {
                        @Suppress("DEPRECATION")
                        root?.recycle()
                        handler.postDelayed(this, 80)
                        return
                    }
                    pending = null
                    try {
                        if (target != originPackage) {
                            SessionState.report("The app changed while you were speaking. Try again on the intended screen.", false)
                        } else if (root != null) {
                            NodeActions.execute(root, command)
                        }
                    } finally {
                        @Suppress("DEPRECATION")
                        root?.recycle()
                    }
                    return
                }
                @Suppress("DEPRECATION")
                root?.recycle()
                // The disappearing voice window can briefly remain Android's active root.
                if (target == null || (target == packageName && originPackage != packageName)) {
                    handler.postDelayed(this, 80); return
                }
                if (target != originPackage) {
                    dismissGrid()
                    cancelPending()
                    SessionState.report("The app changed while you were speaking. Try again on the intended screen.", false)
                    return
                }
                if (command is Command.Grid) {
                    if (bounds.isEmpty) { cancelPending(); dismissGrid(); SessionState.report("This screen has no usable window.", false); return }
                    if (command.operation == GridOperation.SHOW) {
                        cancelPending()
                        dismissGrid()
                        grid = GridOverlay(this@PhoneControlService, target!!, targetWindow, bounds)
                        extendGridTimeout()
                        SessionState.report("Grid shown. Say zoom cell 5 or tap cell 5. Hide grid closes it.")
                        return
                    }
                    val active = grid
                    if (active == null || !active.matches(target, targetWindow, bounds)) {
                        cancelPending(); dismissGrid(); SessionState.report("Show a new grid on this screen first.", false); return
                    }
                    when (command.operation) {
                        GridOperation.ZOOM -> {
                            cancelPending()
                            val ok = command.cell?.let(active::zoom) == true
                            extendGridTimeout()
                            SessionState.report(if (ok) "Grid zoomed. Choose a cell from 1 to 9." else "This grid cannot zoom further. Tap a cell or say grid back.", ok)
                        }
                        GridOperation.BACK -> {
                            cancelPending()
                            val ok = active.back()
                            extendGridTimeout()
                            SessionState.report(if (ok) "Previous grid shown." else "Already at the full grid.", ok)
                        }
                        GridOperation.TAP, GridOperation.LONG_PRESS, GridOperation.DRAG -> {
                            val cell = command.cell?.let(active.region::cell)
                            if (cell == null) { cancelPending(); SessionState.report("Choose a cell from 1 to 9.", false); return }
                            val destination = command.destinationCell?.let(active.region::cell)
                            if (command.operation == GridOperation.DRAG && (destination == null || destination == cell)) {
                                cancelPending(); SessionState.report("Choose two different cells from 1 to 9.", false); return
                            }
                            if (!bubbleSuspended) {
                                bubbleSuspended = true
                                active.setVisible(false)
                                bubble?.let { if (it.isAttachedToWindow) windows.removeViewImmediate(it) }
                                handler.postDelayed(this, 80)
                                return
                            }
                            if (command.operation == GridOperation.DRAG) {
                                dismissGrid()
                                drag(cell, checkNotNull(destination), target!!, targetWindow, bounds)
                                return
                            }
                            val longPress = command.operation == GridOperation.LONG_PRESS
                            dismissGrid()
                            val path = Path().apply { moveTo(cell.centerX, cell.centerY) }
                            dispatchPath(path, if (longPress) ViewConfiguration.getLongPressTimeout().toLong() + 150 else 70,
                                if (longPress) "Long press sent." else "Tap sent.")
                        }
                        else -> Unit
                    }
                    return
                }
                if (command is Command.Pinch) {
                    if (bounds.width() < dp(80) || bounds.height() < dp(80) || bounds.left < 0 || bounds.top < 0) {
                        cancelPending(); SessionState.report("This window is too small for a two-finger gesture.", false); return
                    }
                    if (preparedPinchWindow == null) {
                        preparedPinchWindow = targetWindow
                        preparedPinchBounds = Rect(bounds)
                        bubbleSuspended = true
                        bubble?.let { if (it.isAttachedToWindow) windows.removeViewImmediate(it) }
                        // Settle both microphone and any dismissed grid before touching.
                        handler.postDelayed(this, 80)
                        return
                    }
                    if (preparedPinchWindow != targetWindow || preparedPinchBounds != bounds) {
                        cancelPending(); SessionState.report("The window changed. Try again on the intended screen.", false); return
                    }
                    pinch(command.zoomIn, bounds)
                    return
                }
                if (isBack) {
                    pending = null
                    val ok = performGlobalAction(GLOBAL_ACTION_BACK)
                    SessionState.report(if (ok) "Back requested." else "This navigation action isn’t available here.", ok)
                } else if (command is Command.Swipe) {
                    val button = bubble
                    if (button?.isAttachedToWindow == true) {
                        bubbleSuspended = true
                        windows.removeViewImmediate(button)
                        // InputDispatcher needs a window update before the next touch starts.
                        // Re-enter this validation block after that update, including consent/target checks.
                        handler.postDelayed(this, 80)
                        return
                    }
                    swipe(command.direction, bounds)
                }
            }
        }
        handler.post(pending!!)
    }

    private fun swipe(direction: Direction, bounds: Rect) {
        if (bounds.isEmpty) { cancelPending(); SessionState.report("This screen doesn’t expose a usable window.", false); return }
        val centerX = bounds.exactCenterX(); val centerY = bounds.exactCenterY()
        val left = bounds.left + bounds.width() * .22f; val right = bounds.left + bounds.width() * .78f
        val top = bounds.top + bounds.height() * .28f; val bottom = bounds.top + bounds.height() * .72f
        val path = Path().apply {
            when (direction) {
                Direction.UP -> { moveTo(centerX, bottom); lineTo(centerX, top) }
                Direction.DOWN -> { moveTo(centerX, top); lineTo(centerX, bottom) }
                Direction.LEFT -> { moveTo(right, centerY); lineTo(left, centerY) }
                Direction.RIGHT -> { moveTo(left, centerY); lineTo(right, centerY) }
            }
        }
        dispatchPath(path, 350, "Swipe sent.")
    }

    private fun pinch(zoomIn: Boolean, bounds: Rect) {
        val horizontal = bounds.width() >= bounds.height()
        val span = maxOf(bounds.width(), bounds.height())
        val near = span * .18f
        val far = span * .36f
        val start = if (zoomIn) near else far
        val end = if (zoomIn) far else near
        val paths = listOf(-1, 1).map { side ->
            Path().apply {
                moveTo(bounds.exactCenterX() + if (horizontal) side * start else 0f,
                    bounds.exactCenterY() + if (horizontal) 0f else side * start)
                lineTo(bounds.exactCenterX() + if (horizontal) side * end else 0f,
                    bounds.exactCenterY() + if (horizontal) 0f else side * end)
            }
        }
        // Android 10 samples injected motion every 100 ms; keep enough steps near the scaling threshold.
        val duration = if (android.os.Build.VERSION.SDK_INT == 29) 1_200L else 500L
        dispatchPaths(paths, duration, if (zoomIn) "Zoom-in gesture sent." else "Zoom-out gesture sent.")
    }

    private fun dispatchPath(path: Path, duration: Long, successMessage: String) =
        dispatchPaths(listOf(path), duration, successMessage)

    private fun dispatchPaths(paths: List<Path>, duration: Long, successMessage: String) {
        gestureInFlight = true
        cancelPending() // Clears preparation; gestureInFlight keeps the bubble detached.
        val gesture = GestureDescription.Builder().apply {
            paths.forEach { addStroke(GestureDescription.StrokeDescription(it, 0, duration)) }
        }.build()
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { finishGesture(successMessage, true) }
            override fun onCancelled(gestureDescription: GestureDescription?) { finishGesture("Gesture interrupted. Nothing else will run.", false) }
        }, handler)
        if (!accepted) finishGesture("Android couldn’t perform this gesture.", false)
    }

    private fun drag(start: GridRegion, end: GridRegion, origin: String, windowId: Int, bounds: Rect) {
        gestureInFlight = true
        cancelPending()
        val operation = HeldDrag(this, handler, start.centerX, start.centerY, end.centerX, end.centerY,
            canMove = {
                val root = rootInActiveWindow
                val now = Rect()
                root?.getBoundsInScreen(now)
                val sameWindow = root?.packageName?.toString() == origin && root.windowId == windowId && now == bounds
                @Suppress("DEPRECATION")
                root?.recycle()
                current === this && prefs.controlConsent && !voiceUiVisible && sameWindow &&
                    !getSystemService(KeyguardManager::class.java).isKeyguardLocked &&
                    getSystemService(android.os.PowerManager::class.java).isInteractive
            },
            onFinished = { message, success -> activeDrag = null; finishGesture(message, success) })
        activeDrag = operation
        operation.start()
    }

    /** Cancels queued input. Already dispatched input cannot be undone. */
    fun cancelActions(): Boolean {
        activeDrag?.cancel()
        cancelPending()
        dismissGrid()
        return gestureInFlight
    }

    fun dismissGrid() {
        handler.removeCallbacks(validateGridWindow)
        handler.removeCallbacks(expireGrid)
        grid?.remove()
        grid = null
    }
    private fun extendGridTimeout() {
        handler.removeCallbacks(expireGrid)
        handler.postDelayed(expireGrid, 60_000)
    }

    private fun cancelPending() {
        pending?.let(handler::removeCallbacks)
        pending = null
        bubbleSuspended = false
        restoreBubble()
    }
    private fun finishGesture(message: String, success: Boolean) {
        gestureInFlight = false
        if (current !== this) return
        restoreBubble()
        SessionState.report(message, success)
    }

    private fun restoreBubble() {
        if (gestureInFlight || bubbleSuspended || current !== this || !prefs.controlConsent || !prefs.showBubble) return
        val button = bubble ?: return
        val params = bubbleParams ?: return
        if (!button.isAttachedToWindow) windows.addView(button, params)
    }

    private fun removeBubble() {
        bubble?.let { if (it.isAttachedToWindow) windows.removeViewImmediate(it) }
        bubble = null
        bubbleParams = null
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onDestroy() {
        cancelActions()
        if (screenReceiverRegistered) { unregisterReceiver(screenReceiver); screenReceiverRegistered = false }
        if (current === this) { reference.clear(); mutableConnected.value = false }
        cancelPending()
        removeBubble()
        super.onDestroy()
    }
}





