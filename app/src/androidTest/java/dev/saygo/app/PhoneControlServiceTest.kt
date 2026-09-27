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


    @Test fun swipesReachTheTargetInEveryDirectionWithTheBubbleAtTheirStart() {
        showTestBubble()
        val evidence = mutableListOf<String>()
        Direction.entries.forEach { direction ->
            openGestureTarget()
            val bounds = android.graphics.Rect()
            instrumentation.runOnMainSync { PhoneControlService.current!!.rootInActiveWindow!!.getBoundsInScreen(bounds) }
            val x = when (direction) {
                Direction.LEFT -> bounds.left + bounds.width() * .78f
                Direction.RIGHT -> bounds.left + bounds.width() * .22f
                else -> bounds.exactCenterX()
            }
            val y = when (direction) {
                Direction.UP -> bounds.top + bounds.height() * .72f
                Direction.DOWN -> bounds.top + bounds.height() * .28f
                else -> bounds.exactCenterY()
            }
            dragBubbleTo(x, y)
            val placed = bubbleBounds()
            val before = SessionState.feedback.value.sequence
            instrumentation.runOnMainSync {
                PhoneControlService.current!!.executeWhenReady(Command.Swipe(direction), "dev.saygo.app.test")
            }
            await("$direction action callback") { SessionState.feedback.value.sequence > before }
            assertEquals("Expected gesture completion before checking receiver", "Swipe sent.", SessionState.feedback.value.title)
            await("$direction delivered to target") {
                automation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Received $direction")?.isNotEmpty() == true
            }
            await("$direction completion and bubble restored") {
                SessionState.feedback.value.sequence > before && bubbleNode() != null
            }
            assertEquals("Swipe sent.", SessionState.feedback.value.title)
            assertEquals("Bubble must preserve its position after $direction", placed, bubbleBounds())
            evidence += "$direction: target received gesture; bubble position retained at $placed"
            java.io.File(context.filesDir, "fix-swipe-results.txt").writeText(evidence.joinToString("\n"))
        }
        // Restoration must retain the native drag and click handlers.
        val beforeDrag = bubbleBounds()
        dragBubbleTo(beforeDrag.exactCenterX() + 70, beforeDrag.exactCenterY() + 50)
        assertTrue(bubbleBounds().left > beforeDrag.left + 40)
        val previousSpeech = Preferences(context).speechConsent
        try {
            Preferences(context).speechConsent = false
            assertTrue(bubbleNode()!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            await("restored bubble click opens microphone setup") { automation.rootInActiveWindow?.packageName?.toString() == context.packageName }
        } finally { Preferences(context).speechConsent = previousSpeech }
        java.io.File(context.filesDir, "fix-swipe-results.txt").writeText(evidence.joinToString("\n") + "\nPost-swipe dragging and clicking passed.")
    }

    @Test fun backIsRejectedIfTheOriginalForegroundAppChanged() {
        openSettings()
        val before = SessionState.feedback.value.sequence
        instrumentation.runOnMainSync {
            PhoneControlService.voiceUiVisible = true
            PhoneControlService.current!!.executeWhenReady(Command.Navigate(Destination.BACK), "com.android.settings")
        }
        openGestureTarget()
        instrumentation.runOnMainSync { PhoneControlService.voiceUiVisible = false }
        await("changed target rejected") { SessionState.feedback.value.sequence > before }
        assertFalse(SessionState.feedback.value.success)
        assertTrue(SessionState.feedback.value.title.contains("app changed"))
        assertEquals("dev.saygo.app.test", automation.rootInActiveWindow?.packageName?.toString())
    }

    @Test fun backRequiresAnOriginAndStillWorksFromSaygo() {
        instrumentation.runOnMainSync {
            PhoneControlService.current!!.executeWhenReady(Command.Navigate(Destination.BACK), null)
        }
        assertFalse(SessionState.feedback.value.success)
        assertTrue(SessionState.feedback.value.title.contains("original app"))
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        await("saygo foreground") { automation.rootInActiveWindow?.packageName?.toString() == context.packageName }
        val before = SessionState.feedback.value.sequence
        instrumentation.runOnMainSync {
            PhoneControlService.current!!.executeWhenReady(Command.Navigate(Destination.BACK), context.packageName)
        }
        await("Back completion") { SessionState.feedback.value.sequence > before }
        assertEquals("Back requested.", SessionState.feedback.value.title)
        await("Back leaves saygo") { automation.rootInActiveWindow?.packageName?.toString()?.let { it != context.packageName } == true }
    }

    @Test fun hidingTheBubbleDuringASwipeKeepsItHiddenAfterCompletion() {
        openGestureTarget()
        showTestBubble()
        val before = SessionState.feedback.value.sequence
        instrumentation.runOnMainSync {
            PhoneControlService.current!!.executeWhenReady(Command.Swipe(Direction.UP), "dev.saygo.app.test")
        }
        instrumentation.runOnMainSync {
            Preferences(context).showBubble = false
            PhoneControlService.current!!.refreshBubble()
        }
        await("swipe finished") { SessionState.feedback.value.sequence > before }
        assertEquals("Swipe sent.", SessionState.feedback.value.title)
        await("preference keeps bubble hidden") { bubbleNode() == null }
        showTestBubble()
    }

    @Test fun disablingControlsDuringASwipeCannotRestoreTheBubble() {
        openGestureTarget()
        showTestBubble()
        instrumentation.runOnMainSync {
            PhoneControlService.current!!.executeWhenReady(Command.Swipe(Direction.UP), "dev.saygo.app.test")
        }
        instrumentation.runOnMainSync {
            Preferences(context).controlConsent = false
            PhoneControlService.current!!.disableSelf()
        }
        await("disconnected") { PhoneControlService.current == null }
        SystemClock.sleep(500)
        assertNull(bubbleNode())
        assertFalse(PhoneControlService.connected.value)
    }

    @Test fun namedTapsDescriptionsParentsAndLongPressReachAnotherApp() {
        openControlTarget()
        listOf(
            Command.Tap("search") to "Exact tap",
            Command.Tap("Play media") to "Description tap",
            Command.Tap("Nested control") to "Parent tap",
            Command.Tap("Hold item", true) to "Long press received",
        ).forEach { (command, result) ->
            runControl(command)
            assertTrue(SessionState.feedback.value.title, SessionState.feedback.value.success)
            await(result) { screenHas(result) }
        }
    }

    @Test fun missingAmbiguousAndDisabledLabelsNeverClick() {
        openControlTarget()
        listOf("Missing", "Duplicate", "Unavailable", "Search then Send", "Sear").forEach { label ->
            runControl(Command.Tap(label))
            assertFalse(label, SessionState.feedback.value.success)
            assertTrue(screenHas("No action"))
        }
    }

    @Test fun dictationSelectionReplacementAndClearReachFocusedField() {
        openControlTarget()
        runControl(Command.EditText(dev.saygo.app.commands.TextOperation.INSERT, "wrong"))
        assertFalse(SessionState.feedback.value.success)
        runControl(Command.Tap("Message"))
        await("text field focused after tap") {
            val field = PhoneControlService.current?.rootInActiveWindow?.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
            field?.isEditable == true && field.isFocused && field.isVisibleToUser
        }
        assertTrue(SessionState.feedback.value.success)
        val literal = "Hello,  world! Then tap Send."
        runControl(Command.EditText(dev.saygo.app.commands.TextOperation.INSERT, literal))
        assertTrue(SessionState.feedback.value.title, SessionState.feedback.value.success)
        await("literal text in external field") { focusedText() == literal }
        runControl(Command.EditText(dev.saygo.app.commands.TextOperation.SELECT_ALL))
        assertTrue(SessionState.feedback.value.success)
        await("selection") {
            val node = automation.rootInActiveWindow?.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
            node?.textSelectionStart == 0 && node.textSelectionEnd == literal.length
        }
        runControl(Command.EditText(dev.saygo.app.commands.TextOperation.INSERT, "Replacement?"))
        await("selected text replaced") { focusedText() == "Replacement?" }
        runControl(Command.EditText(dev.saygo.app.commands.TextOperation.REPLACE, "Entire field."))
        await("whole field replaced") { focusedText() == "Entire field." }
        runControl(Command.EditText(dev.saygo.app.commands.TextOperation.CLEAR))
        await("field cleared") { focusedText().isNullOrEmpty() }
        assertTrue(screenHas("No action"))
        assertFalse(SessionState.feedback.value.title.contains(literal))
    }

    @Test fun queuedTapRejectsChangedAppAndQueuedTextRejectsRevokedConsent() {
        openControlTarget()
        var before = SessionState.feedback.value.sequence
        instrumentation.runOnMainSync {
            PhoneControlService.voiceUiVisible = true
            PhoneControlService.current!!.executeWhenReady(Command.Tap("Search"), "dev.saygo.app.test")
        }
        openSettings()
        instrumentation.runOnMainSync { PhoneControlService.voiceUiVisible = false }
        await("stale tap rejected") { SessionState.feedback.value.sequence > before }
        assertFalse(SessionState.feedback.value.success)
        assertTrue(SessionState.feedback.value.title.contains("app changed"))
        openControlTarget()
        runControl(Command.Tap("Message"))
        await("text field focused after tap") {
            val field = PhoneControlService.current?.rootInActiveWindow?.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
            field?.isEditable == true && field.isFocused && field.isVisibleToUser
        }
        before = SessionState.feedback.value.sequence
        instrumentation.runOnMainSync {
            PhoneControlService.voiceUiVisible = true
            PhoneControlService.current!!.executeWhenReady(Command.EditText(dev.saygo.app.commands.TextOperation.INSERT, "wrong"), "dev.saygo.app.test")
            Preferences(context).controlConsent = false
            PhoneControlService.voiceUiVisible = false
        }
        await("text cancelled") { SessionState.feedback.value.sequence > before }
        assertFalse(SessionState.feedback.value.success)
        assertTrue(focusedText().isNullOrEmpty())
    }

    @Test fun oldDisclosureDoesNotGrantNewScreenReadingAccess() {
        val storage = context.getSharedPreferences("saygo_preferences", android.content.Context.MODE_PRIVATE)
        val hadOld = storage.contains("control_disclosure_v1")
        val old = storage.getBoolean("control_disclosure_v1", false)
        try {
            Preferences(context).controlConsent = false
            storage.edit().putBoolean("control_disclosure_v1", true).commit()
            assertFalse(Preferences(context).controlConsent)
            runControl(Command.Tap("Search"))
            assertFalse(SessionState.feedback.value.success)
            assertEquals("Phone controls are off.", SessionState.feedback.value.title)
        } finally {
            storage.edit().apply { if (hadOld) putBoolean("control_disclosure_v1", old) else remove("control_disclosure_v1") }.commit()
        }
    }

    private fun openControlTarget() {
        context.startActivity(Intent().setClassName("dev.saygo.app.test", "dev.saygo.app.ControlTargetActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        await("control fixture") { automation.rootInActiveWindow?.packageName?.toString() == "dev.saygo.app.test" && screenHas("No action") }
    }
    private fun screenHas(text: String) = automation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.any { it.text?.toString() == text } == true
    private fun focusedText(): String? {
        val field = automation.rootInActiveWindow?.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT) ?: return null
        return if (field.isShowingHintText) "" else field.text?.toString()
    }
    private fun runControl(command: Command) {
        val before = SessionState.feedback.value.sequence
        instrumentation.runOnMainSync { PhoneControlService.current!!.executeWhenReady(command, "dev.saygo.app.test") }
        await("control response") { SessionState.feedback.value.sequence > before }
    }

    private fun showTestBubble() {
        val info = automation.serviceInfo
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        instrumentation.runOnMainSync {
            Preferences(context).showBubble = true
            PhoneControlService.current!!.refreshBubble()
        }
        await("floating microphone visible") { bubbleNode() != null }
    }

    private fun openGestureTarget() {
        context.startActivity(Intent().setClassName("dev.saygo.app.test", "dev.saygo.app.GestureTargetActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        await("gesture target ready") {
            val node = automation.rootInActiveWindow
            node?.packageName?.toString() == "dev.saygo.app.test" && node.findAccessibilityNodeInfosByText("Gesture target: ready").isNotEmpty()
        }
    }

    private fun bubbleBounds(): android.graphics.Rect = android.graphics.Rect().also { bubbleNode()!!.getBoundsInScreen(it) }

    private fun dragBubbleTo(x: Float, y: Float) {
        automation.waitForIdle(150, 3000)
        val before = bubbleBounds()
        val down = SystemClock.uptimeMillis()
        fun touch(action: Int, px: Float, py: Float) {
            val event = android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, px, py, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(automation.injectInputEvent(event, true))
            event.recycle()
        }
        touch(android.view.MotionEvent.ACTION_DOWN, before.exactCenterX(), before.exactCenterY())
        for (step in 1..12) {
            val fraction = step / 12f
            touch(android.view.MotionEvent.ACTION_MOVE,
                before.exactCenterX() + (x - before.exactCenterX()) * fraction,
                before.exactCenterY() + (y - before.exactCenterY()) * fraction)
            SystemClock.sleep(25)
        }
        touch(android.view.MotionEvent.ACTION_UP, x, y)
        var lastPosition: android.graphics.Rect? = null
        try {
            await("bubble positioned") {
                lastPosition = bubbleNode()?.let { android.graphics.Rect().also(it::getBoundsInScreen) }
                val position = lastPosition
                position != null && kotlin.math.abs(position.exactCenterX() - x) < 4 && kotlin.math.abs(position.exactCenterY() - y) < 4
            }
        } catch (error: AssertionError) {
            java.io.File(context.filesDir, "fix-drag.txt").writeText("from=$before target=($x,$y) actual=$lastPosition")
            throw error
        }
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
        while (SystemClock.uptimeMillis() < end) {
            if (predicate()) return
            SystemClock.sleep(50)
        }
        fail("Timed out waiting for $label")
    }
}
