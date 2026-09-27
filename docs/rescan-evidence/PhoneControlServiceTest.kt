package dev.saygo.app

import android.app.UiAutomation
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import dev.saygo.app.commands.Command
import dev.saygo.app.commands.Direction
import dev.saygo.app.commands.Destination
import dev.saygo.app.control.PhoneControlService
import dev.saygo.app.data.Preferences
import dev.saygo.app.data.SessionState
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Isolated emulator/device test: restores the prior accessibility configuration afterward. */
class PhoneControlServiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var automation: UiAutomation
    private var previousServices = "null"
    private var previousEnabled = "0"
    private var previousConsent = false
    private var previousBubble = true

    @Before fun enableTestService() {
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        previousServices = shell("settings get secure enabled_accessibility_services")
        previousEnabled = shell("settings get secure accessibility_enabled")
        val prefs = Preferences(context)
        previousConsent = prefs.controlConsent
        previousBubble = prefs.showBubble
        prefs.controlConsent = true
        prefs.showBubble = false
        shell("settings put secure enabled_accessibility_services dev.saygo.app/dev.saygo.app.control.PhoneControlService")
        shell("settings put secure accessibility_enabled 1")
        await("service connection") { PhoneControlService.current != null }
    }

    @After fun restoreConfiguration() {
        instrumentation.runOnMainSync { PhoneControlService.voiceUiVisible = false; PhoneControlService.current?.onInterrupt() }
        if (previousServices == "null" || previousServices.isEmpty()) shell("settings delete secure enabled_accessibility_services")
        else {
            require(previousServices.matches(Regex("[a-zA-Z0-9_.$/:]+")))
            shell("settings put secure enabled_accessibility_services $previousServices")
        }
        if (previousEnabled.matches(Regex("[01]"))) shell("settings put secure accessibility_enabled $previousEnabled")
        Preferences(context).apply { controlConsent = previousConsent; showBubble = previousBubble }
    }

    @Test fun dispatchesExactlyOneGestureToTheValidatedForegroundApp() {
        openSettings()
        val before = SessionState.feedback.value.sequence
        instrumentation.runOnMainSync { PhoneControlService.current!!.executeWhenReady(Command.Swipe(Direction.UP), "com.android.settings") }
        await("gesture callback") { SessionState.feedback.value.sequence > before }
        assertEquals("Swipe sent.", SessionState.feedback.value.title)
        assertTrue(SessionState.feedback.value.success)
        val completed = SessionState.feedback.value.sequence
        SystemClock.sleep(500)
        assertEquals(completed, SessionState.feedback.value.sequence)
    }

    @Test fun rejectsTheGestureWhenTheForegroundAppChanged() {
        openSettings()
        val before = SessionState.feedback.value.sequence
        instrumentation.runOnMainSync { PhoneControlService.current!!.executeWhenReady(Command.Swipe(Direction.UP), "com.example.anotherapp") }
        await("target rejection") { SessionState.feedback.value.sequence > before }
        assertFalse(SessionState.feedback.value.success)
        assertTrue(SessionState.feedback.value.title.contains("app changed"))
    }

    @Test fun interruptionCancelsWorkWaitingForTheVoicePanel() {
        openSettings()
        val before = SessionState.feedback.value.sequence
        instrumentation.runOnMainSync {
            PhoneControlService.voiceUiVisible = true
            PhoneControlService.current!!.executeWhenReady(Command.Swipe(Direction.UP), "com.android.settings")
            PhoneControlService.current!!.onInterrupt()
            PhoneControlService.voiceUiVisible = false
        }
        SystemClock.sleep(500)
        assertEquals(before, SessionState.feedback.value.sequence)
    }


    @Test fun everySwipeDirectionIsAcceptedByAndroid() {
        Direction.entries.forEach { direction ->
            openSettings()
            val before = SessionState.feedback.value.sequence
            instrumentation.runOnMainSync {
                PhoneControlService.current!!.executeWhenReady(Command.Swipe(direction), "com.android.settings")
            }
            await("$direction gesture completion") { SessionState.feedback.value.sequence > before }
            assertEquals("Swipe sent.", SessionState.feedback.value.title)
        }
    }

    @Test fun homeBackAndRecentsDispatchSuccessfully() {
        Destination.entries.forEach { destination ->
            openSettings()
            val before = SessionState.feedback.value.sequence
            instrumentation.runOnMainSync {
                PhoneControlService.current!!.executeWhenReady(Command.Navigate(destination), "com.android.settings")
            }
            await("$destination navigation result") { SessionState.feedback.value.sequence > before }
            assertTrue(SessionState.feedback.value.title, SessionState.feedback.value.success)
            if (destination == Destination.HOME) {
                await("home foreground") { automation.rootInActiveWindow?.packageName?.toString()?.contains("launcher") == true }
            }
        }
    }

    @Test fun ownAppAndUnknownOriginsAreRejected() {
        listOf(null, context.packageName).forEach { origin ->
            instrumentation.runOnMainSync {
                PhoneControlService.current!!.executeWhenReady(Command.Swipe(Direction.UP), origin)
            }
            assertFalse(SessionState.feedback.value.success)
            assertTrue(SessionState.feedback.value.title.contains("floating microphone"))
        }
    }

    @Test fun withdrawnConsentRejectsActions() {
        Preferences(context).controlConsent = false
        instrumentation.runOnMainSync {
            PhoneControlService.current!!.executeWhenReady(Command.Navigate(Destination.HOME), null)
        }
        assertFalse(SessionState.feedback.value.success)
        assertEquals("Phone controls are off.", SessionState.feedback.value.title)
    }

    @Test fun aVoicePanelThatDoesNotCloseTimesOutWithoutAnAction() {
        val before = SessionState.feedback.value.sequence
        instrumentation.runOnMainSync {
            PhoneControlService.voiceUiVisible = true
            PhoneControlService.current!!.executeWhenReady(Command.Navigate(Destination.HOME), null)
        }
        await("bounded screen wait") { SessionState.feedback.value.sequence > before }
        assertFalse(SessionState.feedback.value.success)
        assertTrue(SessionState.feedback.value.title.contains("screen wasn’t ready"))
    }


    @Test fun withdrawingConsentAlsoCancelsAlreadyQueuedNavigation() {
        val before = SessionState.feedback.value.sequence
        instrumentation.runOnMainSync {
            PhoneControlService.voiceUiVisible = true
            PhoneControlService.current!!.executeWhenReady(Command.Navigate(Destination.HOME), null)
            Preferences(context).controlConsent = false
            PhoneControlService.voiceUiVisible = false
        }
        await("queued action cancelled") { SessionState.feedback.value.sequence > before }
        assertFalse(SessionState.feedback.value.success)
        assertEquals("Phone controls are off.", SessionState.feedback.value.title)
    }


    @Test fun floatingMicrophoneCanBeShownHiddenAndDraggedWithoutClicking() {
        openSettings()
        val info = automation.serviceInfo
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        instrumentation.runOnMainSync {
            Preferences(context).showBubble = true
            PhoneControlService.current!!.refreshBubble()
        }
        await("visible floating microphone") { bubbleNode() != null }
        val before = android.graphics.Rect()
        bubbleNode()!!.getBoundsInScreen(before)
        val x = before.exactCenterX()
        val y = before.exactCenterY()
        val down = SystemClock.uptimeMillis()
        fun touch(action: Int, px: Float, py: Float) {
            val event = android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, px, py, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(automation.injectInputEvent(event, true))
            event.recycle()
        }
        touch(android.view.MotionEvent.ACTION_DOWN, x, y)
        touch(android.view.MotionEvent.ACTION_MOVE, x - 180, y + 100)
        touch(android.view.MotionEvent.ACTION_UP, x - 180, y + 100)
        val after = android.graphics.Rect()
        await("bubble moved") {
            bubbleNode()?.getBoundsInScreen(after)
            after.left < before.left - 100 && after.top > before.top + 50
        }
        assertEquals("com.android.settings", automation.rootInActiveWindow?.packageName?.toString())
        instrumentation.runOnMainSync {
            Preferences(context).showBubble = false
            PhoneControlService.current!!.refreshBubble()
        }
        await("bubble hidden") { bubbleNode() == null }
    }

    @Test fun disablingServiceRemovesItsBubbleAndConnectionState() {
        val info = automation.serviceInfo
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        instrumentation.runOnMainSync {
            Preferences(context).showBubble = true
            PhoneControlService.current!!.refreshBubble()
        }
        await("visible bubble") { bubbleNode() != null }
        instrumentation.runOnMainSync { PhoneControlService.current!!.disableSelf() }
        await("disconnected service") { PhoneControlService.current == null && !PhoneControlService.connected.value }
        await("removed bubble") { bubbleNode() == null }
    }


    /** Characterizes a Back action surviving a foreground-app change. */
    @Test fun rescanBackStillRunsAfterOriginAppChanges() {
        openSettings()
        val before = SessionState.feedback.value.sequence
        instrumentation.runOnMainSync {
            PhoneControlService.voiceUiVisible = true
            PhoneControlService.current!!.executeWhenReady(Command.Navigate(Destination.BACK), "com.android.settings")
        }
        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        await("different foreground app") {
            PhoneControlService.current?.rootInActiveWindow?.packageName?.toString() == context.packageName
        }
        instrumentation.runOnMainSync { PhoneControlService.voiceUiVisible = false }
        await("navigation result") { SessionState.feedback.value.sequence > before }
        val observation = "origin=com.android.settings; foreground before dispatch=${context.packageName}; feedback=${SessionState.feedback.value}"
        java.io.File(context.filesDir, "rescan-back.txt").writeText(observation)
        assertEquals(observation, "Back requested.", SessionState.feedback.value.title)
        assertTrue(SessionState.feedback.value.success)
    }

    /** A passing diagnostic means this defect is reproduced, not fixed. */
    @Test fun rescanBubbleInterceptsCommandWhenDraggedOntoSwipeStart() {
        openSettings()
        val info = automation.serviceInfo
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        instrumentation.runOnMainSync {
            Preferences(context).showBubble = true
            PhoneControlService.current!!.refreshBubble()
        }
        await("bubble visible") { bubbleNode() != null }
        val window = android.graphics.Rect()
        instrumentation.runOnMainSync { PhoneControlService.current!!.rootInActiveWindow!!.getBoundsInScreen(window) }
        val targetX = window.exactCenterX()
        val targetY = window.top + window.height() * .72f
        val initial = android.graphics.Rect()
        bubbleNode()!!.getBoundsInScreen(initial)
        val down = SystemClock.uptimeMillis()
        fun touch(action: Int, x: Float, y: Float) {
            val event = android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(automation.injectInputEvent(event, true))
            event.recycle()
        }
        touch(android.view.MotionEvent.ACTION_DOWN, initial.exactCenterX(), initial.exactCenterY())
        touch(android.view.MotionEvent.ACTION_MOVE, targetX, targetY)
        touch(android.view.MotionEvent.ACTION_UP, targetX, targetY)
        val placed = android.graphics.Rect()
        await("bubble at gesture start") {
            bubbleNode()?.getBoundsInScreen(placed)
            placed.contains(targetX.toInt(), targetY.toInt())
        }
        fun capture(name: String) {
            val file = java.io.File(context.filesDir, name)
            file.outputStream().use { automation.takeScreenshot()!!.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        capture("rescan-bubble-before.png")
        val before = SessionState.feedback.value.sequence
        instrumentation.runOnMainSync {
            PhoneControlService.current!!.executeWhenReady(Command.Swipe(Direction.UP), "com.android.settings")
        }
        await("swipe result") { SessionState.feedback.value.sequence > before }
        val after = android.graphics.Rect()
        // Accessibility nodes can lag the completed touch gesture; poll a fresh node.
        await("bubble position after swipe") {
            bubbleNode()?.getBoundsInScreen(after)
            after.top < placed.top - 200
        }
        capture("rescan-bubble-after.png")
        val observation = "window=$window placed=$placed after=$after feedback=${SessionState.feedback.value}"
        java.io.File(context.filesDir, "rescan-bubble.txt").writeText(observation)
        assertEquals(observation, "Swipe sent.", SessionState.feedback.value.title)
        assertTrue("Expected to reproduce interception: $observation", after.top < placed.top - 200)
    }

    private fun bubbleNode(): android.view.accessibility.AccessibilityNodeInfo? {
        fun find(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
            if (node.contentDescription?.toString() == "saygo: speak a command. Drag to move.") return node
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                find(child)?.let { return it }
            }
            return null
        }
        automation.windows.forEach { window -> window.root?.let { find(it)?.let { found -> return found } } }
        return null
    }

    private fun openSettings() {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("Settings foreground") {
            val root = PhoneControlService.current?.rootInActiveWindow
            val name = root?.packageName?.toString()
            @Suppress("DEPRECATION")
            root?.recycle()
            name == "com.android.settings"
        }
    }
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
    private fun await(label: String, predicate: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10_000
        while (!predicate() && SystemClock.uptimeMillis() < end) SystemClock.sleep(50)
        assertTrue("Timed out waiting for $label", predicate())
    }
}
