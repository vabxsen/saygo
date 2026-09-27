package dev.saygo.app.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
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
    }
    private val handler = Handler(Looper.getMainLooper())
    private var bubble: ImageButton? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var pending: Runnable? = null
    private var gestureInFlight = false
    private var bubbleSuspended = false
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
        refreshBubble()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { cancelPending() }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        cancelPending()
        if (::prefs.isInitialized) refreshBubble()
    }

    fun refreshBubble() {
        removeBubble()
        if (!prefs.controlConsent || !prefs.showBubble) return
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
        if (gestureInFlight) { SessionState.report("A swipe is still finishing. Try again in a moment.", false); return }
        cancelPending()
        if (!prefs.controlConsent) { SessionState.report("Phone controls are off.", false); return }
        if (command is Command.Swipe && (originPackage == null || originPackage == packageName)) {
            SessionState.report("Open the app you want to scroll, then use the floating microphone.", false)
            return
        }
        val isBack = command == Command.Navigate(Destination.BACK)
        if (isBack && originPackage == null) {
            SessionState.report("Couldn’t identify the original app. Tap the microphone and try again.", false)
            return
        }
        val deadline = SystemClock.uptimeMillis() + 2_000
        pending = object : Runnable {
            override fun run() {
                if (!prefs.controlConsent) {
                    cancelPending()
                    SessionState.report("Phone controls are off.", false)
                    return
                }
                if (SystemClock.uptimeMillis() >= deadline) {
                    cancelPending()
                    SessionState.report("The screen wasn’t ready. Tap the floating microphone and try again.", false)
                    return
                }
                if (voiceUiVisible) { handler.postDelayed(this, 80); return }
                if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                    cancelPending(); SessionState.report("Unlock your phone before using phone controls.", false); return
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
                val bounds = Rect()
                root?.getBoundsInScreen(bounds)
                @Suppress("DEPRECATION")
                root?.recycle()
                // The disappearing voice window can briefly remain Android's active root.
                if (target == null || (target == packageName && originPackage != packageName)) {
                    handler.postDelayed(this, 80); return
                }
                if (target != originPackage) {
                    cancelPending()
                    SessionState.report("The app changed while you were speaking. Try again on the intended screen.", false)
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
        gestureInFlight = true
        cancelPending() // Clears preparation; gestureInFlight keeps the bubble detached.
        val accepted = dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 350)).build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { finishSwipe("Swipe sent.", true) }
            override fun onCancelled(gestureDescription: GestureDescription?) { finishSwipe("Swipe interrupted. Nothing else will run.", false) }
        }, handler)
        if (!accepted) finishSwipe("Android couldn’t perform this swipe.", false)
    }

    private fun cancelPending() {
        pending?.let(handler::removeCallbacks)
        pending = null
        bubbleSuspended = false
        restoreBubble()
    }
    private fun finishSwipe(message: String, success: Boolean) {
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
        if (current === this) { reference.clear(); mutableConnected.value = false }
        cancelPending()
        removeBubble()
        super.onDestroy()
    }
}





