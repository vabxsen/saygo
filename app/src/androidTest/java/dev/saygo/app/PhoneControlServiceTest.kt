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
        // A previous test's unbind can still be queued after its settings command returns.
        // Establish a genuinely disconnected state before waiting for a fresh binding.
        val otherServices = previousServices.split(':').filter { it != "null" && it.isNotBlank() && !it.startsWith("dev.saygo.app/") }
        if (otherServices.isEmpty()) shell("settings delete secure enabled_accessibility_services")
        else {
            val retained = otherServices.joinToString(":")
            require(retained.matches(Regex("[a-zA-Z0-9_.$/:]+")))
            shell("settings put secure enabled_accessibility_services $retained")
        }
        await("previous service disconnected") { PhoneControlService.current == null }
        val prefs = Preferences(context)
        previousConsent = prefs.controlConsent
        previousBubble = prefs.showBubble
        prefs.controlConsent = true
        prefs.showBubble = false
        val services = (otherServices + "dev.saygo.app/dev.saygo.app.control.PhoneControlService").joinToString(":")
        shell("settings put secure enabled_accessibility_services $services")
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
        assertTrue(SessionState.feedback.value.title, SessionState.feedback.value.title.contains("app changed"))
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
        assertTrue(SessionState.feedback.value.title, SessionState.feedback.value.title.contains("app changed"))
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
        assertTrue(SessionState.feedback.value.title, SessionState.feedback.value.title.contains("app changed"))
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

    @Test fun numberedGridDeliversTapsAcrossUnlabelledCanvasWithBubbleAtTarget() {
        val evidence = mutableListOf<String>()
        for (number in listOf(1, 3, 5, 7, 9)) {
            val bounds = openGridTarget()
            showTestBubble()
            val x = bounds.left + bounds.width() * (((number - 1) % 3) * 2 + 1) / 6f
            val y = bounds.top + bounds.height() * (((number - 1) / 3) * 2 + 1) / 6f
            dragBubbleTo(x, y)
            val placed = bubbleBounds()
            runControl(Command.Grid(dev.saygo.app.commands.GridOperation.SHOW))
            await("grid visible") { gridNode() != null }
            val actualBounds = android.graphics.Rect()
            val view = gridNode()!!
            instrumentation.runOnMainSync {
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                actualBounds.set(location[0], location[1], location[0] + view.width, location[1] + view.height)
            }
            assertEquals("Grid must use screen coordinates", bounds, actualBounds)
            runControl(Command.Grid(dev.saygo.app.commands.GridOperation.TAP, number))
            assertEquals("Tap sent.", SessionState.feedback.value.title)
            awaitGridTouch("Tap", x, y)
            await("bubble restored") { bubbleNode() != null }
            assertEquals(placed, bubbleBounds())
            assertNull(gridNode())
            evidence += "Cell $number: delivered at ($x, $y); overlay removed; microphone position retained."
        }
        java.io.File(context.filesDir, "grid-delivery.txt").writeText(evidence.joinToString("\n"))
    }

    @Test fun nestedGridZoomBackAndLongPressUseTheChosenArea() {
        val bounds = openGridTarget()
        enableWindowInspection()
        runControl(Command.Grid(dev.saygo.app.commands.GridOperation.SHOW))
        await("full grid drawn") { gridNode() != null }
        captureGrid("grid-full.png")
        runControl(Command.Grid(dev.saygo.app.commands.GridOperation.ZOOM, 5))
        captureGrid("grid-zoom.png")
        runControl(Command.Grid(dev.saygo.app.commands.GridOperation.ZOOM, 9))
        await("third grid level") { gridNode()?.contentDescription?.toString()?.contains("level 3") == true }
        runControl(Command.Grid(dev.saygo.app.commands.GridOperation.ZOOM, 1))
        assertFalse(SessionState.feedback.value.success)
        runControl(Command.Grid(dev.saygo.app.commands.GridOperation.BACK))
        assertTrue(SessionState.feedback.value.title, SessionState.feedback.value.success)
        await("second grid level; current=" + gridNode()?.contentDescription) { gridNode()?.contentDescription?.toString()?.contains("level 2") == true }
        runControl(Command.Grid(dev.saygo.app.commands.GridOperation.LONG_PRESS, 9))
        assertEquals("Long press sent.", SessionState.feedback.value.title)
        awaitGridTouch("Hold", bounds.left + bounds.width() * 11 / 18f, bounds.top + bounds.height() * 11 / 18f)
        assertNull(gridNode())
    }

    @Test fun gridHidesForVoiceAndDismissalCannotLeaveTheMicrophoneDetached() {
        openGridTarget()
        showTestBubble()
        runControl(Command.Grid(dev.saygo.app.commands.GridOperation.SHOW))
        val previousSpeechConsent = Preferences(context).speechConsent
        Preferences(context).speechConsent = false // Exercise the real panel without recording audio.
        try {
            val intent = Intent(context, VoiceActivity::class.java)
                .putExtra(VoiceActivity.EXTRA_ORIGIN, "dev.saygo.app.test")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            androidx.test.core.app.ActivityScenario.launch<VoiceActivity>(intent).use { panel ->
                await("actual voice panel hides grid") { PhoneControlService.voiceUiVisible && gridNode() == null }
                panel.onActivity {
                    PhoneControlService.current!!.executeWhenReady(Command.Grid(dev.saygo.app.commands.GridOperation.ZOOM, 5), "dev.saygo.app.test")
                    it.finish()
                }
                await("queued grid command completes on original app after voice panel closes") {
                    !PhoneControlService.voiceUiVisible && automation.rootInActiveWindow?.packageName?.toString() == "dev.saygo.app.test" &&
                        gridNode()?.contentDescription?.toString()?.contains("level 2") == true
                }
            }
        } finally { Preferences(context).speechConsent = previousSpeechConsent }
        val before = SessionState.feedback.value.sequence
        instrumentation.runOnMainSync {
            PhoneControlService.current!!.executeWhenReady(Command.Grid(dev.saygo.app.commands.GridOperation.TAP, 5), "dev.saygo.app.test")
            // Runs after preparation detaches the overlays, before its delayed dispatch.
            android.os.Handler(android.os.Looper.getMainLooper()).post { PhoneControlService.current!!.dismissGrid() }
        }
        await("cancelled coordinate action") { SessionState.feedback.value.sequence > before }
        assertFalse(SessionState.feedback.value.success)
        await("cancelled tap restores microphone") { bubbleNode() != null }
        assertTrue(screenHas("Grid receiver ready"))
    }

    @Test fun gridRejectsChangedWindowAndReopeningDoesNotReviveCoordinates() {
        openGridTarget()
        enableWindowInspection()
        runControl(Command.Grid(dev.saygo.app.commands.GridOperation.SHOW))
        instrumentation.runOnMainSync {
            val oldEvent = android.view.accessibility.AccessibilityEvent.obtain(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            oldEvent.packageName = "com.android.settings"
            oldEvent.eventTime = android.os.SystemClock.uptimeMillis() - 1000
            PhoneControlService.current!!.onAccessibilityEvent(oldEvent)
            oldEvent.recycle()
        }
        SystemClock.sleep(200) // Allow the debounced window validation to run.
        assertNotNull("Delayed events from before grid creation must not dismiss it", gridNode())
        openSettings()
        await("grid removed on app switch") { gridNode() == null }
        openGridTarget()
        runControl(Command.Grid(dev.saygo.app.commands.GridOperation.TAP, 5))
        assertFalse(SessionState.feedback.value.success)
        assertTrue(screenHas("Grid receiver ready"))
        runControl(Command.Grid(dev.saygo.app.commands.GridOperation.SHOW))
        // A new Activity from the same package has a different window identity.
        openControlTarget()
        await("grid removed on window switch") { gridNode() == null }
        runControl(Command.Grid(dev.saygo.app.commands.GridOperation.TAP, 5))
        assertFalse(SessionState.feedback.value.success)
        assertTrue(screenHas("No action"))
    }

    @Test fun revokedConsentAndScreenOffRemoveGridWithoutTouchingTarget() {
        openGridTarget()
        enableWindowInspection()
        runControl(Command.Grid(dev.saygo.app.commands.GridOperation.SHOW))
        Preferences(context).controlConsent = false
        runControl(Command.Grid(dev.saygo.app.commands.GridOperation.TAP, 5))
        assertFalse(SessionState.feedback.value.success)
        assertNull(gridNode())
        assertTrue(screenHas("Grid receiver ready"))
        Preferences(context).controlConsent = true
        runControl(Command.Grid(dev.saygo.app.commands.GridOperation.SHOW))
        try {
            shell("input keyevent KEYCODE_SLEEP")
            await("screen actually off") { !context.getSystemService(android.os.PowerManager::class.java).isInteractive }
            await("grid cleared on screen off") { gridNode() == null }
        } finally {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
        }
        await("same window restored after wake") { context.getSystemService(android.os.PowerManager::class.java).isInteractive && screenHas("Grid receiver ready") }
        runControl(Command.Grid(dev.saygo.app.commands.GridOperation.TAP, 5))
        assertFalse(SessionState.feedback.value.success)
        assertTrue(screenHas("Grid receiver ready"))
    }

    @Test fun gridHideCancelAndRotationRemoveTheOverlay() {
        openGridTarget()
        runControl(Command.Grid(dev.saygo.app.commands.GridOperation.SHOW))
        runControl(Command.Grid(dev.saygo.app.commands.GridOperation.HIDE))
        assertNull(gridNode())
        runControl(Command.Grid(dev.saygo.app.commands.GridOperation.SHOW))
        instrumentation.runOnMainSync { dev.saygo.app.commands.CommandExecutor(context).execute(Command.Stop) }
        assertNull(gridNode())
        runControl(Command.Grid(dev.saygo.app.commands.GridOperation.SHOW))
        val display = context.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val originalRotation = display.rotation
        val autoRotation = shell("settings get system accelerometer_rotation")
        try {
            assertTrue(automation.setRotation(if (originalRotation == android.view.Surface.ROTATION_90) UiAutomation.ROTATION_FREEZE_0 else UiAutomation.ROTATION_FREEZE_90))
            await("grid dismissed on rotation") { gridNode() == null }
        } finally {
            assertTrue(automation.setRotation(originalRotation))
            await("original display rotation restored") { display.rotation == originalRotation }
            automation.waitForIdle(300, 5000)
            if (autoRotation == "1") automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
    }

    @Test fun stableGridExpiresAfterOneMinuteAndShutdownRemovesIt() {
        openGridTarget()
        val started = SystemClock.uptimeMillis()
        runControl(Command.Grid(dev.saygo.app.commands.GridOperation.SHOW))
        assertNotNull(gridNode())
        while (gridNode() != null && SystemClock.uptimeMillis() - started < 65_000) SystemClock.sleep(200)
        assertNull("Grid should expire without another command", gridNode())
        assertTrue("A fresh stable grid should last one minute", SystemClock.uptimeMillis() - started >= 59_000)
        runControl(Command.Grid(dev.saygo.app.commands.GridOperation.SHOW))
        instrumentation.runOnMainSync { PhoneControlService.current!!.disableSelf() }
        await("service and grid removed") { PhoneControlService.current == null && gridNode() == null }
    }

    @Test fun pinchCommandsDeliverTwoPointersAndNativeScalingWithOverlaysAtTheTouchPoint() {
        val evidence = mutableListOf<String>()
        for (offset in listOf(false, true)) for (zoomIn in listOf(true, false)) {
            val bounds = openPinchTarget(offset)
            if (offset) assertTrue("Fixture must exercise offset screen coordinates", bounds.left > 0 && bounds.top > 0)
            showTestBubble()
            val startDistance = bounds.width() * if (zoomIn) .12f else .40f
            dragBubbleTo(bounds.exactCenterX() - startDistance, bounds.exactCenterY())
            val placed = bubbleBounds()
            runControl(Command.Grid(dev.saygo.app.commands.GridOperation.SHOW))
            await("grid visible before pinch") { gridNode() != null }
            val phrase = if (zoomIn) "Zoom in" else "Zoom out"
            val before = SessionState.feedback.value.sequence
            instrumentation.runOnMainSync {
                val executor = dev.saygo.app.commands.CommandExecutor(context)
                executor.execute(executor.parse(phrase)!!, "dev.saygo.app.test")
            }
            await("pinch callback") { SessionState.feedback.value.sequence > before }
            assertEquals(if (zoomIn) "Zoom-in gesture sent." else "Zoom-out gesture sent.", SessionState.feedback.value.title)
            var receipt = ""
            await("two fingers actually scale the other app") {
                receipt = automation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Zoom=")?.firstOrNull()?.text?.toString().orEmpty()
                val factor = Regex("Zoom=([0-9.]+)").find(receipt)?.groupValues?.get(1)?.toFloatOrNull()
                receipt.contains("pointers=2 complete=true down=1 up=1") && factor != null &&
                    (if (zoomIn) factor > 1.2f else factor < .85f)
            }
            assertNull(gridNode())
            await("microphone restored after two-finger gesture") { bubbleNode() != null }
            assertEquals(placed, bubbleBounds())
            evidence += "$phrase offset=$offset bounds=$bounds: $receipt; microphone restored"
        }
        java.io.File(context.filesDir, "pinch-delivery.txt").writeText(evidence.joinToString("\n"))
    }

    @Test fun pinchRejectsAReplacedWindowDuringOverlayPreparation() {
        openPinchTarget(false)
        showTestBubble()
        val before = SessionState.feedback.value.sequence
        try {
            instrumentation.runOnMainSync {
                PhoneControlService.current!!.executeWhenReady(Command.Pinch(true), "dev.saygo.app.test")
                android.os.Handler(android.os.Looper.getMainLooper()).post { PhoneControlService.voiceUiVisible = true }
            }
            await("pinch overlays detached") { bubbleNode() == null }
            openGridTarget() // Same package, different window.
            instrumentation.runOnMainSync { PhoneControlService.voiceUiVisible = false }
            await("replaced pinch window rejected") { SessionState.feedback.value.sequence > before }
            assertFalse(SessionState.feedback.value.success)
            assertEquals("The window changed. Try again on the intended screen.", SessionState.feedback.value.title)
            assertTrue(screenHas("Grid receiver ready"))
            await("microphone restored after rejected pinch") { bubbleNode() != null }
        } finally { instrumentation.runOnMainSync { PhoneControlService.voiceUiVisible = false } }
    }

    @Test fun pinchRechecksConsentBeforeDispatch() {
        openPinchTarget(false)
        val before = SessionState.feedback.value.sequence
        instrumentation.runOnMainSync {
            PhoneControlService.voiceUiVisible = true
            PhoneControlService.current!!.executeWhenReady(Command.Pinch(false), "dev.saygo.app.test")
            Preferences(context).controlConsent = false
            PhoneControlService.voiceUiVisible = false
        }
        await("queued pinch rejected after consent withdrawn") { SessionState.feedback.value.sequence > before }
        assertFalse(SessionState.feedback.value.success)
        assertEquals("Phone controls are off.", SessionState.feedback.value.title)
        assertTrue(screenHas("Pinch receiver ready"))
    }

    @Test fun pinchRejectsMissingOwnAndChangedOriginsWithoutTouchingTheScreen() {
        openPinchTarget(false)
        for (origin in listOf(null, context.packageName, "com.example.other")) {
            val before = SessionState.feedback.value.sequence
            instrumentation.runOnMainSync { PhoneControlService.current!!.executeWhenReady(Command.Pinch(true), origin) }
            await("pinch origin rejected") { SessionState.feedback.value.sequence > before }
            assertFalse(SessionState.feedback.value.success)
            assertTrue(screenHas("Pinch receiver ready"))
        }
    }

    private fun openPinchTarget(offset: Boolean): android.graphics.Rect {
        context.startActivity(Intent().setClassName("dev.saygo.app.test", "dev.saygo.app.PinchTargetActivity")
            .putExtra("offset", offset).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        automation.waitForIdle(300, 5000)
        var result: android.graphics.Rect? = null
        await("pinch fixture ready in both accessibility clients") {
            val root = automation.rootInActiveWindow
            val serviceRoot = PhoneControlService.current?.rootInActiveWindow
            val bounds = android.graphics.Rect().also { root?.getBoundsInScreen(it) }
            val serviceBounds = android.graphics.Rect().also { serviceRoot?.getBoundsInScreen(it) }
            val ready = root?.packageName?.toString() == "dev.saygo.app.test" &&
                root.findAccessibilityNodeInfosByText("Pinch receiver ready").isNotEmpty() &&
                serviceRoot?.windowId == root.windowId && serviceBounds == bounds && !bounds.isEmpty
            @Suppress("DEPRECATION")
            serviceRoot?.recycle()
            if (ready) result = bounds
            ready
        }
        return checkNotNull(result)
    }

    private fun openGridTarget(): android.graphics.Rect {
        context.startActivity(Intent().setClassName("dev.saygo.app.test", "dev.saygo.app.GridTargetActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        // The node tree can appear before the Activity launch animation settles its bounds.
        automation.waitForIdle(300, 5000)
        var observedBounds: android.graphics.Rect? = null
        await("grid target ready") {
            val root = automation.rootInActiveWindow
            if (root?.packageName?.toString() != "dev.saygo.app.test" || root.findAccessibilityNodeInfosByText("Grid receiver ready").isEmpty()) false
            else {
                val bounds = android.graphics.Rect().also(root::getBoundsInScreen)
                val serviceRoot = PhoneControlService.current?.rootInActiveWindow
                val serviceBounds = android.graphics.Rect()
                serviceRoot?.getBoundsInScreen(serviceBounds)
                val ready = !bounds.isEmpty && serviceRoot?.windowId == root.windowId && serviceBounds == bounds &&
                    serviceRoot.findAccessibilityNodeInfosByText("Grid receiver ready").isNotEmpty()
                @Suppress("DEPRECATION")
                serviceRoot?.recycle()
                if (ready) observedBounds = bounds
                ready
            }
        }
        return checkNotNull(observedBounds)
    }
    private fun enableWindowInspection() {
        val info = automation.serviceInfo
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
    }
    // Touch-transparent, non-focusable windows are absent from UiAutomation's interactive
    // window list. Inspect the real attached View, then independently check delivered input.
    private fun gridNode(): android.view.View? {
        var visible: android.view.View? = null
        instrumentation.runOnMainSync {
            val service = PhoneControlService.current
            val grid = service?.let { PhoneControlService::class.java.getDeclaredField("grid").apply { isAccessible = true }.get(it) }
            val view = grid?.let { it.javaClass.getDeclaredField("view").apply { isAccessible = true }.get(it) as android.view.View }
            if (view?.isShown == true && view.isAttachedToWindow) visible = view
        }
        return visible
    }
    private fun captureGrid(name: String) {
        val view = gridNode() ?: error("Grid missing; feedback=${SessionState.feedback.value.title}")
        val drawn = java.util.concurrent.CountDownLatch(1)
        instrumentation.runOnMainSync {
            view.postOnAnimation { view.postOnAnimation { drawn.countDown() } }
        }
        assertTrue("Grid frame completed", drawn.await(3, java.util.concurrent.TimeUnit.SECONDS))
        val screenshot = automation.takeScreenshot() ?: error("No screenshot")
        var bluePixels = 0
        for (y in 0 until screenshot.height step 8) for (x in 0 until screenshot.width step 8) {
            val pixel = screenshot.getPixel(x, y)
            if (android.graphics.Color.blue(pixel) > 150 && android.graphics.Color.red(pixel) < 80) bluePixels++
        }
        assertTrue("Grid must actually be rendered in the screenshot", bluePixels > 40)
        java.io.File(context.filesDir, name).outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
    private fun awaitGridTouch(kind: String, x: Float, y: Float) {
        await("$kind received at $x,$y") {
            val text = automation.rootInActiveWindow?.findAccessibilityNodeInfosByText("$kind at ")?.firstOrNull()?.text?.toString().orEmpty()
            val match = Regex("$kind at ([0-9]+),([0-9]+)").matchEntire(text)
            match != null && kotlin.math.abs(match.groupValues[1].toInt() - x) <= 2 && kotlin.math.abs(match.groupValues[2].toInt() - y) <= 2
        }
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
        try {
            await("Settings foreground") {
                val root = PhoneControlService.current?.rootInActiveWindow
                val name = root?.packageName?.toString()
                @Suppress("DEPRECATION")
                root?.recycle()
                name == "com.android.settings"
            }
        } catch (failure: AssertionError) {
            throw AssertionError("Settings setup failed: serviceRoot=" + PhoneControlService.current?.rootInActiveWindow?.packageName +
                "; uiRoot=" + automation.rootInActiveWindow?.packageName + "; connected=" + PhoneControlService.connected.value, failure)
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
